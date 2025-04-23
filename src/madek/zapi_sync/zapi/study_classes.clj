(ns madek.zapi-sync.zapi.study-classes
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join split]]
   [madek.zapi-sync.zapi.utils :refer [fetch]]))

(def fieldsets (join "," ["basic"]))
(def batch-size 100)
(def max-size Integer/MAX_VALUE)

(defn- fetch-page
  [{:keys [base-url username]} {:keys [id-filter]} offset limit]
  (let [url (str base-url
                 "study-class" (when id-filter (str "/" id-filter)) "?"
                 (http-client/generate-query-string {:fieldsets fieldsets
                                                     :offset offset
                                                     :limit limit}))]
    (fetch url username)))

(defn fetch-many
  [zapi-config {:keys [id-filter] :as options}]
  ;; pre-batching in order to prevent "url too long" error
  (->> (partition-all batch-size (or (some-> id-filter (split #",") seq) [nil]))
       (map
        (fn [batch-of-ids]
          (let [options (assoc options :id-filter (some->> batch-of-ids (join ",")))
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
