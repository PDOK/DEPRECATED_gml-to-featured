(ns featured-gml.zip-test
  (:require [clojure.test :refer :all]
            [featured-gml.zip :refer :all]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]))

(defn remove-zip-files [dir]
  (doseq [f (file-seq (io/file dir)) :when (.endsWith (.getPath f) ".zip")]
    (io/delete-file f)))

(deftest zip-files-in-directory-test
  "Test if zip-files-in-directory creates 2 zip-files if a directory contains 2 files and 1 directory is its input"
  (let [result (zip-files-in-directory (clojure.java.io/resource "zip-test"))]
    (is (= 2 (count result)))
    (is (some #(= "a.txt.zip" (.getName %)) result))
    (is (some #(= "b.gml.zip" (.getName %)) result)))
  ; Clean up created zip-files
  (remove-zip-files (clojure.java.io/resource "zip-test")))


