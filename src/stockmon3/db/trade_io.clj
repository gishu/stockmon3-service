(ns stockmon3.db.trade-io
  
  (:require [clojure.set :refer [rename-keys]]
            [next.jdbc [sql :as sql]
             date-time] ;reqd to recognize java.time.* types as SQL timestamps
            [stockmon3.db.conn :refer [get-db-conn]]
            [stockmon3.domain.trade :refer [map->Trade]]
            [clojurewerkz.money.amounts :as money]
            [stockmon3.utils :refer [make-money money->dbl money->cur]]
            [stockmon3.utils.db :refer [mapSqlToTimeTypes]]
            )
(:import [java.time Instant]))

(defn save-trade [trade]
  (let [{:keys [price charges qty]} trade
        trade_value  (-> (money/multiply price qty)
                         (money/minus charges))
        row (-> trade
                (rename-keys {:date :trade_date :account-id :account_id})
                (dissoc :price)
                (assoc :created_at (Instant/now)
                       :price (money->dbl price)
                       :currency (money->cur price) 
                       :charges (money->dbl charges)
                       :trade_value (money->dbl trade_value)))]
    
    (sql/insert! (get-db-conn) :st3.trades row)))


(defn map->Trades [rows]
  (->> rows
       (map (fn [attr-map]   ; rename trade_date to date
              (let [{:keys [price charges currency]} attr-map]

                (-> attr-map
                    (dissoc :currency :trade_value)
                    (assoc :price (make-money price currency)
                           :charges (make-money charges currency))
                    (rename-keys {:trade_date :date
                                  :account_id :account-id
                                  :created_at :created-at})))))
       (map mapSqlToTimeTypes)
       (map map->Trade)))

(defn get-trades-for-account [account-id]

  (->> (sql/find-by-keys (get-db-conn) :st3.trades {:account_id account-id})
       map->Trades))