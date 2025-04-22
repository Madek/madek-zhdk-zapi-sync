(ns madek.zapi-sync.madek-api
  (:require [clojure.pprint :refer [pprint]]))

(defn sync-one [person]
  (pprint person))

(defn sync-many [people]
  (->> people (run! sync-one)))