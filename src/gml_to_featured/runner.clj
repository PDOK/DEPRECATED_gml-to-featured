(ns gml-to-featured.runner
  (:require [gml-to-featured.xml :as xml]
            [gml-to-featured.code :as code]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.zip :as z])
  (:gen-class)
  (:import (javax.xml.stream.events XMLEvent)))

(def ^:dynamic *sequence-selector* identity)
(def ^:dynamic *feature-selector* identity)
(def ^:dynamic *feature-identifier* identity)
(def ^:dynamic *translators* {})

(def unknown nil)

(defn unknown-translator [_]
  unknown)

(defn get-path-and-feature [path-features]
  (for [pf path-features]
    (merge (map #(hash-map (merge (-> pf first) (:tag %1)) %1) (-> pf second :content)))))

(defn get-possible-paths [feature path-depth]
  (let [first-feature (-> feature z/xml-zip first)
        path (:tag first-feature)
        result-firstnode {[path] first-feature}]
    (loop [result result-firstnode
           iter 1]
      (if (>= iter path-depth)
        result
        (let [paths-to-proces (filter #(= (count %) iter) (keys result))
              result (merge result
                            (into {} (-> result
                                         (#(select-keys % paths-to-proces))
                                         get-path-and-feature
                                         flatten)))]
          (recur result (inc iter)))))))

(defn member->map [fm]
  (let [feature (*feature-identifier* fm)
        path-depth (-> *translators*
                        keys
                       ((partial map count))
                       ((partial apply max)))
        possible-paths-feature (get-possible-paths feature path-depth)
        selected-feature (-> *translators*
                             keys
                             (#(select-keys possible-paths-feature %))
                             first)
        translator (get *translators* (first selected-feature) unknown-translator)
        translated (-> selected-feature second *feature-selector* translator)
        ]
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
    (let [log-progress (progress-logger)
          sequence-selector (if (nil? *sequence-selector*) identity *sequence-selector*)]
      (->> stream
           (.createXMLEventReader xml/input-factory)
           (xml/pull-seq)
           sequence-selector
           xml/event->tree
           :content
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
        (if validity
          (.write writer (json/generate-string (assoc f :_validity validity)))
          (.write writer (json/generate-string f))))
      (.write writer "]}")))

  (defn parse-translator-tag [expr]
    (eval (code/translator (:type expr) (:mapping expr))))

  (defn parse-translator-nested-tag [mapping]
    (eval (code/translator mapping)))

  (defn parse-multi-tag [mappings]
    (eval (code/multi mappings)))

 (def fns {'first clojure.core/first})

 (defn upgrade-comp [function-vector]
   (map (fn [symbol] (get fns symbol symbol)) function-vector))

(defn get-path [filter-vector]
  filter-vector)

  (defn parse-config [config]
    (edn/read-string
      {:readers {'xml2json/mappedcollection parse-translator-tag ;this reader-tag is deprecated
                 'xml2json/mapped           parse-translator-tag
                 'xml2json/nested           parse-translator-nested-tag
                 'xml2json/multi            parse-multi-tag
                 'xml2json/comp             upgrade-comp
                 'xml2json/path             get-path}} config))

  (defn start-element-pred [element-name]
    (fn [^XMLEvent e]
      (and (.isStartElement e)
           (= element-name (keyword (.getLocalPart (.getName (.asStartElement e))))))))

  (defn translate [dataset edn-config validity reader writer]
    (let [config (parse-config edn-config)
          translators (if-let [t (:config/translators config)] t config)
          sequence-element (:config/sequence-element config)
          sequence-selector (if sequence-element
                              (partial drop-while (comp not (start-element-pred sequence-element)))
                              identity)
          feature-identifier (:config/feature-identifier config)
          feature-identifier (if feature-identifier
                             (apply comp feature-identifier)
                             identity)
          feature-selector (:config/feature-selector config)
          feature-selector (if feature-selector
                               (apply comp feature-selector)
                               identity)
          ]
      (binding [*translators* translators
                *sequence-selector* sequence-selector
                *feature-identifier* feature-identifier
                *feature-selector* feature-selector]
        (process reader writer dataset validity))))

  (defn translate-filesystem [dataset edn-config-location validity in-file out-file]
    (with-open [reader (clojure.java.io/reader in-file),
                writer (clojure.java.io/writer out-file)]
      (translate dataset (slurp edn-config-location) validity reader writer))
    (shutdown-agents))

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
          "Usage: xml2featured [options] datasetname mappingconfig inputxml outputfile "
          ""
          "Options:"
          options-summary
          ""
          "Please refer to the manual page for more information."]
         (string/join \newline)))

  (def cli-options
    [["-h" "--help"]
     ["-v" "--version"]
     [nil "--validity VALIDITY" "Sets validity [date-time-string] globally for all features"]])

  (defn -main [& args]
    (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
      ;; Handle help and error conditions
      (cond
        (:help options) (exit 0 (usage summary))
        (:version options) (exit 0 (implementation-version))
        (not= (count arguments) 4) (exit 1 (usage summary))
        errors (exit 1 (error-msg errors)))
      ;; Execute program with options
      (translate-filesystem (nth arguments 0) (nth arguments 1) (:validity options) (nth arguments 2) (nth arguments 3))))
