(ns m-cal.booking-tests
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [m-cal.test-utils :as test-utils]))

(use-fixtures :each test-utils/reset-db-fixture)

(defn contains-key-vals [expected actual]
  (every? (fn [[k v]] (= v (get actual k)))
          expected))

(deftest can-list-all-bookings
  (test-utils/add-test-booking {:name "Tom Anderson"
                                :yacht_name "s/y Meriruoho"
                                :email "tom@example.com"
                                :selected_dates ["2018-11-12" "2018-11-13"]})
  (test-utils/add-test-booking {:name "Jack Anderson"
                                :yacht_name "s/y Abastanza"
                                :email "jack@example.com"
                                :selected_dates ["2018-10-22" "2018-10-26"]})
  (let [all-bookings (test-utils/get-all-bookings)
        [b1 b2 b3 b4] all-bookings]
    (is (= 4 (count all-bookings)))
    (is (contains-key-vals {:name "Jack Anderson" :yacht_name "s/y Abastanza" :booked_date "2018-10-22"} b1))
    (is (contains-key-vals {:name "Jack Anderson" :yacht_name "s/y Abastanza" :booked_date "2018-10-26"} b2))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-11-12"} b3))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-11-13"} b4))))

