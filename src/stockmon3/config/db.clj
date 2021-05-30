(ns stockmon3.config.db
  (:require [environ.core :refer [env]]))

(defn get-db-info []
  {:dbtype "postgresql"
   :dbname (or (env :stockmon-db) "devdb")
   :host (or (env :stockmon-db-host) "localhost")
   :port (or (env :stockmon-db-port) 5432)
   :user (or  (env :stockmon-db-user) "gishu")
   :password (or  (env :stockmon-db-pwd) "postgres")})
