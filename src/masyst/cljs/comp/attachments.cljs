(ns masyst.cljs.comp.attachments
  (:require [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]))

(defn attachments []
  (let [user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        rows (reagent/atom nil)]
    (fn [files parent-ent-type parent-id edit?]
      (when (seq files)
        (reset! rows files)
        [data-table
         :colls [["Název"
                  (fn [row] [:a {:href (if @offline?
                                         (str "./uploads/" (name parent-ent-type) "/" (:db/id row) "-"
                                              (:file/server-name row))
                                         (str "/api/file/" (:db/id row)))
                                 :target "_blank"} (:file/orig-name row)])
                  :none]
                 ["Datum" :-created :none]
                 ["Velikost" #(util/file-size->str (:file/size %)) :none]
                 (when ((:-rights @user) (keyword (name parent-ent-type) "delete"))
                   ["Shlédnuto" #(some-> % :file/view-count (str " x")) :none])
                 [""
                  (fn [row]
                    [:div
                     (when (and edit? ((:-rights @user) (keyword (name parent-ent-type) "delete")))
                       [buttons/delete-button #(re-frame/dispatch
                                                [:file-delete parent-ent-type parent-id (:db/id row)])])])
                  :none]]
         :rows rows
         :order-by 1
         :desc? true]))))

(defn attachments-panel [files parent-ent-type parent-id edit?]
  (when (seq files)
    [:div.panel.panel-default
     [:div.panel-heading.panel-heading-custom
      [:h5.panel-title
       [:a {:data-toggle "collapse" :href "#collapse-files"} "Soubory"]]]
     [:div#collapse-files.panel-collapse.collapse.in
      [:div.panel-body
       [attachments files parent-ent-type parent-id edit?]]]]))
