(ns madek.zapi-sync.core
  (:require [madek.zapi-sync.data-file :as data-file]
            [madek.zapi-sync.madek-api :as madek-api]
            [madek.zapi-sync.zapi.people :as people]
            [madek.zapi-sync.zapi.study-classes :as study-classes]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [join]]
            [clojure.tools.cli :as cli]
            [taoensso.timbre :refer [info]]))

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

(def cli-option-specs
  [["-h" "--help"]
   [nil "--zapi-url ZAPI_URL" "Base URL of ZAPI with trailing slash. Defaults to env var `ZAPI_URL`"]
   [nil "--zapi-username ZAPI_USERNAME" "ZAPI username. Defaults to env var `ZAPI_USERNAME`"]

   [nil "--get-people" "Get and print a list of people"]
   [nil "--person-ids PERSON_IDS" "Option to use with --get-people. Filters by comma-separated list of ids"]
   [nil "--with-non-zhdk" "Option to use with --get-people. Omit the `only-zhdk` filter (requires special permission in ZAPI!)"]

   [nil "--get-study-classes" "(DEBUG) Get and print a list of study-classes"]
   [nil "--study-class-ids STUDY_CLASS_IDS" "(DEBUG) Option to use with --get-study-classes Filters by comma-separated list of ids"]

   [nil "--output-file OUTPUT_FILE" "Used in combination with --get-people or --get-study-classes, will write json data to file instead of stdout."]

   [nil "--madek-api-url MADEK_API_URL" "Base URL of Madek API V2 with trailing slash. Defaults to env var `MADEK_API_URL`"]
   [nil "--madek-api-token MADEK_API_TOKEN" "API Token for Madek API V2. Defaults to env var `MADEK_API_TOKEN`"]
   [nil "--sync-people" "Sync people to Madek"]
   [nil "--input-file INPUT_FILE" "Used in combination with --sync-people, will read json data from file instead of fetching it from ZAPI."]])

(defn- get-zapi-config [options]
  (let [zapi-url (or (:zapi-url options) (System/getenv "ZAPI_URL"))
        zapi-username (or (:zapi-username options) (System/getenv "ZAPI_USERNAME"))]
    (cond (nil? zapi-url)
          (throw (Exception. "ZAPI_URL not found in options or env"))
          (nil? zapi-username)
          (throw (Exception. "ZAPI_USERNAME not found in options or env"))
          :else
          {:base-url zapi-url
           :username zapi-username})))

(defn- out [filename data]
  (if filename
    (data-file/run-write data filename)
    (pprint data)))

(defn run [{:keys [get-people get-study-classes output-file
                   sync-people input-file] :as options}]
  (cond get-people
        (->> (people/fetch-many-with-study-classes (get-zapi-config options) (select-keys options [:person-ids :with-non-zhdk]))
             (out output-file))
        get-study-classes
        (->> (study-classes/fetch-many (get-zapi-config options) (select-keys options [:study-class-ids]))
             (out output-file))
        sync-people
        (if input-file
          (-> (data-file/run-read input-file) madek-api/sync-many)
          (out nil "NOT IMPLEMENTED"))
        :else
        (println "Check out usage (--help)")))

(defn -main [& args]
  (info "Madek ZAPI Sync...")
  (try
    (let [{:keys [options arguments errors summary]}
          (cli/parse-opts args cli-option-specs :in-order true)]
      (cond
        (:help options)
        (println (usage summary {:options options :arguments arguments :errors errors}))
        :else
        (run options))
      (System/exit 0))
    (catch Exception e
      (do
        (println e)
        (System/exit -1)))))
