(ns stockmon3.accounts-trade-test
  (:require [clojure.test :refer :all]
            [stockmon3.db.trade-io :refer [save-trade]]
            [stockmon3.domain 
             [account :refer [make-account buy sell split get-holdings get-gains]]
             [trade :refer [make-trade make-split-event]]]
            [stockmon3.domain.id-gen :as id-gen]
            [stockmon3.id-gen-mock :as mock]
            [stockmon3.utils :refer [make-money]]))


(deftest test-any-trade-is-recorded

  (let [mock (atom {})]
    (with-redefs [id-gen/get-next-id mock/get-next-id
                  save-trade (fn [t] (reset! mock {:trade t}))]

      (let [acc (make-account "Knuckleheads" "Fargo account")
            a-buy (make-trade "2020-12-22" "B" "HDFC" 100 2350.0 0 "INR" "" (:id acc))
            a-sale (make-trade "2020-12-24" "S" "HDFC" 100 2000.0 0 "INR" "liquidate!" (:id acc))]
        (testing "A buy appends to the trades log"
          (buy acc a-buy)

          (is (= "HDFC" (get-in @mock [:trade :stock]))
              "save-trade not called with correct details"))

        (testing "A sale appends to the trades log"
          (sell acc a-sale)
          (is (= "S" (get-in @mock [:trade :type]))
              "save-trade not called with correct details")
          (is (= "liquidate!" (get-in @mock [:trade :notes]))))))))
    


(deftest test-holdings
    ;; mock-out
  (with-redefs [id-gen/get-next-id mock/get-next-id
                save-trade identity]

    (let [acc (make-account "customer" "yada")
          acc-id (:id acc)
          trade1 (make-trade "2020-12-01" "B" "HDFC" 10 1100 0 "INR" "" acc-id)
          trade2 (make-trade "2020-12-12" "B" "HDFC" 20 1000 0 "INR" "" acc-id)
          trade3 (make-trade "2020-12-20" "S" "HDFC" 5 1400 5 "INR" "" acc-id)
          trade4 (make-trade "2020-12-30" "S" "HDFC" 10 1450 10 "INR" "" acc-id)]

      (testing "single holding"
        (buy acc trade1)

        (let [h (get-holdings acc)
              holdings (get h "HDFC")]

          (is (= {:total-qty 10 :avg-price (make-money 1100 "INR")}
                 (select-keys holdings [:total-qty :avg-price]))
              "- should have held 10 HDFC@1100")))

      (testing "report merged holdings after multiple buys"
        (buy acc trade2)

        (let [holdings (-> acc get-holdings (get "HDFC"))]

          (is (= {:total-qty 30 :avg-price (make-money 1033.33 "INR")}
                 (select-keys holdings [:total-qty :avg-price]))
              "- should have held 30 HDFC@1033.33")))



      (testing "deduct holdings on partial sale"
        (sell acc trade3)

        (let [holdings (-> acc get-holdings (get "HDFC"))]

          (is (= {:total-qty 25 :avg-price (make-money 1020 "INR")}
                 (select-keys holdings [:total-qty :avg-price]))
              "- should have held 25 HDFC@1020")))

      (testing "clear holdings on complete sale"
        (sell acc trade4)
        (let [holdings (-> acc get-holdings (get "HDFC"))]

          (is (= {:total-qty 15 :avg-price (make-money 1000 "INR")}
                 (select-keys holdings [:total-qty :avg-price]))
              "- should have held 15 HDFC@1000")
          (is (= 1 (count (get holdings :buys)))
              "- holdings with rem-qty = 0 should be removed."))))))
 
  (deftest test-holdings-on-stock-split

    (with-redefs [id-gen/get-next-id mock/get-next-id
                  save-trade identity]

      (let [acc (make-account "customer" "yada")
            acc-id (:id acc)
            trade1 (make-trade "2020-12-01" "B" "HDFC" 10 26000 10 "INR" "" acc-id)
            trade2 (make-trade "2020-12-12" "B" "HDFC" 20 28000 20 "INR" "" acc-id)
            trade3 (make-trade "2020-12-20" "S" "HDFC" 5 22000 30 "INR" "" acc-id)
            the-split (make-split-event "2020-12-30" "HDFC" 10 "stock splits 1:10" acc-id)]

        (-> acc
            (buy trade1)
            (buy trade2)
            (sell trade3)
            (split the-split))

        (let [hdfc-holdings (-> acc get-holdings (get-in ["HDFC" :buys]))]
          (is (= [50, 200] (map :rem-qty hdfc-holdings))
              "stock split 1:10 should multiply holdings")
          (is (= ["INR 2600.00", "INR 2800.00"] (map #(.toString (:price %)) hdfc-holdings))
              "stock split 1:10 should scale acq price accordingly"))))
    )

(deftest test-gains-calculation
  (with-redefs [id-gen/get-next-id mock/get-next-id
                save-trade identity]
    (let [acc (make-account "customer" "yada")
          acc-id (:id acc)
          buy-1 (make-trade "2020-12-01" "B" "BFIN" 25 3700 105.9 "INR" "" acc-id)
          buy-2 (make-trade "2020-12-12" "B" "BFIN" 25 2370 61.63 "INR" "" acc-id)
          sale-1 (make-trade "2020-12-20" "S" "BFIN" 30 3650 94.61 "INR" "" acc-id)]

      (-> acc
          (buy buy-1)
          (buy buy-2)
          (sell sale-1))

      (let [gains (get-gains acc)
            values (map #(-> % :gain .toString) gains)
            charges (map #(-> % :charges .toString) gains)]

        (is (= '("INR -1434.75" "INR 6371.90") values)
            "gain from sales incorrect")
        (is (= '("INR 184.75" "INR 28.10") charges)
            "deductions/overheads incorrect")))))