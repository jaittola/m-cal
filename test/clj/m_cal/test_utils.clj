(ns m-cal.test-utils
  (:require [clojure.test :refer [is]]
            [clojure.data.json :refer [json-str read-str]]
            [ring.mock.request :as mock]
            [m-cal.handler :as handler]
            [cemerick.url :refer [url-encode]]))

(defn http-req [url method & [body token]]
  "Perform an HTTP request using the Ring Mock"
  (handler/app (cond-> (mock/request method url)
                 body (mock/json-body body)
                 body (mock/content-type "application/json")
                 token (mock/header "X-Auth-Token" token))))

(defn http-get
  "Perform an HTTP GET request using the Ring Mock"
  [url & [token]]
  (http-req url :get nil token))

(defn http-post
  "Perform an HTTP POST request using the Ring Mock"
  [url & [body token]]
  (http-req url :post body token))

(defn http-put
  "Perform an HTTP PUT request using the Ring Mock"
  [url & [body token]]
  (http-req url :put body token))

(defn clean-up-db []
  (http-post "/test/reset"))

(defn reset-db-fixture
  "clean test DB before running test. Note: does not clean up after each test is run, only before each test."
  [f]
  (clean-up-db)
  (f))

(defn setup-handler-config-fixture
  "Call handler's setup function to make sure all needed configuration exists"
  [f]
  (handler/setup)
  (f))

(defn ?assoc
  "Same as assoc, but skip the assoc if v is nil"
  [m & kvs]
  (->> kvs
       (partition 2)
       (filter second)
       (map vec)
       (into m)))

(defn login
  [username password]
  (-> (http-post "/api/1/login"
                 {"username" username
                  "password" password})
      :body
      (read-str :key-fn keyword)
      :token))

(defn add-test-booking
  [booking & [user-token]]
  (http-post "/bookings/api/1/bookings"
             booking
             user-token))

(defn add-test-booking-successfully
  [booking user-token]
  (let [response (add-test-booking booking user-token)
        _ (is (= 200 (:status response)))]
    response))

(defn add-test-booking-unchecked
  [booking]
  (http-post "/test/testBookings"
             booking))

(defn get-all-bookings-admin
  "Get all bookings using the admin API and return the complete reply"
  [& [admin-token]]
  (http-get "/admin/api/1/all_bookings"
            admin-token))

(defn get-all-bookings
  "Get all bookings and return the complete reply"
  [& [user-token]]
  (http-get "/bookings/api/1/bookings"
            user-token))

(defn get-all-booking-values
  "Underscores, argh"
  [& [user-token]]
  (-> (get-all-bookings user-token)
      :body
      (read-str :key-fn keyword)
      :all_bookings))

(defn get-secret-id
  [name]
  (-> (http-get (str "/test/user/" (url-encode name)))
      :body
      (read-str :key-fn keyword)
      :user
      :secret_id))

(defn update-booking
  [secret-id booking & [user-token]]
  (http-put (str "/bookings/api/1/bookings/" secret-id)
            booking
            user-token))

(defn update-booking-successfully
  [secret-id booking user-token]
  (let [response (update-booking secret-id booking user-token)
        _ (is (= 200 (:status response)))]
    response))
