(ns user
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [duct.component.figwheel :as figwheel]
            [eftest.runner :as eftest]
            [masyst.config :as config]
            [masyst.system :as system]
            [meta-merge.core :refer [meta-merge]]
            [reloaded.repl :refer [system init start stop go reset]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]))

(def dev-config
  {:app {:middleware [wrap-stacktrace]}
   :figwheel
   {:server-port 3452
    :css-dirs ["resources/masyst/public/css"]
    :builds   [{:source-paths ["src" "dev"]
                :build-options
                {:optimizations :none
                 :main "cljs.user"
                 :asset-path "/js"
                 :output-to  "target/figwheel/masyst/public/js/main.js"
                 :output-dir "target/figwheel/masyst/public/js"
                 :source-map true
                 :source-map-path "/js"}}]}})

(def config
  (meta-merge config/defaults
              config/environ
              dev-config))

(defn new-system []
  (into (system/new-system config)
        {:figwheel (figwheel/server (:figwheel config))}))

(ns-unmap *ns* 'test)

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(defn cljs-repl []
  (figwheel/cljs-repl (:figwheel system)))

(when (io/resource "local.clj")
  (load "local"))

(reloaded.repl/set-init! new-system)

(defn conns []
  (-> system :datomic :conns))

(defn conn [server-name]
  (get (conns) server-name))

(defn db [server-name]
  (d/db (conn server-name)))


