(ns awl-tour-2013.goog-map
  (:require [domina.css :refer [sel]]
            [domina :refer [single-node]]
            [com.ewen.utils-cljs.utils :refer [add-load-event]]
            [shoreleave.remotes.http-rpc :as rpc]
            [cljs.reader :refer [read-string]]))

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

(defn draw-path [coords]
  (let [maps-coords (to-coords coords)
        path (js/google.maps.Polyline. 
              (js-obj "path" maps-coords
                      "strokeColor" "#FF0000"
                      "strokeOpacity" 1.0
                      "strokeWeight" 2))]
    (.setMap path map-obj)))



;Markers

(defn make-markers [coords]
  (doseq [coord coords]
    (js/google.maps.Marker.
     (js-obj "position" (js/google.maps.LatLng. 
                         (:lat coord) (:lng coord))
             "map" map-obj
             "title" (str (:time coord))))))




(rpc/remote-callback :get-coords [] 
                     #(let [coords (->> % read-string)]
                        (draw-path coords)
                        (make-markers coords)))

 