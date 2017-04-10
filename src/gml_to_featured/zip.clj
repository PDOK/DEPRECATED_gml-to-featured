(ns gml-to-featured.zip
  (:import (java.util.zip ZipEntry ZipInputStream)))

(defn xml-entries
  [^ZipInputStream is]
  (->> is
    (repeat)
    (map #(.getNextEntry ^ZipInputStream %))
    (take-while identity)
    (filter
      (fn [^ZipEntry e]
        (and
          #(not (.isDirectory e))
          (or
            (let [^String name (.getName e)]
              (.endsWith name "xml")
              (.endsWith name "gml"))))))))
