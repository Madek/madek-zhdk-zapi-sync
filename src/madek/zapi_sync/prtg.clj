(ns madek.zapi-sync.prtg
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as http-client]
   [taoensso.timbre :refer [debug info]]))

(defn- post [prtg-url msg]
  (debug prtg-url msg)
  (http-client/post
   prtg-url
   {:accept :json
    :content-type :json
    :as :json
    :body
    (cheshire/generate-string msg)}))

(defn send-success [prtg-url stats]
  (let [msg {:prtg
             {:result
              (map (fn [[k v]] {:channel k :unit "Count" :value v}) stats)}}]
    (info "send-success" msg)
    (post prtg-url msg)))

(defn send-error [prtg-url msg]
  (let [msg {:prtg
             {:error 1
              :text msg}}]
    (info "send-error" msg)
    (post prtg-url msg)))
