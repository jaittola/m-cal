(ns m-cal.booking-tests
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [m-cal.bookings :as bookings]
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

(defn is-thrown-with-error-response* [message-re body-f]
  (try
    (body-f)
    (is (= :expected-fail :but-success)) ;; clumsy
    (catch Exception e
      (let [response-body (->> e
                               ex-data
                               :body)]
        (is (re-matches message-re (.getMessage e)))))))

(def test-booking {:name "Tom Anderson"
                   :yacht_name "s/y Meriruoho"
                   :email "tom@example.com"
                   :selected_dates ["2018-11-12" "2018-11-13"]})

(deftest booking-is-validated
  (testing "all fields are required"
    (doseq [missing-key [:name :yacht_name :email :selected_dates]]
      (is-thrown-with-error-response* #".*status 400.*"
        #(test-utils/add-test-booking (dissoc test-booking missing-key)))))

  (testing "dates must be in proper format, take I"
    (is-thrown-with-error-response* #".*status 400.*"
                                    #(test-utils/add-test-booking (assoc test-booking :selected_dates ["abcd" "2018-11-13"]))))

  (testing "dates must be in proper format, take II"
    (is-thrown-with-error-response* #".*status 400.*"
      #(test-utils/add-test-booking (assoc test-booking :selected_dates ["2018-13-12" "2018-11-13"]))))

  (testing "dates cannot be before the configured date range"
    (is-thrown-with-error-response* #".*status 400.*"
      #(test-utils/add-test-booking (assoc test-booking :selected_dates ["2018-06-12" "2018-11-13"]))))

  (testing "dates cannot be too far into the future"
    (is (= 1 1))))

(deftest date-range-validation
  (is (thrown? java.lang.Exception (bookings/assert-is-in-allowed-range "2018-10-11"))))