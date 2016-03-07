(ns featured-gml.runner-test
  (:require [featured-gml.runner :refer :all]
            [featured-gml.code :refer :all]
            [clojure.test :refer :all]))

(defn translate-resource [resource element translator]
  (with-open [in (clojure.java.io/reader (clojure.java.io/resource resource))]
    (binding [*sequence-selector* :content
              *feature-selector* identity
              *translators* {element translator}]
      (process-stream in))))

(def optional-translator
  (deftranslator :new
    [[:result [:first] [:second]]]))

(def optional-gml-translator
  (deftranslator :new
    [[:result [:first :s/inner-gml] [:second :s/inner-gml]]]))

(deftest optional-selector
  (testing "first is selected"
    (is (= "first inner"
           (-> (translate-resource "optional1.xml" :element optional-translator) first :result))))
  (testing "second is selected"
    (is (= "second inner"
           (-> (translate-resource "optional2.xml" :element optional-translator) first :result)))
    ))

(deftest optional-gml-selector
  (testing "first is selected"
    (is (= "<Point xmlns=\"\"></Point>"
           (-> (translate-resource "optional-gml1.xml" :element optional-gml-translator) first :result :gml))))
  (testing "second is selected"
    (is (= "<Polygon xmlns=\"\"></Polygon>"
           (-> (translate-resource "optional-gml2.xml" :element optional-gml-translator) first :result :gml)))
    ))

(def nested-translator
  (deftranslator :new
    [[:result-level1 [:level1]]
     [:result-level2 [:info :level2]]]))

(deftest nested-selector
  (testing "nested input"
    (let [translated (translate-resource "nested.xml" :element nested-translator)]
      (is (= "bar" (-> translated first :result-level1)))
      (is (= "foo" (-> translated first :result-level2))))))
