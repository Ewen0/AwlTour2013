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
