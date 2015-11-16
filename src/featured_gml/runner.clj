(ns featured-gml.runner
  (:require [featured-gml.xml :as xml]
            [cheshire.core :as json]))

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
  (shutdown-agents))
