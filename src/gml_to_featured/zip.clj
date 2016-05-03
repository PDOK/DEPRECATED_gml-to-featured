(ns gml-to-featured.zip
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (java.util.zip ZipOutputStream ZipFile ZipEntry)))

(defn xml-entries [^ZipFile zipfile]
  (filter (fn [e] (and #(not (.isDirectory e))
                       (or (.endsWith (.getName e) "xml")
                           (.endsWith (.getName e) "gml")))) (enumeration-seq (.entries zipfile))))

(defn zip-file [uncompressed-file]
  "Return zip-file location with zipped content of uncompressed-file"
  (let [compressed-file (io/file (.getParent uncompressed-file) (str (.getName uncompressed-file) ".zip"))]
    (log/debug "Compressing file" (.getName uncompressed-file))
    (with-open [zip (ZipOutputStream. (io/output-stream compressed-file))]
      (.putNextEntry zip (ZipEntry. (.getName uncompressed-file)))
      (io/copy uncompressed-file zip)
      (.closeEntry zip)
      compressed-file)))
