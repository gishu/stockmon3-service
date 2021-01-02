(ns stockmon3.db.account-io-test
  (:require [clojure.test :refer :all]
            [ragtime.repl :as repl]
            [stockmon3.domain.account :refer [make-account get-holdings buy sell]]
            [stockmon3.domain.account-io :refer [save-account load-account]]
            [stockmon3.domain.trade :refer [make-trade]]
            [stockmon3.migrations :refer [config]]
            [stockmon3.utils :refer [make-money]]))

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

(deftest ^:integration account-attributes-persist
  (testing "Accounts can be created and persisted"
  (let [name "TestUser" desc "mera demat account"
        account (make-account name desc)
        created-id (:id account)]
    (save-account account)
    
    (let [loaded (load-account created-id)]
      (is (= (dissoc account :holdings) 
             (dissoc loaded :created_at :holdings)) "loaded account doesn't match the saved one")
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

(deftest ^:integration account-holdings-persist
  (let [acc (make-account "customer" "yada")
        acc-id (:id acc)
        buy-10-hdfc (make-trade "2020-12-01" "B" "HDFC" 10 1100 "INR" acc-id)
        buy-20-hdfc (make-trade "2020-12-12" "B" "HDFC" 20 1000 "INR" acc-id)
        sell-5-hdfc (make-trade "2020-12-20" "S" "HDFC" 5 1400 "INR" acc-id)
        sell-2-hdfc (make-trade "2020-12-25" "S" "HDFC" 2 1200 "INR" acc-id)
        sell-3-hdfc (make-trade "2021-01-01" "S" "HDFC" 3 1250 "INR" acc-id)

        ]

    (testing "- save new holdings"
      (save-account acc)
      (-> (load-account 1)
          (buy  buy-10-hdfc)
          (buy  buy-20-hdfc)
          (sell sell-5-hdfc)
          save-account)

      (let [loaded-account  (load-account 1)
            holdings (-> loaded-account
                         get-holdings
                         (get "HDFC"))]
        (is (= {:total-qty 25 :avg-price (make-money 1020 "INR")}
               (select-keys holdings [:total-qty :avg-price]))
            "! - holdings not loaded for account")))
    
    (testing "- update only changed holdings"
      (-> (load-account 1)
          (sell sell-2-hdfc)
          save-account)
      
      (let [account (load-account 1)
            hdfc-holdings (-> account get-holdings (get-in ["HDFC" :buys]))]

        (is (= [1 2] (map #(:holding_id %) hdfc-holdings))
            "holdings should be updated not recreated!")
        )
      )
    (testing "- delete exhausted holdings"
      (-> (load-account 1)
          (sell sell-3-hdfc)
          save-account)

      (let [account (load-account 1)
            all-holdings (get-holdings account)
            hdfc-holdings (-> account get-holdings (get-in ["HDFC" :buys]))]
        
        ;; check holdings summary
        (is (= [20 "INR 1000.00"]
               [(get-in all-holdings ["HDFC" :total-qty])
                (-> all-holdings
                    (get-in ["HDFC" :avg-price])
                    .toString)]))
        
        ;; check holdings are cleared
        (is (= [2] (map #(:holding_id %) hdfc-holdings))
            "holding#1 is completely sold (10-5-2-3) and should not be seen")))
    ))

