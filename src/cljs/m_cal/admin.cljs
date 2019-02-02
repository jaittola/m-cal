(ns m-cal.admin
  (:require [reagent.core :as reagent]
            [clojure.string :as string]
            [cljs-time.core :as time]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [m-cal.utils :as u]
            [cljsjs.babel-polyfill])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce app-state
  (reagent/atom {:bookings []
                 :event-log []
                 :error-status nil
                 :success-status nil
                 :first-date nil
                 :last-date nil}))

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

(defn load-bookings []
  (go (let [response (<! (http/get "/admin/api/1/all_bookings"))
            status-ok (= (:status response) 200)
            body (:body response)
            bookings (when status-ok
                       (:all_bookings body))
            config (when status-ok
                     (:calendar_config body))]
        (when config
          (set-calendar-config config))
        (if bookings
          (do
            (set-error-status nil)
            (set-bookings bookings))
          (do
            (set-bookings [])
            (set-error-status "Varaustietojen lataaminen epäonnistui. Yritä myöhemmin uudelleen"))))))

(defn load-event-log []
  (go (let [response (<! (http/get "/admin/api/1/event_log"))
            status-ok (= (:status response) 200)
            body (:body response)
            events (when status-ok
                     (:events body))]
        (if events
          (do
            (set-error-status nil)
            (set-event-log events))
          (do
            (set-event-log [])
            (set-error-status "Tapahtumien haku epäonnistui. Yritä myöhemmin uudelleen"))))))

(defn set-page-state [new-state]
  (swap! app-state assoc
         :bookings []
         :event-log [])
  (case new-state
    :bookings (load-bookings)
    :event-log (load-event-log)
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page layout

(defn booking-update-link [secret-id link-text]
  [:a {:href (str "/bookings/index?user=" secret-id)} link-text])

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
    operation))

(defn render-event-row [event]
  (let [booked-date (:booked_date event)
        formatted-booked-date (if booked-date
                                (u/format-date booked-date)
                                "")]
    [:tr.event-row
     [:td.event-cell (:event_timestamp event)]
     [:td.event-cell formatted-booked-date]
     [:td.event-cell (map-log-operation (:operation event))]
     [:td.event-cell (:user_id event)]
     [:td.event-cell
      [user-details (:user_data event)]
      [booking-update-link (:user_secret_id event)
       "Muokkaa käyttäjän tämänhetkisiä varauksia"]]]))

(defn render-event-log [ratom]
  (let [events (:event-log @ratom)]
    [:div.event-log-area
     [:h2 "Tapahtumaloki"]
     [:table.event-log-table
      [:thead.event-heading-row
       [:tr
        [:th.event-heading "Tapahtuman aikaleima"]
        [:th.event-heading "Varattu päivä"]
        [:th.event-heading "Toimenpide"]
        [:th.event-heading "Käyttäjän tunniste"]
        [:th.event-heading "Käyttäjän tiedot tapahtumassa"]]]
      [:tbody
       (map (fn [event]
              ^{:key (str "event-" (:log_id event))}
              [render-event-row event])
            events)]]]))

(defn page-modes []
  [:div
   [:p
    [:a {:href "#"
         :on-click #(set-page-state :bookings)} "Varaukset"]]
   [:p
    [:a {:href "#"
         :on-click #(set-page-state :event-log)} "Tapahtumaloki"]]
   [:p
    [:a {:href "/"} "Tee uusi varaus"]]])

(defn page [ratom]
  [:div
   [page-modes]
   [u/success_status_area ratom]
   [u/error_status_area ratom]
   [render-booking-calendar ratom]
   [render-event-log ratom]])

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
  (reload))
