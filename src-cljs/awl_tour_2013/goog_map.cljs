(ns awl-tour-2013.goog-map
  (:require [domina.css :refer [sel]]
            [domina :refer [single-node]]
            [com.ewen.utils-cljs.utils :refer [add-load-event]]
            [shoreleave.remotes.http-rpc :as rpc]
            [cljs.reader :refer [read-string]]
            [goog.net.WebSocket :as websocket]
            [goog.net.WebSocket.EventType :as websocket-event]
            [goog.net.WebSocket.MessageEvent :as websocket-message]
            [goog.events]))

(def map-opts (js-obj "center" (google.maps/LatLng. 48 2.194)
                      "zoom" 6
                      "mapTypeId" js/google.maps.MapTypeId.ROADMAP))


(def map-obj (js/google.maps.Map.
              (-> (sel "#map-canvas") (single-node)) 
              map-opts))

(defn to-coords [coords-seq]
  (->> coords-seq
       (map #(js/google.maps.LatLng. (:coord/lat %1) (:coord/lng %1)))
       vec
       clj->js))

;Polylines

(def path (js/google.maps.Polyline.))

(defn draw-path [coords]
  (let [maps-coords (to-coords coords)
        new-path (js/google.maps.Polyline. 
                  (js-obj "path" maps-coords
                          "strokeColor" "#FF0000"
                          "strokeOpacity" 1.0
                          "strokeWeight" 2))]
    (.setMap path nil)
    (set! path new-path)
    (.setMap path map-obj)))



;Markers

(defn make-markers [coords]
  (doseq [coord coords]
    (js/google.maps.Marker.
     (js-obj "position" (js/google.maps.LatLng. 
                         (:lat coord) (:lng coord))
             "map" map-obj
             "title" (str (:time coord))))))



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

(connect! soc "ws://localhost:3000/ws")
#_(emit! soc "msg")




 