(ns stockmon3.domain.trade

  (:require [stockmon3.domain.id-gen :refer [get-next-id]]
            [stockmon3.utils :refer [make-money]])   
  (:import java.time.LocalDate))

  (defrecord Trade [id date type stock qty price charges notes account-id])
  (defrecord Dividend [id date stock amount notes account-id])

  (defn make-trade [date type stock qty price charges currency notes account-id]
    (let [validated-type (some #{"B" "S" "X"} [type])]
      
      (when (nil? validated-type)
        (throw (IllegalArgumentException. "Trade type must be [B/S/X]")))
      
      (->Trade (get-next-id :trade 3)
               (LocalDate/parse date)
               validated-type
               stock qty
               (make-money price currency)
               (make-money charges currency)
               notes
               account-id)))

  (defn make-split-event 
    "`date` is LocalDate parseable string, factor must be a long value, 
     numeric `account-id` must refer to a valid account. Other inputs are open strings"
    [date stock factor notes account-id]
    
    (when (not (instance? java.lang.Long factor))
      (throw (IllegalArgumentException. "factor argument must be a long value")))
    
    (make-trade date "X" stock factor 0 0.0 "INR" notes account-id ))
  
  ;id date stock amount notes account-id
  (defn make-dividend [date stock amount currency notes account-id]
    
    (->Dividend (get-next-id :dividend 1)
                (LocalDate/parse date)
                stock
                (make-money amount currency)
                notes
                account-id)
    )




