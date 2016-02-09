(ns featured-gml.runner
  (:require [featured-gml.xml :as xml]
            [featured-gml.code :as code]
            [clojure.edn :as edn]
            [cheshire.core :as json])
  (:gen-class))

(def ^:dynamic *sequence-selector* :content)
(def ^:dynamic *feature-selector* #(-> % :content first))
(def ^:dynamic *translators* {})

(def unknown nil)

(defn unknown-translator [_]
  unknown)

(defn member->map [fm]
  (let [feature (*feature-selector* fm)
        translator (get *translators* (:tag feature) unknown-translator)]
    (translator feature)))

(defn process-stream [stream]
  (->> stream
       (.createXMLEventReader xml/input-factory)
       (xml/pull-seq)
       xml/event->tree
       *sequence-selector*
       (pmap member->map)
       (filter #(not= unknown %))))

(defn process [in out dataset-name]
  (let [first? (ref true)]
    (with-open [reader (clojure.java.io/reader in)
                writer (clojure.java.io/writer out)]
      (.write writer (str "{\"dataset\":\"" dataset-name "\",\n\"features\":["))
      (doseq [f (process-stream reader)]
        (if-not @first?
          (.write writer ",\n")
          (dosync (ref-set first? false)))
        (.write writer (json/generate-string f)))
      (.write writer "]}")))
  (shutdown-agents)
  )

(defn parse-translator-tag [expr]
  (eval (code/translator (:type expr) (:mapping expr))))

(defn resolve-as-function [namespace function]
  (ns-resolve *ns* (symbol (str namespace "/" (name function)))))

(defn parse-fn-tag [kw]
  (if-let [f (resolve-as-function "featured-gml.code" kw)]
    f
    identity))

(defn parse-config [config]
  (edn/read-string
   {:readers {'pdok/translator parse-translator-tag
              'pdok/fn parse-fn-tag}} config))

(defn translate [dataset edn-config in out]
  (let [translators (parse-config (slurp  edn-config))]
    (binding [*translators* translators]
      (process in out dataset))))

;; "{:Gemeenten #pdok/translator {:type :new :mapping
;;                    [[:_collection :s/tag clojure.string/lower-case]
;;                     :Code
;;                     :Gemeentenaam]}}"

(defn -main
  [& args]
  (cond
    (= 3 (count args)) (translate (nth args 0) (nth args 1) (nth args 2) *out*)
    (= 4 (count args)) (translate (nth args 0) (nth args 1) (nth args 2) (nth args 3))
    :else (println "Usage: .exe datasetname config.edn infile [outfile]")))
