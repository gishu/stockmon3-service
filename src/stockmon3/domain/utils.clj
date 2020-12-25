(ns stockmon3.domain.utils
  (:require
   [clojurewerkz.money.amounts :as money]
   [clojurewerkz.money.currencies :as curr]))

(defn make-money [value-dbl currency-str]
  (money/amount-of (curr/of currency-str) value-dbl))