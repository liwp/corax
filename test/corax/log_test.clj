(ns corax.log-test
  (:require [corax.log :refer :all]
            [clojure.test :refer :all]))

(deftest test-corax-logger
  (testing "CoraxLogger"
    (let [logger (->CoraxLogger)]
      (testing "with success"
        (let [s (with-out-str
                  (log-event-id logger :mock-sentry-id :mock-event))]
          (is (.contains s "Corax") "prints out Corax label")
          (is (.contains s ":mock-sentry-id") "prints out Sentry ID")
          (is (.contains s ":mock-event") "prints out event")))

      (testing "with failure"
        (let [s (with-out-str
                  (log-failure logger :mock-http-response :mock-event))]
          (is (.contains s "Corax") "prints out Corax label")
          (is (.contains s ":mock-http-response") "prints out HTTP response")
          (is (.contains s ":mock-event") "prints out event")))

      (testing "with error"
        (let [ex (Exception. "dummy error")
              s (with-out-str (log-error logger ex :mock-event))]
          (is (.contains s "Corax") "prints out Corax label")
          (is (.contains s "dummy error") "should print out exception message")
          (is (.contains s ":mock-event") "should print out event"))))))
