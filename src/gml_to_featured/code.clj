(ns gml-to-featured.code
  (:require [gml-to-featured.xml :refer :all]
            [clojure.zip :as zip]))

(defn hyphenated->camel [^String method-name]
  (clojure.string/replace method-name #"-(\w)"
                          #(clojure.string/upper-case (second %1))))

(defn add-meta [obj m]
  (let [current-meta (meta obj)]
    (with-meta obj (merge current-meta m))))

(defn zip? [obj]
  (:zip? (meta obj)))

(def key->fn {:s/tag `[tag]
              :s/id-attr `[id-attr]
              :s/inner-gml `[inner-gml]
              :s/geometry `[geometry]})

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

(defn apply-translator [multi? translators]
  (let [select-> (if multi? `xmlN-> `xml1->)]
    (letfn [;f is a list of functions and will be executed on the selection (s); example f=s/uppercase
            (translate [[s f]] (if f
                                 (if (and (= 1 (count f)) (string? (first f)))
                                   (first f)
                                   `(-> (~select-> ~'zp ~@s) ~@f))
                                 `(~select-> ~'zp ~@s)))]
      (cond
        (fn? translators)
        `(~translators ~'feature)
        (> (count translators) 1)
        (let [ts (map translate translators)]
          `(or ~@ts))
        :else
        (translate (first translators))))))

(defn translator
  ([selectors] (translator nil selectors (constantly false)))
  ([selectors fn-multi-element?] (translator nil selectors fn-multi-element?))
  ([action selectors fn-multi-element?] (translator action [] selectors fn-multi-element?))
  ([action base selectors fn-multi-element?]
   (let [translators
         (for [s (concat (eval base) (eval selectors))]
           (parse-selector s))]
     `(fn [~'feature]
        (let [~'zp (if (zip? ~'feature)
                     ~'feature
                     (zip/xml-zip ~'feature))]
          ~(reduce (fn [acc [k t]] (assoc acc k (apply-translator ((eval fn-multi-element?) k) t)))
                     (if action {:_action action} {}) translators))))))

(defn multi [mappings]
  (fn [feature]
    (let [zipper (add-meta (zip/xml-zip feature) {:zip? true})]
      ((apply juxt mappings) zipper))))

(defmacro deftranslator
  ([action selectors] (translator action selectors (constantly false)))
  ([action selectors fn-multi-element?] (translator action selectors fn-multi-element?))
  ([action base selectors fn-multi-element?] (translator action base selectors fn-multi-element?)))

(defn params [& params]
  (if (first params)
    (into [] params)))

(defn function [name]
  (fn [& parameters]
    ; use double vector --> outer vector is used as container (edn-mapping)
    [[(str "~#" name) (apply params parameters)]]))

(def moment (function "moment"))

(def geometry (function "geometry"))

(defn inner-gml [input]
  (let [inner (zip/down input)]
    (when inner
      {:type "gml" :gml (xml inner)})))