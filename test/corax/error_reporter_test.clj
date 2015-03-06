(ns corax.error-reporter-test
  (:require [clojure.test :refer :all]
            [corax.error-reporter :refer :all]
            [corax.log :as log]
            [raven-clj.core :as raven])
  (:import [com.fasterxml.jackson.core JsonGenerationException]))

(defrecord MockLogger []
  log/Logger
  (log-event-id [this sentry-id event]
    [:ok sentry-id event])

  (log-failure [this http-response event]
    [:failure http-response event])

  (log-error [this exception event]
    [:error exception event]))

(deftest test-report*
  (testing "report*"
    (let [report-id "12345"
          logger (->MockLogger)]

      (testing "success"
        (with-redefs [corax.error-reporter/utc (fn [] "timestamp")
                      raven/capture
                      (fn [dsn event]
                        (is (= dsn :mock-dsn))
                        {:status 200
                         :body (format "{\"id\":\"%s\"}" report-id)})]
          (testing "with default :level, :platform, and :timestamp"
            (let [mock-result (report* {} :mock-dsn logger)]
              (is (= mock-result
                     [:ok report-id {:level :error
                                     :platform :clojure
                                     :timestamp "timestamp"}]))))

          (testing "with provided :level, :platform, and :timestamp"
            (let [event {:level :my-level
                         :platform :my-platform
                         :timestamp :my-timestamp}
                  mock-result (report* event :mock-dsn logger)]
              (is (= mock-result [:ok report-id event]))))))

      (testing "with unexpected HTTP status code"
        (let [response {:status 400}]
          (with-redefs [corax.error-reporter/utc (fn [] "timestamp")
                        raven/capture (fn [dsn event] response)]
            (let [mock-result (report* {} :mock-dsn logger)]
              (is (= mock-result
                     [:failure response {:level :error
                                         :platform :clojure
                                         :timestamp "timestamp"}])))))))))

(deftest test-report-json-generation-error
  (testing "report-json-generation-error"
    (with-redefs [report* (fn [event dsn logger]
                            {:event event :dsn dsn :logger logger})]
      (let [mock-result (report-json-generation-error
                         (Exception. "foo") :mock-dsn :mock-logger)]
        (is (= (:dsn mock-result) :mock-dsn))
        (is (= (:logger mock-result) :mock-logger))
        (is (-> mock-result :event :message (.contains "JSON")))
        (let [exception (-> mock-result :event :exception :values first)]
          (is (= (:type exception) "java.lang.Exception"))
          (is (= (:value exception) "foo")))))))

(deftest test-corax-error-reporter
  (testing "CoraxErrorReporter"
    (let [report-id "12345"
          logger (->MockLogger)
          error-reporter (->CoraxErrorReporter :mock-dsn logger)]

      (testing "with success"
        (with-redefs [report* (fn [event dsn logger])]
          (let [mock-result (-report error-reporter :mock-event)]
            (is (= mock-result nil)))))

      (testing "with JSON generation error"
        (let [ex (JsonGenerationException. "JSON generation error")]
          (with-redefs [report*
                        (fn [event dsn logger] (throw ex))
                        report-json-generation-error
                        (fn [event dsn logger]
                          :report-json-generation-error)]
            (let [mock-result (-report error-reporter :mock-event)]
              (is (= mock-result :report-json-generation-error))))))

      (testing "with error when reporting JSON error"
        (let [json-ex (JsonGenerationException. "JSON generation error")
              reporting-ex (Exception. "dummy error")]
          (with-redefs [report*
                        (fn [event dsn logger] (throw json-ex))
                        report-json-generation-error
                        (fn [event dsn logger]
                          (throw reporting-ex))]
            (let [mock-result (-report error-reporter :mock-event)]
              (is (= mock-result [:error reporting-ex :mock-event]))))))

      (testing "with unexpected exception"
        (let [ex (Exception. "dummy error")]
          (with-redefs [report* (fn [event dsn logger] (throw ex))]
            (let [mock-result (-report error-reporter :mock-event)]
              (is (= mock-result [:error ex :mock-event])))))))))
