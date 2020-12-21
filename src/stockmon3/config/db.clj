(ns stockmon3.config.db
  (:require [environ.core :refer [env]]))

(defn get-db-info []
  {:dbtype "postgresql"
   :dbname (env :stockmon-db)
   :host (or (env :stockmon-db-host) "localhost")
   :port (or (env :stockmon-db-port) 5432)
   :user (env :stockmon-db-user)
   :password (env :stockmon-db-pwd)})
