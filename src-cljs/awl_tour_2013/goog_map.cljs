(ns awl-tour-2013.goog-map
  (:require [domina.css :refer [sel]]
            [domina :refer [single-node]]
            [com.ewen.utils-cljs.utils :refer [add-load-event]]
            [shoreleave.remotes.http-rpc :as rpc]
            [cljs.reader :refer [read-string]]))

(def coords nil)

(def map-opts (js-obj "center" (google.maps/LatLng. 48 2.194)
                      "zoom" 6
                      "mapTypeId" js/google.maps.MapTypeId.ROADMAP))


(def map-obj (js/google.maps.Map.
              (-> (sel "#map-canvas") (single-node)) 
              map-opts))

(defn to-coords [coords-seq]
  (->> coords-seq
       (map #(js/google.maps.LatLng. (:lat %1) (:lng %1)))
       vec
       clj->js))

(defn draw-path [coords]
  (let [path (js/google.maps.Polyline. 
              (js-obj "path" coords
                      "strokeColor" "#FF0000"
                      "strokeOpacity" 1.0
                      "strokeWeight" 2))]
    (.setMap path map-obj)))



(defn square [x]
  (.pow js/Math x 2))

(defn root-square [x]
  (.pow js/Math x 0.5))

(defn distance [{lat1 :lat1 lng1 :lng :as coord1} {lat2 :lat2 lng2 :lng :as coord2}]
  (root-square (+ (square lat1) (square lat2))))

(rpc/remote-callback :get-coords [] #(->> % read-string (set! coords) to-coords draw-path))

 