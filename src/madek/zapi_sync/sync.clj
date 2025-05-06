(ns madek.zapi-sync.sync
  (:require
   [madek.zapi-sync.madek-api.people :as madek-api.people]
   [madek.zapi-sync.zapi.people :as zapi.people]
   [madek.zapi-sync.utils :refer [now-iso-local]]
   [taoensso.timbre :refer [debug]]))

(defn- update-person [madek-api-config madek-person zapi-person]
  ;; NOTE: zapi-person can be nil (which is equivalent to inactive)
  (debug "update-person" (:institutional_id madek-person))
  (let [active? (-> zapi-person :active? (or false))
        infos (-> zapi-person :infos (or []))
        mutation-data (cond-> {}
                        (not= active? (-> madek-person :institutional_directory_inactive_since nil?))
                        (assoc :institutional_directory_inactive_since
                               (if active? nil (now-iso-local)))
                        (not= infos (-> madek-person :institutional_directory_infos))
                        (assoc :institutional_directory_infos infos))]
    (if (empty? mutation-data)
      (debug "person is up to date (nothing to write)")
      (madek-api.people/patch madek-api-config (:id madek-person) mutation-data))))

(defn- insert-person [madek-api-config institution zapi-person]
  (debug "insert-person" (:id zapi-person))
  (let [{:keys [id first-name last-name infos]} zapi-person
        data {:subtype "Person"
              :institution institution
              :institutional_id (str id)
              :first_name first-name
              :last_name last-name
              :institutional_directory_infos infos}]
    (madek-api.people/post madek-api-config data)))

(defn- push-person
  "Push data to Madek from given ZAPI person (insert, update, reactivate)"
  [madek-api-config institution zapi-person]
  (debug "push-person" (-> zapi-person :id str))
  (let [madek-person (madek-api.people/fetch-one-by-institutional-id madek-api-config institution (-> zapi-person :id str))]
    (if madek-person
      (update-person madek-api-config madek-person zapi-person)
      (insert-person madek-api-config institution zapi-person))))

(defn- pull-person
  "Pull data from ZAPI to given Madek person (update, reactivate, inactivate)"
  [zapi-config madek-api-config madek-person]
  (debug "pull-person" (:institutional_id madek-person))
  (let [zapi-person (zapi.people/fetch-person zapi-config (:institutional_id madek-person))]
    (update-person madek-api-config madek-person zapi-person)))

;; public

(defn sync-people
  "Insert or update (potentially re-activate) people which are active in ZAPI, and inactivate those who are not"
  [zapi-config madek-api-config institution]
  (let [zapi-people (zapi.people/fetch-active-people
                     zapi-config
                     {})
        madek-people (madek-api.people/fetch-all-of-institution
                      madek-api-config
                      {:institution institution :active? true})]
    (->> zapi-people
         (run! #(push-person madek-api-config institution %)))
    (->> madek-people
         (filter (fn [p] (not (some
                               #(= (p :institutional_id) (-> % :id str))
                               zapi-people))))
         (run! #(pull-person zapi-config madek-api-config %)))))

(defn sync-inactive-people
  "Update people which are inactive in Madek and presumably also in ZAPI (meant to pull historic data once when needed)"
  [zapi-config madek-api-config institution]
  (let [madek-people (madek-api.people/fetch-all-of-institution
                      madek-api-config
                      {:institution institution :active? false})]
    (->> madek-people
         (run! #(pull-person zapi-config madek-api-config %)))))

(defn push-people
  "Push data to Madek from list of a given list of ZAPI people (insert, update, reactivate)"
  [madek-api-config zapi-people institution]
  (->> zapi-people
       (run! #(push-person madek-api-config institution %))))

(defn update-single-person
  "Update single Madek person (update, reactivate, inactivate)"
  [zapi-config madek-api-config institution institutional-id]
  (let [madek-person (madek-api.people/fetch-one-by-institutional-id madek-api-config institution institutional-id)]
    (if madek-person
      (pull-person zapi-config madek-api-config madek-person)
      (throw (Exception. (str "Madek person not found (" institution " " institutional-id ")"))))))
