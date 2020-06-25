(ns masyst.cljs.pasman
  (:require [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.comp.history :as history]
            [masyst.cljs.pages :as pages]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [taoensso.timbre :as timbre]))

(defn detail [item user]
  [re-com/v-box :gap "5px"
   :children
   [[re-com/h-box :gap "5px"
     :children
     [[re-com/box :width "300px"
       :child
       [:div
        [:label "Od"]
        [:p (cljc.util/date-to-str (:ent/from item))]
        [:label "Do"]
        [:p (cljc.util/date-to-str (:ent/to item))]
        [:label "Počet nocí"]
        [:p (cljc.util/calendar-nights item)]
        [:label "Chata"]
        [:p (->> item :pasman/cottage-no (get cljc.util/pasman-cottages) :label)]
        [:label "Příjmení"]
        [:p (str (:pasman/last-name item))]
        [:label "Jméno"]
        [:p (str (:pasman/first-name item))]
        [:label "Číslo dokladu"]
        [:p (str (:pasman/passport-no item))]
        [:label "Věk"]
        [:p (str (:pasman/age item))]
        [:label "Město"]
        [:p (str (:pasman/address item))]]]
      ]]
    [buttons/approval item]
    [history/view user (:db/id item)]]])

(defn form []
  (let [users (re-frame/subscribe [:entities :user])
        approval-states (re-frame/subscribe [:entities :approval-status])
        bank-holidays (re-frame/subscribe [:entities :bank-holiday])]
    (fn [item user]
      (if-not (and @users @bank-holidays)
        [re-com/throbber]
        [re-com/v-box :children
         [[:label "Od"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:ent/from item))
           :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :ent/from (cljc.util/from-dMyyyy %)])
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"]
          [:label "Do"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:ent/to item))
           :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :ent/to (cljc.util/from-dMyyyy %)])
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"
           :disabled? (:pasman/half-day? item)]
          [:label "Počet nocí"]
          [:p (cljc.util/calendar-nights item)]
          [:label "Chata"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices (vals cljc.util/pasman-cottages)
             :model (:pasman/cottage-no item)
             :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :pasman/cottage-no %])
             :placeholder "Vyberte chatu"
             :filter-box? true
             :width "400px"]]]
          [:label "Příjmení"]
          [re-com/input-text
           :model (str (:pasman/last-name item))
           :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :pasman/last-name %])
           :width "400px"]
          [:label "Jméno"]
          [re-com/input-text
           :model (str (:pasman/first-name item))
           :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :pasman/first-name %])
           :width "400px"]
          [:label "Číslo dokladu"]
          [re-com/input-text
           :model (str (:pasman/passport-no item))
           :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :pasman/passport-no %])
           :width "400px"]
          [:label "Věk"]
          [re-com/input-text
           :model (str (:pasman/age item))
           :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :pasman/age (or (cljc.util/parse-int %) 0)])
           :width "400px"]
          [:label "Město"]
          [re-com/input-text
           :model (str (:pasman/address item))
           :on-change #(re-frame/dispatch [:entity-change :pasman (:db/id item) :pasman/address %])
           :width "400px"]
          [:br]
          [buttons/form-buttons :pasman item]
          [history/view user (:db/id item)]]]))))

(defn page-pasmans []
  (let [items (re-frame/subscribe [:entities :pasman])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :pasmans])]
    (fn []
      [:div
       [:h3 "Pašmán - Registrace pro Pobytovovou taxu"]
       (when ((:-rights @user) :pasman/save)
         [:div
          [re-com/button :label "Nová"
           :on-click #(re-frame/dispatch
                       [:entity-new :pasman {}])]
          [:br]
          [:br]])
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :pasmans
          :colls [["Od" :ent/from]
                  ["Do" :ent/to]
                  ["Počet nocí" #(cljc.util/calendar-nights %)]
                  ["Chata" #(some->> % :pasman/cottage-no (get cljc.util/pasman-cottages) :label)]
                  ["Příjmení" :pasman/last-name]
                  ["Jméno" :pasman/first-name]
                  ["Číslo dokladu" :pasman/passport-no]
                  ["Věk" :pasman/age]
                  ["Město" :pasman/address]
                  ["ID skupiny" :pasman/group-uuid]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :pasman])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                          :href (str "#/pasman-reg/" (:db/id row))]
                         (when ((:-rights @user) :pasman/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/pasman-reg/" (:db/id row) "e")])
                         (when ((:-rights @user) :pasman/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :pasman (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 0
          :desc? true
          :sum-format "%.1f"])])))

(defn page-pasman []
  (let [edit? (re-frame/subscribe [:entity-edit? :pasman])
        item (re-frame/subscribe [:entity-edit :pasman])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Pašmán - Registrace pro Pobytovovou taxu"]
       (if (and @edit? ((:-rights @user) :pasman/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :pasmans  #'page-pasmans)

(secretary/defroute "/pasman-regs" []
  (re-frame/dispatch [:set-current-page :pasmans]))

(common/add-kw-url :pasman "pasman-reg")
(pages/add-page :pasman #'page-pasman)
(secretary/defroute #"/pasman-reg/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :pasman (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :pasman]))
