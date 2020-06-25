(ns masyst.cljs.crm-subj
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
            [reagent.ratom :as ratom]
            [clojure.string :as str]
            [masyst.cljs.comp.history :as history]))

(re-frame/reg-sub-raw
 ::rows
 (fn [db [_]]
   (let [ents (re-frame/subscribe [:entities :crm-subj])
         page-state (re-frame/subscribe [:page-state :crm-subjs])]
     (ratom/reaction
      (cond->> (or (vals @ents) [])
        (:headquarters? @page-state)
        (remove :crm-subj/parent))))))


;; (defn detail [item user]f
;;   [re-com/v-box :gap "5px"
;;    :children
;;    [[re-com/h-box :gap "5px"
;;      :children
;;      [[re-com/box :width "300px"
;;        :child
;;        [:div
;;         [:label "Název firmy"]
;;         [:p (:ent/title item)]
;;         [:label "IČ"]
;;         [:p (:crm-subj/reg-no item)]
;;         [:label ""]
;;         [:p (:crm-subj/ item)]
;;         [:label ""]
;;         [:p (:crm-subj/ item)]
;;         [:label "Osoba"]
;;         [:p (:crm-subj/person)]
;;         [:label "Datum"]
;;         [:p (str (cljc.util/date-to-str (:crm-subj/date item)))]]]
;;       [:div
;;        [:label "Předmět"]
;;        [:p (str (:crm-subj/subject item))]
;;        [:label "Poznámka"]
;;        (util/dangerousHTML (str/replace (str (:ent/annotation item)) #"\n" "<br />"))]]]
;;     [:div.panel-group
;;      [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
;;     [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]
;;     [history/view user (:db/id item)]]])

(defn input-text [& {:keys [attr item items width]}]
  [re-com/input-text
   :model (str (or (get item attr) (some->> item :crm-subj/parent :db/id (get items) attr)))
   :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) attr %])
   :width width
   :disabled? (and (not (get item attr)) (some->> item :crm-subj/parent :db/id (get items) attr))
   :attr {:on-double-click #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) attr (-> % .-target .-value)])}])

(defn form [item user]
  (let [items (re-frame/subscribe [:entities :crm-subj])
        countries (re-frame/subscribe [:entities :country])]
    (fn [item user]
      (if-not @countries
        [re-com/throbber]
        [re-com/v-box :children
         [[:h3 "CRM Subjekt"]
          [re-com/h-box :gap "20px" :children
           [[re-com/v-box :width "400px" :children
             [[re-com/title
               :label [:div
                       "Firma "
                       (when (:crm-subj/parent item)
                         [re-com/hyperlink-href
                          :href (str "#/crm-subjekt/" (get-in item [:crm-subj/parent :db/id]))
                          :label [re-com/button :label "Sídlo" :class "btn-xs"]])]
               :underline? true?]
              [re-com/h-box :children
               [[re-com/label :label "Název" :width "50px"]
                [input-text :attr :ent/title :item item :items @items :width "350px"]]]
              [re-com/h-box :children
               [[re-com/label :label "IČ" :width "50px"]
                [input-text :attr :crm-subj/reg-no :item item :items @items :width "155px"]
                [re-com/box :justify :end :width "40px"
                 :child [re-com/label :label "DIČ"]]
                [input-text :attr :crm-subj/tax-no :item item :items @items :width "155px"]]]
              [re-com/h-box :children
               [[re-com/box :justify :end :width "245px"
                 :child [re-com/label :label "Číslo klienta"]]
                [re-com/input-text
                 :model (str (:crm-subj/cust-no item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/cust-no %])
                 :width "155px"]]]
              [re-com/title :label "Adresa" :underline? true?]
              [re-com/h-box :children
               [[re-com/label :label "Ulice" :width "50px"]
                [re-com/input-text
                 :model (str (:crm-subj/street item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/street %])
                 :width "350"]]]
              [re-com/h-box :children
               [[re-com/label :label "Č. o." :width "50px"]
                [re-com/input-text
                 :model (str (:crm-subj/house-no item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/house-no %])
                 :width "155px"]
                [re-com/box :justify :end :width "40px"
                 :child [re-com/label :label "Č. p."]]
                [re-com/input-text
                 :model (str (:crm-subj/land-reg-no item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/land-reg-no %])
                 :width "155px"]]]
              [re-com/h-box :children
               [[re-com/label :label "Město" :width "50px"]
                [re-com/input-text
                 :model (str (:crm-subj/zip-code item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/zip-code %])
                 :width "80"]
                [re-com/input-text
                 :model (str (:crm-subj/city item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/city %])
                 :width "270"]]]
              [re-com/h-box :children
               [[re-com/label :label "Stát" :width "50px"]
                [re-com/single-dropdown
                 :choices (->> (vals @countries)
                               (util/sort-by-locale :ent/title))
                 :id-fn :db/id
                 :label-fn :ent/title
                 :model (get-in item [:crm-subj/country :db/id])
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/country {:db/id %}])
                 :filter-box? true
                 :width "350"]]]
              [re-com/title :label "Kontakty" :underline? true?]
              [re-com/h-box :children
               [[re-com/label :label "Osoby" :width "50px"]
                [re-com/input-text
                 :model (str/join ", " (:crm-subj/person item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/person (str/split % #",[\s]*")])
                 :width "350"]]]
              [re-com/h-box :children
               [[re-com/label :label "Emaily" :width "50px"]
                [re-com/input-text
                 :model (str/join ", " (:crm-subj/email item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/email (str/split % #",[\s]*")])
                 :width "350"]]]
              [re-com/h-box :children
               [[re-com/label :label "Tel." :width "50px"]
                [re-com/input-text
                 :model (str/join ", " (:crm-subj/phone item))
                 :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :crm-subj/phone (str/split % #",[\s]*")])
                 :width "350"]]]
              [:br]
              [buttons/form-buttons :crm-subj item]]]
            [re-com/v-box :width "400px" :children
             [[re-com/title :label "Poznámky" :underline? true]
              [re-com/input-textarea
               :model (str (:ent/annotation item))
               :on-change #(re-frame/dispatch [:entity-change :crm-subj (:db/id item) :ent/annotation %])
               :width "400px"
               :rows 25
               :class "input-sm"]]]]]

          [history/view user (:db/id item)]]]))))

(defn page-crm-subjs []
  (let [items (re-frame/subscribe [:entities :crm-subj])
        rows (re-frame/subscribe [::rows])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :crm-subjs])
        page-state (re-frame/subscribe [:page-state :crm-subjs])]
    (fn []
      [:div
       [:h3 "CRM Subjekty"]
       [re-com/h-box :gap "20px" :align :center
        :children
        [(when ((:-rights @user) :crm-subj/save)
           [:div
            [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/crm-subjekt/e")]])
         [re-com/label :label "Zobrazení:"]
         [re-com/horizontal-bar-tabs
          :tabs [{:id nil :label "Vše"}
                 {:id true :label "Sídla"}]
          :model (:headquarters? @page-state)
          :on-change #(re-frame/dispatch [:page-state-change :crm-subjs :headquarters? %])]]]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :crm-subjs
          :colls [["Název" #(or (:ent/title %) (str (some->> % :crm-subj/parent :db/id (get @items) :ent/title) " *"))]
                  ["IČ" #(str (or (:crm-subj/reg-no %) (some->> % :crm-subj/parent :db/id (get @items) :crm-subj/reg-no)))]
                  ["Kl.číslo" #(str (or (:crm-subj/cust-no %) (some->> % :crm-subj/parent :db/id (get @items) :crm-subj/cust-no)))]
                  ["Ulice" :crm-subj/street]
                  ["Město" :crm-subj/city]
                  ["Osoby" #(str/join ", " (:crm-subj/person %))]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :crm-subj])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [#_[re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-view-web"
                                    :tooltip "Detail"]
                            :href (str "#/crm-subjekt/" (:db/id row))]
                         (when ((:-rights @user) :crm-subj/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/crm-subjekt/" (:db/id row) "e")])
                         (when ((:-rights @user) :crm-subj/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :crm-subj (:db/id row)])])]]))
                   :csv-export]]
          :rows rows
          :order-by 0])])))

(defn page-crm-subj []
  (let [edit? (re-frame/subscribe [:entity-edit? :crm-subj])
        item (re-frame/subscribe [:entity-edit :crm-subj])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (if-not @user
        [re-com/throbber]
        [form @item @user]
        #_(if (and @edit? ((:-rights @user) :crm-subj/save))
            [form @item @user]
            [detail @item @user])))))

(pages/add-page :crm-subjs  #'page-crm-subjs)
(secretary/defroute "/crm-subjekty" []
  (re-frame/dispatch [:set-current-page :crm-subjs]))

(common/add-kw-url :crm-subj "crm-subjekt")
(pages/add-page :crm-subj #'page-crm-subj)
(secretary/defroute #"/crm-subjekt/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :crm-subj (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :crm-subj]))
