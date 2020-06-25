(ns masyst.cljs.invoice
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

(re-frame/reg-event-db
 ::upload-csv-import
 util/debug-mw
 (fn [db [_ csv-file]]
   (server-call [:invoice/bulk-import {:type :csv-upload}]
                csv-file
                [::import-finished])
   db))

(re-frame/reg-event-db
 ::import-finished
 util/debug-mw
 (fn [db _]
   (re-frame/dispatch [:entities-load :invoice])
   (re-frame/dispatch [:ciselnik-load :cost-center])
   (re-frame/dispatch [:ciselnik-load :supplier])
   db))

(re-frame/reg-event-db
 ::attach-server-files
 util/debug-mw
 (fn [db [_ csv-file]]
   (server-call [:invoice/bulk-import {:type :server-files}]
                [::attach-server-files-finished])
   db))

(re-frame/reg-event-db
 ::attach-server-files-finished
 util/debug-mw
 (fn [db _]
   (re-frame/dispatch [:entities-load :invoice])
   (re-frame/dispatch [:ciselnik-load :supplier])
   db))

(defn price-rest [{:keys [:invoice/price :invoice/paid] :or {price 0 paid 0}}]
  (-> (util/bigdec->float price)
      (- (util/bigdec->float paid))
      (* 100)
      int
      (/ 100)))

(defn price-paid-% [{:keys [:invoice/price :invoice/paid] :or {price 0 paid 0} :as invoice}]
  (if (pos? price)
    (quot (* (util/bigdec->float paid) 100) (util/bigdec->float price))
    0))

(defn detail []
  (let [suppliers (re-frame/subscribe [:entities-from-ciselnik :supplier])
        cost-centers (re-frame/subscribe [:entities-from-ciselnik :cost-center])]
    (fn [item user]
      (if-not (and @cost-centers @suppliers)
        [re-com/throbber]
        [re-com/v-box :gap "5px"
         :children
         [[re-com/h-box :gap "5px"
           :children
           [[re-com/box :width "300px"
             :child
             [:div
              [:label "Číslo"]
              [:p (str (:ent/code item))]
              [:label "Variabilní symbol"]
              [:p (str (:invoice/variable-symbol item))]
              [:label "Datum faktury"]
              [:p (str (cljc.util/date-to-str (:ent/date item)))]
              [:label "Datum splatnosti"]
              [:p (str (cljc.util/date-to-str (:invoice/due-date item)))]
              [:label "Firma"]
              [:p (->> item :ent/supplier :db/id (get @suppliers) :ent/title)]
              [:label "Středisko"]
              [:p (->> item :ent/cost-center :db/id (get @cost-centers) :ent/title)]]]
            [:div
             [:label "Cena"]
             [:p (util/money->text (util/bigdec->float (:invoice/price item))) " Kč"]
             (when ((:-rights user) :ebs-calc/paid)
               [:div
                [:label (str "Uhrazeno " (price-paid-% item) "%")]
                [:p (util/money->text (util/bigdec->float (:invoice/paid item))) " Kč"]
                [:label (str "Zbývá " (- 100 (price-paid-% item)) "%")]
                [:p (util/money->text (price-rest item)) " Kč"]])
             [:label "Text na faktuře"]
             [:p (:invoice/text item)]
             [:label "Poznámka"]
             (util/dangerousHTML (str/replace (str (:ent/annotation item)) #"\n" "<br />"))
             [:label "Zkontrolováno?"]
             [:p (util/boolean->text (:invoice/checked item))]]]]
          [:div.panel-group
           [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
          [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]
          [history/view user (:db/id item)]]]))))

(defn form []
  (let [suppliers (re-frame/subscribe [:ciselnik :supplier])
        cost-centers (re-frame/subscribe [:ciselnik :cost-center])]
    (fn [item user]
      (if-not (and @cost-centers @suppliers)
        [re-com/throbber]
        [re-com/v-box :children
         [[:label "Číslo"]
          [re-com/input-text
           :model (str (:ent/code item))
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :ent/code (if (str/starts-with? % "Fa ")
                                                                                             %
                                                                                             (str "Fa " %))])
           :width "400px"]
          [:label "Variabilní symbol"]
          [re-com/input-text
           :model (str (:invoice/variable-symbol item))
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :invoice/variable-symbol (or (cljc.util/parse-int %) 0)])
           :validation-regex #"^(\d{0,10})$"
           :width "400px"]
          [:label "Datum faktury"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:ent/date item))
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :ent/date (cljc.util/from-dMyyyy %)])
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"]
          [:label "Datum splatnosti"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:invoice/due-date item))
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :invoice/due-date (cljc.util/from-dMyyyy %)])
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"]
          [:label "Firma"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices suppliers
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:ent/supplier :db/id])
             :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :ent/supplier {:db/id %}])
             :placeholder "Vyberte firmu"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Dodavatelé" :class "btn-sm"] :href (str "#/dodavatele")]]]
          [:label "Středisko"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices cost-centers
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:ent/cost-center :db/id])
             :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :ent/cost-center {:db/id %}])
             :placeholder "Vyberte středisko"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Střediska" :class "btn-sm"] :href (str "#/strediska")]]]
          [:label "Cena Kč"]
          [re-com/input-text
           :model (util/money->text (util/bigdec->float (:invoice/price item)))
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :invoice/price
                                           (util/parse-bigdec (or % 0))])
           :change-on-blur? false
           :validation-regex #"^([\d\s\.\,-]*)$"
           :width "400px"]
          (when ((:-rights user) :ebs-calc/paid)
            [re-com/v-box :children
             [[re-com/h-box :gap "5px" :width "400px" :children
               [[re-com/label :label (str "Uhrazeno Kč " (price-paid-% item) "%") :width "200px"]
                [re-com/label :label (str "Zbývá Kč " (- 100 (price-paid-% item)) "%")]]]
              [re-com/h-box :gap "5px" :width "400px" :children
               [[re-com/input-text
                 :model (util/money->text (util/bigdec->float (:invoice/paid item)))
                 :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :invoice/paid
                                                 (util/parse-bigdec (or % 0))])
                 :change-on-blur? false
                 :validation-regex #"^([\d\s\.\,-]*)$"
                 :width "200px"]
                [:p (util/money->text (price-rest item))]]]]])
          [:label "Text na faktuře"]
          [re-com/input-text
           :model (str (:invoice/text item))
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :invoice/text %])
           :width "400px"]
          [:label "Poznámka"]
          [re-com/input-textarea
           :model (str (:ent/annotation item))
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :ent/annotation %])
           :width "400px"]
          [:label "Zkontrolováno?"]
          [re-com/checkbox
           :model (:invoice/checked item)
           :on-change #(re-frame/dispatch [:entity-change :invoice (:db/id item) :invoice/checked %])]
          [:label "Soubor"]
          [:input#file-upload
           {:type :file
            :on-change
            (fn [ev]
              (let [file (aget (-> ev .-target .-files) 0)]
                (.log js/console file)
                (re-frame/dispatch [:entity-change :invoice (:db/id item) :-file file])
                (when (empty? (:ent/title item))
                  (re-frame/dispatch [:entity-change :invoice (:db/id item) :ent/title (.-name file)]))))}]
          [:br]
          [buttons/form-buttons :invoice item]
          [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]
          [history/view user (:db/id item)]]]))))

(defn page-invoices []
  (let [items (re-frame/subscribe [:entities :invoice])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        suppliers (re-frame/subscribe [:entities-from-ciselnik :supplier])
        cost-centers (re-frame/subscribe [:entities-from-ciselnik :cost-center])
        csv-file (reagent/atom nil)
        table-state (re-frame/subscribe [:table-state :invoices])]
    (fn []
      [:div
       [:h3 "Faktury"]
       (when ((:-rights @user) :invoice/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nová"] :href (str "#/faktura/e")]
          [:br]
          [:br]])
       (if-not (and @items @suppliers)
         [re-com/throbber]
         [data-table/data-table
          :table-id :invoices
          :colls (cond-> [["Číslo" :ent/code]
                          ["Varsym" :invoice/variable-symbol]
                          ["Datum" :ent/date]
                          ["Firma" #(some->> % :ent/supplier :db/id (get @suppliers) :ent/title str)]
                          ["Středisko" #(some->> % :ent/cost-center :db/id (get @cost-centers) :ent/title str)]
                          ["Cena Kč" :invoice/price :sum]]
                   ((:-rights @user) :invoice/paid)
                   (into [["Zbývá Kč" price-rest :sum]])
                   true
                   (into [["Soubory" #(count (:file/_parent %))]
                          ["Zkontrolováno?" :invoice/checked]
                          [(if @offline?
                             ""
                             [re-com/md-icon-button
                              :md-icon-name "zmdi-refresh"
                              :tooltip "Načíst ze serveru"
                              :on-click #(re-frame/dispatch [:entities-load :invoice])])
                           (fn [row]
                             (when (= (:db/id row) (:selected-row-id @table-state))
                               [re-com/h-box
                                :gap "5px"
                                :children
                                [[re-com/hyperlink-href
                                  :label [re-com/md-icon-button
                                          :md-icon-name "zmdi-view-web"
                                          :tooltip "Detail"]
                                  :href (str "#/faktura/" (:db/id row))]
                                 (when ((:-rights @user) :invoice/save)
                                   [re-com/hyperlink-href
                                    :label [re-com/md-icon-button
                                            :md-icon-name "zmdi-edit"
                                            :tooltip "Editovat"]
                                    :href (str "#/faktura/" (:db/id row) "e")])
                                 (when ((:-rights @user) :invoice/delete)
                                   [buttons/delete-button #(re-frame/dispatch [:entity-delete :invoice (:db/id row)])])]]))
                           :csv-export]]))
          :rows items
          :order-by 0
          :desc? true])
       (when ((:-rights @user) :invoice/bulk-import)
         [re-com/h-box :align :center :gap "10px"
          :children
          [[:label "Faktury CSV:"]
           [:input#csv-upload
            {:type :file
             :on-change
             (fn [ev]
               (let [file (aget (-> ev .-target .-files) 0)]
                 (.log js/console file)
                 (reset! csv-file file)))}]
           [re-com/button :label "Importovat CSV" :disabled? (not @csv-file)
            :on-click #(do
                         (re-frame/dispatch [::upload-csv-import @csv-file])
                         (reset! csv-file nil)
                         (-> js/document
                             (.getElementById "csv-upload")
                             (aset "value" "")))]
           [re-com/button :label "Přiřadit soubory na serveru"
            :on-click #(re-frame/dispatch [::attach-server-files])]]])])))

(defn page-invoice []
  (let [edit? (re-frame/subscribe [:entity-edit? :invoice])
        item (re-frame/subscribe [:entity-edit :invoice])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Faktura"]
       (if (and @edit? ((:-rights @user) :invoice/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :invoices  #'page-invoices)

(secretary/defroute "/faktury" []
  (re-frame/dispatch [:set-current-page :invoices]))

(common/add-kw-url :invoice "faktura")
(pages/add-page :invoice #'page-invoice)
(secretary/defroute #"/faktura/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :invoice (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :invoice]))
