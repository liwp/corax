(defproject listora/corax "0.2.0"
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
                 [raven-clj "1.1.0"]]

  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]]

                   :plugins [[jonase/eastwood "0.1.4"]
                             [listora/whitespace-linter "0.1.0"]]

                   :eastwood {:exclude-linters [:deprecations :unused-ret-vals]}

                   :aliases {"ci" ["do" ["test"] ["lint"]]
                             "lint" ["do" ["whitespace-linter"] ["eastwood"]]}}})
