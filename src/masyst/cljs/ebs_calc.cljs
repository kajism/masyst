(ns masyst.cljs.ebs-calc
  (:require [cljs.pprint :as pprint]
            [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :as attachments]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.comp.ebs-data-tables :as ebs-data-tables]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(re-frame/reg-event-db
 ::copy-selected-calcs
 util/debug-mw
 (fn [db [_]]
   (let [table-id :ebs-calcs
         ids (data-table/checked-ids db table-id)]
     (when (seq ids)
       (server-call [:ebs-calc/copy-to {:ids ids :target :ebs-offer}] [:entities-load :ebs-offer])
       (re-frame/dispatch [:table-state-change table-id :row-states {}])))
   db))

(defn price-rest [{:keys [:ebs-calc/price :ebs-calc/paid] :or {price 0 paid 0}}]
  (- price paid))

(defn price-paid-% [{:keys [:ebs-calc/price :ebs-calc/paid] :or {price 0 paid 0} :as ebs-calc}]
  (if (pos? price)
    (quot (* paid 100) price)
    0))

(defn form [item ebs-tree user]
  [:div
   [:label "Struktura"]
   [:br]
   [re-com/single-dropdown
    :choices ebs-tree
    :id-fn :db/id
    :label-fn #(str (:ebs/code %) " " (:ent/title %))
    :model (get-in item [:ebs/code-ref :db/id])
    :on-change #(re-frame/dispatch [:entity-change :ebs-calc (:db/id item) :ebs/code-ref {:db/id %}])
    :placeholder "Začleňte dokument"
    :filter-box? true
    :width "400px"]
   [:br]
   [:label "Název"]
   [re-com/input-text
    :model (str (:ent/title item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-calc (:db/id item) :ent/title %])
    :width "400px"]
   [:label "Cena Kč"]
   [re-com/input-text
    :model (util/money->text (:ebs-calc/price item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-calc (:db/id item) :ebs-calc/price (or (cljc.util/parse-int %) 0)])
    :change-on-blur? false
    :validation-regex #"^([\d\s]*)$"
    :width "400px"]
   (when ((:-rights user) :ebs-calc/paid)
     [:div
      [:label (str "Uhrazeno Kč " (price-paid-% item) "%")]
      [re-com/input-text
       :model (util/money->text (:ebs-calc/paid item))
       :on-change #(re-frame/dispatch [:entity-change :ebs-calc (:db/id item) :ebs-calc/paid (or (cljc.util/parse-int %) 0)])
       :change-on-blur? false
       :validation-regex #"^([\d\s]*)$"
       :width "400px"]
      [:label (str "Zbývá Kč " (- 100 (price-paid-% item)) "%")]
      [:p (util/money->text (price-rest item))]])
   [:label "Poznámka"]
   [re-com/input-textarea
    :model (str (:ent/annotation item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-calc (:db/id item) :ent/annotation %])
    :width "400px"]
   [:br]
   [:label "Soubor"]
   [:input#file-upload
    {:type :file
     :on-change
     (fn [ev]
       (let [file (aget (-> ev .-target .-files) 0)]
         (.log js/console file)
         (re-frame/dispatch [:entity-change :ebs-calc (:db/id item) :-file file])
         (when (empty? (:ent/title item))
           (re-frame/dispatch [:entity-change :ebs-calc (:db/id item) :ent/title (.-name file)]))))}]
   [:br]
   [buttons/form-buttons :ebs-calc item]
   [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]])

(defn detail [item user]
  (let [ebs-tree-id (-> item :ebs/code-ref :db/id)]
    [:div
     [:label "Struktura"]
     [:p (str (-> item :ebs/code-ref :ebs/code) " " (-> item :ebs/code-ref :ent/title))]
     [:label "Název"]
     [:p (str (:ent/title item)) [:br]]
     [:label "Cena"]
     [:p (util/money->text (:ebs-calc/price item)) " Kč" [:br]]
     (when ((:-rights user) :ebs-calc/paid)
       [:div
        [:label (str "Uhrazeno " (price-paid-% item) "%")]
        [:p (util/money->text (:ebs-calc/paid item)) " Kč" [:br]]
        [:label (str "Zbývá " (- 100 (price-paid-% item)) "%")]
        [:p (util/money->text (price-rest item)) " Kč"]])
     [:label "Poznámka"]
     [:p (str (:ent/annotation item)) [:br]]
     [:div.panel-group
      [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]
      [ebs-data-tables/offers-panel user ebs-tree-id]
      [ebs-data-tables/projects-panel user ebs-tree-id]]
     [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]]))

(defn page-ebs-calcs []
  (let [items (re-frame/subscribe [:entities :ebs-calc])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Rozpočty"]
       [:div
        (when ((:-rights @user) :ebs-calc/save)
          [:div
           [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/ebs-rozpocet/e")]
           [:br]
           [:br]])
        [ebs-data-tables/calcs items :filter ((:-rights @user) :ebs-project/save) ((:-rights @user) :ebs-project/delete) :ebs-calcs]
        #_(when (and ((:-rights @user) :ebs-calc/copy-to) ((:-rights @user) :ebs-offer/save))
          [:div
           [:br]
           [buttons/button-with-confirmation "Kopirovat označené do nabídek" "Opravdu checte zkopírovat označené rozpočty?"
            [::copy-selected-calcs]
            :above-right]])]])))

(pages/add-page :ebs-calcs #'page-ebs-calcs)
(secretary/defroute "/ebs-rozpocty" []
  (re-frame/dispatch [:set-current-page :ebs-calcs]))

(defn page-ebs-calc []
  (let [edit? (re-frame/subscribe [:entity-edit? :ebs-calc])
        item (re-frame/subscribe [:entity-edit :ebs-calc])
        ebs-tree (re-frame/subscribe [:ebs-tree])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Rozpočet"]
       (if-not @ebs-tree
         [re-com/throbber]
         (if (and @edit? ((:-rights @user) :ebs-calc/save))
           [form @item @ebs-tree @user]
           [detail @item @user]))])))

(common/add-kw-url :ebs-calc "ebs-rozpocet")
(pages/add-page :ebs-calc #'page-ebs-calc)
(secretary/defroute #"/ebs-rozpocet/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :ebs-calc (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :ebs-calc]))

(defn page-ebs-calc-prices []
  (let [items (re-frame/subscribe [:entities :ebs-calc])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :ebs-calc-prices])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Úhrady"]
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :ebs-calc-prices
          :colls (cond-> [["Struktura" #(str (-> % :ebs/code-ref :ebs/code) " " (-> % :ebs/code-ref :ent/title))]
                          ["Název" :ent/title]
                          ["Cena Kč" :ebs-calc/price :sum]]
                   ((:-rights @user) :ebs-calc/paid)
                   (into [["Uhrazeno Kč" :ebs-calc/paid :sum]
                          ["Zbývá Kč" #(price-rest %) :sum]])
                   true
                   (into [[(if @offline?
                             ""
                             [re-com/md-icon-button
                              :md-icon-name "zmdi-refresh"
                              :tooltip "Načíst ze serveru"
                              :on-click #(re-frame/dispatch [:entities-load :ebs-calc])])
                           (fn [row]
                             (when (= (:db/id row) (:selected-row-id @table-state))
                               [re-com/h-box
                                :gap "5px"
                                :children
                                [[re-com/hyperlink-href
                                  :label [re-com/md-icon-button
                                          :md-icon-name "zmdi-view-web"
                                          :tooltip "Detail"]
                                  :href (str "#/ebs-rozpocet/" (:db/id row))]
                                 (when ((:-rights @user) :ebs-calc/save)
                                   [re-com/hyperlink-href
                                    :label [re-com/md-icon-button
                                            :md-icon-name "zmdi-edit"
                                            :tooltip "Editovat"]
                                    :href (str "#/ebs-rozpocet/" (:db/id row) "e")])
                                 (when ((:-rights @user) :ebs-calc/delete)
                                   [buttons/delete-button #(re-frame/dispatch [:entity-delete :ebs-calc (:db/id row)])])]]))
                           :csv-export]]))
          :rows items
          :order-by 0])])))

(pages/add-page :ebs-calc-prices #'page-ebs-calc-prices)
(secretary/defroute "/ebs-uhrady" []
  (re-frame/dispatch [:set-current-page :ebs-calc-prices]))
