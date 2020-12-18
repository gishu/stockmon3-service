(ns stockmon3.domain.account
  (:require [stockmon3.domain.id-gen :refer [next-id]])
  (:require [stockmon3.db.conn :refer [get-db-conn]])
  (:require [next.jdbc.sql :as sql]))

(defrecord Account [id name description])


(defn make-account 
  "create a new account with the given `name` and `description`"
  [name description]
  
  (->Account (@next-id :account 1) name description))

(defn save-account [an-account]
  (if (:saved an-account)
    
    (sql/update! (get-db-conn) :st3.accounts 
                 (select-keys an-account [:name :description]) 
                 {:id (:id an-account)})
    (sql/insert! (get-db-conn) :st3.accounts 
                 (select-keys an-account [:id :name :description])))
  )

(defn load-account [id]
  (let [map  (sql/get-by-id (get-db-conn) :st3.accounts id)]
    (when map
      (assoc map :saved true))))