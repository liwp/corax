(ns corax.error-reporter
  (:require [cheshire.core :as json]
            [clj-stacktrace.repl :refer [pst-str]]
            [clojure.pprint :refer [pprint]]
            [raven-clj.core :as raven])
  (:import [com.fasterxml.jackson.core JsonGenerationException]))

(def ^:const error-messages
  {:exception "Unexpected exception."
   :http-status "Unexpected HTTP status."
   :invalid-dsn "DSN parsing failed."
   :invalid-payload "Event cannot be serialized to JSON."
   :no-dsn "A sentry DSN was not provided. Cannot report event."})

(defn- default-log-fn
  [{:keys [dsn error event exception id response]}]
  (let [event-str (with-out-str (pprint event))]
    (if id
      (println "Sentry error report ID: " id)
      (let [message (get error-messages error (str "Unknown error: " error))]
        (println message)
        (when dsn
          (println "DSN: " dsn))
        (when exception
          (println (pst-str exception)))
        (when response
          (pprint response))))
    (println "Event:" event-str)))

(defn- handle-event
  [event dsn log-fn]
  (try
    (let [rsp (raven/capture dsn event)]
      (if (= (:status rsp) 200)
        (log-fn {:id (-> rsp :body (json/parse-string true) :id)
                 :event event})
        (log-fn {:error :http-status :response rsp :event event})))
    (catch NullPointerException e
      (log-fn {:error :invalid-dsn :dsn dsn :event event}))
    (catch JsonGenerationException e
      (log-fn {:error :invalid-payload :exception e :event event}))
    (catch Throwable e
      (log-fn {:error :exception :exception e :event event})))
  nil)

(defn report
  [event dsn log-fn]
  (let [log-fn (or log-fn default-log-fn)]
    (if dsn
      (handle-event event dsn log-fn)
      (log-fn {:error :no-dsn :event event}))))
