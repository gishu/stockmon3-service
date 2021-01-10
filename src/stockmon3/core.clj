(ns stockmon3.core
(:require [clojure.data.csv :as csv]
          [clojure.java.io :as io]
          [stockmon3.db.account-io :refer [load-account save-account]]
          [stockmon3.domain
           [account :refer [make-account buy sell split get-holdings get-gains]]
           [trade :refer [make-trade make-split-event]]])
  (:import [java.time Instant ZoneOffset])
  (:gen-class))

(defn csv-data->maps 
  "Return the csv records as a vector of maps"
  [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
            (map keyword) ;; Drop if you want string keys instead
            repeat)
       (rest csv-data)))

(defn apply-trade [acc record]

  (let [account-id (:id acc)
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
    (print record)
    
    (cond

      (= "0" (:Price record))
      (when-let [[[_, factor]] (re-seq split-pattern notes)]

        (split acc  (make-split-event date
                                    stock
                                    (Long/parseLong factor)
                                    (:Notes record)
                                    account-id)))
      (= "B" type)
      (let []
        (buy acc (make-trade date "B" stock qty price brokerage "INR" notes account-id)))
      (= "S" type)
      (let []
        (sell acc (make-trade date "S" stock qty price brokerage "INR" notes account-id)))
      :else
      (println "ERR Unknown trade pattern " record))
    
    (println " <3")))
  

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, Stockmon3")

  (let [acc (make-account "Zerodha" "Gishu's zerodha account")

        account-id (:id acc)]
    (save-account acc)
    (let [account (load-account account-id)]


      (with-open [reader (io/reader "/mnt/share/acc-3-trades.csv")]

        (->> (csv/read-csv reader)
             csv-data->maps
             (map #(apply-trade account %))
             doall))
      
      (save-account account)
      (println "We done!")

      )))
