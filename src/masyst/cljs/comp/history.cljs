(ns masyst.cljs.comp.history
  (:require [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.util :as util]
            [masyst.cljs.comp.data-table :as data-table]
            [re-frame.core :as re-frame]
            [reagent.ratom :as ratom]))

(re-frame/reg-sub-raw
 ::entity-history
 (fn [db [_]]
   (ratom/reaction (:entity-history @db))))

(re-frame/reg-sub-raw
 ::entity-history-datoms
 (fn [db [_]]
   (let [history (re-frame/subscribe [::entity-history])]
     (ratom/reaction (:datoms @history)))))

(re-frame/reg-event-db
 ::load-entity-history
 util/debug-mw
 (fn [db [_ ent-id]]
   (server-call [:entity/history ent-id]
                [::set-history ent-id])
   db))

(re-frame/reg-event-db
 ::set-history
 util/debug-mw
 (fn [db [_ ent-id datoms]]
   (assoc db :entity-history {:db/id ent-id
                              :datoms datoms})))

(defn view [user ent-id]
  (let [history (re-frame/subscribe [::entity-history])
        datoms(re-frame/subscribe [::entity-history-datoms])
        users (when ((:-rights user) :entity/history) (re-frame/subscribe [:entities :user]))]
    (fn [user ent-id]
      (when (and ((:-rights user) :entity/history) ent-id)
        (if (not= ent-id (:db/id @history))
          [:div
           [:br]
           [:a {:on-click #(re-frame/dispatch [::load-entity-history ent-id])} "Zobrazit historii"]]
          [:div
           [:h4 "Historie úprav"]
           [data-table/data-table
            :table-id :history
            :colls [["Kdy" #(get-in % [:tx :db/txInstant])]
                    ["Kdo" #(->> % :tx :tx/uid :db/id (get @users) :ent/title)]
                    ["Atribut" :a]
                    ["Hodnota" :v]
                    ["Smazano?" (complement :added?)]]
            :rows datoms
            :desc? true]])))))
