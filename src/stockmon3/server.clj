(ns stockmon3.server
  (:require [compojure.core :refer :all]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.util.response :refer :all]
            [stockmon3.db.account-io :refer [load-account]]))

(defn get-account [request]

  (try
    (let [account-id (-> request
                         (get-in [:params :id])
                         Integer/parseInt)
          account (load-account account-id)]
      (response (select-keys account [:id  :name :description])))
    (catch NumberFormatException ex
      (bad-request "Error: Account id must be numeric."))))

(defroutes my-routes
  
  (GET "/accounts/:id" [] (wrap-json-response get-account)))

(def app (wrap-defaults  my-routes site-defaults))

(defn -main []
  (run-server #'app {:port 8000}))