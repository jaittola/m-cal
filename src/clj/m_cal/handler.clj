(ns m-cal.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.middleware.json :as middleware]
            [ring.util.response :as resp]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basicauth]
            [environ.core :refer [env]]
            [m-cal.bookings :as bookings])
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
  (GET "/index" [] (resp/file-response "resources/public/index.html"))
  (ANY "/logout" [] {:status 401
                     :body "Olet kirjautunut ulos."})
  (GET "/api/1/bookings" [] (bookings/list-bookings))
  (POST "/api/1/bookings" [:as {body :body}] (bookings/insert-booking body))
  (route/resources "/")
  )

(defroutes other-routes
  (GET "/" [] (resp/redirect "/bookings/index"))
  (ANY "/testi" [] (resp/response "Hello, world!"))
  (route/not-found "Not Found")
 )

(def app
  (let [auth-params (get-auth-params)]
    (routes
     (-> (context "/bookings" []
                  (wrap-basicauth-if-auth-params booking-routes (get-auth-params)))
         (middleware/wrap-json-body {:keywords? true})
         (middleware/wrap-json-response))
     (-> other-routes
         (middleware/wrap-json-body)
         (middleware/wrap-json-response))
     )))


(defn setup
  []
  (when (nil? (env :pg-uri))
    (throw (Error. "You must define the database URI in environment variable PG_URI"))))

(defn -main [& [port]]
  (setup)
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty app {:port port :join? false})))
