(ns madek.zapi-sync.study-classes
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join]]
   [madek.zapi-sync.http-utils :refer [fetch]]))

(def fieldsets (join "," ["basic"]))
(def batch-size 100)
(def max-size Integer/MAX_VALUE)

(defn fetch-study-class-name [url zapi-username]
  (let [data (fetch (str url "?"
                         (http-client/generate-query-string
                          {:fieldsets fieldsets}))
                    zapi-username)]
    (-> data :data first :basic :number)))


(defn- fetch-page
  [{:keys [base-url username]} offset limit]
  (let [url (str base-url
                 "study-class" "?"
                 (http-client/generate-query-string {:fieldsets fieldsets
                                                     :offset offset
                                                     :limit limit}))]
    (fetch url username)))

(defn fetch-study-classes-map
  [zapi-config]
  (let [limit batch-size
        first-response (fetch-page zapi-config 0 limit)
        total-count (-> first-response :pagination_info :result_count (min max-size))]
    (->>
     (loop [offset limit
            results (:data first-response)]
       (if (>= (count results) total-count)
         results
         (let [next-response (fetch-page zapi-config offset limit)]
           (recur (+ offset limit)
                  (concat results (:data next-response))))))
     (map (fn [study-class]
            [(-> study-class :links :self)
             (-> study-class :basic :number)]))
     (into {}))))
