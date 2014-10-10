(defproject pchalasani/cascadog "0.1.6"
  :description "Cascalog tamed so it complains less, like your dog"
  :url "https://github.com/pchalasani/cascadog"
  :scm {:name "git"
        :url "https://github.com/pchalasani/cascadog"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/" "test/"]
  :repositories [["conjars" "http://conjars.org/repo"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cascalog/cascalog-core "2.1.1"]
                 [org.apache.hadoop/hadoop-core "1.2.1"]]

  :profiles {
             :provided
             {:dependencies [[org.apache.hadoop/hadoop-core "1.2.1"]]}
             :dev {:resource-paths ["dev"]
                   :plugins [[lein-midje "3.1.3"]]
                   :dependencies
                   [[cascalog/midje-cascalog "2.1.1"]]}}

)
