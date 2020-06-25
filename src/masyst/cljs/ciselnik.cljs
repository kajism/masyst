(ns masyst.cljs.ciselnik
  (:require [clojure.string :as str]
            [masyst.cljc.tools :as tools]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.comp.buttons :refer [save-button]]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn new-item [kw]
  {:ent/title ""
   :ent/type kw})

(defn validate [item]
  (cond-> {}
    (str/blank? (:ent/title item))
    (assoc :ent/title "Vyplňte název položky")))

;;---- Subscriptions--------------------------------------------
(re-frame/reg-sub-raw
 :ciselnik
 (fn [db [_ kw]]
   (let [out (ratom/reaction (get-in @db [:ciselnik kw]))]
     (when (nil? @out)
       (re-frame/dispatch [:ciselnik-load kw]))
     out)))

(re-frame/reg-sub-raw
 :ciselnik-new
 (fn [db [_ kw]]
   (ratom/reaction (get-in @db [:ciselnik-new kw]))))

;;---- Handlers -----------------------------------------------
(re-frame/reg-event-db
 :ciselnik-load
 util/debug-mw
 (fn [db [_ kw]]
   (server-call [(keyword (name kw) "select")]
                [:ciselnik-set kw])
   db))

(re-frame/reg-event-db
 :ciselnik-set
 util/debug-mw
 (fn [db [_ kw v]]
   (assoc-in db [:ciselnik kw] (vec (util/sort-by-locale :ent/title v)))))

(re-frame/reg-event-db
 :ciselnik-change
 util/debug-mw
 (fn [db [_ kw attr val]]
   (assoc-in db [:ciselnik-new kw attr] val)))

(re-frame/reg-event-db
 :ciselnik-save
 util/debug-mw
 (fn [db [_ kw]]
   (let [item (util/dissoc-temp-keys (get-in db [:ciselnik-new kw]))
         errors (validate item)]
     (if (empty? errors)
       (server-call [(keyword (name kw) "save") item]
                    [:ciselnik-saved kw])
       (re-frame/dispatch [:ciselnik-change kw :-errors errors]))
     db)))

(re-frame/reg-event-db
 :ciselnik-saved
 util/debug-mw
 (fn [db [_ kw {:keys [:db/id] :as item}]]
   (let [ciselnik-new (get-in db [:ciselnik-new kw])
         ciselnik (get-in db [:ciselnik kw])
         ciselnik (if (:db/id ciselnik-new);;existing item edited
                    (remove #(= (:db/id %) id) ciselnik)
                    ciselnik)]
     (re-frame/dispatch [:ciselnik-set kw (conj ciselnik item)])
     (re-frame/dispatch [:set-msg :info "Záznam byl uložen"])
     (assoc-in db [:ciselnik-new kw] (new-item kw)))))

(re-frame/reg-event-db
 :ciselnik-edit
 util/debug-mw
 (fn [db [_ kw id]]
   (assoc-in db [:ciselnik-new kw] (tools/find-by-db-id (get-in db [:ciselnik kw]) id))))

(re-frame/reg-event-db
 :ciselnik-delete
 util/debug-mw
 (fn [db [_ kw id]]
   (server-call [(keyword (name kw) "delete") id] nil nil db)
   (update-in db [:ciselnik kw] #(filterv (fn [item]
                                            (not= id (:db/id item)))
                                          %))))

(re-frame/reg-event-db
 :ciselnik-new
 util/debug-mw
 (fn [db [_ kw]]
   (assoc-in db [:ciselnik-new kw] (new-item kw))))

;; ------ View-------------------------------------------------------
(defn view [kw]
  (let [items (re-frame/subscribe [:ciselnik kw])
        item  (re-frame/subscribe [:ciselnik-new kw])
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state kw])]
    (fn []
      (if-not (and @items @user)
        [re-com/throbber]
        [:div
         (when ((:-rights @user) (keyword (name kw) "save"))
           [re-com/v-box :children
            [[re-com/title :label "Přidat / upravit položku" :underline? true]
             [re-com/h-box :gap "5px" :children
              [[re-com/label :label "Název"]
               [re-com/input-text :model (str (:ent/title @item))
                :on-change #(re-frame/dispatch [:ciselnik-change kw :ent/title %])]
               [save-button (:-errors @item) [:ciselnik-save kw] [:ciselnik-change kw]]]]]])
         [re-com/title :label "Seznam" :underline? true]
         [data-table
          :table-id kw
          :colls [["Název" :ent/title]
                  [[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:ciselnik-load kw])]
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [:div
                        (when ((:-rights @user) (keyword (name kw) "save"))
                          [re-com/md-icon-button
                           :md-icon-name "zmdi-edit"
                           :tooltip "Editovat"
                           :on-click #(re-frame/dispatch [:ciselnik-edit kw (:db/id row)])])
                        (when ((:-rights @user) (keyword (name kw) "delete"))
                          [re-com/md-icon-button
                           :md-icon-name "zmdi-delete"
                           :tooltip "Smazat"
                           :on-click #(re-frame/dispatch [:ciselnik-delete kw (:db/id row)])])]))
                   :none]]
          :rows items
          :order-by 0]]))))

(defn page-cost-center []
  [:div
   [:h3 "Střediska"]
   [view :cost-center]])
(pages/add-page :cost-center  #'page-cost-center)
(secretary/defroute "/strediska" []
  (re-frame/dispatch [:set-current-page :cost-center]))

(defn page-supplier []
  [:div
   [:h3 "Dodavatelé"]
   [view :supplier]])
(pages/add-page :supplier  #'page-supplier)
(secretary/defroute "/dodavatele" []
  (re-frame/dispatch [:set-current-page :supplier]))

(defn page-materials []
  [:div
   [:h3 "Materiály"]
   [view :material]])
(pages/add-page :material #'page-materials)
(secretary/defroute "/materialy" []
  (re-frame/dispatch [:set-current-page :material]))

(defn page-ebs-other-category []
  [:div
   [:h3 "Ostatní kategorie"]
   [view :ebs-other-category]])
(pages/add-page :ebs-other-category #'page-ebs-other-category)
(secretary/defroute "/ebs-ostatni-kategorie" []
  (re-frame/dispatch [:set-current-page :ebs-other-category]))

(defn page-issue-states []
  [:div
   [:h3 "Stavy požadavků"]
   [view :issue-state]])
(pages/add-page :issue-states #'page-issue-states)
(secretary/defroute "/issue-states" []
  (re-frame/dispatch [:set-current-page :issue-states]))

(defn page-issue-types []
  [:div
   [:h3 "Typy požadavků"]
   [view :issue-type]])
(pages/add-page :issue-types #'page-issue-types)
(secretary/defroute "/issue-types" []
  (re-frame/dispatch [:set-current-page :issue-types]))

(defn page-issue-priorities []
  [:div
   [:h3 "Priority požadavků"]
   [view :issue-priority]])
(pages/add-page :issue-priorities #'page-issue-priorities)
(secretary/defroute "/issue-priorities" []
  (re-frame/dispatch [:set-current-page :issue-priorities]))

(defn page-issue-projects []
  [:div
   [:h3 "Projekty požadavků"]
   [view :issue-project]])
(pages/add-page :issue-projects #'page-issue-projects)
(secretary/defroute "/issue-projects" []
  (re-frame/dispatch [:set-current-page :issue-projects]))

(defn page-automobiles []
  [:div
   [:h3 "Automobily"]
   [view :automobile]])
(pages/add-page :automobiles #'page-automobiles)
(secretary/defroute "/automobily" []
  (re-frame/dispatch [:set-current-page :automobiles]))
