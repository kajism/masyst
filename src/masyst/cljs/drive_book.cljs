(ns masyst.cljs.drive-book
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
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(re-frame/reg-sub-raw
 ::items
 (fn [db [_]]
   (let [tss (re-frame/subscribe [:entities :time-sheet])
         ts-item-types (re-frame/subscribe [:ciselnik :time-sheet-item-type])
         on-the-road-id (ratom/reaction
                         (:db/id (cljc.util/first-by-ident :time-sheet-item-type/on-the-road @ts-item-types)))]
     (ratom/reaction
      (->> (vals @tss)
           (mapcat (fn [ts]
                     (->> (:time-sheet/items ts)
                          (filter #(= @on-the-road-id (get-in % [:time-sheet-item/type :db/id])))
                          (map #(cond-> %
                                  (not (:ent/user %))
                                  (assoc :ent/user (:ent/user ts))
                                  (not (:ent/type %))
                                  (assoc :ent/type :time-sheet-item)))))))))))

(defn page-drive-books []
  (let [items (re-frame/subscribe [::items])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        automobiles (re-frame/subscribe [:entities-from-ciselnik :automobile])
        users (re-frame/subscribe [:entities :user])]
    (fn []
      [:div
       [:h3 "Kniha jízd"]
       (if-not (and @items @users @automobiles)
         [re-com/throbber]
         [data-table/data-table
          :table-id :drive-books
          :colls [["RZ" #(some->> % :ent/automobile :db/id (get @automobiles) :ent/title str)]
                  ["Osoba" #(some->> % :ent/user :db/id (get @users) :ent/title str)]
                  ["Od" :ent/from]
                  ["Do" :ent/to]
                  ["Popis" (comp cljc.util/shorten :ent/annotation)]
                  ["Tach. v cíli" :drive-book-item/final-kms]
                  ["Ujeto km" :drive-book-item/trip-kms :sum]
                  ["Tankování [l]" :drive-book-item/refuel-litres]
                  ["Tankování [Kč]" :expense/price]
                  #_[(if @offline?
                      ""
                      [re-com/md-icon-button
                       :md-icon-name "zmdi-refresh"
                       :tooltip "Načíst ze serveru"
                       :on-click #(re-frame/dispatch [:entities-load :drive-book])])
                    (fn [row]
                      #_(when (= (:db/id row) (:selected-row-id @table-state))
                        [re-com/h-box
                         :gap "5px"
                         :children
                         [[re-com/hyperlink-href
                           :label [re-com/md-icon-button
                                   :md-icon-name "zmdi-view-web"
                                   :tooltip "Detail"]
                           :href (str "#/dochazka/" (:db/id row))]
                          (when (can-edit? row @user)
                            [re-com/hyperlink-href
                             :label [re-com/md-icon-button
                                     :md-icon-name "zmdi-edit"
                                     :tooltip "Editovat"]
                             :href (str "#/dochazka/" (:db/id row) "e")])
                          (when ((:-rights @user) :time-sheet/delete)
                            [buttons/delete-button #(re-frame/dispatch [:entity-delete :time-sheet (:db/id row)])])]]))
                    :csv-export]]
          :rows items
          :order-by 2
          :desc? true])])))

#_(defn page-drive-book []
  (let [edit? (re-frame/subscribe [:entity-edit? :drive-book])
        item (re-frame/subscribe [:entity-edit :drive-book])
        user (re-frame/subscribe [:auth-user])]
    (fn []
      [:div
       [:h3 "Jízda"]
       (if (and @edit? ((:-rights @user) :drive-book/save))
         [form @item @user]
         [detail @item @user])])))

(pages/add-page :drive-books  #'page-drive-books)
(secretary/defroute "/jizdy" []
  (re-frame/dispatch [:set-current-page :drive-books]))

;; (common/add-kw-url :drive-book "jizda")
;; (pages/add-page :drive-book #'page-drive-book)
;; (secretary/defroute #"/jizda/(\d*)(e?)" [id edit?]
;;   (re-frame/dispatch [:entity-set-edit :drive-book (cljc.util/parse-int id) (not-empty edit?)])
;;   (re-frame/dispatch [:set-current-page :drive-book]))
