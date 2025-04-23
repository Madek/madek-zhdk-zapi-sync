(ns madek.zapi-sync.madek-api
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as http-client]
   [clojure.pprint :refer [pprint]]
   [taoensso.timbre :refer [debug]]))

(defn- fetch-one
  [{:keys [base-url auth-header]} institution institutional-id]
  (let [url (str base-url "admin/people" "?"
                 (http-client/generate-query-string
                  {:institution institution
                   :institutional_id institutional-id}))]
    (debug "fetching" url)
    (-> (http-client/get url {:as :json
                              :headers {"Authorization" auth-header}})
        :body :people first)))

(defn- post
  [{:keys [base-url auth-header]} institution {:keys [id first-name last-name infos]}]
  (let [url (str base-url "admin/people")
        data {:subtype "Person"
              :institution institution
              :institutional_id (str id)
              :first_name first-name
              :last_name last-name
              :institutional_directory_infos infos}]
    (debug "posting" url data)
    (-> (http-client/post url {:as :json
                               :content-type :json
                               :form-params data
                               :headers {"Authorization" auth-header}}))))

(defn- patch
  [{:keys [base-url auth-header]} madek-id zapi-infos]
  (let [url (str base-url "admin/people/" madek-id)
        data {:institutional_directory_infos zapi-infos}]
    (debug "patching" url data)
    (-> (http-client/patch url {:as :json
                                :content-type :json
                                :form-params data
                                :headers {"Authorization" auth-header}})
        :body)))

(defn sync-one [madek-api-config
                institution
                zapi-person]
  (debug "sync-one" (:id zapi-person))
  (let [madek-person (fetch-one madek-api-config institution (:id zapi-person))]
    (if madek-person
      (if (= (:institutional_directory_infos madek-person) (:infos zapi-person))
        (debug "person present in Madek, infos unmodified (nothing to write)")
        (do (debug "DIFF" (:institutional_directory_infos madek-person) (:infos zapi-person))
            (patch madek-api-config (:id madek-person) (:infos zapi-person))
            (debug "ok, patched")))
      (do (post madek-api-config institution zapi-person)
          (debug "ok, posted")))))

(defn sync-many [madek-api-config institution people]
  (->> people (run! #(sync-one madek-api-config institution %))))
