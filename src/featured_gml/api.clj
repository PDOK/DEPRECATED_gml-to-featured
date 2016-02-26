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

(defn translate-zipstream [dataset mapping validity zipstream workingdir entry]
  "Transform a single entry in a zip-file. Returns the location where the result is saved on the filesystem"
  (let [resulting-file (fs/target-file workingdir (.getName entry))]
    (translate-stream dataset mapping validity zipstream resulting-file)
    (.closeEntry zipstream)
    resulting-file))

; Futurework not safe result to file but back to a stream to improve performance
(defn download-and-translate-files [dataset mapping validity uri workingdir]
  "Download uri. If the file is a zip, extract all files in it"
  (let [{:keys [status body headers]} @(http-kit/get uri {:as :stream})]
    (if (< status 300)
      (if (= (:content-type headers) "application/zip")
        (with-open [zipstream (java.util.zip.ZipInputStream. body)]
          (doall
            (map #(translate-zipstream dataset mapping validity zipstream workingdir %) (zip/entries zipstream))))
        (let [resulting-file (fs/target-file workingdir uri)]
          (do
            (translate-stream dataset mapping validity body resulting-file)
            [resulting-file]))))))

; TODO add edge cases (file cannot be downloaded, server not running, zip with failure, etc.)
(defn process-xml2json [dataset mapping uri validity]
  "Proces the request, zip the result in a zip on the filesystem and return a reference to this zip-file"
  (log/info "Going to transform dataset"dataset"using url" uri)
  (let [uuid (fs/uuid)
        dir-in-store (fs/determine-store-location uuid)]
    (.mkdir (java.io.File. dir-in-store))
    (log/debug "Store-directory" (.toString dir-in-store))
    (let [unzipped-files (download-and-translate-files dataset mapping validity uri dir-in-store)]
      (log/info "Transformation done in store-directory")
      (let [zipped-files (zip/zip-files-in-directory dir-in-store)]
        (log/info "Zipped" (count zipped-files) "file(s) in store-directory")
        (fs/delete-files unzipped-files)
        (log/info "Unzipped files removed")
        (r/response {:uuid uuid
                     :json-files (map #(.getName %) zipped-files)})))))

(defn handle-xml2json-req [req]
  "Get the properties from the request and start translating run iff all are provided"
  (let [dataset (:dataset (:body req))
        mapping (:mapping (:body req))
        file (:file (:body req))
        validity (:validity (:body req))]
    (if (some str/blank? [dataset mapping file validity])
      {:status 500 :body "dataset, mapping, file and validity are all required"}
      (process-xml2json dataset mapping file validity))))

(defn handle-delete-req [req]
  "Delete the file referenced by uuid"
  (let [uuid (:uuid (:params req))]
    (let [store-location (fs/determine-store-location uuid)]
      (log/debug "Delete requested of" store-location)
      (if (fs/delete-directory store-location)
        {:status 200}
        {:status 500, :body "No such file"}))))

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
    (route/not-found "Unknown operation"))

(def app
  (-> handler
     (middleware/wrap-json-body {:keywords? true})
      middleware/wrap-json-response))
