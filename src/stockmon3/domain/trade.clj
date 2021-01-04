(ns stockmon3.domain.trade

  (:require [next.jdbc [sql :as sql]
             date-time]
            [stockmon3.domain.id-gen :refer [get-next-id]]
            [stockmon3.utils :refer [make-money]])   
  (:import java.time.LocalDate))

  (defrecord Trade [id date type stock qty price notes account-id])

  (defn make-trade [date type stock qty price currency notes account-id]
    (->Trade (get-next-id :trade 3)
             (LocalDate/parse date)
             (if (= "S" type) type "B")
             stock qty
             (make-money price currency)
             notes
             account-id))





