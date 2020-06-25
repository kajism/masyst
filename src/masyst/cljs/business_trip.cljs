(ns masyst.cljs.business-trip
  (:require [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.comp.history :as history]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [taoensso.timbre :as timbre]))

(defn detail []
  (let [users (re-frame/subscribe [:entities :user])
        approval-states (re-frame/subscribe [:entities :approval-status])
        automobiles (re-frame/subscribe [:entities-from-ciselnik :automobile])]
    (fn [item user]
      (if-not (and @users @automobiles)
        [re-com/throbber]
        [re-com/v-box :gap "5px"
         :children
         [[re-com/h-box :gap "5px"
           :children
           [[re-com/box :width "300px"
             :child
             [:div
              [:label "Osoba"]
              [:p (or (->> item :ent/user :db/id (get @users) :ent/title)
                      (:ent/title user))]
              [:label "Od"]
              [:p (cljc.util/to-format (:ent/from item) cljc.util/formatter-ddMMyyyyHHmm)]
              [:label "Do"]
              [:p (cljc.util/to-format (:ent/to item) cljc.util/formatter-ddMMyyyyHHmm)]
              [:label "Kam"]
              [:p (:business-trip/where item)]
              [:label "Automobil"]
              [:p (->> item :ent/automobile :db/id (get @automobiles) :ent/title)]
              [:label "Poznámka"]
              (util/dangerousHTML (str/replace (str (:ent/annotation item)) #"\n" "<br />"))
              [:label "Schvalování"]
              [:p (->> item :ent/approval-status :db/id (get @approval-states) :ent/title)]
              (when (:ent/approval-note item)
                [:label "Poznámka schvalovatele"])
              (when (:ent/approval-note item)
                (util/dangerousHTML (str/replace (str (:ent/approval-note item)) #"\n" "<br />")))]]
            #_[:div]]]
          [buttons/approval item]
          [history/view user (:db/id item)]]]))))

(defn form []
  (let [users (re-frame/subscribe [:entities :user])
        approval-states (re-frame/subscribe [:entities :approval-status])
        automobiles (re-frame/subscribe [:ciselnik :automobile])]
    (fn [item user]
      (if-not (and @users @automobiles)
        [re-com/throbber]
        [re-com/v-box :children
         [[:label "Osoba"]
          [:p (or (->> item :ent/user :db/id (get @users) :ent/title)
                  (:ent/title user))]
          [:label "Od"]
          (let [[date-str time-str] (str/split (cljc.util/to-format (:ent/from item) cljc.util/formatter-ddMMyyyyHHmm) #"\s")]
            [re-com/h-box :gap "5px" :children
             [[re-com/input-text
               :model (str date-str)
               :on-change #(re-frame/dispatch [:entity-change :business-trip (:db/id item) :ent/from (cljc.util/from-dMyyyy %)])
               :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
               :width "100px"]
              (when date-str
                [re-com/input-text
                 :model (str time-str)
                 :on-change #(re-frame/dispatch [:entity-change :business-trip (:db/id item) :ent/from (cljc.util/from-HHmm (:ent/from item) %)])
                 :validation-regex #"^(\d{0,2}):?(\d{1,2})?$"
                 :width "100px"])]])
          [:label "Do"]
          (let [[date-str time-str] (str/split (cljc.util/to-format (:ent/to item) cljc.util/formatter-ddMMyyyyHHmm) #"\s")]
            [re-com/h-box :gap "5px" :children
             [[re-com/input-text
               :model (str date-str)
               :on-change #(re-frame/dispatch [:entity-change :business-trip (:db/id item) :ent/to (cljc.util/from-dMyyyy %)])
               :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
               :width "100px"]
              (when date-str
                [re-com/input-text
                 :model (str time-str)
                 :on-change #(re-frame/dispatch [:entity-change :business-trip (:db/id item) :ent/to (cljc.util/from-HHmm (:ent/to item) %)])
                 :validation-regex #"^(\d{0,2}):?(\d{1,2})?$"
                 :width "100px"])]])
          [:label "Kam"]
          [re-com/input-text
           :model (str (:business-trip/where item))
           :on-change #(re-frame/dispatch [:entity-change :business-trip (:db/id item) :business-trip/where@ %])
           :width "400px"]
          [:label "Automobil"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices automobiles
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:ent/automobile :db/id])
             :on-change #(re-frame/dispatch [:entity-change :business-trip (:db/id item) :ent/automobile {:db/id %}])
             :placeholder "Vyberte automobil"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Automobily" :class "btn-sm"] :href (str "#/automobily")]]]
          [:label "Poznámka"]
          [re-com/input-textarea
           :model (str (:ent/annotation item))
           :on-change #(re-frame/dispatch [:entity-change :business-trip (:db/id item) :ent/annotation %])
           :width "400px"]
          [:label "Schvalování"]
          [:p (->> item :ent/approval-status :db/id (get @approval-states) :ent/title)]
          [:br]
          [buttons/form-buttons :business-trip item]
          [history/view user (:db/id item)]]]))))

(defn page-business-trips []
  (let [items (re-frame/subscribe [:entities :business-trip])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        users (re-frame/subscribe [:entities-from-ciselnik :user])
        approval-states (re-frame/subscribe [:entities :approval-status])
        automobiles (re-frame/subscribe [:entities-from-ciselnik :automobile])
        table-state (re-frame/subscribe [:table-state :business-trips])]
    (fn []
      [:div
       [:h3 "Služební cesty"]
       (when ((:-rights @user) :business-trip/save)
         [:div
          [re-com/button :label "Nová"
           :on-click #(re-frame/dispatch
                       [:entity-new :business-trip {:ent/user (select-keys @user [:db/id])
                                               :ent/approval-status (some (fn [x] (when (= :approval-status/draft (:db/ident x))
                                                                                    (select-keys x [:db/id])))
                                                                          (vals @approval-states))}])]
          [:br]
          [:br]])
       (if-not (and @items @users)
         [re-com/throbber]
         [data-table/data-table
          :table-id :business-trips
          :colls [["Osoba" #(some->> % :ent/user :db/id (get @users) :ent/title str)]
                  ["Od" :ent/from]
                  ["Do" :ent/to]
                  ["Kam" :business-trip/where]
                  ["Automobil" #(some->> % :ent/automobile :db/id (get @automobiles) :ent/title str)]
                  ["Poznámka" (comp cljc.util/shorten :ent/annotation)]
                  ["Schvalování" #(some->> % :ent/approval-status :db/id (get @approval-states) :ent/title str)]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :business-trip])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                          :href (str "#/sluzebni-cesta/" (:db/id row))]
                         (when ((:-rights @user) :business-trip/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/sluzebni-cesta/" (:db/id row) "e")])
                         (when ((:-rights @user) :business-trip/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :business-trip (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 1
          :desc? true
          :sum-format "%.1f"])])))

(defn page-business-trip []
  (let [edit? (re-frame/subscribe [:entity-edit? :business-trip])
        item (re-frame/subscribe [:entity-edit :business-trip])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Služební cesta"]
       (if (and @edit? ((:-rights @user) :business-trip/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :business-trips  #'page-business-trips)

(secretary/defroute "/sluzebni-cesty" []
  (re-frame/dispatch [:set-current-page :business-trips]))

(common/add-kw-url :business-trip "sluzebni-cesta")
(pages/add-page :business-trip #'page-business-trip)
(secretary/defroute #"/sluzebni-cesta/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :business-trip (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :business-trip]))
