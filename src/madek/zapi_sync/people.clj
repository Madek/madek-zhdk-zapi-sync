(ns madek.zapi-sync.people
  (:require [clojure.string :refer [join]]))

(defn get-institutional-infos
  [person resolve-study-class-name]
  (let [is-faculty (some #(-> person :affiliation %) [:is_mid-tier :is_lecturer])
        is-staff (-> person :affiliation :is_staff)
        study-class-names (->> person :study_class
                               (map #(some-> % :study_class :link resolve-study-class-name)))]
    (->> [(when is-staff "Staff")
          (when is-faculty "Faculty")
          (when (seq study-class-names) (str "Stud " (join ", " study-class-names)))]
         (remove nil?))))

(defn get-basic-data
  [person]
  {:id (-> person :id)
   :first-name (-> person :basic :first_name)
   :last-name (-> person :basic :last_name)})