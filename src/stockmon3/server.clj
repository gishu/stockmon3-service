(ns stockmon3.server
  (:require [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.reload :as reload]
            [ring.util.response :refer :all]
            [stockmon3.db.account-io :refer [load-account query-pnl-register]]))
(declare get-account-by-id)
(defn get-account [request]

  (try
    (let [account-id (-> request
                         (get-in [:params :id])
                         Integer/parseInt)
          account (load-account account-id)]
      (response (select-keys account [:id  :name :description])))
    (catch NumberFormatException _
      (bad-request "Error: Account id must be numeric."))))

(defn- get-account-id-from [request]
  (let [account-id (-> request
                       (get-in [:params :id])
                       Integer/parseInt)]
    account-id))

(defn- get-year-from [request]
  (let [year (-> request
                 (get-in [:params :year])
                 Integer/parseInt)]
    (if (and (> year 1975) (< year 9999))
      year
      (throw (IllegalArgumentException. "Error: Not a valid year (1975-9999)")))))
(defn respond-with-gains [request]
  (try
    (let [account-id (get-account-id-from request)
          year (get-year-from request)
          formatted-gains (query-pnl-register account-id year)]

      (response  {:data formatted-gains}))
    (catch NumberFormatException _
      (bad-request "Error: Account id must be numeric."))
    (catch IllegalArgumentException ae
      (bad-request (.getMessage ae)))))

(defroutes my-routes

  (GET "/accounts/:id" []  get-account)
  (GET "/accounts/:id/gains/:year" [] respond-with-gains))

(def app (-> my-routes
             wrap-json-response
             (wrap-defaults  site-defaults)
             (wrap-cors :access-control-allow-origin [#"http://localhost:4200"]
                        :access-control-allow-methods [:get])))

(defn in-dev? [_]
  (env :dev))

(defn -main [& args]
  (print (in-dev? args) "Starting server...")
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'app))
                  (site app))]

    (run-server handler {:port 8000})))

(defn- get-account-by-id [request]
  (let [account-id (-> request
                       (get-in [:params :id])
                       Integer/parseInt)]
    (load-account account-id)))