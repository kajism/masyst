(ns masyst.cljs.user
  (:require [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]))

(defn form []
  (let [user (re-frame/subscribe [:entity-edit :user])]
    (fn user-form-render []
      (let [item @user]
        [:div
         [:h3 "Uživatel"]
         [:label "Jméno a příjmení"]
         [re-com/input-text :model (str (:ent/title item))
          :on-change #(re-frame/dispatch [:entity-change :user (:db/id item) :ent/title %])]
         [:label "Email"]
         [re-com/input-text :model (str (:user/email item))
          :on-change #(re-frame/dispatch [:entity-change :user (:db/id item) :user/email %])]
         [:label "Uživatelské jméno"]
         [re-com/input-text :model (str (:user/login item))
          :on-change #(re-frame/dispatch [:entity-change :user (:db/id item) :user/login %])]
         [:label "Heslo"]
         [re-com/input-text :model (str (:user/passwd item))
          :on-change #(re-frame/dispatch [:entity-change :user (:db/id item) :user/passwd %])]
         [:label "Role"]
         [re-com/input-text :model (str (:user/roles item))
          :on-change #(re-frame/dispatch [:entity-change :user (:db/id item) :user/roles %])]
         [:label "Denní úvazek"]
         [re-com/input-text
          :model (util/float--text (:time-sheet/daily-hours item))
          :on-change #(re-frame/dispatch [:entity-change :user (:db/id item) :time-sheet/daily-hours (cljc.util/parse-float %)])
          :validation-regex #"^(\d{0,2},?\d{0,2})$"]
         [:br]
         [buttons/form-buttons :user item]]))))

(defn table []
  (let [users (re-frame/subscribe [:entities :user])
        user (re-frame/subscribe [:auth-user])]
    (fn user-table-render []
      [:div
       [:h3 "Uživatelé"]
       (if (not @users)
         [re-com/throbber]
         [:div
          (when ((:-rights @user) :user/save)
            [:div
             [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/uzivatel/")]])
          [data-table
           :table-id :user
           :colls [["Jméno a příjmení" :ent/title]
                   ["Email" :user/email]
                   ["Uživatelské jméno" :user/login]
                   ["Počet přihlášení" :user/login-count]
                   ["Role" :user/roles]
                   ["Denní úvazek" :time-sheet/daily-hours]
                   [[re-com/md-icon-button
                     :md-icon-name "zmdi-refresh"
                     :tooltip "Načíst ze serveru"
                     :on-click #(re-frame/dispatch [:entities-load :user])]
                    (fn [row] [re-com/h-box :gap "5px"
                               :children
                               [(when ((:-rights @user) :user/save)
                                  [re-com/hyperlink-href
                                   :label [re-com/md-icon-button
                                           :md-icon-name "zmdi-edit"
                                           :tooltip "Editovat"]
                                   :href (str "#/uzivatel/" (:db/id row) "e")])
                                (when ((:-rights @user) :user/delete)
                                  [buttons/delete-button #(re-frame/dispatch [:entity-delete :user (:db/id row)])])]])
                    :none]]
           :rows users
           :order-by 0]])])))

(pages/add-page ::form  #'form)
(pages/add-page ::table  #'table)

(secretary/defroute "/uzivatele" []
  (re-frame/dispatch [:set-current-page ::table]))

(secretary/defroute #"/uzivatel/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :user (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page ::form]))

(common/add-kw-url :user "uzivatel")

