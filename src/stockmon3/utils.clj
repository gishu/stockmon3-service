(ns stockmon3.utils
  (:require [clojurewerkz.money.amounts :as money]
            [clojurewerkz.money.currencies :as curr]
            ))

(defn make-money [value-dbl currency-str]
  (money/amount-of (curr/of currency-str) value-dbl))

(defn money->dbl [an-amount]
  (-> an-amount .getAmount .doubleValue))

(defn money->cur [an-amount]
  (-> an-amount .getCurrencyUnit .toString))