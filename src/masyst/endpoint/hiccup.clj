(ns masyst.endpoint.hiccup
  (:require [clojure.pprint :refer [pprint]]
            [hiccup.page :as hiccup]
            [ring.util.anti-forgery :as anti-forgery]
            [ring.util.response :as response]
            [masyst.cljc.util :as cljc.util]))

(defn hiccup-response
  [body]
  (-> (hiccup/html5 {:lang "cs"}
                    body)
      response/response
      (response/content-type "text/html")
      (response/charset "utf-8")))

(defn hiccup-pprint
  [data]
  [:pre (with-out-str (pprint data))])

(defn- hiccup-frame [body]
  (list
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title "Masyst"]
    [:link {:rel "stylesheet" :href "assets/css/bootstrap.css"}]
    #_[:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css" :crossorigin "anonymous"}]
    [:link {:rel "stylesheet" :href "assets/css/material-design-iconic-font.min.css"}]
    [:link {:rel "stylesheet" :href "assets/css/re-com.css"}]
    [:link {:rel "stylesheet" :href "css/site.css"}]
    [:link {:href "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700,400italic"
            :rel "stylesheet" :type "text/css"}]
    [:link {:href "https://fonts.googleapis.com/css?family=Roboto+Condensed:400 ,300"
            :rel "stylesheet" :type "text/css"}]
    [:link {:rel "stylesheet" :href "css/lightbox.css"}]]
   [:body
    body
    [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.12.0/jquery.min.js"}]
    [:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js"}]
    [:script {:src "/js/lightbox.js"}]]))

(defn- pasman-hiccup-frame [body]
  (list
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:title "Pašmán"]
    [:link {:rel "stylesheet" :href "assets/css/bootstrap.css"}]
    #_[:link {:rel "stylesheet" :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.5/css/bootstrap.min.css" :crossorigin "anonymous"}]
    [:link {:rel "stylesheet" :href "css/pasman.css"}]]
   [:link {:rel "stylesheet" :href "css/lightbox.css"}]
   [:body {:style "background-color: lightblue"}
    body
    #_[:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.12.0/jquery.min.js"}]
    #_[:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js"}]]))

(defn- login-form [title msg]
  [:div.container.login
   [:h3 title]
   [:p "Pro přihlášení zadejte své přihlašovací údaje"]
   (when msg
     [:div.alert.alert-danger msg])
   [:form.form-inline {:method "post"}
    [:div.form-group
     [:label {:for "user-name" :style "margin-right: 3px;"} "Uživatelské jméno"]
     [:input#user-name.form-control {:name "user-name" :type "text"}]]
    [:div.form-group
     [:label {:for "heslo" :style "margin-left: 5px; margin-right: 3px;"} "Heslo"]
     [:input#heslo.form-control {:name "pwd" :type "password"}]]
    (anti-forgery/anti-forgery-field)
    #_[:input {:type "image" :src "img/login.svg"}]
    [:button.btn.btn-default {:type "submit"}
     #_[:span.glyphicon.glyphicon-log-in] " Přihlásit"]]])

(defn- pasman-login-form [msg]
  [:div.container.login
   [:h4 "Pašmán - Registrace pro Pobytovovou taxu / Accomodation tax registration"]
   (when msg
     [:div.alert.alert-danger msg])
   [:form {:method "post"}
    [:div.form-group
     [:label {:for "user-name" :style "margin-right: 3px;"} "Uživatelské jméno / Username"]
     [:input#user-name.form-control {:name "user-name" :type "text"}]]
    [:div.form-group
     [:label {:for "heslo" :style "margin-left: 5px; margin-right: 3px;"} "Heslo / Password"]
     [:input#heslo.form-control {:name "pwd" :type "password"}]]
    (anti-forgery/anti-forgery-field)
    #_[:input {:type "image" :src "img/login.svg"}]
    [:button.btn.btn-default {:type "submit"}
     #_[:span.glyphicon.glyphicon-log-in] " Přihlásit / Login"]]])

(defn login-page [pasman? msg]
  (hiccup-response
   (if pasman?
     (pasman-hiccup-frame
      (pasman-login-form  msg))
     (hiccup-frame
      (login-form "Vítejte v informačním systému MASYST" msg)))))

(defn cljs-landing-page []
  (hiccup-response
   (hiccup-frame
    [:div
     [:div#app "Načítám Masyst ..."]
     (anti-forgery/anti-forgery-field)
     [:script {:src "js/main.js"}]])))

(defn pasman-reg-page [group-items rights item errors]
  (let [error-alert (fn [k]
                      (when (get errors k)
                        [:div.alert.alert-danger (get errors k)]))
        date-str (fn [x]
                   (if (inst? x)
                     (cljc.util/date-to-str x)
                     x))]
    (hiccup-response
     (pasman-hiccup-frame
      [:div.container
       [:h3 "Pašmán - Registrace pro Pobytovovou taxu / Accomodation tax registration"]
       (when (contains? rights :pasman/save)
         (let [last-item (->> group-items
                              (sort-by :db/id)
                              (reverse)
                              (first))]
           [:form.form-horizontal {:method "post" :action "/pasman-reg-save"}
            (error-alert :ent/from)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "date-from"} "Začátek pobytu / From"]
             [:div.col-sm-10
              [:input#date-from.form-control {:name "date-from" :type "text" :placeholder "dd.mm"
                                              :value (date-str (or (:ent/from item)
                                                                   (:ent/from last-item)))}]]]
            (error-alert :ent/to)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "date-to"} "Konec pobytu / To"]
             [:div.col-sm-10
              [:input#date-from.form-control {:name "date-to" :type "text" :placeholder "dd.mm"
                                              :value (date-str (or (:ent/to item)
                                                                   (:ent/to last-item)))}]]]
            (error-alert :pasman/cottage-no)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "cottage-no"} "Chata / Cottage"]
             [:div.col-sm-10
              [:select#cottage-no.form-control {:name "cottage-no"}
               (conj
                (for [cottage (vals cljc.util/pasman-cottages)]
                  [:option (merge {:value (:id cottage)}
                                  (when (= (:id cottage) (or (:pasman/cottage-no item)
                                                             (:pasman/cottage-no last-item)))
                                    {:selected true}))
                   (:label cottage)])
                [:option {:value ""} "Vyberte chatu"])]]]
            (error-alert :pasman/last-name)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "last-name"} "Příjmení / Last name"]
             [:div.col-sm-10
              [:input#date-from.form-control {:name "last-name" :type "text"
                                              :value (:pasman/last-name item)}]]]
            (error-alert :pasman/first-name)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "first-name"} "Jméno / First name"]
             [:div.col-sm-10
              [:input#date-from.form-control {:name "first-name" :type "text"
                                              :value (:pasman/first-name item)}]]]
            (error-alert :pasman/passport-no)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "passport-no"} "Číslo dokladu / ID no"]
             [:div.col-sm-10
              [:input#date-from.form-control {:name "passport-no" :type "text"
                                              :value (:pasman/passport-no item)}]]]
            (error-alert :pasman/age)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "age"} "Věk / Age"]
             [:div.col-sm-10
              [:input#date-from.form-control {:name "age" :type "text"
                                              :value (:pasman/age item)}]]]
            (error-alert :pasman/address)
            [:div.form-group
             [:label.control-label.col-sm-2 {:for "city"} "Město / City"]
             [:div.col-sm-10
              [:input#date-from.form-control {:name "city" :type "text"
                                              :value (or (:pasman/address item)
                                                         (:pasman/address last-item))}]]]
            (anti-forgery/anti-forgery-field)
            [:div.form-group
             [:div.col-sm-offset-2.col-sm-10
              [:button.btn.btn-default {:type "submit"} "Uložit / Save"]]]]))
       [:h3 "Seznam zadaných osob / List of entered persons"]
       (if (empty? group-items)
         [:form.form-inline {:method "post" :action "/pasman-reg-find"}
          [:div.form-group
           [:label.control-label {:for "find-no"} "V případě, že chcete upravit již existující seznam, který byl zadán z jiného zařízení, zadejte jedno z číslel dokladu"]
           " "
           [:input#find-no.form-control {:name "find-no" :type "text"}]]
          " "
          [:button.btn.btn-default {:type "submit"} "Vyhledat"]]
         [:table.table.tree-table.table-hover.table-striped
          [:thead
           [:tr
            [:th "Od / From"]
            [:th "Do / To"]
            [:th "Počet nocí / Nights"]
            [:th "Chata / Cottage"]
            [:th "Příjmení / Last name"]
            [:th "Jméno / First name"]
            [:th "Číslo dokladu / ID no"]
            [:th "Věk / Age"]
            [:th "Město / City"]]]
          [:tbody
           (for [item group-items]
             [:tr
              [:td (cljc.util/date-to-str (:ent/from item))]
              [:td (cljc.util/date-to-str (:ent/to item))]
              [:td (cljc.util/calendar-nights item)]
              [:td (:label (get cljc.util/pasman-cottages (:pasman/cottage-no item)))]
              [:td (:pasman/last-name item)]
              [:td (:pasman/first-name item)]
              [:td (:pasman/passport-no item)]
              [:td (:pasman/age item)]
              [:td (:pasman/address item)]
              [:td
               (when (contains? rights :pasman/delete)
                 [:form {:method "post" :action "/pasman-reg-delete"}
                  [:input {:type "hidden" :name "id" :value (:db/id item)}]
                  [:button.btn.btn-default {:type "submit"} "Smazat / Delete"]])]])]])
       [:a {:href "/logout"} "Odhlásit / Logout"]]))))
