(defproject featured-gml "0.2.0-SNAPSHOT"
  :description "gml to featured json conversion lib"
  :url "http://github.so.kadaster.nl/PDOK/featured-gml"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["releases" "http://mvnrepository.so.kadaster.nl:8081/nexus/content/repositories/releases/"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [org.clojure/tools.cli "0.3.3"]
                 [cheshire "5.5.0"]]
  :main ^:skip-aot featured-gml.runner
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
