(ns corax.exception
  (:require [clj-stacktrace.core :refer [parse-exception]]))

(defn- build-java-frame
  [{:keys [method class line]}]
  {:function method
   :module class
   ;; File name overwrites module name in the Sentry interface
   ;; :filename file
   :lineno line})

(defn- build-clojure-frame
  [{:keys [fn ns line]}]
  {:function fn
   :module ns
   ;; File name overwrites module name in the Sentry interface
   ;; :filename file
   :lineno line})

(defn- build-frame
  [{:keys [clojure] :as frame}]
  (if clojure
    (build-clojure-frame frame)
    (build-java-frame frame)))

(defn- build-stacktrace
  [{:keys [trace-elems]}]
  {:frames (->> trace-elems
                reverse
                (map build-frame)
                vec)})

(defn build-exception-value
  [^Throwable ex]
  (let [parsed-ex (parse-exception ex)]
    {:type (-> parsed-ex :class .getName)
     :value (:message parsed-ex)
     :stacktrace (build-stacktrace parsed-ex)}))
