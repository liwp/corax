(ns corax.middleware
  (:require [corax.core :as err]))

(defn- report-error
  [request exception opts]
  (-> (err/exception exception)
      (err/http request)
      (err/report opts)))

(defn wrap-exception-reporting
  "Report any request handling exceptions to Sentry.

  `opts` is an options map with :dsn and :log-fn keys. :dsn is
  mandatory, and it is used to specify the Sentry DSN. :log-fn is
  optional and is used to override the default Corax logger (see
  corax.core/report for more details).

  Note: this middleware will not produce a ring response when an error
  occurs. Instead it will rethrow the original exception. The
  application should wrap another ring middleware around this one,
  which will catch all exceptions and return ring responses
  with :status 500."
  [handler {:keys [dsn log-fn] :as opts}]
  (fn [req]
    (try
      (handler req)
      (catch Throwable e
        (report-error req e opts)
        (throw e)))))
