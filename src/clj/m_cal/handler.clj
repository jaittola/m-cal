(ns m-cal.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.util.response :as resp]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basicauth]
            [environ.core :refer [env]]
            [m-cal.bookings :as bookings]
            [m-cal.config :as config]
            [m-cal.email-confirmation-sender :as email-sender]
            [m-cal.testing :as testing])
  (:gen-class))

(defn get-auth-params []
  (let [user (env :booking-username)
        password (env :booking-password)
        realm (env :booking-realm)]
    (if (and user password realm)
      {:user user
       :password password
       :realm realm}
      nil)))

(defn basic-authenticate-bookings [auth-params]
  (fn [user password]
    (if (and (= user (:user auth-params))
             (= password (:password auth-params)))
      {:basic-authentication {:user user
                              :password password}}
      nil)))

(defn wrap-basicauth-if-auth-params [handler auth-params]
  (fn [request]
    (if auth-params
      (let [auth-req (basicauth/basic-authentication-request request (basic-authenticate-bookings auth-params))]
        (if (:basic-authentication auth-req)
          (handler auth-req)
          (basicauth/authentication-failure (:realm auth-params))))
      (handler request))))

(defroutes booking-routes
  (GET "/api/1/bookings/:id" [id] (bookings/list-bookings-with-user id))
  (GET "/api/1/bookings" [] (bookings/list-bookings))
  (POST "/api/1/bookings" [:as {body :body}] (bookings/insert-booking body))
  (PUT "/api/1/bookings/:id" [id :as {body :body}] (bookings/update-booking id body)))

(defroutes admin-routes
  (GET "/api/1/all_bookings" [] (bookings/admin-list-bookings))
  (GET "/api/1/event_log" [] (bookings/admin-list-eventlog)))

(defroutes other-routes
  (GET "/" [] (resp/file-response "resources/public/index.html"))
  (GET "/booking-admin" [] (resp/file-response "resources/public/admin_index.html"))
  (ANY "/testi" [] (resp/response "Hello, world!"))
  (POST "/api/1/login" [:as {body :body}] (users/login body))
  (POST "/api/1/logout" [:as {body :body}] (users/logout body))
  (route/resources "/")
  (route/not-found "Not Found"))

(defroutes test-routes
  (POST "/reset" [] (do (testing/reset-db)
                      {:status 200 :body "ok"}))
  (GET "/user/:name" [name] {:status 200 :body {:user (testing/get-user name)}})
  (POST "/testBookings" [:as {body :body}] (testing/insert-booking-unchecked body))
  (route/not-found "Not Found"))

(defn in-test-env [handler]
  (fn [req]
    (if (not (env :testing))
      {:status 404}
      (handler req))))

(defn with-admin-ui [handler]
  (fn [req]
    (if (not (env :has-admin-ui))
      {:status 404}
      (handler req))))

(def app
  (routes
    (-> (context "/bookings" []
          (-> booking-routes
              (wrap-basicauth-if-auth-params (get-auth-params))
              (middleware/wrap-json-body {:keywords? true})
              (middleware/wrap-json-response))))
    (-> (context "/admin" []
          (-> (with-admin-ui admin-routes)
              (wrap-basicauth-if-auth-params (get-auth-params))
              (middleware/wrap-json-body {:keywords? true})
              (middleware/wrap-json-response))))
    (-> (context "/test" []
          (-> (in-test-env test-routes)
              (middleware/wrap-json-body {:keywords? true})
              (middleware/wrap-json-response))))
    (-> other-routes
        (middleware/wrap-json-response))))

(defn setup
  []
  (config/verify-config)
  (when (nil? (env :database-url))
    (throw (Error. "You must define the database URI in environment variable DATABASE_URL")))
    (email-sender/send-email-confirmations)
  )

(defn -main [& [port]]
  (setup)
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty app {:port port :join? false})))
