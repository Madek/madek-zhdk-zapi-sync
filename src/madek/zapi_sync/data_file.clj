(ns madek.zapi-sync.data-file
  (:require [cheshire.core :as cheshire]
            [taoensso.timbre :refer [info]]))

(defn run-write [data filename]
  (info (str "Writing data to " filename " ..."))
  (spit filename (cheshire/generate-string data {:pretty true}))
  (info (str "Writing data to " filename " done.")))

(defn run-read [filename]
  (info (str "Reading data from " filename " ..."))
  (let [data (-> (slurp filename)
                 (cheshire/parse-string true))]
    (info (str "Reading data from " filename " done."))
    data))
