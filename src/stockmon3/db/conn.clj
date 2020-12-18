(ns stockmon3.db.conn
  (:require [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbc-rs]
            [stockmon3.config.db :as config]))

;;TODO Pooled connections?
(defn get-db-conn
  []
  (let [ds (jdbc/get-datasource (config/get-db-info))
        ds-opts (jdbc/with-options ds {:builder-fn jdbc-rs/as-unqualified-lower-maps})]
    ds-opts))

