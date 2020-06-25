(ns masyst.cljs.order
  (:require [clojure.string :as str]
            [cljs-time.coerce :as tc]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :as attachments :refer [attachments]]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]))

(defn table []
  (let [orders (re-frame/subscribe [:entities :order])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (if-not (and @orders @user)
        [re-com/throbber]
        [:div
         (when ((:-rights @user) :order/save)
           [:div
            [re-com/hyperlink-href :label [re-com/button :label "Nová"] :href (str "#/objednavka/")]])
         [data-table
          :colls [["Datum objednávky" :order/date]
                  ["Název" :ent/title]
                  ["Dodavatel" #(str (-> % :order/supplier :ent/title))]
                  [[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:entities-load :order])]
                   (fn [row]
                     [:div
                      (when ((:-rights @user) :order/save)
                        [re-com/hyperlink-href
                         :label [re-com/md-icon-button
                                 :md-icon-name "zmdi-edit"
                                 :tooltip "Editovat"]
                         :href (str "#/objednavka/" (:db/id row))])
                      (when ((:-rights @user) :order/delete)
                        [re-com/md-icon-button
                         :tooltip "Smazat"
                         :md-icon-name "zmdi-delete"
                         :on-click #(re-frame/dispatch [:entity-delete :order (:db/id row)])])])
                   :none]]
          :rows orders
          :order-by 0
          :desc? true]]))))

(defn form []
  (let [order (re-frame/subscribe [:entity-edit :order])
        suppliers (re-frame/subscribe [:ciselnik :supplier])]
    (fn order-form-render []
      (let [item @order]
        (if-not @suppliers
          [re-com/throbber]
          [:div
           [:label "Datum objednávky"]
           [:br]
           [re-com/datepicker-dropdown
            :model (tc/from-date (:order/date item))
            :on-change #(re-frame/dispatch [:entity-change :order (:db/id item) :order/date (tc/to-date %)])
            :show-today? true]
           [:br]
           [:label "Název"]
           [re-com/input-text :model (str (:ent/title item))
            :on-change #(re-frame/dispatch [:entity-change :order (:db/id item) :ent/title %])]
           [:br]
           [:label "Dodavatel"]
           [:br]
           [re-com/single-dropdown
            :choices @suppliers
            :id-fn :db/id
            :label-fn :ent/title
            :model (get-in item [:order/supplier :db/id])
            :on-change #(re-frame/dispatch [:entity-change :order (:db/id item) :order/supplier {:db/id %}])
            :placeholder "Vyberte dodavatele"
            :filter-box? true
            :width "400px"]
           [re-com/hyperlink-href :label [re-com/button :label "Dodavatelé"] :href (str "#/dodavatele")]
           [:br]
           [:label "Poznámka"]
           [re-com/input-textarea :model (str (:ent/annotation item))
            :on-change #(re-frame/dispatch [:entity-change :order (:db/id item) :ent/annotation %])]
           [:label "Soubor"]
           [:input {:type :file
                    :on-change
                    (fn [ev]
                      (let [file (aget (-> ev .-target .-files) 0)]
                        (.log js/console file)
                        (re-frame/dispatch [:entity-change :order (:db/id item) :-file file])))}]
           [:br]
           [buttons/form-buttons :order item]
           [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]])))))

(defn page-orders []
  [:div
   [:h3 "Objednávky"]
   [table]])

(defn page-order []
  [:div
   [:h3 "Objednávka"]
   [form]])

(pages/add-page :order  #'page-order)
(pages/add-page :orders  #'page-orders)

(secretary/defroute "/objednavky" []
  (re-frame/dispatch [:set-current-page :orders]))

(secretary/defroute #"/objednavka/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :order (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :order]))

(common/add-kw-url :order "objednavka")
