(ns masyst.cljs.core
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [devtools.core :as devtools]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [masyst.cljs.audit]
            [masyst.cljs.bank-holiday]
            [masyst.cljs.business-trip]
            [masyst.cljs.ciselnik]
            [masyst.cljs.contract]
            [masyst.cljs.country]
            [masyst.cljs.common]
            [masyst.cljs.crm-event]
            [masyst.cljs.crm-sale]
            [masyst.cljs.crm-subj]
            [masyst.cljs.drive-book]
            [masyst.cljs.ebs-calc]
            [masyst.cljs.ebs-offer]
            [masyst.cljs.ebs-other]
            [masyst.cljs.ebs-project]
            [masyst.cljs.ebs-handover]
            [masyst.cljs.ebs-tree]
            [masyst.cljs.expense]
            [masyst.cljs.file]
            [masyst.cljs.issue]
            [masyst.cljs.order]
            [masyst.cljs.other]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.pasman]
            [masyst.cljs.psg]
            [masyst.cljs.role]
            [masyst.cljs.tech-design]
            [masyst.cljs.time-sheet]
            [masyst.cljs.user]
            [masyst.cljs.invoice]
            [masyst.cljs.util :as util]
            [masyst.cljs.vacation]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]
            [masyst.cljs.ebs-construction-diary])
  (:import goog.History))

(devtools/install!)

(enable-console-print!)

(extend-type Keyword
  IEncodeJS
  (-clj->js [k]
    (str (namespace k) "/" (name k))))

;; ---- Routes ---------------------------------------------------------------
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (re-frame/dispatch [:set-current-page :main]))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn menu [user]
  (let [offline? (re-frame/subscribe [:offline?])]
    (fn [user]
      (if-not user
        [re-com/throbber]
        [:nav.navbar.navbar-default
         [:div.container-fluid
          [:div.navbar-header
           [:button.navbar-toggle {:type "button" :data-toggle "collapse" :data-target "#masyst-navbar"}
            [:span.icon-bar]
            [:span.icon-bar]
            [:span.icon-bar]]
           [:a {:href "#"}
            [:img {:src "img/logo.svg" :alt "MASYST"}]]]
          [:div#masyst-navbar.collapse.navbar-collapse
           [:ul.nav.navbar-nav
            (when (seq (set/intersection (:-rights user) #{:time-sheet/select :expense/select :drive-book/select}))
              [:li.dropdown
               [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"}
                "Docházka" [:span.caret]]
               [:ul.dropdown-menu
                (when ((:-rights user) :time-sheet/select)
                  [:li [:a {:href "#/dochazka" :data-toggle "collapse" :data-target "#masyst-navbar"} "Docházka"]])
                (when ((:-rights user) :drive-book/select)
                  [:li [:a {:href "#/jizdy" :data-toggle "collapse" :data-target "#masyst-navbar"} "Kniha jízd"]])
                (when ((:-rights user) :expense/select)
                  [:li [:a {:href "#/vydaje" :data-toggle "collapse" :data-target "#masyst-navbar"} "Výdaje"]])]])
            (when ((:-rights user) :ebs-tree/select)
             [:li.dropdown
              [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"}
               "EBS" [:span.caret]]
              [:ul.dropdown-menu
               (when ((:-rights user) :ebs-tree/select)
                 [:li [:a {:href "#/ebs-struktura" :data-toggle "collapse" :data-target "#masyst-navbar"} "Struktura"]])
               (when ((:-rights user) :ebs-project/select)
                 [:li [:a {:href "#/ebs-projekty" :data-toggle "collapse" :data-target "#masyst-navbar"} "Projekt.dok."]])
               (when ((:-rights user) :ebs-handover/select)
                 [:li [:a {:href "#/ebs-predavaci" :data-toggle "collapse" :data-target "#masyst-navbar"} "Předávací.dok."]])
               (when ((:-rights user) :ebs-calc/select)
                 [:li [:a {:href "#/ebs-rozpocty" :data-toggle "collapse" :data-target "#masyst-navbar"} "Rozpočty"]])
               (when ((:-rights user) :ebs-calc/paid)
                 [:li [:a {:href "#/ebs-uhrady" :data-toggle "collapse" :data-target "#masyst-navbar"} "Úhrady"]])
               #_(when ((:-rights user) :ebs-offer/select)
                   [:li [:a {:href "#/ebs-nabidky" :data-toggle "collapse" :data-target "#masyst-navbar"} "Nabídky"]])
               (when ((:-rights user) :ebs-other/select)
                 [:li [:a {:href "#/ebs-ostatni" :data-toggle "collapse" :data-target "#masyst-navbar"} "Ostatní"]])
               (when ((:-rights user) :ebs-construction-diary/select)
                 [:li [:a {:href "#/ebs-pracovni-denik-seznam" :data-toggle "collapse" :data-target "#masyst-navbar"} "Pracovní deník"]])]])
            (when ((:-rights user) :psg/select)
              [:li [:a {:href "#/psg" :data-toggle "collapse" :data-target "#masyst-navbar"} "PSG"]])
            (when ((:-rights user) :invoice/select)
              [:li [:a {:href "#/faktury" :data-toggle "collapse" :data-target "#masyst-navbar"} "Faktury"]])
            (when ((:-rights user) :other/select)
              [:li [:a {:href "#/ostatni" :data-toggle "collapse" :data-target "#masyst-navbar"} "Ostatní"]])
            (when ((:-rights user) :issue/select)
              [:li [:a {:href "#/issues" :data-toggle "collapse" :data-target "#masyst-navbar"} "Požadavky"]])
            (when ((:-rights user) :contract/select)
              [:li [:a {:href "#/smlouvy" :data-toggle "collapse" :data-target "#masyst-navbar"} "Smlouvy"]])
            (when ((:-rights user) :tech-design/select)
              [:li [:a {:href "#/vykresy" :data-toggle "collapse" :data-target "#masyst-navbar"} "Výkresy"]])
            #_(when ((:-rights user) :order/select)
                [:li [:a {:href "#/objednavky" :data-toggle "collapse" :data-target "#masyst-navbar"} "Objednávky"]])]
           (when-not @offline?
             [:ul.nav.navbar-nav.navbar-right
              (when (seq (set/intersection (:-rights user) #{:user/select :role/select :audit/select}))
                [:li.dropdown
                 [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"}
                  "Admin" [:span.caret]]
                 [:ul.dropdown-menu
                  (when ((:-rights user) :audit/select)
                    [:li [:a {:href "#/audit" :data-toggle "collapse" :data-target "#masyst-navbar"} "Audit"]])
                  (when ((:-rights user) :pasman/select)
                    [:li [:a {:href "#/pasman-regs" :data-toggle "collapse" :data-target "#masyst-navbar"} "Pašmán - Registrace"]])
                  (when ((:-rights user) :role/select)
                    [:li [:a {:href "#/role" :data-toggle "collapse" :data-target "#masyst-navbar"} "Role"]])
                  (when ((:-rights user) :file/select)
                    [:li [:a {:href "#/soubory" :data-toggle "collapse" :data-target "#masyst-navbar"} "Soubory"]])
                  (when ((:-rights user) :user/select)
                    [:li [:a {:href "#/uzivatele" :data-toggle "collapse" :data-target "#masyst-navbar"} "Uživatelé"]])]])
              (when (seq (set/intersection (:-rights user)
                                           #{:crm-subj/save :supplier/save :cost-center/save :material/save :ebs-other/save}))
                [:li.dropdown
                 [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"}
                  "Ćíselníky" [:span.caret]]
                 [:ul.dropdown-menu
                  (when ((:-rights user) :automobile/select)
                    [:li [:a {:href "#/automobily" :data-toggle "collapse" :data-target "#masyst-navbar"} "Automobily"]])
                  (when ((:-rights user) :supplier/select)
                    [:li [:a {:href "#/dodavatele" :data-toggle "collapse" :data-target "#masyst-navbar"} "Dodavatelé"]])
                  (when ((:-rights user) :material/select)
                    [:li [:a {:href "#/materialy" :data-toggle "collapse" :data-target "#masyst-navbar"} "Materiály"]])
                  (when ((:-rights user) :ebs-other/select)
                    [:li [:a {:href "#/ebs-ostatni-kategorie" :data-toggle "collapse" :data-target "#masyst-navbar"} "Ostatní kategorie"]])
                  (when ((:-rights user) :bank-holiday/select)
                    [:li [:a {:href "#/statni-svatky" :data-toggle "collapse" :data-target "#masyst-navbar"} "Státní svátky"]])
                  (when ((:-rights user) :crm-sale/select)
                    [:li [:a {:href "#/staty" :data-toggle "collapse" :data-target "#masyst-navbar"} "Státy"]])
                  (when ((:-rights user) :cost-center/select)
                    [:li [:a {:href "#/strediska" :data-toggle "collapse" :data-target "#masyst-navbar"} "Střediska"]])]])
              [:li
               [:a
                {:href "/logout"}
                #_[:span.glyphicon.glyphicon-off] " Odhlásit"]]])]]]))))

(defn page-main []
  [:div
   [:h3 "Informační systém MA servis"]])

(pages/add-page :main #'page-main)

(defn main-app-area []
  #_(re-frame/dispatch-sync [:init-db]) ;; this clears all db after figwheel refresh!
  (let [user (re-frame/subscribe [:auth-user])
        bank-holidays (re-frame/subscribe [:entities :bank-holiday]) ;; needed by vacation, time sheets ...
        ]
    (fn []
      (if-not (and @user @bank-holidays)
        [re-com/throbber]
        [:div
         [menu @user]
         [:div.container
          [pages/page]
          ]]))))

(defn main []
  ;; conditionally start the app based on wether the #main-app-area
  ;; node is on the page
  (hook-browser-navigation!)
  (if-let [node (.getElementById js/document "app")]
    (reagent/render [main-app-area] node)))

(main)
