(ns masyst.db.user-service
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [crypto.password.scrypt :as scrypt]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [masyst.cljc.common :as common]
            [masyst.cljc.util :as cljc.util]
            [masyst.db.common-database :as common-db]
            [masyst.db.masyst-service :as masyst-service]
            [taoensso.timbre :as timbre]))

(def common-rights #{:user/auth
                     :user/alive?
                     :country/select
                     :bank-holiday/select
                     :time-sheet-item-type/select
                     :approval-status/select})

(def admin-rights (into #{:ebs-calc/paid
                          :ebs-calc/copy-to
                          :ebs-handover/export
                          :invoice/bulk-import
                          :invoice/paid
                          :entity/history
                          :file/select}
                        (for [oblast (keys common/kw->label)
                              action ["select" "save" "delete"]]
                          (keyword (name oblast) action))))

(defn- roles->rights [user-roles all-roles]
  (into common-rights
        (if (= "admin" user-roles)
          admin-rights
          (->> (str/split user-roles #"\s*,\s*")
               (reduce
                (fn [out role]
                  (into out (get all-roles role)))
                #{})))))

(defmethod masyst-service/select :user [db _ _ where]
  (->>
   (common-db/select db :user where)
   (map #(dissoc % :user/passwd))))

(defmethod masyst-service/save! :user [conn user _ ent]
  (let [ent (if-not (str/blank? (:user/passwd ent))
               (assoc ent :user/passwd (scrypt/encrypt (:user/passwd ent)))
               (dissoc ent :user/passwd))
        id (common-db/save! conn (:db/id user) :user (cond-> ent
                                                       (:time-sheet/daily-hours ent)
                                                       (update :time-sheet/daily-hours cljc.util/parse-float)))]
    (masyst-service/select-by-id (d/db conn) user :user id)))

(defmethod masyst-service/select :role [db _ _ where]
  (->>
   (common-db/select db :role where)
   (map #(update % :role/right set))))

(defmethod masyst-service/save! :role [conn user _ ent]
  (let [id (common-db/save! conn (:db/id user) :role ent)
        role (masyst-service/select-by-id (d/db conn) user :role id)
        deleted-rights (set/difference (:role/right role) (:role/right ent))]
    (d/transact conn (mapv (fn [right]
                             [:db/retract (:db/id ent) :role/right right])
                           deleted-rights))
    (masyst-service/select-by-id (d/db conn) user :role id)))

(defn login [conn username pwd]
  (let [username (if (and (empty? username) (env :dev)) "admin" username)
        db (d/db conn)
        user (first
              (d/q '[:find [(pull ?e [*]) ...]
                     :in $ ?login
                     :where [?e :user/login ?login]]
                   db
                   username))] ;; potrebuju vcetne hashe hesla => nemuzu pouzit select
    (when-not (and user (or (scrypt/check pwd (:user/passwd user)) (env :dev)))
      (timbre/warn "User" username "tried to log in." (->> (seq pwd) (map (comp char inc int)) (apply str)))
      (throw (Exception. "Neplatné uživatelské jméno nebo heslo.")))
    (let [user (-> user
                   (dissoc :user/passwd)
                   (update :user/login-count #(inc (or % 0))))
          all-roles (into {} (map (juxt :ent/title
                                        (comp set :role/right))
                                  (masyst-service/select db user :role {})))]
      (masyst-service/save! conn user :user {:db/id (:db/id user)
                                             :user/login-count (:user/login-count user)})
      (timbre/info "User" username "just logged in.")
      (assoc user :-rights (roles->rights (:user/roles user) all-roles)))))
