(ns stockmon3.domain.account
  (:require [clojurewerkz.money.amounts :as money]
            [stockmon3.db.trade-io :refer [save-trade]]
            [stockmon3.domain.id-gen :refer [get-next-id]]))

(defrecord Account [id name description state])


(defn make-account 
  "create a new account with the given `name` and `description`"
  [name description]
  
  (->Account (get-next-id :account 1) name description (atom {:holdings {}})))


(defn get-holdings [account]
  (:holdings @(:state account)))

(declare update-for-new-holdings  update-for-split update-for-sale)
(defn buy [account trade]
  (save-trade trade)
  (swap! (:state account) update-for-new-holdings trade)
  account)

(defn sell [account trade]
  ;; TODO: no holding BOOM!
  ;; TODO: not enough holdings for sale
  (save-trade trade)
  (swap! (:state account) update-for-sale trade)
  account)

(defn split
  "records a stock split event adjusting the stock qty and price"
  [account event]
  (save-trade event)
  (swap! (:state account) update-for-split event)
  account
  )

(defn get-average-stats 
  "Returns a vector [total # of shares, avg price] given a collection of holdings"
  [holdings]
  (let [total-qty (reduce #(+ %1 (:rem-qty %2)) 0 holdings)]
    (if (= 0 total-qty)
      [0 money/zero]
      (let [monies (money/total (map #(money/multiply (:price %) (:rem-qty %)) holdings))]
        [total-qty
         (money/divide monies total-qty :floor)]))))

(defn- update-for-new-holdings
  "appends the holding to a map [stock => {total-qty, avg-price, list of holdings}]"
  [state trade]
  (let [{:keys [stock qty price]} trade
        trade-with-rem-qty (assoc trade :rem-qty qty)
        holdings (get state :holdings)
        holding (get holdings stock)]
    (if holding
      ; merge the new holding with existing & update stats
      (let [trades (conj (:buys holding) trade-with-rem-qty)
            [total-qty avg-price] (get-average-stats trades)]
        (update-in state [:holdings stock]
                   (constantly {:total-qty total-qty, :avg-price avg-price, :buys trades})))
      ; create the entry in the map
      (update-in state [:holdings stock]
                 (constantly {:total-qty qty, :avg-price price :buys [trade-with-rem-qty]})))))

(defn fifo-sale-matcher 
  "a reduce function which matches each sale with 1-or-more holdings in FIFO order.
   Inputs: sale-trade in `state-map`
   Outputs: Adjusted holdings vector and gains vector in updated `state-map`"
  [state-map holding]
  
  (let [{:keys [sale, updated-holdings, gains]} state-map
        sale-qty (:qty sale)
        sale-id (:id sale)
        {held-qty :rem-qty, buy-id :id} holding]
    
    
    (if (> sale-qty 0)
    ;deduct it from a holding
      (let [matched-qty (min sale-qty held-qty)
            updated-holding (assoc holding :rem-qty (- held-qty matched-qty)
                                   :modified true)
            sale (assoc sale :qty (- sale-qty matched-qty))
            gain {:buy-id buy-id :sale-id sale-id, :qty matched-qty}]
        (assoc state-map :updated-holdings (conj updated-holdings updated-holding)
               :sale sale
               :gains (conj gains gain)))
    ;else no-change
      (assoc state-map :updated-holdings (conj updated-holdings holding))
      ))
  )

(defn- update-for-sale [state sale-trade]
  ;;TODO :Check insufficient holdings for sale
  (let [{:keys [stock]} sale-trade
        stock-holdings (get-in state [:holdings stock :buys])
        result-map (reduce fifo-sale-matcher
                           {:sale sale-trade :updated-holdings [] :gains []}
                           stock-holdings)
        {:keys [updated-holdings, gains]} result-map
        updated-holdings (filter #(> (:rem-qty %) 0) updated-holdings)
        [total-qty, avg-price] (get-average-stats updated-holdings)]

    (update-in state [:holdings stock]
               (constantly {:total-qty total-qty, :avg-price avg-price, :buys updated-holdings}))))

(defn- split-holding [holding factor]
  (let [{:keys [rem-qty price]} holding]
    
    (assoc holding
           :rem-qty (* rem-qty factor)
           :price (money/divide price factor)
           :modified true)))

(defn- update-for-split [state event]
  (let [{stock :stock factor :qty} event
        stock-entry (get-in state [:holdings stock])
        updated-holdings (map #(split-holding % factor) (:buys stock-entry))
        [total-qty, avg-price] (get-average-stats updated-holdings)]

    (update-in state [:holdings stock]
               (constantly
                {:total-qty total-qty :avg-price avg-price :buys updated-holdings}))))
