(ns masyst.cljs.ebs-tree
  (:require [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.pages :as pages]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(re-frame/reg-sub-raw
 :ebs-tree ;;setridene polozky dle kodu (netreba pro data-table, ale vyuzito v dropdownech)
 (fn [db [_]]
   (let [unsorted (re-frame/subscribe [:ciselnik :ebs-tree])]
     (ratom/reaction (sort-by :ebs/code @unsorted)))))

(defn view []
  (let [items (re-frame/subscribe [:ciselnik :ebs-tree])
        item  (re-frame/subscribe [:ciselnik-new :ebs-tree])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        table-state (re-frame/subscribe [:table-state :ebs-tree])]
    (fn []
      (if-not (and @items @user)
        [re-com/throbber]
        (if (:ebs/code @item)
          [:div
           [:h5 (if (:db/id @item)"Upravit položku" "Přidat položku")]
           [re-com/label :label "Kód"]
           [re-com/input-text :model (str (:ebs/code @item))
            :on-change #(re-frame/dispatch [:ciselnik-change :ebs-tree :ebs/code %])]
           [re-com/label :label "Název"]
           [re-com/input-text
            :model (str (:ent/title @item))
            :width "600px"
            :on-change #(re-frame/dispatch [:ciselnik-change :ebs-tree :ent/title %])]
           [:br]
           [buttons/save-button (:-errors @item) [:ciselnik-save :ebs-tree] [:ciselnik-change :ebs-tree]]
           (when (:db/id @item)
             [re-com/button :label "Kopírovat" :on-click #(re-frame/dispatch [:ciselnik-change :ebs-tree :db/id nil])])
           [re-com/button :label "Seznam" :on-click #(re-frame/dispatch [:ciselnik-new :ebs-tree])]]
          [:div
           (when ((:-rights @user) :ebs-tree/save)
             [:div
              [re-com/button :label "Nová" :on-click #(re-frame/dispatch [:ciselnik-change :ebs-tree :ebs/code ""])]
              [:br]
              [:br]])
           [data-table
            :table-id :ebs-tree
            :colls [["Kód" :ebs/code]
                    ["Název" :ent/title]
                    [(if @offline?
                       ""
                       [re-com/md-icon-button
                        :md-icon-name "zmdi-refresh"
                        :tooltip "Načíst ze serveru"
                        :on-click #(re-frame/dispatch [:ciselnik-load :ebs-tree])])
                     (fn [row]
                       (when (= (:db/id row) (:selected-row-id @table-state))
                         [:div
                          (when ((:-rights @user) :ebs-tree/save)
                            [re-com/md-icon-button
                             :md-icon-name "zmdi-edit"
                             :tooltip "Editovat"
                             :on-click #(re-frame/dispatch [:ciselnik-edit :ebs-tree (:db/id row)])])
                          (when ((:-rights @user) :ebs-tree/delete)
                            [buttons/delete-button #(re-frame/dispatch [:ciselnik-delete :ebs-tree (:db/id row)])])]))
                     :none]]
            :rows items
            :order-by 0]])))))

(defn page-ebs-tree []
  [:div
   [:h3 "Energoblok Šternberk - Struktura"]
   [view]])

(pages/add-page :ebs-tree #'page-ebs-tree)

(secretary/defroute "/ebs-struktura" []
  (re-frame/dispatch [:set-current-page :ebs-tree]))
