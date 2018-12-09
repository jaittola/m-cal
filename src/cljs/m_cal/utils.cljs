(ns m-cal.utils
  (:require
   [cljs-time.core :as time]
   [cljs-time.format :as timef]))

(def ymd-formatter (timef/formatters :year-month-day))
(def ym-formatter (timef/formatter "YYYY-MM"))
(def fi-formatter (timef/formatter "dd.MM"))
(def fi-formatter-long (timef/formatter "dd.MM.YYYY"))
(def weekdays ["Su" "Ma" "Ti" "Ke" "To" "Pe" "La" "Su"])
(def months ["Tammikuu" "Helmikuu" "Maaliskuu" "Huhtikuu"
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
