(ns masyst.cljs.tech-design
  (:require [ajax.core :as ajax]
            [ajax.edn :as ajax-edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.ajax :refer [server-call]]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.attachments :refer [attachments]]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :refer [data-table]]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as client-util]
            [masyst.cljs.util :as util]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(re-frame/reg-sub-raw
 ::rows
 (fn [db [_]]
   (let [tech-designs (re-frame/subscribe [:entities :tech-design])
         page-state (re-frame/subscribe [:page-state :tech-designs])]
     (ratom/reaction
      (cond->> (or (vals @tech-designs) [])
        (not (:list? @page-state))
        (filter #(zero? (count (:tech-design/_refs %)))))))))

(re-frame/reg-event-db
 ::tech-design-add-tech-design
 util/debug-mw
 (fn [db [_ from-id to-id]]
   (server-call [:tech-design/save {:db/id from-id
                                    :tech-design/refs {:db/id to-id}}]
                nil nil db)
   (-> db
       (update-in [:tech-design from-id :tech-design/refs] conj {:db/id to-id})
       (assoc-in [:tech-design to-id :tech-design/_refs] [{:db/id from-id}]))))

(re-frame/reg-event-db
 ::delete-tech-design-to-tech-design
 util/debug-mw
 (fn [db [_ from-id to-id]]
   (server-call [:tech-design/delete [from-id :tech-design/refs to-id]]
                nil nil db)
   (update-in db [:tech-design from-id :tech-design/refs] #(filterv (fn [tech-d] (not= to-id (:db/id tech-d))) %))))

(re-frame/reg-event-db
 ::saved
 util/debug-mw
 (fn [db [_ new-ent]]
   (update db :tech-design (fn [tech-designs]
                             (reduce (fn [out {:keys [:db/id]}]
                                       (update out id (fn [parent]
                                                        (if (some #(= (:db/id new-ent) (:db/id %)) (:tech-design/refs parent))
                                                          parent
                                                          (update parent :tech-design/refs #(conj (or % [])
                                                                                                  (select-keys new-ent [:db/id])))))))
                                     tech-designs
                                     (:tech-design/_refs new-ent))))))

(defn- parent-path [tech-design tech-designs depth]
  (if (some-> depth (> 10))
    [:span "... zacykleno ..."]
    [:span
     (if-let [parent-id (or (-> tech-design :tech-design/_refs first :db/id)
                            (get-in tech-design [:tech-design/_refs :db/id]))]
       [parent-path (get tech-designs parent-id) tech-designs (inc (or depth 0))]
       [:b [:a {:href "#/vykresy"} "Výkresy"]])
     " / "
     [:a {:href (str "#/vykres/" (:db/id tech-design))}
      (:ent/title tech-design)]]))

(defn- parent-ids [tech-design]
  (->> tech-design
       (iterate #(some-> % :tech-design/_refs first :db/id))
       (take-while some?)
       (take 20)
       (set)))

(defn- files-count-total [tech-designs tech-design]
  (reduce (fn [out td]
            (+ out (count (:file/_parent td))))
          0
          (tree-seq #(seq (:tech-design/refs %))
                    (fn [td]
                      (->> (:tech-design/refs td)
                           (map #(get tech-designs (:db/id %)))))
                    tech-design)))

(defn tech-design-to-tech-design [tech-design]
  (let [tech-designs (re-frame/subscribe [:entities :tech-design])
        selected-tech-design-id (reagent/atom nil)
        to-tech-design (ratom/reaction (->> (:tech-design/refs @tech-design)
                                            (map #(get @tech-designs (:db/id %)))))
        user (re-frame/subscribe [:auth-user])
        table-state (re-frame/subscribe [:table-state :design-to-design])]
    (fn [tech-design]
      (if-not @tech-designs
        [re-com/throbber]
        [:div
         [:h3 [parent-path @tech-design @tech-designs]]
         [re-com/button :label "Nový"
          :on-click #(re-frame/dispatch [:entity-new :tech-design {:tech-design/_refs (select-keys @tech-design [:db/id])}])]
         [re-com/single-dropdown
          :choices (let [referred (->> @tech-design :tech-design/refs
                                       (map :db/id)
                                       (into (parent-ids @tech-design))
                                       (into #{(:db/id @tech-design)}))]
                     (->> (vals @tech-designs)
                          (remove #(referred (:db/id %)))
                          (util/sort-by-locale :ent/title)))
          :id-fn :db/id
          :label-fn :ent/title
          :model @selected-tech-design-id
          :on-change #(reset! selected-tech-design-id %)
          :placeholder "Vyberte výkres"
          :filter-box? true
          :width "400px"]
         [re-com/button
          :label "Podřadit výkres"
          :disabled? (not @selected-tech-design-id)
          :on-click #(re-frame/dispatch [::tech-design-add-tech-design (:db/id @tech-design) @selected-tech-design-id])]
         [data-table
          :table-id :design-to-design
          :colls [["Název" :ent/title]
                  ["Č. výkresu" :tech-design/code]
                  ["Materiál" #(str (-> % :tech-design/material :ent/title))]
                  ["Šanon" :ent/binder]
                  ["Soubory" #(count (:file/_parent %)) :sum]
                  ["Podřazené výkresy" #(count (:tech-design/refs %)) :sum]
                  ["Celkem souborů" #(files-count-total @tech-designs %) :sum]
                  [""
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [#_[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                          :href (str "#/vykres/" (:db/id row))]
                         (when ((:-rights @user) :tech-design/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/vykres/" (:db/id row) "e")])
                         (when ((:-rights @user) :tech-design/delete)
                           [buttons/delete-button #(re-frame/dispatch [::delete-tech-design-to-tech-design (:db/id @tech-design) (:db/id row)])])]]))
                   :none]]
          :rows to-tech-design
          :order-by 0]]))))

(defn form []
  (let [tech-design (re-frame/subscribe [:entity-edit :tech-design])
        tech-designs (re-frame/subscribe [:entities :tech-design])
        materials (re-frame/subscribe [:ciselnik :material])]
    (fn tech-design-form-render []
      (if-not (and @materials)
        [re-com/throbber]
        (let [item @tech-design]
          [:div
           [:h3 [parent-path item @tech-designs]]
           [:label "Název"]
           [re-com/input-text :model (str (:ent/title item))
            :on-change #(re-frame/dispatch [:entity-change :tech-design (:db/id item) :ent/title %])]
           [:label "Č. výkresu"]
           [re-com/input-text :model (str (:tech-design/code item))
            :on-change #(re-frame/dispatch [:entity-change :tech-design (:db/id item) :tech-design/code %])]
           [:label "Materiál"]
           [:br]
           [re-com/single-dropdown
            :choices @materials
            :id-fn :db/id
            :label-fn :ent/title
            :model (-> item :tech-design/material :db/id)
            :on-change #(re-frame/dispatch [:entity-change :tech-design (:db/id item) :tech-design/material {:db/id %}])
            :placeholder "Vyberte materiál"
            :filter-box? true
            :width "400px"]
           [re-com/hyperlink-href :label [re-com/button :label "Materiály"] :href (str "#/materialy")]
           [:br]
           [:label "Šanon"]
           [re-com/input-text
            :model (str (:ent/binder item))
            :on-change #(re-frame/dispatch [:entity-change :tech-design (:db/id item) :ent/binder %])
            :width "400px"]
           [:label "Poznámka"]
           [re-com/input-textarea
            :model (str (:ent/annotation item))
            :on-change #(re-frame/dispatch [:entity-change :tech-design (:db/id item) :ent/annotation %])
            :width "400px"]
           [:label "Soubor"]
           [:input#file-upload
            {:type :file
             :on-change
             (fn [ev]
               (let [file (aget (-> ev .-target .-files) 0)]
                 (.log js/console file)
                 (re-frame/dispatch [:entity-change :tech-design (:db/id item) :-file file])))}]
           [:br]
           [buttons/form-buttons :tech-design (dissoc item :tech-design/_refs) :saved-evt [::saved]]
           (when (:db/id item)
             [:div
              [attachments (:file/_parent item) (:ent/type item) (:db/id item) true]
              [tech-design-to-tech-design tech-design]])])))))

(defn table []
  (let [rows (re-frame/subscribe [::rows])
        user (re-frame/subscribe [:auth-user])
        tech-designs (re-frame/subscribe [:entities :tech-design])
        table-state (re-frame/subscribe [:table-state :tech-designs])
        page-state (re-frame/subscribe [:page-state :tech-designs])]
    (fn []
      (if-not (and @rows @user)
        [re-com/throbber]
        [:div
         [re-com/h-box :gap "20px" :align :center
          :children
          [(when ((:-rights @user) :tech-design/save)
             [:div
              [re-com/hyperlink-href :label [re-com/button :label "Nový"] :href (str "#/vykres/e")]])
           [re-com/label :label "Zobrazení:"]
           [re-com/horizontal-bar-tabs
            :tabs [{:id nil :label "Struktura"}
                   {:id true :label "Seznam"}]
            :model (:list? @page-state)
            :on-change #(re-frame/dispatch [:page-state-change :tech-designs :list? %])]]]
         [data-table
          :table-id :tech-designs
          :colls [["Název" :ent/title]
                  ["Č. výkresu" :tech-design/code]
                  ["Materiál" #(str (-> % :tech-design/material :ent/title))]
                  ["Šanon" :ent/binder]
                  ["Soubory" #(count (:file/_parent %)) :sum]
                  ["Podřazené výkresy" #(count (:tech-design/refs %)) :sum]
                  ["Celkem souborů" #(files-count-total @tech-designs %) :sum]
                  [[re-com/md-icon-button
                    :md-icon-name "zmdi-refresh"
                    :tooltip "Načíst ze serveru"
                    :on-click #(re-frame/dispatch [:entities-load :tech-design])]
                   (fn [row]
                     (when (= (:db/id row) (:selected-row-id @table-state))
                       [re-com/h-box
                        :gap "5px"
                        :children
                        [#_[re-com/hyperlink-href
                          :label [re-com/md-icon-button
                                  :md-icon-name "zmdi-view-web"
                                  :tooltip "Detail"]
                          :href (str "#/vykres/" (:db/id row))]
                         (when ((:-rights @user) :tech-design/save)
                           [re-com/hyperlink-href
                            :label [re-com/md-icon-button
                                    :md-icon-name "zmdi-edit"
                                    :tooltip "Editovat"]
                            :href (str "#/vykres/" (:db/id row) "e")])
                         (when ((:-rights @user) :tech-design/delete)
                           [buttons/delete-button #(re-frame/dispatch [:entity-delete :tech-design (:db/id row)])])]]))
                   :none]]
          :rows rows
          :order-by 0]]))))

(defn page-tech-design []
  [form])

(defn page-tech-designs []
  [:div
   [:h3 "Výkresy"]
   [table]])

(pages/add-page :tech-designs #'page-tech-designs)
(pages/add-page :tech-design #'page-tech-design)

(secretary/defroute "/vykresy" []
  (re-frame/dispatch [:set-current-page :tech-designs]))

(secretary/defroute #"/vykres/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :tech-design (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :tech-design]))

(common/add-kw-url :tech-design "vykres")
