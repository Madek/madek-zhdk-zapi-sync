(ns madek.zapi-sync.core
  (:require [madek.zapi-sync.people :as people]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [join]]
            [clj-http.client :as http-client]
            [clojure.tools.cli :as cli]
            [cheshire.core :as json]))

(defn fetch
  [url username]
  (println "fetching " url)
  (let [response (http-client/get url {:as :json :basic-auth [username ""]})]
    (:body response)))

(defn fetch-study-class-name [url zapi-username]
  (let [data (fetch (str url "?"
                         (http-client/generate-query-string
                          {:fieldsets (join "," ["basic"])}))
                    zapi-username)]
    (-> data :data first :basic :number)))

(defn fetch-person
  [person-id allow-non-zhdk zapi-url zapi-username]
  (let [url (str zapi-url
                 "person/" person-id "?"
                 (http-client/generate-query-string
                  (-> {:fieldsets (join "," ["default" "basic" "affiliation" "study_base" "study_class"])}
                      (#(if allow-non-zhdk % (assoc % :only_zhdk true))))))
        data (fetch url zapi-username)
        person (-> data :data first)
        institutional-directory-infos (people/get-institutional-infos person #(fetch-study-class-name % zapi-username))
        basic-data (people/get-basic-data person)]
    (println "Person ID:" (basic-data :id) "Name:" (str (-> basic-data :last-name) ", " (-> basic-data :first-name)))
    (println (json/generate-string institutional-directory-infos {:pretty true}))))

(defn usage [options-summary & more]
  (->> ["Madek ZHdK ZAPI Sync"
        ""
        "usage: clojure -M -m madek.zapi-sync.core [<opts>]"
        ""
        "Options:"
        options-summary
        ""
        ""
        (when more
          ["-------------------------------------------------------------------"
           ""
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (join \newline)))

(def cli-options
  [["-h" "--help"]
   [nil "--zapi-url ZAPI_URL" "Defaults to env var `ZAPI_URL`"]
   [nil "--zapi-username ZAPI_USERNAME" "Defaults to env var `ZAPI_USERNAME`"]
   [nil "--get-person PERSON_ID" "Just get a single person by id"]
   [nil "--allow-non-zhdk" "Omit the `only-zhdk` filter (requires special permission in ZAPI!)"]])

(defn run [options]
  (let [zapi-url (or (:zapi-url options) (System/getenv "ZAPI_URL"))
        zapi-username (or (:zapi-username options) (System/getenv "ZAPI_USERNAME"))
        get-person-id (:get-person options)
        allow-non-zhdk (:allow-non-zhdk options)]
    (cond (nil? zapi-url)
          (println "ZAPI_URL missing. Check usage.")
          (nil? zapi-username)
          (println "ZAPI_USERNAME missing. Check usage.")
          get-person-id
          (fetch-person get-person-id allow-non-zhdk zapi-url zapi-username)
          :else
          (println "No instruction from CLI options. Check usage."))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options :in-order true)]
    (cond
      (:help options)
      (println (usage summary {:options options :arguments arguments :errors errors}))
      :else
      (run options))))


(comment
  ;; format as SQL (for debugging only)
  (defn psql-escape [s]
    (clojure.string/escape s {"'" "''"}))

  (defn generate-sql-update [person-id institutional-directory-infos]
    (str "UPDATE people SET institutional_directory_infos = ARRAY["
         (join "," (map #(str "'" (psql-escape %) "'") institutional-directory-infos))
         "] WHERE institutional_id = '" (psql-escape person-id) "';")))
