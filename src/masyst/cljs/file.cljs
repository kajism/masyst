(ns masyst.cljs.file
  (:require [clojure.string :as str]
            [masyst.cljc.common :as cljc.common]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]))

(defn table []
  (let [files (re-frame/subscribe [:entities :file])
        user (re-frame/subscribe [:auth-user])]
    (fn file-table-render []
      [:div
       [:h3 "Soubory"]
       (if (not @files)
         [re-com/throbber]
         [data-table
          :table-id :file
          :colls [["Název"
                   (fn [row] [:a {:href (str "/api/file/" (:db/id row))
                                  :target "_blank"} (:file/orig-name row)])]
                  #_["Datum" :-created]
                  ["Záznam" (fn [row]
                              (let [{{:keys [:db/id :ent/type]} :file/parent :as file} row]
                                [:div
                                 [:a {:href (str "#/" (get @common/kw->url type) "/" id)}
                                  (cljc.common/kw->label type)]]))]
                  ["Velikost" #(util/file-size->str (:file/size %))]
                  ["Shlédnuto" #(some-> % :file/view-count (str " x"))]
                  [[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:entities-load :file])]
                   (fn [row]
                     [:div
                      (when ((:-rights @user) (keyword (name (get-in row [:file/parent :ent/type])) "delete"))
                        [buttons/delete-button #(re-frame/dispatch
                                                 [:file-delete (get-in row [:file/parent :ent/type]) (get-in row [:file/parent :db/id]) (:db/id row)])])])
                   :none]]
          :rows files
          :order-by 0])])))

(pages/add-page ::table  #'table)

(secretary/defroute "/soubory" []
  (re-frame/dispatch [:set-current-page ::table]))

(secretary/defroute #"/soubor/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :file (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page ::form]))

(common/add-kw-url :file "soubor")

