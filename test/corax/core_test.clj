(ns corax.core-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [corax.core :refer :all]
            [corax.error-reporter :as err]))

(deftest test-culprit
  (testing "culprit"
    (testing "when given only a culprit name"
      (is (= (culprit "my-culprit") {:culprit "my-culprit"})
          "should create new event with :culprit field"))

    (testing "when given an event"
      (is (= (culprit {:foo :bar} "my-culprit")
             {:foo :bar :culprit "my-culprit"})
          "should assoc :culprit field to event"))))

(deftest test-exception
  (testing "exception"
    (let [ex (Exception. "my message")]
      (testing "when given only an exception"
        (let [event (exception ex)]
          (is (= (set (keys event)) #{:exception :message})
              "should create new event with :exception field")
          (is (= (count (get-in event [:exception :values])) 1)
              "should contain one exception value")
          (is (= (:message event) "my message"))))

      (testing "when given an event"
        (let [event (exception {:foo :bar} ex)]
          (is (= (set (keys event)) #{:exception :foo :message})
              "should create new event with :exception field")
          (is (= (count (get-in event [:exception :values])) 1)
              "should contain one exception value")
          (is (= (:message event) "my message")))))

    (testing "ex-data"
      (testing "with ex-data"
       (let [ex (ex-info "test" {:a 1 :b 2})
             event (exception ex)]
         (is (= (set (keys event)) #{:exception :extra :message}))
         (is (= (-> event :extra :ex-data) {:a 1 :b 2}))))

      (testing "with ex-data and event"
        (let [ex (ex-info "test" {:a 1 :b 2})
              event (-> (extra {:ex-data {:c 3 :d 4} :foo :bar})
                        (exception ex))]
          (is (= (set (keys event)) #{:exception :extra :message}))
          (is (= (-> event :extra) {:foo :bar
                                    :ex-data {:a 1 :b 2}})
              "Overwrites :ex-data, but keeps :foo"))))

    (testing "message"
      (testing "with existing message"
        (let [ex (Exception. "test")
              event (-> (message "message")
                        (exception ex))]
          (is (= (set (keys event)) #{:exception :message}))
          (is (= (-> event :message) "message"))))

      (testing "with overriding message"
        (let [ex (Exception. "test")
              event (-> (exception ex)
                        (message "message"))]
          (is (= (set (keys event)) #{:exception :message}))
          (is (= (-> event :message) "message")))))))

(deftest test-extra
  (testing "extra"
    (testing "when given only a data argument"
      (is (= (extra {:data "data"}) {:extra {:data "data"}})
          "should create new event with :extra field"))

    (testing "when given an event"
      (is (= (extra {:foo :bar} {:data "data"})
             {:foo :bar :extra {:data "data"}})
          "should assoc :data field to event"))

    (testing "when called multiple times"
      (let [ev (-> (extra {:a "a"})
                   (extra {:b "b"}))]
        (is (= ev {:extra {:a "a" :b "b"}})
            "should merge data maps")))))

(deftest test-http
  (let [req {:request-method :post
             :scheme :http
             :server-name "example.com"
             :server-port 12345
             :uri "/me"}]
    (testing "http"
      (testing "when given only a request"
        (let [event (http req)]
          (is (= (keys event) [:sentry.interfaces.Http])
              "should create new event with :sentry.interfaces.Http field")))

      (testing "when given an event and a request"
        (let [event (http {:foo :bar} req)]
          (is (= (keys event) [:sentry.interfaces.Http :foo])
              "should create new event with :http field"))))))

(deftest test-level
  (testing "level"
    (testing "when given only a level"
      (for [l [:fatal :error :warning :info :debug]]
        (is (= (level :debug) {:level :debug})
            "should create new event with :level field")))

    (testing "when given an event"
      (for [l [:fatal :error :warning :info :debug]]
        (is (= (level {:foo "bar"} :debug) {:foo "bar" :level :debug})
            "should assoc :level to event")))

    (testing "when given an invalid level"
      (is (thrown? AssertionError (level :foobar))
          "should assert level is known")
      (is (thrown? AssertionError (level {:foo "bar"} :foobar))
          "should assert level is known"))))

(deftest test-logger
  (testing "logger"
    (testing "when given only a logger name"
      (is (= (logger "my.logger") {:logger "my.logger"})
          "should create new event with :logger field"))

    (testing "when given an event"
      (is (= (logger {:foo :bar} "my.logger") {:foo :bar :logger "my.logger"})
          "should assoc :logger field to event"))))

(deftest test-message
  (testing "message"
    (testing "when given only a message"
      (is (= (message "message") {:message "message"})
          "should create new event with :message field"))

    (testing "when given an event"
      (is (= (message {:foo :bar} "message") {:foo :bar :message "message"})
          "should assoc :message field to event"))

    (testing "when given a long message"
      (let [msg (apply str (repeat 1280 \a))]
        (is (= (-> msg (message) :message count) 1000)
            "should truncate message to 1000 characters")))))

(deftest test-modules
  (testing "modules"
    (let [mods {:module-a "A" :modules-b "B"}]
      (testing "when given only a modules map"
        (is (= (modules mods) {:modules mods})
            "should create new event with :modules field"))

      (testing "when given an event and modules map"
        (is (= (modules {:foo :bar} mods) {:foo :bar :modules mods})
            "should assoc :modules field to event")))))

(deftest test-platform
  (testing "platform"
    (testing "when given only a platform name"
      (is (= (platform :my-platform) {:platform :my-platform})
          "should create new event with :platform field"))

    (testing "when given an event"
      (is (= (platform {:foo :bar} :my-platform)
             {:foo :bar :platform :my-platform})
          "should assoc :platform field to event"))))

(deftest test-query
  (testing "query"
    (testing "without event"
      (let [ev (query {:query "query"
                       :engine "engine"})]
        (is (= ev
               {:sentry.interfaces.Query
                {:query "query"
                 :engine "engine"}})
            "should create new event with Query interface")))

    (testing "with event"
      (let [ev (query {:foo "foo"}
                      {:query "query"
                       :engine "engine"})]
        (is (= ev
               {:foo "foo"
                :sentry.interfaces.Query
                {:query "query"
                 :engine "engine"}})
            "should assoc Query interface to event")))

    (testing "with no :engine argument"
      (let [ev (query {:foo "foo"}
                      {:query "query"})]
        (is (= ev
               {:foo "foo"
                :sentry.interfaces.Query
                {:query "query"}})
            "Query interface should contain :query field")))

    (testing "with no :query argument"
      (is (thrown? AssertionError (query {:engine "engine"}))
          "should assert query argument is provided"))

    (testing "with unexpected arguments"
      (let [ev (query {} {:query "query" :unexpted "argument"})]
        (is (= ev {:sentry.interfaces.Query {:query "query"}})
            "should ignore unexpected arguments")))))

(deftest test-server-name
  (testing "server-name"
    (testing "when given only a server name"
      (is (= (server-name :my-server-name) {:server_name :my-server-name})
          "should create new event with :server_name field"))

    (testing "when given an event and server name"
      (is (= (server-name {:foo :bar} :my-server-name)
             {:foo :bar :server_name :my-server-name})
          "should assoc :server_name field to event"))))

(deftest test-tags
  (testing "tags"
    (testing "when given only tags"
      (is (= (tags {:a "a" :b "b"}) {:tags {:a "a" :b "b"}})
          "should create new event with :tags field"))

    (testing "when given an event"
      (is (= (tags {:foo :bar} {:a "a" :b "b"})
             {:foo :bar :tags {:a "a" :b "b"}})
          "should assoc :tags field to event"))))

(deftest test-user
  (testing "user"
    (testing "without event"
      (let [ev (user {:id "id"
                      :username "username"
                      :email "email"
                      :ip-address "ip"})]
        (is (= ev
               {:user
                {:id "id"
                 :username "username"
                 :email "email"
                 :ip_address "ip"}})
            "should create new event with User interface")))

    (testing "with event"
      (let [ev (user {:foo "foo"}
                     {:id "id"
                      :username "username"
                      :email "email"
                      :ip-address "ip"})]
        (is (= ev
               {:foo "foo"
                :user
                {:id "id"
                 :username "username"
                 :email "email"
                 :ip_address "ip"}})
            "should assoc User interface to event")))

    (testing "with subset of fields provided"
      (let [all-fields {:id "id"
                        :username "username"
                        :email "email"
                        :ip_address "ip"}]
        (for [f (keys all-fields)
              :let [fields (assoc all-fields f nil)
                    expected-ev {:user (dissoc all-fields f)}
                    actual-ev (user {} fields)]]
          (is (= actual-ev expected-ev)
              "should not assoc nil fields"))))

    (testing "with unexpected arguments"
      (let [ev (user {} {:unexpted "argument"})]
        (is (= ev {:user {}})
            "should ignore unexpected arguments")))))

(deftest test-report
  (testing "report"
    (let [report-called (atom nil)
          dummy-log-fn (fn [])]
      (with-redefs [corax.core/utc (fn [] "timestamp")]

        (testing "with default :level, :platform, and :timestamp fields"
          (with-redefs [err/report (fn [event dsn log-fn]
                                     (reset! report-called true)
                                     (is (= event
                                            {:level :error
                                             :platform :clojure
                                             :timestamp "timestamp"}))
                                     (is (= dsn "dummy dsn"))
                                     (is (= log-fn dummy-log-fn)))]
            (report {} {:dsn "dummy dsn" :log-fn dummy-log-fn})
            (is (true? @report-called) "should call report")))

        (testing "with overriding :level, :platform, and :timestamp fields"
          (with-redefs [err/report (fn [event dsn log-fn]
                                     (reset! report-called true)
                                     (is (= event
                                            {:level :my-level
                                             :platform :my-platform
                                             :timestamp :my-timestamp}))
                                     (is (= dsn "dummy dsn"))
                                     (is (= log-fn dummy-log-fn)))]
            (report {:level :my-level
                     :platform :my-platform
                     :timestamp :my-timestamp}
                    {:dsn "dummy dsn" :log-fn dummy-log-fn})
            (is (true? @report-called) "should call report")))))))
