(ns gml-to-featured.zip
  (:import (java.util.zip ZipEntry ZipFile)))

(defn xml-entries [^ZipFile zipfile]
  (filter (fn [^ZipEntry e] (and #(not (.isDirectory e))
                                 (or (.endsWith (.getName e) "xml")
                                     (.endsWith (.getName e) "gml")))) (enumeration-seq (.entries zipfile))))
