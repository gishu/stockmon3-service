(ns stockmon3.utils.db)

(defn mapSqlToTimeTypes
  "next.jdbc returns java.sql.* types for date/time fields, which I'd like to 
   replace with java.time.* types"
  [map-with-sql-types]
  (reduce (fn [return-map [k, v]]

            (cond
              (instance? java.sql.Timestamp v) (assoc return-map k (.toInstant v))
              (instance? java.sql.Date v) (assoc return-map k (.toLocalDate v))
              :else (assoc return-map k v)))
          {}
          map-with-sql-types))
