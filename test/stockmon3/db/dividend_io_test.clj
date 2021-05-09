(ns stockmon3.db.dividend-io-test
  (:require [clojure.test :refer :all]
            [ragtime.repl :as repl]
            [stockmon3.db [account-io :refer [save-account]]
             [dividend-io :refer [save-dividend get-dividends-for-account]]]
            [stockmon3.domain [account :refer :all]
             [trade :refer [make-dividend]]
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

(deftest ^:integration test-save-dividend
  (let [name "TestUser" desc "mera demat account"
        account (make-account name desc)
        created-id (:id account)
        div (make-dividend "2020-12-22" "MGL" 1250.50 "INR" "share of profits" created-id)]
    (save-account account)

    (testing "Append a dividend to the log"
      (save-dividend div)
      (let [loaded-div  (first (get-dividends-for-account created-id))]

        (is (= div
               (dissoc loaded-div :created-at)) "dividend details not saved")))
    ))

