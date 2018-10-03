(ns m-cal.config
  (:require
   [environ.core :refer [env]]
   [m-cal.util :refer [parse-int]]
))

(defn required-days []
  (parse-int (env :required-days)))

(defn calendar-config []
  {:first_date (env :first-booking-date)
   :last_date (env :last-booking-date)
   :required_days (required-days)})

(defn sendgrid-email-config []
  {:api-key (env :sendgrid-api-key)
   :disabled (env :email-disabled)})

(defn email-contact-addr []
  (or (env :contact-addr) ""))

(defn email-parameters []
  {:from (env :email-confirmation-from)
   :subject (env :email-confirmation-subject)})

(defn base-uri-for-updates []
  (env :base-uri-for-updates))

(defn verify-config []
  (when (some #(nil? %)
              [(env :first-booking-date)
               (env :last-booking-date)
               (required-days)
               (env :base-uri-for-updates)])
    (throw (Exception. "You must define environment variables FIRST_BOOKING_DATE, LAST_BOOKING_DATE, REQUIRED_DAYS, and BASE_URI_FOR_UPDATES"))))
