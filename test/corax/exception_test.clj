(ns corax.exception-test
  (:require [corax.exception :refer :all]
            [clojure.test :refer :all]))

(deftest test-build-frame
  (testing "build-frame"
    (testing "with clojure frame"
      (let [frame (#'corax.exception/build-frame {:clojure true
                                                  :fn ::fn-name
                                                  :ns ::ns-name
                                                  :line 12345})]
        (is (= frame {:function ::fn-name
                      :module ::ns-name
                      :lineno 12345}))))

    (testing "with java frame"
      (let [frame (#'corax.exception/build-frame {:clojure false
                                                  :method "getName"
                                                  :class "java.lang.Class"
                                                  :line 12345})]
        (is (= frame {:function "getName"
                      :module "java.lang.Class"
                      :lineno 12345}))))))

(deftest test-build-exception-value
  (testing "build-exception-value"
    (let [ex (Exception. "my message")
          val (build-exception-value ex)]
      (is (= (:type val) "java.lang.Exception"))
      (is (= (:value val) "my message"))
      (is (not= (get-in val [:stacktrace :frames] :missing) :missing)))))
