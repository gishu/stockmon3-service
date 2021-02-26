(ns stockmon3.server
  (:require [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.reload :as reload]
            [ring.util.response :refer :all]
            [stockmon3.domain.account :refer [apply-trades]]
            [stockmon3.db.account-io :refer [load-account save-account query-pnl-register]]))

(declare get-account-by-id get-year-from get-account-id-from 
         post-trade)

(defn get-account [request]

  (try
    (let [account-id (-> request
                         (get-in [:params :id])
                         Integer/parseInt)
          account (load-account account-id)]
      (response (select-keys account [:id  :name :description])))
    (catch NumberFormatException _
      (bad-request "Error: Account id must be numeric."))))

(defn get-gains [request]
  

  (try
    (let [account-id (get-account-id-from request)
          year (get-year-from request)
          formatted-gains (query-pnl-register account-id year)]

      (response  {:data formatted-gains}))
    (catch NumberFormatException _
      (bad-request "Error: Account id must be numeric."))
    (catch IllegalArgumentException ae
      (bad-request (.getMessage ae)))))

(defn post-trades [request]
  (try
    (let [account-id (get-account-id-from request)
          account (load-account account-id)
          trades (get-in request [:body :trades])]

      (apply-trades account trades)
      (save-account account)

      (response {:result "OK"}))

    (catch Exception ex  (let [error (.getMessage ex)]
                           (prn error)
                           {:status 500
                            :headers {"Content-Type" "application/json; charset=utf-8"}
                            :body {:error error}}))))

(defroutes my-routes

  (GET "/accounts/:id" []  get-account)
  (GET "/accounts/:id/gains/:year" [] get-gains)
  (POST "/accounts/:id/trades" [] post-trades ))

(def app (-> my-routes
             wrap-json-response
             (wrap-json-body {:keywords? true})
             (wrap-defaults  api-defaults)
             (wrap-cors :access-control-allow-origin [#"http://localhost:4200"]
                        :access-control-allow-methods [:get :post])
             ))

(defn in-dev? [_]
  (env :dev))

(defn -main [& args]
  
  
  (let [port 8000
        handler (if (in-dev? args)
                  (reload/wrap-reload (site #'app))
                  (site app))]

    (println "Dev=" (in-dev? args) ". Starting server on port " port)
    (run-server handler {:port port})))

(defn- get-account-by-id [request]
  (let [account-id (-> request
                       (get-in [:params :id])
                       Integer/parseInt)]
    (load-account account-id)))

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
