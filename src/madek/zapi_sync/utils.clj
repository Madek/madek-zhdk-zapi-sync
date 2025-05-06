(ns madek.zapi-sync.utils
  (:require
   [clojure.string]))

(defn batch-fetcher
  [fetch-page-fn get-total-fn batch-size]
  (let [limit batch-size
        first-response (fetch-page-fn 0 limit)
        total-count (-> first-response get-total-fn)]
    (->>
     (loop [offset limit
            results (:data first-response)]
       (if (>= (count results) total-count)
         results
         (let [next-response (fetch-page-fn offset limit)]
           (recur (+ offset limit)
                  (concat results (:data next-response)))))))))

(defn now-iso-local []
  (.format java.time.format.DateTimeFormatter/ISO_OFFSET_DATE_TIME
           (java.time.OffsetDateTime/now)))

(defn non-empty-string? [s]
  (and (string? s) (not (clojure.string/blank? s))))
