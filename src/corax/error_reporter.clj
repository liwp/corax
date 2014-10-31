(ns corax.error-reporter
  (:require [cheshire.core :as json]
            [clj-stacktrace.repl :refer [pst-str]]
            [clojure.pprint :refer [pprint]]
            [raven-clj.core :as raven]))

(def ^:const error-messages
  {:exception "Unexpected exception."
   :http-status "Unexpected HTTP status."
   :no-dsn "A sentry DSN was not provided. Cannot report event."
   })

(defn- default-log-fn
  [{:keys [error event exception response]}]
  (let [event-str (with-out-str (pprint event))
        message (get error-messages error (str "Unknown error: " error))]
    (println message)
    (when exception
      (println (pst-str exception)))
    (when response
      (pprint response))
    (println "Event:" event-str)))

(defn- handle-event
  [event dsn log-fn]
  (try
    (let [rsp (raven/capture dsn event)]
      (if (= (:status rsp) 200)
        (-> rsp :body (json/parse-string true) :id)
        (do
          (log-fn {:error :http-status :response rsp :event event})
          nil)))
    (catch Throwable e
      (log-fn {:error :exception :exception e :event event})
      nil)))

(defn report
  [event dsn log-fn]
  (let [log-fn (or log-fn default-log-fn)]
    (if dsn
      (handle-event event dsn log-fn)
      (log-fn {:error :no-dsn :event event}))))
