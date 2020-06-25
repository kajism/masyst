(ns cljs.user
  (:require [figwheel.client :as figwheel]
            [masyst.cljs.core]))

(js/console.info "Starting in development mode")

(enable-console-print!)

(figwheel/start {:websocket-url (str "ws://" (.-hostname js/location) ":3452/figwheel-ws")})
