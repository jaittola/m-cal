(ns m-cal.core
  (:require [reagent.core :as reagent]
            [clojure.string :as string]
            [cljs-time.core :as time]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [m-cal.utils :as u]
            [cemerick.url :refer (url url-encode)]
            [cljsjs.babel-polyfill])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(def min-input-len 5)
(def email-validation-regex #"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])")
(def phone-number-validation-regex #"^((\+([0-9] *){1,3})|0)([0-9] *){6,15}$")

(defonce app-state
  (reagent/atom {:required_days 2
                 :selected_dates []
                 :name ""
                 :email ""
                 :phone ""
                 :yacht_name ""
                 :user_private_id nil
                 :user_public_id nil
                 :first_date nil
                 :last_date nil
                 :booked_dates []
                 :error_status nil
                 :success_status nil
                 :request_in_progress false}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for maintaining the state and input validation

(defn set-error-status [new-status]
  (swap! app-state assoc :error_status new-status :success_status nil))

(defn set-success-status [new-status]
  (swap! app-state assoc :error_status nil :success_status new-status))

(defn clear-statuses []
  (swap! app-state assoc :error_status nil :success_status nil))

(defn update-state-from-text-input [field on-change-event]
  (swap! app-state assoc field (-> on-change-event .-target .-value)))

(defn add-date-selection [date]
  (clear-statuses)
  (swap! app-state (fn [state new-date]
                     (->> (conj (:selected_dates state) new-date)
                          (distinct)
                          (assoc state :selected_dates)))
         date))

(defn remove-date-selection [date]
  (clear-statuses)
  (swap! app-state (fn [state new-date]
                     (->> (filter #(not (= date %)) (:selected_dates state))
                          (assoc state :selected_dates)))
         date))

(defn set-booked-dates [new-dates]
  (swap! app-state assoc :booked_dates new-dates))

(defn clear-user []
  (swap! app-state assoc
         :selected_dates []
         :name ""
         :email ""
         :phone ""
         :yacht_name ""
         :user_private_id nil
         :user_public_id nil))

(defn set-user [user selected_dates]
  (swap! app-state assoc
         :selected_dates []
         :name (:name user)
         :email (:email user)
         :phone (:phone user)
         :yacht_name (:yacht_name user)
         :user_private_id (:secret_id user)
         :user_public_id (:id user)
         :selected_dates (or selected_dates [])))

(defn set-selected-dates [selected_dates]
  (swap! app-state assoc
         :selected_dates (or selected_dates [])))

(defn set-user-private-id [private_id]
  (swap! app-state assoc
         :user_private_id private_id))

(defn clear-selected-days []
  (swap! app-state assoc
         :selected_dates []))

(defn set-request-in-progress [in-progress]
  (swap! app-state assoc
         :request_in_progress in-progress))

(defn set-calendar-config [config]
  (swap! app-state assoc
         :first_date (:first_date config)
         :last_date (:last_date config)
         :required_days (or (:required_days config) 2)))

(defn simple-input-validation [value]
  (let [string-len (count value)]
    (cond
      (== string-len 0) :empty
      (< string-len min-input-len) :bad
      :else :good)))

(defn re-input-validation [field-validation-regex value]
  (let [string-len (count value)]
    (cond
      (== string-len 0) :empty
      (< string-len min-input-len) :bad
      :else (if (re-matches field-validation-regex value)
              :good
              :bad))))

(defn email-input-validation [value]
  (re-input-validation email-validation-regex value))

(defn phone-input-validation [value]
  (re-input-validation phone-number-validation-regex value))

(defn all-input-validates [ratom]
  (and (every? #(= :good %)
               [(simple-input-validation (:name @ratom))
                (simple-input-validation (:yacht_name @ratom))
                (email-input-validation (:email @ratom))
                (phone-input-validation (:phone @ratom))])
       (>= (count (:selected_dates @ratom)) (:required_days @ratom))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HTTP calls to the booking API

(defn load-bookings [ratom]
  (go (let [private-id (:user_private_id @ratom)
            request-uri (if private-id
                          (str "/bookings/api/1/bookings/" private-id)
                          "/bookings/api/1/bookings")
            response (<! (http/get request-uri))
            status-ok (= (:status response) 200)
            body (:body response)
            bookings (when status-ok
                       (:all_bookings body))
            config (when status-ok
                     (:calendar_config body))
            user (when status-ok
                   (:user body))
            selected_dates (when status-ok
                             (:selected_dates body))]
        (if bookings
          (set-booked-dates bookings)
          (do
            (set-booked-dates [])
            (set-error-status "Varaustietojen lataaminen epäonnistui. Yritä myöhemmin uudelleen")))
        (when config
          (set-calendar-config config))
        (when (and selected_dates user)
          (set-user user selected_dates)))))

(defn save-bookings [ratom]
  (go (do
        (set-request-in-progress true)
        (let [private-id (:user_private_id @ratom)
              body {:json-params {:name (:name @ratom)
                                  :email (:email @ratom)
                                  :phone (:phone @ratom)
                                  :yacht_name (:yacht_name @ratom)
                                  :selected_dates (:selected_dates @ratom)}}
              request (if private-id
                        (http/put (str "/bookings/api/1/bookings/" private-id) body)
                        (http/post "/bookings/api/1/bookings" body))
              response (<! request)
              status (:status response)
              body (:body response)
              bookings (:all_bookings body)
              user (:user body)
              selected_dates (:selected_dates body)]
          (set-request-in-progress false)
          (when bookings
            (set-booked-dates bookings))
          (case status
            200 (do
                  (set-user user selected_dates)
                  (set-success-status "Varauksesi on talletettu. Järjestelmä lähettää varausvahvistuksen antamaasi sähköpostiosoitteeseen. Varausvahvistuksessa on linkki, jota voit käyttää varaustesi muokkaamiseen."))
            409 (do
                  (set-selected-dates selected_dates)
                  (set-error-status "Joku muu ehti valita samat päivät kuin sinä. Valitse uudet päivät."))
            (do
              (set-error-status "Varauksien tallettaminen epäonnistui. Yritä myöhemmin uudelleen.")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn input-class-name [validator-function value]
  (case (validator-function value)
    :empty "contact_input"
    :bad "contact_input_bad"
    :good "contact_input"))

(defn instructions []
  [:div.instruction_area
   [:h3 "Ohjeet"]
   [:ol
    [:li.instruction "Syötä nimesi, veneesi nimi ja sähköpostiosoitteesi "
     "allaoleviin kenttiin"]
    [:li.instruction "Valitse kaksi vapaata vartiovuoroa kalenterinäkymästä"]
    [:li.instruction "Paina \"Varaa valitsemasi vuorot\" -nappia"]
    [:li.instruction "Varausjärjestelmä lähettää sähköpostitse vahvistuksen "
     "varauksestasi. Sähköpostiviestissä on WWW-linkki, jota voit käyttää "
     "varauksiesi muokkaamiseen." ]
    ]]
  )

(defn contact_entry [ratom]
  [:div
   [:div.contact_entry
    [:div.contact_title "Nimesi:"]
    [:input {:type "text"
             :class (input-class-name simple-input-validation (:name @ratom))
             :value (:name @ratom)
             :on-change #(update-state-from-text-input :name %)}]
    ]
   [:div.contact_entry
    [:div.contact_title "Veneesi nimi:"]
    [:input {:type "text"
             :class (input-class-name simple-input-validation (:yacht_name @ratom))
             :value (:yacht_name @ratom)
             :on-change #(update-state-from-text-input :yacht_name %)}]
    ]
   [:div.contact_entry
    [:div.contact_title "Puhelinnumerosi:"]
    [:input {:type "tel"
             :maxLength 25
             :class (input-class-name phone-input-validation (:phone @ratom))
             :value (:phone @ratom)
             :on-change #(update-state-from-text-input :phone %)}]
    ]
   [:div.contact_entry
    [:div.contact_title "Sähköpostiosoitteesi:"]
    [:input {:type "email"
             :class (input-class-name email-input-validation (:email @ratom))
             :value (:email @ratom)
             :on-change #(update-state-from-text-input :email %)}]
    ]
   ])

(defn blank-element []
  [:div.blank-element {:dangerouslySetInnerHTML {:__html "&nbsp;"}}])

(defn selected_day [day]
   (if day
     [:div.selected_day
      [:div.selected_day_date (u/format-date day)]
      [:input.booking_cancel_button
       {:type "image"
        :on-click #(remove-date-selection day)
        :src "images/red-x.png"}]]
     [:div.selected_day [blank-element]]))

(defn selection_area [ratom]
  (let [days (vec (sort (:selected_dates @ratom)))]
    [:div.selected_days_area
     [:div.contact_title.selected_days_title "Valitsemasi vartiovuorot:"]
     [:div.selected_days_selections
      (->> (range (:required_days @ratom))
           (map (fn [dayidx]
                  (let [day (get days dayidx)]
                    ^{:key (str "day-" dayidx)}
                    [selected_day day]))))]]))

(defn selection_button_area [ratom]
  (let [updating (some? (:user_private_id @ratom))]
    [:div.select_button_container
     [:button.selection {:disabled (or
                                    (:request_in_progress @ratom)
                                    (not (all-input-validates ratom)))
                         :on-click #(save-bookings ratom)}
      (if updating
        "Tallenna muutokset"
        "Varaa valitsemasi vuorot")]
     (when updating
       [:button.selection {:disabled (:request_in_progress @ratom)
                           :on-click #(load-bookings ratom)}
        "Peruuta muutokset"])]))

(defn status-area [status-property class ratom]
   (let [status (status-property @ratom)]
     (if status
       [:div {:class class} status]
       [:div])))

(defn success_status_area [ratom]
  [status-area :success_status "success_status_area" ratom])

(defn error_status_area [ratom]
  [status-area :error_status "error_status_area" ratom])

(defn find-booking-for [bookings day]
  (first (filter #(== (:isoformat day) (:booked_date %)) bookings)))

(defn booking-details [booking]
  [:div (:name booking) [:br] (:yacht_name booking)])

(defn my-details-for-booking [ratom]
  [:div (:name @ratom) [:br] (:yacht_name @ratom)])

(defn day-details-chosen-cancellable [isoday]
  [:input.booking_checkbox
   {:type "image"
    :on-click #(remove-date-selection isoday)
    :src "images/blue-checkmark.png"}])

(defn day-details-bookable [isoday]
  [:div.booking_checkbox
   {:on-click #(add-date-selection isoday)}])

(defn day-details-free-but-not-bookable []
  [:div.booking_checkbox_appear_disabled
   {:disabled true}])

(defn calendar-cell-booked-for-me [isoday ratom]
  [:div.calendar-booked-content-cell
   [day-details-chosen-cancellable isoday]
   [my-details-for-booking ratom]])

(defn booking-or-free [today daydata ratom] ""
  (let [booking (:booking daydata)
        isoday (:isoformat (:day daydata))
        theday (:date (:day daydata))
        is-in-future (time/after? theday today)
        is-booked-for-me (some #(== % isoday) (:selected_dates @ratom))
        has-required-bookings (>= (count (:selected_dates @ratom)) (:required_days @ratom))]
    (cond
      (and is-booked-for-me is-in-future) [calendar-cell-booked-for-me isoday ratom]
      (and is-booked-for-me (not is-in-future)) [booking-details booking]
      (and booking (not (= (:user_id booking) (:user_public_id @ratom)))) [booking-details booking]
      (and (nil? booking) (not is-in-future)) blank-element
      has-required-bookings [day-details-free-but-not-bookable]
      :else [day-details-bookable isoday])))

(defn make-monthly-calendar-seq [first-date last-date]
  (let [calendar-by-month (->> (u/make-calendar-seq first-date last-date)
                               (group-by :month))
        months (sort (keys calendar-by-month))]
    (map (fn [month]
           {:monthname (get u/months (dec month))
            :days (get calendar-by-month month)})
         months)))

(def make-calendar-seq-memo (memoize make-monthly-calendar-seq))

(defn render-day [daydata today ratom]
  (let [day (:day daydata)
        thedate (:date (:day daydata))
        is-in-past (time/before? thedate today)
        booking (:booking daydata)
        classes (string/join " " (filter some?
                                         ["calendar-day"
                                          (when (== 7 (:weekday day)) "calendar-sunday")
                                          (when is-in-past "calendar-day-past")]))]
    [:tr
     [:td {:class (str "calendar-date-cell " classes)}
      (:formatted-date day)]
     [:td {:class (str "calendar-booking-cell " classes)}
      [booking-or-free today daydata ratom]]]))

(defn render-month [{:keys [monthname days]} booked-dates today ratom]
  [:div.calendar-month
   [:h4 monthname]
   [:table.calendar-month-table
    [:tbody
     (->> days
          (map (fn [day]
                 {:day day
                  :booking (find-booking-for booked-dates day)
                  :key (str "day-" (:dateidx day))}))
          (map (fn [daydata]
                 ^{:key (:key daydata)} [render-day daydata today ratom]))
          (doall))]]])

(defn render-calendar [ratom]
  (let [first-date (:first_date @ratom)
        last-date (:last_date @ratom)
        booked-dates (:booked_dates @ratom)
        today (time/now)]
    [:div.calendar-area
     [:h2 "Varauskalenteri"]
     (when (not (or (nil? first-date) (nil? last-date)))
       [:div.calendar-container
        (->> (make-calendar-seq-memo first-date last-date)
             (map (fn [month]
                    ^{:key (str "month-" (:monthname month))}
                    [render-month month booked-dates today ratom]))
             (doall))])]))

(defn footer []
  [:div.footer
   [:div.footer_element
    [:a.footer_link {:href "http://www.merenkavijat.fi/"} "Merenkävijät ry"]]
   [:div.footer_element
    [:a.footer_link {:href "http://www.merenkavijat.fi/tietosuojaseloste.html"} "Tietosuojaseloste"]]])

(defn logout-link []
  [:div.logout_header
   [:div.push_right]
   [:div.logout_link
    [:a {:href "logout"} "Kirjaudu ulos"]]])

(defn page [ratom]
  [:div
   [:div.header]
   [:h1 "Merenkävijät ry"]
   [:h2 "Särkän vartiovuorojen varaukset"]
   [instructions]
   [contact_entry ratom]
   [selection_area ratom]
   [selection_button_area ratom]
   [success_status_area ratom]
   [error_status_area ratom]
   [render-calendar ratom]
   [footer]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (println "dev mode")
    ))

(defn reload []
  (let [location-url (url (-> js/window .-location .-href))
        user (get (:query location-url) "user")]
    (when user
      (set-user-private-id user)))
  (load-bookings app-state)
  (reagent/render [page app-state]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (reload))
