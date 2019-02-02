(ns m-cal.login
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce login-ui-state
  (reagent/atom {:username nil
                 :password nil
                 :failed nil
                 :request-in-progress false}))

(defn update-username [event]
  (swap! login-ui-state assoc
         :username (-> event .-target .-value)
         :failed nil))

(defn update-password [event]
  (swap! login-ui-state assoc
         :password (-> event .-target .-value)
         :failed nil))

(defn get-and-clear-password []
  (let [pw (:password @login-ui-state)]
    (swap! login-ui-state assoc
           :password nil)
    pw))

(defn set-request-in-progress [is-in-progress]
  (swap! login-ui-state assoc
         :request-in-progress is-in-progress))

(defn perform-logout-request [token]
  (go
    (let [body {:json-params {:token token}}]
      (http/post "/api/1/logout" body))))

(defn perform-login-request [login-params on-token-receive]
  (go
    (set-request-in-progress true)
    (let [body {:json-params login-params}
          request (http/post "/api/1/login" body)
          response (<! request)
          status (:status response)
          token (get-in response [:body :token])]
      (set-request-in-progress false)
      (if (= 200 status)
        (do
          (swap! login-ui-state assoc
                 :username nil
                 :failed nil)
          (on-token-receive token))
        (swap! login-ui-state assoc :failed "Kirjautuminen epäonnistui. Tarkista salasanasi")))))

(defn login-for-default-user [on-token-receive]
  (go
    (perform-login-request {:password (get-and-clear-password)}
                           on-token-receive)))

(defn login-for-named-user [on-token-receive]
  (go
    (perform-login-request {:username (:username @login-ui-state)
                            :password (get-and-clear-password)}
                           on-token-receive)))

(defn login-form-for-default-user [on-token-receive]
  [:div.login-fullwidth-container
   [:div.login-container
    [:input.login-password {:type "password"
                            :on-change #(update-password %)
                            :on-key-press #(when (= 13 (.-charCode %))
                                             (login-for-default-user on-token-receive))
                            :maxLength 80
                            :placeholder "Syötä salasana"}]

    [:button.login-button {:type "button"
                           :disabled (or (:failed @login-ui-state)
                                         (:request-in-progress @login-ui-state)
                                         (< (count (:password @login-ui-state)) 1))
                           :on-click #(login-for-default-user on-token-receive)}
     "Kirjaudu sisään"]
    [:div.login-status
     (let [error-state-message (:failed @login-ui-state)
           request-in-progress (:request-in-progress @login-ui-state)]
       (cond
         error-state-message error-state-message
         request-in-progress "Odota hetki ..."
         :else ""))]]])

(defn can-login []
  (and (>= (count (:password @login-ui-state)) 1)
       (>= (count (:username @login-ui-state)) 1)))

(defn login-form-for-named-user [on-token-receive]
  [:div.login-fullwidth-container
   [:div.login-container
    [:input.login-password {:type "text"
                            :autoComplete "username"
                            :on-change #(update-username %)
                            :on-key-press #(when (and
                                                  (= 13 (.-charCode %))
                                                  (can-login))
                                             (login-for-named-user on-token-receive))
                            :maxLength 80
                            :placeholder "Syötä käyttäjätunnus"}]
    [:input.login-password {:type "password"
                            :on-change #(update-password %)
                            :on-key-press #(when (and
                                                  (= 13 (.-charCode %))
                                                  (can-login))
                                             (login-for-named-user on-token-receive))
                            :maxLength 80
                            :placeholder "Syötä salasana"}]

    [:button.login-button {:type "button"
                           :disabled (or (:failed @login-ui-state)
                                         (:request-in-progress @login-ui-state)
                                         (not (can-login)))
                           :on-click #(login-for-default-user on-token-receive)}
     "Kirjaudu sisään"]
    [:div.login-status
     (let [error-state-message (:failed @login-ui-state)
           request-in-progress (:request-in-progress @login-ui-state)]
       (cond
         error-state-message error-state-message
         request-in-progress "Odota hetki ..."
         :else ""))]]])
