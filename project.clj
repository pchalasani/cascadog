(defproject pchalasani/cascadog "0.1.0"
  :description "Cascalog tamed so it complains less, like your dog"
  :url "https://github.com/pchalasani/cascadog"
  :scm {:name "git"
        :url "https://github.com/pchalasani/cascadog"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ["-Xmx768m"
             "-server"
             "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
  :source-paths ["src"]
  :repositories {"conjars" "http://conjars.org/repo"}
  :exclusions [log4j/log4j org.slf4j/slf4j-log4j12]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cascalog/cascalog-core "2.1.1"]
                 [org.clojure/tools.macro "0.1.2"]
                 [log4j "1.2.16"]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]
                 ]
  :profiles {:1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :provided {:dependencies [[org.apache.hadoop/hadoop-core "1.2.1"]]}
             :dev {:resource-paths ["dev"]
                   :plugins [[lein-midje "3.1.3"]]
                   :dependencies  [[cascalog/midje-cascalog "2.1.1"]]}})
