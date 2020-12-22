(ns stockmon3.accounts-trade-test
  (:require 
   [clojure.test :refer :all]
   [stockmon3.domain.id-gen :as id-gen]
   [stockmon3.id-gen-mock :as mock]
   [stockmon3.domain [account :refer [make-account buy]] 
    [trade :refer [make-trade save-trade]]]
   ))


(deftest test-buy
  (testing "Appends trade to the trades log"
    (let [mock (atom {})]
      (with-redefs [id-gen/get-next-id mock/get-next-id
                    save-trade (fn [t] (reset! mock {:trade t}))]

        (let [acc (make-account "Knuckleheads" "Fargo account")
              trade (make-trade "2020-12-22" "B" "HDFC" 100 2350.0 (:id acc))]
          (buy acc trade)

          (is (= "HDFC" (get-in @mock [:trade :stock])) "save-trade not called with correct details"))))))
