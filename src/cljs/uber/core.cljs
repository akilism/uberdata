(ns uber.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<! chan put!] :as async]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Uber Data!"}))

(defn main []
 (println (:text @app-state))
 (let [event-source (js/EventSource. "/pickups")
       event-chan (chan)]
      (set! (.-onmessage js/event-source) #(put! event-chan %))
      (go
        (while true
          (let [sse (<! event-chan)]
            (println (js->clj (.parse js/JSON (.-data sse))))
            )))))
