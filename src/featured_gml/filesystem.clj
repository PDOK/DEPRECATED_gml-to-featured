(ns featured-gml.filesystem
  (:require [clojure.java.io :as io]
            [environ.core :refer [env]]))

(def default-attributes (make-array java.nio.file.attribute.FileAttribute 0))

(def resultstore
  (let [path (or (env :jsonstore) (System/getProperty "java.io.tmpdir")),
        separator (System/getProperty "file.separator")]
    (if-not (.endsWith path separator)
      (str path separator)
      path)))

(def uuid
  (str (java.util.UUID/randomUUID)))

(defn safe-delete [file-path]
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

(def get-tmp-dir
  (.toFile (java.nio.file.Files/createTempDirectory "xml2json" default-attributes)))

(defn determine-zip-name [uuid]
  (str resultstore uuid ".zip"))
