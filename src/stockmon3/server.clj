(ns stockmon3.server
  (:require [compojure.core :refer :all]
            [compojure.handler :refer [site]]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.reload :as reload]
            [ring.util.response :refer :all]
            [stockmon3.db.account-io :refer [load-account save-account query-pnl-register get-years-for] :as account-io]
            [stockmon3.domain.account :as account]
            [stockmon3.domain.trade :refer [make-dividend]]
            [stockmon3.quotes :as quotes]
            [stockmon3.utils :refer [money->dbl]])
  (:gen-class))

(declare get-account-by-id get-year-from get-account-id-from 
         post-trade)


(defn post-account
  "Creates a new account"
  [request]
  (try
    (let [account (get-in request [:body :account])
          {:keys [name description]} account
          created-id (-> (account/make-account name description)
                         save-account
                         :id)]

      (created (str "accounts/" created-id)))

    (catch Exception ex (let [error (.getMessage ex)]
                          (prn error)
                          {:status  500
                           :headers {"Content-Type" "application/json; charset=utf-8"}
                           :body    {:error error}}))))

(defn get-accounts
  "HTTP handler to fetch list of accounts"
  [request]
  (try
    (let [accounts-list (account-io/get-accounts)]
      (response {:data accounts-list}))
    (catch Exception e
      (bad-request (.getMessage e))))
  )

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

    (catch Exception ex (let [error (.getMessage ex)]
                          (prn error)
                          {:status  500
                           :headers {"Content-Type" "application/json; charset=utf-8"}
                           :body    {:error error}}))))

(defn post-dividends [request]
  (try
    (let [account-id (get-account-id-from request)
          divs (get-in request [:body :divs])]

      (doseq [div divs]
        (let [{:keys [date stock amount currency notes]} div]
          
          (-> (make-dividend date stock amount currency notes account-id)
              account/add-dividend)))

      (response {:result "OK"}))

    (catch Exception ex  (let [error (.getMessage ex)]
                           (prn ex)
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

(defn get-years-list [request]
  (try
    (let [account-id (get-account-id-from request)]

      (response  {:data (get-years-for account-id)}))
    (catch NumberFormatException _
      (bad-request "Error: Account id must be numeric."))
    (catch Exception ex  (let [error (.getMessage ex)]
                           (prn error)
                           {:status 500
                            :headers {"Content-Type" "application/json; charset=utf-8"}
                            :body {:error error}}))))

(defroutes my-routes

  (GET "/accounts" [] get-accounts)
  (POST "/accounts" [] post-account )
  (GET "/accounts/:id" [] get-account)

  (GET "/accounts/:id/gains/:year" [] get-gains)
  (GET "/accounts/:id/dividends/:year" [] get-dividends)
  (GET "/accounts/:id/holdings" [] get-holdings)
  (POST "/accounts/:id/trades" [] post-trades )
  (POST "/accounts/:id/divs" [] post-dividends)
  (GET "/accounts/:id/years" [] get-years-list)

  (GET "/quotes" [] quotes/get-quotes )
  (POST "/quotes" [] quotes/post-quotes))

(def app (-> my-routes
             wrap-json-response
             (wrap-json-body {:keywords? true})
             (wrap-defaults  api-defaults)
             wrap-multipart-params
             (wrap-cors :access-control-allow-origin [
                                                      ;#"http://localhost:4200"
                                                      #"st3-ui"
                                                      ]
                        :access-control-allow-methods [:get :post])))

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
