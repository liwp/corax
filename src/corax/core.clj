(ns corax.core
  (:require [corax.error-reporter :as error-reporter]
            [corax.exception :refer [build-exception-value]]
            [corax.http :refer [build-http-info]]
            [corax.log :as log]
            [raven-clj.core :as raven]))

;;; --------------
;;; Event builders
(defn culprit
  "The name of the function that was the primary perpetrator of this
  event. Can be a symbol or a string. Note: an exception event will
  include this information in the stack trace. Eg:
  (culprit ::my-fn)
  (culprit ev ::my-fn)
  (culprit ev \"foo.core/my-fn\")

  The culprit will show up as the heading of an event in the Sentry
  web UI."
  ([c]
   (culprit {} c))
  ([event c]
   (assoc event :culprit c)))

(declare extra)
(declare message)

(defn exception
  "Include an exception in the event. The `ex` argument must be a
  java.lang.Throwable. Eg:
  (exception ex)
  (exception event ex)

  ex-data is automatically called on `ex` and the resulting map is
  included in the event under the :ex-data key in the :extra map. Any
  existing :ex-data field will be overwritten.

  If the event doesn't contain a :message field yet, the exception
  message will be set as :message. The field can be explicitly set by
  calling `message` either before or after calling `exception`.

  An exception in the error report is rendered as a stack trace under
  the Exception heading in the Sentry web UI. The type of the
  exception and the message are also rendered."
  ([^Throwable ex] (exception {} ex))
  ([event ^Throwable ex]
   (let [value (build-exception-value ex)
         data (ex-data ex)
         msg (:message event)]
     ;; :sentry.interfaces.Exception
     (-> event
         (assoc :exception {:values [value]})
         (cond-> data (extra {:ex-data data}))
         (cond-> (nil? msg) (message (.getMessage ex)))))))

(defn extra
  "Include any arbitrary metadata in the event. The argument must be a
  map.

  Eg:
  (extra {:foo :bar})
  (extra event {:foo :bar})

  When this function is called more than once on an event, the :extra
  fields are shallowly merged together, eg {:foo 1} and {:bar 2} will
  become {:foo 1 :bar 2}, but {:foo {:bar 1}} and {:foo {:baz 2}} will
  become {:foo {:baz 2}}.

  The extra payload must be serializable to JSON. Otherwise an
  exception is thrown when the event is being serialized for
  submitting to Sentry.

  The provided data is rendered under the Additional Data heading in
  the Sentry web UI."
  ([e]
   (extra {} e))
  ([event e]
   {:pre [(map? e)]}
   (update-in event [:extra] merge e)))

;; TODO: how to pass in an env map while providing one and two arity
;; versions of the fn?
(defn http
  ([req]
   (http {} req))
  ([event req]
   {:pre [(some? (:request-method req))
          (some? (:scheme req))
          (some? (:server-name req))
          (some? (:server-port req))
          (some? (:uri req))]}
   (assoc event :sentry.interfaces.Http (build-http-info req))))

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

(defn- truncate-string-to-1000-chars
  [s]
  (if (> (count s) 1000)
    (.substring s 0 1000)
    s))

(defn message
  "Set the :message field in an event. The value should be a
  user-readable representation of this event. Maximum length is 1000
  characters. Longer messages are silently truncated. Eg:
    (message \"Failed to froblify the packet.\")
    (message ev \"Failed to froblify the packet.\")"
  ([m]
   (message {} m))
  ([event m]
   (let [m (truncate-string-to-1000-chars m)]
     (assoc event :message m))))

(defn modules
  "A list of relevant modules and their versions. Eg:
     (modules {:clojure \"1.5.1\" :clj-stacktrace \"0.2.8\"})
     (modules ev {:clojure \"1.5.1\" :clj-stacktrace \"0.2.8\"})

  Modules are rendered as a list under the Package Versions heading in
  the Sentry web UI."
  ([ms]
   (modules {} ms))
  ([event ms]
   {:pre [(map? ms)]}
   (assoc event :modules ms)))

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

(defn query
  "Create an event with a Query interface, or assoc a Query interface
  to a provided event. The Query interface consists of two fields:
  query and engine, where the latter is optinal, but the former is
  mandatory. If the query field is not present, the function will
  thrown an AssertionError. The query field value should be a database
  query that was being performed when the error occurred. The engine
  field is used to describe the database driver. Eg:
    (query {:query \"SELECT * FROM users\" :engine \"JDBC\"})
    (query ev {:query \"SELECT * FROM users\"})

  Query is not rendered on the Sentry web UI at the moment. The UI
  allows to search for events with a Query in the payload, but there
  doesn't seem to be a way of accessing the fields of the Query."
  ([q]
   (query {} q))
  ([event {:keys [query engine]}]
   {:pre [(some? query)]}
   (assoc event
          :sentry.interfaces.Query
          (merge {:query query}
                 (when engine {:engine engine})))))

(defn server-name
  "A string representing the server the client is submitting from. Eg
  \"web1.example.com\". If a server-name is not specified, it will
  default to the client's IP address. Eg:
    (server-name \"web1.example.com\")
    (server-name ev \"web1.example.com\")"
  ([name]
   (server-name {} name))
  ([event name]
   (assoc event :server_name name)))

(defn tags
  "A map of tags and values. Eg:
     (tags {:version \"1.2.3\" :environment :production})
     (tags ev {:version \"1.2.3\" :environment :production})

  Tags will show up in the Tags section of the Sentry web UI. A number
  of tags are generated via other mechanism in a Raven error report,
  eg the log level, logger and server name are also treated as tags."
  ([ts]
   (tags {} ts))
  ([event ts]
   (assoc event :tags ts)))

(defn user
  "Create an event with a User interface, or assoc a User interface to
  a provided event. The User interface consists of four fields: id,
  username, email, and ip-address. All are optional, but the caller
  should provide at least either an id or an ip-address. Eg:
    (user {:id \"12345\" :email \"user@example.com\"})
    (user ev {:id \"12345\" :ip-address \"127.0.0.1\"})

  User information is rendered under the User heading in the Sentry
  web UI. Sentry keeps track of the number of users that have reported
  the same error."
  ([u]
   (user {} u))
  ([event {:keys [id username email ip-address]}]
   (assoc event
          ;; :sentry.interfaces.User
          :user
          (merge {}
                 (when id {:id id})
                 (when username {:username username})
                 (when email {:email email})
                 (when ip-address {:ip_address ip-address})))))


;;;--------
;;; Logging

;; Re-export the corax.log/Logger protocol for user convenience.
(def Logger log/Logger)

;; Provide a NullLogger which ignores all logging calls.
(defrecord NullLogger []
  log/Logger
  (log-event-id [this sentry-id event])
  (log-failure [this http-response event])
  (log-error [this exception event]))


;;; ---------
;;; Reporting

;; Re-export the corax.error-reporter/ErrorReporter protocol for user
;; convenience.
(def ErrorReporter error-reporter/ErrorReporter)

;; TODO: we could try to be stricter, eg check that protocol is
;; http/https.
(defn- valid-dsn?
  "Try to parse the given DSN."
  [dsn]
  (try
    (raven/parse-dsn dsn)
    true
    (catch Exception e
      false)))

(defn new-error-reporter
  "Create a new `CoraxErrorReporter`.

  The `dsn` argument specifies the Sentry DSN to use.

  The optional `logger` argument is an implementation of
  `corax.core/Logger`. It is used to notify the calling application of
  the result of an error report. See `corax.core/Logger for more
  details. If a logger is not provided, the default
  logger (`corax.logger/CoraxLogger`) will be used. The default logger
  logs to `stdout`. `corax.core/NullLogger` can be used to ignore
  *all* logging."
  [dsn & [logger]]
  {:pre [(valid-dsn? dsn)
         (or (nil? logger) (satisfies? Logger logger))]}
  (let [logger (or logger (log/->CoraxLogger))]
    (error-reporter/->CoraxErrorReporter dsn logger)))

(defn report
  "Report the event to Sentry. This includes setting some mandatory
  event fields that should be set by the sentry client rather than the
  user (eg `event-id` and `timestamp`), and providing default values
  for some mandatory values when they have not been provided by the
  user (eg `level`, `platform`, `server-name`)."
  [event error-reporter]
  (error-reporter/-report error-reporter event))
