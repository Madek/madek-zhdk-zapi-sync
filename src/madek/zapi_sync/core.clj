(ns madek.zapi-sync.core
  (:require [madek.zapi-sync.people :as people]
            [madek.zapi-sync.study-classes :as study-classes]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [join]]
            [clojure.tools.cli :as cli]))

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
   [nil "--zapi-url ZAPI_URL" "Defaults to env var `ZAPI_URL`"]
   [nil "--zapi-username ZAPI_USERNAME" "Defaults to env var `ZAPI_USERNAME`"]

   [nil "--get-people" "Get and print a list of people"]
   [nil "--person-ids PERSON_IDS" "Option to use with --get-people. Filters by comma-separated list of ids"]
   [nil "--with-non-zhdk" "Option to use with --get-people. Omit the `only-zhdk` filter (requires special permission in ZAPI!)"]

   [nil "--get-study-classes" "Get and print a list of study-classes"]
   [nil "--study-class-ids STUDY_CLASS_IDS" "Option to use with --get-study-classes Filters by comma-separated list of ids"]])

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

(defn run [options]
  (let [zapi-config  (get-zapi-config options)
        get-people (:get-people options)
        get-study-classes (:get-study-classes options)]
    (cond get-people
          (->> (people/fetch-many zapi-config (select-keys options [:person-ids :with-non-zhdk]))
               doall
               (study-classes/fetch-decorate-people zapi-config)
               doall
               pprint)
          get-study-classes
          (->> (study-classes/fetch-many zapi-config (select-keys options [:study-class-ids]))
               doall
               pprint)
          :else
          (println "Check out usage (--help)"))))

(defn -main [& args]
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
