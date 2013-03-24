(ns awl-tour-2013.goog-map
  (:require [domina.css :refer [sel]]
            [domina :refer [single-node]]
            [com.ewen.utils-cljs.utils :refer [add-load-event]]))

(def map-opts (js-obj "center" (google.maps/LatLng. 48.961 2.194)
                      "zoom" 6
                      "mapTypeId" google.maps.MapTypeId/ROADMAP))


(def map-obj (google.maps/Map. 
               (-> (sel "#map-canvas") (single-node)) 
               map-opts))

(def coords 
  (clj->js [(google.maps/LatLng. 48.961 2.194)
            (google.maps/LatLng. 49.961 3.194)]))

(def path (google.maps/Polyline. (js-obj "path" coords
                                         "strokeColor" "#FF0000"
                                         "strokeOpacity" 1.0
                                         "strokeWeight" 2)))

(.setMap path map-obj)

 