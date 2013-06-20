(ns awl-tour-2013.handler
  (:require [compojure.core :refer [GET POST ANY defroutes]]
            [compojure.handler]
            [compojure.route :as route]
            [org.httpkit.server :refer [run-server with-channel 
                                        on-close on-receive
                                        send!]]
            [awl-tour-2013.template :refer [main-tml]]
            [shoreleave.middleware.rpc :refer [defremote wrap-rpc]]
            [awl-tour-2013.gps :as gps]
            [lamina.core :refer [map* fork close]])
  (:import [java.io.File]
           [java.security KeyStore]))


(defremote ^{:remote-name :get-data} remote-get-data []
  (-> (gps/get-data) vec str))

(defn ws-handler [request]
  (with-channel request channel
    (let [cc-min-dist (fork gps/cc-min-dist)
          cc-coord (fork gps/cc-coord)
          cc-dist (fork gps/cc-dist)
          cc-instant-speed (fork gps/cc-instant-speed)
          cc-average-speed (fork gps/cc-average-speed)]
      (map* #(->> % vec str (send! channel)) 
            cc-min-dist)
      (map* #(->> % vec str (send! channel)) 
            cc-coord)
      (map* #(->> % str (send! channel)) 
            cc-dist)
      (map* #(->> % str (send! channel)) 
            cc-instant-speed)
      (map* #(->> % str (send! channel)) 
            cc-average-speed)
      (on-close channel (fn [status] 
                          (close cc-min-dist)
                          (close cc-coord) 
                          (close cc-dist)
                          (close cc-instant-speed)
                          (close cc-average-speed)
                          #_(println "channel closed: " status)))
      (on-receive channel (fn [data] ;; echo it back
                            (send! channel data))))))


(defroutes app-routes
  (GET "/" [] (main-tml) #_(main-tml (java.io.File. "resources/public/main.html")))
  (ANY "/push-coord" {params :form-params} (do (gps/push-coord params) (str "")))
  (ANY "/ws" {:as req} (ws-handler req))
  (route/files "/static" {:root "resources/public"})
  (route/not-found "Not Found"))

(def app
  (-> app-routes (wrap-rpc) (compojure.handler/site)))

(defn -main [port]
  (run-server app {:port (Integer. port)}))