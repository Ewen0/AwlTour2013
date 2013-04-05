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

#_ (prn (find-maps "coord"))
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









(comment 
"({:_id #<ObjectId 51561b1fe4b048077709bfed>, :lat 47.59026078, :lng 1.32785465, :timestamp 1364597541151} {:_id #<ObjectId 51561b5be4b048077709bfee>, :lat 47.5420816, :lng 0.97560692, :timestamp 1364631412817} {:_id #<ObjectId 51569f78e4b0efe0a0f4b5af>, :lat 47.33038158, :lng 0.69346009, :timestamp 1364632626368} {:_id #<ObjectId 5156a435e4b0efe0a0f4b5b0>, :lat 47.00803934, :lng 0.55675958, :timestamp 1364633894824} {:_id #<ObjectId 5156a929e4b0efe0a0f4b5b1>, :lat 46.70699055, :lng 0.37280597, :timestamp 1364635164785} {:_id #<ObjectId 5156ae20e4b0efe0a0f4b5b2>, :lat 46.48604326, :lng 0.073333, :timestamp 1364636373671} {:_id #<ObjectId 5156b2d7e4b0efe0a0f4b5b3>, :lat 46.3522021, :lng -0.2804638, :timestamp 1364637294581} {:_id #<ObjectId 5156b670e4b0efe0a0f4b5b4>, :lat 46.10691141, :lng -0.53533745, :timestamp 1364638265344} {:_id #<ObjectId 5156ba3be4b0efe0a0f4b5b5>, :lat 45.77477941, :lng -0.4174715, :timestamp 1364646687184} {:_id #<ObjectId 5156db23e4b0efe0a0f4b5b6>, :lat 45.62736183, :lng -0.09034563, :timestamp
 1364653968905} {:_id #<ObjectId 5156f792d628ecae0c1d7ace>, :lat 45.61944794, :lng 0.26746623, :timestamp 1364719806515} {:_id #<ObjectId 5157f8c1b30e44a83f6bdbdd>, :lat 45.96503682, :lng 0.20408409, :timestamp 1364732107745} {:_id #<ObjectId 515828cfb30e44a83f6bdbde>, :lat 46.29779427, :lng 0.37743895, :timestamp 1364743974624} {:_id #<ObjectId 5158572ab30e44a83f6bdbdf>, :lat 46.53535664, :lng 0.11598655, :timestamp 1364805703687} {:_id #<ObjectId 5159484ae4b0dc26e8ea928f>, :lat 46.76799494, :lng 0.47555086, :timestamp 1364809085867} {:_id #<ObjectId 5159557ee4b0dc26e8ea9290>, :lat 47.12798133, :lng 0.61221372, :timestamp 1364810294836} {:_id #<ObjectId 51595a38e4b0dc26e8ea9291>, :lat 47.4633539, :lng 0.7622068, :timestamp 1364811621839} {:_id #<ObjectId 51595f69e4b0dc26e8ea9292>, :lat 47.61352502, :lng 1.14812406, :timestamp 1364812586986} {:_id #<ObjectId 5159632ce4b0dc26e8ea9293>, :lat 47.62431253, :lng 1.34474003, :timestamp 1364813010819})")



