(ns stockmon3.db.account-test
  (:require [clojure.test :refer :all]
            [stockmon3.migrations :refer [config]]
            [ragtime.repl :as repl]
            [stockmon3.domain.account :refer :all]))

(declare setup-db teardown-db)

(defn db-fixture [f]
  (setup-db)
  (f)
  (teardown-db))

(use-fixtures :each db-fixture)

(defn setup-db []
  (repl/migrate config))

(defn teardown-db []
  
  (let [total-migrations (-> config :migrations count)]
    (repl/rollback config total-migrations)))

(deftest ^:integration persistence 
  (testing "Accounts can be created and persisted"
  (let [name "TestUser" desc "mera demat account"
        account (make-account name desc)
        created-id (:id account)]
    (save-account account)
    
    (let [loaded (load-account created-id)]
      (is (= name (:name loaded)) "- incorrect account.name!")
      (is (= desc (:description loaded)) "- incorrect account.description!")
      )
    
    ))
  (testing "Accounts can be updated"
    (let [original (load-account 1)
          new-name "Mando"
          new-desc "The Mandalorian"]
      (save-account (assoc original :name new-name :description new-desc))
      
      (let [updated (load-account 1)
            {:keys [name, description]} updated]
        (is (= new-name name) "- name not updated!")
        (is (= new-desc description) "- desc not updated!")

        )
      )
    )
  )