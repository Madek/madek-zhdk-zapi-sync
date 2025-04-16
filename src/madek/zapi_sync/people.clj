(ns madek.zapi-sync.people
  (:require
   [clj-http.client :as http-client]
   [clojure.string :refer [join]]
   [madek.zapi-sync.http-utils :refer [fetch]]
   [madek.zapi-sync.study-classes :refer [fetch-study-class-name]]))

(defonce fieldsets (join "," ["default" "basic" "affiliation" "study_base" "study_class"]))
(defonce batch-size 100)
(def max-size 101 #_Integer/MAX_VALUE)

(defn- extract-institutional-infos
  [person resolve-study-class-name]
  (let [is-faculty (some #(-> person :affiliation %) [:is_mid-tier :is_lecturer])
        is-staff (-> person :affiliation :is_staff)
        study-class-names (->> person :study_class
                               (map #(some-> % :study_class :link resolve-study-class-name)))]
    (->> [(when is-staff "Staff")
          (when is-faculty "Faculty")
          (when (seq study-class-names) (str "Stud " (join ", " study-class-names)))]
         (remove nil?))))

(defn- extract-basic-data
  [person]
  {:id (-> person :id)
   :first-name (-> person :basic :first_name)
   :last-name (-> person :basic :last_name)})

(defn- extract-person
  [person study-class-name-resolver]
  (-> person
      extract-basic-data
      (assoc :institutional-directory-infos
             (extract-institutional-infos person study-class-name-resolver))))

(defn fetch-person
  [{:keys [base-url username]} person-id with-non-zhdk]
  (let [url (str base-url
                 "person/" person-id "?"
                 (http-client/generate-query-string
                  (-> {:fieldsets fieldsets}
                      (#(if with-non-zhdk % (assoc % :only_zhdk true))))))
        data (fetch url username)
        person (-> data :data first)
        study-class-name-resolver #(fetch-study-class-name % username)]
    (extract-person person study-class-name-resolver)))

(defn- fetch-page
  [{:keys [base-url username]} options offset limit]
  (let [url (str base-url
                 "person" "?"
                 (http-client/generate-query-string
                  (-> {:fieldsets fieldsets
                       :offset offset
                       :limit limit}
                      (#(if (:with-non-zhdk options) % (assoc % :only_zhdk true))))))]
    (fetch url username)))

(defn fetch-people
  [zapi-config options]
  (let [limit batch-size
        first-response (fetch-page zapi-config options 0 limit)
        total-count (-> first-response :pagination_info :result_count (min max-size))
        study-class-name-resolver (if (> total-count 100)
                                    identity
                                    #(fetch-study-class-name % (:username zapi-config)))]
    (->>
     (loop [offset limit
            results (:data first-response)]
       (if (>= (count results) total-count)
         results
         (let [next-response (fetch-page zapi-config options offset limit)]
           (recur (+ offset limit)
                  (concat results (:data next-response))))))
     (map #(extract-person % study-class-name-resolver)))))
