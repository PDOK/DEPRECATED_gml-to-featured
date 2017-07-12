(ns gml-to-featured.config
  (:require [clojure.stacktrace]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [environ.core :as environ])
  (:import (java.io Reader)
           (java.util Properties)))

(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread throwable]
      (log/error throwable "Stacktrace:"
                 (print-str (clojure.stacktrace/print-stack-trace throwable))))))

(defn- keywordize [s]
  (-> (str/lower-case s)
      (str/replace "_" "-")
      (str/replace "." "-")
      (keyword)))

(defn load-props [resource-file]
  (with-open [^Reader reader (io/reader (io/resource resource-file))]
    (let [props (Properties.)
          _ (.load props reader)]
      (into {} (for [[k v] props
                     ;; no mustaches, for local use
                     :when (not (re-find #"^\{\{.*\}\}$" v))]
                 [(keywordize k) v])))))

(defonce env
         (merge environ/env
                (load-props "plp.properties")))

(defn create-url [path]
  (let [fully-qualified-domain-name (or (env :fully-qualified-domain-name) "localhost")
        port (or (env :port) "4000")
        context-root (or (env :context-root) nil)]
    (str "http://" fully-qualified-domain-name ":" port "/" context-root (when context-root "/") path)))

(defn create-workers [factory-f]
  (let [n-workers (read-string (or (env :n-workers) "2"))]
    (dorun (for [i (range 0 n-workers)]
             (factory-f i)))))

(def store-dir
  (let [fqdn (or (env :fully-qualified-domain-name) "localhost")
        s (System/getProperty "file.separator")
        path (io/file (str (System/getProperty "java.io.tmpdir") s "gml-to-featured" s fqdn s ))]
    path))

(def cleanup-threshold
  (read-string (or (env :cleanup-threshold) "5")))

(def max-json-size
  (read-string (or (env :max-json-size) "50000000")))
