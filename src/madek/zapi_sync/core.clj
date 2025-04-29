(ns madek.zapi-sync.core
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string :refer [join]]
   [clojure.tools.cli :as cli]
   [madek.zapi-sync.data-file :as data-file]
   [madek.zapi-sync.madek-api :as madek-api]
   [madek.zapi-sync.zapi.people :as people]
   [madek.zapi-sync.zapi.study-classes :as study-classes]
   [taoensso.timbre :refer [info]]))

(def INSTITUTION "zhdk.ch")

(defn usage [options-summary]
  (->> ["Madek ZHdK ZAPI Sync"
        ""
        "usage: clojure -M -m madek.zapi-sync.core [<opts>]"
        ""
        "Options summary:"
        options-summary
        ""]
       flatten (join \newline)))

(def cli-option-specs
  [["-h" "--help"]
   [nil "--sync-people" "Command: Get people from ZAPI and sync them to Madek (see also --with-deactivation)"]
   [nil "--with-deactivation" "- Use together with --sync-people to also deactivate people which are gone from ZAPI. Do NOT apply when incomplete ZAPI data is used (see --id-filter)"]
   [nil "--get-people" "Command: Get people from ZAPI and write them to output"]
   [nil "--get-study-classes" "Command: Get study classes from ZAPI and write them to output (for debugging)"]

   [nil "--zapi-url ZAPI_URL" "Config: Base URL of ZAPI with trailing slash. Defaults to env var `ZAPI_URL`"]
   [nil "--zapi-username ZAPI_USERNAME" "Config: ZAPI username. Defaults to env var `ZAPI_USERNAME`"]
   [nil "--madek-api-url MADEK_API_URL" "Config: Base URL of Madek API V2 with trailing slash. Defaults to env var `MADEK_API_URL`"]
   [nil "--madek-api-token MADEK_API_TOKEN" "Config: API Token for Madek API V2. Defaults to env var `MADEK_API_TOKEN`"]
   #_[nil "--institution INSTITUTION" "Config: Institution (builds the unique identifier together with `institutional_id` coming from ZAPI)"]

   [nil "--id-filter ID_FILTER" "Option: Get data from ZAPI filtered by a list of ids (comma-separated)"]
   [nil "--output-file OUTPUT_FILE" "Option: With --get-people and --get-study-classes, write json data to a file (otherwise to stdout)"]
   [nil "--input-file INPUT_FILE" "Option: With --sync-people, skip querying ZAPI and use json data from a file instead"]])

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

#_(defn- require-institution [{:keys [institution]}]
    (if (empty? institution)
      (throw (Exception. "INSTITUTION not present in options (--institution <INSTITUTION>)"))
      institution))

(defn- out [filename data]
  (if filename
    (data-file/run-write data filename)
    (pprint data)))

(defn- run-sync-people [options]
  (let [zapi-people
        (if-let [input-file (:input-file options)]
          (data-file/run-read input-file)
          (people/fetch-many-with-study-classes
           (require-zapi-config options)
           (select-keys options [:id-filter])))
        madek-api-config (require-madek-api-config options)]
    (madek-api/sync-people madek-api-config INSTITUTION zapi-people)
    (if (:with-deactivation options)
      (madek-api/inactivate-missing-people madek-api-config INSTITUTION zapi-people)
      (println "NOTE: Sync is not complete, deactivation task was skipped! See `--with-deactivation` option"))))

(defn- run-get-people [options]
  (->> (people/fetch-many (require-zapi-config options) (select-keys options [:id-filter]))
       (out (:output-file options))))

(defn- run-get-study-classes [options]
  (->> (study-classes/fetch-many (require-zapi-config options) (select-keys options [:id-filter]))
       (out (:output-file options))))

(defn run [{:keys [get-people get-study-classes sync-people] :as options}]
  (cond get-people (run-get-people options)
        get-study-classes (run-get-study-classes options)
        sync-people (run-sync-people options)
        :else (println "No command found. Check usage (--help)")))

(defn -main [& args]
  (info "Madek ZAPI Sync...")
  (try
    (let [{:keys [options #_arguments errors summary]}
          (cli/parse-opts args cli-option-specs :in-order true)]
      (cond
        (seq errors)
        (do (println "Check out usage (--help)")
            (pprint errors))
        (:help options)
        (println (usage summary))
        :else
        (run options))
      (System/exit 0))
    (catch Exception e
      (do
        (println e)
        (System/exit -1)))))
