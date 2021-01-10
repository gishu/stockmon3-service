(ns stockmon3.db.account-io-test
  (:require [clojure.test :refer :all]
            [ragtime.repl :as repl]
            [stockmon3.db.account-io :refer [save-account load-account]]
            [stockmon3.domain.account :refer [make-account 
                                              get-holdings get-gains
                                              buy sell split]]
            [stockmon3.domain.trade :refer [make-trade make-split-event]]
            [stockmon3.domain.id-gen :as id-gen]
            [stockmon3.migrations :refer [config]]
            [stockmon3.utils :refer [make-money money->dbl]]))

(declare setup-db teardown-db)

(defn db-fixture [f]
  (setup-db)
  (f)
  (teardown-db))

(use-fixtures :each db-fixture)

(defn setup-db []
  (repl/migrate config)
  ;; reset the id generator to avoid getting cached ids
  (id-gen/reset))

(defn teardown-db []
  
  (let [total-migrations (-> config :migrations count)]
    (repl/rollback config total-migrations)))

(deftest ^:integration account-attributes-persist
  (testing "Accounts can be created and persisted"
    (let [name "TestUser" desc "mera demat account"
          account (make-account name desc)
          created-id (:id account)]
      (save-account account)

      (let [loaded-account (load-account created-id)]
        
        (is (= (dissoc account :state)
               (dissoc loaded-account :created-at :state))
            "loaded account doesn't match the saved one")
        (is (= @(:state account)
               @(:state loaded-account))
            "loaded account state:holdings don't match"))))
  
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
        buy-10-hdfc (make-trade "2020-12-01" "B" "HDFC" 10 1100 1 "INR" "" acc-id)
        buy-20-hdfc (make-trade "2020-12-12" "B" "HDFC" 20 1000 2 "INR" "" acc-id)
        sell-5-hdfc (make-trade "2020-12-20" "S" "HDFC" 5 1400 3 "INR" "" acc-id)
        sell-2-hdfc (make-trade "2020-12-25" "S" "HDFC" 2 1200 4 "INR" "" acc-id)
        sell-3-hdfc (make-trade "2021-01-01" "S" "HDFC" 3 1250 5 "INR" "" acc-id)]

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
            "holdings should be updated not recreated!")))
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

    (testing "- split stocks are persisted correctly"
      ;; 20@1000 and 5@1200 is split in 1:10 ratio 
      (-> (load-account 1)
          (buy (make-trade "2021-01-18" "B" "HDFC" 5 1200 60 "INR" "" acc-id))
          (split (make-split-event "2021-01-10" "HDFC" 10 "1:10 split" acc-id))
          save-account)

      (let [account (load-account 1)
            hdfc-entry (-> account get-holdings (get "HDFC"))]
        (is (= '([200, 100.00] [50, 120.00]) 
               (map #(vector (:rem-qty %) (-> % :price money->dbl)) 
                     (:buys hdfc-entry) ))
            "individual holdings are not split 1:10")
        
        (is (= {:total-qty 250 :avg-price (make-money 104 "INR")}
               (select-keys hdfc-entry [:total-qty :avg-price]))
            "! - holdings do not account for stock split 20*10 @ 1000/10")))))

(deftest ^:integration account-gains-persist
  (let [acc (make-account "customer" "yada")
        acc-id (:id acc)
        b1 (make-trade "2020-03-12" "B" "BFIN" 25 3700 105.9 "INR" "" acc-id)
        b2 (make-trade "2020-03-23" "B" "BFIN" 25 2370 61.63 "INR" "" acc-id)
        s1 (make-trade "2020-08-21" "S" "BFIN" 30 3650 94.61 "INR" "" acc-id)
        s2 (make-trade "2020-11-10" "S" "BFIN" 20 4220 109 "INR" "" acc-id)]
    (testing "- save gains"
      (save-account acc)
      
      (-> (load-account 1)
          (buy  b1)
          (buy  b2)
          (sell s1)
          save-account)
      
      (let [account (load-account 1)
            gains (get-gains account)]

        (is (= 2 (count gains))
            "should have loaded 2 gain entries")
        (is (= '(25 5) (map :qty gains)))))
    
    (testing "- save new gains with details"
      
      (-> (load-account 1)
          (sell s2)
          save-account)

      (let [account (load-account 1)
            gains (get-gains account)
            profits (map #(-> % :gain .toString) gains)
            charges (map #(-> % :charges .toString) gains)]

        (is (= 3 (count gains))
            "shoud have loaded old 2 + 1 new gain entries now")
        (is (= '(25 5 20) (map :qty gains)))
        
        (is (= '("INR -1434.75" "INR 6371.90" "INR 36841.69") profits)
            "gains not persisted correctly")
        (is (= '("INR 184.75" "INR 28.10" "INR 158.31") charges)
            "overheads/charges not persisted correctly")
        ))))