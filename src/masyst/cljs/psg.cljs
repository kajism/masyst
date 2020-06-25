(ns masyst.cljs.psg
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
        table-state (re-frame/subscribe [:table-state :psgs])]
    (fn [items user]
      [:div
       (when ((:-rights user) :psg/save)
         [:div
          [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/psg/e")]
          [:br]
          [:br]])
       (if-not @items
         [re-com/throbber]
         [data-table/data-table
          :table-id :psgs
          :colls [["Název" :ent/title]
                  ["Poznámka" :ent/annotation]
                  ["Soubory" #(count (:file/_parent %))]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :psg])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [[re-com/hyperlink-href
                          :href (str "#/psg/" (:db/id row))
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]]
                         (when ((:-rights user) :psg/save)
                           [re-com/hyperlink-href
                            :href (str "#/psg/" (:db/id row) "e")
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]])
                         (when ((:-rights user) :psg/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :psg (:db/id row)])])]]))
                   :none]]
          :rows items
          :order-by 0])])))

(defn form [item]
  [:div
   [:label "Název"]
   [re-com/input-text
    :model (str (:ent/title item))
    :on-change #(re-frame/dispatch [:entity-change :psg (:db/id item) :ent/title %])
    :width "400px"]
   [:label "Poznámka"]
   [re-com/input-textarea
    :model (str (:ent/annotation item))
    :on-change #(re-frame/dispatch [:entity-change :psg (:db/id item) :ent/annotation %])
    :width "400px"]
   [:br]
   [:label "Soubor"]
   [:input#file-upload
    {:type :file
     :on-change
     (fn [ev]
       (let [file (aget (-> ev .-target .-files) 0)]
         (.log js/console file)
         (re-frame/dispatch [:entity-change :psg (:db/id item) :-file file])
         (when (empty? (:ent/title item))
           (re-frame/dispatch [:entity-change :psg (:db/id item) :ent/title (.-name file)]))))}]
   [:br]
   [buttons/form-buttons :psg item]
   [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]])

(defn detail [item]
  [:div
   [:label "Název"]
   [:p (str (:ent/title item)) [:br]]
   [:label "Poznámka"]
   [:p (str (:ent/annotation item)) [:br]]
   [:div.panel-group
    [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
   [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]])

(defn page-psgs []
  (let [items (re-frame/subscribe [:entities :psg])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "PSG"]
       (if-not @items
         [re-com/throbber]
         [table items @user])])))

(defn page-psg []
  (let [edit? (re-frame/subscribe [:entity-edit? :psg])
        item (re-frame/subscribe [:entity-edit :psg])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "PSG"]
       (if (and @edit? ((:-rights @user) :psg/save))
         [form @item]
         [detail @item])])))

(pages/add-page :psgs #'page-psgs)
(pages/add-page :psg #'page-psg)

(secretary/defroute "/psg" []
  (re-frame/dispatch [:set-current-page :psgs]))

(secretary/defroute #"/psg/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :psg (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :psg]))

(common/add-kw-url :psg "psg")
