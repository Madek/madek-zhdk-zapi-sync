(ns madek.zapi-sync.people
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join]]
   [madek.zapi-sync.http-utils :refer [fetch]]))

(defonce fieldsets (join "," ["default" "basic" "affiliation" "study_base" "study_class"]))
(defonce batch-size 100)
(def max-size Integer/MAX_VALUE)

(defn- extract-person
  [data]
  {:id (-> data :id)
   :first-name (-> data :basic :first_name)
   :last-name (-> data :basic :last_name)
   :institutional-directory-infos (let [is-faculty (some #(-> data :affiliation %) [:is_mid-tier :is_lecturer])
                                        is-staff (-> data :affiliation :is_staff)]
                                    (->> [(when is-staff "Staff")
                                          (when is-faculty "Faculty")]
                                         (remove nil?)))
   :study-class-ids (->> data :study_class
                         (map #(some-> % :study_class :id)))})

(defn- fetch-page
  [{:keys [base-url username]} {:keys [with-non-zhdk person-ids]} offset limit]
  (let [url (str base-url
                 "person" (when person-ids (str "/" person-ids)) "?"
                 (http-client/generate-query-string
                  (-> {:fieldsets fieldsets
                       :order_by "name-asc"
                       :offset offset
                       :limit limit}
                      (#(if with-non-zhdk % (assoc % :only_zhdk true))))))]
    (fetch url username)))

(defn fetch-many
  [zapi-config options]
  (let [limit batch-size
        first-response (fetch-page zapi-config options 0 limit)
        total-count (-> first-response :pagination_info :result_count (min max-size))]
    (println total-count)
    (->>
     (loop [offset limit
            results (:data first-response)]
       (if (>= (count results) total-count)
         results
         (let [next-response (fetch-page zapi-config options offset limit)]
           (recur (+ offset limit)
                  (concat results (:data next-response))))))
     (map extract-person))))
