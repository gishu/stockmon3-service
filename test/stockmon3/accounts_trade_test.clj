(ns stockmon3.accounts-trade-test
  (:require 
   [clojure.test :refer :all]
   [stockmon3.domain.id-gen :as id-gen]
   [stockmon3.id-gen-mock :as mock]
   [stockmon3.domain [account :refer [make-account buy get-holdings]] 
    [trade :refer [make-trade save-trade]]]
   [stockmon3.domain.utils :refer [make-money]]
   ))


(deftest test-buy
  (testing "Appends trade to the trades log"
    (let [mock (atom {})]
      (with-redefs [id-gen/get-next-id mock/get-next-id
                    save-trade (fn [t] (reset! mock {:trade t}))]

        (let [acc (make-account "Knuckleheads" "Fargo account")
              trade (make-trade "2020-12-22" "B" "HDFC" 100 2350.0 "INR" (:id acc))]
          (buy acc trade)

          (is (= "HDFC" (get-in @mock [:trade :stock])) "save-trade not called with correct details"))))))
  
;; mock-out
 
  (deftest test-holdings
    (with-redefs [id-gen/get-next-id mock/get-next-id
                  save-trade identity]

      (let [acc (make-account "customer" "yada")
            acc-id (:id acc)
            trade1 (make-trade "2020-12-01" "B" "HDFC" 10 1100 "INR" acc-id)
            trade2 (make-trade "2020-12-12" "B" "HDFC" 20 1000 "INR" acc-id)]

        (testing "single holding"
          (buy acc trade1)

          (let [h (get-holdings acc)
                holdings (get h "HDFC")]

            (is (= {:total-qty 10 :avg-price (make-money 1100 "INR")}
                   (select-keys holdings [:total-qty :avg-price]))
                "- should have held 10 HDFC@1100")))

        (testing "report merged holdings"
          (buy acc trade2)

          (let [holdings (-> acc get-holdings (get "HDFC"))]
 
            (is (= {:total-qty 30 :avg-price (make-money 1033.33 "INR")}
                   (select-keys holdings [:total-qty :avg-price]))
                "- should have held 30 HDFC@1033.33")))))) 
  
