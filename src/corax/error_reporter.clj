(ns corax.error-reporter
  (:require [cheshire.core :as json]
            [corax.exception :refer [build-exception-value]]
            [corax.log :as log]
            [raven-clj.core :as raven])
  (:import [com.fasterxml.jackson.core JsonGenerationException]
           [java.text SimpleDateFormat]
           [java.util Date TimeZone]))

(defn- default-event-values
  "These are the default value that the user can override via the
  event map."
  [timestamp]
  {:level :error
   :platform :clojure
   :timestamp timestamp})

(defn- utc
  "Returns the current or the provided UTC time as an ISO 8601 format string."
  ([] (utc (Date.)))
  ([date]
   (let [fmt (doto (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss")
               (.setTimeZone (TimeZone/getTimeZone "UTC")))]
     (.format fmt date))))

(defprotocol ErrorReporter
  (-report [this event]))

(defn report* [event dsn logger]
  (let [event (merge (default-event-values (utc)) event)
        response (raven/capture dsn event)]
    (if (= (:status response) 200)
      (let [id (-> response :body (json/parse-string true) :id)]
        (log/log-event-id logger id event))
      (log/log-failure logger response event))))

(defn report-json-generation-error [exception dsn logger]
  (let [value (build-exception-value exception)
        event {:message
               (str "An object in the error report could not be "
                    "serialized to JSON. This error is masking "
                    "the real application error. For more details "
                    "see https://github.com/listora/corax")
               :exception {:values [value]}}]
    (report* event dsn logger)))

(defrecord CoraxErrorReporter [dsn logger]
  ErrorReporter
  (-report [this event]
    (try
      (try
        (report* event dsn logger)
        (catch JsonGenerationException e
          (report-json-generation-error e dsn logger)))
      (catch Exception e
        (log/log-error logger e event)))))
