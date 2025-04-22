(ns madek.zapi-sync.zapi.utils
  (:require [clj-http.client :as http-client]
            [taoensso.timbre :refer [debug]]))

(defn fetch
  [url username]
  (debug "fetching " url)
  (let [response (http-client/get url {:as :json :basic-auth [username ""]})]
    (:body response)))
