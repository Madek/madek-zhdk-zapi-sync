(ns madek.zapi-sync.zapi.people
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join]]
   [madek.zapi-sync.utils :refer [batch-fetcher]]
   [madek.zapi-sync.zapi.study-classes :as study-classes]
   [madek.zapi-sync.zapi.utils :refer [fetch]]))

(defonce fieldsets (join "," ["default" "basic" "affiliation" "study_base" "study_class"]))
(defonce batch-size 100)

(defn- extract-person
  [data]
  {:id (-> data :id)
   :first-name (-> data :basic :first_name)
   :last-name (-> data :basic :last_name)
   :infos (let [is-faculty (some #(-> data :affiliation %) [:is_mid-tier :is_lecturer])
                is-staff (-> data :affiliation :is_staff)]
            (->> [(when is-staff "Staff")
                  (when is-faculty "Faculty")]
                 (remove nil?)))
   :study-class-ids (->> data :study_class
                         (map #(some-> % :study_class :id)))})

(defn- fetch-page
  [{:keys [base-url username]} {:keys [with-non-zhdk id-filter]} offset limit]
  (let [url (str base-url
                 "person" (when id-filter (str "/" id-filter)) "?"
                 (http-client/generate-query-string
                  (-> {:fieldsets fieldsets
                       :order_by "name-asc"
                       :offset offset
                       :limit limit}
                      (#(if with-non-zhdk % (assoc % :only_zhdk true))))))]
    (fetch url username)))

(defn fetch-many
  [zapi-config options]
  (->> (batch-fetcher
        #(fetch-page zapi-config options %1 %2)
        #(-> % :pagination_info :result_count)
        batch-size)
       (map extract-person)
       doall))

(defn fetch-many-with-study-classes [zapi-config options]
  (let [people (doall (fetch-many zapi-config options))
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
            (if-let [study-class-names (->> person :study-class-ids (map #(get study-class-name-by-id %)) seq)]
              (update person :infos
                      #(concat % [(str "Stud " (join ", " study-class-names))]))
              person)))
         doall)))
