(ns madek.zapi-sync.sync
  (:require
   [madek.zapi-sync.madek-api.people :as madek-api.people]
   [madek.zapi-sync.zapi.people :as zapi.people]
   [madek.zapi-sync.utils :refer [now-iso-local]]
   [taoensso.timbre :refer [debug info]]))

(defn- update-person [madek-api-config madek-person zapi-person]
  ;; NOTE: zapi-person can be nil (which is equivalent to inactive) 
  (let [active? (-> zapi-person :active? (or false))
        infos (-> zapi-person :infos (or []))
        status-change? (not= active? (-> madek-person :institutional_directory_inactive_since nil?))
        mutation-data (cond-> {}
                        status-change?
                        (assoc :institutional_directory_inactive_since
                               (if active? nil (now-iso-local)))
                        (not= infos (-> madek-person :institutional_directory_infos))
                        (assoc :institutional_directory_infos infos))]
    (if (empty? mutation-data)
      (do
        (debug "person " (:institutional_id madek-person) "is up to date (nothing to write)")
        :already-up-to-date)
      (do
        (madek-api.people/patch madek-api-config (:id madek-person) mutation-data)
        (if status-change?
          (if active? :reactivated :inactivated)
          :updated)))))

(defn- insert-person [madek-api-config institution zapi-person]
  (let [{:keys [id first-name last-name infos]} zapi-person
        data {:subtype "Person"
              :institution institution
              :institutional_id (str id)
              :first_name first-name
              :last_name last-name
              :institutional_directory_infos infos}]
    (madek-api.people/post madek-api-config data)
    :inserted))

(defn- push-person
  "Push data to Madek from given ZAPI person (insert, update, reactivate)"
  [madek-api-config institution zapi-person]
  (let [madek-person (madek-api.people/fetch-one-by-institutional-id madek-api-config institution (-> zapi-person :id str))]
    (if madek-person
      (update-person madek-api-config madek-person zapi-person)
      (insert-person madek-api-config institution zapi-person))))

(defn- pull-person
  "Pull data from ZAPI to given Madek person (update, reactivate, inactivate)"
  [zapi-config madek-api-config madek-person]
  (let [zapi-person (zapi.people/fetch-person zapi-config (:institutional_id madek-person))]
    (update-person madek-api-config madek-person zapi-person)))

;; public

(defn sync-people
  "Insert or update (potentially re-activate) people which are active in ZAPI, and inactivate those who are not"
  [zapi-config madek-api-config institution]
  (let [zapi-people (do (info "Fetching ZAPI data...")
                        (zapi.people/fetch-active-people
                         zapi-config
                         {}))
        madek-people (do (info "Fetching Madek data...")
                         (madek-api.people/fetch-all-of-institution
                          madek-api-config
                          {:institution institution :active? true}))]
    (info "Syncing to Madek...")
    (concat
     (->> zapi-people
          (map #(push-person madek-api-config institution %)))
     (->> madek-people
          (filter (fn [p] (not (some
                                #(= (p :institutional_id) (-> % :id str))
                                zapi-people))))
          (map #(pull-person zapi-config madek-api-config %))))))

(defn sync-inactive-people
  "Update people which are inactive in Madek and presumably also in ZAPI (intended to pull historical data once when needed)"
  [zapi-config madek-api-config institution]
  (let [madek-people (do (info "Fetching Madek inactive people data...")
                         (madek-api.people/fetch-all-of-institution
                          madek-api-config
                          {:institution institution :active? false}))]
    (info "Update inactive people...")
    (->> madek-people
         (map #(pull-person zapi-config madek-api-config %)))))

(defn push-people
  "Push data to Madek from list of a given list of ZAPI people (insert, update, reactivate)"
  [madek-api-config zapi-people institution]
  (->> zapi-people
       (map #(push-person madek-api-config institution %))))

(defn update-single-person
  "Update single Madek person (update, reactivate, inactivate)"
  [zapi-config madek-api-config institution institutional-id]
  (let [madek-person (madek-api.people/fetch-one-by-institutional-id madek-api-config institution institutional-id)]
    (if madek-person
      [(pull-person zapi-config madek-api-config madek-person)]
      (throw (Exception. (str "Madek person not found (" institution " " institutional-id ")"))))))
