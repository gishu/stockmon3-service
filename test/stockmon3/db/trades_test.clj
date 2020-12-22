(ns stockmon3.db.trades-test
  (:require
   [clojure.test :refer :all]
   [stockmon3.migrations :refer [config]]
   [ragtime.repl :as repl]

   [stockmon3.domain [account :refer :all] [trade :refer :all]]))

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
  (testing "Appends trade to the trades log"

    (let [name "TestUser" desc "mera demat account"
          account (make-account name desc)
          created-id (:id account)
          trade (make-trade "2020-12-22" "B" "MGL" 100 "825" created-id)]
      (save-account account)

      (save-trade trade)
      (let [loaded-trade  (first (get-trades-for-account created-id))
            to-map (select-keys trade (keys trade))
            expected-row (assoc to-map :account_id created-id :type "B")]

        (is (= trade 
               (dissoc loaded-trade :created_at)) "trade not saved linked to the right account")))))
