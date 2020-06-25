(ns masyst.cljs.time-sheet
  (:require [cljs-time.predicates :as tp]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as t]
            [clojure.string :as str]
            [masyst.cljc.util :as cljc.util]
            [masyst.cljs.common :as common]
            [masyst.cljs.comp.buttons :as buttons]
            [masyst.cljs.comp.data-table :as data-table]
            [masyst.cljs.comp.history :as history]
            [masyst.cljs.pages :as pages]
            [masyst.cljs.util :as util]
            [re-com.core :as re-com]
            [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [secretary.core :as secretary]
            [taoensso.timbre :as timbre]))

(def fill-today-in-work-after-hour 15)
(def default-from-hour-str "8:00")
(def whole-day-ts-item-types #{:time-sheet-item-type/vacation
                               :time-sheet-item-type/bank-holiday
                               :time-sheet-item-type/illnesss})

(re-frame/reg-event-db
 ::new-ts
 util/debug-mw
 (fn [db [_]]
   (let [user-id (get-in db [:auth-user :db/id])
         last-users-ts (->> (:time-sheet db)
                            (vals)
                            (filter #(and (:db/id %)
                                          (= user-id (get-in % [:ent/user :db/id]))))
                            (sort-by :ent/from)
                            (reverse)
                            (first))
         new-ent {:ent/user {:db/id user-id}
                  :ent/approval-status (or (some (fn [x] (when (= :approval-status/draft (:db/ident x))
                                                           (select-keys x [:db/id])))
                                                 (vals (:approval-status db)))
                                           {:db/ident :approval-status/draft})
                  :ent/from (if last-users-ts
                              (cljc.util/with-date (:ent/from last-users-ts) #(t/plus % (t/months 1)))
                              (cljc.util/with-date (js/Date.) t/first-day-of-the-month))}]
     (re-frame/dispatch [:entity-new :time-sheet new-ent])
     (re-frame/dispatch [::fill-missing-daily-items]))
   db))

(defn- new-ts-item [ts ts-item]
  (merge ts-item {:ent/user (:ent/user ts)
                  :ent/type :time-sheet-item}))

(re-frame/reg-event-db
 ::new-ts-items
 util/debug-mw
 (fn [db [_ new-ts-items]]
   (let [id (get-in db [:entity-edit :time-sheet :id])
         ts (get-in db [:time-sheet id])]
     (update-in db [:time-sheet id :time-sheet/items]
                (fn [items]
                  (->> new-ts-items
                       (map (partial new-ts-item ts))
                       (into (or items []))))))))

(re-frame/reg-event-db
 ::recalculate-kms
 util/debug-mw
 (fn [db [_]]
   (let [id (get-in db [:entity-edit :time-sheet :id])
         ts (get-in db [:time-sheet id])]
     (update-in db [:time-sheet id :time-sheet/items]
                (fn [items]
                  (->> items
                       (sort-by :ent/from)
                       (reduce (fn [out it]
                                 (if-not (:drive-book-item/final-kms it)
                                   (update out :items conj it)
                                   (-> out
                                       (assoc :last-final-kms (:drive-book-item/final-kms it))
                                       (update :items conj (cond-> it
                                                             (:last-final-kms out)
                                                             (assoc :drive-book-item/trip-kms (- (:drive-book-item/final-kms it)
                                                                                                 (:last-final-kms out))))))))
                               {:last-final-kms nil
                                :items []})
                       :items))))))

(defn- dissoc-excess-attrs [it it-type]
  (cond-> it
    (not= :time-sheet-item-type/vacation (:db/ident it-type))
    (dissoc :vacation/half-day?)
    (not= :time-sheet-item-type/on-the-road (:db/ident it-type))
    (dissoc :ent/automobile :drive-book-item/final-kms :drive-book-item/trip-kms
            :drive-book-item/refuel-litres :expense/price)))

(defn recalculate-duration [it it-type daily-hours]
  (let [whole-day-type? (contains? whole-day-ts-item-types (:db/ident it-type))
        daily-hours (cond-> (or daily-hours 0)
                      (:vacation/half-day? it)
                      (/ 2))]
    (cond
      whole-day-type?
      (let [from (cljc.util/from-HHmm (:ent/from it) default-from-hour-str)]
        (assoc it
               :ent/from from
               :ent/to (cljc.util/date-plus-hours from daily-hours)
               :ent/duration-min (int (* daily-hours 60))))
      (and (:ent/from it) (:ent/to it))
      (assoc it :ent/duration-min (cljc.util/duration-min (:ent/from it) (:ent/to it)))
      :else
      it)))

(re-frame/reg-event-db
 ::change-item
 util/debug-mw
 (fn [db [_ idx attr val]]
   (let [id (get-in db [:entity-edit :time-sheet :id])
         db (if (some? val)
              (assoc-in db [:time-sheet id :time-sheet/items idx attr] val) ;; value change
              (update-in db [:time-sheet id :time-sheet/items idx] #(dissoc % attr)))
         ts (get-in db [:time-sheet id])
         ts-item-types (get-in db [:ciselnik :time-sheet-item-type])]
     (update-in db [:time-sheet id :time-sheet/items idx]
                (fn [it]
                  (let [it-type-id (get-in it [:time-sheet-item/type :db/id])
                        it-type (some #(when (= it-type-id (:db/id %)) %) ts-item-types)
                        it (dissoc-excess-attrs it it-type)]
                    (cond-> (recalculate-duration it it-type (:time-sheet/daily-hours (get-in db [:user (get-in ts [:ent/user :db/id])])))
                      (and (= :time-sheet-item-type/on-the-road (:db/ident it-type))
                           (not (:ent/automobile it)))
                      (assoc :ent/automobile (not-empty (select-keys (some :ent/automobile (reverse (:time-sheet/items ts))) [:db/id]))))))))))

(re-frame/reg-event-db
 ::delete-item
 util/debug-mw
 (fn [db [_ idx]]
   (let [id (get-in db [:entity-edit :time-sheet :id])]
     (update-in db [:time-sheet id :time-sheet/items]
                (fn [items]
                  (into (subvec items 0 idx) (subvec items (inc idx))))))))

(re-frame/reg-event-db
 ::add-vacation-items
 util/debug-mw
 (fn [db [_ from-excl to-incl]]
   (let [id (get-in db [:entity-edit :time-sheet :id])
         ts (get-in db [:time-sheet id])
         bank-holiday?-fn (partial cljc.util/bank-holiday? (vals (get-in db [:bank-holiday])))
         vacation-type (select-keys (cljc.util/first-by-ident :time-sheet-item-type/vacation
                                                              (get-in db [:ciselnik :time-sheet-item-type]))
                                    [:db/id])
         from-dt (tc/from-date from-excl)
         last-day (t/last-day-of-the-month from-dt)]
     (re-frame/dispatch [::new-ts-items (->> (cljc.util/period-working-date-times bank-holiday?-fn (t/plus from-dt (t/days 1)) (tc/from-date to-incl))
                                             (take-while #(not (t/after? % last-day)))
                                             (map #(recalculate-duration
                                                    {:time-sheet-item/type vacation-type
                                                     :ent/from (tc/to-date %)}
                                                    vacation-type
                                                    (:time-sheet/daily-hours (get-in db [:user (get-in ts [:ent/user :db/id])])))))])
     db)))

(re-frame/reg-event-db
 ::fill-missing-daily-items
 util/debug-mw
 (fn [db [_]]
   (let [id (get-in db [:entity-edit :time-sheet :id])
         ts-item-types (get-in db [:ciselnik :time-sheet-item-type])
         ts (get-in db [:time-sheet id])
         daily-hours (:time-sheet/daily-hours (get-in db [:user (get-in ts [:ent/user :db/id])]))
         bank-holiday? (partial cljc.util/bank-holiday? (vals (get-in db [:bank-holiday])))]
     (if (or (nil? ts)
             (nil? daily-hours)
             (<= daily-hours 0))
       db
       (update-in db [:time-sheet id :time-sheet/items]
                  (fn [items]
                    (let [items (vec (sort-by :ent/from items))
                          from-dt (cljc.util/date-time-midnight
                                   (or (some-> items (last) :ent/from (tc/to-date-time) (t/plus (t/days 1)))
                                       (-> (:ent/from ts) (tc/to-date-time) (t/first-day-of-the-month))))
                          to-dt (cljc.util/date-time-midnight
                                 (min (t/now)
                                      (t/last-day-of-the-month (tc/from-date (:ent/from ts)))))
                          period-dts (->> from-dt
                                          (iterate #(t/plus % (t/days 1)))
                                          (remove tp/weekend?)
                                          (take-while #(not (t/after? % to-dt))))
                          ;; after 3PM pre-fill todays working hours?
                          ends-today? (and (last period-dts)
                                           (t/equal? (last period-dts) (cljc.util/date-time-midnight (t/now))))]
                      (when (and ends-today? (>= (t/hour (t/to-default-time-zone (t/now))) fill-today-in-work-after-hour))
                        (re-frame/dispatch [::new-ts-items
                                            (if (<= daily-hours 6)
                                              (let [d1 (cljc.util/from-HHmm (js/Date.) default-from-hour-str)
                                                    d2 (cljc.util/date-plus-hours d1 (+ daily-hours))]
                                                [{:time-sheet-item/type (cljc.util/first-by-ident :time-sheet-item-type/in-work ts-item-types)
                                                  :ent/from d1
                                                  :ent/to (if daily-hours d2 nil)
                                                  :ent/duration-min (* 60 daily-hours)}])
                                              (let [half-daily-hours (/ daily-hours 2)
                                                    d1 (cljc.util/from-HHmm (js/Date.) default-from-hour-str)
                                                    d2 (cljc.util/date-plus-hours d1 (+ half-daily-hours))
                                                    d3 (cljc.util/date-plus-hours d2 (+ 0.5))
                                                    d4 (cljc.util/date-plus-hours d3 (+ half-daily-hours))]
                                                [{:time-sheet-item/type (cljc.util/first-by-ident :time-sheet-item-type/in-work ts-item-types)
                                                  :ent/from d1
                                                  :ent/to d2
                                                  :ent/duration-min (* 60 half-daily-hours)}
                                                 {:time-sheet-item/type (cljc.util/first-by-ident :time-sheet-item-type/break ts-item-types)
                                                  :ent/from d2
                                                  :ent/to d3
                                                  :ent/duration-min 30}
                                                 {:time-sheet-item/type (cljc.util/first-by-ident :time-sheet-item-type/in-work ts-item-types)
                                                  :ent/from d3
                                                  :ent/to d4
                                                  :ent/duration-min (* 60 half-daily-hours)}]))]))
                      (into items
                            (for [dt (if ends-today? (butlast period-dts) period-dts)
                                  :let [date (tc/to-date dt)
                                        from (cljc.util/from-HHmm date default-from-hour-str)]]
                              (new-ts-item
                               ts
                               (if (bank-holiday? dt)
                                 {:time-sheet-item/type (cljc.util/first-by-ident :time-sheet-item-type/bank-holiday ts-item-types)
                                  :ent/from from
                                  :ent/to (cljc.util/date-plus-hours from daily-hours)
                                  :ent/duration-min (* 60 daily-hours)}
                                 {:time-sheet-item/type (cljc.util/first-by-ident :time-sheet-item-type/absence ts-item-types)
                                  :ent/from from
                                  :ent/to (cljc.util/date-plus-hours from daily-hours)
                                  :ent/duration-min (* 60 daily-hours)})))))))))))

(defn can-edit? [ts {rights :-rights user-id :db/id :as user}]
  (or (= "admin" (:user/roles user))
      (and (contains? rights :time-sheet/save)
           (= (get-in ts [:ent/user :db/id]) user-id))))

(defn time-sheet-item []
  (let [ts-item-types (re-frame/subscribe [:ciselnik :time-sheet-item-type])
        ts-item-types-map (re-frame/subscribe [:entities-from-ciselnik :time-sheet-item-type])
        automobiles (re-frame/subscribe [:ciselnik :automobile])
        automobiles-map (re-frame/subscribe [:entities-from-ciselnik :automobile])
        drive-book-items (re-frame/subscribe [:masyst.cljs.drive-book/items])
        more-days? (reagent/atom false)
        vacation-end-day (reagent/atom nil)
        max-final-kms-fn #(or (->> @drive-book-items
                                   (sort-by :drive-book-item/final-kms)
                                   (reverse)
                                   (some (fn [on-road-item]
                                           (when (and (not= % on-road-item)
                                                      (= (get-in % [:ent/automobile :db/id])
                                                         (get-in on-road-item [:ent/automobile :db/id]))
                                                      (:drive-book-item/final-kms on-road-item))
                                             (:drive-book-item/final-kms on-road-item)))))
                              0)]
    (fn [it d idx edit?]
      (let [it-type (->> it :time-sheet-item/type :db/id (get @ts-item-types-map))
            whole-day-type? (contains? whole-day-ts-item-types (:db/ident it-type))]
        [:tbody
         [:tr
          [:td
           (if edit?
             [re-com/single-dropdown
              :choices ts-item-types
              :id-fn :db/id
              :label-fn :ent/title
              :model (get-in it [:time-sheet-item/type :db/id])
              :on-change #(re-frame/dispatch [::change-item idx :time-sheet-item/type {:db/id %}])
              :filter-box? true
              :width "150px"]
             [:p (->> it :time-sheet-item/type :db/id (get @ts-item-types-map) :ent/title)])]
          [:td
           (let [[_ time-str] (str/split (cljc.util/to-format (:ent/from it) cljc.util/formatter-ddMMyyyyHHmm) #"\s")]
             (if (and edit?
                      (not whole-day-type?))
               [re-com/input-text
                :model time-str
                :on-change #(re-frame/dispatch [::change-item idx :ent/from (cljc.util/from-HHmm d %)])
                :validation-regex #"^(\d{1,2}):?(\d{1,2})?$"
                :width "100px"]
               [:p time-str]))]
          [:td
           (let [[_ time-str] (str/split (cljc.util/to-format (:ent/to it) cljc.util/formatter-ddMMyyyyHHmm) #"\s")]
             (if (and edit?
                      (not whole-day-type?))
               [re-com/input-text
                :model (str time-str)
                :on-change #(re-frame/dispatch [::change-item idx :ent/to (let [to (cljc.util/from-HHmm d %)]
                                                                            (if (some-> to (.getTime) (< (.getTime (:ent/from it))))
                                                                              (cljc.util/date-plus-hours to 24)
                                                                              to))])
                :validation-regex #"^(\d{0,2}):?(\d{1,2})?$"
                :width "100px"]
               [:p time-str]))]
          [:td
           [:p (cljc.util/mins--hm (:ent/duration-min it))]]
          [:td
           (if (= :time-sheet-item-type/vacation (:db/ident it-type))
             (if edit?
               [re-com/h-box :gap "20px"
                :children
                [[re-com/checkbox
                  :model (:vacation/half-day? it)
                  :on-change #(re-frame/dispatch [::change-item idx :vacation/half-day? %])
                  :label "půldenní?"]
                 (when-not (:db/id it)
                   [re-com/checkbox
                    :model more-days?
                    :on-change #(swap! more-days? not)
                    :label "vícedenní?"])]]
               [:p (when (:vacation/half-day? it)
                     "půldenní")])
             (if edit?
               [re-com/input-textarea
                :model (str (:ent/annotation it))
                :on-change #(re-frame/dispatch [::change-item idx :ent/annotation %])
                :width "400px"
                :rows 1]
               [:p (util/dangerousHTML (str/replace (str (:ent/annotation it)) #"\n" "<br />"))]))]
          [:td
           (when edit?
             [buttons/delete-button #(re-frame/dispatch [::delete-item idx])])]]
         (when @more-days?
           [:tr
            [:td {:col-span "4"}]
            [:td
             [re-com/h-box :gap "5px" :children
              [[:label "Datum konce dovolené:"]
               [re-com/input-text
                :model (str @vacation-end-day)
                :on-change #(reset! vacation-end-day (cljc.util/parse-int %))
                :validation-regex #"^\d{0,2}$"
                :width "45px"]
               [:p "." (cljc.util/to-format d cljc.util/formatter-MMyyyy)]
               [re-com/button
                :class "btn-danger"
                :label "Přidat záznamy"
                :on-click #(when @vacation-end-day
                             (re-frame/dispatch [::add-vacation-items d (cljc.util/from-format (str @vacation-end-day "." (cljc.util/to-format d cljc.util/formatter-MMyyyy)) cljc.util/formatter-dMyyyy)]))]]]]])
         (when (= :time-sheet-item-type/on-the-road (-> it :time-sheet-item/type :db/id (@ts-item-types-map) :db/ident))
           [:tr
            [:td {:style {:text-align "right"}} [:label "Automobil:"]]
            [:td
             (if edit?
               [re-com/single-dropdown
                :choices automobiles
                :placeholder "Automobil"
                :id-fn :db/id
                :label-fn :ent/title
                :model (get-in it [:ent/automobile :db/id])
                :on-change #(re-frame/dispatch [::change-item idx :ent/automobile {:db/id %}])
                :width "100px"]
               [:p (->> it :ent/automobile :db/id (get @automobiles-map) :ent/title)])]
            [:td
             (if edit?
               [re-com/input-text
                :model (str (:drive-book-item/final-kms it))
                :placeholder "Tach. v cíli [km]"
                :on-change #(do
                              (re-frame/dispatch [::change-item idx :drive-book-item/final-kms (or (cljc.util/parse-int %) 0)])
                              (let [kms (max-final-kms-fn it)]
                                (when (pos? kms)
                                  (re-frame/dispatch [::change-item idx :drive-book-item/trip-kms
                                                      (- (or (cljc.util/parse-int %) 0)
                                                         kms)]))))
                :validation-regex #"^(\d{0,6})$"
                :width "100px"]
               [:p (:drive-book-item/final-kms it)])]
            [:td
             (if (and edit? (zero? (max-final-kms-fn it)))
               [re-com/input-text
                :model (str (:drive-book-item/trip-kms it))
                :placeholder "Ujeto [km]"
                :on-change #(re-frame/dispatch [::change-item idx :drive-book-item/trip-kms (or (cljc.util/parse-int %) 0)])
                :validation-regex #"^(\d{0,4})$"
                :width "100px"]
               [:p.right {:class (when (neg? (:drive-book-item/trip-kms it)) "error")}
                (or (:drive-book-item/trip-kms it) "?")])]
            [:td
             [re-com/h-box :gap "5px" :justify :between :children
              [[:label "km"]
               [:label "Tankování:"]]]]
            [:td
             [re-com/h-box :gap "5px" :children :between :children
              [(if edit?
                 [re-com/input-text
                  :model (util/float--text (:drive-book-item/refuel-litres it))
                  :placeholder "Množství"
                  :on-change #(re-frame/dispatch [::change-item idx :drive-book-item/refuel-litres (cljc.util/parse-float %)])
                  :validation-regex #"^(\d{0,2},?\d{0,2})$"
                  :width "100px"]
                 [:p (util/float--text (:drive-book-item/refuel-litres it))])
               [:label "litrů"]
               (if edit?
                 [re-com/input-text
                  :model (util/float--text (:expense/price it))
                  :placeholder "Cena celkem"
                  :on-change #(re-frame/dispatch [::change-item idx :expense/price (cljc.util/parse-float %)])
                  :validation-regex #"^(\d{0,4},?\d{0,2})$"
                  :width "100px"]
                 [:p (util/float--text (:expense/price it))])
               [:label "Kč"]]]]])]))))

(defn daily-table [d its edit?]
  [:table.table.tree-table.table-hover.table-striped
   [:thead
    [:tr
     [:th "Typ záznamu"]
     [:th "Čas od"]
     [:th "Čas do"]
     [:th "Doba"]
     [:th "Popis práce (cesty)"]]]
   (doall
    (for [it its
          :let [idx (:idx (meta it))]]
        ^{:key (or (:db/id it) idx)}
        [time-sheet-item it d idx edit?]))
   (when edit?
     [:tbody
      [:tr
       [:td {:col-span 10}
        [re-com/h-box :gap "5px" :children
         [[re-com/button :label "Uložit" :class "btn-success"
           :on-click #(do (re-frame/dispatch [:entity-save :time-sheet]))]
          [re-com/button :label "Přidat záznam"
           :on-click #(re-frame/dispatch [::new-ts-items [(if (seq its)
                                                            {:ent/from (or (-> (last its) :ent/to)
                                                                           (cljc.util/date-with-current-time d))}
                                                            {:ent/from (if (cljc.util/today? d)
                                                                         (cljc.util/date-with-current-time d)
                                                                         (cljc.util/from-HHmm d default-from-hour-str))})]])]
          (when (some :drive-book-item/final-kms its)
            [re-com/button :label "Přepočítat km" :class "btn-warning" :on-click #(re-frame/dispatch [::recalculate-kms])])]]]]])])

(defn working-hours-hm [its ts-item-types-map]
  (->> its
       (reduce (fn [out it]
                 (if (and (:ent/duration-min it)
                          (->> (get-in it [:time-sheet-item/type :db/id])
                               (get ts-item-types-map)
                               :time-sheet-item-type/as-working-hours?))
                   (+ out (:ent/duration-min it))
                   out))
               0)
       (cljc.util/mins--hm)))

(defn sums-by-type-tds [its-by-type used-item-types]
  (map #(vector :td [:p (->> (get its-by-type (:db/id %))
                             (map :ent/duration-min)
                             (reduce + 0)
                             (cljc.util/mins--hm))])
       used-item-types))

(defn monthly-time-sheet [ts user edit?]
  (let [users (re-frame/subscribe [:entities :user])
        approval-states (re-frame/subscribe [:entities :approval-status])
        ts-item-types (re-frame/subscribe [:ciselnik :time-sheet-item-type])
        ts-item-types-map (re-frame/subscribe [:entities-from-ciselnik :time-sheet-item-type])
        automobiles (re-frame/subscribe [:ciselnik :automobile])
        bank-holidays (re-frame/subscribe [:ciselnik :bank-holiday])
        expand-days (reagent/atom (cond-> #{}
                                    (t/before? (t/now) (-> (:ent/from ts)
                                                           (cljc.util/with-date t/last-day-of-the-month)
                                                           (cljc.util/with-date #(t/plus % (t/days 1)))))
                                    (conj (t/day (t/now)))))]
    (if (nil? ts)
      (re-frame/dispatch [::new-ts])
      (re-frame/dispatch [::fill-missing-daily-items]))
    (fn [ts user]
      (if-not (and @users @ts-item-types @automobiles)
        [re-com/throbber]
        (let [its-by-type (group-by #(get-in % [:time-sheet-item/type :db/id]) (:time-sheet/items ts))
              its-by-day (->> (:time-sheet/items ts)
                              (map-indexed (fn [idx it]
                                             (with-meta it {:idx idx
                                                            :day (-> (:ent/from it)
                                                                     (cljc.util/to-format cljc.util/formatter-ddMMyyyyHHmm)
                                                                     (str/split #"\.")
                                                                     (first)
                                                                     (cljc.util/parse-int))})))
                              (group-by #(-> (meta %) :day))
                              (into (sorted-map)))
              used-item-types (->> @ts-item-types
                                   (keep #(when (contains? its-by-type (:db/id %)) %)))
              header-row [:thead
                          (into
                           [:tr
                            [:th]
                            [:th "Den v týdnu"]
                            [:th "Datum"]
                            [:th "Název svátku"]
                            [:th "Započteno"]]
                           (map #(vector :td [:label (:ent/title %)])
                                used-item-types))]]
          [re-com/v-box :children
           [[:table.table.tree-table.table-hover.table-striped
             [:thead
              (into
               [:tr
                [:th]
                [:th "Osoba"]
                [:th "Rok/měsíc"]
                [:th "Schvalování"]
                [:th "Započteno"]]
               (map #(vector :td [:label (:ent/title %)])
                    used-item-types))]
             [:tbody
              (into
               [:tr
                [:td]
                [:td
                 [:p (or (->> ts :ent/user :db/id (get @users) :ent/title)
                         (:ent/title user))]]
                [:td
                 [:p (cljc.util/to-format (:ent/from ts) cljc.util/formatter-yyyyMM)]]
                [:td
                 [:p (->> ts :ent/approval-status :db/id (get @approval-states) :ent/title)]]
                [:td
                 [:p (working-hours-hm (:time-sheet/items ts) @ts-item-types-map)]]]
               (sums-by-type-tds its-by-type used-item-types))]
             header-row
             (seq (reduce into
                          (for [d (cljc.util/dates-of-the-month (:ent/from ts))
                                :let [dt (tc/from-date d)
                                      day-no (t/day dt)
                                      day-of-week (t/day-of-week dt)
                                      its (get its-by-day day-no)]]
                            [^{:key day-no}
                             [:tbody
                              (into
                               [:tr
                                [:td
                                 (if (contains? @expand-days day-no)
                                   [re-com/md-icon-button :md-icon-name "zmdi-minus-square"
                                    :tooltip "Skrýt položky dne" :tooltip-position :right-center
                                    :on-click #(swap! expand-days (fn [xs] (disj xs day-no)))]
                                   [re-com/md-icon-button :md-icon-name "zmdi-plus-square"
                                    :tooltip "Upravit položky dne" :tooltip-position :right-center
                                    :on-click #(swap! expand-days (fn [xs] (conj xs day-no)))])]
                                [:td
                                 (get cljc.util/czech-day-of-week (t/day-of-week dt))]
                                [:td
                                 [:label (cljc.util/to-format d cljc.util/formatter-ddMMyyyy)]]
                                [:td
                                 (:bank-holiday/label (cljc.util/bank-holiday-for-date-time @bank-holidays dt))]
                                [:td
                                 (working-hours-hm its @ts-item-types-map)]]
                               (sums-by-type-tds (group-by #(get-in % [:time-sheet-item/type :db/id]) its) used-item-types))
                              (when (contains? @expand-days day-no)
                                [:tr
                                 [:td]
                                 [:td {:col-span (+ 4 (count used-item-types))}
                                  [daily-table d its (and edit? (can-edit? ts user))]]])]
                             (when (= day-of-week 7)
                               (with-meta header-row {:key (str "head" day-no)}))])))]
            [:br]
            (when edit?
              [buttons/form-buttons :time-sheet ts :create-evt [::new-ts] :copy-button? false])
            [history/view user (:db/id ts)]]])))))

(defn page-time-sheets []
  (let [time-sheets (re-frame/subscribe [:entities :time-sheet])
        user (re-frame/subscribe [:auth-user])
        offline? (re-frame/subscribe [:offline?])
        users (re-frame/subscribe [:entities-from-ciselnik :user])
        approval-states (re-frame/subscribe [:entities :approval-status])
        table-state (re-frame/subscribe [:table-state :time-sheets])
        ts-item-types (re-frame/subscribe [:ciselnik :time-sheet-item-type])
        ts-item-types-map (re-frame/subscribe [:entities-from-ciselnik :time-sheet-item-type])]
    (fn []
      [:div
       [:h3 "Evidence docházky"]
       (if-not (and @users @ts-item-types)
         [re-com/throbber]
         [:div
          (when ((:-rights @user) :time-sheet/save)
            [:div
             [re-com/button :label "Nový" :on-click #(re-frame/dispatch [::new-ts])]
             [:br]
             [:br]])
          [data-table/data-table
           :table-id :time-sheets
           :colls (-> [["Rok/měsíc" #(cljc.util/to-format (:ent/from %) cljc.util/formatter-yyyyMM)]
                       ["Osoba" #(some->> % :ent/user :db/id (get @users) :ent/title str)]
                       ["Schvalování" #(some->> % :ent/approval-status :db/id (get @approval-states) :ent/title str)]
                       ["Započteno" #(working-hours-hm (:time-sheet/items %) @ts-item-types-map)]]
                      (into (map (fn [item-type]
                                   [(:ent/title item-type)
                                    (fn [ts]
                                      (->> (:time-sheet/items ts)
                                           (filter #(= (:db/id item-type)
                                                       (get-in % [:time-sheet-item/type :db/id])))
                                           (map :ent/duration-min)
                                           (reduce + 0)
                                           (cljc.util/mins--hm)))])
                                 @ts-item-types))
                      (conj [(if @offline?
                               ""
                               [re-com/md-icon-button
                                :md-icon-name "zmdi-refresh"
                                :tooltip "Načíst ze serveru"
                                :on-click #(re-frame/dispatch [:entities-load :time-sheet])])
                             (fn [row]
                               (when (= (:db/id row) (:selected-row-id @table-state))
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
                             :csv-export]))
           :rows time-sheets
           :order-by 0
           :desc? true
           :sum-format "%.1f"]])])))

(defn page-time-sheet []
  (let [edit? (re-frame/subscribe [:entity-edit? :time-sheet])
        ts (re-frame/subscribe [:entity-edit :time-sheet])
        user (re-frame/subscribe [:auth-user])
        ts-item-types (re-frame/subscribe [:ciselnik :time-sheet-item-type]) ;; must be initialized before handlers are called
]
    (fn []
      [:div
       [:h3 "Evidence docházky"]
       (if-not (and @ts @ts-item-types)
         [re-com/throbber]
         [monthly-time-sheet @ts @user (and @edit? (can-edit? @ts @user))])])))

(pages/add-page :time-sheets  #'page-time-sheets)

(secretary/defroute "/dochazka" []
  (re-frame/dispatch [:set-current-page :time-sheets]))

(common/add-kw-url :time-sheet "dochazka")
(pages/add-page :time-sheet #'page-time-sheet)
(secretary/defroute #"/dochazka/(\d*)(e?)" [id edit?]
  (re-frame/dispatch [:entity-set-edit :time-sheet (cljc.util/parse-int id) (not-empty edit?)])
  (re-frame/dispatch [:set-current-page :time-sheet]))
