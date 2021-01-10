(ns stockmon3.trade-test
  (:require [clojure.test :refer :all]
            [stockmon3.domain.id-gen :as id-gen]
            [stockmon3.domain.trade :refer [make-trade make-split-event]]
            [stockmon3.id-gen-mock :as mock])
  )

(deftest trade-type-enumeration
  (with-redefs [id-gen/get-next-id mock/get-next-id]
    
    (are [type] (= type
                   (-> (make-trade "2020-12-11" type "HDFC" 10 1800 0 "INR" "nada" 100)
                       :type))
      "B" ; buy
      "X" ; split
      "S" ; sell
      )
    ))

(deftest trade-invalid-type-throws
  (with-redefs [id-gen/get-next-id mock/get-next-id]

    (is (thrown-with-msg? IllegalArgumentException #"type must be \[B/S/X\]"
                          (make-trade "2020-12-11" "R" "XXX" 0 0 0 "INR" "XXX" 1))
        "invalid trade type not validated")))

(deftest split-trade-must-have-factor-as-long
  (with-redefs [id-gen/get-next-id mock/get-next-id]

    (let [bad-factor-value (Integer/parseInt "2")]
      (is (thrown-with-msg? IllegalArgumentException #"factor[\s\w]+must be a long value"
                            (make-split-event "2020-12-11" "XXX"  bad-factor-value  "XXX" 1))
          "split factor not type-checked => long"))))