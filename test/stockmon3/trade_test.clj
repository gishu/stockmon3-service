(ns stockmon3.trade-test
  (:require [clojure.test :refer :all]
            [stockmon3.domain.trade :refer [make-trade]]
            [stockmon3.domain.id-gen :as id-gen]
            [stockmon3.id-gen-mock :as mock])
  ;(:import java.lang.IllegalArgumentException)
  )

(deftest trade-type-enumeration
  (with-redefs [id-gen/get-next-id mock/get-next-id]
    
    (are [type] (= type
                   (-> (make-trade "2020-12-11" type "HDFC" 10 1800 "INR" "nada" 100)
                       :type))
      "B" ; buy
      "X" ; split
      "S" ; sell
      )
    ))

(deftest trade-invalid-type-throws
  (with-redefs [id-gen/get-next-id mock/get-next-id]

    (is (thrown-with-msg? IllegalArgumentException #"type must be \[B/S/X\]"
                          (make-trade "2020-12-11" "R" "XXX" 10 0 "INR" "XXX" 1))
        "invalid trade type not validated")))