(ns masyst.cljs.comp.ebs-data-tables
  (:require [masyst.cljs.common :as common]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]))

(defn projects []
  (let [offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :projects])]
    (fn [items header-modifier edit? delete?]
      (if-not @items
        [re-com/throbber]
        [data-table/data-table
         :table-id :projects
         :colls [["Struktura" #(str (-> % :ebs/code-ref :ebs/code) " " (-> % :ebs/code-ref :ent/title)) header-modifier]
                 ["Název" :ent/title header-modifier]
                 ["Soubory" #(count (:file/_parent %)) header-modifier]
                 [(if @offline?
                    ""
                    [re-com/md-icon-button
                     :md-icon-name "zmdi-refresh"
                     :tooltip "Načíst ze serveru"
                     :on-click #(re-frame/dispatch [:entities-load :ebs-project])])
                  (fn [row]
                    (when (= (:db/id row) (:selected-row-id @table-state))
                      [re-com/h-box
                       :gap "5px"
                       :children
                       [[re-com/hyperlink-href
                         :href (str "#/ebs-projekt/" (:db/id row))
                         :label [re-com/md-icon-button
                                 :md-icon-name "zmdi-view-web"
                                 :tooltip "Detail"]]
                        (when edit?
                          [re-com/hyperlink-href
                           :href (str "#/ebs-projekt/" (:db/id row) "e")
                           :label [re-com/md-icon-button
                                   :md-icon-name "zmdi-edit"
                                   :tooltip "Editovat"]])
                        (when delete?
                          [buttons/delete-button #(re-frame/dispatch [:entity-delete :ebs-project (:db/id row)])])]]))
                  :none]]
         :rows items
         :order-by 0]))))

(defn handovers []
  (let [offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :handovers])]
    (fn [items header-modifier edit? delete?]
      (if-not @items
        [re-com/throbber]
        [data-table/data-table
         :table-id :handovers
         :colls [["Struktura" #(str (-> % :ebs/code-ref :ebs/code) " " (-> % :ebs/code-ref :ent/title)) header-modifier]
                 ["Název" #(str (-> % :ebs/code-ref :ebs/code) " " (:ent/title %) ) header-modifier]
                 ["Poznámka" :ent/annotation]
                 ["Soubory" #(count (:file/_parent %)) header-modifier]
                 [(if @offline?
                    ""
                    [re-com/md-icon-button
                     :md-icon-name "zmdi-refresh"
                     :tooltip "Načíst ze serveru"
                     :on-click #(re-frame/dispatch [:entities-load :ebs-handover])])
                  (fn [row]
                    (when (= (:db/id row) (:selected-row-id @table-state))
                      [re-com/h-box
                       :gap "5px"
                       :children
                       [[re-com/hyperlink-href
                         :href (str "#/ebs-predavaci/" (:db/id row))
                         :label [re-com/md-icon-button
                                 :md-icon-name "zmdi-view-web"
                                 :tooltip "Detail"]]
                        (when edit?
                          [re-com/hyperlink-href
                           :href (str "#/ebs-predavaci/" (:db/id row) "e")
                           :label [re-com/md-icon-button
                                   :md-icon-name "zmdi-edit"
                                   :tooltip "Editovat"]])
                        (when delete?
                          [buttons/delete-button #(re-frame/dispatch [:entity-delete :ebs-handover (:db/id row)])])]]))
                  :none]]
         :rows items
         :order-by 1]))))

(defn projects-panel [{{right? :ebs-project/select} :-rights} ebs-tree-id]
  (let [items (re-frame/subscribe [:ebs-entities :ebs-project ebs-tree-id])]
    (when (and right? (seq @items))
      [:div.panel.panel-default
       [:div.panel-heading.panel-heading-custom
        [:h5.panel-title
         [:a {:data-toggle "collapse" :href "#collapse-projects"} "Projektová dokumentace"]]]
       [:div#collapse-projects.panel-collapse.collapse
        [:div.panel-body
         [projects items :none]]]])))

(defn calcs []
  (let [offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :calcs])]
    (fn [items header-modifier edit? delete?]
      (if-not @items
        [re-com/throbber]
        [data-table/data-table
         :table-id :calcs
         :colls [["Struktura" #(str (-> % :ebs/code-ref :ebs/code) " " (-> % :ebs/code-ref :ent/title)) header-modifier]
                 ["Název" :ent/title header-modifier]
                 ["Cena Kč" :ebs-calc/price :sum]
                 ["Soubory" #(count (:file/_parent %)) header-modifier]
                 [(if @offline?
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
                        (when edit?
                          [re-com/hyperlink-href
                           :label [re-com/md-icon-button
                                   :md-icon-name "zmdi-edit"
                                   :tooltip "Editovat"]
                           :href (str "#/ebs-rozpocet/" (:db/id row) "e")])
                        (when delete?
                          [buttons/delete-button #(re-frame/dispatch [:entity-delete :ebs-calc (:db/id row)])])]]))
                  :csv-export]]
         :rows items
         :row-checkboxes? false ;;(and ((:-rights user) :ebs-calc/copy-to) ((:-rights user) :ebs-offer/save))
         :order-by 0]))))

(defn calcs-panel [{{right? :ebs-calc/select} :-rights} ebs-tree-id]
  (let [items (re-frame/subscribe [:ebs-entities :ebs-calc ebs-tree-id])]
    (when (and right? (seq @items))
      [:div.panel.panel-default
       [:div.panel-heading.panel-heading-custom
        [:h5.panel-title
         [:a {:data-toggle "collapse" :href "#collapse-calcs"} "Rozpočty"]]]
       [:div#collapse-calcs.panel-collapse.collapse
        [:div.panel-body
         [calcs items :none]]]])))

(defn offers []
  (let [offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :offers])]
    (fn [items header-modifier edit? delete?]
      (if-not @items
        [re-com/throbber]
        [data-table/data-table
         :table-id :offers
         :colls [["Struktura" #(str (-> % :ebs/code-ref :ebs/code) " " (-> % :ebs/code-ref :ent/title)) header-modifier]
                 ["Název" :ent/title header-modifier]
                 ["Vítězná?" #(util/boolean->text (:ebs-offer/winner? %)) header-modifier]
                 ["Soubory" #(count (:file/_parent %)) header-modifier]
                 [(if @offline?
                    ""
                    [re-com/md-icon-button
                     :md-icon-name "zmdi-refresh"
                     :tooltip "Načíst ze serveru"
                     :on-click #(re-frame/dispatch [:entities-load :ebs-offer])])
                  (fn [row]
                    (when (= (:db/id row) (:selected-row-id @table-state))
                      [re-com/h-box
                       :gap "5px"
                       :children
                       [[re-com/hyperlink-href
                         :label [re-com/md-icon-button
                                 :md-icon-name "zmdi-view-web"
                                 :tooltip "Detail"]
                         :href (str "#/" (@common/kw->url (:ent/type row)) "/" (:db/id row))]
                        (when edit?
                          [re-com/hyperlink-href
                           :label [re-com/md-icon-button
                                   :md-icon-name "zmdi-edit"
                                   :tooltip "Editovat"]
                           :href (str "#/" (@common/kw->url (:ent/type row)) "/" (:db/id row) "e")])
                        (when delete?
                          [buttons/delete-button #(re-frame/dispatch [:entity-delete (:ent/type row) (:db/id row)])])]]))
                  :none]]
         :rows items
         :order-by 0]))))

(defn offers-panel [{{right? :ebs-calc/select} :-rights} ebs-tree-id]
  (let [items (re-frame/subscribe [:ebs-entities :ebs-offer ebs-tree-id])]
    (when (and right? (seq @items))
      [:div.panel.panel-default
       [:div.panel-heading.panel-heading-custom
        [:h5.panel-title
         [:a {:data-toggle "collapse" :href "#collapse-offers"} "Nabidky"]]]
       [:div#collapse-offers.panel-collapse.collapse
        [:div.panel-body
         [offers items :none]]]])))
