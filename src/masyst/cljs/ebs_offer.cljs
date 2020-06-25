(ns masyst.cljs.ebs-offer
  (:require [cljs.pprint :as pprint]
            [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :as attachments]
            [masyst.cljs.comp.ebs-data-tables :as ebs-data-tables]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [masyst.cljs.comp.buttons :as buttons]))

(defn table [items user]
  [:div
   (when ((:-rights user) :ebs-offer/save)
     [:div
      [re-com/hyperlink-href :label [re-com/button :label "Nová"] :href (str "#/ebs-nabidka/e")]
      [:br]
      [:br]])
   [ebs-data-tables/offers items :filter ((:-rights user) :ebs-offer/save) ((:-rights user) :ebs-offer/delete) :ebs-offers]])

(defn form [item ebs-tree]
  [:div
   [:label "Struktura"]
   [:br]
   [re-com/single-dropdown
    :choices ebs-tree
    :id-fn :db/id
    :label-fn #(str (:ebs/code %) " " (:ent/title %))
    :model (get-in item [:ebs/code-ref :db/id])
    :on-change #(re-frame/dispatch [:entity-change :ebs-offer (:db/id item) :ebs/code-ref {:db/id %}])
    :placeholder "Začleňte dokument"
    :filter-box? true
    :width "400px"]
   [:br]
   [:label "Název"]
   [re-com/input-text
    :model (str (:ent/title item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-offer (:db/id item) :ent/title %])
    :width "400px"]
   [:label "Vítězná nabídka?"]
   [re-com/checkbox
    :model (boolean (:ebs-offer/winner? item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-offer (:db/id item) :ebs-offer/winner? %])]
   [:label "Poznámka"]
   [re-com/input-textarea
    :model (str (:ent/annotation item))
    :on-change #(re-frame/dispatch [:entity-change :ebs-offer (:db/id item) :ent/annotation %])
    :width "400px"]
   [:br]
   [:label "Soubor"]
   [:input#file-upload
    {:type :file
     :on-change
     (fn [ev]
       (let [file (aget (-> ev .-target .-files) 0)]
         (.log js/console file)
         (re-frame/dispatch [:entity-change :ebs-offer (:db/id item) :-file file])
         (when (empty? (:ent/title item))
           (re-frame/dispatch [:entity-change :ebs-offer (:db/id item) :ent/title (.-name file)]))))}]
   [:br]
   [buttons/form-buttons :ebs-offer item]
   [attachments/attachments (:file/_parent item) (:ent/type item) (:db/id item) true]])

(defn detail [item user]
  (let [ebs-tree-id (-> item :ebs/code-ref :db/id)]
    [:div
     [:label "Struktura"]
     [:p (str (-> item :ebs/code-ref :ebs/code) " " (-> item :ebs/code-ref :ent/title))]
     [:label "Název"]
     [:p (str (:ent/title item)) [:br]]
     [:label "Vítězná nabídka?"]
     [:p (util/boolean->text (:ebs-offer/winner? item))]
     [:label "Poznámka"]
     [:p (str (:ent/annotation item)) [:br]]
     [:div.panel-group
      [attachments/attachments-panel (:file/_parent item) (:ent/type item) (:db/id item) false]
      [ebs-data-tables/calcs-panel user ebs-tree-id]
      [ebs-data-tables/projects-panel user ebs-tree-id]]
     [re-com/button :label "Zpět" :on-click #(-> js/window .-history .back)]]))

(defn page-ebs-offers []
  (let [items (re-frame/subscribe [:entities :ebs-offer])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Nabidky"]
       (if-not @items
         [re-com/throbber]
         [table items @user])])))

(defn page-ebs-offer []
  (let [edit? (re-frame/subscribe [:entity-edit? :ebs-offer])
        item (re-frame/subscribe [:entity-edit :ebs-offer])
        ebs-tree (re-frame/subscribe [:ebs-tree])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Energoblok Šternberk - Nabidka"]
       (if-not @ebs-tree
         [re-com/throbber]
         (if (and @edit? ((:-rights @user) :ebs-offer/save))
           [form @item @ebs-tree]
           [detail @item @user]))])))

(pages/add-page :ebs-offers #'page-ebs-offers)
(pages/add-page :ebs-offer #'page-ebs-offer)

(secretary/defroute "/ebs-nabidky" []
  (re-frame/dispatch [:set-current-page :ebs-offers]))

(secretary/defroute #"/ebs-nabidka/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :ebs-offer (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :ebs-offer]))

(common/add-kw-url :ebs-offer "ebs-nabidka")
