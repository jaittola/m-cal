(ns m-cal.handler
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware])
  (:use [m-cal.reservations :only (list-reservations)]))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/reservations" [] (list-reservations))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)))
