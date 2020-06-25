(ns masyst.db.import-doc-server
  (:require [clojure.java.io :as io]
            [clojure.java.jdbc :as j]
            [cognitect.transit :as tran]
            [clojure.string :as str]
            [datomic.api :as d]
            [taoensso.timbre :as timbre]))

(defn remove-blanks [row]
  (->> row
       (keep (fn [[k v]]
               (when v
                 (if-not (string? v)
                   [k v]
                   (when-not (str/blank? v)
                     [k (str/trim v)])))))
       (into {})))

(defn write-transit-file [name data]
  (let [writer (tran/writer (io/make-output-stream (str "./resources/dokser/" name ".tr") {:encoding "UTF-8"}) :json)]
    (tran/write writer data)))

(defn read-transit-file [name]
  (let [reader (tran/reader (io/make-input-stream (io/resource (str "dokser/" name ".tr")) {:encoding "UTF-8"}) :json)]
    (tran/read reader)))

(def tables {:countries "A900STAT"
             :addresses "A001ADRESA"
             :users "G001UZIV"
             :projects "C003PROJEKT"
             :products "C007PRODUKT"
             :sales "C008PRODEJ"
             :events "C004EVENT"})

(defn select-from-mssql [table]
  (j/with-db-connection [conn {}]
    (->>
     (j/query conn [(str "SELECT * FROM " (get tables table))])
     (map remove-blanks))))

(defn mssql->tr []
  (doseq [table (keys tables)]
    (write-transit-file (name table) (select-from-mssql table))))

(defn txd->ids [conn txd]
  (let [txr @(d/transact conn txd)]
    #_(timbre/debug txr)
    (->> txd
         (map (fn [{:keys [:db/id]}]
                [(- (:idx id)) (d/resolve-tempid (:db-after txr) (:tempids txr) id)]))
         (into {}))))

(defn import-ref-tables [conn countries products projects]
  (let [country-ids (txd->ids conn (map (fn [{:keys [a900_id a900_nazev]}]
                                          {:db/id (d/tempid :part/crm)
                                           :country/code a900_id
                                           :ent/title a900_nazev
                                           :ent/type :country})
                                        countries))
        prod-ids (txd->ids conn (map (fn [{:keys [c007_nazev c007_id]}]
                                       {:db/id (d/tempid :part/crm (- c007_id))
                                        :ent/title c007_nazev
                                        :ent/type :crm-product})
                                     products))
        proj-ids (txd->ids  conn (map (fn [{:keys [c003_nazev c003_id]}]
                                        {:db/id (d/tempid :part/crm (- c003_id))
                                         :ent/title c003_nazev
                                         :ent/type :crm-project})
                                      projects))]
    [country-ids prod-ids proj-ids]))

(defn subjects->txd [addrs]
  (let [addrs (->> addrs (filter (comp (partial = "N")
                                       :a001_tmp)))
        addrs-by-id (->> addrs (map (juxt :a001_id identity)) (into {}))]
    (for [a addrs
          :let [parent (get addrs-by-id (:a001_id_rodic a))]]
      (reduce
       (fn [out [ki ko c-fn]]
         (let [c-fn (or c-fn (fn [x _] x))]
           (if (or (not (ki a)) (= (ki a) (ki parent)))
             out
             (let [v (try
                       (c-fn (ki a) a)
                       (catch Exception e
                         (timbre/warn "ICO" (.getMessage e))
                         nil))]
               (if-not v
                 out
                 (if-not (get out ko)
                   (assoc out ko v)
                   (update out ko into v)))))))
       (cond->
        {:db/id (d/tempid :part/crm (- (:a001_id a)))
         :ent/type :crm-subj}
         parent
         (assoc :crm-subj/parent (d/tempid :part/crm (- (:a001_id_rodic a)))))
       [[:a001_nazev :ent/title]
        [:a001_ico :crm-subj/reg-no (fn [n _] (->> n (drop-while (partial = \0)) (apply str) (Long/parseLong)))]
        [:a001_dic :crm-subj/tax-no]
        [:a001_kodcislo :crm-subj/cust-no]
        [:a001_kodznak :crm-subj/cust-code]
        [:a001_ulice :crm-subj/street]
        [:a001_cisp :crm-subj/land-reg-no]
        [:a001_ciso :crm-subj/house-no]
        [:a001_obec :crm-subj/city]
        [:a001_psc :crm-subj/zip-code]
        [:a900_id_stat :crm-subj/country (fn [c _] [:country/code c])]
        [:a001_poznamka :ent/annotation]
        [:a001_osoba :crm-subj/person (fn [osoba addr]
                                        (if osoba
                                          (str/split osoba #"\s*,\s*")
                                          (when-let [v (->> (str (:a001_prijmeni addr)
                                                                 " "
                                                                 (:a001_jmeno addr))
                                                            str/trim
                                                            not-empty)]
                                            (vector v))))]
        [:a001_telefon :crm-subj/phone (fn [xs _] (str/split xs #"\s*,\s*"))]
        [:a001_mobil :crm-subj/phone (fn [xs _] (str/split xs #"\s*,\s*"))]
        [:a001_email :crm-subj/email]]))))

(defn- events->txd [events subj-ids proj-ids]
  (let [events (filter #(and (= "N" (:c004_deleted %))
                              (:c004_id %)
                              (pos? (:c004_id %)))
                       events)]
    (for [evt events]
      (reduce
       (fn [out [ki ko c-fn]]
         (let [c-fn (or c-fn (fn [x _] x))]
           (if-not (ki evt)
             out
             (let [v (try
                       (c-fn (ki evt) evt)
                       (catch Exception e
                         (timbre/warn "ICO" (.getMessage e))
                         nil))]
               (if-not v
                 out
                 (if-not (get out ko)
                   (assoc out ko v)
                   (update out ko into v)))))))
       {:db/id (d/tempid :part/crm (- (:c004_id evt)))
        :ent/type :crm-event}
       [[:c004_datzadal :ent/date]
        [:c004_typ :crm-event/type (fn [typ _]
                                     (case typ
                                       "hotline" :crm-event.type/hotline
                                       "activation" :crm-event.type/activation
                                       "administrative" :crm-event.type/administrative
                                       "hotline_exceeded_warning" :crm-event.type/hotline-exceeded-warning
                                       "coding" :crm-event.type/coding
                                       "order" :crm-event.type/order
                                       "testing" :crm-event.type/testing
                                       "informace" :crm-event.type/information
                                       "fixing" :crm-event.type/fixing
                                       "datarepair" :crm-event.type/data-repair
                                       "maintenance" :crm-event.type/maintenance
                                       :crm-event.type/other))]
        [:c004_a001_id_klient :crm/subject (fn [id _]
                                             (get subj-ids id))]
        [:c004_a001_id_zadal :crm-event/created-by (fn [id _]
                                                     (get subj-ids id))]
        [:c004_trvani :crm-event/duration]
        [:c004_c003_id :crm-event/project (fn [id _]
                                            (get proj-ids id))]
        [:c004_popis :ent/annotation]]))))

(defn- sales->txd [sales subj-ids prod-ids]
  (let [sales (filter #(and (:c008_id %)
                            (pos? (:c008_id %)))
                      sales)]
    (for [sale sales]
      (reduce
       (fn [out [ki ko c-fn]]
         (let [c-fn (or c-fn (fn [x _] x))]
           (if-not (ki sale)
             out
             (let [v (try
                       (c-fn (ki sale) sale)
                       (catch Exception e
                         (timbre/warn "ICO" (.getMessage e))
                         nil))]
               (if-not v
                 out
                 (if-not (get out ko)
                   (assoc out ko v)
                   (update out ko into v)))))))
       {:db/id (d/tempid :part/crm (- (:c008_id sale)))
        :ent/type :crm-sale}
       [[:c008_a001_id :crm/subject (fn [id _]
                                      (get subj-ids id))]
        [:c008_modules :crm-sale/modules]
        [:c008_modules_text :crm-sale/modules-text]
        [:c008_pocet_kusu :crm-sale/pcs]
        [:c008_akt_kod :crm-sale/act-code]
        [:c008_user_level :crm-sale/user-level]
        [:c008_user_level_text :crm-sale/user-level-text]
        [:c008_cislo_klienta :crm-subj/cust-no (fn [no _]
                                                 (Long/parseLong no))]
        [:c008_c007_id :crm-sale/product (fn [id _]
                                           (get prod-ids id))]
        [:c008_pozn :ent/annotation]
        [:c008_datdo :crm-sale/expires]
        [:c008_datprodej :ent/date]
        [:c008_net_inst :crm-sale/net-install (fn [yn _]
                                                (when (= (str/upper-case yn) "A")
                                                  true))]]))))

(defn import-from-tr [conn]
  (let [[_ prod-ids proj-ids] (import-ref-tables conn
                                                 (read-transit-file "countries")
                                                 (read-transit-file "products")
                                                 (read-transit-file "projects"))
        ;;_ (write-transit-file "prod-ids" prod-ids)
        ;;_ (write-transit-file "proj-ids" proj-ids)
        addr-ids (txd->ids conn (subjects->txd (read-transit-file "addresses")))
        ;;_ (write-transit-file "addr-ids" addr-ids)
        ]
    (d/transact-async conn (events->txd (read-transit-file "events") addr-ids proj-ids))
    (d/transact-async conn (sales->txd (read-transit-file "sales") addr-ids prod-ids))))
