(ns stockmon3.domain.trade

  (:require [stockmon3.domain.id-gen :refer [get-next-id]]
            [stockmon3.db.conn :refer [get-db-conn]]
            [next.jdbc [sql :as sql]
             date-time]  ; required to recognize java.time.* types as SQL timestamps
            [clojure.set :refer [rename-keys]])   
  (:import [java.time LocalDate Instant]))

(defrecord Trade [id date type stock qty price account-id])

(defn make-trade [date type stock qty price account-id]
  (->Trade (get-next-id :trade 3) 
           (LocalDate/parse date)
           (if (= "S" type) type "B")
           stock qty price account-id))

(defn save-trade [trade]
  (let [row (-> trade
                (rename-keys {:date :trade_date :account-id :account_id})
                (assoc :created_at (Instant/now)))]
  
    (sql/insert! (get-db-conn) :st3.trades row)))


(defn- mapSqlToTimeTypes 
  "next.jdbc returns java.sql.* types for date/time fields, which we need to 
   replace with java.time.* types"
  [map-with-sql-types]
  (reduce (fn [return-map [k, v]]
            
            (cond
              (instance? java.sql.Timestamp v) (assoc return-map k (.toInstant v))
              (instance? java.sql.Date v) (assoc return-map k (.toLocalDate v))
              :else (assoc return-map k v)))
          {} 
          map-with-sql-types))

(defn get-trades-for-account [account-id]

  (->> (sql/find-by-keys (get-db-conn) :st3.trades {:account_id account-id})
       (map (fn [map]   ; rename trade_date to date
              (rename-keys map {:trade_date :date :account_id :account-id})))
       (map mapSqlToTimeTypes)
       (map map->Trade)))