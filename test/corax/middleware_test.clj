(ns corax.middleware-test
  (:require [corax.core :as err]
            [corax.middleware :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]))

(deftest test-wrap-exception-reporting
  (testing "wrap-exception-reporting"
    (let [ex (Exception. "error")
          handler (wrap-exception-reporting
                   (fn [req] (throw ex))
                   {:dsn :mock-dsn
                    :log-fn :mock-log-fn})
          report-called (atom nil)]
      (with-redefs [err/report
                    (fn [event {:keys [dsn log-fn]}]
                      (reset! report-called true)
                      (is (= (-> event :message) "error"))
                      (is (some? (:exception event)))
                      (is (some? (:sentry.interfaces.Http event)))
                      (is (= (-> event :sentry.interfaces.Http :method) "GET"))
                      (is (= dsn :mock-dsn))
                      (is (= log-fn :mock-log-fn)))]
        (is (thrown-with-msg?
             Exception #"error"
             (handler (mock/request :get "/"))))
        (is (true? @report-called))))))
