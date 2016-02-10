(defproject featured-gml "0.2.0-SNAPSHOT"
  :description "gml to featured json conversion lib"
  :url "http://github.so.kadaster.nl/PDOK/featured-gml"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["releases" "http://mvnrepository.so.kadaster.nl:8081/nexus/content/repositories/releases/"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.9.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.cli "0.3.3"]
                 [cheshire "5.5.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.3.1"]
                 [compojure "1.4.0"]]
  :main ^:skip-aot featured-gml.runner
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler featured-gml.api/app}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
