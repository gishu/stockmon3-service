(ns stockmon3.utils.account
  (:require [stockmon3.domain.account :refer [get-holdings get-gains]]))

(defn prettyprint
  "Pretty print an account for debugging"
  [acc]
  (let [h (get-holdings acc)
        g (get-gains acc)]
    (println "Account#" (:id acc) " " (:name acc))
    (println "holdings")
    (doseq [[stock, entry] h]
      (println "\t" stock (:total-qty entry) "@" (.toString  (:avg-price entry)))

      (doseq [{:keys [id date qty price rem-qty]} (:buys entry)]
        (println "\t\t" (.toString date) qty "@" (.toString price) " #" id " => " rem-qty)))

    (println "\n\nGains")
    (doseq [{:keys [sale_date buy-id sale-id qty]} g]
      (println "\t " (.toString sale_date) buy-id ">>" sale-id " -" qty)))
  (println "------"))
