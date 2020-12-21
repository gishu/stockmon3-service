(ns stockmon3.migrations
  (:require [ragtime.jdbc :as jdbc]
            [stockmon3.config.db :as dbconfig]))

(def config
  {:datastore (jdbc/sql-database (dbconfig/get-db-info))
   :migrations (jdbc/load-resources "migrations")})

; applying the migrations
(comment 
 (require 'stockmon3.migrations)
 (ns stockmon3.migrations)
 (require '[ragtime.repl :as repl])
 (repl/migrate|rollback config)
)
