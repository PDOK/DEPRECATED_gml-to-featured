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

(defn translate-stream [dataset mapping reader out-file]
  "Read from reader, xml2json translate the content"
  (with-open [writer (io/writer out-file)]
    (runner/translate dataset mapping reader writer)))

(defn download-and-translate-files [dataset mapping uri tmpdir]
  "Download uri. If the file is a zip, extract all files in it"
  (let [{:keys [status body headers]} @(http-kit/get uri {:as :stream})]
    (if (< status 300)
      (if (= (:content-type headers) "application/zip")
        (with-open [zipstream (java.util.zip.ZipInputStream. body)]
          (doseq [e (zip/entries zipstream)]
            (translate-stream dataset mapping zipstream (zip/target-file tmpdir (.getName e)))
            (.closeEntry zipstream)))
        (translate-stream dataset mapping body (zip/target-file tmpdir uri))))))

(defn process-xml2json [req dataset mapping uri]
  "Proces the request, zip the result in a zip on the filesystem and return a reference to this zip-file"
  (log/info "Going to transform \""dataset"\" using url" uri)
  (let [workingdir fs/get-tmp-dir,
        uuid fs/uuid]
      (download-and-translate-files dataset mapping uri workingdir)
      (log/debug "Transformation done and saved in" (.toString workingdir))
      (let [zip-file (str fs/resultstore uuid ".zip")]
        (zip/zip-directory zip-file workingdir)
        (log/debug "Result zipped to " zip-file)
        (fs/delete-directory workingdir)
        (log/info uri "transformed. Resulting uuid:"uuid)
        (r/response {:json-uuid uuid}))))

(defn handle-xml2json-req [req]
  "Get the properties from the request and start translating run iff all are provided"
  (let [dataset (:dataset (:body req))
        mapping (:mapping (:body req))
        file (:file (:body req))]
    (if (or (nil? dataset) (nil? mapping) (nil? file))
      {:status 500 :body "dataset, mapping and file are all required"}
      (process-xml2json req dataset mapping file))))

(defroutes handler
    (context "/api" []
             (GET "/info" [] (r/response {:version (runner/implementation-version)}))
             (GET "/ping" [] (r/response {:pong (tl/local-now)}))
             (POST "/xml2json" request handle-xml2json-req))
    (route/not-found "Unknown operation"))

(def app
  (-> handler
     (middleware/wrap-json-body {:keywords? true})
      middleware/wrap-json-response))
