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
                                         :event {:my :event}}))]
          (is (.contains s (:exception err/error-messages))
              "should print out message")
          (is (.contains s "dummy error") "should print out exception")
          (is (.contains s "{:my :event}") "should print out event"))))

    (testing "without exception"
      (let [s (with-out-str
                (#'err/default-log-fn {:error :no-dsn
                                              :event {:my :event}}))]
        (is (.contains s (:no-dsn err/error-messages))
            "should print out message")
        (is (.contains s "{:my :event}") "should print out event")))

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
        (with-redefs [raven/capture (fn [dsn event]
                                      (reset! capture-called true)
                                      (is (= dsn "dummy dsn"))
                                      (is (= event {})))]
          (#'err/handle-event {} "dummy dsn" (fn []))
          (is (true? @capture-called) "should call capture"))))

    (testing "with exception"
      (let [ex (Exception. "dummy error")
            log-fn-called (atom nil)
            log-fn (fn [{:keys [error exception event]}]
                     (reset! log-fn-called true)
                     (is (= error :exception))
                     (is (= event {})))]
        (with-redefs [raven/capture (fn [dsn event] (throw ex))]
          (#'err/handle-event {} "dummy dsn" log-fn))
        (is (true? @log-fn-called) "should call log-fn")))))

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
