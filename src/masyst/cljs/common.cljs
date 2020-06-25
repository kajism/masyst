(ns masyst.cljs.common
  (:require [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.ratom :as ratom]
            [taoensso.timbre :as timbre]))

(defonce kw->url (atom {}))

(defn add-kw-url [kw url]
  (swap! kw->url assoc kw url))

;;---- Subscriptions--------------------------------------------
(re-frame/reg-sub-raw
 :auth-user
 (fn [db [_]]
   (let [out (ratom/reaction (:auth-user @db))]
     (when (nil? @out)
       (re-frame/dispatch [:load-auth-user]))
     out)))

(re-frame/reg-sub-raw
 :offline?
 (fn [db [_]]
   (ratom/reaction (:offline? @db))))

(re-frame/reg-sub-raw
 :entities
 (fn [db [_ kw]]
   (let [out (ratom/reaction (get @db kw))]
     (when (nil? @out)
       (re-frame/dispatch [:entities-load kw]))
     out)))

(re-frame/reg-sub-raw
 :entities-from-ciselnik
 (fn [db [_ kw]]
   (let [out (re-frame/subscribe [:ciselnik kw])]
     (ratom/reaction
      (->> @out
           (map (juxt :db/id identity))
           (into {}))))))

(re-frame/reg-sub-raw
 :entities-by-right
 (fn [db [_ kw] [user]]
   (if ((:-rights user) (keyword (name kw) "select"))
     (re-frame/subscribe [:entities kw])
     (atom nil))))

(re-frame/reg-sub-raw
 :ebs-entities
 (fn [db [_ kw ebs-tree-id]]
   (let [user (re-frame/subscribe [:auth-user])
         out (re-frame/subscribe [:entities-by-right kw] [user])]
     (ratom/reaction
      (->> @out
           (filter
            (fn [[k v]] (= ebs-tree-id (-> v :ebs/code-ref :db/id))))
           (into {}))))))

(re-frame/reg-sub-raw
 :entity-edit
 (fn [db [_ kw]]
   (let [id (ratom/reaction (get-in @db [:entity-edit kw :id]))
         ents (re-frame/subscribe [:entities kw])]
     (ratom/reaction (get @ents @id)))))

(re-frame/reg-sub-raw
 :entity-edit-dynamic
 (fn [db [_] [kw]]
   (re-frame/subscribe [:entity-edit kw])))

(re-frame/reg-sub-raw
 :entity-edit?
 (fn [db [_ kw]]
   (ratom/reaction (get-in @db [:entity-edit kw :edit?]))))

(re-frame/reg-sub-raw
 :entity-edit?-dynamic
 (fn [db [_] [kw]]
   (re-frame/subscribe [:entity-edit? kw])))

;;---- Handlers -----------------------------------------------
(re-frame/reg-event-db
 :load-auth-user
 util/debug-mw
 (fn [db [_]]
   (server-call [:user/auth {}]
                [:set-auth-user])
   db))

(re-frame/reg-event-db
 :set-auth-user
 util/debug-mw
 (fn [db [_ auth-user]]
   (assoc db :auth-user auth-user)))

(re-frame/reg-event-db
 :init-db
 (fn [db [_]]
   {:offline? false}))

(re-frame/reg-event-db
 :entities-load
 util/debug-mw
 (fn [db [_ kw]]
   (server-call [(keyword (name kw) "select") {}]
                [:entities-set kw])
   db))

(re-frame/reg-event-db
 :entities-set
 ;;util/debug-mw
 (fn [db [_ kw v]]
   (assoc db kw (into {} (map (juxt :db/id identity)
                              v)))))

(re-frame/reg-event-db
 :entity-set-edit
 util/debug-mw
 (fn [db [_ kw id edit?]]
   (assoc-in db [:entity-edit kw] {:id id
                                   :edit? (boolean edit?)})))

(re-frame/reg-event-db
 :entity-new
 util/debug-mw
 (fn [db [_ kw new-ent]]
   (set! js/window.location.hash (str "#/" (get @kw->url kw) "/e"))
   (if new-ent
     (assoc-in db [kw nil] new-ent)
     (update db kw dissoc nil))))

(re-frame/reg-event-db
 :entity-change
 util/debug-mw
 (fn [db [_ kw id attr val]]
   (if (fn? val)
     (update-in db [kw id attr] val)
     (assoc-in db [kw id attr] val))))

(re-frame/reg-event-db
 :entity-save
 util/debug-mw
 (fn [db [_ kw saved-evt]]
   (let [ent (get-in db [kw (get-in db [:entity-edit kw :id])])
         file (:-file ent)]
     (server-call [(keyword (name kw) "save") (util/dissoc-temp-keys ent)]
                  file
                  [:entity-saved kw saved-evt]))
   db))

(re-frame/reg-event-db
 :entity-saved
 util/debug-mw
 (fn [db [_ kw saved-evt new-ent]]
   (re-frame/dispatch [:set-msg :saved "Záznam byl uložen"])
   (when saved-evt
     (re-frame/dispatch (conj saved-evt new-ent)))
   (set! js/window.location.hash (str "#/" (get @kw->url kw) "/" (:db/id new-ent) "e"))
   (-> db
       (assoc-in [kw (:db/id new-ent)] new-ent)
       (update kw #(dissoc % nil)))))

(re-frame/reg-event-db
 :entity-delete
 util/debug-mw
 (fn [db [_ kw id]]
   (when id
     (server-call [(keyword (name kw) "delete") id] nil nil db))
   (update db kw #(dissoc % id))))

(re-frame/reg-event-db
 :file-delete
 util/debug-mw
 (fn [db [_ kw parent-id file-id]]
   (when file-id
     (server-call [(keyword (name kw) "delete") file-id] nil nil db))
   (-> db
       (update-in [kw parent-id :file/_parent] #(filterv (fn [file] (not= file-id (:db/id file))) %))
       (update :file #(dissoc % file-id)))))

(re-frame/reg-sub
 ::path-value
 (fn [db [_ path]]
   (assert (seqable? path) "Path should be a vector!")
   (get-in db path)))

(re-frame/reg-event-db
 ::set-path-value
 ;;util/debug-mw
 (fn [db [_ path value]]
   (assert (seqable? path) "Path should be a vector!")
   (if (and (fn? value)
            (not (keyword? value)))
     (update-in db path value)
     (assoc-in db path value))))
