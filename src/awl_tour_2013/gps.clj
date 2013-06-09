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




















(defn attr-missing? [db eid attr]
  (-> (dat/entity db eid) attr not))

(defn get-coord-id []
  (let [coord-id (-> (dat/q '[:find ?coord-id
                              :where 
                              [?coord-id :coord/lat]
                              [?coord-id :coord/lng]
                              [(awl-tour-2013.gps/attr-missing? $ ?coord-id :coord/min-distance)]] 
                            (dat/db conn)) 
                     ffirst)]
    (if (nil? coord-id) (dat/tempid :db.part/user) coord-id)))

(defn get-coord-id-min-dist [min-dist]
  (let [coord-id (-> (dat/q '[:find ?coord-id
                              :in $ ?min-dist
                              :where 
                              [?coord-id :coord/lat]
                              [?coord-id :coord/lng]
                              [?coord-id :coord/min-distance ?min-distance]] 
                            (dat/db conn) min-dist) 
                     ffirst)]
    (if (nil? coord-id) (dat/tempid :db.part/user) coord-id)))









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


(defn above-min-distance? [coord1 coord2]
  (or (and (nil? (:lng coord1)) (nil? (:lat coord1))
           (nil? (:coord/lng coord1)) (nil? (:coor/lng coord1))) 
      (and (nil? (:lng coord2)) (nil? (:lat coord2))
           (nil? (:coord/lng coord2)) (nil? (:coor/lng coord2)))
      (>= (distance coord1 coord2) min-distance)))


















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

(defn assoc-min-dist-attrs [in-map]
  (assoc in-map :db/id (dat/tempid (dat/db conn)) :coord/min-distance min-distance))

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
      coord-min-dist)))












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

(defn id-coords []
  (dat/q '[:find ?id :where [?id :coord/lat]] (dat/db conn)))

#_(push-coord {"lat" "48.83" "lng" "2.35"})
#_(push-coord {"lat" "48.71" "lng" "2.19"})