(ns masyst.cljs.crm-sale
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

(defn page-crm-sales []
  (let [items (re-frame/subscribe [:entities :crm-sale])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        subjects (re-frame/subscribe [:entities :crm-subj])
        products (re-frame/subscribe [:entities :crm-product])
        table-state (re-frame/subscribe [:table-state :crm-sales])]
    (fn []
      [:div
       [:h3 "CRM Prodeje"]
       (when ((:-rights @user) :crm-sale/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/crm-prodej/e")]
          [:br]
          [:br]])
       (if-not (and @items @subjects @products)
         [re-com/throbber]
         [data-table/data-table
          :table-id :crm-sales
          :colls [["Datum" :ent/date]
                  ["Subjekt" #(some->> % :crm/subject :db/id (get @subjects) :ent/title str)]
                  ["Projekt" #(some->> % :crm-sale/product :db/id (get @products) :ent/title str)]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :crm-sale])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [#_[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                            :href (str "#/crm-prodej/" (:db/id row))]
                         (when ((:-rights @user) :crm-sale/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/crm-prodej/" (:db/id row) "e")])
                         (when ((:-rights @user) :crm-sale/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :crm-sale (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 0
          :desc? true])])))

(defn page-crm-sale []
  (let [edit? (re-frame/subscribe [:entity-edit? :crm-sale])
        item (re-frame/subscribe [:entity-edit :crm-sale])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "CRM Prodej"]
       [form @item @user]
       #_(if (and @edit? ((:-rights @user) :crm-sale/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :crm-sales  #'page-crm-sales)
(secretary/defroute "/crm-prodeje" []
  (re-frame/dispatch [:set-current-page :crm-sales]))

(common/add-kw-url :crm-sale "crm-prodej")
(pages/add-page :crm-sale #'page-crm-sale)
(secretary/defroute #"/crm-prodej/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :crm-sale (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :crm-sale]))
