(ns awl-tour-2013.gps
  (:require [monger.core :as mg]
            [monger.collection :refer [insert find-maps] :as mc]
            [cemerick.shoreleave.rpc :refer [defremote]])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]))

(defn connect-db []
  (let [^MongoOptions opts (mg/mongo-options :threads-allowed-to-block-for-connection-multiplier 300)
        ^ServerAddress sa (mg/server-address "127.0.0.1" 27017)]
    #_(mg/connect! sa opts)
    #_(mg/set-db! (mg/get-db "gps"))
    (mg/connect-via-uri! "mongodb://heroku:62966bc12b046e9525a0459b09b7cfec@linus.mongohq.com:10044/app14009883")))

(defn disconnect-db []
  (mg/disconnect!))

(defn put-coord [lat lng]
  (insert "coord" { :_id (ObjectId.) :lat lat :lng lng :timestamp (System/currentTimeMillis)}))

#_(insert "coord" { :_id (ObjectId.) :lat 48.961 :lng 2.194 :timestamp (System/currentTimeMillis)})
#_(insert "coord" { :_id (ObjectId.) :lat 48.961 :lng 3.194 :timestamp (System/currentTimeMillis)})
#_(mc/remove "coord")

(defn get-coords []
  (find-maps "coord"))

#_(defn push-coord [coord] 
  (let [normalized-coord (reduce #(assoc %1 %2 (Double/parseDouble (%1 %2)))
                                 coord
                                 ["lat" "lng" "timestamp"])]
    (insert "coord" (assoc normalized-coord :_id (ObjectId.)))))



(defn square [x]
  (Math/pow x 2))

(defn square-root [x]
  (Math/pow x 0.5))

(defn distance [coord1 coord2]
  (square-root (+ (square (- (:lat coord2) (:lat coord1)))
                  (square (- (:lng coord2) (:lng coord1))))))

(def min-distance 0.35)

(defn last-but-one [in-vec]
  (-> in-vec rseq rest vec rseq last))

(comment "normalized coord example"
          {:lat 50.61 :lng 3.05 :timestamp (System/currentTimeMillis)}
          {:lat 50.31 :lng 2.90 :timestamp (System/currentTimeMillis)})

(defn push-coord [coord] 
  (let [normalized-coord (reduce #(assoc %1 %2 (Double/parseDouble (%1 %2)))
                                 coord
                                 ["lat" "lng"])
        normalized-coord (update-in normalized-coord ["timestamp"] #(Long/parseLong %))
        normalized-coord (zipmap (map keyword (keys normalized-coord))
                                 (vals normalized-coord))
        coords (-> (find-maps "coord") vec)]
    (cond 
     (= 0 (count coords)) (insert "coord" (assoc normalized-coord :_id (ObjectId.)))
     (= 1 (count coords)) (insert "coord" (assoc normalized-coord :_id (ObjectId.)))
     :else 
     (let [actual-distance (distance (last-but-one coords) normalized-coord)]
       (if (> min-distance actual-distance)
         (mc/update "coord" {:_id (:_id (last coords))}
                    normalized-coord :multi false)
         (do (mc/update "coord" {:_id (:_id (last coords))}
                        normalized-coord :multi false)
             (insert "coord" (assoc normalized-coord :_id (ObjectId.)))))))))

#_(find-maps "coord")
#_(mc/remove "coord")
#_{"timestamp" "1364593504919" "lng" "2.9" "lat" "50.31"}
#_{:timestamp 1364593504919 :lng 2.9 :lat 50.31}

#_(mg/connect-via-uri! "mongodb://heroku:62966bc12b046e9525a0459b09b7cfec@linus.mongohq.com:10044/app14009883")



#_(push-coord {"lat" "50.61" "lng" "3.05" "timestamp" (str (System/currentTimeMillis))})
#_(push-coord {"lat" "50.39" "lng" "2.91" "timestamp" (str (System/currentTimeMillis))})
#_(push-coord {"lat" "50.316" "lng" "2.906" "timestamp" (str (System/currentTimeMillis))})
#_(push-coord {"lat" "50.18" "lng" "2.87" "timestamp" (str (System/currentTimeMillis))})
#_(push-coord {"lat" "50.08" "lng" "2.77" "timestamp" (str (System/currentTimeMillis))})
#_(push-coord {"lat" "49.70" "lng" "2.47" "timestamp" (str (System/currentTimeMillis))})

#_(map #(push-coord {"lat" (str %1) "lng" (str %2) "timestamp" (str (System/currentTimeMillis))}) 
       (range 48 49.60 0.10) (range 0.87 2.47 0.10))

#_(map #(push-coord {"lat" (str %1) "lng" (str %2) "timestamp" (str (System/currentTimeMillis))}) 
       (range 45 48 0.10) (range -3 0 0.10))



