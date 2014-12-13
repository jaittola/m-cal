(ns m-cal.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.util.response :as resp]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [m-cal.google-cal-api :as cal :refer (list-bookings
                                                  setup-crypto)]
            [m-cal.bookings :as bookings :refer (insert-bookings)]))

(defn api-routes
  []
  (routes
   (GET "/bookings" [] (cal/list-bookings))
   (POST "/booking" {body :body} (bookings/insert-bookings body))))

(defroutes app-routes
  (GET "/" [] (resp/file-response "resources/public/index.html"))
  (context "/api/1" [] (api-routes))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (handler/site #'app) {:port port :join? false})))
