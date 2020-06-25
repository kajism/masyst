(ns masyst.system
  (:require [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [duct.component.endpoint :refer [endpoint-component]]
            [duct.component.handler :refer [handler-component]]
            [duct.middleware.not-found :refer [wrap-not-found]]
            [duct.middleware.route-aliases :refer [wrap-route-aliases]]
            [environ.core :refer [env]]
            [masyst.component.datomic :refer [datomic]]
            [masyst.component.nrepl-server :refer [nrepl-server]]
            [masyst.endpoint.api :as api :refer [api-endpoint]]
            [meta-merge.core :refer [meta-merge]]
            [ring.component.jetty :refer [jetty-server]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.session-timeout :refer [wrap-absolute-session-timeout wrap-idle-session-timeout]]
            [ring.middleware.session.cookie :as cookie]
            [ring.middleware.session.memory :as mem]
            [ring.middleware.webjars :refer [wrap-webjars]]
            [ring.util.response :as response]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :refer [rotor-appender]]
            [taoensso.timbre.appenders.core :refer [println-appender]]))

(defn wrap-exceptions [handler api-pattern]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (timbre/error e)
        (if-not (re-find api-pattern (:uri request))
          (throw e)
          {:status 500
           :headers {"Content-Type" "text/plain;charset=utf-8"}
           :body (.getMessage e)})))))

(def access-denied-response {:status 403
                             :headers {"Content-Type" "text/plain;charset=utf-8"}
                             :body "Přístup odmítnut. Aktualizujte stránku a přihlašte se."})

(def login-redirect (response/redirect "/login"))

(defn wrap-auth [handler api-pattern]
  (fn [request]
    (let [user (get-in request [:session :user])
          login? (= "/login" (:uri request))
          api-call? (re-find api-pattern (:uri request))]
      (if (or user login?)
        (cond-> (handler request)
          (and login? (= :get (:request-method request))) ;; to disable session timeout middlewares
          (assoc :session nil))
        (if api-call?
          access-denied-response
          login-redirect)))))

(def base-config
  {:app {:middleware [wrap-restful-format
                      [wrap-exceptions :api-routes-pattern]
                      [wrap-not-found :not-found]
                      [wrap-webjars]
                      [wrap-absolute-session-timeout :absolute-session-timeout]
                      [wrap-idle-session-timeout :idle-session-timeout]
                      [wrap-auth :api-routes-pattern]
                      [wrap-defaults :defaults]
                      [wrap-route-aliases :aliases]]
         :api-routes-pattern #"/api"
         :absolute-session-timeout {:timeout-response login-redirect}
         :idle-session-timeout {:timeout-response login-redirect}
         :not-found  (io/resource "masyst/errors/404.html")
         :defaults   (meta-merge site-defaults {:static {:resources "masyst/public"}
                                                :security {:anti-forgery false}
                                                :proxy true
                                                :session {:store (if-not (:dev env)
                                                                   (mem/memory-store api/sessions-atom) ;;kvuli nemoznosti vice session 1 usera
                                                                   (cookie/cookie-store {:key "TroubskoZidenice"}))}})
         :aliases    {}}})

(defn new-system [config]
  (timbre/set-config!
   {:level     (if (:dev env) :debug :info)
    :appenders {:println (println-appender)
                :rotor (rotor-appender
                        {:path "log/masyst.log"
                         :max-size (* 2 1024 1024)
                         :backlog 10})}})
  (let [config (meta-merge base-config config)]
    (-> (component/system-map
         :nrepl (nrepl-server (:nrepl-port config))
         :datomic (datomic (get-in config [:datomic :uri]))
         :api (endpoint-component api-endpoint)
         :app  (handler-component (:app config))
         :http (jetty-server (:http config)))
        (component/system-using
         {:http [:app]
          :app  [:api]
          :api [:datomic]}))))

