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
            [clojure.java.io :as io])
  (:gen-class))

(extend-protocol cheshire.generate/JSONable
  org.joda.time.DateTime
  (to-json [t jg] (.writeString jg (str t))))

(defn translate-stream [dataset mapping validity reader out-file]
  "Read from reader, xml2json translate the content"
  (with-open [writer (io/writer out-file)]
    (runner/translate dataset mapping validity reader writer)))

; Futurework not safe result to file but back to a stream to improve performance
(defn download-and-translate-files [dataset mapping validity uri tmpdir]
  "Download uri. If the file is a zip, extract all files in it"
  (let [{:keys [status body headers]} @(http-kit/get uri {:as :stream})]
    (if (< status 300)
      (if (= (:content-type headers) "application/zip")
        (with-open [zipstream (java.util.zip.ZipInputStream. body)]
          (doseq [e (zip/entries zipstream)]
            (translate-stream dataset mapping validity zipstream (zip/target-file tmpdir (.getName e)))
            (.closeEntry zipstream)))
        (translate-stream dataset mapping body (zip/target-file tmpdir uri))))))

; TODO add edge cases (file cannot be downloaded, server not running, zip with failure, etc.)
(defn process-xml2json [req dataset mapping uri validity]
  "Proces the request, zip the result in a zip on the filesystem and return a reference to this zip-file"
  (log/info "Going to transform \""dataset"\" using url" uri)
  (let [workingdir (fs/get-tmp-dir),
        uuid fs/uuid]
      (download-and-translate-files dataset mapping validity uri workingdir)
      (log/debug "Transformation done and saved in" (.toString workingdir))
      (let [zip-file (fs/determine-zip-location uuid)]
        (zip/zip-directory zip-file workingdir)
        (log/debug "Result zipped to " zip-file)
        (fs/delete-directory workingdir)
        (log/info uri "transformed. Resulting uuid:"uuid)
        (r/response {:json-uuid uuid}))))

(defn handle-xml2json-req [req]
  "Get the properties from the request and start translating run iff all are provided"
  (let [dataset (:dataset (:body req))
        mapping (:mapping (:body req))
        file (:file (:body req))
        validity (:validity (:body req))]
    (if (some str/blank? [dataset mapping file validity])
      {:status 500 :body "dataset, mapping, file and validity are all required"}
      (process-xml2json req dataset mapping file validity))))

(defn handle-delete-req [req]
  "Delete the file referenced by req"
  (let [uuid (:uuid (:params req))]
    (log/debug "Delete requested of" uuid)
    (if (fs/safe-delete (fs/determine-zip-location (str/trim uuid)))
      {:status 200, :deleted uuid}
      {:status 500, :body "No such file"})))

; Possible add access constrains to file
(defn handle-getjson-req [req]
  "Stream a json file identified by uuid"
  (let [uuid (:uuid (:params req))]
    (let [zip-filename (fs/determine-zip-filename (str/trim uuid))
           zip-file (io/file  (fs/determine-zip-location (str/trim uuid)))]
      (if (.exists zip-file)
        {:headers {"Content-Description" "File Transfer"
                          "Content-type" "application/octet-stream"
                          "Content-Disposition" (str "attachment;filename=" zip-filename)
                          "Content-Transfer-Encoding" "binary"}
         :body zip-file}
        {:status 500, :body "No such file"}))))

(defroutes handler
    (context "/api" []
             (GET "/info" [] (r/response {:version (runner/implementation-version)}))
             (GET "/ping" [] (r/response {:pong (tl/local-now)}))
             (GET "/get/:uuid" request handle-getjson-req)
             (POST "/xml2json" request handle-xml2json-req)
             (DELETE "/delete/:uuid" request handle-delete-req))
    (route/not-found "Unknown operation"))

(def app
  (-> handler
     (middleware/wrap-json-body {:keywords? true})
      middleware/wrap-json-response))
