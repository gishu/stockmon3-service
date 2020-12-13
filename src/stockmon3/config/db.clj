(ns stockmon3.config.db)

(defn get-db-info []
  {:dbtype "postgresql"
   :dbname "st3"
   :host "localhost"
   :port 5432
   :user ""
   :password ""})
