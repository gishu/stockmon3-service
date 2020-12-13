(ns stockmon3.id-gen-mock)

(def next-id (atom 0))

(defn get-next-id [entity buffer-size]
  (swap! next-id inc))