(ns scripts.build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")

(def uber-file (format "madek-zhdk-zapi-sync.jar"))

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile '[madek.zapi-sync.main]
                  :src-dirs ["src"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'madek.zapi-sync.main}))
