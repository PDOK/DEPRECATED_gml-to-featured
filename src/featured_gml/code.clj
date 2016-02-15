(ns featured-gml.code
  (:require [featured-gml.xml :refer :all]
            [clojure.zip :as zip]))

(defn hyphenated->camel [^String method-name]
  (clojure.string/replace method-name #"-(\w)"
                          #(clojure.string/upper-case (second %1))))

(def key->fn {:s/tag `[tag]
              :s/id-attr `[id-attr]
              :s/inner-xml `[zip/down xml]
              })

(defn parse-selector-vector [key selector]
  (let [;; convert tags to fn, do use them in de xml1-> selector
        key-rest (mapcat #(get key->fn %1 (vector %1)) (take-while #(keyword? %) selector))
        key-replaced? (not= key-rest (take-while #(keyword? %) selector))
        fn-rest (drop-while #(keyword? %) selector)
        transfn (if-not (empty? fn-rest) fn-rest)]
      (if (empty? key-rest)
        `[[~(keyword (hyphenated->camel (name key))) text] ~transfn]
        `[~(into [] (concat key-rest
                            (if key-replaced? [] `[text]))) ~transfn])))

(defn parse-selector [s]
  (if (vector? s)
    (let [[key & rest] s]
      (if (vector? (first rest))
        `[~key ~(into [] (map #(parse-selector-vector key %) rest))]
        `[~key [~(parse-selector-vector key rest)]]))
    `[~s [[[~(keyword (hyphenated->camel (name s))) text] nil]]]))

(defn apply-translator [translators]
  (letfn [(translate [[s f]] (if f
                                  `(-> (xml1-> ~'zp ~@s) ~@f)
                                  `(xml1-> ~'zp ~@s)))]
    (if (> (count translators) 1)
      (let [ts (map translate translators)]
        `(or ~@ts))
      (translate (first translators)))))

(defn translator
  ([action selectors] (translator action [] selectors))
  ([action base selectors]
   (let [translators
         (for [s (concat (eval base) (eval selectors))]
           (parse-selector s))]
     `(fn [~'feature]
        (let [~'zp (zip/xml-zip ~'feature)]
          ~(reduce (fn [acc [k t]] (assoc acc k (apply-translator t)))
                   {:_action action} translators))))))

(defmacro deftranslator
  ([action selectors] (translator action selectors))
  ([action base selectors] (translator action base selectors)))

(defn params [& params]
  (if (first params)
    (into [] params)))

(defn function [name]
  (fn [& parameters]
    [(str "~#" name) (apply params parameters)]))

(def moment (function "moment"))

(defn gml [inner]
  (when inner
    {:type "gml" :gml inner}))
