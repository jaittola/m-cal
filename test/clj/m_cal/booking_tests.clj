(ns m-cal.booking-tests
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [m-cal.test-utils :as test-utils]))

(use-fixtures :each test-utils/reset-db-fixture)

(defn contains-key-vals [expected actual]
  (every? (fn [[k v]] (= v (get actual k)))
          expected))

(deftest can-list-all-bookings
  (test-utils/add-test-booking-successfully {:name "Tom Anderson"
                                             :yacht_name "s/y Meriruoho"
                                             :email "tom@example.com"
                                             :phone "040123 4343"
                                             :selected_dates ["2018-11-12" "2018-11-13"]})
  (test-utils/add-test-booking-successfully {:name "Jack Anderson"
                                             :yacht_name "s/y Abastanza"
                                             :email "jack@example.com"
                                             :phone "+358 40123 4343"
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
                   :phone "+35802312345"
                   :selected_dates ["2018-11-12" "2018-11-13"]})

(defn assert-add-booking-fails-with-400
  [booking]
  (let [response (test-utils/add-test-booking booking)]
    (is (= 400 (:status response)))))

(defn assert-update-booking-fails-with-400
  [secret-id booking]
  (let [response (test-utils/update-booking secret-id booking)]
    (is (= 400 (:status response)))))

(deftest booking-is-validated
  (testing "all fields are required"
    (doseq [missing-key [:name :yacht_name :phone :email :selected_dates]]
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
    (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-11-12" "2019-01-13"])))

  (testing "phone number must be in an appropriate format I"
    (assert-add-booking-fails-with-400 (assoc test-booking :phone "98989898989"))))

(deftest can-update-booking
  (test-utils/add-test-booking-successfully {:name "Tom Anderson"
                                             :yacht_name "s/y Meriruoho"
                                             :email "tom@example.com"
                                             :phone "0812342 2"
                                             :selected_dates ["2018-11-12" "2018-11-13"]})
  (test-utils/update-booking-successfully (test-utils/get-secret-id "Tom Anderson")
                                          {:name "Tom Anderson"
                                           :yacht_name "s/y Meriruoho"
                                           :email "tom@example.com"
                                           :phone "+35840998877663"
                                           :selected_dates ["2018-11-15" "2018-11-16"]})
  (let [[b1 b2] (test-utils/get-all-bookings)]
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-11-15"} b1))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-11-16"} b2))))

(deftest booking-update-can-contain-unmodified-dates-in-the-past
  (test-utils/add-test-booking-unchecked {:name "Tom Anderson"
                                          :yacht_name "s/y Meriruoho"
                                          :email "tom@example.com"
                                          :phone "0912344 5"
                                          :selected_dates ["2018-09-15" "2018-11-13"]})
  (test-utils/update-booking-successfully (test-utils/get-secret-id "Tom Anderson")
                                          {:name "Tom Anderson"
                                           :yacht_name "s/y Meriruoho"
                                           :email "tom@example.com"
                                           :phone "+497123412348"
                                           :selected_dates ["2018-09-15" "2018-11-14"]})
  (let [[b1 b2] (test-utils/get-all-bookings)]
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-09-15"} b1))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2018-11-14"} b2))))

(deftest user-cannot-modify-future-dates-to-past-in-update
  (test-utils/add-test-booking-unchecked {:name "Tom Anderson"
                                          :yacht_name "s/y Meriruoho"
                                          :email "tom@example.com"
                                          :phone "0412222114424"
                                          :selected_dates ["2018-09-15" "2018-11-13"]})
  (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Tom Anderson")
                                        {:name "Tom Anderson"
                                         :yacht_name "s/y Meriruoho"
                                         :email "tom@example.com"
                                         :phone "0501234"
                                         :selected_dates ["2018-09-15" "2018-09-14"]}))
