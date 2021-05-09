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
            [stockmon3.domain.account :as account]
            [stockmon3.utils :refer [money->dbl]]
            [stockmon3.domain.quotes :refer [load-quotes-map]]
            [stockmon3.db.account-io :refer [load-account save-account query-pnl-register] :as account-io])
  (:gen-class))

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

(defn get-dividends [request]

  (try
    (let [account-id (get-account-id-from request)
          year (get-year-from request)
          dividends (account-io/get-dividends account-id year)]

      (response  {:data dividends}))
    (catch NumberFormatException _
      (bad-request "Error: Account id must be numeric."))
    (catch IllegalArgumentException ae
      (bad-request (.getMessage ae)))))

(defn post-trades [request]
  (try
    (let [account-id (get-account-id-from request)
          account (load-account account-id)
          trades (get-in request [:body :trades])]

      (account/apply-trades account trades)
      (save-account account)

      (response {:result "OK"}))

    (catch Exception ex  (let [error (.getMessage ex)]
                           (prn error)
                           {:status 500
                            :headers {"Content-Type" "application/json; charset=utf-8"}
                            :body {:error error}}))))

(defn- serialize-money2 [holding]
  (let [{:keys [date rem-qty price charges notes]} holding]
    {:date (.toString date)
     :qty rem-qty
     :price (money->dbl price)
     :charges (money->dbl charges)
     :notes notes}))

(defn- serialize-money 
  "Money instances are not JSON-encodable by ring"
  [[stock holding-info]]
  (let [{:keys [total-qty avg-price buys]} holding-info]
    (vector stock {:total-qty total-qty
                   :avg-price (money->dbl avg-price)
                   :buys (map serialize-money2 buys)
                   }))
  )

(defn get-holdings [request]
  (try
    (let [account-id (get-account-id-from request)
          account (load-account account-id)
          holdings (account/get-holdings account)]

      (response  {:data (into {} (map serialize-money holdings))}))
    (catch NumberFormatException _
      (bad-request "Error: Account id must be numeric."))
    (catch Exception ex  (let [error (.getMessage ex)]
                           (prn error)
                           {:status 500
                            :headers {"Content-Type" "application/json; charset=utf-8"}
                            :body {:error error}}))))

(defn get-quotes [request]
  (let [symbols (get-in request [:body :symbols])
        latest-quotes (load-quotes-map)]
    
    (response {:quotes
               (reduce #(assoc %1 %2 (get latest-quotes %2 0)) {} symbols)})))

(defroutes my-routes

  (GET "/accounts/:id" []  get-account)
  (GET "/accounts/:id/gains/:year" [] get-gains)
  (GET "/accounts/:id/dividends/:year" [] get-dividends)
  (GET "/accounts/:id/holdings" [] get-holdings)
  (POST "/accounts/:id/trades" [] post-trades )
  (POST "/quotes" [] get-quotes ))

(def app (-> my-routes
             wrap-json-response
             (wrap-json-body {:keywords? true})
             (wrap-defaults  api-defaults)
             (wrap-cors :access-control-allow-origin [#"st3-ui"]
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
