(ns corax.error-reporter
  (:require [clj-stacktrace.repl :refer [pst-str]]
            [clojure.pprint :refer [pprint]]
            [raven-clj.core :as raven]))

(def ^:const error-messages
  {:no-dsn "A sentry DSN was not provided. Cannot report event."
   :exception "Unexpected exception."})

(defn- default-log-fn
  [{:keys [error event exception]}]
  (let [event-str (with-out-str (pprint event))
        message (get error-messages error (str "Unknown error: " error))]
    (println message)
    (when exception
      (println (pst-str exception)))
    (println "Event:" event-str)))

(defn- handle-event
  [event dsn log-fn]
  (try
    (raven/capture dsn event)
    (catch Throwable e
      (log-fn {:error :exception :exception e :event event}))))

(defn report
  [event dsn log-fn]
  (let [log-fn (or log-fn default-log-fn)]
    (if dsn
      (handle-event event dsn log-fn)
      (log-fn {:error :no-dsn :event event}))))
