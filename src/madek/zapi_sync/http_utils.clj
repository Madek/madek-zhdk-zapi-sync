(ns madek.zapi-sync.http-utils
  (:require [clj-http.client :as http-client]))

(defn fetch
  [url username]
  (println "fetching " url)
  (let [response (http-client/get url {:as :json :basic-auth [username ""]})]
    (:body response)))
