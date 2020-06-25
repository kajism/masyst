(ns masyst.cljs.expense
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
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as t]))

(re-frame/reg-sub-raw
 ::rows
 (fn [db [_]]
   (let [expenses (re-frame/subscribe [:entities :expense])
         page-state (re-frame/subscribe [:page-state :expenses])
         user (re-frame/subscribe [:auth-user])]
     (ratom/reaction
      (cond->> (or (vals @expenses) [])
        (some? (::from @page-state))
        (filter (let [dt (tc/from-date (::from @page-state))]
                  #(not (t/before? (tc/from-date (or (:ent/date %) (:ent/from %))) dt))))
        (some? (::to @page-state))
        (filter (let [dt (tc/from-date (::to @page-state))]
                  #(not (t/after? (tc/from-date (or (:ent/date %) (:ent/from %))) dt)))))))))

(defn detail []
  (let [users (re-frame/subscribe [:entities :user])]
    (fn [item user]
      (if-not @users
        [re-com/throbber]
        [re-com/v-box :gap "5px"
         :children
         [[:label "Osoba"]
          [:p (some->> item :ent/user :db/id (get @users) :ent/title (str))]
          [:label "Datum"]
          [:p
           (cljc.util/date-to-str (:ent/date item))
           (cljc.util/datetime-to-str (:ent/from item))]
          [:label "Cena Kč"]
          [:p (util/float--text (:expense/price item))]
          [:label "Proplaceno?"]
          [:p (util/boolean->text (:expense/paid? item))]
          [:label "Poznámka"]
          (util/dangerousHTML (str/replace (str (:ent/annotation item)) #"\n" "<br />"))
          [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]
          [history/view user (:db/id item)]]]))))

(defn form []
  (let [users (re-frame/subscribe [:entities :user])]
    (fn [item user]
      (if-not @users
        [re-com/throbber]
        [re-com/v-box :children
         [[:label "Osoba"]
          [:p (some->> item :ent/user :db/id (get @users) :ent/title (str))]
          [:label "Datum"]
          (if (:ent/from item)
            [:p (cljc.util/datetime-to-str (:ent/from item))]
            [re-com/input-text
             :model (cljc.util/date-to-str (:ent/date item))
             :on-change #(re-frame/dispatch [:entity-change :expense (:db/id item) :ent/date (cljc.util/from-dMyyyy %)])
             :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
             :width "100px"])
          [:label "Cena Kč"]
          [re-com/input-text
           :model (util/float--text (:expense/price item))
           :on-change #(re-frame/dispatch [:entity-change :expense (:db/id item) :expense/price (cljc.util/parse-float %)])
           :validation-regex #"^(\d{0,4},?\d{0,2})$"
           :width "100px"]
          [:label "Proplaceno?"]
          [re-com/checkbox
           :model (:expense/paid? item)
           :on-change #(re-frame/dispatch [:entity-change :expense (:db/id item) :expense/paid? %])]
          [:label "Poznámka"]
          [re-com/input-textarea
           :model (str (:ent/annotation item))
           :on-change #(re-frame/dispatch [:entity-change :expense (:db/id item) :ent/annotation %])
           :width "400px"]
          [:br]
          [buttons/form-buttons :expense item]
          [history/view user (:db/id item)]]]))))

(defn page-expenses []
  (let [items (re-frame/subscribe [::rows])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        users (re-frame/subscribe [:entities :user])
        table-state (re-frame/subscribe [:table-state :expenses])
        page-state (re-frame/subscribe [:page-state :expenses])]
    (fn []
      [:div
       [:h3 "Výdaje"]
       [re-com/h-box :gap "20px" :align :center
        :children
        [(when ((:-rights @user) :expense/save)
           [:div
            [re-com/button :label "Nový"
             :on-click #(re-frame/dispatch [:entity-new :expense {:ent/user (select-keys @user [:db/id])
                                                                  :ent/date (cljc.util/today)
                                                                  :expense/paid? false}])]
            [:br]
            [:br]])
         [re-com/label :label "Od:"]
         [re-com/input-text
          :model (cljc.util/date-to-str (::from @page-state))
          :on-change #(re-frame/dispatch [:page-state-change :expenses ::from (cljc.util/from-dMyyyy %)])
          :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
          :width "100px"]
         [re-com/label :label "Do:"]
         [re-com/input-text
          :model (cljc.util/date-to-str (::to @page-state))
          :on-change #(re-frame/dispatch [:page-state-change :expenses ::to (cljc.util/from-dMyyyy %)])
          :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
          :width "100px"]]]
       (if-not (and @items @users)
         [re-com/throbber]
         [data-table/data-table
          :table-id :expenses
          :colls [["Osoba" #(some->> % :ent/user :db/id (get @users) :ent/title (str))]
                  ["Datum" #(or (:ent/date %) (:ent/from %))]
                  ["Cena Kč" :expense/price :sum]
                  ["Proplaceno?" :expense/paid?]
                  ["Poznámka" (comp cljc.util/shorten :ent/annotation)]
                  [(if @offline?
                    ""
                    [re-com/md-icon-button
                     :md-icon-name "zmdi-refresh"
                     :tooltip "Načíst ze serveru"
                     :on-click #(re-frame/dispatch [:entities-load :expense])])
                  (fn [row]
                    (when (= (:db/id row) (:selected-row-id @table-state))
                      [re-com/h-box
                       :gap "5px"
                       :children
                       [[re-com/hyperlink-href
                         :label [re-com/md-icon-button
                                 :md-icon-name "zmdi-view-web"
                                 :tooltip "Detail"]
                         :href (str "#/vydaj/" (:db/id row))]
                        (when ((:-rights @user) :expense/save)
                          [re-com/hyperlink-href
                           :label [re-com/md-icon-button
                                   :md-icon-name "zmdi-edit"
                                   :tooltip "Editovat"]
                           :href (str "#/vydaj/" (:db/id row) "e")])
                        (when ((:-rights @user) :expense/delete)
                          [buttons/delete-button #(re-frame/dispatch [:entity-delete :expense (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 1
          :desc? true])])))

(defn page-expense []
  (let [edit? (re-frame/subscribe [:entity-edit? :expense])
        item (re-frame/subscribe [:entity-edit :expense])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Výdaj"]
       (if (and @edit? ((:-rights @user) :expense/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :expenses  #'page-expenses)

(secretary/defroute "/vydaje" []
  (re-frame/dispatch [:set-current-page :expenses]))

(common/add-kw-url :expense "vydaj")
(pages/add-page :expense #'page-expense)
(secretary/defroute #"/vydaj/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :expense (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :expense]))
