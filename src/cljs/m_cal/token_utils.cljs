(ns m-cal.token-utils
  (:require [reagent.cookies :as cookies]))

(defn set-user-token [app-state token]
  (swap! app-state assoc
         :user-token token))

(defn set-user-token-and-cookie [app-state token]
  (set-user-token app-state token)
  (cookies/set! "session" token {:max-age (* 8 3600)
                                 :path "/"}))

(defn set-user-token-from-cookie [app-state]
  (set-user-token app-state (cookies/get "session")))

(defn get-user-token [app-state]
  (:user-token @app-state))

(defn clear-user-token [app-state]
  (swap! app-state assoc
         :user-token nil))

(defn clear-cookie []
  (cookies/remove! "session"))

(defn clear-user-token-and-cookie [app-state]
  (clear-cookie)
  (clear-user-token app-state))
