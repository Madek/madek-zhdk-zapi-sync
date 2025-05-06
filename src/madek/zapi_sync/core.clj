(ns madek.zapi-sync.core
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string :refer [join]]
   [clojure.tools.cli :as cli]
   [madek.zapi-sync.data-file :as data-file]
   [madek.zapi-sync.sync :as sync]
   [madek.zapi-sync.zapi.people :as zapi.people]
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

   [nil "--zapi-url ZAPI_URL" "Config: Base URL of ZAPI with trailing slash. Defaults to env var `ZAPI_URL`"]
   [nil "--zapi-username ZAPI_USERNAME" "Config: ZAPI username. Defaults to env var `ZAPI_USERNAME`"]
   [nil "--madek-api-url MADEK_API_URL" "Config: Base URL of Madek API V2 with trailing slash. Defaults to env var `MADEK_API_URL`"]
   [nil "--madek-api-token MADEK_API_TOKEN" "Config: API Token for Madek API V2. Defaults to env var `MADEK_API_TOKEN`"]

   [nil "--sync-people" "Command: Get active people from ZAPI and sync them to Madek. Inactivate people when not active in ZAPI anymore"]
   [nil "--sync-inactive-people" "Command: Update people which are already inactive (to pull historic data once when needed)"]

   [nil "--push-people-from-file INPUT_FILE" "Command (debugging): Read data from file and push to Madek (insert, update, reactivate, but never inactivate)"]
   [nil "--update-single-person INSTITUTIONAL_ID" "Command (debugging): Update single Madek person (update, reactivate, inactivate)"]
   [nil "--get-people" "Command (debugging): Get people from ZAPI and write them to output"]
   [nil "--get-study-classes" "Command (debugging): Get study classes from ZAPI and write them to output"]

   [nil "--id-filter ID_FILTER" "Option for the `--get-*`commands: Get data filtered by a list of ids (comma-separated)"]
   [nil "--output-file OUTPUT_FILE" "Option for the `--get-*`commands: write json data to a file (otherwise to stdout)"]])

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

(defn- out [filename data]
  (if filename
    (data-file/run-write data filename)
    (pprint data)))

(defn run [{:keys [sync-people sync-inactive-people push-people-from-file update-single-person get-people get-study-classes] :as options}]
  (cond
    sync-people
    (let [zapi-config (require-zapi-config options)
          madek-api-config (require-madek-api-config options)]
      (sync/sync-people zapi-config madek-api-config INSTITUTION))

    sync-inactive-people
    (let [zapi-config (require-zapi-config options)
          madek-api-config (require-madek-api-config options)]
      (sync/sync-inactive-people zapi-config madek-api-config INSTITUTION))

    push-people-from-file
    (let [data (data-file/run-read push-people-from-file)
          madek-api-config (require-madek-api-config options)]
      (sync/push-people madek-api-config data INSTITUTION))

    update-single-person
    (let [zapi-config (require-zapi-config options)
          madek-api-config (require-madek-api-config options)]
      (sync/update-single-person zapi-config madek-api-config INSTITUTION update-single-person))

    get-people
    (let [zapi-config (require-zapi-config options)]
      (->> (zapi.people/fetch-active-people zapi-config (select-keys options [:id-filter]))
           (out (:output-file options))))

    get-study-classes
    (let [zapi-config (require-zapi-config options)]
      (->> (study-classes/fetch-many zapi-config (select-keys options [:id-filter]))
           (out (:output-file options))))

    :else (println "No command given. Check usage (--help)")))

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
