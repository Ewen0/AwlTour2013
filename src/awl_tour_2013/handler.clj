(ns awl-tour-2013.handler
  (:require [compojure.core :refer [GET POST ANY defroutes]]
            [compojure.handler]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server]]
            [awl-tour-2013.template :refer [main-tml]]
            [shoreleave.middleware.rpc :refer [defremote wrap-rpc]]
            [awl-tour-2013.gps :as gps])
  (:import [java.io.File]
           [java.security KeyStore]))


(defremote ^{:remote-name :get-coords} remote-get-coords []
  (-> (gps/get-coords) vec str))

(defroutes app-routes
  (GET "/" [] (main-tml) #_(main-tml (java.io.File. "resources/public/main.html")))
  (ANY "/push-coord" {params :form-params} (do (gps/push-coord params) (str "")))
  (route/files "/static" {:root "resources/public"})
  (route/not-found "Not Found"))

(def app
  (-> app-routes (wrap-rpc) (compojure.handler/site)))

(defn -main [command port]
  (run-server app {:port (Integer. port)}))