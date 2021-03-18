(ns stockmon3.domain.account
  (:require [clojurewerkz.money.amounts :as money]
            [stockmon3.db.trade-io :refer [save-trade]]
            [stockmon3.domain.id-gen :refer [get-next-id]]
            [stockmon3.domain.trade :refer [make-trade make-split-event]])
  (:import java.time.temporal.ChronoUnit))

(defrecord Account [id name description state])


(defn make-account 
  "create a new account with the given `name` and `description`"
  [name description]
  
  (->Account (get-next-id :account 1) name description (atom {:holdings {} :gains []})))


(defn get-holdings [account]
  (:holdings @(:state account)))

(defn get-gains [account]
  (:gains @(:state account)))

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

(defn apply-trades
  "Bulk insert of trades to multiple accounts. Mark stock split events with a note [STOCK SPLIT 1:10]"
  [account trades]
  (doseq [record trades]

    (let [account-id (:id account)
          split-pattern #"STOCK SPLIT 1:(\d+)"
          {:keys [date type stock qty price brokerage currency notes]} record]
      (cond
        ; stock split event
        (and (= 0 price) (re-seq split-pattern notes))
        (when-let [[[_, factor]] (re-seq split-pattern notes)]

          (split account  (make-split-event date
                                            stock
                                            (Long/parseLong factor)
                                            notes
                                            account-id)))
        (= "B" type)
        (buy account (make-trade date "B" stock qty price brokerage currency notes account-id))

        (= "S" type)
        (sell account (make-trade date "S" stock qty price brokerage currency notes account-id))

        :else
        (println "ERR Unknown trade pattern " record)))))

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

(declare get-trade-stats)

(defn fifo-sale-matcher 
  "a reduce function which matches each sale with 1-or-more holdings in FIFO order.
   Inputs: sale-trade in `state-map`
   Outputs: Adjusted holdings vector and gains vector in updated `state-map`"
  [state-map holding]
  
  (let [{:keys [sale, updated-holdings, gains]} state-map
        sale-qty (:qty-to-match sale)
        sale-id (:id sale)
        {held-qty :rem-qty, buy-id :id, cost-price :price} holding]
    
    
    (if (> sale-qty 0)
    ;deduct it from a holding
      (let [matched-qty (min sale-qty held-qty)
            updated-holding (assoc holding :rem-qty (- held-qty matched-qty)
                                   :modified true)
            sale (assoc sale :qty-to-match (- sale-qty matched-qty))
            {:keys [charges net duration-in-days]} (get-trade-stats holding sale matched-qty)
            gain {:sale_date (:date sale) 
                  :buy-id buy-id :cost-price cost-price, 
                  :sale-id sale-id 
                  :qty matched-qty
                  :charges charges :gain net 
                  :duration duration-in-days}]
        
        (assoc state-map :updated-holdings (conj updated-holdings updated-holding)
               :sale sale
               :gains (conj gains gain)))
    ;else no-change
      (assoc state-map :updated-holdings (conj updated-holdings holding))
      ))
  )

(defn- get-trade-stats
  "get combined charges in proportion for sale qty including buy + sale charges.
   e.g. if 100 was buy_charges for 100 qty and 100 as sale_charges for 50 qty,
   then total charges for sale_qty=50 => 50+100"
  [buy-trade sale-trade qty]

  (let [{buy-charges :charges, buy-qty :qty cost-price :price buy-date :date} buy-trade
        {sale-charges :charges, sale-qty :qty sale-price :price sale-date :date} sale-trade
        charges-on-buy (money/multiply buy-charges (/ qty buy-qty) :half-up)
        charges-on-sale (money/multiply sale-charges (/ qty sale-qty) :half-up)
        total-charges (money/plus charges-on-buy charges-on-sale)]

    {:charges total-charges
     :net (->
           (money/minus sale-price cost-price)
           (money/multiply qty)
           (money/minus total-charges))
     :duration-in-days (.between (ChronoUnit/DAYS) buy-date sale-date)}))



(defn- update-for-sale [state sale-trade]
  ;;TODO :Check insufficient holdings for sale

  (let [{:keys [stock qty]} sale-trade
        stock-holdings (get-in state [:holdings stock :buys])
        result-map (reduce fifo-sale-matcher
                           {:sale (assoc sale-trade :qty-to-match qty)
                            :updated-holdings [] :gains []}
                           stock-holdings)
        {:keys [updated-holdings, gains]} result-map
        updated-holdings (filter #(> (:rem-qty %) 0) updated-holdings)
    ; BUGFIX Seq operations can turn vector -> list flipping insertion point
        updated-holdings (vec updated-holdings)
        [total-qty, avg-price] (get-average-stats updated-holdings)]
    
    (assert (instance? clojure.lang.PersistentVector updated-holdings)
            "must be a vector to preserve FIFO order")

    (->
     (update-in state [:holdings stock]
                (constantly {:total-qty total-qty, :avg-price avg-price, :buys updated-holdings}))
     (update-in [:gains] #(apply conj %1 %2) gains))))

(defn- split-holding [holding factor]
  (let [{:keys [qty rem-qty price]} holding]
    
    (assoc holding
           :qty (* qty factor)
           :rem-qty (* rem-qty factor)
           :price (money/divide price factor :floor)
           :modified true)))

(defn- update-for-split [state event]
  (let [{stock :stock factor :qty} event
        stock-entry (get-in state [:holdings stock])
        updated-holdings (->  (map #(split-holding % factor) (:buys stock-entry))
                              vec)
        [total-qty, avg-price] (get-average-stats updated-holdings)]

    (assert (instance? clojure.lang.PersistentVector updated-holdings)
            "must be a vector to preserve FIFO order")
    
    (update-in state [:holdings stock]
               (constantly
                {:total-qty total-qty :avg-price avg-price :buys updated-holdings}))))
