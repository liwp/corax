(ns corax.error-reporter-test
  (:require [corax.error-reporter :as err]
            [clojure.test :refer :all]
            [raven-clj.core :as raven])
  (:import [com.fasterxml.jackson.core JsonGenerationException]))

(deftest test-default-log-fn
  (testing "default-log-fn"
    (testing "with success"
      (let [s (with-out-str
                (#'err/default-log-fn {:id :mock-id
                                       :event :mock-event}))]
        (is (.contains s ":mock-id") "should print out ID")
        (is (.contains s ":mock-event") "should print out event")))

    (testing "with :exception"
      (let [ex (Exception. "dummy error")
            s (with-out-str
                (#'err/default-log-fn {:error :exception
                                       :exception ex
                                       :event :mock-event}))]
        (is (.contains s (:exception err/error-messages))
            "should print out message")
        (is (.contains s "dummy error") "should print out exception")
        (is (.contains s ":mock-event") "should print out event")))

    (testing "with :http-status"
      (let [s (with-out-str
                (#'err/default-log-fn {:error :http-status
                                       :response :mock-http-response
                                       :event :mock-event}))]
        (is (.contains s (:http-status err/error-messages))
            "should print out message")
        (is (.contains s ":mock-http-response") "should print out response")
        (is (.contains s ":mock-event") "should print out event")))

    (testing "with :invalid-dsn"
      (let [s (with-out-str
                (#'err/default-log-fn {:error :invalid-dsn
                                       :dsn :mock-dsn
                                       :event :mock-event}))]
        (is (.contains s (:invalid-dsn err/error-messages))
            "should print out message")
        (is (.contains s ":mock-dsn") "should print out DSN")
        (is (.contains s ":mock-event") "should print out event")))

    (testing "with :invalid-payload"
      (let [ex (JsonGenerationException. "JSON generation error")
            s (with-out-str
                (#'err/default-log-fn {:error :invalid-payload
                                       :exception ex
                                       :event :mock-event}))]
        (is (.contains s (:invalid-payload err/error-messages))
            "should print out message")
        (is (.contains s "JSON generation error") "should print out exception")
        (is (.contains s ":mock-event") "should print out event")))

    (testing "with :no-dsn"
      (let [s (with-out-str
                (#'err/default-log-fn {:error :no-dsn
                                       :event :mock-event}))]
        (is (.contains s (:no-dsn err/error-messages))
            "should print out message")
        (is (.contains s ":mock-event") "should print out event")))

    (testing "with unknown error"
      (let [s (with-out-str
                (#'err/default-log-fn {:error :unknown-error
                                       :event {:my :event}}))]
        (is (.contains s "Unknown error: :unknown-error")
            "should print out message")
        (is (.contains s "{:my :event}") "should print out event")))))

(deftest test-handle-event
  (testing "handle-event"
    (testing "with success"
      (let [log-fn-called (atom nil)
            log-fn (fn [{:keys [event id]}]
                     (reset! log-fn-called true)
                     (is (= event :mock-event))
                     (is (= id "711a0503...")))]
        (with-redefs [raven/capture
                      (fn [dsn event]
                        (is (= dsn :mock-dsn))
                        (is (= event :mock-event))
                        {:status 200
                         :body "{\"id\":\"711a0503...\"}"})]
          (is (nil? (#'err/handle-event :mock-event :mock-dsn log-fn)))
          (is (true? @log-fn-called) "should call log-fn"))))

    (testing "with unexpected HTTP status code"
      (let [log-fn-called (atom nil)
            log-fn (fn [{:keys [error response event]}]
                     (reset! log-fn-called true)
                     (is (= response {:status 400}))
                     (is (= event :mock-event)))]
        (with-redefs [raven/capture (fn [dsn event]
                                      (is (= dsn :mock-dsn))
                                      (is (= event :mock-event))
                                      {:status 400})]
          (is (nil? (#'err/handle-event :mock-event :mock-dsn log-fn)))
          (is (true? @log-fn-called) "should call log-fn"))))

    (testing "with NPE (caused by a DSN parse error)"
      (let [ex (NullPointerException. "DSN parse error")
            log-fn-called (atom nil)
            log-fn (fn [{:keys [error exception event]}]
                     (reset! log-fn-called true)
                     (is (= error :invalid-dsn))
                     (is (= event :mock-event)))]
        (with-redefs [raven/capture (fn [dsn event]
                                      (is (= dsn :mock-dsn))
                                      (is (= event :mock-event))
                                      (throw ex))]
          (is (nil? (#'err/handle-event :mock-event :mock-dsn log-fn)))
          (is (true? @log-fn-called) "should call log-fn"))))

    (testing "with JSON generation error"
      (let [ex (JsonGenerationException. "JSON generation error")
            log-fn-called (atom nil)
            log-fn (fn [{:keys [error exception event]}]
                     (reset! log-fn-called true)
                     (is (= error :invalid-payload))
                     (is (= event :mock-event)))]
        (with-redefs [raven/capture (fn [dsn event]
                                      (is (= dsn :mock-dsn))
                                      (is (= event :mock-event))
                                      (throw ex))]
          (is (nil? (#'err/handle-event :mock-event :mock-dsn log-fn)))
          (is (true? @log-fn-called) "should call log-fn"))))

    (testing "with unexpected exception"
      (let [ex (Exception. "dummy error")
            log-fn-called (atom nil)
            log-fn (fn [{:keys [error exception event]}]
                     (reset! log-fn-called true)
                     (is (= error :exception))
                     (is (= event :mock-event)))]
        (with-redefs [raven/capture (fn [dsn event]
                                      (is (= dsn :mock-dsn))
                                      (is (= event :mock-event))
                                      (throw ex))]
          (is (nil? (#'err/handle-event :mock-event :mock-dsn log-fn)))
          (is (true? @log-fn-called) "should call log-fn"))))))

(deftest test-report
  (testing "report"
    (testing "with DSN"
      (let [capture-called (atom nil)
            log-fn (fn [m])]
        (with-redefs [raven/capture (fn [dsn event]
                                      (reset! capture-called true)
                                      (is (= dsn "dummy dsn"))
                                      (is (= event {})))]
          (err/report {} "dummy dsn" log-fn)
          (is (true? @capture-called) "should call capture"))))

    (testing "without DSN"
      (let [log-fn-called (atom nil)
            log-fn (fn [{:keys [error event]}]
                     (reset! log-fn-called true)
                     (is (= error :no-dsn))
                     (is (= event {})))]
        (err/report {} nil log-fn)
        (is (true? @log-fn-called) "should call log-fn")))))
