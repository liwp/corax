(defproject listora/corax "0.2.0-SNAPSHOT"
  :description "A layer of sugar on raven-clj"
  :url "https://github.com/liwp/corax"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/liwp/corax"}
  :deploy-repositories [["releases" :clojars]]
  :dependencies [[cheshire "5.3.1"]
                 [clj-stacktrace "0.2.8"]
                 [org.clojure/clojure "1.6.0"]
                 [raven-clj "1.1.0"]])
