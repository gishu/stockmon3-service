(ns stockmon3.quotes
            
  (:require [stockmon3.domain.quotes :refer [load-quotes-map]]
            [ring.util.response :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as str]
            ))

(defn get-quotes [request]
  (let [symbols
        (-> request
            (get-in [:params :symbols])
            (str/split #","))
        latest-quotes (load-quotes-map)
        default-quote {:type "EQ" :price 0}]
    (response {:quotes
               (reduce #(assoc %1 %2 (get latest-quotes %2 default-quote)) 
                       {} 
                       symbols)})))

(defn post-quotes [request]
  (let [srcFile (get-in request [:params :quotes :tempfile])
        destFilePath "resources/quotes.csv"]

    (io/copy srcFile (io/file destFilePath))
    (response {:upload "OK"})))