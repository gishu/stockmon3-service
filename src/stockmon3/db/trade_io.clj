(ns stockmon3.db.trade-io
  
  (:require [clojure.set :refer [rename-keys]]
            [next.jdbc [sql :as sql]
             date-time] ;reqd to recognize java.time.* types as SQL timestamps
            [stockmon3.db.conn :refer [get-db-conn]]
            [stockmon3.utils :refer [make-money]]
            [stockmon3.domain.trade :refer [map->Trade]])
(:import [java.time Instant]))

(defn- mapSqlToTimeTypes
  "next.jdbc returns java.sql.* types for date/time fields, which I'd like to 
   replace with java.time.* types"
  [map-with-sql-types]
  (reduce (fn [return-map [k, v]]

            (cond
              (instance? java.sql.Timestamp v) (assoc return-map k (.toInstant v))
              (instance? java.sql.Date v) (assoc return-map k (.toLocalDate v))
              :else (assoc return-map k v)))
          {}
          map-with-sql-types))

(defn save-trade [trade]
  (let [amount (:price trade)
        row (-> trade
                (rename-keys {:date :trade_date :account-id :account_id})
                (dissoc :price)
                (assoc :created_at (Instant/now)
                       :price (-> amount .getAmount .doubleValue)
                       :currency (-> amount .getCurrencyUnit .toString)))]

    (sql/insert! (get-db-conn) :st3.trades row)))


(defn map->Trades [rows]
  (->> rows
       (map (fn [attr-map]   ; rename trade_date to date
              (let [{:keys [price currency]} attr-map]

                (-> attr-map
                    (dissoc :currency)
                    (assoc :price (make-money price currency))
                    (rename-keys {:trade_date :date :account_id :account-id})))))
       (map mapSqlToTimeTypes)
       (map map->Trade)))

(defn get-trades-for-account [account-id]

  (->> (sql/find-by-keys (get-db-conn) :st3.trades {:account_id account-id})
       (map (fn [attr-map]   ; rename trade_date to date
              (let [{:keys [price currency]} attr-map]

                (-> attr-map
                    (dissoc :currency)
                    (assoc :price (make-money price currency))
                    (rename-keys {:trade_date :date :account_id :account-id})))))
       (map mapSqlToTimeTypes)
       (map map->Trade)))