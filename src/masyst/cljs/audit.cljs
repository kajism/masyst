(ns masyst.cljs.audit
  (:require [masyst.cljc.common :as cljc-common]
            [masyst.cljs.common :as cljs-common]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.comp.buttons :refer [save-button]]
            [masyst.cljs.pages :as pages]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn view []
  (let [items (re-frame/subscribe [:ciselnik :audit])
        users (re-frame/subscribe [:entities :user])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      (if-not (and @items @user)
        [re-com/throbber]
        [:div
         [data-table
          :table-id :audit
          :colls [["Datum" :db/txInstant]
                  ["Uživatel" (fn [row]
                                (let [u (get @users (:tx/uid row))]
                                  [:a {:href (str "#/uzivatel/" (:tx/uid row))}
                                   (str (:ent/title u) " (" (:user/login u) ")")]))]
                  ["Akce" (fn [row]
                            (let [{file-id :db/id orig-name :file/orig-name {:keys [:db/id :ent/type]} :file/parent :as file}
                                  (:file row)]
                              (if file
                                [:div
                                 [:a {:href (str "#/" (get @cljs-common/kw->url type) "/" id)}
                                  (cljc-common/kw->label type)]
                                 " "
                                 [:a {:href (str "/api/file/" file-id) :target "_blank"} orig-name]]
                                "Přihlášení")))]
                  [[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:ciselnik-load :audit])]
                   (fn [row] "")
                   :none]]
          :rows items
          :order-by 0
          :desc? true]]))))

(defn page-audit []
  [:div
   [:h3 "Audit"]
   [view]])

(pages/add-page :audit #'page-audit)

(secretary/defroute "/audit" []
  (re-frame/dispatch [:set-current-page :audit]))
