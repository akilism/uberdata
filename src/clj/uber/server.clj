(ns uber.server
  (:require [uber.dev :refer [is-dev? inject-devmode-html browser-repl start-figwheel]]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [resources]]
            [net.cgrand.enlive-html :refer [deftemplate]]
            [net.cgrand.reload :refer [auto-reload]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [environ.core :refer [env]]
            [clojure.string :as str]
            [aleph.http :as http]
            [manifold.stream :as stream]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:gen-class))

; "Date/Time","Lat","Lon","Base"
; "5/1/2014 0:02:00",40.7521,-73.9914,"B02512"
; "5/1/2014 0:06:00",40.6965,-73.9715,"B02512"

(def csv-pickups (-> "pickup_data/uber-raw-data-may14.csv" slurp str/split-lines rest))
(defn pickup-transform
  [raw-pickup]
  (let [[date-time latitude longitude & _] (str/split raw-pickup #",")
        date-parts (str/split date-time #" ")]
    {:date (first date-parts)
     :time (nth date-parts 1)
     :coords [latitude ,longitude]}))

(defn event-transform
  ([data]
   (str "data: " data "\n\n"))
  ([event-type data]
   (str "event: " event-type "\ndata:" data "\n\n")))

(def format-pickup
  (comp
    event-transform
    json/write-str
    pickup-transform))

(def format-pickups
  (partial map format-pickup))


(defn server-sent-pickups [{:keys [params]}]
  (let [pickups (async/chan)
        csv-pickups (take 100 csv-pickups)]
    (async/go-loop [pickup (first csv-pickups) xs (rest csv-pickups)]
      (if (nil? pickup)
        (async/close! pickups)
        (let [_ (async/<! (async/timeout 150))]
          (async/put! pickups (format-pickup pickup))
          (recur (first xs) (rest xs)))))
    {:status 200
     :headers {"content-type" "text/event-stream; charset=utf-8"}
     :body (stream/->source pickups)}))

(deftemplate page (io/resource "index.html") []
  [:body] (if is-dev? inject-devmode-html identity))

(defroutes routes
  (resources "/")
  (resources "/react" {:root "react"})
  (GET "/pickups" [] server-sent-pickups)
  (GET "/*" req (page)))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (wrap-defaults #'routes api-defaults))
    (wrap-defaults routes api-defaults)))

(defn run-web-server [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (println (format "Starting web server on port %d." port))
    (http/start-server http-handler {:port port :join? false})))

(defn run-auto-reload [& [port]]
  (auto-reload *ns*)
  (start-figwheel))

(defn run [& [port]]
  (when is-dev?
    (run-auto-reload))
  (run-web-server port))

(defn -main [& [port]]
  (run port))
