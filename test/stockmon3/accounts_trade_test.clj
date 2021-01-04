(ns stockmon3.accounts-trade-test
  (:require [clojure.test :refer :all]
            [stockmon3.db.trade-io :refer [save-trade]]
            [stockmon3.domain [account :refer [make-account buy sell get-holdings]]
             [trade :refer [make-trade]]]
            [stockmon3.domain.id-gen :as id-gen]
            [stockmon3.id-gen-mock :as mock]
            [stockmon3.utils :refer [make-money]])
  (:import java.time.LocalDate))


(deftest test-any-trade-is-recorded

  (let [mock (atom {})]
    (with-redefs [id-gen/get-next-id mock/get-next-id
                  save-trade (fn [t] (reset! mock {:trade t}))]

      (let [acc (make-account "Knuckleheads" "Fargo account")
            a-buy (make-trade "2020-12-22" "B" "HDFC" 100 2350.0 "INR" (:id acc))
            a-sale (make-trade "2020-12-24" "S" "HDFC" 100 2000.0 "INR" (:id acc))]
        (testing "A buy appends to the trades log"
          (buy acc a-buy)

          (is (= "HDFC" (get-in @mock [:trade :stock]))
              "save-trade not called with correct details"))

        (testing "A sale appends to the trades log"
          (sell acc a-sale)
          (is (= "S" (get-in @mock [:trade :type]))
              "save-trade not called with correct details")
          (is (= (LocalDate/parse "2020-12-24") (get-in @mock [:trade :date]))))))))
    

(deftest test-holdings
    ;; mock-out
  (with-redefs [id-gen/get-next-id mock/get-next-id
                save-trade identity]

    (let [acc (make-account "customer" "yada")
          acc-id (:id acc)
          trade1 (make-trade "2020-12-01" "B" "HDFC" 10 1100 "INR" acc-id)
          trade2 (make-trade "2020-12-12" "B" "HDFC" 20 1000 "INR" acc-id)
          trade3 (make-trade "2020-12-20" "S" "HDFC" 5 1400 "INR" acc-id)
          trade4 (make-trade "2020-12-30" "S" "HDFC" 10 1450 "INR" acc-id)]

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
 
  
