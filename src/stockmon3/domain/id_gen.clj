(ns stockmon3.domain.id-gen
  (:require [stockmon3.db.conn :refer [get-db-conn]]
            [next.jdbc.sql :as sql]))

;; map [:entity => {:next_id, :reserved_max}]
(def id-map (atom {}))

(defn- update-id-map [oldVal entity next-id res-max]
  (assoc oldVal entity {:next-id next-id, :reserved-max res-max}))

(defn get-next-id
  "Generates unique ids for `entities` to be persisted to the DB. 
   Grabs the next available id from the keys table. 
   Use `buffer-size` to cache N ids to reduce DB hits"

  [entity buffer-size]

  (let [{{:keys [next-id reserved-max]} entity} @id-map]

    ;; load from DB - reserve buffer-size ids
    ;; if this is first call for entity type OR ids have been exhausted
    (when (or (nil? (entity @id-map))
              (> next-id reserved-max))
      (let [ds (get-db-conn)
            entity-name (name entity)]

        (let [rs
              (sql/get-by-id ds :st3.keys entity-name :entity {})
              db-next-id (:next_id rs)]
          (if (nil? db-next-id)
            (throw (Exception. (str "Unknown entity type " entity-name))))

          (let [db-res-max (+ db-next-id (dec buffer-size))]
            (sql/update! ds :st3.keys {:next_id (inc db-res-max)} {:entity entity-name})
            (swap! id-map update-id-map entity db-next-id db-res-max))))))

  ;; get next available id
  (let [{{:keys [next-id reserved-max]} entity} @id-map]
    (swap! id-map update-id-map entity (inc next-id) reserved-max)
    next-id))


;; for testing, inject a mock generator e.g. see id_gen_mock)
(def next-id "function to get next id" (atom get-next-id))

(defn set-id-generator [fn]
  (reset! next-id fn))