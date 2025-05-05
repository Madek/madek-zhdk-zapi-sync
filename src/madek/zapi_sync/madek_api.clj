(ns madek.zapi-sync.madek-api
  (:require
   [clj-http.client :as http-client]
   [madek.zapi-sync.utils :refer [now-iso-local]]
   [taoensso.timbre :refer [debug]]))

;; Reading from Madek API V2

(defn- fetch-person-by-institutional-id
  [{:keys [base-url auth-header]} institution institutional-id]
  (let [url (str base-url "admin/people" "?"
                 (http-client/generate-query-string
                  {:institution institution
                   :institutional_id institutional-id}))]
    (debug "fetching" url)
    (-> (http-client/get url {:as :json
                              :headers {"Authorization" auth-header}})
        :body :people first)))

(defn- fetch-people-of-institution
  "Fetch all people from Madek API v2 which belong to the institution"
  [{:keys [base-url auth-header]} institution]
  (let [url (str base-url
                 "admin/people" "?"
                 (http-client/generate-query-string {:subtype "Person" :institution institution}))]
    (debug "fetching" url)
    (->> (http-client/get url {:as :json
                               :headers {"Authorization" auth-header}})
         :body :people
         (filter :institutional_id))))

;; Writing to Madek API V2

(defn- post
  [{:keys [base-url auth-header]} data]
  (let [url (str base-url "admin/people")]
    (debug "posting" url data)
    (-> (http-client/post url {:as :json
                               :content-type :json
                               :form-params data
                               :headers {"Authorization" auth-header}}))))

(defn- patch
  [{:keys [base-url auth-header]} madek-id data]
  (let [url (str base-url "admin/people/" madek-id)]
    ;; NOTE: The sync must never overwrite person's first/last name!
    (debug "patching" url data)
    (-> (http-client/patch url {:as :json
                                :content-type :json
                                :form-params data
                                :headers {"Authorization" auth-header}})
        :body)))

;; Tasks (single person)

(defn- sync-one-active [madek-api-config institution zapi-person]
  (debug "sync-one-active" (:id zapi-person))
  (let [madek-person (fetch-person-by-institutional-id madek-api-config institution (:id zapi-person))]
    (if madek-person

      ;; update existing madek person
      (if (= (:institutional_directory_infos madek-person) (:infos zapi-person))
        (debug "person present in Madek, infos unmodified (nothing to write)")
        (let [data {:institutional_directory_infos (:infos zapi-person)}]
          (patch madek-api-config (:id madek-person) data)
          (debug "ok, patched")))

      ;; create new madek person
      (let [{:keys [id first-name last-name infos]} zapi-person
            data {:subtype "Person"
                  :institution institution
                  :institutional_id (str id)
                  :first_name first-name
                  :last_name last-name
                  :institutional_directory_infos infos}]
        (post madek-api-config data)
        (debug "ok, posted")))))

(defn- sync-one-inactive [madek-api-config get-zapi-person madek-person]
  (debug "sync-one-inactive" (:institutional_id madek-person))
  (let [zapi-person (get-zapi-person (:institutional_id madek-person))
        infos (if zapi-person (:infos zapi-person) [])
        mutation-data (cond-> {}
                        (not= infos (:institutional_directory_infos madek-person))
                        (assoc :institutional_directory_infos infos)
                        (not (:institutional_directory_inactive_since madek-person))
                        (assoc :institutional_directory_inactive_since (now-iso-local)))]
    (cond
      (-> zapi-person :active?)
      (debug "person is NOT inactive (nothing to write)")
      (empty? mutation-data)
      (debug "person is already inactive and infos are unmodified (nothing to write)")
      :else
      (patch madek-api-config (:id madek-person) mutation-data))))

;; Tasks (multiple people)

(defn deactivation-task
  "Deactivate people which are active yet in Madek, but not in ZAPI anymore"
  [madek-api-config institution active-zapi-people get-zapi-person]
  (->> (fetch-people-of-institution madek-api-config institution)
       (filter (fn [p] (-> p :institutional_directory_inactive_since not)))
       (filter (fn [p] (not (some #(-> p :institutional_id (= (-> % :id str))) active-zapi-people))))
       (run! #(sync-one-inactive madek-api-config get-zapi-person %))))

(defn sync-task
  "Update, reactivate or insert people to Madek, which are active in ZAPI"
  [madek-api-config institution active-zapi-people]
  (->> active-zapi-people
       (run! #(sync-one-active madek-api-config institution %))))

(defn history-sync-task
  "Update people which are inactive in Madek"
  [madek-api-config institution get-zapi-person]
  (->> (fetch-people-of-institution madek-api-config institution)
       (filter :institutional_directory_inactive_since)
       (run! #(sync-one-inactive madek-api-config get-zapi-person %))))
