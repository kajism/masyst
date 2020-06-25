(ns masyst.cljs.country
  (:require [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :as attachments]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [reagent.core :as reagent]
            [clojure.string :as str]
            [masyst.cljs.comp.history :as history]))

(defn detail [item user]
  [re-com/v-box :gap "5px"
   :children
   [[:label "Název"]
    [:p (str (:ent/title item))]
    [:label "Kód"]
    [:p (str (:country/code item))]
    [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]
    [history/view user (:db/id item)]]])

(defn form [item user]
  [:div
   [:label "Název"]
   [re-com/input-text
    :model (str (:ent/title item))
    :on-change #(re-frame/dispatch [:entity-change :country (:db/id item) :ent/title %])
    :width "400px"]
   [:br]
   [:label "Kód"]
   [re-com/input-text
    :model (str (:country/code item))
    :on-change #(re-frame/dispatch [:entity-change :country (:db/id item) :country/code %])
    :width "400px"]
   [:br]
   [buttons/form-buttons :country item]
   [history/view user (:db/id item)]])

(defn page-countries []
  (let [items (re-frame/subscribe [:entities :country])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :country])]
    (fn []
      [:div
       [:h3 "Státy"]
       (when ((:-rights @user) :country/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/stat/e")]
          [:br]
          [:br]])
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :country
          :colls [["Název" :ent/title]
                  ["Kód" :country/code]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :country])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                          :href (str "#/stat/" (:db/id row))]
                         (when ((:-rights @user) :country/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/stat/" (:db/id row) "e")])
                         (when ((:-rights @user) :country/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :country (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 0])])))

(defn page-country []
  (let [edit? (re-frame/subscribe [:entity-edit? :country])
        item (re-frame/subscribe [:entity-edit :country])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Stát"]
       (if (and @edit? ((:-rights @user) :country/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :countries  #'page-countries)
(secretary/defroute "/staty" []
  (re-frame/dispatch [:set-current-page :countries]))

(common/add-kw-url :country "stat")
(pages/add-page :country #'page-country)
(secretary/defroute #"/stat/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :country (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :country]))
