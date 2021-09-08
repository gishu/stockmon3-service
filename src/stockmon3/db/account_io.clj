(ns stockmon3.db.account-io
  (:require [clojure.set :refer [rename-keys difference]]
            [next.jdbc :as jdbc]
            [next.jdbc [sql :as sql] date-time]
            [stockmon3.db.conn :refer [get-db-conn]]
            [stockmon3.db.trade-io :refer [map->Trades]]
            [stockmon3.domain.account :refer [map->Account get-holdings get-gains get-average-stats]]
            [stockmon3.utils :refer [make-money money->dbl money->cur]]
            [stockmon3.utils.db :refer [mapSqlToTimeTypes]])
  (:import java.time.Instant java.time.LocalDate java.math.BigDecimal))

(declare save-holdings save-new-holdings save-gains load-holdings load-gains get-financial-year)

(defn save-account [an-account]

  (let [db (get-db-conn)]
    
    (if (:created-at an-account)
      (let [{:keys [id]} an-account]
        (sql/update! db :st3.accounts
                     (select-keys an-account [:name :description])
                     {:id id})
        (save-holdings db (get-holdings an-account) id)
        (save-gains db (get-gains an-account) id))
      
      (sql/insert! db :st3.accounts
                   (-> (select-keys an-account [:id :name :description])
                       (assoc :created_at (Instant/now)))))))

(defn load-account [id]
  (let [db (get-db-conn)
        row  (sql/get-by-id (get-db-conn) :st3.accounts id)]
    (when row
      (let [created-at (:created_at row)
            holdings (load-holdings db id)
            gains (load-gains db id)]
        (-> row
            (assoc :state (atom {:holdings holdings :gains gains})
                   :created-at (.toInstant created-at))
            (dissoc :created_at)
            map->Account)))))

(defn- save-holdings [db all-holdings account-id]
; flatten the grouped holdings into a seq of {}[Trade + rem-qty]
  (let [holdings (map (fn [[_, value]] (:buys value)) all-holdings)
        holdings (->> holdings
                      flatten
                      (sort-by :id))
        ; new records will be missing a db id
        to-insert (filter (complement :holding_id) holdings)
        ; saved records with modified tag are dirty
        to-update (filter #(and (:holding_id %) (:modified %)) holdings)
        cur-holding-ids (->> (filter :holding_id holdings)
                             (map :holding_id)
                             set)
        ; get prev saved holding ids to identify holdings to delete
        prev-holding-ids (->> (sql/find-by-keys db :st3.holdings
                                                {:account_id account-id}
                                                {:columns [:id]})
                              (map :id)
                              set)]

    ;; CREATE > UPDATE > DELETE
    (save-new-holdings to-insert db)

    (doseq [record to-update]
      (let [{:keys [rem-qty price]} record
            new-price  (money->dbl price)]
        (sql/update! db :st3.holdings {:rem_qty rem-qty :price new-price}
                     {:id (:holding_id record)})))

    (let [to-delete (difference prev-holding-ids cur-holding-ids)]
      (doseq [record-id to-delete]
        (sql/delete! db :st3.holdings {:id record-id})))))

(defn- save-new-holdings [to-insert db]
  (let [rows-to-insert (map #(let [{:keys [account-id id rem-qty price]} %]
                               [account-id id rem-qty (money->dbl price) (money->cur price)])
                            to-insert)]
    (sql/insert-multi! db :st3.holdings
                       [:account_id :buy_id :rem_qty :price :currency]
                       rows-to-insert)))

(defn- load-holdings [db account-id]

  (let [rows (jdbc/execute! db ["select t.id, t.account_id, t.trade_date, t.type, t.stock, t.qty, t.charges, t.notes, t.created_at,
                                 h.rem_qty, h.price, h.currency, h.id as holding_id 
                                 from st3.holdings h inner join st3.trades t
                                 ON h.buy_id = t.id AND h.account_id = ?
                                 order by h.id" account-id])
        trades (map->Trades rows)]

    (->> trades
         (map #(rename-keys %1 {:rem_qty :rem-qty}))
         (group-by :stock)
         (map (fn [[stock, holdings]]

                (let [[total-qty avg-price] (get-average-stats holdings)]
                  [stock {:total-qty total-qty, :avg-price avg-price, :buys holdings}])))
         (into {}))))

(defn- load-gains [db account-id]
  (let [rows (sql/query db ["select * from st3.profit_n_loss where account_id = ? order by id" account-id])]
    (->> rows
         (map #(let [{:keys [cost_price charges gain currency]} %]
                 (-> %
                     (assoc :cost_price (make-money cost_price currency)
                            :charges (make-money charges currency)
                            :gain (make-money gain currency))
                     (dissoc :currency :duration_days))))
         (map #(rename-keys % {:account_id :account-id
                               :buy_id :buy-id
                               :sale_id :sale-id
                               :cost_price :cost-price}))
         (map mapSqlToTimeTypes)
         (into []))))

(defn- save-gains [db gains account-id]
  
  (let [to-insert (filter #((complement :id) %) gains)
        rows-to-insert (map #(let [{:keys [sale_date buy-id sale-id cost-price qty charges gain duration]} %]
                               [account-id 
                                sale_date buy-id sale-id 
                                (money->dbl cost-price)
                                qty
                                (money->dbl charges)
                                (money->dbl gain)
                                (money->cur gain)
                                duration])
                            to-insert)]

    (sql/insert-multi! db :st3.profit_n_loss
                       [:account_id :sale_date :buy_id :sale_id :cost_price :qty :charges :gain :currency :duration_days]
                       rows-to-insert)))
(declare get-cagr)
(defn query-pnl-register [account-id year]
  (let [db (get-db-conn)
        range-start (LocalDate/of year 4 1)
        range-end (LocalDate/of (inc year) 3 31)
        rows (jdbc/execute! db ["select * from st3.vw_pnl_report where account_id = ? and sale_date between ? and ? "
                                account-id range-start range-end])]

    (->> rows
         (map mapSqlToTimeTypes)
         (map #(let [{:keys [id sale_date stock, qty, cost_price, sale_price, charges, gain, currency, duration_days, age]} %
                     tco (->> (BigDecimal. qty) 
                              (.multiply cost_price))
                     gain_percent (if (= BigDecimal/ZERO tco)
                                    BigDecimal/ZERO
                                    (.divide gain tco 4 BigDecimal/ROUND_HALF_EVEN))
                     cagr  (get-cagr %)]


                 {:id id
                  :sale_date (.toString  sale_date)
                  :stock stock
                  :qty qty
                  :cost_price (.doubleValue cost_price)
                  :sale_price (.doubleValue sale_price)
                  :charges (.doubleValue charges)
                  :gain (.doubleValue gain)
                  :currency currency
                  :duration_days duration_days
                  :type age
                  :gain_percent (.doubleValue gain_percent)
                  :cagr (.doubleValue  cagr)})))))

(defn get-cagr [gain]
  (let [{:keys [cost_price sale_price duration_days]} gain]
    (if (= BigDecimal/ZERO cost_price)
      BigDecimal/ZERO
      (->
       (.divide sale_price cost_price 4 BigDecimal/ROUND_HALF_EVEN)
       (.doubleValue)
       (Math/pow (/ 1 (/ duration_days 365)))
       (- 1)
       (BigDecimal/valueOf)
       (.setScale 4 BigDecimal/ROUND_HALF_EVEN)))))

(defn get-dividends
  "report the annual dividends received for an account"
  [account-id year]
  (let [db (get-db-conn)
        range-start (LocalDate/of year 4 1)
        range-end (LocalDate/of (inc year) 3 31)
        rows (jdbc/execute! db ["select * from st3.dividends where account_id = ? and date between ? and ? order by date"
                                account-id range-start range-end])]

    (->> rows
         (map mapSqlToTimeTypes)
         (map #(let [{:keys [id date stock, amount, currency, notes]} %]

                 {:id id
                  :date (.toString date)
                  :stock stock
                  :amount (.doubleValue amount)
                  :currency currency
                  :notes notes
                  }
                 )))))
(defn get-years-for
  "list of all financial years for which a trade exists for this account"
  [account-id]
  (let [db (get-db-conn)
        {start :first_trade end :last_trade} (->>
                                              (jdbc/execute-one! db ["select min(trade_date) as first_trade, max(trade_date) as last_trade from st3.trades where account_id = ?"
                                                                     account-id])
                                              mapSqlToTimeTypes)
        start-year (get-financial-year start)
        end-year (get-financial-year end)]
    (->> (range start-year (inc end-year))
         reverse)
    ))

(defn- get-financial-year 
  "get financial/accounting year for India for the input date"
  [a-date]
  (let [the-year (.getYear a-date)]
    (if (.isBefore a-date (LocalDate/of the-year 4 1))
      (dec the-year)
      the-year)))