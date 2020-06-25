(ns masyst.config
  (:require [clojure.string :as str]
            [environ.core :refer [env]]
            [masyst.cljc.common :as common]))

(def defaults
  {:http {:port 3002}
   :app {:absolute-session-timeout {:timeout common/absolute-session-timeout}
         :idle-session-timeout {:timeout common/idle-session-timeout}}})

(def environ
  {:http {:port (some-> env :port Integer.)}
   :datomic {:uri  (env :datomic-uri)}
   :postgres {:uri (env :database-url)}
   :nrepl-port (some-> env :nrepl-port Integer.)
   :app {:absolute-session-timeout {:timeout (some-> env :absolute-session-timeout Integer.)}
         :idle-session-timeout {:timeout (some-> env :idle-session-timeout Integer.)}}})

(def dbs (zipmap (str/split (or (:app-domains env) "") #"\s+")
                 (str/split (or (:app-dbs env) "") #"\s+")))
