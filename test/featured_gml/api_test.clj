(ns featured-gml.api-test
  (:require [featured-gml.api :refer :all]
            [featured-gml.filesystem :as fs]
            [clojure.java.io :as io]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.string :as str]
            [org.httpkit.client :as http-kit]
            [clojure.tools.logging :as log]
            [ring.mock.request :refer :all]
            [environ.core :refer [env]]
            [clojure.test :refer :all]))

(defn use-test-result-store [f]
  "Set a specific, temporary result-store in tmp-dir"
  (let [test-result-store (.getPath (io/file (System/getProperty "java.io.tmpdir") (fs/uuid)))]
    (log/debug "Using featured-gml.jsonstore during TEST" test-result-store)
    (env "featured-gml.jsonstore" test-result-store)
    (f)
    (fs/safe-delete test-result-store)))

(use-fixtures :once use-test-result-store)

(def built-in-formatter (time-format/formatters :basic-date-time))

(defn test-get [file-name]
  (let [get-req (str "/api/get/" file-name)
        response (app (request :get get-req))]
    (log/info "Using following url for GET: "get-req)
    (is (= (:status response) 200))))

(deftest single-file-converted-correctly
  "Test if input-file api-test/Landsgrens.gml results in a zip. Note does not test CONTENT of result"
  (let [mapping (slurp (io/resource "api-test/bestuurlijkegrenzen.edn"))
        input (io/input-stream (io/resource "api-test/Landsgrens.gml"))
        validity (time-format/unparse built-in-formatter (time/now))
        result (process-downloaded-xml2json-data "test" mapping validity false input "inputnaam.gml")]
    ; check resulting content
    (is (= 1 (count (:json-files result))))
    (is (.endsWith (first (:json-files result)) "inputnaam.gml.json.zip"))
    (test-get (first (:json-files result)))))


(deftest zip-file-converted-correctly
  "Test if input-file api-test/bestuurlijkegrenzen.zip results in multiple zip files. Note does not test CONTENT of result"
  (let [mapping (slurp (io/resource "api-test/bestuurlijkegrenzen.edn"))
        input (io/input-stream (io/resource "api-test/bestuurlijkegrenzen.zip"))
        validity (time-format/unparse built-in-formatter (time/now))
        result (process-downloaded-xml2json-data "test" mapping validity true input "bestuurlijkegrenzen.zip")]
    ; check resulting content
    (is (= 2 (count (:json-files result))))
    (doall
      (map test-get (:json-files result)))))
