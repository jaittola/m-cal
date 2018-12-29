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
          (set-bookings bookings)
          (do
            (set-bookings [])
            (set-error-status "Varaustietojen lataaminen epäonnistui. Yritä myöhemmin uudelleen"))))))

(defn set-page-state [new-state]
  (swap! app-state assoc
         :bookings []
         :event-log [])
  (case new-state
    :bookings (load-bookings)
    nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page layout

(defn booking-update-link [booking]
  (let [secret-id (:secret_id booking)]
    (str "/bookings/index?user=" secret-id)))

(defn booking-or-free [today daydata ratom] ""
  (let [booking (:booking daydata)]
    (if booking
      [:div
       (:name booking) [:br]
       (:yacht_name booking) [:br]
       (:phone booking) [:br]
       (:email booking) [:br]
       [:a {:href (booking-update-link booking)} "Muokkaa"]]
      [u/blank-element])))

(defn render-booking-calendar [ratom]
  (let [bookings (:bookings @ratom)
        first-date (:first-date @ratom)
        last-date (:last-date @ratom)]
    (if (seq bookings)
      [u/render-calendar ratom first-date last-date bookings booking-or-free]
      [:div])))

(defn render-event-log [ratom]
  [:div])

(defn page-modes []
  [:div
   [:p
    [:a {:href "#"
         :on-click #(set-page-state :bookings)} "Varaukset"]]
   [:p
    [:a {:href "#"
         :on-click #(set-page-state :event-log)} "Tapahtumaloki"]]
   [:p
    [:a {:href "/bookings/index"} "Tee uusi varaus"]]])

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
