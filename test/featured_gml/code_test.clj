(ns featured-gml.code-test
  (:require [clojure.test :refer :all]
            [featured-gml.code :refer :all]))


(deftest set-action-test
  (testing "translator copies action to result"
    (is (= ((deftranslator :random111 []) {}) {:_action :random111}))))

(def auto-name
  `(deftranslator :new
    [:test-selector]))

(deftest auto-name-test
  (testing "auto name with camelcase"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (featured-gml.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :test-selector (featured-gml.xml/xml1-> zp :testSelector featured-gml.xml/text)})))
         (macroexpand auto-name)))))


(def define-name
  `(deftranslator :new
     [[:target-key :selectorKey]]))

(deftest define-name-test
  (testing "self defined selector"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (featured-gml.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :target-key (featured-gml.xml/xml1-> zp :selectorKey featured-gml.xml/text)})))
         (macroexpand define-name)))))

(def base
  `[:selector])

(def extension
  `(deftranslator :new base
     [:selector2]))

(deftest extension-test
  (testing "translator with base"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (featured-gml.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :selector (featured-gml.xml/xml1-> zp :selector featured-gml.xml/text)
                   :selector2 (featured-gml.xml/xml1-> zp :selector2 featured-gml.xml/text)})))
         (macroexpand extension)))))

(def or-choice
  `(deftranslator :new
     [[:choice [:A] [:B]]]))

(deftest choiche-test
  (testing "choice test"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (featured-gml.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :choice (clojure.core/or (featured-gml.xml/xml1-> zp :A featured-gml.xml/text)
                               (featured-gml.xml/xml1-> zp :B featured-gml.xml/text))})))
         (macroexpand or-choice)))))

(def tag-replacement
  `(deftranslator :new
     [[:target-key :s/tag]]))

(deftest tag-replacement-test
  (testing "replace function tags"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (featured-gml.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :target-key (featured-gml.xml/xml1-> zp featured-gml.xml/tag)})))
         (macroexpand tag-replacement)))))

(def function-append
  `(deftranslator :new
     [[:target-key 'moment]]))


(deftest function-append-test
  (testing "append function"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (featured-gml.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :target-key (clojure.core/->
                                (featured-gml.xml/xml1-> zp :targetKey featured-gml.xml/text)
                                featured-gml.code/moment)})))
         (macroexpand function-append)))))
