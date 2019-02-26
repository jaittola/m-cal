(ns m-cal.admin
  (:require [reagent.core :as reagent]
            [clojure.string :as string]
            [cljs-time.core :as time]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [m-cal.utils :as u]
            [m-cal.login :as login]
            [m-cal.token-utils :as t]
            [cljsjs.babel-polyfill]
            [goog.string :as gstring])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce app-state
  (reagent/atom {:page-state :bookings
                 :bookings []
                 :event-log []
                 :error-status nil
                 :success-status nil
                 :first-date nil
                 :last-date nil
                 :user-token nil}))

(defn set-calendar-config [config]
  (swap! app-state assoc
         :first-date (:first_date config)
         :last-date (:last_date config)))

(defn set-bookings [bookings]
  (swap! app-state assoc :bookings bookings))

(defn set-error-status [msg]
  (swap! app-state assoc
         :error-status msg
         :success-status nil))

(defn set-event-log [events]
  (swap! app-state assoc :event-log events))

(defn auth-header [ratom]
  {"X-Auth-Token" (or (t/get-user-token ratom) "")})

(defn load-bookings []
  (go (let [response (<! (http/get "/admin/api/1/all_bookings"
                                   {:headers (auth-header app-state)}))
            status-ok (= (:status response) 200)
            status-unauthorised (= (:status response) 401)
            body (:body response)
            bookings (when status-ok
                       (:all_bookings body))
            config (when status-ok
                     (:calendar_config body))]
        (if status-unauthorised
          (t/clear-user-token-and-cookie app-state)
          (do
            (when config
              (set-calendar-config config))
            (if bookings
              (do
                (set-error-status nil)
                (set-bookings bookings))
              (do
                (set-bookings [])
                (set-error-status "Varaustietojen lataaminen epäonnistui. Yritä myöhemmin uudelleen"))))))))

(defn load-event-log []
  (go (let [response (<! (http/get "/admin/api/1/event_log"
                                   {:headers (auth-header app-state)}))
            status-ok (= (:status response) 200)
            status-unauthorised (= (:status response) 401)
            body (:body response)
            events (when status-ok
                     (:events body))]
        (if status-unauthorised
          (t/clear-user-token-and-cookie app-state)
          (if events
            (do
              (set-error-status nil)
              (set-event-log events))
            (do
              (set-event-log [])
              (set-error-status "Tapahtumien haku epäonnistui. Yritä myöhemmin uudelleen")))))))

(defn load-data-for-page []
  (case (:page-state @app-state)
    :bookings (load-bookings)
    :event-log (load-event-log)
    nil))

(defn set-page-state [new-state]
  (swap! app-state assoc
         :page-state new-state
         :bookings []
         :event-log [])
  (load-data-for-page))

(defn logout []
  (let [token (t/get-user-token app-state)]
    (t/clear-user-token-and-cookie app-state)
    (when token
      (login/perform-logout-request token))))

(defn successful-login [token]
  (t/set-user-token-and-cookie app-state token)
  (load-data-for-page))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page layout

(defn booking-update-link [secret-id link-text]
  [:a {:href (str "/?user=" secret-id)} link-text])

(defn booking-update-link-for-booking [booking link-text]
  (let [secret-id (:secret_id booking)]
    (booking-update-link secret-id link-text)))

(defn user-details [userdata]
  [:div
   (:name userdata) [:br]
   (:yacht_name userdata) [:br]
   (:phone userdata) [:br]
   (:email userdata) [:br]])

(defn booking-or-free [today daydata ratom] ""
  (let [booking (:booking daydata)]
    (if booking
      [:div
       [user-details booking]
       [booking-update-link-for-booking booking "Muokkaa"]]
      [u/blank-element])))

(defn render-booking-calendar [ratom]
  (let [bookings (:bookings @ratom)
        first-date (:first-date @ratom)
        last-date (:last-date @ratom)]
    (if (seq bookings)
      [u/render-calendar ratom first-date last-date bookings booking-or-free]
      [:div])))

(defn map-log-operation [operation]
  (case operation
    1 "Vuoron varaus"
    2 "Vuoron vapautus"
    3 "Vahvistussähköpostin lähetys"
    4 "Vahvistussähköpostin lähetys (sähköposti ei käytössä)"
    5 "Käyttäjän tietojen päivitys"
    6 "Varauksen poisto ylläpidon toimesta"
    operation))

(defn render-event-row [event]
  (let [{:keys [booked_date user_login_id user_login_username]} event
        formatted-booked-date (if booked_date
                                (u/format-date booked_date)
                                "")]
    [:tr.event-row
     [:td.event-cell (:event_timestamp event)]
     [:td.event-cell formatted-booked-date]
     [:td.event-cell (map-log-operation (:operation event))]
     [:td.event-cell (:user_id event)]
     [:td.event-cell
      [user-details (:user_data event)]
      [booking-update-link (:user_secret_id event)
       "Muokkaa käyttäjän tämänhetkisiä varauksia"]]
     [:td.event-cell
      (cond
        (and user_login_id user_login_username) (gstring/format "%s (%d)"
                                                                user_login_username
                                                                user_login_id)
        user_login_id (str "[user with id] " user_login_id)
        :else (u/blank-element))]]))

(defn render-event-log [ratom]
  (let [events (:event-log @ratom)]
    (if (seq events)
      [:div.event-log-area
       [:h2 "Tapahtumaloki"]
       [:table.event-log-table
        [:thead.event-heading-row
         [:tr
          [:th.event-heading "Tapahtuman aikaleima"]
          [:th.event-heading "Varattu päivä"]
          [:th.event-heading "Toimenpide"]
          [:th.event-heading "Käyttäjän tunniste"]
          [:th.event-heading "Käyttäjän tiedot tapahtumassa"]
          [:th.event-heading "Muutoksen tekijä"]]]
        [:tbody
         (map (fn [event]
                ^{:key (str "event-" (:log_id event))}
                [render-event-row event])
              events)]]]
       [:div])))

(defn page-modes []
  (let [token (t/get-user-token app-state)]
    (when token
      [:div
       [:div
        [:span.link_like {:on-click #(set-page-state :bookings)
                          :tabIndex 0}
         "Varaukset"]]
       [:div
        [:span.link_like {:on-click #(set-page-state :event-log)
                         :tabIndex 0}
         "Tapahtumaloki"]]
       [:form {:action "/export/all-bookings"
               :method "post"}
        [:input {:type "hidden"
                 :name "lang"
                 :value "fi"}]
        [:input {:type "hidden"
                 :name "auth-token"
                 :value token}]
        [:button {:type "submit"
                  :class "not_button link_like"}
         "Kaikki varaukset Excel-tiedostona"]]
       [:a {:href "/"} "Tee uusi varaus"]])))

(defn logout-link []
  [:div.logout_header
   [:div.push_right]
   [:div.logout_link
    [:div.link_like {:on-click #(logout)}
     "Kirjaudu ulos"]]])

(defn page [ratom]
  (if (t/get-user-token ratom)
    [:div
     [logout-link]
     [:h1 "Varausten hallinta"]
     [page-modes]
     [u/success_status_area ratom]
     [u/error_status_area ratom]
     [render-booking-calendar ratom]
     [render-event-log ratom]]
    [:div
     [login/login-form-for-named-user successful-login]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (println "dev mode")))

(defn reload []
  (set-page-state :bookings)
  (reagent/render [page app-state]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (t/set-user-token-from-cookie app-state)
  (reload))
