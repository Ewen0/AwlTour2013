(ns awl-tour-2013.goog-map
  (:require [domina.css :refer [sel]]
            [domina :refer [single-node set-text! detach!] :as domina]
            [com.ewen.utils-cljs.utils :refer [add-load-event]]
            [shoreleave.remotes.http-rpc :as rpc]
            [cljs.reader :refer [read-string]]
            [com.ewen.flapjax-cljs :refer [timerB insertDomB 
                                           insertValueB liftB
                                           receiverE sendEvent
                                           mapE changes valueNow
                                           extractValueB]]
            [F]
            [goog.net.WebSocket :as websocket]
            [goog.net.WebSocket.EventType :as websocket-event]
            [goog.net.WebSocket.MessageEvent :as websocket-message]
            [goog.events.EventHandler]))

(defn filterE
  [pred streamE]
  (.filterE streamE pred))


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


;;Panoramio
(def panoramio-layer (google.maps.panoramio.PanoramioLayer.))
(.setMap panoramio-layer map-obj)
(.setUserId panoramio-layer "7728826")
#_(.setTag panoramio-layer "awl-tour-2013")





;Dates

(def months ["janvier" "février" "mars" "avril" "mai" "juin" "juillet" "août" "septembre" "octobre" "novembre" "décembre"])
(def days ["Dimanche" "Lundi" "Mardi" "Mercredi" "Jeudi" "Vendredi" "Samedi"])

(defn format-digits [digit]
  (.slice (str "0" digit) -2))

(defn format-time [time]
  (let [time (-> time .getTime (js/Date.))
        day (->> time .getDay (get days))
        month (->> time .getMonth (get months))]
    (str day " " (.getDate time) " " month ", " 
         (-> (.getHours time) format-digits) ":"
         (-> (.getMinutes time) format-digits))))




;;; Distance

(defn format-distance [dist]
  (str "Distance parcourue : " (Math/floor dist) " kilomètres"))

;;;; Speed

(defn format-instant-speed [speed]
  (str "Vitesse instantanée : " (Math/round speed) " km/h"))

(defn format-average-speed [speed]
  (str "Vitesse moyenne : " (Math/round speed) " km/h"))




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










(rpc/remote-callback :get-data []
                     #(let [data (->> % read-string)
                            coords (filter :coord/lat data)
                            dist (-> (filter :coord/distance data) first)
                            instant-speed (-> (filter 
                                               (fn [in] (= :coord/instant-speed-id (:db/ident in)))
                                               data) 
                                              first)
                            average-speed (-> (filter 
                                               (fn [in] (= :coord/average-speed-id (:db/ident in)))
                                               data) 
                                              first)
                            coords (sort (fn [coord1 coord2] 
                                           (compare (-> coord1 :coord/orig-tx-inst) 
                                                    (-> coord2 :coord/orig-tx-inst)))
                                         coords)
                            coords (vec coords)]
                        (.log js/console (str data))
                        (reset! maps-coords coords)
                        (set-text! (sel "#distance") 
                                   (format-distance (:coord/distance dist)))
                        (set-text! (sel "#instant-speed") 
                                   (format-instant-speed (:coord/speed instant-speed)))
                        (set-text! (sel "#average-speed") 
                                   (format-average-speed (:coord/speed average-speed)))))





;;;;;; Countdown



(defn floor-B [n]
  (liftB js/Math.floor n))

(def time-second 1000)
(def time-minute (* time-second 60))
(def time-hour (* time-minute 60))
(def time-day (* time-hour 24))

(def start-date-lille 1372399200000) ;28 juin 8h00 

#_(set! start-date-lille (-> (js/Date.) .getTime (+ 5000)))

(def start-date-bezons 1372671000000) ;1 juillet 11h30
(def start-date-blois 1372842000000) ;3 juillet 11h00
(def start-date-lyon 1373274000000) ;8 juillet 11h00

(def start-date (atom nil))

(defn switch-countdown-div [in-key]
  (cond (= :lille in-key) (do (reset! start-date start-date-lille)
                              (set-text! (sel "#countdown-title") "Départ de Lille"))
        (= :bezons in-key) (do (reset! start-date start-date-bezons)
                               (set-text! (sel "#countdown-title") "Arrivée à Bezons"))
        (= :blois in-key) (do (reset! start-date start-date-blois)
                              (set-text! (sel "#countdown-title") "Arrivée à Blois"))
        (= :lyon in-key) (do (reset! start-date start-date-lyon)
                             (set-text! (sel "#countdown-title") "Arrivée à Lyon"))
        (= :stop in-key) (detach! (sel "#countdown-container"))))

(let [now (-> (js/Date.) .getTime)]
  (cond (< now start-date-lille) (switch-countdown-div :lille)
        (< now start-date-bezons) (switch-countdown-div :bezons)
        (< now start-date-blois) (switch-countdown-div :blois)
        (< now start-date-lyon) (switch-countdown-div :lyon)))

(when (nil? @start-date) (detach! (sel "#countdown-container")))

(when-not (nil? @start-date)

  (def start-date-B (extractValueB start-date))

  (def countdown (-> "#countdown" sel single-node))
  (def c-timer (liftB - start-date-B (timerB 1000)))
  (def c-days (-> (liftB / c-timer time-day) floor-B))
  (def time-c-days (liftB * c-days time-day))
  (def c-hours (-> (liftB / (liftB - c-timer time-c-days) time-hour) floor-B))
  (def time-c-hours (liftB * c-hours time-hour))
  (def c-minutes (-> (liftB / (liftB - c-timer time-c-days time-c-hours) time-minute) floor-B))
  (def time-c-minutes (liftB * c-minutes time-minute))
  (def c-seconds (-> (liftB / (liftB - c-timer time-c-days time-c-hours time-c-minutes) time-second) floor-B))

  (def countdown-str (liftB str 
                            c-days " jours " 
                            c-hours " heures " 
                            c-minutes " minutes " 
                            c-seconds " secondes"))

  (F/insertDomB countdown-str countdown)

  (def switch-countdown (->> (changes c-timer) 
                             (filterE #(= 0 (.floor js/Math (/ % 1001)))) 
                             (mapE #(cond 
                                     (= start-date-lille @start-date) 
                                     :bezons
                                     (= start-date-bezons @start-date)
                                     :blois
                                     (= start-date-blois @start-date)
                                     :lyon
                                     (= start-date-lyon @start-date)
                                     :stop))))


(mapE switch-countdown-div
      switch-countdown))









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






  (defn handle-msg [data-str]
    (.log js/console (.-message data-str))
    (let [data (->> (.-message data-str) read-string)]
      (cond (and (vector? data) (:coord/lat (first data)))
            (swap! maps-coords #(-> % filter-tmp-coords 
                                    (concat data) 
                                    vec))
            (:coord/distance data)
            (set-text! (sel "#distance") (format-distance (:coord/distance data)))
            (= :coord/instant-speed-id (:db/ident data))
            (set-text! (sel "#instant-speed") 
                       (format-instant-speed (:coord/speed data)))
            (= :coord/average-speed-id (:db/ident data))
            (set-text! (sel "#average-speed") 
                       (format-average-speed (:coord/speed data))))))

  (configure soc 
             #(.log js/console "opened")
             handle-msg
             #(.log js/console "error")
             #(.log js/console "closed"))

  #_(connect! soc "ws://www.awl-tour-2013.com/ws")
  (connect! soc "ws://localhost:3000/ws")
  #_(emit! soc "msg")) 



