(ns m-cal.test-utils
  (:require [clj-http.client :as client]
            [clojure.test :refer [is]]
            [clojure.data.json :refer [json-str read-str]]))

(defn clean-up-db []
  (client/post "http://localhost:3000/test/reset"))

(defn reset-db-fixture
  "clean test DB before running test. Note: does not clean up after each test is run, only before each test."
  [f]
  (clean-up-db)
  (f))

(defn ?assoc
  "Same as assoc, but skip the assoc if v is nil"
  [m & kvs]
  (->> kvs
    (partition 2)
    (filter second)
    (map vec)
    (into m)))

(defn get-headers
  [& [token]]
  (?assoc {} "X-Auth-Token" token))

(defn post-headers
  [& [token]]
  (?assoc {"Content-Type" "application/json"}
          "X-Auth-Token" token))

(defn login
  [username password]
  (-> (client/post "http://localhost:3000/api/1/login"
                   {:body (json-str {"username" username
                                     "password" password})
                    :headers (post-headers)})
      :body
      (read-str :key-fn keyword)
      :token))

(defn add-test-booking
  [booking & [user-token]]
  (client/post "http://localhost:3000/bookings/api/1/bookings"
               {:throw-exceptions false
                :body (json-str booking)
                :headers (post-headers user-token)}))

(defn add-test-booking-successfully
  [booking user-token]
  (let [response (add-test-booking booking user-token)
        _ (is (= 200 (:status response)))]
    response))

(defn add-test-booking-unchecked
  [booking]
  (client/post "http://localhost:3000/test/testBookings"
               {:body (json-str booking)
                :headers (post-headers)}))

(defn get-all-bookings-admin
  "Get all bookings using the admin API and return the complete reply"
  [& [admin-token]]
  (client/get "http://localhost:3000/admin/api/1/all_bookings"
              {:throw-exceptions false
               :headers (get-headers admin-token)}))

(defn get-all-bookings
  "Get all bookings and return the complete reply"
  [& [user-token]]
  (client/get "http://localhost:3000/bookings/api/1/bookings"
              {:throw-exceptions false
               :headers (get-headers user-token)}))

(defn get-all-booking-values
  "Underscores, argh"
  [& [user-token]]
  (-> (get-all-bookings user-token)
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
  [secret-id booking & [user-token]]
  (client/put (str "http://localhost:3000/bookings/api/1/bookings/" secret-id)
              {:throw-exceptions false
               :body (json-str booking)
               :headers (post-headers user-token)}))

(defn update-booking-successfully
  [secret-id booking user-token]
  (let [response (update-booking secret-id booking user-token)
        _ (is (= 200 (:status response)))]
    response))
