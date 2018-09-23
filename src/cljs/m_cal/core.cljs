(ns m-cal.core
  (:require [reagent.core :as reagent]
            [clojure.string :as string]
            [cljs-time.core :as time]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [m-cal.utils :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(def app-locale "FI-fi")
(def weekdays ["Su" "Ma" "Ti" "Ke" "To" "Pe" "La"])
(def min-input-len 5)
(def email-validation-regex #"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])")

(defonce app-state
  (reagent/atom {:required_days 2
                 :selected_days []
                 :name ""
                 :email ""
                 :yacht-name ""
                 :first-date "2018-05-12"
                 :last-date "2018-12-31"
                 :booked-dates []
                 :error-status nil
                 :success-status nil
                 :request-in-progress false}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utilities for maintaining the state and input validation

(defn set-error-status [new-status]
  (swap! app-state assoc :error-status new-status :success-status nil))

(defn set-success-status [new-status]
  (swap! app-state assoc :error-status nil :success-status new-status))

(defn clear-statuses []
  (swap! app-state assoc :error-status nil :success-status nil))

(defn update-state-from-text-input [field on-change-event]
  (swap! app-state assoc field (-> on-change-event .-target .-value)))

(defn add-date-selection [date]
  (clear-statuses)
  (swap! app-state (fn [state new-date]
                     (->> (conj (:selected_days state) new-date)
                          (distinct)
                          (assoc state :selected_days)))
         date))

(defn remove-date-selection [date]
  (clear-statuses)
  (swap! app-state (fn [state new-date]
                     (->> (filter #(not (= date %)) (:selected_days state))
                          (assoc state :selected_days)))
         date))

(defn set-booked-dates [new-dates]
  (swap! app-state assoc :booked-dates new-dates))

(defn clear-user []
  (swap! app-state assoc
         :selected_days []
         :name ""
         :email ""
         :yacht-name ""))

(defn clear-selected-days []
  (swap! app-state assoc
         :selected_days []))

(defn set-request-in-progress [in-progress]
  (swap! app-state assoc
         :request-in-progress in-progress))

(defn simple-input-validation [value]
  (let [string-len (count value)]
    (cond
      (== string-len 0) :empty
      (< string-len min-input-len) :bad
      :else :good)))

(defn email-input-validation [value]
  (let [string-len (count value)]
    (cond
      (== string-len 0) :empty
      (< string-len min-input-len) :bad
      :else (if (re-matches email-validation-regex value)
              :good
              :bad))))

(defn all-input-validates [ratom]
  (and (every? #(= :good %)
               [(simple-input-validation (:name @ratom))
                (simple-input-validation (:yacht-name @ratom))
                (email-input-validation (:email @ratom))])
       (>= (count (:selected_days @ratom)) (:required_days @ratom))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; HTTP calls to the booking API

(defn load-bookings []
  (go (let [response (<! (http/get "/bookings/api/1/bookings"))
            bookings (when (= (:status response) 200)
                       (:bookings (:body response)))]
        (if bookings
          (set-booked-dates bookings)
          (do
            (set-booked-dates [])
            (set-error-status "Varaustietojen lataaminen epäonnistui. Yritä myöhemmin uudelleen"))))))

(defn save-bookings [ratom]
  (go (do
        (set-request-in-progress true)
        (let [response (<! (http/post "/bookings/api/1/bookings"
                                      {:json-params {:name (:name @ratom)
                                                     :email (:email @ratom)
                                                     :yacht_name (:yacht-name @ratom)
                                                     :booked_dates (:selected_days @ratom)}}))
              status (:status response)
              bookings (:all_bookings (:body response))]
          (set-request-in-progress false)
          (when bookings
            (set-booked-dates bookings))
          (case status
            200 (do
                  (clear-user)
                  (set-success-status "Varauksesi on talletettu. Varausvahvistus on lähetetty sähköpostiisi. Varausvahvistuksessa on linkki, jota voit käyttää varaustesi muokkaamiseen."))
            409 (do
                  (clear-selected-days)
                  (set-error-status "Joku muu ehti valita samat päivät kuin sinä. Valitse uudet päivät."))
            (do
              (clear-user)
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
             :class (input-class-name simple-input-validation (:yacht-name @ratom))
             :value (:yacht-name @ratom)
             :on-change #(update-state-from-text-input :yacht-name %)}]
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
  [:div {:dangerouslySetInnerHTML {:__html "&nbsp;"}}])

(defn selection_area [ratom]
  (let [days (vec (sort (:selected_days @ratom)))]
    [:div.selected_days_area
     [:div.selected_days_title "Valitsemasi vartiovuorot:"]
     [:div.selected_days_selections
      (->> (range (:required_days @ratom))
           (map (fn [dayidx]
                  (let [day (get days dayidx)]
                       ^{:key (str "day-" dayidx)}
                       [:div.selected_days_selections
                        (if (some? day) (u/format-date day app-locale weekdays)
                            [blank-element])]))))]]))

(defn selection_button_area [ratom]
  [:div.select_button_container
   [:button.selection {:disabled (or
                                  (:request-in-progress @ratom)
                                  (not (all-input-validates ratom)))
                       :on-click #(save-bookings ratom)}
    "Varaa valitsemasi vuorot"]])

(defn status-area [status-property class ratom]
   (let [status (status-property @ratom)]
     (if (some? status)
       [:div {:class class} status]
       [:div])))

(defn success_status_area [ratom]
  [status-area :success-status "success_status_area" ratom])

(defn error_status_area [ratom]
  [status-area :error-status "error_status_area" ratom])

(defn find-booking-for [bookings day]
  (first (filter #(== (:isoformat day) (:date %)) bookings)))

(defn booking-or-free [today daydata ratom] ""
  (let [booking (:booking daydata)
        isoday (:isoformat (:day daydata))
        theday (:date (:day daydata))
        is-in-future (time/after? theday today)
        is-booked-for-me (some #(== % isoday) (:selected_days @ratom))]
    (cond
      (some? booking) (:bookee booking)
      (and is-booked-for-me is-in-future) [:button
                                          {:on-click #(remove-date-selection isoday)}
                                          "Poista valinta"]
      (and is-booked-for-me (not is-in-future)) [:div "Oma varauksesi"]
      (and (not is-booked-for-me) (not is-in-future)) blank-element
      :else [:button
             {:on-click #(add-date-selection isoday)
              :disabled (>= (count (:selected_days @ratom)) (:required_days @ratom))}
             "Valitse tämä päivä"])))

(defn make-monthly-calendar-seq [first-date last-date]
  (let [calendar-by-month (->> (u/make-calendar-seq first-date last-date)
                               (group-by :month))
        months (sort (keys calendar-by-month))]
    (map (fn [month]
           {:monthname (get u/months (dec month))
            :days (get calendar-by-month month)})
         months)))

(def make-calendar-seq-memo (memoize make-monthly-calendar-seq))

(defn render-calendar [ratom]
  (let [first-date (:first-date @ratom)
        last-date (:last-date @ratom)
        booked-dates (:booked-dates @ratom)
        today (time/now)
        bookings (map (fn [booking]
                        {:date (:booked_date booking)
                         :bookee [:div (:name booking) [:br] (:yachtname booking)]})
                      booked-dates)]
    [:div.calendar-area
     [:h2 "Varauskalenteri"]
     [:div.calendar-container
      (->> (make-calendar-seq-memo first-date last-date)
           (map (fn [mo]
                  (let [monthname (:monthname mo)
                        days (:days mo)]
                    ^{:key (str "month-" monthname)}
                    [:div.calendar-month
                     [:h3 monthname]
                     [:table.calendar-month-table
                      [:tbody
                       (->> days (map (fn [day]
                                        (let [booking (find-booking-for bookings day)]
                                          {:day day
                                           :booking booking
                                           :key (str "calendarday-" (:dateidx day))
                                           :classes (string/join " " (filter some?
                                                                             ["calendar-day"
                                                                              (when (== 7 (:weekday day)) "calendar-sunday")
                                                                              (if (some? booking) "calendar-taken" "calendar-free")]))})))
                            (map (fn [daydata]
                                   ^{:key (:key daydata)}
                                   [:tr
                                    [:td {:class (str "calendar-date-cell " (:classes daydata))}
                                     (:formatted-date (:day daydata))]
                                    [:td {:class (str "calendar-booking-cell " (:classes daydata))}
                                     (booking-or-free today daydata ratom)]]))
                            (doall))]]])))
           (doall))]]))

(defn logout-link []
  [:div.logout_header
   [:div.push_right]
   [:div.logout_link
    [:a {:href "logout"} "Kirjaudu ulos"]]])

(defn page [ratom]
  [:div
   [logout-link]
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
   ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (println "dev mode")
    ))

(defn reload []
  (load-bookings)
  (reagent/render [page app-state]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (reload))
