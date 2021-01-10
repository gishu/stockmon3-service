(ns stockmon3.db.account-io
  (:require [clojure.set :refer [rename-keys difference]]
            [next.jdbc :as jdbc]
            [next.jdbc [sql :as sql] date-time]
            [stockmon3.db.conn :refer [get-db-conn]]
            [stockmon3.db.trade-io :refer [map->Trades]]
            [stockmon3.domain.account :refer [map->Account get-holdings get-gains get-average-stats]]
            [stockmon3.utils :refer [make-money money->dbl money->cur]])
  (:import java.time.Instant))

(declare save-holdings save-new-holdings save-gains load-holdings load-gains)

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
         (map #(let [{:keys [charges gain currency]} %]
                 (-> %
                     (assoc :charges (make-money charges currency)
                            :gain (make-money gain currency))
                     (dissoc :currency :duration_days))))
         (map #(rename-keys % {:account_id :account-id
                               :buy_id :buy-id
                               :sale_id :sale-id}))
         (into []))))

(defn- save-gains [db gains account-id]
  
  (let [to-insert (filter #((complement :id) %) gains)
        rows-to-insert (map #(let [{:keys [buy-id sale-id qty charges gain duration]} %]
                               [account-id buy-id sale-id qty
                                (money->dbl charges)
                                (money->dbl gain)
                                (money->cur gain)
                                duration])
                            to-insert)]

    (sql/insert-multi! db :st3.profit_n_loss
                       [:account_id :buy_id :sale_id :qty :charges :gain :currency :duration_days]
                       rows-to-insert)))

