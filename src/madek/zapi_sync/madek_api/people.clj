(ns madek.zapi-sync.madek-api.people
  (:require
   [clj-http.client :as http-client]
   [madek.zapi-sync.utils :refer [non-empty-string?]]
   [taoensso.timbre :refer [debug]]))

;; Reading from Madek API V2

(defn fetch-one-by-institutional-id
  [{:keys [base-url auth-header]} institution institutional-id]
  (assert (non-empty-string? institution))
  (assert (non-empty-string? institutional-id))
  (let [url (str base-url "admin/people" "?"
                 (http-client/generate-query-string
                  {:institution institution
                   :institutional_id institutional-id}))]
    (debug "fetching" url)
    (-> (http-client/get url {:as :json
                              :headers {"Authorization" auth-header}})
        :body :people first)))

(defn fetch-all-of-institution
  [{:keys [base-url auth-header]} {:keys [institution active?]}]
  (assert (non-empty-string? institution))
  (let [url (str base-url
                 "admin/people" "?"
                 (http-client/generate-query-string {:subtype "Person" :institution institution}))]
    (debug "fetching" url)
    (->> (http-client/get url {:as :json
                               :headers {"Authorization" auth-header}})
         :body :people
         (filter :institutional_id)
         (filter (fn [p] (or (nil? active?)
                             (= active? (-> p :institutional_directory_inactive_since some?))))))))

;; Writing to Madek API V2

(defn post
  [{:keys [base-url auth-header]} data]
  (let [url (str base-url "admin/people")]
    (debug "posting" url data)
    (-> (http-client/post url {:as :json
                               :content-type :json
                               :form-params data
                               :headers {"Authorization" auth-header}}))))

(defn patch
  [{:keys [base-url auth-header]} madek-id data]
  (assert (non-empty-string? madek-id))
  (let [url (str base-url "admin/people/" madek-id)]
    (debug "patching" url data)
    (-> (http-client/patch url {:as :json
                                :content-type :json
                                :form-params data
                                :headers {"Authorization" auth-header}})
        :body)))
