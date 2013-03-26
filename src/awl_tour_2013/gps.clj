(ns awl-tour-2013.gps
  (:require [monger.core :as mg]
            [monger.collection :refer [insert find-maps]]
            [cemerick.shoreleave.rpc :refer [defremote]])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]))

(defn connect-db []
  (let [^MongoOptions opts (mg/mongo-options :threads-allowed-to-block-for-connection-multiplier 300)
        ^ServerAddress sa (mg/server-address "127.0.0.1" 27017)]
    (mg/connect! sa opts))
  (mg/set-db! (mg/get-db "gps")))

(defn disconnect-db []
  (mg/disconnect!))

(defn put-coord []
  (insert "coord" { :_id (ObjectId.) :lat 48.961 :lng 2.194 :timestamp (System/currentTimeMillis)}))

#_(insert "coord" { :_id (ObjectId.) :lat 48.961 :lng 3.194 :timestamp (System/currentTimeMillis)})

(defn get-coords []
  (find-maps "coord"))
