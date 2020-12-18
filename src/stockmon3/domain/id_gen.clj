(ns stockmon3.domain.id-gen
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc-rs]
            [stockmon3.config.db :as config]))

;; map [:entity => {:next_id, :reserved_max}]
(def id-map (atom {}))

;;TODO Duplication
;; make jdbc.next return simple maps
(def as-simple-maps {:builder-fn jdbc-rs/as-unqualified-lower-maps})

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
      (let [ds (jdbc/get-datasource (config/get-db-info))
            entity-name (name entity)]

        (let [rs
              (jdbc/execute-one!
               ds
               ["SELECT next_id from st3.keys where entity = ?" entity-name]
               as-simple-maps)
              db-next-id (:next_id rs)]
          (if (nil? db-next-id)
            (throw (Exception. (str "Unknown entity type " entity-name))))

          (let [db-res-max (+ db-next-id (dec buffer-size))]

            (jdbc/execute-one! ds
                               ["UPDATE st3.keys SET next_id = ? where entity = ?" (inc db-res-max) entity-name])

            (swap! id-map update-id-map entity db-next-id db-res-max))))))

  ;; get next available id
  (let [{{:keys [next-id reserved-max]} entity} @id-map]
    (swap! id-map update-id-map entity (inc next-id) reserved-max)
    next-id))


;; for testing, inject a mock generator e.g. see id_gen_mock)
(def next-id "function to get next id" (atom get-next-id))

(defn set-id-generator [fn]
  (reset! next-id fn))