(ns stockmon3.domain.account
  (:require [clojurewerkz.money.amounts :as money]
            [stockmon3.domain.id-gen :refer [get-next-id]]
            [stockmon3.domain.trade :refer [save-trade]]))

(defrecord Account [id name description holdings])


(defn make-account 
  "create a new account with the given `name` and `description`"
  [name description]
  
  (->Account (get-next-id :account 1) name description (atom {})))


(defn get-holdings [account]
  @(:holdings account))

(declare add deduct)
(defn buy [account trade]
  (save-trade trade)
  (swap! (:holdings account) add trade)
  account)

(defn sell [account trade]
  ;; TODO: no holding BOOM!
  ;; TODO: not enough holdings for sale
  (save-trade trade)
  (swap! (:holdings account) deduct trade)
  account)

(defn get-average-stats [trades]
  (let [monies (money/total (map #(money/multiply (:price %) (:rem-qty %)) trades))
        total-qty (reduce #(+ %1 (:rem-qty %2)) 0 trades)]
    [total-qty
     (money/divide monies total-qty :floor)]
    )
  )

(defn- add [holdings trade]
  (let [{:keys [stock qty price]} trade
        trade-with-rem-qty (assoc trade :rem-qty qty)
        holding (get holdings stock)]
    (if holding
      ; merge the new holding with existing & update stats
      (let [trades (conj (:buys holding) trade-with-rem-qty)
            [total-qty avg-price] (get-average-stats trades)]
        (assoc holdings stock {:total-qty total-qty, :avg-price avg-price, :buys trades}))
      ; create the entry in the map
      (assoc holdings stock {:total-qty qty, :avg-price price :buys [trade-with-rem-qty]}))
    )
  )

(defn- map-with-state[func state coll]
  (when-let [[x & xs] (seq coll)]
    
    (lazy-seq
     (let [[mapped-x new-state] (func x state)]
       (cons mapped-x (map-with-state func new-state xs))))))

(defn- fifo-match-sale [buy-trade sale-qty]
  (if (> sale-qty 0)

    (let [held-qty (:rem-qty buy-trade)
          gain-qty (min sale-qty held-qty)
          updated-trade (assoc buy-trade :rem-qty (- held-qty gain-qty) :modified true)]

      [updated-trade, (- sale-qty gain-qty)])
    [buy-trade, 0]))
  

(defn- deduct [holdings sale-trade]
  (let [{stock :stock, sale-qty :qty} sale-trade
        holding  (get holdings stock)]
    

    (let [updated-trades (map-with-state fifo-match-sale sale-qty (:buys holding))
          ; remove holdings with 0 qty remaining
          updated-trades (filter #(> (:rem-qty %) 0) updated-trades)
          [total-qty, avg-price] (get-average-stats updated-trades)]

      (assoc holdings stock {:total-qty total-qty, :avg-price avg-price, :buys updated-trades}))))



