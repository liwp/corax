(ns corax.error-reporter-test
  (:require [corax.error-reporter :as err]
            [clojure.test :refer :all]
            [raven-clj.core :as raven]))

(deftest test-default-log-fn
  (testing "default-log-fn"
    (testing "with exception"
      (let [ex (Exception. "dummy error")]
        (let [s (with-out-str
                  (#'err/default-log-fn {:error :exception
                                         :exception ex
                                         :event :mock-event}))]
          (is (.contains s (:exception err/error-messages))
              "should print out message")
          (is (.contains s "dummy error") "should print out exception")
          (is (.contains s ":mock-event") "should print out event"))))

    (testing "with missing DSN"
      (let [s (with-out-str
                (#'err/default-log-fn {:error :no-dsn
                                       :event :mock-event}))]
        (is (.contains s (:no-dsn err/error-messages))
            "should print out message")
        (is (.contains s ":mock-event") "should print out event")))

    (testing "with invalid HTTP status code"
      (let [s (with-out-str
                (#'err/default-log-fn {:error :http-status
                                       :response :mock-http-response
                                       :event :mock-event}))]
        (is (.contains s (:http-status err/error-messages))
            "should print out message")
        (is (.contains s ":mock-http-response") "should print out response")
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
      (let [capture-called (atom nil)]
        (with-redefs [raven/capture
                      (fn [dsn event]
                        (reset! capture-called true)
                        (is (= dsn "dummy dsn"))
                        (is (= event {}))
                        {:status 200
                         :body "{\"id\":\"711a050314874ff08e18d14b27a2dbec\"}"})]
          (let [report-id (#'err/handle-event {} "dummy dsn" (fn [& args]))]
            (is (true? @capture-called) "should call capture")
            (is (= report-id "711a050314874ff08e18d14b27a2dbec"))))))

    (testing "with unexpected HTTP status code"
      (let [log-fn-called (atom nil)
            log-fn (fn [{:keys [error response event]}]
                     (reset! log-fn-called true)
                     (is (= response {:status 400}))
                     (is (= event {})))]
        (with-redefs [raven/capture (fn [dsn event]
                                      {:status 400})]
          (let [report-id (#'err/handle-event {} "dummy dsn" log-fn)]
            (is (true? @log-fn-called) "should call log-fn")
            (is (nil? report-id))))))

    (testing "with exception"
      (let [ex (Exception. "dummy error")
            log-fn-called (atom nil)
            log-fn (fn [{:keys [error exception event]}]
                     (reset! log-fn-called true)
                     (is (= error :exception))
                     (is (= event {})))]
        (with-redefs [raven/capture (fn [dsn event] (throw ex))]
          (let [report-id (#'err/handle-event {} "dummy dsn" log-fn)]
            (is (true? @log-fn-called) "should call log-fn")
            (is (nil? report-id))))))))

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
