(ns stockmon3.domain.account-io
  (:require [clojure.set :refer [rename-keys difference]]
            [next.jdbc :as jdbc]
            [next.jdbc [sql :as sql] date-time]
            [stockmon3.db.conn :refer [get-db-conn]]
            [stockmon3.domain.account :refer [map->Account get-holdings get-average-stats]]
            [stockmon3.domain.trade :refer [map->Trades]])
  (:import java.time.Instant))

(declare save-holdings)

(defn save-account [an-account]
  (let [db (get-db-conn)]
    
    (if (:created_at an-account)
      (let [{:keys [id]} an-account]
        (sql/update! db :st3.accounts
                     (select-keys an-account [:name :description])
                     {:id id})
        (save-holdings db (get-holdings an-account) id))
      (sql/insert! db :st3.accounts
                   (-> (select-keys an-account [:id :name :description])
                       (assoc :created_at (Instant/now)))))))


(declare save-new-holdings)
(defn save-holdings [db all-holdings account-id]
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
      (sql/update! db :st3.holdings {:rem_qty (:rem-qty record)}
                   {:id (:holding_id record)}))

    (let [to-delete (difference prev-holding-ids cur-holding-ids)]
      (doseq [record-id to-delete]
        (sql/delete! db :st3.holdings {:id record-id})))))

(defn load-holdings [db account-id]

  (let [rows (jdbc/execute! db ["select t.*, h.rem_qty, h.id as holding_id 
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
  
(defn load-account [id]
  (let [db (get-db-conn)
        row  (sql/get-by-id (get-db-conn) :st3.accounts id)]
    (when row
      (let [created-at (:created_at row)
            holdings (load-holdings db id)]
        (-> row
            (assoc :holdings (atom holdings))
            map->Account
            (assoc :created_at (.toInstant created-at)))))))

(defn- save-new-holdings [to-insert db]
  (let [rows-to-insert (map #(let [{:keys [account-id id rem-qty]} %]
                               [account-id id rem-qty])
                            to-insert)]
    (sql/insert-multi! db :st3.holdings
                       [:account_id :buy_id :rem_qty]
                       rows-to-insert)))