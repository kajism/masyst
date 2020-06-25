(ns masyst.cljs.ebs-construction-diary
  (:require [cljs.pprint :as pprint]
            [cljs-time.coerce :as tc]
            [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
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
            [masyst.cljs.comp.history :as history]))

(defn detail [item user suppliers]
  (let [ebs-tree-id (-> item :ebs/code-ref :db/id)
        supplier-id (get-in item [:ent/supplier :db/id])
        supplier (reduce (fn [_ x] (when (= (:db/id x) supplier-id) (reduced x))) suppliers)]
    [:div
     [re-com/h-box
      :size "auto"
      :children
      [[re-com/box
        :padding "0 40px 0 0"
        :child [:div
                [:label "Datum"]
                [:p (str (cljc.util/date-to-str (:ebs-constr-diary/date item)))]
                [:label "Datum do"]
                [:p (str (cljc.util/date-to-str (:ebs-constr-diary/date-to item)))]
                [:label "Dodavatel"]
                [:p (:ent/title supplier)]
                [:label "Struktura"]
                [:p (str (-> item :ebs/code-ref :ebs/code) " " (-> item :ebs/code-ref :ent/title))]
                [:label "Činnost"]
                [:p (str (:ent/title item))]]]
       [re-com/box
        :child [:div
                [:label "Poznámka"]
                (util/dangerousHTML (str/replace (str (:ent/annotation item)) #"\n" "<br />"))]]]]

     [:h3 "Fotografie"]
     [:div
      (doall
       (for [img (->> (:file/_parent item)
                      (filter #(= "image/" (subs (:file/content-type %) 0 6))))]
         ^{:key (:db/id img)}
         [:a {:href (str "/api/file/" (:db/id img))
              :data-lightbox "images-group"}
          [:img.ecd-img {:src (str "/api/file/" (:db/id img))}]]))]
     [:div.panel-group
      [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
     [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]
     [history/view user (:db/id item)]]))

(defn form [item user ebs-tree suppliers]
  [:div
   [re-com/h-box
    :children
    [[re-com/box
      :padding "10px"
      :child
      [:div
       [:label "Datum"]
       [:br]
       [re-com/datepicker-dropdown
        :model (tc/from-date (:ebs-constr-diary/date item))
        :on-change #(re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :ebs-constr-diary/date (tc/to-date %)])
        :show-today? true
        :format "dd.MM.yyyy"]
       [:br]
       [:label "Datum do"]
       [:br]
       [re-com/datepicker-dropdown
        :model (tc/from-date (:ebs-constr-diary/date-to item))
        :on-change #(re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :ebs-constr-diary/date-to (tc/to-date %)])
        :show-today? true
        :format "dd.MM.yyyy"]
       [:br]
       [:label "Dodavatel"]
       [:br]
       [re-com/single-dropdown
        :choices suppliers
        :id-fn :db/id
        :label-fn :ent/title
        :model (get-in item [:ent/supplier :db/id])
        :on-change #(re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :ent/supplier {:db/id %}])
        :placeholder "Vyberte dodavatele"
        :filter-box? true
        :width "400px"]
       [re-com/hyperlink-href
        :label [re-com/button :label "Dodavatelé"]
        :href (str "#/dodavatele")]
       [:br]
       [:label "Činnost"]
       [re-com/input-text
        :model (str (:ent/title item))
        :on-change #(re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :ent/title %])
        :width "400px"]
       [:label "Struktura"]
       [:br]
       [re-com/single-dropdown
        :choices ebs-tree
        :id-fn :db/id
        :label-fn #(str (:ebs/code %) " " (:ent/title %))
        :model (get-in item [:ebs/code-ref :db/id])
        :on-change #(re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :ebs/code-ref {:db/id %}])
        :placeholder "Začleňte dokument"
        :filter-box? true
        :width "400px"]
       [:br]]]
     [re-com/box
      :child [:div
              [:label "Poznámka"]
              [re-com/input-textarea
               :model (str (:ent/annotation item))
               :on-change #(re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :ent/annotation %])
               :width "400px"
               :height "300px"]]]]]
   [:label "Soubor"]
   [:input#file-upload
    {:type :file
     :on-change
     (fn [ev]
       (let [file (aget (-> ev .-target .-files) 0)]
         (.log js/console file)
         (re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :-file file])
         (when (empty? (:ent/title item))
           (re-frame/dispatch [:entity-change :ebs-construction-diary (:db/id item) :ent/title (.-name file)]))))}]
   [:br]
   [buttons/form-buttons :ebs-construction-diary item]
   [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]
   [history/view user (:db/id item)]])

(defn page-ebs-construction-diary-list []
  (let [items (re-frame/subscribe [:entities :ebs-construction-diary])
        user (re-frame/subscribe [:auth-user])
        suppliers (re-frame/subscribe [:entities-from-ciselnik :supplier])
        offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :ebs-construction-diary])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Pracovní deník"]
       (when ((:-rights @user) :ebs-construction-diary/save)
         [:div
          [re-com/hyperlink-href
           :label [re-com/button :label "Nový"]
           :href (str "#/ebs-pracovni-denik/e")]
          [:br]
          [:br]])
       (if-not (and @items @suppliers)
         [re-com/throbber]
         [data-table/data-table
          :table-id :ebs-construction-diary
          :colls (cond-> [["Datum" :ebs-constr-diary/date]
                          ["Datum do" :ebs-constr-diary/date-to]
                          ["Dodavatel" #(some->> % :ent/supplier :db/id (get @suppliers) :ent/title str)]
                          ["Struktura" #(str (-> % :ebs/code-ref :ebs/code) " " (-> % :ebs/code-ref :ent/title))]
                          ["Název" :ent/title]
                          ["Soubory" #(count (:file/_parent %))]]
                   true
                   (into [[(if @offline?
                             ""
                             [re-com/md-icon-button
                              :md-icon-name "zmdi-refresh"
                              :tooltip "Načíst ze serveru"
                              :on-click #(re-frame/dispatch [:entities-load :ebs-construction-diary])])
                           (fn [row]
                             (when (= (:db/id row) (:selected-row-id @table-state))
                               [re-com/h-box
                                :gap "5px"
                                :children
                                [[re-com/hyperlink-href
                                  :label [re-com/md-icon-button
                                          :md-icon-name "zmdi-view-web"
                                          :tooltip "Detail"]
                                  :href (str "#/ebs-pracovni-denik/" (:db/id row))]
                                 (when ((:-rights @user) :ebs-construction-diary/save)
                                   [re-com/hyperlink-href
                                    :label [re-com/md-icon-button
                                            :md-icon-name "zmdi-edit"
                                            :tooltip "Editovat"]
                                    :href (str "#/ebs-pracovni-denik/" (:db/id row) "e")])
                                 (when ((:-rights @user) :ebs-construction-diary/delete)
                                   [buttons/delete-button #(re-frame/dispatch [:entity-delete :ebs-construction-diary (:db/id row)])])]]))
                           :csv-export]]))
          :rows items
          :order-by 0
          :desc? true])])))

(defn page-ebs-construction-diary []
  (let [edit? (re-frame/subscribe [:entity-edit? :ebs-construction-diary])
        item (re-frame/subscribe [:entity-edit :ebs-construction-diary])
        ebs-tree (re-frame/subscribe [:ebs-tree])
        suppliers (re-frame/subscribe [:ciselnik :supplier])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Pracovní deník"]
       (if-not (and  @ebs-tree @suppliers)
         [re-com/throbber]
         (if (and @edit? ((:-rights @user) :ebs-construction-diary/save))
           [form @item @user @ebs-tree @suppliers]
           [detail @item @user @suppliers]))])))

(pages/add-page :ebs-construction-diary-list #'page-ebs-construction-diary-list)
(pages/add-page :ebs-construction-diary #'page-ebs-construction-diary)

(secretary/defroute "/ebs-pracovni-denik-seznam" []
  (re-frame/dispatch [:set-current-page :ebs-construction-diary-list]))

(secretary/defroute #"/ebs-pracovni-denik/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :ebs-construction-diary (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :ebs-construction-diary]))

(common/add-kw-url :ebs-construction-diary "ebs-pracovni-denik")

