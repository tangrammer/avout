(ns avout-mongo.atom
  (:require [avout.atoms :as atoms]
            [somnium.congomongo :as mongo]
            [avout.locks :as locks])
  (:import (avout.atoms AtomData)))

(deftype MongoAtomData [conn name]
  AtomData
  (getValue [this]
    (:value (mongo/with-mongo conn
              (mongo/fetch-one :atoms :where {:name name}))))

  (setValue [this new-value]
    (let [data (mongo/with-mongo conn (mongo/fetch-one :atoms :where {:name name}))]
      (mongo/with-mongo conn
        (mongo/update! :atoms data (assoc data :value new-value))))))

(defn mongo-atom
  ([zk-client mongo-conn name init-value & {:keys [validator]}]
     (doto (mongo-atom zk-client mongo-conn name)
       (set-validator! validator)
       (.reset init-value)))
  ([zk-client mongo-conn name]
     (mongo/with-mongo mongo-conn
       (or (mongo/fetch-one :atoms :where {:name name})
           (mongo/insert! :atoms {:name name}))
       (atoms/distributed-atom zk-client name (MongoAtomData. mongo-conn name)))))

;; example usage
(comment
  (use 'avout.atoms)
  (use 'avout-mongo.atom :reload-all)
  (require '[somnium.congomongo :as mongo])
  (require '[zookeeper :as zk])

  (def zk-client (zk/connect "127.0.0.1"))
  (def mongo-conn (mongo/make-connection "mydb" :host "127.0.0.1" :port 27017))

  (def matom0 (mongo-atom zk-client mongo-conn "/matom" {:a 1}))
  @matom0
  (swap!! matom0 assoc :c 3)
  @matom0
  (swap!! matom0 update-in [:a] inc)
  @matom0

  (def matom1 (mongo-atom zk-client mongo-conn "/matom1" 1 :validator #(> % 0)))
  (add-watch matom1 :matom1 (fn [key ref old-val new-val]
                              (println key ref old-val new-val)))
  @matom1
  (swap!! matom1 inc)
  @matom1
  (swap!! matom1 - 2)
  @matom1

)