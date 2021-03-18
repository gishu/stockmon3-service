(ns stockmon3.domain.quotes
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [stockmon3.utils :refer [csv-data->maps]]))

(defn load-quotes-map []
  (with-open [reader (io/reader "resources/quotes.csv")]
    (->> (csv/read-csv reader)
         csv-data->maps
         (reduce #(let [alias (:Alias %2)
                        alias (if (= "" alias) nil alias)
                        stock (or alias (:Code %2))
                        price (Double/parseDouble (:Price %2))]
                    
                    (assoc %1 stock price)) 
                 {})
         doall)))
