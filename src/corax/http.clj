(ns corax.http)

(defn build-url
  [{:keys [scheme server-name server-port uri]}]
  (let [port-str (if (= server-port 80) "" (str ":" server-port))]
    (str (name scheme) "://" server-name port-str uri)))

(defn build-http-info
  [{:keys [headers request-method params query-string remote-addr] :as req}
   & [env]]
  {:data params
   :env (merge {:REMOTE_ADDR remote-addr} env)
   :headers headers
   :method (-> request-method name .toUpperCase)
   :query_string query-string
   :url (build-url req)})
