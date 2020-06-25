(ns masyst.cljs.pages
  (:require [clojure.string :as str]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]))

(def pages (atom {}))

(defn add-page [kw comp-fn]
  (swap! pages assoc kw comp-fn))

(re-frame/reg-sub-raw
 :page-state
 (fn [db [_ page-id]]
   (ratom/reaction (get-in @db [:page-states page-id]))))

(re-frame/reg-event-db
 :page-state-set
 util/debug-mw
 (fn [db [_ page-id state]]
   (assoc-in db [:page-states page-id] state)))

(re-frame/reg-event-db
 :page-state-change
 util/debug-mw
 (fn [db [_ page-id key val]]
   ((if (fn? val) update-in assoc-in) db [:page-states page-id key] val)))

(re-frame/reg-sub-raw
 :current-page
 (fn [db _]
   (ratom/reaction (:current-page @db))))

(re-frame/reg-event-db
 :set-current-page
 util/debug-mw
 (fn [db [_ current-page]]
   (when-not (:offline? db)
     (server-call [:user/alive? {}] nil))
   (assoc db :current-page current-page)))

(re-frame/reg-sub-raw
 :msg
 (fn [db [_ kw]]
   (ratom/reaction (get-in @db [:msg kw]))))

(re-frame/reg-event-db
 :set-msg
 util/debug-mw
 (fn [db [_ kw msg rollback-db]]
   (when (and msg (#{:info :saved} kw))
     (js/setTimeout #(re-frame/dispatch [:set-msg kw nil]) 2000))
   (let [db (or rollback-db db)]
     (assoc-in db [:msg kw] msg))))

(defn error-msg-popup [title msg on-close]
  [re-com/modal-panel
   :backdrop-on-click on-close
   :child [re-com/alert-box
           :alert-type :danger
           :closeable? true
           :heading title
           :body msg
           :on-close on-close]])

(defn page []
  (let [current-page (re-frame/subscribe [:current-page])
        error-msg (re-frame/subscribe [:msg :error])
        info-msg (re-frame/subscribe [:msg :info])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (if-not (and @current-page @user)
        [re-com/throbber]
        [:div
         (when-not (str/blank? @info-msg)
           [re-com/alert-box
            :alert-type :info
            :body @info-msg
            :style {:position "fixed" :top "70px"}])
         [(get @pages @current-page)]
         (when-not (str/blank? @error-msg)
           [error-msg-popup "Systémová chyba" @error-msg #(re-frame/dispatch [:set-msg :error nil])])]))))
