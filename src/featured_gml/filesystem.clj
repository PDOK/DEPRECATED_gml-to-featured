(ns featured-gml.filesystem
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [clj-time.core :as time]
            [clj-time.format :as time-format]))

(def default-attributes (make-array java.nio.file.attribute.FileAttribute 0))

(def resultstore
  (let [path (or (env :featured-gml.jsonstore) (System/getProperty "java.io.tmpdir")),
        separator (System/getProperty "file.separator")]
    (if-not (.endsWith path separator)
      (str path separator)
      path)))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn safe-delete [file-path]
  (log/debug "Going to delete" file-path)
  (if (.exists (io/file file-path))
    (try
      (clojure.java.io/delete-file file-path)
      (catch Exception e (str "exception: " (.getMessage e))))
    false))

(defn delete-directory [directory-path]
  (let [directory-contents (file-seq (io/file directory-path))
        files-to-delete (filter #(.isFile %) directory-contents)]
    (doseq [file files-to-delete]
      (safe-delete (.getPath file)))
    (safe-delete directory-path)))

(defn delete-files [files]
  (map #(safe-delete %) files))

(defn get-tmp-dir []
  (.toFile (java.nio.file.Files/createTempDirectory "xml2json" default-attributes)))

(def custom-formatter
   (time-format/with-zone
     (time-format/formatter "yyyyMMddHHmm")
     (time/default-time-zone)))

(def time-now
  (time-format/unparse custom-formatter (time/now)))

(defn determine-store-location [uuid]
  (str resultstore uuid))

(defn target-file [storedir inputname]
  (io/file storedir (str time-now "_" inputname ".json")))
