(ns stockmon3.db.trades-test
  (:require [clojure.test :refer :all]
            [ragtime.repl :as repl]
            [stockmon3.domain [account :refer :all]
             [account-io :refer [save-account]]
             [trade :refer :all]]
            [stockmon3.migrations :refer [config]]))

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

(deftest ^:integration test-save-trade
  (let [name "TestUser" desc "mera demat account"
        account (make-account name desc)
        created-id (:id account)
        trade (make-trade "2020-12-22" "B" "MGL" 100 825 "INR" created-id)]
    (save-account account)

    (testing "Append a buy to trade log"
      (save-trade trade)
      (let [loaded-trade  (first (get-trades-for-account created-id))]

        (is (= trade
               (dissoc loaded-trade :created_at)) "trade not saved linked to the right account")))

    (testing "Append a sale to the trade log"
      (let [sale (make-trade "2021-01-01" "S" "MGL" 50 1060 "INR" created-id)]
        (save-trade sale)

        (let [loaded-trade  (second (get-trades-for-account created-id))]

          (is (= sale
                 (dissoc loaded-trade :created_at)) "trade not saved linked to the right account"))))))

