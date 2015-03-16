# Corax

[![Build Status](https://travis-ci.org/listora/corax.png?branch=master)](https://travis-ci.org/listora/corax)

A layer of sugar on top of
[raven-clj](https://github.com/sethtrain/raven-clj).

## Clojars:

```clj
[listora/corax "0.3.0"]
```

## Usage

Corax is used to incrementally build a raven event and to eventually
submit that event to [Sentry][1].

### Require the library:

```clj
(require '[corax.core :as err])
```

### Create an error reporter:

A `CoraxErrorReporter` is used to wrap state, like the Sentry DSN, and
to report errors to Sentry. Create one with `new-error-reporter`:

```clj
(def my-dsn "") ;; get your DSN from getsentry.com
(def error-reporter (err/new-error-reporter my-dsn))
```

### Build an event:

An event is built with the thread-first (`->`) macro and the various
helper functions provided by Corax:

```clj
(-> (err/message "Things went wrong")
    (err/exception ex)
    (err/culprit ::my-fn)
    ...)
```

### Send the event:

A built event is submitted to Sentry with `report`:

```clj
(-> ... ;; build the event like above
    (err/report error-reporter))

```

### A complete example:

```clj
(require '[corax.core :as err])
(def my-dsn "") ;; get your DSN from getsentry.com
(def error-reporter (err/new-error-reporter my-dsn))
(-> (err/message "Things went wrong")
    (err/exception ex)
    (err/culprit ::my-fn)
    (err/report error-reporter
```

## API

The Corax API consists of a number of builders used to build an event
incrementally, and a `report` function used to submit the built event
to Sentry. All the builders can either create a new event map, or add
new fields to a provided map, which allows the builders to be chained
together with the threading macro (`->`).

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

`ex-data` is automatically called on `ex` and the resulting map is
included in the event under the `:ex-data` key in the `:extra`
map. Any existing `:ex-data` field in the `:extra` map will be
overwritten.

If the event doesn't contain a `:message` field yet, the exception
message will be set as `:message`. The `:message` field can be
explicitly set by calling `message` either before or after calling
`exception`.

An exception in the event is rendered as a stack trace under the
Exception heading in the Sentry web UI. The type and message of the
exception are also rendered. Corax uses [`clj-stacktrace`][5] to
translate a stack trace to something that Sentry can render in the web
UI. We try to provide sensible, language-specific stack frames for
both Clojure and Java.


### extra

Include any arbitrary data in the event. The argument must be a
map. When this function is called more than once on an event, the
`:extra` fields are merged together. If the multiple calls share keys,
the latter calls will overwrite the keys from earlier calls (see
Clojure's [`merge`][10]). The extra payload must be serializable to
JSON by [Cheshire][6].

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
is not exposed by Corax at the moment, but the user can add fields to
the `:env` map manually if they so wish:

```clj
(-> (http ring-req)
    (assoc-in [:sentry.interfaces.Http :env :my-key] :my-data))
```

But it might be simpler to just use `extra`.


### level

The severity of the event (a string or a keyword). If not specified,
Corax will default the field to `:error`. Accepted values are
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
Corax will default to `:clojure`. The platform field is used by the
Sentry interface to customize various components in the web UI, but
it's unlikely that the web UI would have been customized for Clojure.

```clj
(platform :clojure)
(platform ev "clojure")
```


### query

Add a [`Query` interface][8] to an event. The Query interface consists
of two fields: `:query` and `:engine`, where the former is mandatory
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
specified, it will default to the Corax client's IP address.

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

### new-error-reporter

A new error reporter is instantiated with `new-error-reporter`. The
function takes two arguments: `dsn` and `logger`.

The `dsn` argument is mandatory and is used to specify the Sentry DSN
to use when reporting events. If the provided DSN is `nil`, or looks
otherwise invalid, `new-error-reporter` will throw an
`AssertionError`. A DSN looks something like this:
`https://12345...:67890...@app.getsentry.com/12345`.

The optional `logger` argument is an implementation of
`corax.log/Logger`. It is used to notify the calling application of
the result of an error report. See `corax.log/Logger` for more
details. If a logger is not provided, the default logger
(`corax.log/CoraxLogger`) will be used. The default logger logs to
`stdout`. `corax.core/NullLogger` can be used to turn *all* logging
off. Please use your own custom implementation to redirect the output
to your logging system.


### report

The `report` function is used to submit an event to Sentry. `report`
takes an event and an error reporter as arguments.

#### Logger

The `corax.log/Logger` protocol defines the following methods:

* `log-event-id` - the event was reported to Sentry

* `log-failure` - the call to Sentry returned an unexpected HTTP status
  code

* `log-error` - an unexpected exception was thrown when reporting the
  event

The `event` argument in all the above method definitions contains the
event that was being reported.

#### JSON serialization errors

The event will be serialized to JSON before it is submitted to Sentry
(by [Cheshire][6]). If the event contains an object that is not
serializable, Corax will report an error to Sentry pointing this out,
but the original event will have been lost.

The JSON serialization error contains the following message:

```
An object in the error report could not be serialized to
JSON. This error is masking the real application error. For more
details see https://github.com/listora/corax
```

The error report will also contain the
`com.fasterxml.jackson.core.JsonGenerationException` that was thrown
by Cheshire. The exception message explains which object failed to
serialize, e.g. `Cannot JSON encode object of class: class
java.lang.Exception: java.lang.Exception: foo`.

The serialization problem can be addressed by:

- making sure that the offending object is stripped out from the event
before `report`ing it to Sentry

- by adding a custom JSON encoder for the offending type

- by adding a catch-all JSON encoder for `Object`

The first approach is sometimes hard to implement since it might not
be obvious where the offending object is coming from. The second
approach is quite simple, but it will fix the problem only for one
type and adding JSON encoders to your application might have unwanted
effects if you use Cheshire in your own application. The third option
is the nuclear option and will address all JSON serialization
problems, but again with possibly unwanted side-effects if you use
Cheshire in your own app.

Custom JSON encoders can be added to Cheshire with the `add-encoder`
fn:

```clj
(require '[cheshire.generate :as generate])
(generate/add-encoder Exception (fn [e jg] (.writeString jg (str e))))
```

Here we simply call `str` on the provided `Exception` instance to
produce a `String` which can be included in the JSON event.

The nuclear option is to enable to `str` -based serialization as a
default:

```clj
(generate/add-encoder Object (fn [e jg] (.writeString jg (str e))))
```


## Ring middleware

Corax provides an example ring middleware for reporting any exceptions
thrown by the request handler as errors to Sentry:

```clj
(require '[corax.core :as corax])
(require '[corax.middleware :refer [wrap-exception-reporting]])

(def my-dsn "") ;; get your DSN from getsentry.com
(def error-reporter (err/new-error-reporter my-dsn))

(defn apply-middleware
  [routes]
  (-> routes
      ...
      (wrap-exception-reporting error-reporter)
      ...))
```

Note: the middleware will rethrow the caught exception to allow some
other middleware to catch it and return an appropriate HTTP response.


### Testing

The library defines an `ErrorReporter` protocol which is implemented
by the `CoraxErrorReporter` record. You can mock out the Corax error
reporter in your tests by providing a mock implementation of
`ErrorReporter` and passing that to `report`.


## License

Copyright © 2014 - 2015 Listora

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
