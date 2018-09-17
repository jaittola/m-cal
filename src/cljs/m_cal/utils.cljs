(ns m-cal.utils
  (:require
   [cljs-time.core :as time]
   [cljs-time.coerce :as timeco]
   [cljs-time.format :as timef]))

(def ymd-formatter (timef/formatters :year-month-day))
(def fi-formatter (timef/formatter "dd.MM"))
(def weekdays ["Su" "Ma" "Ti" "Ke" "To" "Pe" "La" "Su"])
(def months ["Tammikuu" "Helmikuu" "Maaliskuu" "Huhtikuu"
             "Toukokuu" "Kesäkuu" "Heinäkuu" "Elokuu"
             "Syyskuu" "Lokakuu" "Marraskuu" "Joulukuu"])

(defn format-date [date locale weekdays]
  (let [d (js/Date. date)
        weekday (nth weekdays (-> d .getDay))]
    (str weekday " " (-> d (.toLocaleDateString locale)))))

(defn format-date2 [date weekday]
  (let [weekday-str (nth weekdays weekday)]
    (str weekday-str " " (timef/unparse fi-formatter date))))

(defn make-calendar-seq [first-date last-date]
  (let [first-date-time (timef/parse ymd-formatter first-date)
        last-date-time (timef/parse ymd-formatter last-date)]
    (loop [dateidx 0
           days[]]
      (let [next-date (time/plus first-date-time (time/days dateidx))
            next-date-time (timeco/to-long next-date)
            next-date-isoformat (timef/unparse ymd-formatter next-date)
            weekday (time/day-of-week next-date)
            month (time/month next-date)]
        (if (time/after? next-date last-date-time)
          days
          (recur (inc dateidx) (conj days {:dateidx dateidx
                                           :date next-date
                                           :formatted-date (format-date2 next-date weekday)
                                           :weekday weekday
                                           :month month
                                           :isoformat next-date-isoformat})))))))
