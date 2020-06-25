(ns masyst.cljs.contract
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

(defn price-rest [{:keys [:contract/price :contract/paid] :or {price 0 paid 0}}]
  (-> (util/bigdec->float price)
      (- (util/bigdec->float paid))
      (* 100)
      int
      (/ 100)))

(defn price-paid-% [{:keys [:contract/price :contract/paid] :or {price 0 paid 0} :as contract}]
  (if (pos? price)
    (quot (* (util/bigdec->float paid) 100) (util/bigdec->float price))
    0))

(defn detail [item user]
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
              [:label "Datum"]
              [:p (str (cljc.util/date-to-str (:ent/date item)))]
              [:label "Firma"]
              [:p (->> item :ent/supplier :db/id (get @suppliers) :ent/title)]
              [:label "Středisko"]
              [:p (->> item :ent/cost-center :db/id (get @cost-centers) :ent/title)]
              [:label "Šanon"]
              [:p (str (:ent/binder item))]]]
            [:div
             [:label "Předmět"]
             [:p (str (:contract/subject item))]
             [:label "Cena"]
             [:p (util/money->text (util/bigdec->float (:contract/price item))) " Kč"]
             (when ((:-rights user) :ebs-calc/paid)
               [:div
                [:label (str "Uhrazeno " (price-paid-% item) "%")]
                [:p (util/money->text (util/bigdec->float (:contract/paid item))) " Kč"]
                [:label (str "Zbývá " (- 100 (price-paid-% item)) "%")]
                [:p (util/money->text (price-rest item)) " Kč"]])
             [:label "Poznámka"]
             (util/dangerousHTML (str/replace (str (:ent/annotation item)) #"\n" "<br />"))]]]
          [:div.panel-group
           [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
          [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]
          [history/view user (:db/id item)]]]))))

(defn form [item user]
  (let [suppliers (re-frame/subscribe [:ciselnik :supplier])
        cost-centers (re-frame/subscribe [:ciselnik :cost-center])]
    (fn [item user]
      (if-not (and @cost-centers @suppliers)
        [re-com/throbber]
        [re-com/v-box :children
         [[:label "Datum"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:ent/date item))
           :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :ent/date (cljc.util/from-dMyyyy %)])
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"]
          [re-com/label :label "Firma"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices suppliers
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:ent/supplier :db/id])
             :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :ent/supplier {:db/id %}])
             :placeholder "Vyberte firmu"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Dodavatelé" :class "btn-sm"] :href (str "#/dodavatele")]]]
          [re-com/label :label "Středisko"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices cost-centers
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:ent/cost-center :db/id])
             :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :ent/cost-center {:db/id %}])
             :placeholder "Vyberte středisko"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Střediska" :class "btn-sm"] :href (str "#/strediska")]]]
          [:label "Předmět"]
          [re-com/input-text
           :model (str (:contract/subject item))
           :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :contract/subject %])
           :width "400px"]
          [:label "Cena Kč"]
          [re-com/input-text
           :model (util/money->text (util/bigdec->float (:contract/price item)))
           :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :contract/price
                                           (util/parse-bigdec (or % 0))])
           :change-on-blur? false
           :validation-regex #"^([\d\s\.\,-]*)$"
           :width "400px"]
          (when ((:-rights user) :ebs-calc/paid)
            [re-com/v-box :children
             [[re-com/h-box :gap "5px" :width "400px" :children
               [[re-com/label :label (str "Uhrazeno Kč " (price-paid-% item) "%") :width "200px"]
                [re-com/label :label (str "Zbývá Kč " (- 100 (price-paid-% item)) "%")]]]
              [re-com/h-box :gap "5px" :children
               [[re-com/input-text
                 :model (util/money->text (util/bigdec->float (:contract/paid item)))
                 :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :contract/paid
                                                 (util/parse-bigdec (or % 0))])
                 :change-on-blur? false
                 :validation-regex #"^([\d\s\.\,-]*)$"
                 :width "200px"]
                [:p (util/money->text (price-rest item))]]]]])
          [:label "Šanon"]
          [re-com/input-text
           :model (str (:ent/binder item))
           :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :ent/binder %])
           :width "400px"]
          [:label "Poznámka"]
          [re-com/input-textarea
           :model (str (:ent/annotation item))
           :on-change #(re-frame/dispatch [:entity-change :contract (:db/id item) :ent/annotation %])
           :width "400px"]
          [:label "Soubor"]
          [:input#file-upload
           {:type :file
            :on-change
            (fn [ev]
              (let [file (aget (-> ev .-target .-files) 0)]
                (.log js/console file)
                (re-frame/dispatch [:entity-change :contract (:db/id item) :-file file])
                (when (empty? (:ent/title item))
                  (re-frame/dispatch [:entity-change :contract (:db/id item) :ent/title (.-name file)]))))}]
          [:br]
          [buttons/form-buttons :contract item]
          [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]
          [history/view user (:db/id item)]]]))))

(defn page-contracts []
  (let [items (re-frame/subscribe [:entities :contract])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        suppliers (re-frame/subscribe [:entities-from-ciselnik :supplier])
        cost-centers (re-frame/subscribe [:entities-from-ciselnik :cost-center])
        table-state (re-frame/subscribe [:table-state :contracts])]
    (fn []
      [:div
       [:h3 "Smlouvy"]
       (when ((:-rights @user) :contract/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nová"] :href (str "#/smlouva/e")]
          [:br]
          [:br]])
       (if-not (and @items @suppliers @cost-centers)
         [re-com/throbber]
         [data-table/data-table
          :table-id :contracts
          :colls (cond-> [["Datum" :ent/date]
                          ["Firma" #(some->> % :ent/supplier :db/id (get @suppliers) :ent/title str)]
                          ["Středisko" #(some->> % :ent/cost-center :db/id (get @cost-centers) :ent/title str)]
                          ["Předmět" (comp cljc.util/shorten :contract/subject)]
                          ["Cena Kč" :contract/price :sum]]
                   ((:-rights @user) :contract/paid)
                   (into [["Zbývá" #(price-rest %) :sum]])
                   true
                   (into [["Poznámka" (comp cljc.util/shorten :ent/annotation)]
                          ["Šanon" :ent/binder]
                          ["Soubory" #(count (:file/_parent %))]
                          [(if @offline?
                             ""
                             [re-com/md-icon-button
                              :md-icon-name "zmdi-refresh"
                              :tooltip "Načíst ze serveru"
                              :on-click #(re-frame/dispatch [:entities-load :contract])])
                           (fn [row]
                             (when (= (:db/id row) (:selected-row-id @table-state))
                               [re-com/h-box
                                :gap "5px"
                                :children
                                [[re-com/hyperlink-href
                                  :label [re-com/md-icon-button
                                          :md-icon-name "zmdi-view-web"
                                          :tooltip "Detail"]
                                  :href (str "#/smlouva/" (:db/id row))]
                                 (when ((:-rights @user) :contract/save)
                                   [re-com/hyperlink-href
                                    :label [re-com/md-icon-button
                                            :md-icon-name "zmdi-edit"
                                            :tooltip "Editovat"]
                                    :href (str "#/smlouva/" (:db/id row) "e")])
                                 (when ((:-rights @user) :contract/delete)
                                   [buttons/delete-button #(re-frame/dispatch [:entity-delete :contract (:db/id row)])])]]))
                           :csv-export]]))
          :rows items
          :order-by 0
          :desc? true])])))

(defn page-contract []
  (let [edit? (re-frame/subscribe [:entity-edit? :contract])
        item (re-frame/subscribe [:entity-edit :contract])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Smlouva"]
       (if (and @edit? ((:-rights @user) :contract/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :contracts  #'page-contracts)
(secretary/defroute "/smlouvy" []
  (re-frame/dispatch [:set-current-page :contracts]))

(common/add-kw-url :contract "smlouva")
(pages/add-page :contract #'page-contract)
(secretary/defroute #"/smlouva/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :contract (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :contract]))
