(ns stockmon3.core
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [stockmon3.db.account-io :refer [load-account save-account]]
            [stockmon3.domain
             [account :refer [make-account buy sell split add-dividend]]
             [trade :refer [make-trade make-split-event make-dividend]]]
            [stockmon3.utils :refer [csv-data->maps]])
  (:import [java.time Instant ZoneOffset])
  (:gen-class))

(defn apply-divs [accounts record]
  (try
    (let [account-id (Long/parseLong  (:AccountId record))
          acc (get accounts account-id)
          ts (:Date record)

          inst (Instant/parse ts)
          date (-> inst
                   (.atOffset (ZoneOffset/ofHoursMinutes 5 30))
                   .toLocalDate
                   .toString)
          type (:Type record)
          notes (:Notes record)
          stock (:Stock record)
          amount (Double/parseDouble (:Amount record))]

    ;log (print record)

      (cond
        (= "D" type)
        (add-dividend (make-dividend date stock amount "INR" notes account-id))

        :else
        (println "ERR Unknown div record" record)))
    (catch Exception e (println e))))

(defn apply-trade [accounts record]
(try
  (let [account-id (Long/parseLong  (:AccountId record))
        acc (get accounts account-id)
        ts (:Date record)

        inst (Instant/parse ts)
        date (-> inst
                 (.atOffset (ZoneOffset/ofHoursMinutes 5 30))
                 .toLocalDate
                 .toString)
        type (:Type record)
        notes (:Notes record)
        stock (:Stock record)
        qty (Integer/parseInt (:Qty record))
        price (Double/parseDouble (:Price record))
        brokerage (Double/parseDouble (:Brokerage record))

        split-pattern #"STOCK SPLIT 1:(\d+)"]

    ;log (print record)

    (cond

      (and  (= 0.0 price) (re-seq split-pattern notes))
      (when-let [[[_, factor]] (re-seq split-pattern notes)]

        (split acc  (make-split-event date
                                      stock
                                      (Long/parseLong factor)
                                      (:Notes record)
                                      account-id)))
      (= "B" type)
      (buy acc (make-trade date "B" stock qty price brokerage "INR" notes account-id))

      (= "S" type)
      (sell acc (make-trade date "S" stock qty price brokerage "INR" notes account-id))

      :else
      (println "ERR Unknown trade pattern " record)))
      
      (catch Exception e (println  e))
      ))

    ;log(println " <3")


(defn -main
  "Seed the db with past trades"
  [& args]
  (println "Hello, Stockmon3. Bulk import begins..")

  ;; create the 3 accounts in data dump
  (doall (map (fn [[name, desc]]
                (-> (make-account name desc)
                    save-account))
              [["H", "G's H account"]
               ["H-M", "M's account"]
               ["Zero G", "G's trading account"]]))
  (let [accounts (reduce (fn [map, id] (assoc map id (load-account id)))
                         {} [1 2 3])]

    (with-open [reader (io/reader "/mnt/share/divs.csv")]

      ; @*#&^@#*^@ BOM character  - https://github.com/clojure/data.csv#byte-order-mark
      (.skip reader 1)

      (->> (csv/read-csv reader)
           csv-data->maps
           (map #(apply-divs accounts %))
           doall))


    (doall (map (fn [[_, a]]
                    ;; (println "------------------------------------")
                    ;; (println (:id  a))
                    ;; (println (count  (get-gains a)))
                  (save-account a))  accounts))
    (println "We done!")))
