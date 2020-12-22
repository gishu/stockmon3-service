(ns stockmon3.domain.account
  (:require [stockmon3.domain.id-gen :refer [get-next-id]] 
            [stockmon3.db.conn :refer [get-db-conn]]
            [stockmon3.domain.trade :refer [save-trade]]
            [next.jdbc [sql :as sql] date-time])
  (:import java.time.Instant))

(defrecord Account [id name description])


(defn make-account 
  "create a new account with the given `name` and `description`"
  [name description]
  
  (->Account (get-next-id :account 1) name description))

(defn save-account [an-account]
  (if (:created_at an-account)
    
    (sql/update! (get-db-conn) :st3.accounts 
                 (select-keys an-account [:name :description]) 
                 {:id (:id an-account)})
    (sql/insert! (get-db-conn) :st3.accounts 
                 (-> (select-keys an-account [:id :name :description])
                     (assoc :created_at (Instant/now)))))
  )

(defn load-account [id]
  (let [row  (sql/get-by-id (get-db-conn) :st3.accounts id)]
    (when row
      (let [created-at (:created_at row)]
        (-> row
            map->Account
            (assoc :created_at (.toInstant created-at)))))))

(defn buy [account trade]
  (save-trade trade))