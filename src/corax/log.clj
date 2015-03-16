(ns corax.log
  (:require [clj-stacktrace.repl :refer [pst-str]]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

(defprotocol Logger
  (log-event-id [this sentry-id event]
    "Called when the event has been reported successfully to
    Sentry. `sentry-id` is the Sentry-assigned ID of the event.")

  (log-failure [this http-response event]
    "Called when the event reporting failed and Sentry returned an
    error. `http-response` is the non-200 HTTP response returned by
    Sentry.")

  (log-error [this exception event]
    "Called when the event reporting failed with an
    exception. `exception` is the exception that was thrown."))

(defn event->str
  "Pretty-prints the event and returns the output as a string (nothing
  is printed to stdout)."
  [event]
  (with-out-str (pprint event)))

(defn- print-event [event]
  (println "Event:" (event->str event)))

(defrecord CoraxLogger []
  Logger
  (log-event-id [this sentry-id event]
    (println "Corax - Sentry event ID:" sentry-id)
    (print-event event))

  (log-failure [this http-response event]
    (println "Corax - Sentry returned an error")
    (pprint http-response)
    (print-event event))

  (log-error [this exception event]
    (println "Corax - Unexpected exception when reporting event")
    (println (pst-str exception))
    (print-event event)))
