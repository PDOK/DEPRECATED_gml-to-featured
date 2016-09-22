(ns gml-to-featured.runner
  (:require [gml-to-featured.xml :as xml]
            [gml-to-featured.code :as code]
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
        translator (get *translators* (:tag feature) unknown-translator)
        translated (translator feature)]
    (if (sequential? translated)
      translated
      [translated])))

(defn progress-logger []
  (let [counter (volatile! 0)]
    (fn [obj]
      (vswap! counter inc)
      (when (= 0 (mod @counter 10000))
        (log/info "Processed " @counter))
      obj)))

(defn process-stream [stream]
  (let [log-progress (progress-logger)]
    (->> stream
         (.createXMLEventReader xml/input-factory)
         (xml/pull-seq)
         xml/event->tree
         *sequence-selector*
         (mapcat member->map)
         (map log-progress)
         (filter #(not= unknown %)))))

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

(defn parse-translator-nested-tag [mapping]
  (eval (code/translator mapping)))

(defn parse-multi-tag [mappings]
  (eval (code/multi mappings)))

(defn parse-config [config]
  (edn/read-string
    {:readers {'xml2json/mappedcollection parse-translator-tag ;this reader-tag is deprecated
               'xml2json/mapped           parse-translator-tag
               'xml2json/nested           parse-translator-nested-tag
               'xml2json/multi parse-multi-tag}} config))

(defn translate [dataset edn-config validity reader writer]
  (let [translators (parse-config edn-config)]
    (binding [*translators* translators]
      (process reader writer dataset validity))))

(defn translate-filesystem [dataset edn-config-location validity in-file out-file]
  (with-open [reader (clojure.java.io/reader in-file),
              writer (clojure.java.io/writer out-file)]
    (translate dataset (slurp edn-config-location) validity reader writer)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn implementation-version []
  (if-let [version (System/getProperty "gml-to-featured.version")]
    version
    (-> ^java.lang.Class (eval 'gml_to_featured.runner) .getPackage .getImplementationVersion)))

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
      (not= (count arguments) 5) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (if (= 5 (count args))
     (translate-filesystem (nth args 0) (nth args 1) (nth args 2) (nth args 3) (nth args 4))
     (exit 1 (usage summary)))))
