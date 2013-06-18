(ns awl-tour-2013.gps
  (:require [cemerick.shoreleave.rpc :refer [defremote]]
            [datomic.api :as dat]
            [lamina.core :refer [channel permanent-channel map* 
                                 filter* enqueue on-realized close 
                                 ground siphon] :as lamina]
            [lamina.executor :refer [task]]
            [clojure.set :refer [union]]))

(dat/create-database "datomic:free://localhost:4334/coords")
#_(dat/delete-database "datomic:free://localhost:4334/coords")

(def conn (dat/connect "datomic:free://localhost:4334/coords"))
#_(dat/release conn)


(when (empty? (dat/q '[:find ?id :where [?id :db/ident :coord/lat]] (dat/db conn)))
  (dat/transact conn [{:db/id #db/id[:db.part/db]
                       :db/ident :coord/lat
                       :db/valueType :db.type/double
                       :db/cardinality :db.cardinality/one
                       :db/doc "Latitude coordinate"
                       :db.install/_attribute :db.part/db
                       :db/noHistory false}

                      {:db/id #db/id[:db.part/db]
                       :db/ident :coord/lng
                       :db/valueType :db.type/double
                       :db/cardinality :db.cardinality/one
                       :db/doc "Longitude coordinate"
                       :db.install/_attribute :db.part/db
                       :db/noHistory false}]))

(when (empty? (dat/q '[:find ?id :where [?id :db/ident :coord/min-distance]] (dat/db conn)))
  (dat/transact conn [{:db/id #db/id[:db.part/db]
                         :db/ident :coord/min-distance
                         :db/valueType :db.type/double
                         :db/cardinality :db.cardinality/one
                         :db/doc "Coords having this attribute set respect a minimum distance interval between each other. The value of the minimum distance is the value of the attribute."
                         :db.install/_attribute :db.part/db}
                        {:db/id #db/id[:db.part/db]
                         :db/ident :coord/orig-tx-inst
                         :db/valueType :db.type/instant
                         :db/cardinality :db.cardinality/one
                         :db/doc "The transaction time the original coord was added to the DB."
                         :db.install/_attribute :db.part/db}]))

(when (empty? (dat/q '[:find ?id :where [?id :db/ident :coord/trans-type]] (dat/db conn)))
  (dat/transact conn [{:db/id #db/id[:db.part/db]
                       :db/ident :coord/trans-type
                       :db/valueType :db.type/string
                       :db/cardinality :db.cardinality/one
                       :db/doc "Metadata for a transaction that add a coord object. Usefull to distinguish between transactions that add coordinates with different values for the \"min-distance\" attribute."
                       :db.install/_attribute :db.part/db}]))


(when (empty? (dat/q '[:find ?id :where [?id :db/ident :coord/distance]] (dat/db conn)))
  (dat/transact conn [{:db/id #db/id[:db.part/db]
                       :db/ident :coord/distance
                       :db/valueType :db.type/double
                       :db/cardinality :db.cardinality/one
                       :db.install/_attribute :db.part/db}]))

(when (empty? (dat/q '[:find ?id :where [?id :db/ident :coord/speed]] (dat/db conn)))
  (dat/transact conn [{:db/id #db/id[:db.part/db]
                       :db/ident :coord/speed
                       :db/valueType :db.type/double
                       :db/cardinality :db.cardinality/one
                       :db.install/_attribute :db.part/db}]))

(when (nil? (-> (dat/entity (dat/db conn) :coord/coord-id) :db/id))
  (dat/transact conn [{:db/id #db/id[:db.part/user]
                       :db/ident :coord/coord-id}]))

(when (nil? (-> (dat/entity (dat/db conn) :coord/coord-min-dist-id) :db/id))
  (dat/transact conn [{:db/id #db/id[:db.part/user]
                       :db/ident :coord/coord-min-dist-id}]))

(when (nil? (-> (dat/entity (dat/db conn) :coord/dist-id) :db/id))
  (dat/transact conn [{:db/id #db/id[:db.part/user]
                       :db/ident :coord/dist-id}]))

(when (nil? (-> (dat/entity (dat/db conn) :coord/instant-speed-id) :db/id))
  (dat/transact conn [{:db/id #db/id[:db.part/user]
                       :db/ident :coord/instant-speed-id}]))






















(defn attr-missing? [db eid attr]
  (-> (dat/entity db eid) attr not))

(defn get-coord-id []
  (-> (dat/entity (dat/db conn) :coord/coord-id) :db/id))

(defn get-coord-id-min-dist [min-dist]
  (-> (dat/entity (dat/db conn) :coord/coord-min-dist-id) :db/id))









(def min-distance 0.35)

(defn square [x]
  (Math/pow x 2))

(defn square-root [x]
  (Math/pow x 0.5))

(defmulti distance #(into #{} (concat (keys %1) (keys %2))))

(defmethod distance #{:lng :lat :time} 
  [coord1 coord2]
  (square-root (+ (square (- (:lat coord2) (:lat coord1)))
                  (square (- (:lng coord2) (:lng coord1))))))

(defmethod distance #{:coord/lat :coord/lng 
                      :coord/min-distance 
                      :coord/orig-tx-inst :db/id}
  [coord1 coord2]
  (square-root (+ (square (- (:coord/lat coord2) 
                             (:coord/lat coord1)))
                  (square (- (:coord/lng coord2) 
                             (:coord/lng coord1))))))

(defmethod distance #{:coord/lat :coord/lng 
                      :coord/min-distance 
                      :coord/orig-tx-inst}
  [coord1 coord2]
  (square-root (+ (square (- (:coord/lat coord2) 
                             (:coord/lat coord1)))
                  (square (- (:coord/lng coord2) 
                             (:coord/lng coord1))))))

(defmethod distance #{:coord/lat :coord/lng 
                      :coord/orig-tx-inst}
  [coord1 coord2]
  (if (or (empty? coord1) (empty? coord2)) 0
      (square-root (+ (square (- (:coord/lat coord2) 
                                 (:coord/lat coord1)))
                      (square (- (:coord/lng coord2) 
                                 (:coord/lng coord1)))))))


(defn above-min-distance? [coord1 coord2]
  (or (and (nil? (:lng coord1)) (nil? (:lat coord1))
           (nil? (:coord/lng coord1)) (nil? (:coor/lng coord1))) 
      (and (nil? (:lng coord2)) (nil? (:lat coord2))
           (nil? (:coord/lng coord2)) (nil? (:coor/lng coord2)))
      (>= (distance coord1 coord2) min-distance)))

(defn rad [x]
  (/ (* x (. Math PI)) 180))

(defn real-distance [coord1 coord2]
  (if (or (empty? coord1) (empty? coord2)) 0
      (let [R 6371
            dLat (rad (- (:coord/lat coord2) (:coord/lat coord1)))
            dLong (rad (- (:coord/lng coord2) (:coord/lng coord1)))
            a (+ (* (Math/sin (/ dLat 2)) (Math/sin (/ dLat 2)))
                 (* (Math/cos (rad (:coord/lat coord1))) (Math/cos (rad (:coord/lat coord2)))
                    (Math/sin (/ dLong 2)) (Math/sin (/ dLong 2))))
            c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))
            d (* R c)]
        d)))




















(defn ent-history [db-value coord-id]
  (dat/q '[:find ?attr ?val ?time
           :in $ ?id
           :where
           [?id ?attr ?val ?tx true]
           [?tx :db/txInstant ?time]]
         (-> db-value dat/history) 
         coord-id))

(defn dat-result->coord-maps [dat-result]
  (let [dat-result->coord-map 
        (fn [init-map dat-result]
          (assoc init-map
            (->> (:attr dat-result) 
                 (dat/ident (dat/db conn))) 
            (:val dat-result) 
            :coord/orig-tx-inst (:time dat-result)))]
    (reduce dat-result->coord-map {} dat-result)))

(defn fill-missing-keys [vec-maps map]
  (conj vec-maps 
        (-> (last vec-maps) 
            (merge map))))

;Perform a datomic request on an entity history (returning a history composed by all the datums that have been added)
;Then build (using the datums retreived earlier) the successive entities as they are at the different points (transaction times) in history.
(defn coord-history [db-value coord-id] 
  (->> (ent-history db-value coord-id)
       (map #(zipmap [:attr :val :time] %))
       (group-by :time)
       (into (sorted-map))
       (map last)
       (map dat-result->coord-maps)
       (filter #(or (:coord/lat %) (:coord/lng %)))
       (reduce fill-missing-keys [])))





















(defonce tx-channel (permanent-channel))

(defn item-queue->channel [queue channel]
  (->> queue
      (.take)
      (enqueue channel)))

(defonce tx-channel-thread 
  (Thread. 
   (fn [] 
     (while true 
       (item-queue->channel 
        (dat/tx-report-queue conn)
        tx-channel)))))

(when-not (.isAlive tx-channel-thread)
  (.start tx-channel-thread))




(defn filter-coord-tx [trans-type tx-event]
  (let [tx-type-coord? '[:find ?e
                         :in $ ?trans-type [[?e ?a ?v _ _]]
                         :where
                         [?e ?a ?v]
                         [?e :coord/trans-type ?trans-type]]]
    (not-empty (dat/q 
                tx-type-coord?
                (:db-after tx-event) 
                trans-type
                (:tx-data tx-event)))))



(defn coord-min-dist-id [min-dist]
  (-> (dat/q 
       '[:find ?entid
         :in $ ?min-distance
         :where 
         [?entid :coord/min-distance ?min-distance]]  
       (dat/db conn) min-dist)
      first first))

(defn last-coord-min-dist []
  (let [coord-map (->> (dat/q 
                        '[:find ?entid ?lat ?lng ?time ?min-distance
                          :in $ ?min-distance
                          :where 
                          [?entid :coord/min-distance ?min-distance]
                          [?entid :coord/lat ?lat]
                          [?entid :coord/lng ?lng]
                          [?entid :coord/orig-tx-inst ?time]]  
                        (dat/db conn) min-distance)
                       (first)
                       (zipmap [:db/id :coord/lat :coord/lng 
                                :coord/orig-tx-inst 
                                :coord/min-distance]))]
    (if (empty? coord-map) 
      [{:coord/min-distance min-distance}] 
      [coord-map])))


(defn coords-after-last-min-dist [tx-event] 
  (let [time-last-coord-min-dist '[:find ?time
                                   :where [?e :coord/min-distance]
                                   [?e :coord/orig-tx-inst ?time]]
        time-last-coord-min-dist (dat/q 
                                  time-last-coord-min-dist
                                  (dat/db conn))]
    (if (empty? time-last-coord-min-dist)
      (coord-history (:db-after tx-event) (get-coord-id))
      (coord-history 
       (-> (:db-after tx-event) 
           (dat/since (-> time-last-coord-min-dist first first)))
       (get-coord-id)))))

(defn reduce-min-dist [res arg] 
  (let [updated-arg (merge (last res) arg)]
    (if (above-min-distance? updated-arg (last res)) 
      (conj res updated-arg) res)))

(defn add-coord-min-dist [in-map]
  (let [updated-map (if-not (find in-map :db/id) 
                      (assoc in-map :db/id 
                             (get-coord-id-min-dist min-distance))
                      in-map)]
    (dat/transact conn [{:db/id (dat/tempid :db.part/tx)
                         :coord/trans-type "coord-min-dist"} 
                        updated-map])))

(def cc (->> tx-channel 
             (filter* #(filter-coord-tx "coord" %)) 
             (map* coords-after-last-min-dist)
             (map* #(reduce reduce-min-dist (last-coord-min-dist) %))
             (map* #(-> % rest vec))
             (map* #(dorun (map add-coord-min-dist %)))
             #_(map* prn)))











(defn get-coord-added [tx-event]
  (union 
   (set (dat/q '[:find ?e ?time
                 :in $ [[?e _ _ ?tx ?added]]
                 :where [?e :coord/lat _ ?tx true]
                 [?tx :db/txInstant ?time]]
               (:db-after tx-event)
               (:tx-data tx-event)))
   (set (dat/q '[:find ?e ?time
                 :in $ [[?e _ _ ?tx ?added]]
                 :where [?e :coord/lng _ ?tx true]
                 [?tx :db/txInstant ?time]]
               (:db-after tx-event)
               (:tx-data tx-event)))))

(defn eid-min-dist-time->entity [[eid time]]
  (let [coord-min-dist (->> 
                        (dat/q '[:find ?lat ?lng ?orig-time
                                 :in $ ?eid
                                 :where [?eid :coord/lat ?lat]
                                 [?eid :coord/lng ?lng]
                                 [?eid :coord/orig-tx-inst ?orig-time]]
                               (-> (dat/db conn) 
                                   (dat/as-of time)) eid)
                        (first)
                        (zipmap [:coord/lat 
                                 :coord/lng 
                                 :coord/orig-tx-inst]))]
    (assoc coord-min-dist :min-dist true 
           :coord/min-distance 0.35)))



(def cc-min-dist (->> tx-channel 
                      (filter* #(filter-coord-tx "coord-min-dist" %))
                      (map* get-coord-added)
                      (map* #(map eid-min-dist-time->entity %))
                      #_(map* prn)))









(defn eid-time->entity [[eid time]]
  (let [coord-min-dist (->> 
                        (dat/q '[:find ?lat ?lng ?time
                                 :in $ ?eid ?time
                                 :where [?eid :coord/lat ?lat]
                                 [?eid :coord/lng ?lng]]
                               (-> (dat/db conn) 
                                   (dat/as-of time)) eid time)
                        (first)
                        (zipmap [:coord/lat 
                                 :coord/lng 
                                 :coord/orig-tx-inst]))]
    (assoc coord-min-dist :min-dist false)))

(def cc-coord (->> tx-channel 
                      (filter* #(filter-coord-tx "coord" %))
                      (map* get-coord-added)
                      (map* #(map eid-time->entity %))
                      #_(map* prn)))




#_(def cc-coord (channel))

#_(map* prn cc-coord)

#_(push-coord {"lat" "48.83" "lng" "2.35"})
#_(push-coord {"lat" "48.71" "lng" "2.19"})
#_(push-coord {"lat" "48.83" "lng" "6.35"})

(ground cc)
(ground cc-min-dist)
(ground cc-coord)





(def dist-id (-> (dat/entity (dat/db conn) :coord/dist-id) :db/id))

(defn maybe-get-dist-id [db]
  (-> (dat/q '[:find ?dist-id
               :where 
               [?dist-id :coord/distance]] 
             db) 
      ffirst))

(defn get-coords-without-dist [db]
  (if (nil? (maybe-get-dist-id db)) 
    (coord-history db (get-coord-id))
    (coord-history 
     (dat/since db 
                (-> (dat/entity db (maybe-get-dist-id db)) 
                    :coord/orig-tx-inst))
     (get-coord-id))))

(defn get-last-distance [db]
  (if (nil? (maybe-get-dist-id db)) 0
      (-> (dat/entity db (maybe-get-dist-id db)) :coord/distance)))

(defn get-last-distance-entity [db]
  (if (nil? (maybe-get-dist-id db)) []
      [(-> (dat/entity db (maybe-get-dist-id db)) dat/touch)]))

(defn get-last-coord-with-distance [db]
  (if (nil? (maybe-get-dist-id db)) {}
      (dat/touch (dat/entity
                  (dat/as-of db
                             (-> (dat/entity db (maybe-get-dist-id db)) 
                                 :coord/orig-tx-inst))
                  (get-coord-id)))))



(defn reduce-distance [[distances prev-coord db] next-coord]
  (let [last-dist (if (empty? distances) 0 
                      (:coord/distance (last distances)))]
    [(conj distances 
           {:coord/distance (+ last-dist (real-distance prev-coord next-coord))
            :coord/orig-tx-inst (:coord/orig-tx-inst next-coord)}) 
     next-coord db]))

(defn transact-dist [db {dist :coord/distance tx-inst :coord/orig-tx-inst}]
  (dat/transact conn [{:db/id #db/id[:db.part/tx]
                       :coord/trans-type "distance"}
                      {:db/id dist-id 
                       :coord/distance (.doubleValue dist) 
                       :coord/orig-tx-inst tx-inst}]))

(defn single-item-or-rest [in-seq]
  (cond (or (empty? in-seq) (nil? in-seq)) in-seq
        (= 1 (count in-seq)) in-seq
        :else (rest in-seq)))

(->> tx-channel 
     (filter* #(filter-coord-tx "coord" %))
     (map* #(reduce reduce-distance 
                    [(get-last-distance-entity (:db-after %)) 
                     (get-last-coord-with-distance (:db-after %)) (:db-after %)]
                    (get-coords-without-dist (:db-after %))))
     (map* #(dorun (map (partial transact-dist (last %)) (-> (first %) single-item-or-rest)))))

#_(push-coord {"lat" "48.83" "lng" "2.35"})
#_(push-coord {"lat" "48.71" "lng" "2.19"})
#_(push-coord {"lat" "48.83" "lng" "6.35"})


(defn get-dist-added [tx-event]
  (dat/q '[:find ?dist ?time
           :in $ [[?e _ _ ?tx ?added]]
           :where [?e :coord/distance ?dist ?tx true]
           [?e :coord/orig-tx-inst ?time ?tx true]]
         (:db-after tx-event)
         (:tx-data tx-event)))

(defn dist->entity [datums]
  (->> (first datums) (zipmap [:coord/distance
                               :coord/orig-tx-inst])))

(def cc-dist (->> tx-channel 
                  (filter* #(filter-coord-tx "distance" %))
                  (map* #(get-dist-added %))
                  (map* #(dist->entity %))
                  (filter* not-empty)))

(ground cc-dist)





(defn speed [dist1 dist2]
  (if (or (empty? dist1) (nil? dist1)) 0
      (Math/round (* (/ (- (:coord/distance dist2) 
                           (:coord/distance dist1))
                        (- (.getTime (:coord/orig-tx-inst dist2)) 
                           (.getTime (:coord/orig-tx-inst dist1))))
                     3600000))))

(defn speed-with-time [dist1 dist2]
    (if (or (empty? dist1) (nil? dist1)) 
      {:coord/speed 0 
       :coord/orig-tx-inst (:coord/orig-tx-inst dist2)}
      {:coord/speed (Math/round (* (/ (- (:coord/distance dist2) 
                                         (:coord/distance dist1))
                                      (- (.getTime (:coord/orig-tx-inst dist2)) 
                                         (.getTime (:coord/orig-tx-inst dist1))))
                                   3600000))
       :coord/orig-tx-inst (:coord/orig-tx-inst dist2)}))

(defn transact-instant-speed [{speed :coord/speed time :coord/orig-tx-inst}]
  (dat/transact conn [{:db/id #db/id[:db.part/tx]
                       :coord/trans-type "instant-speed"}
                      {:db/id :coord/instant-speed-id
                       :coord/speed (.doubleValue speed) 
                       :coord/orig-tx-inst time}]))

(->> tx-channel
     (filter* #(filter-coord-tx "distance" %))
     (map* #(speed-with-time (first (get-last-distance-entity (:db-before %)))
              (first (get-last-distance-entity (:db-after %)))))
     (map* transact-instant-speed))

(def cc-instant-speed (->> tx-channel 
                           (filter* #(filter-coord-tx "instant-speed" %))
                           (map* #(->> :coord/instant-speed-id 
                                       (dat/entity (:db-after %)) 
                                       dat/touch))
                           (map* #(into {} %))))


(ground cc-instant-speed)

#_(push-coord {"lat" "48.83" "lng" "2.35"})
#_(push-coord {"lat" "48.71" "lng" "2.19"})
#_(push-coord {"lat" "48.83" "lng" "6.35"})

#_{:db/id 17592186072102, :coord/orig-tx-inst #inst "2013-06-17T00:04:52.997-00:00", :db/ident :coord/dist-id, :coord/distance 7098.7}
#_{:db/id 17592186072102, :coord/orig-tx-inst #inst "2013-06-17T00:05:52.997-00:00", :db/ident :coord/dist-id, :coord/distance 7099.7}














(defn push-coord [{lat "lat" lng "lng"}]
  (let [lat (Double/parseDouble lat)
        lng (Double/parseDouble lng)]
    (dat/transact conn [{:db/id #db/id[:db.part/tx]
                         :coord/trans-type "coord"}
                        {:db/id (get-coord-id) 
                         :coord/lat lat 
                         :coord/lng lng}])))

(defn compare-coord [coord1 coord2]
  (if (and (= (:coord/lat coord1) (:coord/lat coord2)) 
           (= (:coord/lng coord1) (:coord/lng coord2)))
    true false))

(defn get-last-coord []
  (->> (dat/q 
        '[:find ?lat ?lng ?time 
          :where [?coord-id :coord/lat ?lat]
          [?coord-id :coord/lng ?lng]
          [?coord-id _ _ ?tx]
          [?tx :db/txInstant ?time]
          [(awl-tour-2013.gps/attr-missing? $ ?coord-id :coord/min-distance)]]
        (dat/db conn))
       (first)
       (zipmap [:coord/lat :coord/lng :coord/orig-tx-inst])))


(defn get-coords []
  (let [coord-min-dist (coord-history 
                        (dat/db conn) 
                        (get-coord-id-min-dist min-distance))
        coord-min-dist (map #(assoc % :min-dist true) coord-min-dist)
        last-coord (get-last-coord)
        last-coord (assoc last-coord :min-dist false)]
    (if-not (compare-coord (last coord-min-dist) last-coord)
      (conj coord-min-dist last-coord)
      (vec coord-min-dist))))

(defn get-distance []
  (->> :coord/dist-id (dat/entity (dat/db conn)) dat/touch))

(defn get-instant-speed []
  (->> :coord/instant-speed-id (dat/entity (dat/db conn)) dat/touch))

(defn get-data []
  (conj (get-coords) (get-distance) (get-instant-speed)))















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





(defn clear-coords []
  (dat/transact conn [{:db/id (dat/tempid :db.part/user)
                       :db/excise (get-coord-id)}])
  (dat/transact conn [{:db/id (dat/tempid :db.part/user)
                       :db/excise (get-coord-id-min-dist min-distance)}])
  (dat/request-index conn))

(defn clear-dist []
  (dat/transact conn [{:db/id (dat/tempid :db.part/user)
                       :db/excise dist-id}])
  (dat/request-index conn))

(defn clear-instant-speed []
  (dat/transact conn [{:db/id (dat/tempid :db.part/user)
                       :db/excise (-> (dat/entity (dat/db conn) :coord/instant-speed-id) :db/id)}])
  (dat/request-index conn))

(defn clear-all []
  (clear-coords) (clear-dist) (clear-instant-speed))



(defn id-coords []
  (dat/q '[:find ?id :where [?id :coord/lat]] (dat/db conn)))

#_(push-coord {"lat" "48.83" "lng" "2.35"})
#_(push-coord {"lat" "48.71" "lng" "2.19"})