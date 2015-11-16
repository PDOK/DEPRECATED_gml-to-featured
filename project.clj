(defproject featured-gml "0.1.2"
  :description "gml to featured json conversion lib"
  :url "http://github.so.kadaster.nl/PDOK/featured-gml"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-pprint "1.1.1"]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [cheshire "5.5.0"]]
  :repositories [["snapshots"
                  {:url "http://mvnrepository.so.kadaster.nl:8081/nexus/content/repositories/snapshots/"
                   :username :env/nexus_username
                   :password :env/nexus_password
                   :sign-releases false}]
                 ["releases"
                  {:url "http://mvnrepository.so.kadaster.nl:8081/nexus/content/repositories/releases/"
                   :username :env/nexus_username
                   :password :env/nexus_password
                   :sign-releases false}]]
  :install-releases? false
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :aliases {"build" ["do" ["compile"] ["test"]]}
  :profiles {:test {:resource-paths ["dev-resources"]}
             :dev {:resource-paths ["dev-resources"]}})
