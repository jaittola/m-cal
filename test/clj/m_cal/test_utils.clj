(ns m-cal.test-utils
  (:require [clj-http.client :as client]
            [clojure.data.json :refer [json-str read-str]]
            [clojure.string :as str]))

(defn clean-up-db []
  (client/post "http://localhost:3000/test/reset"))

(defn reset-db-fixture
  "clean test DB before running test. Note: does not clean up after each test is run, only before each test."
  [f]
  (clean-up-db)
  (f))

(defn add-test-booking [booking]
  (client/post "http://localhost:3000/bookings/api/1/bookings"
               {:body (json-str booking)
                :headers {"Content-Type" "application/json"}}))

(defn get-all-bookings
  "Underscores, argh"
  []
  (-> (client/get "http://localhost:3000/bookings/api/1/bookings")
      :body
      (read-str :key-fn keyword)
      :all_bookings))

