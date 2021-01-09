(ns stockmon3.db.trade-io-test
  (:require [clojure.test :refer :all]
            [ragtime.repl :as repl]
            [stockmon3.db [account-io :refer [save-account]]
             [trade-io :refer [save-trade get-trades-for-account]]]
            [stockmon3.domain [account :refer :all]
             [trade :refer [make-trade make-split-event]]
             [id-gen :as id-gen]]
            [stockmon3.migrations :refer [config]]))

(declare setup-db teardown-db)

(defn db-fixture [f]
  (setup-db)
  (f)
  (teardown-db))

(use-fixtures :each db-fixture)

(defn setup-db []
  (repl/migrate config)
  (id-gen/reset))

(defn teardown-db []

  (let [total-migrations (-> config :migrations count)]
    (repl/rollback config total-migrations)))

(deftest ^:integration test-save-trade
  (let [name "TestUser" desc "mera demat account"
        account (make-account name desc)
        created-id (:id account)
        trade (make-trade "2020-12-22" "B" "MGL" 100 825 "INR" "gas?" created-id)]
    (save-account account)

    (testing "Append a buy to trade log"
      (save-trade trade)
      (let [loaded-trade  (first (get-trades-for-account created-id))]

        (is (= trade
               (dissoc loaded-trade :created-at)) "trade details not saved")))

    (testing "Append a sale to the trade log"
      (let [sale (make-trade "2021-01-01" "S" "MGL" 50 1060 "INR" "sold!" created-id)]
        (save-trade sale)

        (let [loaded-trade  (second (get-trades-for-account created-id))]

          (is (= sale
                 (dissoc loaded-trade :created-at)) "trade details not saved "))))
    
    (testing "Append a split event to the trade log"
      (let [event (make-split-event "2021-01-05" "MGL" 10 "Split 1:10" created-id)]
        (save-trade event)
        (let [loaded-trade  (nth (get-trades-for-account created-id) 2)]

          (is (= event
                 (dissoc loaded-trade :created-at)) "trade details not saved ")
          (is (= "X"
                 (:type loaded-trade))
              "split events should be denoted by type X")
          )))))

