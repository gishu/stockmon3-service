(ns stockmon3.migrations
  (:require [ragtime.jdbc :as jdbc]
            [stockmon3.config.db :as dbconfig]))

(def config
  {:datastore (jdbc/sql-database (dbconfig/get-db-info))
   :migrations (jdbc/load-resources "migrations")})

