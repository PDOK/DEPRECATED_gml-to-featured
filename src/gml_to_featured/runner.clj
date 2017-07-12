(ns gml-to-featured.runner
  (:require [gml-to-featured.xml :as xml]
            [gml-to-featured.code :as code]
            [gml-to-featured.config :as config]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.zip :as z]
            [clj-time.format :as f])
  (:gen-class)
  (:import (java.io InputStream OutputStream)
           (javax.xml.stream.events XMLEvent)))

(def ^:dynamic *sequence-selector* identity)
(def ^:dynamic *feature-selector* identity)
(def ^:dynamic *feature-identifier* identity)
(def ^:dynamic *date-formatter* identity)
(def ^:dynamic *translators* {})

(def default-datetime-formatter (f/formatters :date-hour-minute-second-ms))

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
        translated (-> selected-feature second *feature-selector* translator)]
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

  (defn process-stream [^InputStream stream]
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
  
  (defn- partition-by-size [max-size coll]
    (letfn [(partition-id [current-id init-size coll]
              (when-let [head (first coll)]
                (let [item-size (count head)
                      [current-size current-id] (if (< (- max-size item-size) init-size) 
                                                    [item-size (inc current-id)] 
                                                    [(+ init-size item-size) current-id])]
                  (cons
                    current-id
                    (lazy-seq
                      (partition-id
                        current-id
                        current-size
                        (next coll)))))))]
      (->> (map
             vector
             (partition-id 0 0 coll)
             coll)
        (partition-by first)
        (map (partial map second)))))

  (defn process [reader, dataset-name, validity]
    (let [features (process-stream reader)
          features (if validity
                     (map #(assoc % :_validity validity) features)
                     features)]
      (->> features
        (map json/generate-string)
        (partition-by-size config/max-json-size)
        (map
          #(concat
            (cons
              (str "{\"dataset\":\"" dataset-name "\",\n\"features\":[")
              (interpose ",\n" %))
            (list "]}"))))))

  (defn parse-translator-tag [expr]
    (eval (code/translator (:type expr) (:mapping expr) (or (:arrays expr) (constantly false)))))

  (defn parse-translator-nested-tag [mapping]
    (eval (code/translator mapping)))

  (defn parse-multi-tag [mappings]
    (eval (code/multi mappings)))

  (defn todate [datestring]
    (if datestring
      (f/unparse
        default-datetime-formatter
        (f/parse *date-formatter* datestring))))

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

  (defn translate [dataset edn-config validity reader writer-fn]
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
          date-formatter (:config/date-formatter config)
          date-formatter (if date-formatter
                           (f/formatter date-formatter)
                           (f/formatters :date-hour-minute-second-ms))
          feature-selector (:config/feature-selector config)
          feature-selector (if feature-selector
                               (apply comp feature-selector)
                               identity)
          ]
      (binding [*translators* translators
                *sequence-selector* sequence-selector
                *feature-identifier* feature-identifier
                *date-formatter* date-formatter
                *feature-selector* feature-selector]
        (writer-fn (process reader dataset validity)))))

  (defn translate-filesystem [dataset edn-config-location validity in-file out-file-prefix]
    (with-open [reader (io/input-stream in-file)]
      (translate
        dataset
        (slurp edn-config-location)
        validity
        reader
        #(doseq [[idx file] (map-indexed vector %)]
           (let [out-file (str out-file-prefix "-" (->> idx inc (format "%04d")) ".json")]
             (with-open [writer (io/writer out-file :encoding "utf-8")]
               (doseq [fragment file]
                 (.write writer fragment))))))))

  (defn error-msg [errors]
    (str "The following errors occurred while parsing your command:\n\n"
         (string/join \newline errors)))

  (defn exit [status msg]
    (println msg)
    (System/exit status))

  (defn implementation-version []
    (if-let [version (System/getProperty "gml-to-featured.version")]
      version
      (-> ^Class (eval 'gml_to_featured.runner) .getPackage .getImplementationVersion)))

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

