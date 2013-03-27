(ns awl-tour-2013.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.handler]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [awl-tour-2013.template :refer [main-tml]]
            [shoreleave.middleware.rpc :refer [defremote wrap-rpc]]
            [awl-tour-2013.gps :as gps])
  (:import [java.io.File]
           [java.security KeyStore]))


(defremote ^{:remote-name :get-coords} remote-get-coords []
  (-> (map #(dissoc % :_id) (gps/get-coords)) vec str))

(defroutes app-routes
  (GET "/" [] (main-tml) #_(main-tml (java.io.File. "resources/public/main.html")))
  (route/files "/" {:root "resources/public"})
  (route/not-found "Not Found"))

(def app
  (-> app-routes (wrap-rpc) (compojure.handler/site)))

(defn -main [port]
  (gps/connect-db)
  (run-jetty app {:port (Integer. port)}))