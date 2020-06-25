(ns masyst.db.masyst-service
  (:require [clojure.data :as data]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [datomic.api :as d]
            [masyst.cljc.util :as cljc.util]
            [masyst.db.common-database :as common-db]
            [masyst.db.file-service :as file-service]
            [postal.core :as postal]
            [taoensso.timbre :as timbre]
            [taoensso.truss :refer [have]])
  (:import java.nio.file.Files
           java.nio.file.Paths
           java.io.IOException))

#_(d/q '[:find [?e ...]
       :where
       [?e :db/ident ?ident]
       [_ :db.install/attribute ?e]
       [(namespace ?ident) ?ns]
       [(= ?ns "crm-subj")]]
     (user/db))

(defmulti select (fn [db user ent-type where] ent-type))

(defmulti save! (fn [conn user ent-type ent] ent-type))

(defn select-by-id [db user ent-type id]
  (first (select db user ent-type {:db/id id})))

(defn save-default [conn user ent-type ent]
  (let [id (common-db/save! conn (:db/id user) ent-type ent)]
    (select-by-id (d/db conn) user ent-type id)))

(defmethod save! :default [conn user ent-type ent]
  (save-default conn user ent-type ent))

(defmethod save! :issue [conn user ent-type ent]
  (let [db (d/db conn)
        old-assignee-id (get-in (d/pull db '[{:issue/assignee [:db/id]}] (:db/id ent)) [:issue/assignee :db/id])
        saved (save-default conn user ent-type ent)
        saved-assignee-id (get-in saved [:issue/assignee :db/id])]
    (when (and saved-assignee-id
               (not= saved-assignee-id old-assignee-id))
      (let [msg {:from (:user/email user)
                 :to (:user/email (d/pull db '[:user/email] saved-assignee-id))
                 :subject (str "[masyst] Přiřazen požadavek: " (:ent/title saved))
                 :body (str "https://xyz.cz/#/issue/" (:db/id saved) "e")}
            send-result (postal/send-message msg)]
        (timbre/info "Email sent" msg ":" send-result)))
    saved))

(defmethod save! :time-sheet [conn user ent-type ent]
  (save-default conn user ent-type
                (update ent :time-sheet/items
                        #(walk/prewalk (fn [x]
                                         (if (and (map-entry? x)
                                                  (contains? #{:drive-book-item/refuel-litres
                                                               :expense/price}
                                                             (key x)))
                                           (when-let [flx (cljc.util/parse-float (val x))]
                                             [(key x) flx])
                                           x))
                                       %))))

(defmethod save! :vacation [conn user ent-type ent]
  (save-default conn user ent-type (update ent :vacation/working-days cljc.util/parse-float)))

(defmethod save! :expense [conn user ent-type ent]
  (save-default conn user ent-type (update ent :expense/price cljc.util/parse-float)))

(defmethod save! :pasman [conn user ent-type ent]
  (save-default conn user ent-type (update ent :pasman/group-uuid #(or % (d/squuid)))))

(defn save-with-file [conn user ent-type ent file]
  (let [{:keys [:db/id] :as item} (save! conn user ent-type ent)]
    (if file
      (do
        (file-service/save-file! conn user file ent-type id)
        (select-by-id (d/db conn) user ent-type id))
      item)))

(defn delete-with-file [conn user ent-type to-delete]
  (let [uid (:db/id user)]
    (if (vector? to-delete)
      (common-db/transact conn uid [(into [:db/retract] to-delete)])
      (let [id (cond (number? to-delete) to-delete
                     (map? to-delete) (:db/id to-delete)
                     :else (throw (RuntimeException. "Can only delete by entity or numeric ID.")))
            ent (d/pull (d/db conn) '[* {:file/_parent [* {:file/parent [:ent/type]}]}
                                      {:file/parent [:ent/type]}] id)]
        (when (= ent-type (:ent/type ent)) ;;bezpecnostni kontrola / prava
          (doseq [file (:file/_parent ent)]
            (file-service/delete-file conn user (:db/id file)))
          (common-db/transact conn uid [[:db.fn/retractEntity id]]))
        ;; jedna se o soubor?
        (when (and (= :file (:ent/type ent)) (= ent-type (-> ent :file/parent :ent/type)))
          (file-service/delete-file conn user id)))))
  nil)

(defmulti delete! (fn [conn user ent-type to-delete] ent-type))

(defmethod delete! :default [conn user ent-type to-delete]
  (delete-with-file conn user ent-type to-delete))

(defn select-file-path-type-name [db user id]
  (file-service/select-file-path-type-name db user id))

(defn copy-to [conn user ent-type ids target]
  (timbre/debug "copy-to called ids: " ids)
  (let [db (d/db conn)]
    (doseq [id ids]
      (let [from-item (select-by-id db user ent-type id)
            to-item (-> from-item
                        (dissoc :db/id :ent/type :file/_parent :ebs-calc/price :ebs-calc/paid)
                        (assoc :ebs/code-ref {:db/id (-> from-item :ebs/code-ref :db/id)}))
            files (:file/_parent from-item)]
        (if (seq files)
          (reduce (fn [item from-file]
                    (save-with-file conn user target (dissoc item :file/_parent) (select-file-path-type-name db user (:db/id from-file))))
                  to-item
                  files)
          (save! conn user target to-item))))))

(defn parse-bigdec [str]
  (-> str
      (str/replace "," ".")
      bigdec))

(defn parse-bigdec-kc [str]
  (-> str
      (subs 0 (- (count str) 3))
      parse-bigdec))

(defn- read-csv-lines [file]
  (with-open [in-file (io/reader file :encoding "cp1250")]
    (doall
     (csv/read-csv in-file :separator \;))))

(defmulti bulk-import (fn [conn user ent-type {import-type :type :as opts} file]
                        [ent-type import-type]))

(defmethod bulk-import [:invoice :csv-upload]
  [conn user ent-type opts file]
  (timbre/info "Import faktur z csv")
  (case ent-type
    :invoice
    (let [lines (read-csv-lines file)
          db (d/db conn)
          supplier-name->id (atom (into {} (map (juxt :ent/title :db/id)
                                                (select db user :supplier {}))))
          exist-supp-count (count @supplier-name->id)
          cost-center-name->id (atom (into {} (map (juxt :ent/title :db/id)
                                                   (select db user :cost-center {}))))
          exist-center-count (count @cost-center-name->id)
          invoices-by-code (->> (select db user :invoice {})
                             (map (juxt :ent/code identity))
                             (into {}))
          header (first lines)
          tx-data
          (mapcat
           (fn [line]
             (let [row (zipmap header line)
                   supplier-name (get row "Firma")
                   cost-center-name (get row "Středisko")
                   code (get row "Číslo")
                   existing-supplier-id (get @supplier-name->id supplier-name)
                   new-supplier (when-not existing-supplier-id
                                  {:db/id (d/tempid :db.part/ma)
                                   :ent/type :supplier
                                   :ent/title supplier-name})
                   existing-cost-center-id (get @cost-center-name->id cost-center-name)
                   new-cost-center (when-not existing-cost-center-id
                                     {:db/id (d/tempid :db.part/ma)
                                      :ent/type :cost-center
                                      :ent/title cost-center-name})
                   csv-inv (try
                             {:ent/type :invoice
                              :ent/code code
                              :ent/title code
                              :invoice/variable-symbol (Long. (get row "Varsym"))
                              :ent/date (let [s (get row "Datum")]
                                              (cljc.util/str-to-date (subs s 0 (- (count s) 8))))
                              :invoice/due-date (let [s (get row "Splatno")]
                                                  (cljc.util/str-to-date (subs s 0 (- (count s) 8))))
                              :invoice/price (parse-bigdec-kc (get row "Celkem"))
                              :invoice/paid (.subtract (parse-bigdec-kc (get row "Celkem")) (parse-bigdec-kc (get row "K likvidaci")))
                              :ent/supplier {:db/id (or existing-supplier-id (:db/id new-supplier))}
                              :ent/cost-center {:db/id (or existing-cost-center-id (:db/id new-cost-center))}
                              :invoice/text (get row "Text")}
                             (catch Exception e
                               (timbre/error e row)))
                   old-inv (get invoices-by-code code)
                   tx-data-fn #(cond-> [%]
                                 new-supplier
                                 (conj new-supplier)
                                 new-cost-center
                                 (conj new-cost-center))]
               (when (re-find #"^Fa \d+" code)
                 (when new-supplier
                   (swap! supplier-name->id assoc supplier-name (:db/id new-supplier)))
                 (when new-cost-center
                   (swap! cost-center-name->id assoc cost-center-name (:db/id new-cost-center)))
                 (if-not old-inv
                   (tx-data-fn (merge csv-inv {:db/id (d/tempid :db.part/ma)
                                               :invoice/checked false})) ;;nova faktura
                   (let [[only-in-csv only-in-db _] (data/diff csv-inv old-inv)]
                     (when only-in-csv
                       (tx-data-fn (cond-> (assoc only-in-csv :db/id (:db/id old-inv))
                                     (not-empty (dissoc only-in-csv :invoice/paid :invoice/price :invoice/variable-symbol
                                                        :ent/cost-center :invoice/text :ent/date :invoice/due-date))
                                     (assoc :invoice/checked false)))))))))
           (rest lines))]
      (when user
        (common-db/transact conn (:db/id user) tx-data))
      (let [supp-count (- (count @supplier-name->id) exist-supp-count)
            center-count (- (count @cost-center-name->id) exist-center-count)]
        (timbre/info "Naimportovano" supp-count  "dodavatelu," center-count  "stredisek a "
                     (- (count tx-data) supp-count center-count) "faktur"))
      (if user
        {:status "Ok"}
        tx-data))))

(defmethod bulk-import [:invoice :server-files]
  [conn user ent-type opts _]
  (timbre/info "Prirazeni souboru k fakturam")
  (let [db (d/db conn)
        import-dir (io/file "import-files")
        files (file-seq import-dir)]
    ;; vypis adresare -> nazev souboru -> nazev faktury
    (doseq [file files]
      (when-not (.isDirectory file)
        (let [filename (.getName file)
              invoice-code (str "Fa " (subs filename 0 (- (count filename) 4)))
              invoice (first (select db user :invoice {:ent/code invoice-code}))
              tempfile {:filename filename
                        :size (.length file)
                        :tempfile file
                        :content-type "application/pdf"}]
          ;;(timbre/debug filename "," invoice-code "," invoice "," file)
          (timbre/info "Prirazuji soubor faktury " invoice-code )
          (try
            (file-service/save-file! conn user tempfile :invoice (:db/id invoice))
            (.delete file)
            (catch Exception e
              (timbre/warn "Nepodarilo se najit fakturu pro kod " invoice-code))))))))

(defn entity-history [db ent-id]
  (->>
   (d/q '[:find ?tx ?a ?v ?added
          :in $ ?e
          :where
          [?e ?a ?v ?tx ?added]]
        (d/history db)
        ent-id)
   (map (fn [[tx a v added?]]
          {:tx (d/pull db '[*] tx)
           :a (d/ident db a)
           :v (if-not (integer? v)
                v
                (or (:ent/title (d/pull db '[:ent/title] v)) v))
           :added? added?}))
   (sort-by last)))

(defn delete-recursively [fname]
  (let [func (fn [func f]
               (when (.isDirectory f)
                 (doseq [f2 (.listFiles f)]
                   (func func f2)))
               (clojure.java.io/delete-file f))]
    (try
      (func func (clojure.java.io/file fname))
      (catch IOException e
        nil))))

(def HTML_HEADER "<html lang=\"cs\"><head><meta charset=\"UTF-8\"><link href=\"../bootstrap.css\" rel=\"stylesheet\"><link href=\"../site.css\" rel=\"stylesheet\"></head><body><div class=\"container-fluid\"><nav class=\"navbar navbar-default\"><div class=\"navbar-header\"><img src=\"../logo.svg\" alt=\"Masyst\" /></div></nav></div><div class=\"container\"> ")

(def HTML_FOOTER "</div></div></body></html>")

(defn ebs-handover-export [conn user {:keys [name ids]}]
  (let [name (if (str/blank? name) "noname" name)
        dir (str "/var/www/masyst/exports/" name "/")
        index-file (str dir "index.html")
        items (d/pull-many (d/db conn) '[* {:ebs/code-ref [*]
                                            :file/_parent [*]}]
                           ids)
        trs (->> items
                 (map-indexed (fn fn1 [idx item]
                                (str "<tr><td><a href=\"detail" idx ".html\">" (-> item :ebs/code-ref :ebs/code) " " (:ent/title item) "</a></td></tr>"))))]
    (delete-recursively dir)
    (io/make-parents index-file)
    (str/join "\n")
    (->>
     (str HTML_HEADER "<table class=\"table tree-table table-hover table-striped\"><thead><tr><th>Název</th></tr></thead><tbody>"
          (str/join "\n" trs)
          "</tbody></table>" HTML_FOOTER)
     (spit index-file))
    (->> items
         (map-indexed vector)
         (run! (fn fn2 [[idx item]]
                 (->>
                  (str HTML_HEADER "<div><h3>" (-> item :ebs/code-ref :ebs/code) " " (:ent/title item) "</h3>"
                       "<label>Struktura</label><p>" (-> item :ebs/code-ref :ebs/code) " " (-> item :ebs/code-ref :ent/title) "</p>"
                       "<label>Název</label><p>" (:ent/title item) "</p>"
                       "<label>Poznámka</label><p>" (:ent/annotation item) "</p></br>"
                       "<div class=\"panel-group\"><table class=\"table tree-table table-hover table-striped\"><thead><tr><th>Soubory</th></tr></thead><tbody>"
                       (->> (:file/_parent item)
                            (map #(str "<tr><td><a href=\"" (:db/id %) "-" (:file/server-name %) "\">" (:file/orig-name %) "</a></td></tr>"))
                            (str/join "\n"))
                       "</tbody></table></div></div>" HTML_FOOTER)
                  (spit (str dir "detail" idx ".html")))
                 (->> (:file/_parent item)
                      (run! #(let [link (Paths/get dir (into-array [(str (:db/id %) "-" (:file/server-name %))]))
                                   target (Paths/get "/home/masyst/uploads/masyst/ebs-handover/" (into-array [(str (:db/id %) "-" (:file/server-name %))]))]
                               (println link)
                               (println target)
                               (Files/createSymbolicLink link target (make-array java.nio.file.attribute.FileAttribute 0))))))))) nil)
