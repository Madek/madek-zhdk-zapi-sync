(ns madek.zapi-sync.madek-api
  (:require
   [clj-http.client :as http-client]
   [madek.zapi-sync.utils :refer [now-iso-local]]
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

(defn- fetch-institutional-people
  [{:keys [base-url auth-header]} institution]
  (let [url (str base-url
                 "admin/people" "?"
                 (http-client/generate-query-string {:subtype "Person" :institution institution}))]
    (debug "fetching" url)
    (->> (http-client/get url {:as :json
                               :headers {"Authorization" auth-header}})
         :body :people
         (filter :institutional_id))))

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

(defn- sync-one [madek-api-config
                 institution
                 zapi-person]
  (debug "sync-one" (:id zapi-person))
  (let [madek-person (fetch-one madek-api-config institution (:id zapi-person))]
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

(defn- deactivate-one [madek-api-config madek-person]
  (debug "deactivate-one" (:institutional_id madek-person))
  (let [infos (->> madek-person
                   :institutional_directory_infos
                   (remove #{"Staff" "Faculty"})
                   vec)
        data {:institutional_directory_infos infos
              :institutional_directory_inactive_since (now-iso-local)}]
    (patch madek-api-config (:id madek-person) data)
    (debug "ok, deactivated")))

(defn- history-sync-one [madek-api-config fetch-from-zapi madek-person]
  (debug "history-sync-one" (:institutional_id madek-person))
  (let [zapi-person (fetch-from-zapi (:institutional_id madek-person))]
    (when zapi-person
      (if (= (:institutional_directory_infos madek-person) (:infos zapi-person))
        (debug "Infos unmodified (nothing to write)")
        (let [data {:institutional_directory_infos (:infos zapi-person)}]
          (patch madek-api-config (:id madek-person) data))))))

;; Tasks

(defn deactivation-task [madek-api-config institution zapi-people]
  (->> (fetch-institutional-people madek-api-config institution)
       (filter (fn [p] (-> p :institutional_directory_inactive_since not)))
       (filter (fn [p] (not (some #(-> p :institutional_id (= (-> % :id str))) zapi-people))))
       (run! #(deactivate-one madek-api-config %))))

(defn sync-task [madek-api-config institution zapi-people]
  (->> zapi-people
       (run! #(sync-one madek-api-config institution %))))

(defn history-sync-task [madek-api-config institution fetch-from-zapi]
  (->> (fetch-institutional-people madek-api-config institution)
       (filter (fn [p] (-> p :institutional_directory_inactive_since)))
       (run! #(history-sync-one madek-api-config fetch-from-zapi %))))
