(ns masyst.cljs.issue
  (:require [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :as attachments]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.comp.history :as history]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(re-frame/reg-sub-raw
 ::rows
 (fn [db [_]]
   (let [issues (re-frame/subscribe [:entities :issue])
         page-state (re-frame/subscribe [:page-state :issues])
         user (re-frame/subscribe [:auth-user])]
     (ratom/reaction
      (cond->> (or (vals @issues) [])
        (some? (::closed? @page-state))
        (filter #(= (::closed? @page-state) (boolean (:issue/closed? %))))
        (some? (::assigned-to-me? @page-state))
        (filter #(= (::assigned-to-me? @page-state)
                    (or (= (get-in % [:issue/assignee :db/id])
                           (:db/id @user))
                        (= (get-in % [:-created-by :db/id])
                           (:db/id @user))))))))))

#_(defn detail [item user]
  (let [suppliers (re-frame/subscribe [:entities-from-ciselnik :supplier])
        cost-centers (re-frame/subscribe [:entities-from-ciselnik :cost-center])]
    (fn [item user]
      (if-not (and @cost-centers @suppliers)
        [re-com/throbber]
        [re-com/v-box :gap "5px"
         :children
         [[re-com/h-box :gap "5px"
           :children
           [[re-com/box :width "300px"
             :child
             [:div
              [:label "Datum"]
              [:p (str (cljc.util/date-to-str (:ent/date item)))]
              [:label "Firma"]
              [:p (->> item :ent/supplier :db/id (get @suppliers) :ent/title)]
              [:label "Středisko"]
              [:p (->> item :ent/cost-center :db/id (get @cost-centers) :ent/title)]]]
            [:div
             [:label "Předmět"]
             [:p (str (:ent/title item))]
             [:label "Cena"]
             [:p (util/money->text (util/bigdec->float (:issue/price item))) " Kč"]
             (when ((:-rights user) :ebs-calc/paid)
               [:div
                [:label (str "Uhrazeno " (price-paid-% item) "%")]
                [:p (util/money->text (util/bigdec->float (:issue/paid item))) " Kč"]
                [:label (str "Zbývá " (- 100 (price-paid-% item)) "%")]
                [:p (util/money->text (price-rest item)) " Kč"]])
             [:label "Poznámka"]
             (util/dangerousHTML (str/replace (str (:ent/annotation item)) #"\n" "<br />"))]]]
          [:div.panel-group
           [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]]
          [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]
          [history/view user (:db/id item)]]]))))

(defn form [item user]
  (let [users (re-frame/subscribe [:entities :user])
        issue-states (re-frame/subscribe [:ciselnik :issue-state])
        issue-projects (re-frame/subscribe [:ciselnik :issue-project])
        issue-priorities (re-frame/subscribe [:ciselnik :issue-priority])
        issue-types (re-frame/subscribe [:ciselnik :issue-type])]
    (fn [item user]
      (if-not (and @issue-states @users)
        [re-com/throbber]
        [re-com/v-box :children
         [[re-com/label :label "Vytvořeno"]
          [:p (cljc.util/datetime-to-str (:-created-at item))]
          [re-com/label :label "Zadal"]
          [:p (some-> item :-created-by :db/id (@users) :ent/title str)]

          [re-com/label :label "Projekt"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices issue-projects
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:issue/project :db/id])
             :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :issue/project {:db/id %}])
             :placeholder "Vyberte projekt"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Projekty" :class "btn-sm"] :href (str "#/issue-projects")]]]
          [re-com/label :label "Stav"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices issue-states
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:issue/state :db/id])
             :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :issue/state {:db/id %}])
             :placeholder "Vyberte stav"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Stavy" :class "btn-sm"] :href (str "#/issue-states")]]]
          [re-com/label :label "Priorita"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices issue-priorities
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:issue/priority :db/id])
             :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :issue/priority {:db/id %}])
             :placeholder "Vyberte prioritu"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Priority" :class "btn-sm"] :href (str "#/issue-priorities")]]]
          [re-com/label :label "Typ"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices issue-types
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:issue/type :db/id])
             :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :issue/type {:db/id %}])
             :placeholder "Vyberte typ"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Typy" :class "btn-sm"] :href (str "#/issue-types")]]]
          [:label "Předmět"]
          [re-com/input-text
           :model (str (:ent/title item))
           :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :ent/title %])
           :width "500px"]
          [:label "Popis"]
          [re-com/input-textarea
           :model (str (:ent/annotation item))
           :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :ent/annotation %])
           :width "500px"
           :rows 8]
          [:label "Termín"]
          [re-com/input-text
           :model (cljc.util/date-to-str (:ent/date item))
           :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :ent/date (cljc.util/from-dMyyyy %)])
           :validation-regex #"^\d{0,2}$|^\d{0,2}\.\d{0,2}$|^\d{0,2}\.\d{0,2}\.\d{0,4}$"
           :width "100px"]
          [re-com/label :label "Přiřazeno"]
          [re-com/h-box :gap "5px" :children
           [[re-com/single-dropdown
             :choices (some->> @users vals (sort-by :ent/title))
             :id-fn :db/id
             :label-fn :ent/title
             :model (get-in item [:issue/assignee :db/id])
             :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :issue/assignee {:db/id %}])
             :placeholder "Vyberte osobu"
             :filter-box? true
             :width "400px"]
            [re-com/hyperlink-href :label [re-com/button :label "Typy" :class "btn-sm"] :href (str "#/issue-types")]]]
          [:label "Uzavřeno"]
          [re-com/checkbox
           :model (:issue/closed? item)
           :on-change #(re-frame/dispatch [:entity-change :issue (:db/id item) :issue/closed? %])]
          [:label "Soubor"]
          [:input#file-upload
           {:type :file
            :on-change
            (fn [ev]
              (let [file (aget (-> ev .-target .-files) 0)]
                (.log js/console file)
                (re-frame/dispatch [:entity-change :issue (:db/id item) :-file file])
                (when (empty? (:ent/title item))
                  (re-frame/dispatch [:entity-change :issue (:db/id item) :ent/title (.-name file)]))))}]
          [:br]
          [buttons/form-buttons :issue item]
          [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]
          [history/view user (:db/id item)]]]))))

(defn- find-s-w [m s-w]
  {:db/id (some->> m vals (reduce #(when (str/starts-with? (str (:ent/title %2)) s-w) (reduced (:db/id %2))) nil))})

(defn- default-issue [projects states priorities types users]
  {:issue/project (find-s-w projects "EBS")
   :issue/state (find-s-w states "1 - ")
   :issue/priority (find-s-w priorities "C - ")
   :issue/type (find-s-w types "chyba")
   :issue/assignee (find-s-w users "Karel Miarka")})

(defn page-issues []
  (let [items (re-frame/subscribe [::rows])
        user (re-frame/subscribe [:auth-user])
        users (re-frame/subscribe [:entities :user])
        offline? (re-frame/subscribe [:offline?])
        issue-projects (re-frame/subscribe [:entities-from-ciselnik :issue-project])
        issue-states (re-frame/subscribe [:entities-from-ciselnik :issue-state])
        issue-priorities (re-frame/subscribe [:entities-from-ciselnik :issue-priority])
        issue-types (re-frame/subscribe [:entities-from-ciselnik :issue-type])
        table-state (re-frame/subscribe [:table-state :issues])
        page-state (re-frame/subscribe [:page-state :issues])]
    (fn []
      (when-not @page-state
        (re-frame/dispatch [:page-state-change :issues ::closed? false])
        (re-frame/dispatch [:page-state-change :issues ::assigned-to-me? true]))
      [:div
       [:h3 "Požadavky"]
       [re-com/h-box :gap "20px" :align :center
        :children
        [(when ((:-rights @user) :issue/save)
           [:div
            [re-com/button :label "Nový"
             :on-click #(re-frame/dispatch
                         [:entity-new :issue (default-issue @issue-projects @issue-states @issue-priorities @issue-types @users)])]])
         [re-com/label :label "Zobrazení:"]
         [re-com/horizontal-bar-tabs
          :tabs [{:id nil :label "Vše"}
                 {:id true :label "Uzavřené"}
                 {:id false :label "Neuzavřené"}]
          :model (::closed? @page-state)
          :on-change #(re-frame/dispatch [:page-state-change :issues ::closed? %])]
         [re-com/horizontal-bar-tabs
          :tabs [{:id nil :label "Vše"}
                 {:id true :label "Moje"}
                 {:id false :label "Ostatních"}]
          :model (::assigned-to-me? @page-state)
          :on-change #(re-frame/dispatch [:page-state-change :issues ::assigned-to-me? %])]]]
       (if-not (and @items @issue-projects @issue-states @issue-priorities @issue-types)
         [re-com/throbber]
         [data-table/data-table
          :table-id :issues
          :colls [["Vytvořeno" :-created-at]
                  ["Zadal" #(some-> % :-created-by :db/id (@users) :ent/title str)]
                  ["Termín" :ent/date]
                  ["Projekt" #(some-> % :issue/project :db/id (@issue-projects) :ent/title str)]
                  ["Stav" #(some-> % :issue/state :db/id (@issue-states) :ent/title str)]
                  ["Priorita" #(some-> % :issue/priority :db/id (@issue-priorities) :ent/title str)]
                  ["Typ" #(some-> % :issue/type :db/id (@issue-types) :ent/title str)]
                  ["Předmět" :ent/title]
                  ["Přiřazeno" #(some-> % :issue/assignee :db/id (@users) :ent/title str)]
                  ["Uzavřeno" #(or (:issue/closed? %) false)]
                  ["Soubory" #(count (:file/_parent %))]
                  [(if @offline?
                     ""
                     [re-com/md-icon-button
                      :md-icon-name "zmdi-refresh"
                      :tooltip "Načíst ze serveru"
                      :on-click #(re-frame/dispatch [:entities-load :issue])])
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [#_[re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-view-web"
                                    :tooltip "Detail"]
                            :href (str "#/issue/" (:db/id row))]
                         (when ((:-rights @user) :issue/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/issue/" (:db/id row) "e")])
                         (when ((:-rights @user) :issue/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :issue (:db/id row)])])]]))
                   :csv-export]]
          :rows items
          :order-by 0
          :desc? true])])))

(defn page-issue []
  (let [edit? (re-frame/subscribe [:entity-edit? :issue])
        item (re-frame/subscribe [:entity-edit :issue])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Požadavek"]
       [form @item @user]
       #_(if (and @edit? ((:-rights @user) :issue/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :issues  #'page-issues)
(secretary/defroute "/issues" []
  (re-frame/dispatch [:set-current-page :issues]))

(common/add-kw-url :issue "issue")
(pages/add-page :issue #'page-issue)
(secretary/defroute #"/issue/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :issue (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :issue]))
