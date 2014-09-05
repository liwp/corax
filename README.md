# Corax

[![Build Status](https://travis-ci.org/listora/corax.png?branch=master)](https://travis-ci.org/listora/corax)

A layer of sugar on top of
[raven-clj](https://github.com/sethtrain/raven-clj).

## Clojars:

```clj
[listora/corax "0.1.0"]
```

## Usage

Corax is used to incrementally build a raven event and to eventually
submit that event to [Senty][1].

### Require the library:

```clj
(require '[corax.core :as err])
```

### Build an event:

An event is built with the thread-first (`->`) macro and the various
helper functions provided by `corax`:

```clj
(-> (err/message "Things went wrong")
    (err/exception ex)
    (err/culprit ::my-fn)
    ...)
```

### Send the event:

A built event is finally submitted to Sentry with `report`:

```clj
(-> ... ;; build the event like above
    (err/report {:dsn my-dsn}))

```

### A complete example:

```clj
(require '[corax.core :as err])
(def my-dsn "") ;; get your DSN from getsentry.com
(-> (err/message "Things went wrong")
    (err/exception ex)
    (err/culprit ::my-fn)
    (err/report {:dsn my-dsn})
```

## API

The `corax` API consists of a number of builders used to build an
event incrementally, and a `report` function used to submit the built
event to Sentry. All the builders can either create a new event map,
or add new fields to a provided map, which allows the builders to be
chained together with the threading macro (`->`).

More details on the format and semantics of a Sentry event can be
found in the Sentry documentation on [writing a client][2] and [the
supported interfaces][3].


### culprit

The name of the function that was the primary perpetrator of this
event. Can be a symbol or a string.

```clj
(culprit ::my-fn)
(culprit ev ::my-fn)
(culprit "any random string")
(culprit ev "foo.core/my-fn")
```

The culprit will show up as the heading of an event in the Sentry web
UI.


### exception

Add an [`Exception` interface][4] to an event. The `ex` argument must
be an instance of `java.lang.Throwable`.

```clj
(exception ex)
(exception event ex)
```

An exception in the event is rendered as a stack trace under the
Exception heading in the Sentry web UI. The type of the exception and
the message are also rendered. Corax uses [`clj-stacktrace`][5] to
translate a stack trace to something that Sentry can render in the web
UI. We try to provide sensible, language-specific stack frames for
both Clojure and Java.


### extra

Include any arbitrary data in the event. The argument must be a
map. When this function is called more than once on an event, the
`:extra` fields are merged together. If the multiple calls share keys,
the latter calls will overwrite the keys from earlier calld (see
Clojure's [`merge`][10]). The extra payload must be serializable to
JSON by [`cheshire`][6].

```clj
(extra {:foo :bar})
(extra event {:foo "bar"})
```

The provided data is rendered under the Additional Data heading in the
Sentry web UI.


### http

Add an [`Http` interface][7] to an event. The Http interface is used
to describe an HTTP request. The `http` function takes a ring request
and translates that to a map understood by Sentry.

```clj
(http ring-req)
(http ev ring-req)
```

Note: Sentry supports arbitrary data in the `:env` map. This facility
is not exposed by `corax` at the moment, but the user can add fields
to the `:env` map manually if they so wish:

```clj
(-> (http ring-req)
    (assoc-in [:sentry.interfaces.Http :env :my-key] :my-data))
```

But it might be simpler to just use `extra`.


### level

The severity of the event (a string or a keyword). If not specified,
`corax` will default the field to `:error`. Accepted values are
`:fatal`, `:error`, `:warning`, `:info`, and `:debug`.

```clj
(level :fatal)
(level ev "fatal")
```


### logger

The name of the logger (string or keyword) which created the
record. Eg `"my.logger.name"`. If not specified, Sentry defaults
logger to `"root"`.

```clj
(logger :my.logger.name)
(logger ev "my.logger.name")
```


### message

Set the message field in an event. The value should be a user-readable
description of the event. Maximum length is 1000 characters. Longer
messages are silently truncated.

```clj
(message "Failed to froblify the packet.")
(message ev "Failed to froblify the packet.")
```


### modules

A list of relevant modules and their versions.

```clj
(modules {:clojure "1.6.0" :clj-stacktrace "0.2.8"})
(modules ev {:clojure "1.6.0" :clj-stacktrace "0.2.8"})
```

Modules are rendered as a list under the Package Versions heading in
the Sentry web UI.


### platform

A string or keyword representing the platform the client is submitting
from. Eg `:clojure` or `:python`. If a platform is not specified,
`corax` will default it to `:clojure`. The platform field is used by
the Sentry interface to customize various components in the web UI,
but it's unlikely that the web UI would have been customised for
Clojure.

```clj
(platform :clojure)
(platform ev "clojure")
```


### query

Add a [`Query` interface][8] to an event. The Query interface consists
of two fields: `:query` and `:engine`, where the former is mandator
and the latter is optional. The `:query` field value should be a
database query that was being performed when the error occurred. The
`:engine` field is used to describe the database driver.

```clj
(query {:query "SELECT * FROM users" :engine "JDBC"})
(query ev {:query "SELECT * FROM users"})
```

Query is not rendered on the Sentry web UI at the moment. The UI
allows to search for events with a Query field, but there doesn't seem
to be a way of accessing the fields of the Query payload.


### server-name

A string or keyword representing the server that the client is
submitting from. Eg `"web1.example.com"`. If a `server-name` is not
specified, it will default to the `corax` client's IP address.

```clj
(server-name :web1)
(server-name ev \"web1.example.com\")"
```


### tags

A map of tags and values.

```clj
(tags {:version "1.2.3" :environment :production})
(tags ev {:version "1.2.3" :environment :production})
```

Tags will show up in the Tags section of the Sentry web UI. A number
of tags are generated via other mechanism in a Raven error report, eg
the log level, logger and server name are also treated as tags.


### user

Add a [`User` interface][9] to an event. The User interface consists
of four fields: `:id`, `:username`, `:email`, and `:ip-address`. All
are optional, but the caller should provide at least an `:id` or an
`:ip-address`.

```clj
(user {:id "12345" :email "user@example.com"})
(user ev {:id "12345" :ip-address "127.0.0.1"})
```

User information is rendered under the User heading in the Sentry web
UI. Sentry keeps track of the number of users that have reported the
same error.


### report

The `report` function is used to submit an event to Sentry. `report`
takes an event and an options map as arguments. The options map
supports two keys: `:dsn` and `:log-fn`.

The `:dsn` keys is mandatory and is used to specify the Sentry DSN to
use when reporting events. Without the DSN `corax` won't be able to
submit events to Sentry. A DSN looks something like this:
`https://12345...:67890...@app.getsentry.com/12345`.

The `:log-fn` is used to override `corax`'s default logger. The
default logger logs to stdout, which isn't appopriate for many
applications, so the caller can provide their own logging function by
specifying the `:log-fn` key in the options map.

#### log function

The log function signature is `(defn log [{:keys [error event
exception]}] ...)`. The `error` argument is a keyword describing the
error that occurred. The two errors defined at the moment as `:no-dsn`
and `:exception`. The former occurres if the `:dsn` key has not been
provided in the `report` call. The latter occurs if an exception is
thrown when the event is being sent to Sentry.

`corax.error-reporter/error-messages` defines a mapping of `error`
keywords to error messages, but the caller can also define their own
translation.

The `exception` argument will contain a `java.lang.Throwable` in cases
where an exception was thrown.

The `event` argument will contain the event that was being reported.

Here's the default logging function as an example of how to override
the logger:

```clj
(defn my-log-fn
  [{:keys [error event exception]}]
  (let [event-str (with-out-str (clojure.pprint/pprint event))
        message (get corax.error-reporter/error-messages
                     error
                     (str "Unknown error: " error))]
    (println message)
    (when exception
      (println (clj-stacktrace.repl/pst-str exception)))
    (println "Event:" event-str)))

(-> (message "test logger")
    (report {:dsn my-dsn :log-fn my-log-fn}))
```

A few points to notice:

* We pretty-print the event. Events, especially those with exceptions
  in them, can get quite large. Simply printing them out to a log file
  will make them almost impossible to understand. By pretty-printing
  them, we can make them a bit more digestable.

* We translate the `error` keyword to a user-readable message.

* We handle the case where `error` is not a keyword that we expected.

* The exception won't be always present in the call to `log-fn`.

* We leverage `clj-stacktrace` to produce a Clojure-specific
  stacktrace for us.


## License

Copyright © 2014 Listora

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: http://getsentry.com/
[2]: http://sentry.readthedocs.org/en/latest/developer/client/index.html#building-the-json-packet
[3]: http://sentry.readthedocs.org/en/latest/developer/interfaces/index.html
[4]: http://sentry.readthedocs.org/en/latest/developer/interfaces/index.html#sentry.interfaces.exception.Exception
[5]: https://github.com/mmcgrana/clj-stacktrace
[6]: https://github.com/dakrone/cheshire
[7]: http://sentry.readthedocs.org/en/latest/developer/interfaces/index.html#sentry.interfaces.http.Http
[8]: http://sentry.readthedocs.org/en/latest/developer/interfaces/index.html#sentry.interfaces.query.Query
[9]: http://sentry.readthedocs.org/en/latest/developer/interfaces/index.html#sentry.interfaces.user.User
[10]: https://clojure.github.io/clojure/clojure.core-api.html#clojure.core/merge
