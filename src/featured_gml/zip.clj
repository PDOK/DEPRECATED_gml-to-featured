(ns featured-gml.zip
  (:require [clojure.java.io :as io]))

(defn entries [zipfile]
 (lazy-seq
  (if-let [entry (.getNextEntry zipfile)]
   (cons entry (entries zipfile)))))

(defn zip-directory [zip-file dir]
  (with-open [zip (java.util.zip.ZipOutputStream. (io/output-stream zip-file))]
    (doseq [f (file-seq (io/file dir)) :when (.isFile f)]
      (.putNextEntry zip (java.util.zip.ZipEntry. (.getName f)))
      (io/copy f zip)
      (.closeEntry zip))))


(defn extract-target-file-name [inputname]
  "Extract the target-file name for inputname"
  (str(last (re-find #"(\w+).(?:\w+)$" inputname)) ".json"))

(defn target-file [tmpdir inputname]
  (io/file tmpdir (extract-target-file-name inputname)))
