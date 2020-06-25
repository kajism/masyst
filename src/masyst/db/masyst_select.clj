(ns masyst.db.masyst-select
  (:require [datomic.api :as d]
            [masyst.db.common-database :as common-db]
            [masyst.db.file-service :as file-service]
            [masyst.db.masyst-service :as masyst-service :refer [select]]
            [taoensso.timbre :as timbre]))

(defn select-default
  ([db user ent-type where]
   (select-default db user ent-type where '[* {:file/_parent [*]}]))
  ([db user ent-type where pattern]
   (let [where (merge where {:ent/type ent-type})]
     (->>
      (apply d/q (common-db/add-where
                  {:find [[(list 'pull '?e pattern) '...]]
                   :in ['$]
                   :where []}
                  where)
             db
             (vals where))
      (file-service/assoc-file-created db)))))

(defmethod select :default [db user ent-type where]
  (select-default db user ent-type where))

(defmethod select :tech-design [db user ent-type where]
  (select-default db user ent-type where '[* :tech-design/_refs {:file/_parent [*]}]))

(defmethod select :audit [db _ _ _]
  (into (mapv
         #(do {:db/txInstant (% 0)
               :tx/uid (% 1)
               :file {:db/id (% 2)
                      :file/orig-name (% 3)
                      :file/parent {:db/id (% 4)
                                    :ent/type (% 5)}}})
         (d/q '[:find ?txInstant ?uid ?file ?orig-name ?parent-id ?parent-ent-type
                :where
                [?file :file/view-count _ ?tx]
                [?tx :db/txInstant ?txInstant]
                [?tx :tx/uid ?uid]
                [?file :file/orig-name ?orig-name]
                [?file :file/parent ?parent-id]
                [?parent-id :ent/type ?parent-ent-type]]
              (d/history db)))
        (mapv
         #(do {:db/txInstant (% 0)
               :tx/uid (% 1)})
         (d/q '[:find ?txInstant ?uid
                :where
                [_ :user/login-count _ ?tx]
                [?tx :db/txInstant ?txInstant]
                [?tx :tx/uid ?uid]]
              (d/history db)))))

(defn select-ebs-documents [db ent-type where]
  (->>
   (apply d/q (common-db/add-where
               '{:find [[(pull ?e [* {:ebs/code-ref [*]
                                      :file/_parent [*]}]) ...]]
                 :in [$ ?ent-type]
                 :where [[?e :ent/type ?ent-type]]}
               where)
          db
          ent-type
          (vals where))
   (file-service/assoc-file-created db)))

(defmethod select :ebs-project [db user ent-type where]
  (select-ebs-documents db ent-type where))

(defmethod select :ebs-handover [db user ent-type where]
  (select-ebs-documents db ent-type where))

(defmethod select :ebs-construction-diary [db user ent-type where]
  (select-ebs-documents db ent-type where))

(defmethod select :ebs-calc [db user _ where]
  (cond->> (select-ebs-documents db :ebs-calc where)
    (not ((:-rights user) :ebs-calc/paid))
    (mapv #(dissoc % :ebs-calc/paid))))

(defmethod select :ebs-offer [db user ent-type where]
  (select-ebs-documents db ent-type where))

(defmethod select :ebs-other [db user ent-type where]
  (select-ebs-documents db ent-type where))

(defmethod select :crm-event-type [db user ent-type where]
  (->> (d/q '[:find [(pull ?e [*]) ...]
              :where
              [?e :db/ident ?kw]
              [(namespace ?kw) ?kw-ns]
              [(= ?kw-ns "crm-event.type")]]
            db)
       (sort-by :ent/title)))

(defmethod select :file [db user ent-type where]
  (select-default db user ent-type where '[* {:file/parent [:db/id :ent/type]}]))

;; see https://github.com/Datomic/day-of-datomic/blob/master/tutorial/time-rules.clj
(def time-rules
  '[[(entity-at [?e] ?tx ?t ?inst)
     [?e _ _ ?tx]
     [(datomic.api/tx->t ?tx) ?t]
     [?tx :db/txInstant ?inst]]
    [(value-at [?e] ?tx ?t ?inst)
     [_ _ ?e ?tx]
     [(datomic.api/tx->t ?tx) ?t]
     [?tx :db/txInstant ?inst]]
    [(entities-with [?log ?e] ?es)
     [?e _ _ ?tx]
     [(tx-data ?log ?tx) [[?es]]]]])

(defmethod select :issue [db user ent-type where]
  (->>
   (apply d/q (common-db/add-where
               {:find '[(pull ?e [* {:file/_parent [*]}]) (min ?inst) (min ?tx)]
                :in ['$ '$hist '%]
                :where '[[?e :ent/type :issue]
                         (entity-at ?e ?tx _ ?inst)]}
               where)
          db
          (d/history db)
          time-rules
          (vals where))
   (map (fn [row]
          (assoc (first row)
                 :-created-at (second row)
                 :-created-by (:tx/uid (d/pull db [:tx/uid] (nth row 2))))))
   (file-service/assoc-file-created db)))

(defn- administration? [{roles :user/roles}]
  (contains? #{"admin" "Monika"} roles))

(defmethod select :time-sheet [db user ent-type where]
  (select-default db user ent-type (cond-> where
                                     (not (administration? user))
                                     (assoc :ent/user (:db/id user)))))

(defmethod select :expense [db user ent-type where]
  (d/q (cond-> '[:find [(pull ?e [*]) ...]
                 :in $
                 :where
                 [?e :expense/price _]]
         (not (administration? user))
         (conj ['?e :ent/user (:db/id user)]))
       db))

(defmethod select :drive-book [db user _ _]
  (d/q (cond-> '[:find [(pull ?e [* {:time-sheet/_items [{:ent/user [:db/id]}]}]) ...]
                 :in $
                 :where
                 [?e :time-sheet/_items ?ts] ;; ignore orphans
                 [?e :time-sheet-item/type :time-sheet-item-type/on-the-road]]
         (not (administration? user))
         (conj ['?e :ent/user (:db/id user)]))
       db))
