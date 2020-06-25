(ns masyst.db.file-service
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [image-resizer.core :as resizer-core]
            [image-resizer.format :as resizer-format]
            [masyst.config :as config]
            [masyst.cljc.tools :as tools]
            [masyst.db.common-database :as common-db]))

(defn- upload-dir []
  (or (:upload-dir env) "./uploads/"))

(defn- upload-file-path [server-name file]
  (str (upload-dir)
       (get config/dbs server-name) "/"
       (name (-> file :file/parent :ent/type))
       "/" (:db/id file) "-" (:file/server-name file)))

(defn select-file-path-type-name [db user id]
  (let [file (first (d/q '[:find (pull ?e [* {:file/parent [:ent/type]}]) ?created
                           :in $ ?e
                           :where
                           [?e :ent/type :file ?tx]
                           [?tx :db/txInstant ?created]]
                         db
                         id))
        file (assoc (first file) :-created (second file))
        file-path (upload-file-path (:-server-name user) file)]
    (when file
      (when-not ((:-rights user) (keyword (name (-> file :file/parent :ent/type)) "select"))
        (throw (Exception. "Not authorized")))
      {:file-path file-path
       :tempfile (io/file file-path)
       :orig-name (:file/orig-name file)
       :filename (:file/orig-name file)
       :size (:file/size file)
       :content-type (:file/content-type file)
       :view-count (:file/view-count file)
       :created (:-created file)})))

(defn save-file! [conn user file parent-type parent-id]
  (let [orig-name (:filename file)
        server-name (tools/sanitize-filename orig-name)]
    (when-let [file (when-not (= "null" file) file)]
      (let [file-ent {:file/orig-name orig-name
                      :file/server-name server-name
                      :file/parent {:db/id parent-id}
                      :file/size (:size file)
                      :file/content-type (:content-type file)}
            file-id (common-db/save! conn (:db/id user) :file file-ent)
            {:keys [file-path]} (select-file-path-type-name (d/db conn) user file-id)]
        (io/make-parents file-path)
        (cond
          (and (= parent-type :ebs-construction-diary)
               (= "image/" (subs (:content-type file) 0 6)))
          (resizer-format/as-file
           (resizer-core/resize (:tempfile file) 1024 1024)
           file-path
           :verbatim)
          :else
          (io/copy (:tempfile file) (io/file file-path)))
        (common-db/save! conn (:db/id user) :file (-> file-ent
                                                      (assoc :db/id file-id)
                                                      (assoc :file/size (.length (io/file file-path)))))))))

(defn delete-file [conn user id]
  (when-let [file (select-file-path-type-name (d/db conn) user id)]
    (when (< (- (System/currentTimeMillis) (-> file :created .getTime)) (tools/hour->millis 2))
      ;;soubor se maze z disku pouze kdyz neni starsi nez 2 hodiny, jinak zachovan kvuli historii
      (try
        (io/delete-file (:tempfile file))
        (catch java.io.IOException e
          (pprint e))))
    (common-db/transact conn (:db/id user) [[:db.fn/retractEntity id]])))

(defn assoc-file-created [db rows]
  (mapv (fn [row]
          (update row :file/_parent
                  #(mapv (fn [file]
                           (assoc file :-created (common-db/select-created-date db (:db/id file) :file/content-type)))
                         %)))
        rows))

