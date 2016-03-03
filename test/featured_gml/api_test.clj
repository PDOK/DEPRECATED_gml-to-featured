(ns featured-gml.api-test
  (:require [featured-gml.api :refer :all]
            [clojure.java.io :as io]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [clojure.string :as str]
            [org.httpkit.client :as http-kit]
            [clojure.tools.logging :as log]
            [ring.mock.request :refer :all]
            [clojure.test :refer :all]))

(def built-in-formatter (time-format/formatters :basic-date-time))

(defn test-get [uuid file-name]
  (let [get-req (str "/api/get/" uuid "/" file-name)
        response (app (request :get get-req))]
    (log/info "Using following url for GET: "get-req)
    (is (= (:status response) 200))))

(defn test-delete [uuid]
  (let [del-req (str "/api/delete/" uuid)
          response (app (request :delete del-req))]
      (log/info "Using following url for DELETE: "del-req)
      (is (= (:status response) 200))))

(deftest single-file-converted-correctly
  "Test if input-file api-test/Landsgrens.gml results in a zip. Note does not test CONTENT of result"
  (let [mapping (slurp (io/resource "api-test/bestuurlijkegrenzen.edn"))
        input (io/input-stream (io/resource "api-test/Landsgrens.gml"))
        validity (time-format/unparse built-in-formatter (time/now))
        result (process-downloaded-xml2json-data "test" mapping validity false input "inputnaam.gml")]
    ; check resulting content
    (is (not (str/blank? (:uuid result))))
    (is (= 1 (count (:json-files result))))
    (is (.endsWith (first (:json-files result)) "inputnaam.gml.json.zip"))

    (test-get (:uuid result) (first (:json-files result)))
    (test-delete (:uuid result))))


(deftest zip-file-converted-correctly
  "Test if input-file api-test/bestuurlijkegrenzen.zip results in multiple zip files. Note does not test CONTENT of result"
  (let [mapping (slurp (io/resource "api-test/bestuurlijkegrenzen.edn"))
        input (io/input-stream (io/resource "api-test/bestuurlijkegrenzen.zip"))
        validity (time-format/unparse built-in-formatter (time/now))
        result (process-downloaded-xml2json-data "test" mapping validity true input "bestuurlijkegrenzen.zip")]
    ; check resulting content
    (is (not (str/blank? (:uuid result))))
    (is (= 2 (count (:json-files result))))
    (doall
      (map #(test-get (:uuid result) %) (:json-files result)))
    (test-delete (:uuid result))))
