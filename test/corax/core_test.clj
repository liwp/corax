(ns corax.core-test
  (:require [clojure.test :refer [deftest is run-tests testing]]
            [corax.core :refer :all]))

(deftest test-message
  (testing "message"
    (testing "when given only a message"
      (is (= {:message "message"} (message "message"))
          "should create new event with :message field"))

    (testing "when given an event"
      (is (= {:foo :bar :message "message"}
             (message {:foo :bar} "message"))
          "should assoc :message field to event"))

    (testing "when given a long message"
      (let [msg (apply str (repeat 1280 \a))]
        (is (= 1000
               (-> msg
                   (message)
                   :message
                   count))
            "should truncate message to 1000 characters")))))

(deftest test-messagef
  (testing "messagef"
    (testing "when given only a format message and params"
      (is (= {:sentry.interfaces.message.Message
              {:message "message"
               :params [:param]}}
             (messagef "message" [:param]))
          "should create new event with a Message interface"))

    (testing "when given an event"
      (is (= {:foo :bar
              :sentry.interfaces.message.Message
              {:message "message"
               :params [:param]}}
             (messagef {:foo :bar} "message" [:param]))
          "should assoc Message interface to event"))

    (testing "when given a long format message"
      (let [msg (apply str (repeat 1280 \a))]
        (is (= 1000
               (-> msg
                   (messagef [:param])
                   :sentry.interfaces.message.Message
                   :message
                   count))
            "should truncate :message field to 1000 characters")))))

(deftest test-level
  (testing "level"
    (testing "when given only a level"
      (for [l [:fatal :error :warning :info :debug]]
        (is (= {:level :debug}
               (level :debug))
            "should create new event with :level field")))

    (testing "when given an event"
      (for [l [:fatal :error :warning :info :debug]]
        (is (= {:foo "bar" :level :debug}
               (level {:foo "bar"} :debug))
            "should assoc :level to event")))

    (testing "when given an invalid level"
      (is (thrown? AssertionError (level :foobar))
          "should assert level is known")
      (is (thrown? AssertionError (level {:foo "bar"} :foobar))
          "should assert level is known"))))

(deftest test-logger
  (testing "logger"
    (testing "when given only a logger name"
      (is (= {:logger "my.logger"} (logger "my.logger"))
          "should create new event with :logger field"))

    (testing "when given an event"
      (is (= {:foo :bar :logger "my.logger"}
             (logger {:foo :bar} "my.logger"))
          "should assoc :logger field to event"))))

(deftest test-platform
  (testing "platform"
    (testing "when given only a platform name"
      (is (= {:platform :my-platform} (platform :my-platform))
          "should create new event with :platform field"))

    (testing "when given an event"
      (is (= {:foo :bar :platform :my-platform}
             (platform {:foo :bar} :my-platform))
          "should assoc :platform field to event"))))

(deftest test-culprit
  (testing "culprit"
    (testing "when given only a culprit name"
      (is (= {:culprit "my-culprit"} (culprit "my-culprit"))
          "should create new event with :culprit field"))

    (testing "when given an event"
      (is (= {:foo :bar :culprit "my-culprit"}
             (culprit {:foo :bar} "my-culprit"))
          "should assoc :culprit field to event"))))

(deftest test-tags
  (testing "tags"
    (testing "when given only tags"
      (is (= {:tags {:a "a" :b "b"}} (tags {:a "a" :b "b"}))
          "should create new event with :tags field"))

    (testing "when given an event"
      (is (= {:foo :bar :tags {:a "a" :b "b"}}
             (tags {:foo :bar} {:a "a" :b "b"}))
          "should assoc :tags field to event"))))

(deftest test-extra
  (testing "extra"
    (testing "when given only a data argument"
      (is (= {:extra {:data "data"}} (extra {:data "data"}))
          "should create new event with :extra field"))

    (testing "when given an event"
      (is (= {:foo :bar :extra {:data "data"}}
             (extra {:foo :bar} {:data "data"}))
          "should assoc :data field to event"))

    (testing "when called multiple times"
      (let [ev (-> (extra {:a "a"})
                   (extra {:b "b"}))]
        (is (= {:extra {:a "a" :b "b"}} ev)
            "should merge data maps")))))

(deftest test-user
  (testing "user"
    (testing "without event"
      (let [ev (user {:id "id"
                      :username "username"
                      :email "email"
                      :ip-address "ip"})]
        (is (= {:sentry.interfaces.user.User
                {:id "id"
                 :username "username"
                 :email "email"
                 :ip_address "ip"}}
               ev)
            "should create new event with User interface")))

    (testing "with event"
      (let [ev (user {:foo "foo"}
                     {:id "id"
                      :username "username"
                      :email "email"
                      :ip-address "ip"})]
        (is (= {:foo "foo"
                :sentry.interfaces.user.User
                {:id "id"
                 :username "username"
                 :email "email"
                 :ip_address "ip"}}
               ev)
            "should assoc User interface to event")))

    (testing "with subset of fields provided"
      (for [f [:id :username :email :ip-address]
            :let [fields (assoc {:id "id"
                                 :username "username"
                                 :email "email"
                                 :ip_address "ip"}
                           f
                           nil)
                  expected-ev {:sentry.interfaces.user.User
                               (dissoc {:id "id"
                                        :username "username"
                                        :email "email"
                                        :ip_address "ip"}
                                       f)}
                  actual-ev (user {} fields)]]
        (is (= actual-ev expected-ev)
            "should not assoc nil fields")))

    (testing "with unexpected arguments"
      (let [ev (user {} {:unexpted "argument"})]
        (is (= {:sentry.interfaces.user.User {}} ev)
            "should ignore unexpected arguments")))))

(deftest test-query
  (testing "query"
    (testing "without event"
      (let [ev (query {:query "query"
                       :engine "engine"})]
        (is (= {:sentry.interfaces.query.Query
                {:query "query"
                 :engine "engine"}}
               ev)
            "should create new event with Query interface")))

    (testing "with event"
      (let [ev (query {:foo "foo"}
                      {:query "query"
                       :engine "engine"})]
        (is (= {:foo "foo"
                :sentry.interfaces.query.Query
                {:query "query"
                 :engine "engine"}}
               ev)
            "should assoc Query interface to event")))

    (testing "with no :engine argument"
      (let [ev (query {:foo "foo"}
                      {:query "query"})]
        (is (= {:foo "foo"
                :sentry.interfaces.query.Query
                {:query "query"}}
               ev)
            "Query interface should contain :query field")))

    (testing "with no :query argument"
      (is (thrown? AssertionError (query {:engine "engine"}))
          "should assert query argument is provided"))

    (testing "with unexpected arguments"
      (let [ev (query {} {:query "query" :unexpted "argument"})]
        (is (= {:sentry.interfaces.query.Query {:query "query"}} ev)
            "should ignore unexpected arguments")))))
