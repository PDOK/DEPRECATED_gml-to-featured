(def version (slurp "VERSION"))
(def artifact-name (str "gml-to-featured-" version))
(def uberjar-name (str artifact-name "-standalone.jar"))
(def webjar-name (str artifact-name "-web.jar"))
(def uberwar-name (str artifact-name ".war"))
(def git-ref (clojure.string/replace (:out (clojure.java.shell/sh "git" "rev-parse" "HEAD"))#"\n" "" ))

(defproject gml-to-featured version
  :min-lein-version "2.5.4"
  :manifest {"Implementation-Version" ~(str version "(" git-ref ")")}
  :description "gml to featured json conversion lib"
  :url "http://github.so.kadaster.nl/PDOK/gml-to-featured"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["local" "file:repo"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.11.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/core.async "0.2.374"]
                 [cheshire "5.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.5.1"]
                 [prismatic/schema "0.4.3"]
                 [clj-http "2.2.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [log4j/log4j "1.2.17"]
                 [environ "1.0.3"]]
  :main ^:skip-aot gml-to-featured.runner
  :plugins [[lein-ring "0.9.7"]
            [lein-filegen "0.1.0-SNAPSHOT"]]
  :ring {:port 4000
         :uberwar-name ~uberwar-name
         :handler gml-to-featured.api/app}
  :filegen [{:data ~(str version "(" git-ref ")")
             :template-fn #(str %1)
             :target "resources/version"}]
  :profiles {:uberjar {:aot :all}
             :cli {:uberjar-name ~uberjar-name
                   :aliases {"build" ["do" "uberjar"]}}
             :web-war {:aliases {"build" ["do" "filegen" ["ring" "uberwar"]]}}
             :web-jar {:uberjar-name ~webjar-name
                       :aliases {"build" ["do" "filegen" ["ring" "uberjar"]]}}
             :test {:dependencies [[ring/ring-mock "0.3.0"]]}})
