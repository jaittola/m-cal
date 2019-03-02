(ns m-cal.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.middleware.defaults :as ring-defaults]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-kw-params]
            [ring.util.response :as resp]
            [ring.adapter.jetty :as jetty]
            [m-cal.bookings :as bookings]
            [m-cal.bookings-export :as bookings-export]
            [m-cal.config :as config]
            [m-cal.email-confirmation-sender :as email-sender]
            [m-cal.users :as users]
            [m-cal.testing :as testing])
  (:gen-class))

(defn authenticate-request-and-require-role [handler accepted-roles request auth-token]
  (let [user-info (users/check-login auth-token)
        user-role (:user_login_role user-info)
        request-with-user-info (if user-info
                                 (assoc request :user-info user-info)
                                 request)]
    (if (some #(= user-role %) accepted-roles)
      (handler request-with-user-info)
      {:status 401
       :body {:error_result "Unauthorised. Please log in."}})))

(defn wrap-tokenauth-and-require-role [handler accepted-roles]
  (fn [request]
    (authenticate-request-and-require-role handler
                                           accepted-roles
                                           request
                                           (get-in request [:headers "x-auth-token"]))))

(defn wrap-form-token-auth-and-require-role [handler accepted-roles]
  (fn [request]
    (authenticate-request-and-require-role handler
                                           accepted-roles
                                           request
                                           (get-in request [:params :auth-token]))))

(defn html-file-response [filename]
  (->
   (resp/file-response filename)
   (resp/content-type "text/html")))

(defroutes booking-routes
  (GET "/api/1/bookings/:id" [id]
       (bookings/list-bookings-with-user id))
  (GET "/api/1/bookings" [] (bookings/list-bookings))
  (POST "/api/1/bookings" [:as {body :body user-info :user-info}]
        (bookings/insert-booking body user-info))
  (PUT "/api/1/bookings/:id" [id :as {body :body user-info :user-info}]
       (bookings/update-booking id body user-info)))

(defroutes admin-routes
  (GET "/api/1/all_bookings" [] (bookings/admin-list-bookings))
  (GET "/api/1/event_log" [] (bookings/admin-list-eventlog))
  (DELETE "/api/1/booking/:id" [id :as {user-info :user-info}]
          (bookings/admin-del-booking id user-info)))

(defroutes export-routes
  (POST "/all-bookings" [lang] (bookings-export/export-all lang)))

(defroutes other-routes
  (GET "/" [] (html-file-response "resources/public/index.html"))
  (GET "/booking-admin" [] (html-file-response "resources/public/admin_index.html"))
  (ANY "/testi" [] (resp/response "Hello, world!"))
  (POST "/api/1/login" [:as {body :body}] (users/login body))
  (POST "/api/1/logout" [:as {body :body}] (users/logout body))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (routes
   (-> (context "/bookings" []
                (-> booking-routes
                    (middleware/wrap-json-body {:keywords? true})
                    (wrap-tokenauth-and-require-role ["user" "admin"])
                    (middleware/wrap-json-response))))
   (-> (context "/admin" []
                (-> admin-routes
                    (middleware/wrap-json-body {:keywords? true})
                    (wrap-tokenauth-and-require-role ["admin"])
                    (middleware/wrap-json-response))))
   (-> (context "/export" []
                (-> export-routes
                    (wrap-form-token-auth-and-require-role ["admin"])
                    (ring-kw-params/wrap-keyword-params)
                    (ring-params/wrap-params)
                    (middleware/wrap-json-response))))
   (-> other-routes
       (middleware/wrap-json-body {:keywords? true})
       (middleware/wrap-json-response))))

(defn setup
  []
  (config/verify-config)
  (when (not (config/is-testing))
    (email-sender/send-email-confirmations)))

(defn -main [& [port]]
  (setup)
  (jetty/run-jetty app {:port (config/port) :join? false}))
