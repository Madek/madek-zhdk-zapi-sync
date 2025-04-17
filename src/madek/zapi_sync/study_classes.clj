(ns madek.zapi-sync.study-classes
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join split]]
   [madek.zapi-sync.http-utils :refer [fetch]]))

(def fieldsets (join "," ["basic"]))
(def batch-size 100)
(def max-size Integer/MAX_VALUE)

(defn- fetch-page
  [{:keys [base-url username]} {:keys [study-class-ids]} offset limit]
  (let [url (str base-url
                 "study-class" (when study-class-ids (str "/" study-class-ids)) "?"
                 (http-client/generate-query-string {:fieldsets fieldsets
                                                     :offset offset
                                                     :limit limit}))]
    (fetch url username)))

(defn fetch-many
  [zapi-config {:keys [study-class-ids] :as options}]
  ;; pre-batching in order to avoid "url too long"
  (->> (partition-all batch-size (or (some-> study-class-ids (split #",") seq) [nil]))
       (map
        (fn [study-class-id-batch]
          (let [options (assoc options :study-class-ids (some->> study-class-id-batch (join ",")))
                limit batch-size
                first-response (fetch-page zapi-config options 0 limit)
                total-count (-> first-response :pagination_info :result_count (min max-size))]
            (->>
             (loop [offset limit
                    results (:data first-response)]
               (if (>= (count results) total-count)
                 results
                 (let [next-response (fetch-page zapi-config options offset limit)]
                   (recur (+ offset limit)
                          (concat results (:data next-response))))))
             (map (fn [study-class]
                    {:id (-> study-class :id)
                     :link (-> study-class :links :self)
                     :short-name (-> study-class :basic :number)
                     :name (-> study-class :basic :name)}))))))
       (mapcat identity)))

(defn fetch-decorate-people
  [zapi-config people]
  (let [name-by-id (->> people
                        (mapcat :study-class-ids)
                        distinct
                        (join ",")
                        (hash-map :study-class-ids)
                        (fetch-many zapi-config)
                        (map (juxt :id :short-name))
                        (into {}))]
    (->> people
         (map
          (fn [person]
            (if-let [study-class-names (->> person :study-class-ids (map #(get name-by-id %)) seq)]
              (update person :institutional-directory-infos
                      #(concat % [(str "Stud " (join ", " study-class-names))]))
              person))))))
