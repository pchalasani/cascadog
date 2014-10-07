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
  :javac-options ["-target" "1.6" "-source" "1.6"]
  :aot [cascadog.core]
  :main cascadog.core
  :source-paths ["src"]
  :repositories {"conjars" "http://conjars.org/repo"}
  :exclusions [log4j/log4j org.slf4j/slf4j-log4j12]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [cascalog/cascalog-core "2.1.1"]
                 [org.clojure/tools.macro "0.1.2"]
                 [log4j "1.2.16"]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]
                 [cascading/cascading-hadoop "2.5.3"
                  :exclusions [org.codehaus.janino/janino
                               org.apache.hadoop/hadoop-core]]
                 [com.twitter/chill-hadoop "0.3.5"]
                 [com.twitter/carbonite "1.4.0"]
                 [com.taoensso/nippy "2.6.3"]  ;; serialization
                 [com.twitter/maple "0.2.2"]
                 [jackknife "0.1.7"]
                 [hadoop-util "0.3.0"]]
  :profiles {
             ;; :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             ;; :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             ;; :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :provided {:dependencies [[org.apache.hadoop/hadoop-core "1.2.1"]]}
             :dev {:resource-paths ["dev"]
                   :plugins [[lein-midje "3.1.3"]]
                   :dependencies
                   [[cascalog/midje-cascalog "2.1.1"]
                    [org.apache.hadoop/hadoop-core "1.2.1"]
                    ]}})
