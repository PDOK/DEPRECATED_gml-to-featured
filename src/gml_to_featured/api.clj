(ns gml-to-featured.api
  (:require [gml-to-featured.runner :as runner]
            [gml-to-featured.config :as config]
            [gml-to-featured.zip :as zip]
            [gml-to-featured.filesystem :as fs]
            [ring.util.response :as r]
            [ring.middleware.json :refer :all]
            [ring.middleware.defaults :refer :all]
            [clj-time [local :as tl]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]
            [clojure.java.io :as io])
  (:gen-class)
  (:import (clojure.lang PersistentQueue)
           (com.fasterxml.jackson.core JsonGenerator)
           (java.io File FileInputStream)
           (java.net URI URISyntaxException)
           (java.util.zip ZipEntry ZipFile ZipOutputStream)
           (org.joda.time DateTime)))

(extend-protocol cheshire.generate/JSONable
  DateTime
  (to-json [t, ^JsonGenerator jg] (.writeString jg (str t))))

(defn- translate-file-from-stream [reader, dataset, mapping, validity, ^String json-filename]
  "Read from reader, xml2json translate the content"
  (let [compressed-file (fs/create-target-file json-filename)]
    (with-open [output-stream (io/output-stream compressed-file)
                zip (ZipOutputStream. output-stream)]
      (.putNextEntry zip (ZipEntry. json-filename))
      (runner/translate dataset mapping validity reader zip)
      (.closeEntry zip))
    compressed-file))

(defn- translate-file-from-zipentry [dataset, mapping, validity, ^ZipFile zipfile, ^ZipEntry entry]
  "Transform a single entry in a zip-file. Returns the location where the result is saved on the filesystem"
  (let [json-filename (fs/json-filename (.getName (File. (.getName entry))))
        entry-stream (.getInputStream zipfile entry)]
    (log/debug "Going to transform zip entry" (.getName entry) "to" json-filename)
    (let [resulting-file (translate-file-from-stream entry-stream dataset mapping validity json-filename)]
      (.close entry-stream)
      resulting-file)))

(defn- translate-from-zipfile [^File file, dataset, mapping, validity]
  "Transforms entries in a zip file and returns a vector with the transformed files"
  (with-open [zip (ZipFile. file)]
    (into [] (map (partial translate-file-from-zipentry
                           dataset
                           mapping
                           validity
                           zip) (zip/xml-entries zip)))))

(defn translate-entire-file [zipped, ^File file, dataset, mapping, validity, original-filename]
  "Transforms a file or zip-stream and returns a vector with the transformed files"
  (if zipped
    (translate-from-zipfile file dataset mapping validity)
    (let [json-filename (fs/json-filename original-filename)]
      (log/debug "Going to transform file" original-filename "to" json-filename)
      (with-open [stream (FileInputStream. file)]
        [(translate-file-from-stream stream dataset mapping validity json-filename)]))))

(defn extract-filename [headers, ^String uri]
  "Extract the original filename from the Content-Disposition header, or from the URI if unavailable"
  (let [filename-from-header (last (re-find #"filename=\"?([^\";]+)" (or (:content-disposition headers) "")))
        filename-from-uri (try (last (re-find #"([^\/]+)$" (.getPath (URI. uri)))) (catch URISyntaxException e nil))]
    (or filename-from-header filename-from-uri "data")))

(defn download-file [uri]
  "Download uri and get the body as stream. Returns :error key if an error occured"
  (let [{:keys [status body headers]} (http/get uri {:as :stream, :throw-exceptions false})]
    (if (nil? status)
      [:download-error (str "No response when trying to download: " uri)]
      (if (< status 300)
        (let [original-filename (extract-filename headers uri)
              tmp (File/createTempFile "gml-to-featured" original-filename)]
          (io/copy body tmp)
          (.close body)
          {:zipped (re-find #"zip" (:content-type headers))
           :file tmp
           :original-filename original-filename})
        {:download-error (str "No success: Got a statuscode " status " when downloading " uri)}))))

(defn process-downloaded-xml2json-data [datasetname mapping validity zipped data-file original-filename]
  (log/info "Going to transform dataset" datasetname)
  (let [zipped-files (translate-entire-file zipped
                                            data-file
                                            datasetname
                                            mapping
                                            validity
                                            original-filename)]
    (log/info "Created" (count zipped-files) "zip file(s) in store directory")
    {:json-files (map #(config/create-url (str "api/get/" (.getName %))) zipped-files)}))

(defn stats-on-callback [callback-chan request stats]
  (when (:callback request)
    (go (>! callback-chan [(:callback request) stats]))))

(defn process-xml2json* [worker-id stats callback-chan {:keys [file dataset mapping validity] :as request}]
  "Proces the request and  zip the result in a zip on the filesystem and return a reference to this zip-file"
  (log/info "Processing " request)
  (swap! stats assoc-in [:processing worker-id] (dissoc request :mapping))
  (swap! stats update-in [:queued] pop)
  (let [result (download-file file)]
    (if (:download-error result)
      (do
        (log/error "Download error" result)
        (swap! stats assoc-in [:processing worker-id] nil)
        (stats-on-callback callback-chan request (assoc request :error (:download-error result))))
      (try
        (let [process-result (process-downloaded-xml2json-data dataset mapping validity (:zipped result) (:file result)
                                                               (:original-filename result))]
          (fs/safe-delete (:file result))
          (swap! stats assoc-in [:processing worker-id] nil)
          (stats-on-callback callback-chan request process-result))
        (catch Exception e
          (log/error "Processing error" e)
          (swap! stats assoc-in [:processing worker-id] nil)
          (stats-on-callback callback-chan request (assoc request :error (str e))))))))

(defn handle-xml2json-req [stats process-chan http-request]
  "Get the properties from the request and start an async xml2json operation"
  (future (fs/cleanup-old-files (* 3600 24 config/cleanup-threshold)))
  (let [request (:body http-request)
        dataset (:dataset request)
        mapping (:mapping request)
        file (:file request)
        validity (:validity request)]
    (if (some str/blank? [dataset mapping file validity])
      (r/status (r/response {:error "dataset, mapping, file and validity are all required"}) 400)
      (do
        (log/info "Queueing " (dissoc request :mapping))
        (if (a/offer! process-chan request)
          (do (swap! stats update-in [:queued] #(conj % (dissoc request :mapping))) (r/response {:result :ok}))
          (r/status (r/response {:error "queue full"}) 429))))))

(defn handle-getjson-req [req]
  "Stream a json file identified by uuid"
   (let [file (:file (:params req))]
     (log/debug "Request for" file)
     (if-let [local-file (fs/get-file file)]
       {:headers {"Content-Description"       "File Transfer"
                  "Content-Type"              "application/octet-stream"
                  "Content-Disposition"       (str "attachment; filename=" (.getName local-file))
                  "Content-Transfer-Encoding" "binary"}
        :body    local-file}
       {:status 500, :body "No such file"})))

(defn- callbacker [uri result]
  (try
    (http/post uri {:body    (cheshire.core/generate-string result)
                    :headers {"Content-Type" "application/json"}})
    (catch Exception e (log/error "Callback error" e))))

(defn api-routes [process-chan stats]
  (defroutes api-routes
             (context "/api" []
               (GET "/info" [] (r/response {:version (slurp (clojure.java.io/resource "version"))
                                            :base-url (config/create-url "")}))
               (GET "/ping" [] (r/response {:pong (tl/local-now)}))
               (POST "/ping" [] (fn [r] (log/info "!ping pong!" (:body r)) (r/response {:pong (tl/local-now)})))
               (GET "/stats" [] (r/response @stats))
               (GET "/get/:file" [] handle-getjson-req)
               (POST "/xml2json" [] (partial handle-xml2json-req stats process-chan)))
             (route/not-found "gml-to-featured: Unknown operation. Try /api/stats, /api/info, /api/ping, /api/get, /api/xml2json")))

(defn wrap-exception-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e)
        {:status 400 :body (.getMessage e)}))))

(defn create-workers [stats callback-chan process-chan]
  (let [factory-fn (fn [worker-id]
                     (swap! stats assoc-in [:processing worker-id] nil)
                     (log/info "Creating worker " worker-id)
                     (go (while true (process-xml2json* worker-id stats callback-chan (<! process-chan)))))]
    (config/create-workers factory-fn)))

(def process-chan (chan 1000))
(def callback-chan (chan 10))
(def stats (atom {:processing {}
                        :queued     (PersistentQueue/EMPTY)}))

(defn init! []
  (create-workers stats callback-chan process-chan)
  (go (while true (apply callbacker (<! callback-chan)))))

(def app (-> (api-routes process-chan stats)
             (wrap-json-body {:keywords? true :bigdecimals? true})
             (wrap-json-response)
             (wrap-defaults api-defaults)
             (wrap-exception-handling)
             (routes)))
