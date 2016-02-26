(ns featured-gml.zip
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log],
            [clj-time.format :as time-format]))

(defn entries [zipfile]
 (lazy-seq
  (if-let [entry (.getNextEntry zipfile)]
   (cons entry (entries zipfile)))))

(defn zip-directory [zip-file dir]
  "Put all files in dir into specified zip-file"
  (with-open [zip (java.util.zip.ZipOutputStream. (io/output-stream zip-file))]
    (doseq [f (file-seq (io/file dir)) :when (.isFile f)]
      (.putNextEntry zip (java.util.zip.ZipEntry. (.getName f)))
      (io/copy f zip)
      (.closeEntry zip))))

(defn zip-file [uncompressed-file]
  "Return zip-file location with zipped content of uncompressed-file"
  (let [compressed-file (io/file (.getParent uncompressed-file) (str (.getName uncompressed-file) ".zip"))]
    (log/debug "Compressing file" (.getName uncompressed-file))
    (with-open [zip (java.util.zip.ZipOutputStream. (io/output-stream compressed-file))]
      (.putNextEntry zip (java.util.zip.ZipEntry. (.getName uncompressed-file)))
      (io/copy uncompressed-file zip)
      (.closeEntry zip)
      compressed-file)))

(defn zip-files-in-directory [dir]
  "Zip each file in a directory separately"
  (doall
    (map #(zip-file %) (filter #(.isFile %) (file-seq (io/file dir))))))
