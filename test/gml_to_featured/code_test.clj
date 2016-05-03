(ns gml-to-featured.code-test
  (:require [clojure.test :refer :all]
            [gml-to-featured.code :refer :all]))


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
                (clojure.core/let [zp (if (gml-to-featured.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :test-selector (gml-to-featured.xml/xml1-> zp :testSelector gml-to-featured.xml/text)})))
         (macroexpand auto-name)))))


(def define-name
  `(deftranslator :new
     [[:target-key :selectorKey]]))

(deftest define-name-test
  (testing "self defined selector"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (gml-to-featured.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :target-key (gml-to-featured.xml/xml1-> zp :selectorKey gml-to-featured.xml/text)})))
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
                (clojure.core/let [zp (if (gml-to-featured.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :selector (gml-to-featured.xml/xml1-> zp :selector gml-to-featured.xml/text)
                   :selector2 (gml-to-featured.xml/xml1-> zp :selector2 gml-to-featured.xml/text)})))
         (macroexpand extension)))))

(def or-choice
  `(deftranslator :new
     [[:choice [:A] [:B]]]))

(deftest choiche-test
  (testing "choice test"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (gml-to-featured.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :choice (clojure.core/or (gml-to-featured.xml/xml1-> zp :A gml-to-featured.xml/text)
                               (gml-to-featured.xml/xml1-> zp :B gml-to-featured.xml/text))})))
         (macroexpand or-choice)))))

(def tag-replacement
  `(deftranslator :new
     [[:target-key :s/tag]]))

(deftest tag-replacement-test
  (testing "replace function tags"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (gml-to-featured.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :target-key (gml-to-featured.xml/xml1-> zp gml-to-featured.xml/tag)})))
         (macroexpand tag-replacement)))))

(def function-append
  `(deftranslator :new
     [[:target-key 'moment]]))


(deftest function-append-test
  (testing "append function"
    (is (=
         '(fn* ([feature]
                (clojure.core/let [zp (if (gml-to-featured.code/zip? feature)
                                        feature
                                        (clojure.zip/xml-zip feature))]
                  {:_action :new,
                   :target-key (clojure.core/->
                                (gml-to-featured.xml/xml1-> zp :targetKey gml-to-featured.xml/text)
                                gml-to-featured.code/moment)})))
         (macroexpand function-append)))))
