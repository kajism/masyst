(ns masyst.cljs.role
  (:require [clojure.string :as str]
            [masyst.cljc.common :as cljc.common]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as cljs-common]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]
            [reagent.core :as reagent]))

(defn table []
  (let [roles (re-frame/subscribe [:entities :role])
        role (re-frame/subscribe [:auth-user])]
    (fn role-list-render []
      (if (not @roles)
        [re-com/throbber]
        [:div
         (when ((:-rights @role) :role/save)
           [:div
            [re-com/button :label "Nová"
             :on-click #(re-frame/dispatch [:entity-new :role {}])]
            [:br]
            [:br]])
         [data-table
          :table-id :role
          :colls [["Jméno role" :ent/title]
                  ["Počet oprávnění"  #(count (:role/right %))]
                  ["Poznámka" :ent/annotation]
                  [[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:entities-load :role])]
                   (fn [row] [:div
                              (when ((:-rights @role) :role/save)
                                [re-com/hyperlink-href
                                 :href (str "#/role/" (:db/id row) "e")
                                 :label [re-com/md-icon-button
                                         :md-icon-name "zmdi-edit"
                                         :tooltip "Editovat"]])
                              (when ((:-rights @role) :role/delete)
                                [buttons/delete-button #(re-frame/dispatch [:entity-delete :role (:db/id row)])])])
                   :none]]
          :rows roles
          :order-by 0]]))))

(defn right-checkbox [ent-kw action role]
  (let [on-change-fn (fn [right value]
                       (re-frame/dispatch [:entity-change :role (:db/id role) :role/right
                                           (fn [rights]
                                             (let [rights (or rights #{})]
                                               (if value
                                                 (conj rights right)
                                                 (disj rights right))))]))
        right (keyword (name ent-kw) action)
        default-checkbox [re-com/checkbox :model (boolean (contains? (:role/right role) right))
                          :on-change (partial on-change-fn right)]]
    (condp (fn [test-expr expr] (test-expr right)) nil
      #{:audit/save :audit/delete}
      [:span "-"]
      #{:ebs-calc/select}
      [re-com/h-box :children [default-checkbox
                               ", úhrady: "
                               [re-com/checkbox :model (boolean (contains? (:role/right role) :ebs-calc/paid))
                                :on-change (partial on-change-fn :ebs-calc/paid)]]]
      #{:invoice/select}
      [re-com/h-box :children [default-checkbox
                               ", úhrady: "
                               [re-com/checkbox :model (boolean (contains? (:role/right role) :invoice/paid))
                                :on-change (partial on-change-fn :invoice/paid)]]]
      #{:invoice/save}
      [re-com/h-box :children [default-checkbox
                               ", import: "
                               [re-com/checkbox :model (boolean (contains? (:role/right role) :invoice/import))
                                :on-change (partial on-change-fn :invoice/import)]]]
      default-checkbox)))

(defn form []
  (let [role (re-frame/subscribe [:entity-edit :role])]
    (fn []
      (if-not @role
        [re-com/throbber]
        [re-com/v-box
         :children [[:label "Jméno role"]
                    [re-com/input-text :model (str (:ent/title @role))
                     :on-change #(re-frame/dispatch [:entity-change :role (:db/id @role) :ent/title %])]
                    [:label "Poznámka"]
                    [re-com/input-textarea :model (str (:ent/annotation @role))
                     :on-change #(re-frame/dispatch [:entity-change :role (:db/id @role) :ent/annotation %])]

                    [:label "Role"]
                    [data-table
                     :table-id :role-detail
                     :colls [["Oblast" cljc.common/kw->label]
                             ["Čtení"
                              (fn [ent-kw]
                                [right-checkbox ent-kw "select" @role])]
                             ["Editace"
                              (fn [ent-kw]
                                [right-checkbox ent-kw "save" @role])]
                             ["Smazání"
                              (fn [ent-kw]
                                [right-checkbox ent-kw "delete" @role])]]
                     :rows (reagent/atom (keys cljc.common/kw->label))]
                    [:br]
                    [buttons/form-buttons :role @role]]]))))

(defn page-roles []
  [:div
   [:h3 "Role"]
   [table]])

(defn page-role []
  [:div
   [:h3 "Role"]
   [form]])

(pages/add-page :role  #'page-role)
(pages/add-page :roles  #'page-roles)

(secretary/defroute "/role" []
  (re-frame/dispatch [:set-current-page :roles]))

(secretary/defroute #"/role/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :role (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :role]))

(cljs-common/add-kw-url :role "role")
