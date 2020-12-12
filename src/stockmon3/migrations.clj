(ns stockmon3.migrations
  (:require [ragtime.jdbc :as jdbc]))

(def config
  {:datastore (jdbc/sql-database {:dbtype "postgresql"
                                  :dbname "st3"
                                  :host "localhost"
                                  :port 5432
                                  :user "gishu"
                                  :password "pwd-101"})
   :migrations (jdbc/load-resources "migrations")})

; applying the migrations
(comment 
 (require 'stockmon3.migrations)
 (ns stockmon3.migrations)
 (require '[ragtime.repl :as repl])
 (repl/migrate|rollback config)
)
