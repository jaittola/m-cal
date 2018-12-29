(ns m-cal.utils
  (:require [cljs-time.core :as time]
            [cljs-time.format :as timef]
            [clojure.string :as string]))

(def ymd-formatter (timef/formatters :year-month-day))
(def ym-formatter (timef/formatter "YYYY-MM"))
(def fi-formatter (timef/formatter "dd.MM"))
(def fi-formatter-long (timef/formatter "dd.MM.YYYY"))
(def weekdays ["Su" "Ma" "Ti" "Ke" "To" "Pe" "La" "Su"])
(def month-names ["Tammikuu" "Helmikuu" "Maaliskuu" "Huhtikuu"
             "Toukokuu" "Kesäkuu" "Heinäkuu" "Elokuu"
             "Syyskuu" "Lokakuu" "Marraskuu" "Joulukuu"])

(defn parse-ymd [isodate]
  (timef/parse ymd-formatter isodate))

(defn format-date [isodate]
  (let [date (parse-ymd isodate)
        weekday-str (nth weekdays (time/day-of-week date))]
    (str weekday-str " " (timef/unparse fi-formatter-long date))))

(defn format-date2 [date weekday]
  (let [weekday-str (nth weekdays weekday)]
    (str weekday-str " " (timef/unparse fi-formatter date))))

(defn make-calendar-seq [first-date last-date]
  (let [first-date-time (parse-ymd first-date)
        last-date-time (parse-ymd last-date)]
    (loop [dateidx 0
           days[]]
      (let [next-date (time/plus first-date-time (time/days dateidx))
            next-date-isoformat (timef/unparse ymd-formatter next-date)
            weekday (time/day-of-week next-date)
            month (time/month next-date)
            year (time/year next-date)]
        (if (time/after? next-date last-date-time)
          days
          (recur (inc dateidx) (conj days {:dateidx dateidx
                                           :date next-date
                                           :formatted-date (format-date2 next-date weekday)
                                           :weekday weekday
                                           :month month
                                           :year-month (timef/unparse ym-formatter next-date)
                                           :isoformat next-date-isoformat})))))))

(defn make-monthly-calendar-seq [first-date last-date]
  (let [calendar-by-month (->> (make-calendar-seq first-date last-date)
                               (group-by :year-month))
        months-in-calendar (sort (keys calendar-by-month))]
    (map (fn [month]
           (let [days (get calendar-by-month month)]
             {:monthname (get month-names (dec (:month (first days))))
              :days days}))
         months-in-calendar)))

(def make-calendar-seq-memo (memoize make-monthly-calendar-seq))

(defn status-area [status-property class ratom]
   (let [status (status-property @ratom)]
     (if status
       [:div {:class class} status]
       [:div])))

(defn success_status_area [ratom]
  [status-area :success_status "success_status_area" ratom])

(defn error_status_area [ratom]
  [status-area :error_status "error_status_area" ratom])

(defn blank-element []
  [:div.blank-element {:dangerouslySetInnerHTML {:__html "&nbsp;"}}])

(defn find-booking-for [bookings day]
  (first (filter #(== (:isoformat day) (:booked_date %)) bookings)))

(defn render-day [daydata today ratom render-booking-details]
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
      [render-booking-details today daydata ratom]]]))

(defn render-month [{:keys [monthname days]} booked-dates today ratom render-booking-details]
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
                 ^{:key (:key daydata)} [render-day
                                         daydata
                                         today
                                         ratom
                                         render-booking-details]))
          (doall))]]])

(defn render-calendar [ratom first-date last-date bookings render-booking-details]
  (let [today (time/now)]
    [:div.calendar-area
     [:h2 "Varauskalenteri"]
     (when (and first-date last-date)
       [:div.calendar-container
        (->> (make-calendar-seq-memo first-date last-date)
             (map (fn [month]
                    ^{:key (str "month-" (:monthname month))}
                    [render-month month bookings today ratom render-booking-details]))
             (doall))])]))

