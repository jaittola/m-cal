(ns m-cal.test-utils
  (:require [clj-http.client :as client]
            [clojure.data.json :refer [json-str read-str]]))

(defn clean-up-db []
  (client/post "http://localhost:3000/test/reset"))

(defn reset-db-fixture
  "clean test DB before running test. Note: does not clean up after each test is run, only before each test."
  [f]
  (clean-up-db)
  (f))

(defn add-test-booking
  ([booking]
   (add-test-booking booking {}))
  ([booking http-options]
    (client/post "http://localhost:3000/bookings/api/1/bookings"
                 (merge http-options
                        {:body (json-str booking)
                         :headers {"Content-Type" "application/json"}}))))

(defn add-test-booking-unchecked
  [booking]
  (client/post "http://localhost:3000/test/testBookings"
               {:body (json-str booking)
                :headers {"Content-Type" "application/json"}}))

(defn get-all-bookings
  "Underscores, argh"
  []
  (-> (client/get "http://localhost:3000/bookings/api/1/bookings")
      :body
      (read-str :key-fn keyword)
      :all_bookings))

(defn get-secret-id
  [name]
  (-> (client/get (str "http://localhost:3000/test/user/" name))
      :body
      (read-str :key-fn keyword)
      :user
      :secret_id))

(defn update-booking
  ([secret-id booking]
   (update-booking secret-id booking {}))
  ([secret-id booking http-options]
    (client/put (str "http://localhost:3000/bookings/api/1/bookings/" secret-id)
                (merge http-options
                       {:body (json-str booking)
                       :headers {"Content-Type" "application/json"}}))))
