(ns m-cal.config
  (:require
   [environ.core :refer [env]]
   [m-cal.util :refer [parse-int]]
))

(defn calendar-config []
  {:first_date (env :first-booking-date)
   :last_date (env :last-booking-date)
   :required_days (parse-int (env :required-days))})

(defn verify-config []
  (when (some #(nil? %)
              [(env :first-booking-date)
               (env :last-booking-date)
               (parse-int (env :required-days))])
    (throw (Exception. "You must define environment variables FIRST_BOOKING_DATE, LAST_BOOKING_DATE and REQUIRED_DAYS"))))
