(ns corax.core
  (:require [clj-stacktrace.core :refer [parse-exception]]
            [raven-clj.core :as raven])
  (:import (java.text SimpleDateFormat)
           (java.util Date TimeZone)))

(defn- truncate-string-to-1000-chars
  [s]
  (if (> (count s) 1000)
    (.substring s 0 1000)
    s))

(defn message
  "Set the :message field in an event. The value should be a
  user-readable representation of this event. Maximum length is 1000
  characters. Longer messages are silently truncated. Eg:
    (message \"The failed to froblify the message.\")
    (message ev \"The failed to froblify the message.\")"
  ([m]
     (message {} m))
  ([event m]
     (let [m (truncate-string-to-1000-chars m)]
       (assoc event :message m))))

(defn messagef
  "Add the Message interface to an event. The params elements will be
  inserted into the fmt string by the server. The result should be a
  user-readable representation of this event. Maximum length of the
  fmt argument is 1000 characters. Longer messages are silently
  truncated. Eg:
    (messagef \"The failed to froblify the message: %s\" msg)
    (messagef ev \"The failed to froblify the message: %s\" msg)"
  ([fmt params]
     (messagef {} fmt params))
  ([event fmt params]
     (let [fmt (truncate-string-to-1000-chars fmt)
           message {:message fmt :params params}]
       (assoc event :sentry.interfaces.Message message))))

(defn level
  "The the severity of the event (a string or a keyword). If not
  specified the server will use the default severity of
  \"error\". Accepted values are \"fatal\", \"error\" \"warning\"
  \"info\", and \"debug. Eg:
    (level :fatal)
    (level ev :fatal)"
  ([l]
     (level {} l))
  ([event l]
     {:pre [(#{:fatal :error :warning :info :debug} (keyword l))]}
     (assoc event :level l)))

(defn logger
  "The name of the logger which created the record. Eg
  \"my.logger.name\". If missing, defaults to the string
  \"root\". Eg:
    (logger \"my.logger.name\")
    (logger ev \"my.logger.name\")"
  ([l]
     (logger {} l))
  ([event l]
     (assoc event :logger l)))

(defn platform
  "A string representing the platform the client is submitting
  from. Eg \"clojure\" or \"python\". If a platform is not specified,
  it will default to \"clojure\". The platform field is used by the
  Sentry interface to customize various components in the
  interface. Eg:
    (platform \"clojure\")
    (platform ev \"clojure\")"
  ([p]
     (platform {} p))
  ([event p]
     (assoc event :platform p)))

(defn culprit
  "The name of the function that was the primary perpetrator of this
  event. Can be a symbol or a string. Note: an exception event will
  include this information in the stack trace. Eg:
    (culprit ::my-fn)
    (culprit ev ::my-fn)
    (culprit ev \"foo.core/my-fn\")"
  ([c]
     (culprit {} c))
  ([event c]
     (assoc event :culprit c)))

(defn tags
  "A map of tags and values. Eg:
     (tags {:version \"1.2.3\" :environment :production})
     (tags ev {:version \"1.2.3\" :environment :production})"
  ([ts]
     (tags {} ts))
  ([event ts]
     (assoc event :tags ts)))

(defn modules
  "A list of relevant modules and their versions. Eg:
     (modules ev :clojure \"1.5.1\" :clj-stacktrace \"0.2.8\")"
  ([ms]
     (modules {} ms))
  ([event ms]
     (assoc event :modules ms)))

(defn extra
  "Include any arbitrary metadata in the event. The argument must be a
  map. When this function is called more than once on an event,
  the :extra fields are merged together. The extra payload must be
  serializable to JSON. Otherwise an exception is thrown when the
  event is being serialized for submitting to Sentry. Eg:
    (extra event {:foo :bar})
    (extra event {:foo \"bar\"} :test-serialize true)"
  ([e]
     (extra {} e))
  ([event e]
     (update-in event [:extra] merge e)))

(defn- build-java-frame
  [{:keys [method class file line]}]
  {:function method
   :module class
   ;; File name overwrites module name in the Sentry interface
   ;; :filename file
   :lineno line})

(defn- build-clojure-frame
  [{:keys [fn ns file line]}]
  {:function fn
   :module ns
   ;; File name overwrites module name in the Sentry interface
   ;; :filename file
   :lineno line})

(defn- build-frame
  [{:keys [clojure] :as frame}]
  (if clojure
    (build-clojure-frame frame)
    (build-java-frame frame)))

(defn- build-stacktrace
  [{:keys [trace-elems]}]
  {:frames (->> trace-elems
                reverse
                (map build-frame)
                vec)})

(defn exception
  "Include an exception in the event. The `ex` argument must be a
  java.lang.Throwable. Eg:
    (exception ex)
    (exception event ex)"
  ([^Throwable ex] (exception {} ex))
  ([event ^Throwable ex]
     (let [parsed-ex (parse-exception ex)
           ex-type (:class parsed-ex)
           ex-value (:message parsed-ex)
           stacktrace (build-stacktrace parsed-ex)]
       (assoc event
         :sentry.interfaces.Exception {:values [{:type (.getName ex-type)
                                                 :value ex-value
                                                 :stacktrace stacktrace}]}))))

(defn http
  []
  ;; TODO
  )

(defn user
  "Create an event with a User interface, or assoc a User interface to
  a provided event. The User interface consists of four fields: id,
  username, email, and ip-address. All are optional, but the caller
  should provide at least either an id or an ip-address. Eg:
    (user {:id \"12345\" :email \"user@example.com\"})
    (user ev {:id \"12345\" :ip-address \"127.0.0.1\"})"
  ([u]
     (user {} u))
  ([event {:keys [id username email ip-address]}]
     (assoc event
       :sentry.interfaces.User
       (merge {}
              (when id {:id id})
              (when username {:username username})
              (when email {:email email})
              (when ip-address {:ip_address ip-address})))))

(defn query
  "Create an event with a Query interface, or assoc a Query interface
  to a provided event. The Query interface consists of two fields:
  query and engine, where the latter is optinal, but the former is
  mandatory. If the query field is not present, the function will
  thrown an AssertionError. The query field value should be a database
  query that was being performed when the error occurred. The engine
  field is used to describe the database driver. Eg:
    (query {:query \"SELECT * FROM users\" :engine \"JDBC\"})
    (query ev {:query \"SELECT * FROM users\"})"
  ([q]
     (query {} q))
  ([event {:keys [query engine]}]
     {:pre [(some? query)]}
     (assoc event
       :sentry.interfaces.Query
       (merge {:query query}
              (when engine {:engine engine})))))

(defn- utc
  "Returns the current or the provided UTC time as an ISO 8601 format string."
  ([] (utc (Date.)))
  ([date]
     (let [fmt (doto (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss")
                 (.setTimeZone (TimeZone/getTimeZone "UTC")))]
       (.format fmt date))))

(defn- default-event-values
  "These are the default value that the user can override via the
  event map."
  []
  {:level :error
   :platform :clojure
   :timestamp (utc)})

(defn report
  "Report the event to Sentry. This includes setting some mandatory
  event fields that should be set by the sentry client rather than the
  user (eg event-id, timestamp, and server-name), and providing
  default values for some mandatory values when they have not been
  provided by the user (eg level and platform). Note:
  raven-clj.core/capture sets the :event-id and :server-name fields."
  [event dsn]
  (let [event (merge (default-event-values) event)]
    (raven/capture dsn event)))

(comment
  (-> (exception e)
      (message "Failed to handle message.")
      (extra msg)
      (user {:id "liwp"})
      (report "dsn://1.2.3"))
  )
