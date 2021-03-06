(ns masyst.component.datomic
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [io.rkn.conformity :as conformity]
            [masyst.config :as config]
            [taoensso.timbre :as timbre]))

(defrecord Datomic [uri conns]
  component/Lifecycle
  (start [component]
    (let [norms-map (conformity/read-resource "masyst_schema.edn")]
      (reduce
       (fn [out [server-name db-name]]
         (let [uri (str uri db-name)
               _ (d/create-database uri)
               conn (d/connect uri)]
           (timbre/info server-name "connected to datomic DB" db-name ", going to run conformity")
           (conformity/ensure-conforms conn norms-map)

           (let [db (d/db conn)]
             (when (empty? (d/q '[:find ?e :where [?e :ent/type :ebs-handover]] db))
               (timbre/info "Prazdna predavaci dokumentace. Generuji zaznamy pro kazdou polozku struktury")
               (->> db
                    (d/q '[:find [?e ...] :where [?e :ent/type :ebs-tree]])
                    (map #(hash-map :db/id (d/tempid :part/ebs) :ent/type :ebs-handover :ebs/code-ref %))
                    (d/transact conn))))

           (assoc-in out [:conns server-name] conn)))
       component
       config/dbs)))
  (stop [component]
    (reduce
     (fn [out [server-name conn]]
       (when conn
         (timbre/info "Releasing datomic connection of" server-name)
         (d/release conn))
       (assoc-in out [:conns server-name] nil))
     component
     conns)))

(defn datomic [uri]
  (map->Datomic {:uri uri}))
