(ns featured-gml.runner
  (:require [featured-gml.xml :as xml]
            [featured-gml.code :as code]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
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

(defn process [reader writer dataset-name validity]
  (let [first? (ref true)]
    (.write writer (str "{\"dataset\":\"" dataset-name "\",\n\"features\":["))
    (doseq [f (process-stream reader)]
      (if-not @first?
        (.write writer ",\n")
        (dosync (ref-set first? false)))
      (.write writer (json/generate-string (assoc f :_validity validity))))
    (.write writer "]}")))

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
   {:readers {'xml2json/mappedcollection parse-translator-tag
              'xml2json/fn parse-fn-tag}} config))

(defn translate [dataset edn-config validity reader writer]
  (let [translators (parse-config edn-config)]
    (binding [*translators* translators]
      (process reader writer dataset validity))))

(defn translate-filesystem [dataset edn-config-location validity in-file out-file]
  (with-open [reader (clojure.java.io/reader in-file),
              writer (clojure.java.io/writer out-file)]
    (translate dataset (slurp edn-config-location) validity reader writer))
  (shutdown-agents))

;; "{:Gemeenten #pdok/translator {:type :new :mapping
;;                    [[:_collection :s/tag clojure.string/lower-case]
;;                     :Code
;;                     :Gemeentenaam]}}"

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn implementation-version []
(->> "project.clj"
     slurp
     read-string
     (drop 2)
     (cons :version)
     (apply hash-map)
     (:version)))

(defn usage [options-summary]
  (->> ["This program converts xml to featured-ready json. The conversion is done using the provided mappingconfig(uration) specified in edn."
        ""
        "Usage: xml2featured [options] datasetname mappingconfig validity inputxml outputfile "
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(def cli-options
  [["-h" "--help"]
   ["-v" "--version"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
       (:version options) (exit 0 (implementation-version))
      (not= (count arguments) 4) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (if (= 5 (count args))
     (translate-filesystem (nth args 0) (nth args 1) (nth args 2) (nth args 3) (nth args 4))
     (exit 1 (usage summary)))))
