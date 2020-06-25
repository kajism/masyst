(ns masyst.cljs.comp.buttons
  (:require [masyst.cljc.util :as cljc.util]
            [masyst.cljs.common :as common]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [clojure.string :as str]
            [taoensso.timbre :as timbre]))

(defn button-with-confirmation [label confirm-query yes-evt position]
  (let [showing? (reagent/atom false)]
    (fn []
      [re-com/popover-anchor-wrapper
       :showing? showing?
       :position position
       :anchor [re-com/button
                :label label
                :on-click #(reset! showing? true)]
       :popover [re-com/popover-content-wrapper
                 :on-cancel #(reset! showing? false)
                 :body [re-com/v-box
                        :gap "10px"
                        :children [confirm-query
                                   [re-com/h-box
                                    :gap "5px"
                                    :children [[re-com/button
                                                :label "Ano"
                                                :on-click #(do
                                                             (re-frame/dispatch yes-evt)
                                                             (reset! showing? false))]
                                               [re-com/button
                                                :label "Ne"
                                                :on-click #(reset! showing? false)]]]]]]])))

(defn save-button
  "Save button with display of validation errors"
  [errors save-evt cancel-evt]
  (let [errors (atom errors)] ;;atom, protoze to vyzaduji re-com/popover-*
    [re-com/popover-anchor-wrapper
     :showing? errors
     :position :below-left
     :anchor [re-com/button
              :label "Uložit"
              :class "btn-success"
              :on-click #(re-frame/dispatch save-evt)]
     :popover [re-com/popover-content-wrapper
               :title "Validační chyby"
               :on-cancel #(re-frame/dispatch (into cancel-evt [:-errors nil]))
               :body [re-com/alert-list
                      :on-close #()
                      :alerts (mapv
                               (fn [[attr msg]]
                                 {:id attr
                                  :alert-type :warning
                                  :body msg})
                               @errors)]]]))

(defn delete-confirmation [& {:keys [anchor on-delete]}]
  (let [showing? (reagent/atom false)
        hide! #(reset! showing? false)]
    (fn []
      [re-com/popover-anchor-wrapper
       :showing? showing?
       :position :below-left
       :anchor (into anchor [:on-click #(reset! showing? true)])
       :popover [re-com/popover-content-wrapper
                 :on-cancel hide!
                 :body [re-com/v-box
                        :gap "10px"
                        :children ["Opravdu smazat tuto položku?"
                                   [re-com/h-box
                                    :gap "5px"
                                    :children [[re-com/button
                                                :label "Ano"
                                                :on-click #(do
                                                             (on-delete)
                                                             (hide!))]
                                               [re-com/button
                                                :label "Ne"
                                                :on-click hide!]]]]]]])))

(defn delete-button
  "Delete icon button with confirmation"
  [on-delete]
  [delete-confirmation
   :anchor [re-com/md-icon-button
            :md-icon-name "zmdi-delete"
            :tooltip "Smazat"]
   :on-delete on-delete])

(defn form-buttons []
  (let [saved-msg (re-frame/subscribe [:msg :saved])
        user (re-frame/subscribe [:auth-user])
        approval-states (re-frame/subscribe [:entities :approval-status])]
    (fn [kw item &{:keys [saved-evt create-evt copy-button?] :or {copy-button? true}}]
      (let [approval-status-ident (some->> item :ent/approval-status :db/id (get @approval-states) :db/ident)]
        [:div
         [re-com/h-box
          :gap "5px"
          :children
          [[re-com/button :label "Uložit" :class "btn-success"
            :on-click #(do (re-frame/dispatch [:entity-save kw saved-evt])
                           (when-let [fu (.getElementById js/document "file-upload")]
                             (aset fu "value" "")))]
           (when (and (= approval-status-ident :approval-status/draft)
                      (= (:db/id @user) (get-in item [:ent/user :db/id])))
             [re-com/button :label "Předložit ke schválení" :class "btn-success"
              :on-click #(do
                           (re-frame/dispatch [:entity-change kw
                                               (:db/id item)
                                               :ent/approval-status
                                               (cljc.util/find-by-ident :approval-status/submitted (vals @approval-states))])
                           (re-frame/dispatch [:entity-save kw]))])
           (when (:db/id item)
             [re-com/hyperlink-href
              :href (str "#/" (get @common/kw->url kw) "/e")
              :label [re-com/button :label "Nový"
                      :on-click #(re-frame/dispatch (or create-evt [:entity-new kw {}]))]])
           (when (and (:db/id item) copy-button?)
             [re-com/hyperlink-href
              :href (str "#/" (get @common/kw->url kw) "/e")
              :label [re-com/button :label "Kopie"
                      :on-click #(re-frame/dispatch [:entity-new kw (dissoc item :db/id :file/_parent)])]])
           (when (and (:db/id item)
                      ((:-rights @user) (keyword (name kw) "delete")))
             [delete-confirmation
              :anchor [re-com/button :label "Smazat"]
              :on-delete #(do (re-frame/dispatch [:entity-delete kw (:db/id @(re-frame/subscribe [:entity-edit kw]))])
                              (-> js/window .-history .back))])
           [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]]]
         (when-not (str/blank? @saved-msg)
           [re-com/alert-box
            :alert-type :info
            :body @saved-msg
            :style {:position "absolute"}])]))))

(defn approval []
  (let [user (re-frame/subscribe [:auth-user])
        approval-states (re-frame/subscribe [:entities :approval-status])]
    (fn [item]
      (let [approval-status-ident (some->> item :ent/approval-status :db/id (get @approval-states) :db/ident)
            approver? (and (= approval-status-ident :approval-status/submitted)
                           (:-rights @user) :approval-status/save)]
        [re-com/v-box
         :gap "5px"
         :children
         [[re-com/h-box
           :gap "5px"
           :children
           [(when approver?
              [re-com/button :label "Schválit" :class "btn-success"
               :on-click #(do
                            (re-frame/dispatch [:entity-change (:ent/type item)
                                                (:db/id item)
                                                :ent/approval-status
                                                (cljc.util/find-by-ident :approval-status/approved (vals @approval-states))])
                            (re-frame/dispatch [:entity-save (:ent/type item)]))])
            (when approver?
              [re-com/button :label "Zamítnout" :class "btn-danger"
               :on-click #(do
                            (re-frame/dispatch [:entity-change (:ent/type item)
                                                (:db/id item)
                                                :ent/approval-status
                                                (cljc.util/find-by-ident :approval-status/rejected (vals @approval-states))])
                            (re-frame/dispatch [:entity-save (:ent/type item)]))])
            [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]]]
          (when approver?
            [re-com/h-box
             :gap "5px"
             :children
             [[re-com/label :label "Poznámka schvalovatele:"]
              [re-com/input-text
               :model (str (:ent/approval-note item))
               :on-change #(re-frame/dispatch [:entity-change (:ent/type item) (:db/id item) :ent/approval-note %])
               :width "400px"]]])]]))))
