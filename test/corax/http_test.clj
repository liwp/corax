(ns corax.http-test
  (:require [corax.http :refer :all]
            [clojure.test :refer :all]))

(def default-request
  {:remote-addr "127.0.0.1"
   :request-method :get
   :scheme :http
   :server-name "example.com"
   :server-port 80
   :uri "/"})

(deftest test-build-url
  (testing "with server port 80"
    (is (= (build-url default-request)
           "http://example.com/")))

  (testing "with server port 443"
    (is (= (build-url (assoc default-request :server-port 443))
           "http://example.com:443/")))

  (testing "with no uri"
    (is (= (build-url (dissoc default-request :uri))
           "http://example.com"))))

(deftest test-build-http-info
  (testing "method"
    (let [http-info (build-http-info default-request)]
      (is (= (:method http-info) "GET"))))

  (testing ":env"
    (testing "with no env map"
      (let [http-info (build-http-info default-request)]
        (is (= (:env http-info)
               {:REMOTE_ADDR "127.0.0.1"}))))

    (testing "with env map"
      (let [http-info (build-http-info default-request {:foo :bar})]
        (is (= (:env http-info)
               {:foo :bar :REMOTE_ADDR "127.0.0.1"}))))

    (testing "with env map overriding :REMOTE_ADDR"
      (let [http-info (build-http-info default-request {:REMOTE_ADDR "foo"})]
        (is (= (:env http-info)
               {:REMOTE_ADDR "foo"}))))))
