(ns awl-tour-2013.goog-map
  (:require [domina.css :refer [sel]]
            [domina :refer [single-node]]
            [com.ewen.utils-cljs.utils :refer [add-load-event]]
            [shoreleave.remotes.http-rpc :as rpc]
            [cljs.reader :refer [read-string]]
            [goog.net.WebSocket :as websocket]
            [goog.net.WebSocket.EventType :as websocket-event]
            [goog.net.WebSocket.MessageEvent :as websocket-message]
            [goog.events.EventHandler]))


(def map-opts (js-obj "center" (google.maps/LatLng. 48 2.194)
                      "zoom" 6
                      "mapTypeId" google.maps.MapTypeId/ROADMAP))


(def map-obj (google.maps/Map.
              (-> (sel "#map-canvas") (single-node))
              map-opts))

(defn to-coords [coords-seq]
  (->> coords-seq
       (map #(google.maps/LatLng. (:coord/lat %1) (:coord/lng %1)))
       vec
       clj->js))

;Polylines

(def path (google.maps/Polyline.))

(defn draw-path [coords]
  (let [maps-coords (to-coords coords)
        new-path (google.maps/Polyline. 
                  (js-obj "path" maps-coords
                          "strokeColor" "#FF0000"
                          "strokeOpacity" 1.0
                          "strokeWeight" 2))]
    (.setMap path nil)
    (set! path new-path)
    (.setMap path map-obj)
    coords))





;Dates

(def months ["janvier" "février" "mars" "avril" "mai" "juin" "juillet" "août" "septembre" "octobre" "novembre" "décembre"])
(def days ["Lundi" "Mardi" "Mercredi" "Jeudi" "Vendredi" "Samedi" "Dimanche"])

(defn format-time [time]
  (let [time (-> time .getTime (- 7200000) (js/Date.))
        day (->> time .getDay (get days))
        month (->> time .getMonth (get months))]
    (str day " " (.getDate time) " " month ", " 
         (.getHours time) ":" (.getMinutes time))))




;Markers

(defn make-marker [coord animate]
  (when (:min-dist coord)
    (google.maps/Marker.
     (js-obj "position" (google.maps/LatLng. 
                         (:coord/lat coord) (:coord/lng coord))
             "map" map-obj
             "title" (format-time (:coord/orig-tx-inst coord))
             "animation" (if animate google.maps.Animation/DROP nil)))))

(defn make-markers [coords]
  (when-not (empty? coords)
    (doseq [coord (pop coords)]
      (make-marker coord false))
    (make-marker (last coords) true)))



;Coords

(def maps-coords (atom []))

(add-watch maps-coords nil 
           (fn [_ _ _ new] 
             (-> new draw-path make-markers)))

(defn filter-tmp-coords [maps-coords]
  (filter #(:min-dist %) maps-coords))










(rpc/remote-callback :get-coords [] 
                     #(let [coords (->> % read-string)]
                        (reset! maps-coords coords)
                        (.log js/console (str coords))
                        #_(draw-path coords)
                        #_(make-markers coords)))


;;;;;;;;;;;;;;;;;;;;;
;;WEB-SOCKETS;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;


(when (.-MozWebSocket js/window) (set! (.-WebSocket js/window) (.-MozWebSocket js/window)))

(when (.-WebSocket js/window) 

  (def soc (goog.net.WebSocket.))

  (defn configure
    "Configures WebSocket"
    ([soc opened message]
       (configure soc opened message nil))
    ([soc opened message error]
       (configure soc opened message error nil))
    ([soc opened message error closed]
       (let [handler (goog.events/EventHandler.)]
         (.listen handler soc websocket-event/OPENED opened)
         (.listen handler soc websocket-event/MESSAGE message)
         (when error
           (.listen handler soc websocket-event/ERROR error))
         (when closed
           (.listen handler soc websocket-event/CLOSED closed))
         soc)))


  (defn connect!
    "Connects WebSocket"
    [socket url]
    (try
      (.open socket url)
      socket
      (catch js/Error e
        (.log js/console "No WebSocket supported, get a decent browser."))))

  (defn close!
    "Closes WebSocket"
    [socket]
    (.close socket))

  (defn emit! [socket msg]
    (.send socket msg))






  (defn handle-msg [coords-str]
    #_(.log js/console (.-message coords-str))
    (let [coords (->> (.-message coords-str) read-string)]
      (swap! maps-coords #(-> % filter-tmp-coords 
                              (concat coords) 
                              vec))))

  (configure soc 
             #(.log js/console "opened")
             handle-msg
             #(.log js/console "error")
             #(.log js/console "closed"))

  #_(connect! soc "ws://www.awl-tour-2013.com/ws")
  (connect! soc "ws://localhost:3000/ws")
  #_(emit! soc "msg"))  