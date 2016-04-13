(def feature-version (or (System/getenv "FEATURE_GML_VERSION") "0.1"))
(def build-number (or (System/getenv "BUILD_NUMBER") "HANDBUILT"))
(def change-number (or (System/getenv "CHANGE_NUMBER") "031415"))
(def release-version (str feature-version "." build-number))
(def project-name "featured-gml")
(def uberjar-name (str project-name "-standalone.jar"))
(def uberwar-name (str project-name ".war"))
(def git-ref (clojure.string/replace (:out (clojure.java.shell/sh "git" "rev-parse" "HEAD"))#"\n" "" ))

(create-ns 'pdok.lein)
(defn key->placeholder [k]
  (re-pattern (str "\\$\\{" (name k) "\\}")))

(defn generate-from-template [template-file replacement-map]
  (let [template (slurp template-file)
        replacements (map (fn [[k v]] [(key->placeholder k) (str v)]) replacement-map)]
    (reduce (fn [acc [k v]] (clojure.string/replace acc k v)) template replacements)))

(intern 'pdok.lein 'key->placeholder key->placeholder)
(intern 'pdok.lein 'generate-from-template generate-from-template)

(defproject featured-gml release-version
  :uberjar-name ~uberjar-name
  :manifest {"Implementation-Version" ~(str release-version "(" git-ref ")")}
  :description "gml to featured json conversion lib"
  :url "http://github.so.kadaster.nl/PDOK/featured-gml"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["local" "file:repo"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.9.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/core.async "0.2.374"]
                 [cheshire "5.5.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.4.0"]
                 [prismatic/schema "0.4.3"]
                 [http-kit "2.1.18"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.17"]
                 [environ "0.5.0"]]
  :main ^:skip-aot featured-gml.runner
  :plugins [[lein-ring "0.9.7"]
            [lein-filegen "0.1.0-SNAPSHOT"]]
  :ring {:port 4000
	 :handler featured-gml.api/app
         :uberwar-name ~uberwar-name}
  :profiles {:uberjar {:aot :all}
             :test {:dependencies [[ring/ring-mock "0.3.0"]]}}
  :aliases {"build" ["do" ["clean"] ["compile"] ["test"] ["filegen"]
                      ["ring" "uberwar"]]}
  :filegen [{:data {:RELEASE_VERSION ~release-version :CHANGE_NUMBER ~change-number}
             :template-fn (partial pdok.lein/generate-from-template "deployit-manifest.xml.template")
             :target "target/deployit-manifest.xml"}
            {:data ~release-version
             :template-fn #(str "FEATURED_GML_VERSION=" %1 "\n")
             :target "target/featured-gml.properties"}
            {:data ~(str release-version "(" git-ref ")")
             :template-fn #(str %1)
             :target "resources/version"}])
