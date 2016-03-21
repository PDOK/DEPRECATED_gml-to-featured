(ns featured-gml.zip
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (java.util.zip ZipOutputStream ZipEntry)))

(defn entries [zipfile]
 (lazy-seq
  (if-let [entry (.getNextEntry zipfile)]
   (cons entry (entries zipfile)))))

(defn zip-file [uncompressed-file]
  "Return zip-file location with zipped content of uncompressed-file"
  (let [compressed-file (io/file (.getParent uncompressed-file) (str (.getName uncompressed-file) ".zip"))]
    (log/debug "Compressing file" (.getName uncompressed-file))
    (with-open [zip (ZipOutputStream. (io/output-stream compressed-file))]
      (.putNextEntry zip (ZipEntry. (.getName uncompressed-file)))
      (io/copy uncompressed-file zip)
      (.closeEntry zip)
      compressed-file)))
