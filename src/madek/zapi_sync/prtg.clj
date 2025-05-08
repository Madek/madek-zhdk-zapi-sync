(ns madek.zapi-sync.prtg
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as http-client]
   [taoensso.timbre :refer [debug info]]))

(defn- post [prtg-url msg]
  (let [body (cheshire/generate-string msg)]
    (debug prtg-url body)
    (http-client/post
     prtg-url
     {:accept :json
      :content-type :json
      :as :json
      :body body})))

(def channel-map {:already-up-to-date "people-unmodified-count"
                  :updated "people-updated-count"
                  :inserted "people-inserted-count"
                  :inactivated "people-inactivated-count"
                  :reactivated "people-reactivated-count"})

(defn send-success [prtg-url stats]
  (let [msg {:prtg
             {:result
              (map (fn [[k v]] {:channel (get channel-map k k) :unit "Count" :value v}) stats)}}]
    (info "send-success" msg)
    (post prtg-url msg)))

(defn send-error [prtg-url msg]
  (let [msg {:prtg
             {:error 1
              :text msg}}]
    (info "send-error" msg)
    (post prtg-url msg)))
