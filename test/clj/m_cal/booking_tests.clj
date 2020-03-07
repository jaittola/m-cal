(ns m-cal.booking-tests
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :refer [blank?]]
            [m-cal.test-utils :as test-utils]
            [m-cal.config :as app-config]))

(use-fixtures :once test-utils/setup-handler-config-fixture)
(use-fixtures :each test-utils/reset-db-fixture)

(def test-user "the-user")
(def test-pass "usersecret")

(def test-admin-user "admin")
(def test-admin-pass "adminsecret")

(defn user-login []
  (test-utils/login test-user test-pass))

(defn admin-login []
  (test-utils/login test-admin-user test-admin-pass))

(defn contains-key-vals [expected actual]
  (every? (fn [[k v]] (= v (get actual k)))
          expected))

(deftest can-list-all-bookings
  (let [token (user-login)]
    (test-utils/add-test-booking-successfully {:name "Tom Anderson"
                                               :yacht_name "s/y Meriruoho"
                                               :email "tom@example.com"
                                               :phone "040123 4343"
                                               :selected_dates ["2019-05-10" "2019-05-11"]}
                                              token)
    (test-utils/add-test-booking-successfully {:name "Jack Anderson"
                                               :yacht_name "s/y Abastanza"
                                               :email "jack@example.com"
                                               :phone "+358 40123 4343"
                                               :selected_dates ["2019-05-01" "2019-05-02"]}
                                              token)
    (let [all-bookings (test-utils/get-all-booking-values token)
          [b1 b2 b3 b4] all-bookings]
      (is (= 4 (count all-bookings)))
      (is (contains-key-vals {:name "Jack Anderson" :yacht_name "s/y Abastanza" :booked_date "2019-05-01"} b1))
      (is (contains-key-vals {:name "Jack Anderson" :yacht_name "s/y Abastanza" :booked_date "2019-05-02"} b2))
      (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2019-05-10"} b3))
      (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2019-05-11"} b4)))))

(def test-booking {:name "Tom Anderson"
                   :yacht_name "s/y Meriruoho"
                   :email "tom@example.com"
                   :phone "+35802312345"
                   :selected_dates ["2019-05-12" "2019-05-13"]})

(defn assert-add-booking-fails-with-401
  [booking]
  (let [response (test-utils/add-test-booking booking)]
    (is (= 401 (:status response)))))

(defn assert-add-booking-fails-with-400
  [booking token]
  (let [response (test-utils/add-test-booking booking token)]
    (is (= 400 (:status response)))))

(defn assert-update-booking-fails-with-400
  [secret-id booking token]
  (let [response (test-utils/update-booking secret-id booking token)]
    (is (= 400 (:status response)))))

(deftest user-login-is-required-to-access-bookings
  (testing "user must be logged in to add a booking"
    (assert-add-booking-fails-with-401 test-booking))

  (testing "user must be logged in to list bookings"
    (let [response (test-utils/get-all-bookings)]
      (is (= 401 (:status response)))))

  (testing "user must have a valid token list bookings"
    (let [token (user-login)  ;; do not use this token
          response (test-utils/get-all-bookings "1234")]
      (is (= 401 (:status response))))))

(deftest admin-login-is-required-to-access-admin-apis
  (testing "admin booking list api cannot be accessed without a token"
    (let [response (test-utils/get-all-bookings-admin)]
      (is (= 401 (:status response)))))

  (testing "admin booking list api cannot be accessed with a user token"
    (let [user-token (user-login)
          response (test-utils/get-all-bookings-admin user-token)]
      (is (= 401 (:status response)))))

  (testing "admin booking list api returns a success with an admin token"
    (let [admin-token (admin-login)
          response (test-utils/get-all-bookings-admin admin-token)]
      (is (= 200 (:status response))))))  ;; this test should be expanded to check content

(deftest booking-is-validated
  (let [token (user-login)]

    (testing "all fields are required"
      (doseq [missing-key [:name :yacht_name :phone :email :selected_dates]]
        (assert-add-booking-fails-with-400 (dissoc test-booking missing-key) token)))

    (testing "dates must be in proper format, take I"
      (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["abcd" "2019-05-13"]) token))

    (testing "dates must be in proper format, take II"
      (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-13-12" "2019-05-13"]) token))

    (testing "dates cannot be before the configured date range"
      (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-06-12" "2019-05-13"]) token))

    (testing "dates for inserts cannot be before today"
      (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2018-09-12" "2019-05-13"]) token))

    (testing "dates cannot be too far into the future"
      (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2019-05-12" "2019-01-13"]) token))

    (testing "only one date (two are needed)"
      (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2019-05-12"]) token))

    (testing "three dates (two are needed)"
      (assert-add-booking-fails-with-400 (assoc test-booking :selected_dates ["2019-05-12" "2019-01-13"  "2019-01-13"]) token))

    (testing "phone number must be in an appropriate format I"
      (assert-add-booking-fails-with-400 (assoc test-booking :phone "98989898989") token))))

(deftest can-update-booking
  (let [token (user-login)]
    (test-utils/add-test-booking-successfully {:name "Tom Anderson"
                                               :yacht_name "s/y Meriruoho"
                                               :email "tom@example.com"
                                               :phone "0812342 2"
                                               :selected_dates ["2019-05-12" "2019-05-13"]}
                                              token)
    (test-utils/update-booking-successfully (test-utils/get-secret-id "Tom Anderson")
                                            {:name "Tom Anderson"
                                             :yacht_name "s/y Meriruoho"
                                             :email "tom@example.com"
                                             :phone "+35840998877663"
                                             :selected_dates ["2019-05-15" "2019-05-16"]}
                                            token)
    (let [[b1 b2] (test-utils/get-all-booking-values token)]
      (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2019-05-15"} b1))
      (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2019-05-16"} b2)))))

(deftest booking-update-cannot-contain-only-one-booking
  (let [token (user-login)]
    (test-utils/add-test-booking-successfully {:name "Tom Anderson"
                                               :yacht_name "s/y Meriruoho"
                                               :email "tom@example.com"
                                               :phone "0812342 2"
                                               :selected_dates ["2019-05-12" "2019-05-13"]}
                                              token)
    (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Tom Anderson")
                                          {:name "Tom Anderson"
                                           :yacht_name "s/y Meriruoho"
                                           :email "tom@example.com"
                                           :phone "+35840998877663"
                                           :selected_dates ["2019-05-15"]}
                                          token)))

(deftest booking-update-can-contain-unmodified-dates-in-the-past
  (let [token (user-login)]
    (test-utils/add-test-booking-unchecked {:name "Tom Anderson"
                                            :yacht_name "s/y Meriruoho"
                                            :email "tom@example.com"
                                            :phone "0912344 5"
                                            :selected_dates ["2019-01-15" "2019-04-13"]})
    (test-utils/update-booking-successfully (test-utils/get-secret-id "Tom Anderson")
                                            {:name "Tom Anderson"
                                             :yacht_name "s/y Meriruoho"
                                             :email "tom@example.com"
                                             :phone "+497123412348"
                                             :selected_dates ["2019-01-15" "2019-05-14"]}
                                            token)
  (let [[b1 b2] (test-utils/get-all-booking-values token)]
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2019-01-15"} b1))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2019-05-14"} b2)))))

(deftest user-cannot-modify-future-dates-to-past-in-update
  (let [token (user-login)]
    (test-utils/add-test-booking-unchecked {:name "Tom Anderson"
                                            :yacht_name "s/y Meriruoho"
                                            :email "tom@example.com"
                                            :phone "0412222114424"
                                            :selected_dates ["2019-02-02" "2019-04-13"]})
    (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Tom Anderson")
                                          {:name "Tom Anderson"
                                           :yacht_name "s/y Meriruoho"
                                           :email "tom@example.com"
                                           :phone "0501234"
                                           :selected_dates ["2019-02-02" "2019-01-31"]}
                                          token)))

(deftest user-cannot-modify-past-dates-to-future-in-update
  (let [token (user-login)]
    (test-utils/add-test-booking-unchecked {:name "Tom Anderson"
                                            :yacht_name "s/y Meriruoho"
                                            :email "tom@example.com"
                                            :phone "0412222114424"
                                            :selected_dates ["2019-02-02" "2019-04-13"]})
    (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Tom Anderson")
                                          {:name "Tom Anderson"
                                           :yacht_name "s/y Meriruoho"
                                           :email "tom@example.com"
                                           :phone "0501234"
                                           :selected_dates ["2019-04-12" "2019-04-13"]}
                                          token)))

(deftest user-can-make-booking-within-buffer-days-but-cannot-change-it
  (let [token (user-login)]
    (test-utils/add-test-booking-successfully {:name "Matti Myöhäinen"
                                               :yacht_name "s/y Last Minute"
                                               :email "matti@example.com"
                                               :phone "04012345690"
                                               :selected_dates ["2019-03-10" "2019-03-04"]}
                                              token)
    (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Matti Myöhäinen")
                                          {:name "Matti Myöhäinen"
                                           :yacht_name "s/y Last minute"
                                           :email "matti@example.com"
                                           :phone "04012345690"
                                           :selected_dates ["2019-03-10" "2019-03-09"]}
                                          token)))

(deftest user-can-make-booking-for-same-date
  (let [token (user-login)]
    (test-utils/add-test-booking-successfully {:name "Matti Myöhäinen"
                                               :yacht_name "s/y Last Minute"
                                               :email "matti@example.com"
                                               :phone "04012345690"
                                               :selected_dates [(app-config/today) "2019-03-31"]}
                                              token)))

(deftest user-can-pay-one-booking-and-select-one
  (let [token (user-login)
        body (test-utils/add-test-booking-successfully-parsed-body {:name "Kimmo Kiireinen"
                                                                    :yacht_name "s/y Quick"
                                                                    :email "kimmo@example.com"
                                                                    :phone "0401212122"
                                                                    :selected_dates ["2019-04-02"]
                                                                    :number_of_paid_bookings 1}
                                                                   token)
        { user-id :user_id secret-id :secret_id number-of-paid-bookings :number_of_paid_bookings } (:user body)
        all-bookings-in-db (test-utils/get-all-booking-values token)
        user-bookings-in-db (->> all-bookings-in-db
                                 (filter #(= (:user-id %) user-id))
                                 (map #(:booked_date %)))]
    (is (not (blank? secret-id)))
    (is (= 1 number-of-paid-bookings))
    (is (= ["2019-04-02"] (:selected_dates body)))
    (is (= (:selected_dates body) user-bookings-in-db))))

(deftest user-can-pay-all-and-not-book
  (let [token (user-login)
        body (test-utils/add-test-booking-successfully-parsed-body {:name "Reino Rahamies"
                                                                    :yacht_name "s/y Well off"
                                                                    :email "reiska@example.com"
                                                                    :phone "0409999999"
                                                                    :selected_dates []
                                                                    :number_of_paid_bookings 2}
                                                                   token)
        { user-id :user_id secret-id :secret_id number-of-paid-bookings :number_of_paid_bookings } (:user body)
        all-bookings-in-db (test-utils/get-all-booking-values token)]
    (is (not (blank? secret-id)))
    (is (= 2 number-of-paid-bookings))
    (is (= [] (:selected_dates body)))
    (is (= [] all-bookings-in-db))))

(deftest user-can-update-with-one-booking-paid-for
  (let [token (user-login)
        body (test-utils/add-test-booking-successfully-parsed-body {:name "Eero Esteellinen"
                                                                    :yacht_name "s/y Obstacle"
                                                                    :email "eero@example.com"
                                                                    :phone "0403333333"
                                                                    :selected_dates ["2019-04-04"]
                                                                    :number_of_paid_bookings 1}
                                                                   token)
        { user-id :user_id secret-id :secret_id} (:user body)
        query-result (test-utils/get-user-bookings secret-id token)
        update-body (test-utils/update-booking-successfully-parsed-body secret-id
                                                                        {:name "Eero Esteellinen"
                                                                         :yacht_name "s/y Obstacle"
                                                                         :email "eero@example.com"
                                                                         :phone "0403333333"
                                                                         :selected_dates ["2019-05-02"]}
                                                                        token)
        number-of-paid-bookings (-> update-body
                                    :user
                                    :number_of_paid_bookings)
        all-bookings-in-db (test-utils/get-all-booking-values token)
        user-bookings-in-db (->> all-bookings-in-db
                                 (filter #(= (:user-id %) user-id))
                                 (map #(:booked_date %)))]
    (is (not (blank? secret-id)))
    (is (= 1 number-of-paid-bookings))
    (is (= ["2019-05-02"] (:selected_dates update-body)))
    (is (= (:selected_dates update-body) user-bookings-in-db))))

(deftest user-cannot-modify-paid-booking-count
  (let [token (user-login)]
    (testing "with no paid bookings, user cannot change to paid bookings"
      (test-utils/add-test-booking-successfully-parsed-body {:name "Pekka Päättämätön"
                                                             :yacht_name "s/y left-or-right"
                                                             :email "pekka@example.com"
                                                             :phone "040333333434"
                                                             :selected_dates ["2019-04-09" "2019-04-10"]}
                                                            token)
      (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Pekka Päättämätön")
                                            {:name "Pekka Päättämätön"
                                             :yacht_name "s/y left-or-right"
                                             :email "pekka@example.com"
                                             :phone "040333333434"
                                             :selected_dates []
                                             :number_of_paid_bookings 2}
                                            token))
    (testing "with one paid booking, user cannot change to fully paid bookings"
      (test-utils/add-test-booking-successfully-parsed-body {:name "Teijo Tuuliviiri"
                                                             :yacht_name "s/y windex"
                                                             :email "teijo@example.com"
                                                             :phone "040333333434"
                                                             :selected_dates ["2019-04-11"]
                                                             :number_of_paid_bookings 1}
                                                            token)
      (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Teijo Tuuliviiri")
                                            {name "Teijo Tuuliviiri"
                                             :yacht_name "s/y windex"
                                             :email "teijo@example.com"
                                             :phone "040333333434"
                                             :selected_dates []
                                             :number_of_paid_bookings 2}
                                            token))

    (testing "with bookings, user cannot change to one paid booking"
      (test-utils/add-test-booking-successfully-parsed-body {:name "Oiva Yövuorolainen"
                                                             :yacht_name "s/y windex"
                                                             :email "teijo@example.com"
                                                             :phone "040333333434"
                                                             :selected_dates ["2019-04-12" "2019-04-13"]}
                                                            token)
      (assert-update-booking-fails-with-400 (test-utils/get-secret-id "Oiva Yövuorolainen")
                                            {:name "Oiva Yövuorolainen"
                                             :yacht_name "s/y windex"
                                             :email "teijo@example.com"
                                             :phone "040333333434"
                                             :selected_dates ["2019-04-12"]
                                             :number_of_paid_bookings 1}
                                            token))))

(deftest admin-del-booking
  (let [admin-token (admin-login)
        [booking1 booking2] (test-utils/add-test-booking-unchecked {:name "Tom Anderson"
                                                                    :yacht_name "s/y Meriruoho"
                                                                    :email "tom@example.com"
                                                                    :phone "0412222114424"
                                                                    :selected_dates ["2019-02-02" "2019-04-13"]})
        del-response (test-utils/delete-booking-admin (:booking_id booking1) admin-token)
        bookings-after-delete (test-utils/get-all-booking-values admin-token)
        b1 (first bookings-after-delete)]
    (is (= 200 (:status del-response)))
    (is (= 1 (count bookings-after-delete)))
    (is (contains-key-vals {:name "Tom Anderson" :yacht_name "s/y Meriruoho" :booked_date "2019-04-13"} b1))))

(deftest admin-del-booking-not-available-with-user-token
  (let [user-token (user-login)
        [booking1 booking2] (test-utils/add-test-booking-unchecked {:name "Tom Anderson"
                                                                    :yacht_name "s/y Meriruoho"
                                                                    :email "tom@example.com"
                                                                    :phone "0412222114424"
                                                                    :selected_dates ["2019-02-02" "2019-04-13"]})
        del-response (test-utils/delete-booking-admin (:booking_id booking1) user-token)
        bookings-after-delete (test-utils/get-all-booking-values user-token)]
    (is (= 401 (:status del-response)))
    (is (= 2 (count bookings-after-delete)))))

(deftest admin-list-all-users
  (let [admin-token (admin-login)
        user-token (user-login)
        _ (test-utils/add-test-booking-successfully {:name "Teijo Tuuliviiri"
                                                     :yacht_name "s/y windex"
                                                     :email "teijo@example.com"
                                                     :phone "040333333434"
                                                     :selected_dates ["2019-04-11"]
                                                     :number_of_paid_bookings 1}
                                                    user-token)
        _ (test-utils/add-test-booking-successfully {:name "Turkka Tarkkaavainen"
                                                     :yacht_name "s/y View"
                                                     :email "turkka@example.com"
                                                     :phone "040333332211"
                                                     :selected_dates ["2019-04-12" "2019-04-13"]}
                                                    user-token)
        body (test-utils/get-all-users-admin-successfully-parsed-body admin-token)
        users (:users body)
        teijo (first users)
        turkka (nth users 1)]
    (is (= 2 (count users)))
    (is (= "Teijo Tuuliviiri" (:name teijo)))
    (is (= 1 (:number_of_paid_bookings teijo)))
    (is (= "Turkka Tarkkaavainen\" (:name turkka)"))
    (is (nil? (:number_of_paid_bookings turkka)))))

(deftest admin-list-all-users-not-available-with-user-token
  (let [user-token (user-login)]
    (test-utils/add-test-booking-successfully {:name "Teijo Tuuliviiri"
                                               :yacht_name "s/y windex"
                                               :email "teijo@example.com"
                                               :phone "040333333434"
                                               :selected_dates ["2019-04-11"]
                                               :number_of_paid_bookings 1}
                                              user-token)
    (is (= 401 (->
                (test-utils/get-all-users-admin user-token)
                :status)))))
