(ns masyst.db.common-database
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [datomic.api :as d]
            [taoensso.timbre :as timbre]))

(defn transact [conn uid tx-data]
  (let [tx-data (vec tx-data)]
    (timbre/info "transacting uid:" uid "tx-data:" tx-data)
    @(d/transact conn (conj tx-data {:db/id (d/tempid :db.part/tx)
                                     :tx/uid uid}))))

(defn add-where [query where]
  (let [where (into {} (map-indexed
                        (fn [idx [k v]]
                          [k (symbol (str "?v" idx))])
                        where))]
    (-> query
        (update :where #(into % (->> where
                                     (filter (fn [[k v]] (not= k :db/id)))
                                     (map (fn [[k v]]
                                            ['?e k v])))))
        (update :in #(into % (map (fn [[k v]] (if (= k :db/id) '?e v)) where))))))

(defn select [db ent-type where]
  (apply d/q (add-where '{:find [[(pull ?e [*]) ...]]
                          :in [$ ?type]
                          :where [[?e :ent/type ?type]]}
                        where)
         db
         ent-type
         (vals where)))

(def tempid? map?)

(defn- remove-backward-refs [xs]
  (walk/prewalk (fn [x]
                  (if (and (map-entry? x)
                           (str/starts-with? (name (key x)) "_"))
                    nil
                    x))
                xs))

(defn- coll--tx-data [eid k xs old-xs]
  (if (every? map? xs)
    (into [{:db/id eid
            k (remove-backward-refs xs)}]
          (map
           #(vector :db/retract eid k %)
           (set/difference (set (map :db/id old-xs))
                           (set (map :db/id xs)))))
    (let [xs (set xs)
          old-xs (set old-xs)]
      (into
       (mapv
        #(vector :db/add eid k %)
        (set/difference xs old-xs))
       (map
        #(vector :db/retract eid k %)
        (set/difference old-xs xs))))))

(defn- entity--tx-data [db ent]
  (let [eid (:db/id ent)
        old (when-not (tempid? eid) (d/pull db '[*] eid))]
    (mapcat (fn [[k v]]
              (if (or (nil? v) (= v {:db/id nil}))
                (when-let [old-v (get old k)]
                  (list [:db/retract eid k (if (map? old-v)
                                             (:db/id old-v)
                                             old-v)]))
                (when-not (= v (get old k))
                  (if (and (coll? v) (not (map? v)))
                    (coll--tx-data eid k v (get old k))
                    (list {:db/id eid
                           k (if (and (map? v) (not (tempid? (:db/id v))))
                               (or (:db/id v) (:db/ident v))
                               v)})))))
            (dissoc ent :db/id))))

(defn- db-partition [ent-type]
  (case ent-type
    (:country :crm-subj :crm-event :crm-sale :crm-product :crm-project)
    :part/crm

    (:invoice :contract :tech-design :order :supplier :cost-center :material :other
              :issue :issue-state :issue-priority :issue-project :issue-type
              :vacation :business-trip :automobile :drive-book :absence :time-sheet)
    :part/ma

    (:ebs-tree :ebs-project :ebs-handover :ebs-calc :ebs-offer :ebs-other :ebs-other-category :ebs-construction-diary)
    :part/ebs

    (:user :role :file)
    :db.part/user

    (do (timbre/warn "Unknown ent-type" ent-type ". Using default partition")
        :db.part/user)))

(defn save! [conn uid ent-type ent]
  (let [id (:db/id ent)
        ent (cond-> (assoc ent :ent/type ent-type)
              (not id)
              (assoc :db/id (d/tempid (db-partition ent-type))))
        tx-result (when-let [tx-data (not-empty (entity--tx-data (d/db conn) ent))]
                    (transact conn uid tx-data))]
    (or id
        (d/resolve-tempid (:db-after tx-result) (:tempids tx-result) (:db/id ent)))))

(defn select-created-date [db id attr]
  (ffirst (d/q '[:find ?created
                 :in $ ?e ?attr
                 :where
                 [?e ?attr _ ?tx]
                 [?tx :db/txInstant ?created]]
               db
               id
               attr)))
