(ns madek.zapi-sync.zapi.study-classes
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join split]]
   [madek.zapi-sync.utils :refer [batch-fetcher]]
   [madek.zapi-sync.zapi.utils :refer [fetch]]))

(def fieldsets (join "," ["basic"]))
(def batch-size 100)

(defn- extract-study-class
  [data]
  {:id (-> data :id)
   :link (-> data :links :self)
   :short-name (-> data :basic :number)
   :name (-> data :basic :name)})

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
  ;; pre-batching when using id-filter (in order to prevent "url too long" error)
  (->> (partition-all batch-size (or (some-> id-filter (split #",") seq) [nil]))
       (map
        (fn [batch-of-ids]
          (let [options (assoc options :id-filter (some->> batch-of-ids (join ",")))]
            (->> (batch-fetcher
                  #(fetch-page zapi-config options %1 %2)
                  #(-> % :pagination_info :result_count)
                  batch-size)
                 (map extract-study-class)))))
       (mapcat identity)))
