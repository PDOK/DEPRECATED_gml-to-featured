(ns gml-to-featured.runner-test
  (:require [gml-to-featured.runner :refer :all]
            [gml-to-featured.code :refer :all]
            [clojure.test :refer :all]))

(defn translate-resource [resource element translator]
  (with-open [in (clojure.java.io/reader (clojure.java.io/resource resource))]
    (binding [*sequence-selector* identity
              *translators* {element translator}]
      (process-stream in))))

(def optional-translator
  (deftranslator :new
    [[:result [:first] [:second]]]))

(def depth-translator
  (deftranslator :new
    [[:result [:third]]]))

(def optional-gml-translator
  (deftranslator :new
    [[:result [:first :s/inner-gml] [:second :s/inner-gml]]]))

(deftest optional-selector
  (testing "first is selected"
    (is (= "first inner"
           (-> (translate-resource "optional1.xml" [:element] optional-translator) first :result))))
  (testing "second is selected"
    (is (= "second inner"
           (-> (translate-resource "optional2.xml" [:element] optional-translator) first :result)))
    ))

(deftest path-depth-selector
  (testing "path-depth"
    (is (= "third inner"
           (-> (translate-resource "optional3.xml" [:element :first :second] depth-translator) first :result)))))

(deftest optional-gml-selector
  (testing "first is selected"
    (is (= "<Point xmlns=\"\"></Point>"
           (-> (translate-resource "optional-gml1.xml" [:element] optional-gml-translator) first :result :gml))))
  (testing "second is selected"
    (is (= "<Polygon xmlns=\"\"></Polygon>"
           (-> (translate-resource "optional-gml2.xml" [:element] optional-gml-translator) first :result :gml)))
    ))

(def nested-translator
  (deftranslator :new
    [[:result-level1 [:level1]]
     [:result-level2 [:info :level2]]]))

(deftest nested-selector
  (testing "nested input"
    (let [translated (translate-resource "two-levels.xml" [:element] nested-translator)]
      (is (= "bar" (-> translated first :result-level1)))
      (is (= "foo" (-> translated first :result-level2))))))

(deftest test-translate
  (let [out (java.io.StringWriter.)]
    (testing "translate"
      (with-open [in (clojure.java.io/reader (clojure.java.io/resource "nested.xml"))]
        (binding [*sequence-selector* identity]
          (translate "dataset-1" (slurp "dev-resources/nested-config.edn") "2016-01-01" in out))
        (let [result (.toString out)]
          (is (= true (boolean (re-find #"\"dataset\":\"dataset-1\"" result))))
          (is (= true (boolean (re-find #"\"attr-k\":\"foo\"" result))))
          (is (= true (boolean (re-find #"\"attr-l\":\{\"attr-xyz\":\"bar\"\}" result)))))))))
