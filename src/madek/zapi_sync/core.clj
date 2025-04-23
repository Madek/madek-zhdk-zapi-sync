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
   [nil "--get-study-classes" "(DEBUG) Get and print a list of study-classes"]
   [nil "--with-non-zhdk" "Option to use with --get-people. Omit the `only-zhdk` filter (requires special permission in ZAPI!)"]
   [nil "--id-filter ID_FILTER" "Option to use with --get-people or --get-study-classes. Filters by comma-separated list of ids"]

   [nil "--output-file OUTPUT_FILE" "Used in combination with --get-people or --get-study-classes, will write json data to file instead of stdout."]

   [nil "--madek-api-url MADEK_API_URL" "Base URL of Madek API V2 with trailing slash. Defaults to env var `MADEK_API_URL`"]
   [nil "--madek-api-token MADEK_API_TOKEN" "API Token for Madek API V2. Defaults to env var `MADEK_API_TOKEN`"]

   [nil "--sync-people" "Sync people to Madek. "]
   [nil "--institution INSTITUTION" "Institution (required when syncing to define the scope of the institutional id)"]
   [nil "--input-file INPUT_FILE" "Used in combination with --sync-people, will read json data from file instead of fetching it from ZAPI."]])

(defn- require-zapi-config [options]
  (let [zapi-url (or (:zapi-url options) (System/getenv "ZAPI_URL"))
        zapi-username (or (:zapi-username options) (System/getenv "ZAPI_USERNAME"))]
    (cond
      (nil? zapi-url)
      (throw (Exception. "ZAPI_URL not found in options or env"))
      (nil? zapi-username)
      (throw (Exception. "ZAPI_USERNAME not found in options or env"))
      :else
      {:base-url zapi-url
       :username zapi-username})))

(defn- require-madek-api-config [options]
  (let [madek-api-url (or (:madek-api-url options) (System/getenv "MADEK_API_URL"))
        madek-api-token (or (:madek-api-token options) (System/getenv "MADEK_API_TOKEN"))]
    (cond
      (nil? madek-api-url)
      (throw (Exception. "MADEK_API_URL not found in options or env"))
      (nil? madek-api-token)
      (throw (Exception. "MADEK_API_TOKEN not found in options or env"))
      :else
      {:base-url madek-api-url
       :auth-header (str "token " madek-api-token)})))

(defn- require-institution [{:keys [institution]}]
  (if (empty? institution)
    (throw (Exception. "INSTITUTION not present in options (--institution <INSTITUTION>)"))
    institution))

(defn- out [filename data]
  (if filename
    (data-file/run-write data filename)
    (pprint data)))

(defn run [{:keys [get-people get-study-classes output-file
                   sync-people input-file] :as options}]
  (cond get-people
        (->> (people/fetch-many-with-study-classes (require-zapi-config options) (select-keys options [:id-filter :with-non-zhdk]))
             (out output-file))
        get-study-classes
        (->> (study-classes/fetch-many (require-zapi-config options) (select-keys options [:id-filter]))
             (out output-file))
        sync-people
        (if input-file
          (madek-api/sync-many (require-madek-api-config options) (require-institution options) (data-file/run-read input-file))
          (->> (people/fetch-many-with-study-classes (require-zapi-config options) (select-keys options [:id-filter :with-non-zhdk]))
               (madek-api/sync-many (require-madek-api-config options) (require-institution options))))
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
