(ns featured-gml.api
  (:require [featured-gml.runner :as runner]
            [ring.util.response :as r]
            [ring.middleware.json :as middleware]
            [clj-time [core :as t] [local :as tl]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [schema.core :as s]
            [org.httpkit.client :as http-kit]
            [clojure.tools.logging :as log])
  (:gen-class))

; TODO check server online
(extend-protocol cheshire.generate/JSONable
  org.joda.time.DateTime
  (to-json [t jg] (.writeString jg (str t))))

; no success case
(defn transform-runner [dataset mapping file-dl output-file]
  "Try to download file-dl, apply the mapping and saving result to output-file"
  (when (< (:status @file-dl) 300 )
    (runner/translate dataset mapping (:body @file-dl) output-file)
    (log/debug "Translated to temporary-file: " (.toString output-file))))

(defn upload-result [tmp-file upload-loc]
  "Upload the content of tmp-file to upload-loc using http"
  (try
    (let [upload (http-kit/post upload-loc {:body (slurp tmp-file)})]
      (let [uploadresult ( :status @upload)]
        (log/debug "Uploading results to"upload-loc" -> http.response="uploadresult)
        (if (< uploadresult 300)
          (upload-loc)
          nil)))
  (finally (.delete tmp-file))))

(defn process-xml2json [dataset mapping uri]
  "Downloads the uri and transform it to temporary file"
  (log/info "Going to transform \""dataset"\" using url" uri)
  (let [file-dl (http-kit/get uri)
        temp-out-file (java.io.File/createTempFile "transformed" ".json")
        upload-loc (str "http://localhost:8000/" "teverzinnen")]
        (transform-runner dataset mapping file-dl temp-out-file)
        (if (upload-result temp-out-file upload-loc)
          {:json-location upload-loc}
          {:status 500 :error "Error uploading result"})))


(defn handle-xml2json-req [req]
  "Get the parameters from the request and start a translation run if all filled correctly"
  (let [dataset (:dataset (:body req))
        mapping (:mapping (:body req))
        file (:file (:body req))]
    (if (or (nil? dataset) (nil? mapping) (nil? file))
     {:status 500 :body "dataset, mapping and file are all required"}
      (process-xml2json dataset mapping file))))

(defroutes handler
    (context "/api" []
             (GET "/info" [] (r/response {:version (runner/implementation-version)}))
             (GET "/ping" [] (r/response {:pong (tl/local-now)}))
             ; TODO get status 500 when handle-xml2json-req return map
             (POST "/xml2json" request (r/response (handle-xml2json-req request))))
    (route/not-found "NOT FOUND"))

(def app
  (-> handler
     (middleware/wrap-json-body {:keywords? true})
      middleware/wrap-json-response))
