(ns madek.zapi-sync.core
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string :refer [join]]
   [clojure.tools.cli :as cli]
   [madek.zapi-sync.data-file :as data-file]
   [madek.zapi-sync.sync :as sync]
   [madek.zapi-sync.zapi.people :as zapi.people]
   [madek.zapi-sync.zapi.study-classes :as study-classes]
   [madek.zapi-sync.prtg :as prtg]
   [taoensso.timbre :refer [info error] :as logging]))

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
   [nil "--output-file OUTPUT_FILE" "Option for the `--get-*`commands: write json data to a file (otherwise to stdout)"]

   [nil "--verbose" "Sets min log level to :debug (default is :info)"]
   [nil "--prtg-url PRTG_URL" "When given, success and exceptions of `--sync-people` will be sent to PRTG (not for other commands because they are not intended to be automated)"]])

(defn- require-zapi-config [options]
  (let [zapi-url (or (:zapi-url options) (System/getenv "ZAPI_URL"))
        zapi-username (or (:zapi-username options) (System/getenv "ZAPI_USERNAME"))]
    (cond
      (empty? zapi-url)
      (throw (Exception. "ZAPI_URL not found in options or env"))
      (empty? zapi-username)
      (throw (Exception. "ZAPI_USERNAME not found in options or env"))
      :else
      {:base-url zapi-url
       :username zapi-username})))

(defn- require-madek-api-config [options]
  (let [madek-api-url (or (:madek-api-url options) (System/getenv "MADEK_API_URL"))
        madek-api-token (or (:madek-api-token options) (System/getenv "MADEK_API_TOKEN"))]
    (cond
      (empty? madek-api-url)
      (throw (Exception. "MADEK_API_URL not found in options or env"))
      (empty? madek-api-token)
      (throw (Exception. "MADEK_API_TOKEN not found in options or env"))
      :else
      {:base-url madek-api-url
       :auth-header (str "token " madek-api-token)})))

(defn- out [filename data]
  (if filename
    (data-file/run-write data filename)
    (pprint data)))

(defn- log-success
  ([results] (log-success results nil))
  ([results prtg-url]
   (let [stats (frequencies results)]
     (info "Success" stats)
     (when prtg-url
       (info "Sending to PRTG...")
       (prtg/send-success prtg-url stats)))))

(defn- log-error
  ([ex] (log-error ex nil))
  ([ex prtg-url]
   (error ex)
   (when prtg-url
     (info "Sending to PRTG...")
     (prtg/send-error prtg-url (.getMessage ex)))))

(defn run [{:keys [verbose prtg-url
                   sync-people
                   sync-inactive-people
                   push-people-from-file
                   update-single-person
                   get-people
                   get-study-classes] :as options}]
  (if verbose
    (logging/set-min-level! :debug)
    (logging/set-min-level! :info))
  (info "Madek ZAPI Sync...")
  (let [zapi-config (require-zapi-config options)
        madek-api-config (require-madek-api-config options)]
    (cond
      sync-people
      (do (info "Command sync-people")
          (-> (sync/sync-people zapi-config madek-api-config INSTITUTION)
              log-success)
          (info "Command sync-people done"))

      sync-inactive-people
      (do (info "Command sync-inactive-people")
          (-> (sync/sync-inactive-people zapi-config madek-api-config INSTITUTION)
              log-success)
          (info "Command sync-inactive-people done"))

      ;; commands for testing/debugging:

      push-people-from-file
      (let [prtg-url (or (:prtg-url options) (System/getenv "PRTG_URL"))] ;; TODO: move this PRTG shizzle to sync-people
        (try
          (let [data (data-file/run-read push-people-from-file)]
            (info "Command push-people-from-file")
            (-> (sync/push-people madek-api-config data INSTITUTION)
                (log-success prtg-url))
            (info "Command push-people-from-file done"))
          (catch Exception e
            (log-error e prtg-url)
            (System/exit -1))))

      update-single-person
      (do (info "Command update-single-person")
          (-> (sync/update-single-person zapi-config madek-api-config INSTITUTION update-single-person)
              log-success)
          (info "Command update-single-person done"))

      get-people
      (do (info "Command get-people")
          (let [data (zapi.people/fetch-active-people zapi-config (select-keys options [:id-filter]))]
            (out (:output-file options) data)
            (info "N =" (count data))
            (info "Command get-people done")))

      get-study-classes
      (do (info "Command get-study-classes")
          (let [data (study-classes/fetch-many zapi-config (select-keys options [:id-filter]))]
            (out (:output-file options) data)
            (info "N =" (count data))
            (info "Command get-study-classes done")))

      :else (println "No command given. Check usage (--help)"))))

(defn -main [& args]
  (try
    (let [{:keys [options #_arguments errors summary]}
          (cli/parse-opts args cli-option-specs :in-order true)]
      (cond
        (seq errors)
        (do
          (println "Check out usage (--help)")
          (pprint errors))

        (:help options)
        (println (usage summary))

        :else
        (run options))
      (System/exit 0))
    (catch Exception e
      (log-error e)
      (System/exit -1))))
