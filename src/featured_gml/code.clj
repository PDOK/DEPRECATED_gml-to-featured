(ns featured-gml.code
  (:require [featured-gml.xml :refer :all]
            [clojure.zip :as zip]))

(defn hyphenated->camel [^String method-name]
  (clojure.string/replace method-name #"-(\w)"
                          #(clojure.string/upper-case (second %1))))

(def key->fn {:s/tag `[tag]
              :s/id-attr `[id-attr]
              :s/inner-gml `[inner-gml]})

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
      (cond
        (vector? (first rest))
         `[~key ~(into [] (map #(parse-selector-vector key %) rest))]
        (fn? (first rest))
          `[~key ~(first rest)]
        :else
        `[~key [~(parse-selector-vector key rest)]]))
    `[~s [[[~(keyword (hyphenated->camel (name s))) text] nil]]]))

(defn apply-translator [translators]
  (letfn [;f is a list of functions and will be executed on the selection (s); example f=s/uppercase
          (translate [[s f]] (if f
                               (if (and (= 1 (count f)) (string? (first f)))
                                 (first f)
                                 `(-> (xml1-> ~'zp ~@s) ~@f))
                               `(xml1-> ~'zp ~@s)))]
    (cond
      (fn? translators)
       `(~translators ~'feature)
      (> (count translators) 1)
        (let [ts (map translate translators)]
          `(or ~@ts))
      :else
        (translate (first translators)))))

(defn translator
  ([selectors] (translator nil selectors))
  ([action selectors] (translator action [] selectors))
  ([action base selectors]
   (let [translators
         (for [s (concat (eval base) (eval selectors))]
           (parse-selector s))]
     `(fn [~'feature]
        (let [~'zp (zip/xml-zip ~'feature)]
          ~(reduce (fn [acc [k t]] (assoc acc k (apply-translator t)))
                     (if action {:_action action} {}) translators))))))

(defn multi [mappings]
  (apply juxt mappings))

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

(defn inner-gml [input]
  (let [inner (zip/down input)]
    (when inner
      {:type "gml" :gml (xml inner)})))
