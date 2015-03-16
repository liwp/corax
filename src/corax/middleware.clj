(ns corax.middleware
  (:require [corax.core :as err]))

(defn- report-error
  [request exception error-reporter]
  (-> (err/exception exception)
      (err/http request)
      (err/report error-reporter)))

(defn wrap-exception-reporting
  "Report any request handling exceptions to Sentry.

  `error-reporter` is an implementation of
  `corax.error-reporte/ErrorReporter`,
  i.e. `corax.error-reporter/CoraxErrorReporter` in most cases.

  Note: this middleware will not produce a ring response when an error
  occurs. Instead it will rethrow the original exception. The
  application should wrap another ring middleware around this one,
  which will catch all exceptions and return ring responses
  with, e.g., `:status 500`."
  [handler error-reporter]
  (fn [req]
    (try
      (handler req)
      (catch Throwable e
        (report-error req e error-reporter)
        (throw e)))))
