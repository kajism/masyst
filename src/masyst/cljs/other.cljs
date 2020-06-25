(ns masyst.cljs.other
  (:require [clojure.string :as str]
            [masyst.cljc.tools :as tools]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :as attachments]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn table [items user]
  (let [offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :others])
        suppliers (re-frame/subscribe [:entities-from-ciselnik :supplier])
        cost-centers (re-frame/subscribe [:entities-from-ciselnik :cost-center])
        categories (re-frame/subscribe [:entities-from-ciselnik :ebs-other-category])]
    (fn [items user]
      [:div
       (when ((:-rights user) :other/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/ostatni/e")]
          [:br]
          [:br]])
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :others
          :colls [["Datum" :ent/date]
                  ["Firma" #(some->> % :ent/supplier :db/id (get @suppliers) :ent/title str)]
                  ["Středisko" #(some->> % :ent/cost-center :db/id (get @cost-centers) :ent/title str)]
                  ["Kategorie" #(some->> % :ebs/other-category :db/id (get @categories) :ent/title str)]
                  ["Název" (comp cljc.util/shorten :ent/title)]
                  ["Označení / číslo" :ent/code]
                  ["Poznámka" (comp cljc.util/shorten :ent/annotation)]
                  ["Šanon" :ent/binder]
                  ["Soubory" #(count (:file/_parent %))]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :other])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [[re-com/hyperlink-href
                          :href (str "#/ostatni/" (:db/id row))
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]]
                         (when ((:-rights user) :other/save)
                           [re-com/hyperlink-href
                            :href (str "#/ostatni/" (:db/id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights user) :other/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :other (:db/id row)])])]]))
                   :none]]
          :rows items
          :order-by 0
          :desc? true])])))

(defn form [item user]
  (let [suppliers (re-frame/subscribe [:ciselnik :supplier])
        cost-centers (re-frame/subscribe [:ciselnik :cost-center])
        categories(re-frame/subscribe [:ciselnik :ebs-other-category])]
    (if-not (and @cost-centers @suppliers @categories)
      [re-com/throbber]
      [re-com/v-box :children
       [[:label "Datum"]
        [re-com/input-text
         :model (cljc.util/date-to-str (:ent/date item))
         :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ent/date (cljc.util/from-dMyyyy %)])
         :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
         :width "100px"]
        [re-com/label :label "Firma"]
        [re-com/h-box :gap "5px" :children
         [[re-com/single-dropdown
           :choices suppliers
           :id-fn :db/id
           :label-fn :ent/title
           :model (get-in item [:ent/supplier :db/id])
           :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ent/supplier {:db/id %}])
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
           :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ent/cost-center {:db/id %}])
           :placeholder "Vyberte středisko"
           :filter-box? true
           :width "400px"]
          [re-com/hyperlink-href :label [re-com/button :label "Střediska" :class "btn-sm"] :href (str "#/strediska")]]]
        [:label "Kategorie"]
        [re-com/h-box :gap "5px" :children
         [[re-com/single-dropdown
           :choices categories
           :id-fn :db/id
           :label-fn :ent/title
           :model (get-in item [:ebs/other-category :db/id])
           :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ebs/other-category {:db/id %}])
           :placeholder "Začleňte dokument"
           :filter-box? true
           :width "400px"]
          [re-com/hyperlink-href :label [re-com/button :label "Ostatní kategorie" :class "btn-sm"] :href (str "#/ebs-ostatni-kategorie")]]]
        [:label "Název"]
        [re-com/input-text
         :model (str (:ent/title item))
         :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ent/title %])
         :width "400px"]
        [:label "Označení / číslo"]
        [re-com/input-text
         :model (str (:ent/code item))
         :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ent/code %])
         :width "400px"]
        [:label "Šanon"]
        [re-com/input-text
         :model (str (:ent/binder item))
         :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ent/binder %])
         :width "400px"]
        [:label "Poznámka"]
        [re-com/input-textarea
         :model (str (:ent/annotation item))
         :on-change #(re-frame/dispatch [:entity-change :other (:db/id item) :ent/annotation %])
         :width "400px"]
        [:label "Soubor"]
        [:input#file-upload
         {:type :file
          :on-change
          (fn [ev]
            (let [file (aget (-> ev .-target .-files) 0)]
              (.log js/console file)
              (re-frame/dispatch [:entity-change :other (:db/id item) :-file file])
              (when (empty? (:ent/title item))
                (re-frame/dispatch [:entity-change :other (:db/id item) :ent/title (.-name file)]))))}]
        [:br]
        [buttons/form-buttons :other (dissoc item :ent/code)]
        [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]]])))

(defn detail [item user]
  (let [suppliers (re-frame/subscribe [:entities-from-ciselnik :supplier])
        cost-centers (re-frame/subscribe [:entities-from-ciselnik :cost-center])
        categories(re-frame/subscribe [:entities-from-ciselnik :ebs-other-category])]
    (fn [item user]
      (if-not (and @cost-centers @suppliers @categories)
        [re-com/throbber]
        [:div
         [:label "Datum"]
         [:p (str (cljc.util/date-to-str (:ent/date item)))]
         [:label "Firma"]
         [:p (->> item :ent/supplier :db/id (get @suppliers) :ent/title)]
         [:label "Středisko"]
         [:p (->> item :ent/cost-center :db/id (get @cost-centers) :ent/title)]
         [:label "Kategorie"]
         [:p (:ent/title (tools/find-by-db-id @categories (-> item :ebs/other-category :db/id)))]
         [:label "Název"]
         [:p (str (:ent/title item)) [:br]]
         [:label "Označení / číslo"]
         [:p (str (:ent/code item))]
         [:label "Šanon"]
         [:p (str (:ent/binder item))]
         [:label "Poznámka"]
         [:p (str (:ent/annotation item)) [:br]]
         [:div.panel-group
          [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
         [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]]))))

(defn page-others []
  (let [items (re-frame/subscribe [:entities :other])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Ostatní dokumentace"]
       (if-not @items
         [re-com/throbber]
         [table items @user])])))

(defn page-other []
  (let [edit? (re-frame/subscribe [:entity-edit? :other])
        item (re-frame/subscribe [:entity-edit :other])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Ostatní dokument"]
       (if (and @edit? ((:-rights @user) :other/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :others #'page-others)
(pages/add-page :other #'page-other)

(secretary/defroute "/ostatni" []
  (re-frame/dispatch [:set-current-page :others]))

(secretary/defroute #"/ostatni/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :other (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :other]))

(common/add-kw-url :other "ostatni")
