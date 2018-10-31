(ns m-cal.booking-tests
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
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

(def test-booking {:name "Tom Anderson"
                   :yacht_name "s/y Meriruoho"
                   :email "tom@example.com"
                   :selected_dates ["2018-11-12" "2018-11-13"]})

(defn assert-add-booking-fails-with-400
  [booking]
  (let [response (test-utils/add-test-booking booking {:throw-exceptions false})]
    (is (= 400 (:status response)))))

(deftest booking-is-validated
  (testing "all fields are required"
    (doseq [missing-key [:name :yacht_name :email :selected_dates]]
      (assert-add-booking-fails-with-400 (dissoc test-booking missing-key))))

  (testing "dates must be in proper format, take I"
    (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["abcd" "2018-11-13"])))

  (testing "dates must be in proper format, take II"
    (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-13-12" "2018-11-13"])))

  (testing "dates cannot be before the configured date range"
    (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-06-12" "2018-11-13"])))

  (testing "dates for inserts cannot be before today"
    (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-09-12" "2018-11-13"])))

  (testing "dates cannot be too far into the future"
    (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-11-12" "2019-01-13"]))))

(deftest can-update-booking
  (test-utils/add-test-booking {:name "Tom Anderson"
                                :yacht_name "s/y Meriruoho"
                                :email "tom@example.com"
                                :selected_dates ["2018-11-12" "2018-11-13"]})
  (test-utils/update-booking (test-utils/get-secret-id "Tom Anderson")
                             {:name "Tom Anderson"
                              :yacht_name "s/y Meriruoho"
                              :email "tom@example.com"
                              :selected_dates ["2018-11-15" "2018-11-16"]})
  (let [[b1 b2] (test-utils/get-all-bookings)]
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-11-15"} b1))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-11-16"} b2))))

(deftest booking-update-can-contain-dates-in-the-past-gaping-whole-here-for-hackers
  (test-utils/add-test-booking {:name "Tom Anderson"
                                :yacht_name "s/y Meriruoho"
                                :email "tom@example.com"
                                :selected_dates ["2018-11-12" "2018-11-13"]})
  (test-utils/update-booking (test-utils/get-secret-id "Tom Anderson")
                             {:name "Tom Anderson"
                              :yacht_name "s/y Meriruoho"
                              :email "tom@example.com"
                              :selected_dates ["2018-09-15" "2018-09-16"]})
  (let [[b1 b2] (test-utils/get-all-bookings)]
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-09-15"} b1))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-09-16"} b2))))