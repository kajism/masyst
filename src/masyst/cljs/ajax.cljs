(ns masyst.cljs.ajax
  (:require [ajax.core :as ajax]
            [cljs.pprint :refer [pprint]]
            [cognitect.transit :as tran]
            [masyst.cljc.common :as common]
            [re-frame.core :as re-frame]))

(def logout-timer (atom common/idle-session-timeout))

(js/setInterval #(do (swap! logout-timer dec)
                     (when (neg? @logout-timer)
                       (set! js/window.location.href "/logout")))
                1000)

(defn server-call
  ([request-msg response-msg]
   (server-call request-msg nil response-msg))
  ([request-msg file response-msg]
   (server-call request-msg file response-msg nil))
  ([request-msg file response-msg rollback-db]
   (reset! logout-timer common/idle-session-timeout)
   (ajax/POST "/api"
       (merge
        {:headers {;;"Accept" "application/transit+json"
                   "X-CSRF-Token" (.. js/document (getElementById "__anti-forgery-token") -value)}
         :handler #(when response-msg (re-frame/dispatch (conj response-msg %)))
         :error-handler #(re-frame/dispatch [:set-msg :error
                                             (or (get-in % [:parse-error :original-text])
                                                 "Server je nedostupný")
                                             rollback-db])
         :response-format (ajax/transit-response-format)}
        (if file
          {:body
           (doto (js/FormData.)
             (.append "req-msg" (tran/write (tran/writer :json) request-msg))
             (.append "file" file))
           :format :raw}
          {:params {:req-msg request-msg}
           :format (ajax/transit-request-format)})))
   nil))
