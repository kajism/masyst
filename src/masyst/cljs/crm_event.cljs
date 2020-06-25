(ns masyst.cljs.crm-event
  (:require [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :as attachments]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.comp.history :as history]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]))

(defn form [item user]
  [:div "TBD"])

(defn page-crm-events []
  (let [items (re-frame/subscribe [:entities :crm-event])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        subjects (re-frame/subscribe [:entities :crm-subj])
        projects (re-frame/subscribe [:entities :crm-project])
        event-types (re-frame/subscribe [:entities :crm-event-type])
        table-state (re-frame/subscribe [:table-state :crm-events])]
    (fn []
      [:div
       [:h3 "CRM Události"]
       (when ((:-rights @user) :crm-event/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nová"] :href (str "#/crm-udalost/e")]
          [:br]
          [:br]])
       (if-not (and @items @event-types @subjects @projects)
         [re-com/throbber]
         [data-table/data-table
          :table-id :crm-events
          :colls [["Datum" :ent/date]
                  ["Typ" #(some->> % :crm-event/type :db/id (get @event-types) :ent/title str)]
                  ["Subjekt" #(some->> % :crm/subject :db/id (get @subjects) :ent/title str)]
                  ["Projekt" #(some->> % :crm-event/project :db/id (get @projects) :ent/title str)]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :crm-event])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [#_[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                            :href (str "#/crm-udalost/" (:db/id row))]
                         (when ((:-rights @user) :crm-event/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/crm-udalost/" (:db/id row) "e")])
                         (when ((:-rights @user) :crm-event/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :crm-event (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 0
          :desc? true])])))

(defn page-crm-event []
  (let [edit? (re-frame/subscribe [:entity-edit? :crm-event])
        item (re-frame/subscribe [:entity-edit :crm-event])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Událost"]
       [form @item @user]
       #_(if (and @edit? ((:-rights @user) :crm-event/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :crm-events  #'page-crm-events)
(secretary/defroute "/crm-udalosti" []
  (re-frame/dispatch [:set-current-page :crm-events]))

(common/add-kw-url :crm-event "crm-udalost")
(pages/add-page :crm-event #'page-crm-event)
(secretary/defroute #"/crm-udalost/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :crm-event (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :crm-event]))
