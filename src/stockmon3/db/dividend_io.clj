(ns stockmon3.db.dividend-io
  (:require
   [clojure.set :refer [rename-keys]]
   [next.jdbc [sql :as sql]
    date-time] ;reqd to recognize java.time.* types as SQL timestamps
   [stockmon3.db.conn :refer [get-db-conn]]
   [stockmon3.domain.trade :refer [map->Dividend]]
   [stockmon3.utils :refer [make-money money->dbl money->cur]]
   [stockmon3.utils.db :refer [mapSqlToTimeTypes]])
  
  (:import [java.time Instant]))

(defn save-dividend [trade]
  (let [{:keys [amount]} trade
        
        row (-> trade
                (rename-keys {:account-id :account_id})
                
                (assoc :created_at (Instant/now)
                       :amount (money->dbl amount)
                       :currency (money->cur amount)
                       ))]

    (sql/insert! (get-db-conn) :st3.dividends row)))

(defn map->Dividends [rows]
  (->> rows
       (map (fn [attr-map]   ; rename trade_date to date
              (let [{:keys [amount currency]} attr-map]

                (-> attr-map
                    (dissoc :currency)
                    (assoc :amount (make-money amount currency))
                    (rename-keys {:account_id :account-id
                                  :created_at :created-at})))))
       (map mapSqlToTimeTypes)
       (map map->Dividend)))


(defn get-dividends-for-account [account-id]

  (->> (sql/find-by-keys (get-db-conn) :st3.dividends {:account_id account-id})
       map->Dividends))