(ns awl-tour-2013.handler
  (:require [compojure.core :refer [GET defroutes]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [awl-tour-2013.template :refer [main-tml]])
  (:import [java.io.File]
           [java.security KeyStore]))



(defroutes app-routes
  (GET "/" [] (main-tml) #_(main-tml (java.io.File. "resources/public/main.html")))
  (route/files "/" {:root "resources/public"})
  (route/not-found "Not Found"))

(def app
  (-> app-routes (handler/site)))

(defn -main [port]
  (run-jetty app {:port (Integer. port)}))