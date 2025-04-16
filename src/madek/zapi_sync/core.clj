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
   [nil "--get-person PERSON_ID" "Get and print a single person by id"]
   [nil "--get-people" "Get and print a list of people"]
   [nil "--get-study-classes" "Get and print a list of study-classes"]
   [nil "--with-non-zhdk" "Omit the `only-zhdk` filter (requires special permission in ZAPI!)"]])

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

(defn- print-person [person]
  (println "Person ID:" (-> person :id) "| Name:" (str (-> person :last-name) ", " (-> person :first-name)) "| Infos: " (->> person :institutional-directory-infos (join ", "))))

(defn- print-study-class [[link name]]
  (println "Link:" link "| Name:" name))

(defn run [options]
  (let [zapi-config  (get-zapi-config options)
        get-person-id (:get-person options)
        get-people (:get-people options)
        with-non-zhdk (:with-non-zhdk options)
        get-study-classes (:get-study-classes options)]
    (cond get-person-id
          (let [person (people/fetch-person zapi-config get-person-id with-non-zhdk)]
            (print-person person))
          get-people
          (->> (people/fetch-people zapi-config with-non-zhdk) (map print-person) doall)
          get-study-classes
          (->> (study-classes/fetch-study-classes-map zapi-config) (map print-study-class) doall)
          :else
          (println "Check usage (--help)"))))

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
