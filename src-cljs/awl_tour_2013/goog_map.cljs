(ns awl-tour-2013.goog-map
  (:require [domina.css :refer [sel]]
            [domina :refer [single-node]]
            [com.ewen.utils-cljs.utils :refer [add-load-event]]
            [shoreleave.remotes.http-rpc :as rpc]
            [cljs.reader :refer [read-string]]))

(def map-opts (js-obj "center" (google.maps/LatLng. 48.961 2.194)
                      "zoom" 6
                      "mapTypeId" google.maps.MapTypeId/ROADMAP))


(def map-obj (google.maps/Map. 
               (-> (sel "#map-canvas") (single-node)) 
               map-opts))

(defn to-coords [coords-seq]
  (->> coords-seq 
       read-string 
       (map #(google.maps/LatLng. (:lat %1) (:lng %1)))
       vec
       clj->js))

(defn draw-path [coords]
  (let [path (google.maps/Polyline. 
              (js-obj "path" coords
                      "strokeColor" "#FF0000"
                      "strokeOpacity" 1.0
                      "strokeWeight" 2))]
    (.setMap path map-obj)))



(rpc/remote-callback :get-coords [] #(-> % (to-coords) (draw-path)))

 