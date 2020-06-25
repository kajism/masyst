(ns masyst.endpoint.api
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [cognitect.transit :as tran]
            [compojure.coercions :refer [as-int]]
            [compojure.core :refer :all]
            [environ.core :refer [env]]
            [masyst.db.masyst-service :as masyst-service]
            [masyst.db.masyst-select]
            [masyst.db.user-service :as user-service]
            [masyst.endpoint.hiccup :as hiccup]
            [ring.util.response :as response]
            [datomic.api :as d]
            [taoensso.timbre :as timbre]
            [masyst.cljc.util :as cljc.util]
            [clojure.string :as str])
  (:import java.io.ByteArrayInputStream
           java.nio.charset.StandardCharsets
           java.util.UUID))

(defonce sessions-atom (atom {}))

(defn- find-existing-user-key [sessions user-login]
  (->> sessions
       (filter
        (fn [[k v]]
          (= user-login (get-in v [:user :user/login]))))
       ffirst))

(defn- dissoc-existing-user-session [user-login]
  (swap! sessions-atom dissoc (find-existing-user-key @sessions-atom user-login)))

(def pasman-reg-domain (if (:dev env) "127.0.0.1" "registrace.pasman.cz"))

(defn- pasman? [req]
  (= (:server-name req) pasman-reg-domain))

(defn- get-group-uuid [req]
  (when-let [uuid (get-in req [:cookies "group-uuid" :value])]
    (UUID/fromString uuid)))

(defn- select-pasman-regs [user db group-uuid]
  (cond
    (contains? (:-rights user) :pasman/select)
    (->>
     (masyst-service/select db user :pasman {})
     (sort-by :ent/from)
     (reverse))
    (contains? (:-rights user) :pasman/save)
    (->>
     (masyst-service/select db user :pasman {:pasman/group-uuid (or group-uuid "")})
     (sort-by (juxt :pasman/last-name :pasman/first-name)))))

(defn- pasman-validation [item]
  (cond-> {}
    (nil? (:ent/from item))
    (assoc :ent/from "Vyplňte datum začátku pobytu / Missing date From:")
    (string? (:ent/from item))
    (assoc :ent/from "Chybné datum začátku pobytu / Invalid date From:")
    (nil? (:ent/to item))
    (assoc :ent/to "Vyplňte datum konce pobytu / Missing date To:")
    (string? (:ent/to item))
    (assoc :ent/to "Chybné datum konce pobytu / Invalid date To:")
    (nil? (:pasman/cottage-no item))
    (assoc :pasman/cottage-no "Vyberte chatu / Select Cottage:")
    (str/blank? (:pasman/last-name item))
    (assoc :pasman/last-name "Vyplňte příjmeni / Missing Last name:")
    (str/blank? (:pasman/first-name item))
    (assoc :pasman/first-name "Vyplňte jméno / Missing First name:")
    (str/blank? (:pasman/passport-no item))
    (assoc :pasman/passport-no "Vyplňte číslo dokladu / Missing ID number:")
    (nil? (:pasman/age item))
    (assoc :pasman/age "Vyplňte věk / Missing age:")
    (str/blank? (:pasman/address item))
    (assoc :pasman/address "Vyplňte město / Missing city:")))

(defn api-endpoint [{{conns :conns} :datomic}]
  (routes
   (context "" {{user :user} :session server-name :server-name :as req}
     (GET "/" req
          (if (pasman? req)
            (hiccup/pasman-reg-page (select-pasman-regs user (d/db (conns server-name)) (get-group-uuid req))
                                    (:-rights user)
                                    {}
                                    {})
            (hiccup/cljs-landing-page)))
     (GET "/login" req (hiccup/login-page (pasman? req) nil))
     (POST "/login" [user-name pwd :as req]
       (try
         (when-let [user (user-service/login (conns server-name) user-name pwd)]
           ;;(dissoc-existing-user-session (:user/login user));; neumoznit vice session 1 usera
           (-> (response/redirect "/" :see-other)
               (assoc-in [:session :user] (assoc user :-server-name server-name))))
         (catch Exception e
           (hiccup/login-page (pasman? req) (.getMessage e)))))
     (GET "/logout" []
       (-> (response/redirect "/" :see-other)
           (assoc :session nil)))
     (POST "/pasman-reg-save" [date-from date-to cottage-no last-name first-name passport-no age city :as req]
           (let [group-uuid (get-group-uuid req)
                 posted {:ent/from (try
                                     (cljc.util/from-dMyyyy date-from)
                                     (catch IllegalArgumentException e
                                       date-from))
                         :ent/to (try
                                   (cljc.util/from-dMyyyy date-to)
                                   (catch IllegalArgumentException e
                                     date-to))
                         :pasman/cottage-no (cljc.util/parse-int cottage-no)
                         :pasman/last-name last-name
                         :pasman/first-name first-name
                         :pasman/passport-no passport-no
                         :pasman/age (cljc.util/parse-int age)
                         :pasman/address city
                         :pasman/group-uuid group-uuid}
                 errors (pasman-validation posted)]
             (if (seq errors)
               (hiccup/pasman-reg-page (select-pasman-regs user (d/db (conns server-name)) (get-group-uuid req))
                                       (:-rights user)
                                       posted
                                       errors)
               (let [saved (masyst-service/save! (conns server-name) user :pasman posted)]
                 (-> (response/redirect "/" :see-other)
                     (assoc-in [:cookies "group-uuid"] {:value (:pasman/group-uuid saved)
                                                        :max-age (* 365 24 60 60)}))))))
     (POST "/pasman-reg-delete" [id]
       (masyst-service/delete! (conns server-name) user :pasman (cljc.util/parse-int id))
       (response/redirect "/" :see-other))
     (POST "/pasman-reg-find" [find-no]
           (let [item (first (masyst-service/select (d/db (conns server-name)) user :pasman {:pasman/passport-no find-no}))]
             (cond-> (response/redirect "/" :see-other)
               (:pasman/group-uuid item)
               (assoc-in [:cookies "group-uuid"] {:value (:pasman/group-uuid item)
                                                  :max-age (* 365 24 60 60)})))))

   (context "/api" {{user :user} :session server-name :server-name}
     (POST "/" [req-msg file]
       (let [req-msg (if (string? req-msg)
                       (tran/read
                        (tran/reader
                         (ByteArrayInputStream. (.getBytes req-msg StandardCharsets/UTF_8)) :json))
                       req-msg) ;;osetreni :raw requestu (upload)
             [msg-id ?data] req-msg
             ent-type (keyword (namespace msg-id))
             op (name msg-id)
             conn (conns server-name)
             ;; conn (case ent-type
             ;;        (:crm-subj :crm-event-type :crm-event :crm-sale :crm-product :crm-project :country)
             ;;        crm-conn
             ;;        conn)
             ]
         (when-not ((:-rights user) msg-id)
           (throw (Exception. (str  "Not authorized:" msg-id))))
         (response/response
          (case op
            "select" (masyst-service/select (d/db conn) user ent-type ?data)
            "save" (masyst-service/save-with-file conn user ent-type ?data file)
            "delete" (masyst-service/delete! conn user ent-type ?data)
            "copy-to" (masyst-service/copy-to conn user ent-type (:ids ?data) (:target ?data))
            "bulk-import" (masyst-service/bulk-import conn user ent-type ?data (:tempfile file))
            "export" (masyst-service/ebs-handover-export conn user ?data)
            "auth" user
            "alive?" {:response "Ok"}
            "history" (masyst-service/entity-history (d/db conn) ?data)
            (do ;; default
              (println "Unknown msg-id: %s" msg-id)
              (str "Unknown msg-id: " msg-id))))))

     (GET "/status" req (str "Connection: " (if (count (masyst-service/select (d/db (conns server-name)) user :material {})) "Ok" "Fail")))

     (GET "/file/:id" [id :<< as-int]
       (when-let [{:keys [file-path content-type orig-name view-count]}
                  (masyst-service/select-file-path-type-name (d/db (conns server-name)) user id)]
         (when-not (#{"admin" "Monika"} (:user/roles user))
           (masyst-service/save! (conns server-name) user
                                 :file
                                 {:db/id id
                                  :file/view-count (inc (or view-count 0))}))
         (-> (response/file-response file-path {:root "."})
             (response/content-type content-type)
             (response/header "Content-Disposition" (str "inline; filename=" (str/replace orig-name #"[ ,;]" "_")))))))))
