(ns featured-gml.api
  (:require [featured-gml.runner :as runner]
            [featured-gml.zip :as zip]
            [featured-gml.filesystem :as fs]
            [ring.util.response :as r]
            [ring.middleware.json :as middleware]
            [clj-time [core :as t] [local :as tl]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [schema.core :as s]
            [org.httpkit.client :as http-kit]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout]]
            [clojure.java.io :as io])
  (:gen-class))

(extend-protocol cheshire.generate/JSONable
  org.joda.time.DateTime
  (to-json [t jg] (.writeString jg (str t))))

(defn translate-file-from-stream [dataset mapping validity reader out-file]
  "Read from reader, xml2json translate the content"
  (with-open [writer (io/writer out-file)]
    (runner/translate dataset mapping validity reader writer)))

(defn translate-file-from-zipstream [dataset mapping validity zipstream workingdir entry]
  "Transform a single entry in a zip-file. Returns the location where the result is saved on the filesystem"
  (let [resulting-file (fs/target-file workingdir (.getName entry))]
    (log/debug "Going to transform zip-entry" (.getName entry) "to" (.getPath resulting-file))
    (translate-file-from-stream dataset mapping validity zipstream resulting-file)
    (.closeEntry zipstream)
    resulting-file))

(defn translate-entire-stream [zipped stream dataset mapping validity workingdir original-filename]
  (if zipped
    (with-open [zipstream (java.util.zip.ZipInputStream. stream)]
        (assoc {} :transformed-files [(doall
                                        (map #(translate-file-from-zipstream dataset mapping validity zipstream workingdir %) (zip/entries zipstream)))]))
    (do
      (let [target-filename  (fs/target-file workingdir original-filename)]
        (translate-file-from-stream dataset mapping validity stream target-filename)
        (assoc {} :transformed-files [target-filename])))))

(defn extract-name-from-uri [uri]
  "Extract the requested-path from an uri"
  (last (re-find #"(\w+).(?:\w+)$" uri)))

(defn download-file-as-stream [uri]
  "Download uri and get the body as stream. Returns :error key if an error occured"
  (let [{:keys [status body headers]} @(http-kit/get uri {:as :stream})]
    (if (nil? status)
      [:download-error (str "No response when trying to download: " uri)]
      (if (< status 300)
        {:zipped (re-find #"zip" (:content-type headers))
         :dlstream body
         :filename (extract-name-from-uri uri)}
        {:download-error (str "No success: Got a statuscode " status " when downloading " uri )}))))

(defn process-downloaded-xml2json-data [datasetname mapping validity zipped data-stream original-filename]
  (log/info "Going to transform dataset"datasetname)
  (let [uuid (fs/uuid)
        dir-in-store (fs/determine-store-location uuid)]
    (.mkdir (java.io.File. dir-in-store))
    (log/info "Going to store files in" (.toString dir-in-store))
    (let [transform-result (translate-entire-stream zipped data-stream datasetname mapping validity dir-in-store original-filename),
          unzipped-files (:transformed-files transform-result)]
      (log/debug "Transformation of"(count unzipped-files) "file(s) done")
      (let [zipped-files (zip/zip-files-in-directory dir-in-store)]
        (log/info "Zipped" (count zipped-files) "file(s) in store-directory")
        (fs/delete-files unzipped-files)
        (log/info "Unzipped files removed")
        {:uuid uuid
         :json-files (map #(.getName %) zipped-files)}))))

(defn process-xml2json [dataset mapping uri validity]
  "Proces the request and  zip the result in a zip on the filesystem and return a reference to this zip-file"
  (let [result (download-file-as-stream uri)]
     (if (:download-error result)
      (r/response {:status 400 :body (:download-error result)})
      (process-downloaded-xml2json-data dataset mapping validity (:zipped result) (:dlstream result) (:filename result)))))

(def xml2json-callback-chan (chan))

(defn async-process-xml2json [callback-uri dataset mapping file validity]
  "Async handling of xml2json. Will do a callback once completed on the provided callback-uri"
  (go
    (doall
      (http-kit/post callback-uri {
                                    :body (cheshire.core/generate-string (<! xml2json-callback-chan))
                                    :headers {"content-type" "application/json"}})
      (log/info "Async job complete. Callback done on" callback-uri)))
  (>!! xml2json-callback-chan (process-xml2json dataset mapping file validity))
  {:status 200 :body (str "Accepted will do callback on " callback-uri)})

(defn handle-xml2json-req [req]
  "Get the properties from the request and start an sync or async xml2json operation (depending on if a callback-uri is provided)"
  (r/response
    (let [dataset (:dataset (:body req))
          mapping (:mapping (:body req))
          file (:file (:body req))
          validity (:validity (:body req))
          callback-uri (:callback-uri (:body req))]
      (if (some str/blank? [dataset mapping file validity])
        {:status 400 :body "dataset, mapping, file and validity are all required"}
        (if callback-uri
          (async-process-xml2json callback-uri dataset mapping file validity)
          (process-xml2json dataset mapping file validity))))))

(defn handle-delete-req [req]
  "Delete the file referenced by uuid"
  (let [uuid (:uuid (:params req))]
    (let [store-location (fs/determine-store-location uuid)]
      (log/debug "Delete requested of" store-location)
      (if (fs/delete-directory store-location)
        (do
          (log/info uuid "deleted")
          {:status 200})
         (do
          (log/warn uuid "deleted failed. No such file?")
           {:status 500, :body "No such file"})))))

(defn handle-getjson-req [req]
  "Stream a json file identified by uuid"
   (let [uuid (:uuid (:params req))
         file (:file (:params req))]
     (log/debug "Request for" uuid "file" file)
     (let [local-file (io/file (fs/determine-store-location (str/trim uuid)) file)]
       (if (.exists local-file)
         {:headers {"Content-Description" "File Transfer"
                          "Content-type" "application/octet-stream"
                          "Content-Disposition" (str "attachment;filename=" (.getName local-file))
                          "Content-Transfer-Encoding" "binary"}
          :body local-file}
         {:status 500, :body "No such file"}))))

(defroutes handler
    (context "/api" []
             (GET "/info" [] (r/response {:version (runner/implementation-version)}))
             (GET "/ping" [] (r/response {:pong (tl/local-now)}))
             (GET "/get/:uuid/:file" request handle-getjson-req)
             (POST "/xml2json" request handle-xml2json-req)
             (DELETE "/delete/:uuid" request handle-delete-req))
    (route/not-found "Featured-gml: Unknown operation. Try /api/info, /api/ping, /api/get, /api/xml2json or /api/delete"))

(defn wrap-exception-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e)
        {:status 400 :body (.getMessage e)}))))

(def app
  (-> handler
     (middleware/wrap-json-body {:keywords? true})
      middleware/wrap-json-response
      wrap-exception-handling))
