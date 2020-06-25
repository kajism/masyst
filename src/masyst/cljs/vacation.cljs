(ns masyst.cljs.vacation
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

(defn- working-days-count [{:keys [:ent/from :ent/to :vacation/half-day?]} bank-holidays]
  (if (and from to)
    (-> (cljc.util/period-working-date-times
         (partial cljc.util/bank-holiday? bank-holidays)
         (tc/from-date from)
         (tc/from-date to))
        (count)
        (cond->
            half-day?
          (- 0.5)))
    0))

(re-frame/reg-event-db
 ::calculate-working-days
 util/debug-mw
 (fn [db [_]]
   (let [item (get-in db [:vacation (get-in db [:entity-edit :vacation :id])])
         bank-holidays (get-in db [:bank-holiday])]
     (when-let [wds (working-days-count item (vals bank-holidays))]
       (re-frame/dispatch [:entity-change :vacation (:db/id item) :vacation/working-days wds])))
   db))

(defn detail []
  (let [users (re-frame/subscribe [:entities :user])
        approval-states (re-frame/subscribe [:entities :approval-status])]
    (fn [item user]
      (if-not @users
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
              [:label "Půldenní?"]
              [:p (util/boolean->text (:vacation/half-day? item))]
              [:label "Od"]
              [:p (cljc.util/date-to-str (:ent/from item))]
              [:label "Do"]
              [:p (cljc.util/date-to-str (:ent/to item))]
              [:label "Kalendářních dnů"]
              [:p (cljc.util/calendar-days item)]
              [:label "Pracovních dnů"]
              [:p (:vacation/working-days item)]
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
        approval-states (re-frame/subscribe [:entities :approval-status])]
    (fn [item user]
      (if-not @users
        [re-com/throbber]
        [re-com/v-box :children
         [[:label "Osoba"]
          [:p (or (->> item :ent/user :db/id (get @users) :ent/title)
                  (:ent/title user))]
          [:label "Půldenní?"]
          [re-com/checkbox
           :model (:vacation/half-day? item)
           :on-change #(do
                         (re-frame/dispatch [:entity-change :vacation (:db/id item) :vacation/half-day? %])
                         (when %
                           (cond
                             (:ent/from item)
                             (re-frame/dispatch [:entity-change :vacation (:db/id item) :ent/to (:ent/from item)])
                             (:ent/to item)
                             (re-frame/dispatch [:entity-change :vacation (:db/id item) :ent/from (:ent/to item)])))
                         (re-frame/dispatch [::calculate-working-days]))]
          [:label "Od"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:ent/from item))
           :on-change #(let [new-date (cljc.util/from-dMyyyy %)]
                         (re-frame/dispatch [:entity-change :vacation (:db/id item) :ent/from new-date])
                         (when (:vacation/half-day? item)
                           (re-frame/dispatch [:entity-change :vacation (:db/id item) :ent/to new-date]))
                         (re-frame/dispatch [::calculate-working-days]))
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"]
          [:label "Do"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:ent/to item))
           :on-change #(do
                         (re-frame/dispatch [:entity-change :vacation (:db/id item) :ent/to (cljc.util/from-dMyyyy %)])
                         (re-frame/dispatch [::calculate-working-days]))
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"
           :disabled? (:vacation/half-day? item)]
          [:label "Kalendářních dnů"]
          [:p (cljc.util/calendar-days item)]
          [:label "Pracovních dnů"]
          [:p (str (:vacation/working-days item))]
          [:label "Poznámka"]
          [re-com/input-textarea
           :model (str (:ent/annotation item))
           :on-change #(re-frame/dispatch [:entity-change :vacation (:db/id item) :ent/annotation %])
           :width "400px"]
          [:label "Schvalování"]
          [:p (->> item :ent/approval-status :db/id (get @approval-states) :ent/title)]
          [:br]
          [buttons/form-buttons :vacation item]
          [history/view user (:db/id item)]]]))))

(defn page-vacations []
  (let [items (re-frame/subscribe [:entities :vacation])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        users (re-frame/subscribe [:entities-from-ciselnik :user])
        approval-states (re-frame/subscribe [:entities :approval-status])
        table-state (re-frame/subscribe [:table-state :vacations])]
    (fn []
      [:div
       [:h3 "Dovolené"]
       (when ((:-rights @user) :vacation/save)
         [:div
          [re-com/button :label "Nová"
           :on-click #(re-frame/dispatch
                       [:entity-new :vacation {:ent/user (select-keys @user [:db/id])
                                               :ent/approval-status (cljc.util/first-by-ident :approval-status/draft approval-states)}])]
          [:br]
          [:br]])
       (if-not (and @items @users)
         [re-com/throbber]
         [data-table/data-table
          :table-id :vacations
          :colls [["Osoba" #(some->> % :ent/user :db/id (get @users) :ent/title str)]
                  ["Od" :ent/from]
                  ["Do" :ent/to]
                  ["Pracovních dnů" :vacation/working-days :sum]
                  ["Poznámka" (comp cljc.util/shorten :ent/annotation)]
                  ["Schvalování" #(some->> % :ent/approval-status :db/id (get @approval-states) :ent/title str)]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :vacation])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                          :href (str "#/dovolena/" (:db/id row))]
                         (when ((:-rights @user) :vacation/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/dovolena/" (:db/id row) "e")])
                         (when ((:-rights @user) :vacation/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :vacation (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 1
          :desc? true
          :sum-format "%.1f"])])))

(defn page-vacation []
  (let [edit? (re-frame/subscribe [:entity-edit? :vacation])
        item (re-frame/subscribe [:entity-edit :vacation])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Dovolená"]
       (if (and @edit? ((:-rights @user) :vacation/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :vacations  #'page-vacations)

(secretary/defroute "/dovolene" []
  (re-frame/dispatch [:set-current-page :vacations]))

(common/add-kw-url :vacation "dovolena")
(pages/add-page :vacation #'page-vacation)
(secretary/defroute #"/dovolena/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :vacation (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :vacation]))
