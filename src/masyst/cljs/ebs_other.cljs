(ns masyst.cljs.ebs-other
  (:require [clojure.string :as str]
            [masyst.cljc.tools :as tools]
            [masyst.cljc.util :as cljc.util]
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

(defn table []
  (let [offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :ebs-others])]
    (fn [items categories user]
      [:div
       (when ((:-rights user) :ebs-other/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/ebs-ostatni/e")]
          [:br]
          [:br]])
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :ebs-others
          :colls [["Kategorie" #(:ent/title (tools/find-by-db-id categories (-> % :ebs/other-category :db/id)))]
                  ["Název" :ent/title]
                  ["Soubory" #(count (:file/_parent %))]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :ebs-other])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [[re-com/hyperlink-href
                          :href (str "#/ebs-ostatni/" (:db/id row))
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]]
                         (when ((:-rights user) :ebs-other/save)
                           [re-com/hyperlink-href
                            :href (str "#/ebs-ostatni/" (:db/id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights user) :ebs-other/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :ebs-other (:db/id row)])])]]))
                   :none]]
          :rows items
          :order-by 0])])))

(defn form [item categories]
  [:div
   [:label "Kategorie"]
   [:br]
   [re-com/single-dropdown
    :choices categories
    :id-fn :db/id
    :label-fn :ent/title
    :model (get-in item [:ebs/other-category :db/id])
    :on-change #(re-frame/dispatch [:entity-change :ebs-other (:db/id item) :ebs/other-category {:db/id %}])
    :placeholder "Začleňte dokument"
    :filter-box? true
    :width "400px"]
   [re-com/hyperlink-href :label [re-com/button :label "Ostatní kategorie"] :href (str "#/ebs-ostatni-kategorie")]
   [:br]
   [:label "Název"]
   [re-com/input-text
    :model (str (:ent/title item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-other (:db/id item) :ent/title %])
    :width "400px"]
   [:label "Poznámka"]
   [re-com/input-textarea
    :model (str (:ent/annotation item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-other (:db/id item) :ent/annotation %])
    :width "400px"]
   [:br]
   [:label "Soubor"]
   [:input#file-upload
    {:type :file
     :on-change
     (fn [ev]
       (let [file (aget (-> ev .-target .-files) 0)]
         (.log js/console file)
         (re-frame/dispatch [:entity-change :ebs-other (:db/id item) :-file file])
         (when (empty? (:ent/title item))
           (re-frame/dispatch [:entity-change :ebs-other (:db/id item) :ent/title (.-name file)]))))}]
   [:br]
   [buttons/form-buttons :ebs-other item]
   [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]])

(defn detail [item categories]
  [:div
   [:label "Kategorie"]
   [:p (:ent/title (tools/find-by-db-id categories (-> item :ebs/other-category :db/id)))]
   [:label "Název"]
   [:p (str (:ent/title item)) [:br]]
   [:label "Poznámka"]
   [:p (str (:ent/annotation item)) [:br]]
   [:div.panel-group
    [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
   [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]])

(defn page-ebs-others []
  (let [items (re-frame/subscribe [:entities :ebs-other])
        categories (re-frame/subscribe [:ciselnik :ebs-other-category])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Ostatní dokumentace"]
       (if-not @items
         [re-com/throbber]
         [table items @categories @user])])))

(defn page-ebs-other []
  (let [edit? (re-frame/subscribe [:entity-edit? :ebs-other])
        item (re-frame/subscribe [:entity-edit :ebs-other])
        categories (re-frame/subscribe [:ciselnik :ebs-other-category])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Dokument"]
       (if-not @categories
         [re-com/throbber]
         (if (and @edit? ((:-rights @user) :ebs-other/save))
           [form @item @categories]
           [detail @item @categories]))])))

(pages/add-page :ebs-others #'page-ebs-others)
(pages/add-page :ebs-other #'page-ebs-other)

(secretary/defroute "/ebs-ostatni" []
  (re-frame/dispatch [:set-current-page :ebs-others]))

(secretary/defroute #"/ebs-ostatni/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :ebs-other (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :ebs-other]))

(common/add-kw-url :ebs-other "ebs-ostatni")
