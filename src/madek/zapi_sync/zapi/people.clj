(ns madek.zapi-sync.zapi.people
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join]]
   [madek.zapi-sync.utils :refer [batch-fetcher non-blank-string?]]
   [madek.zapi-sync.zapi.study-classes :as study-classes]
   [madek.zapi-sync.zapi.utils :refer [fetch]]
   [taoensso.timbre :refer [warn]]))

(defonce fieldsets (join "," ["default" "basic" "affiliation" "study_base" "study_class"]))
(defonce batch-size 100)

(defn- extract-person
  [data]
  {:id (-> data :id)
   :active? (-> data :basic :is_zhdk)
   :first-name (-> data :basic :first_name)
   :last-name (-> data :basic :last_name)
   :infos (let [is-faculty (some #(-> data :affiliation %) [:is_mid-tier :is_lecturer])
                is-staff (-> data :affiliation :is_staff)]
            (->> [(when is-staff "Staff")
                  (when is-faculty "Faculty")]
                 (remove nil?)))
   :study-class-ids (->> data :study_class
                         (filter #(or (:is_successfully_completed %) (:is_current %)))
                         (map #(-> % :study_class :id)))})

(defn- fetch-page
  [{:keys [base-url username]} {:keys [id-filter]} offset limit]
  (let [url (str base-url
                 "person" (when id-filter (str "/" id-filter)) "?"
                 (http-client/generate-query-string (-> {:fieldsets fieldsets
                                                         :only_zhdk true
                                                         :order_by "name-asc"
                                                         :offset offset
                                                         :limit limit})))]
    (fetch url username)))

(defn- fetch-active-people-raw
  "Fetch active people from ZAPI (without resolving study class names)"
  [zapi-config options]
  (->> (batch-fetcher
        #(fetch-page zapi-config options %1 %2)
        #(-> % :pagination_info :result_count)
        batch-size)
       (map extract-person)
       doall))

(defn- update-study-class-names [person study-class-names]
  (if (seq study-class-names)
    (update person :infos #(concat % [(str "Stud " (join ", " study-class-names))]))
    person))

(defn fetch-active-people
  "Fetch active people from ZAPI"
  [zapi-config options]
  (let [people (doall (fetch-active-people-raw zapi-config options))
        study-class-ids (->> people
                             (mapcat :study-class-ids)
                             distinct)
        study-class-name-by-id (if (seq study-class-ids)
                                 (->> study-class-ids
                                      (join ",")
                                      (hash-map :id-filter)
                                      (study-classes/fetch-many zapi-config)
                                      (map (juxt :id :short-name))
                                      (into {}))
                                 {})]
    (->> people
         (map
          (fn [person]
            (let [study-class-names (->> person :study-class-ids (map #(get study-class-name-by-id %)) seq)]
              (update-study-class-names person study-class-names))))
         doall)))

(defn fetch-person
  "Fetch person from ZAPI (be it inactive or active). Returns nil when status is not 200."
  [{:keys [base-url username] :as zapi-config} id]
  (assert (non-blank-string? id))
  (let [url (str base-url
                 "person/" id "?"
                 (http-client/generate-query-string {:fieldsets fieldsets}))
        response (http-client/get url {:as :json :basic-auth [username ""] :throw-exceptions false})
        status (:status response)]
    (if (= status 200)
      (let [person (extract-person (-> response :body :data first))
            study-class-names (some->> person :study-class-ids seq (join ",")
                                       (hash-map :id-filter)
                                       (study-classes/fetch-many zapi-config)
                                       (map :short-name))]
        (update-study-class-names person study-class-names))
      (do (warn "Person" id "has HTTP Status" status "in ZAPI")
          nil))))
