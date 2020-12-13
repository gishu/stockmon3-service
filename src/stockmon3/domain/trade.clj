(ns stockmon3.domain.trade
  (:require [stockmon3.domain.id-gen :refer [get-next-id]]))

(defrecord Trade [id stock qty price])

(def id-generator (atom get-next-id))

;; for testing, inject a mock generator e.g. see id_gen_mock)
(defn set-id-generator [fn]
  (reset! id-generator fn))

(defn make-trade-bulk [stock qty price]
  (->Trade (@id-generator :trade 10) stock qty price))

(defn make-trade [stock qty price]
  (->Trade (@id-generator :trade 3) stock qty price))