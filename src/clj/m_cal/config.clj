(ns m-cal.config
  (:require [environ.core :refer [env]]))

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
       (catch Exception e nil)))

(defn port []
  (or (parse-int (env :port)) 3000))

(defn database-uri []
  (env :database-url))

(defn required-days []
  (parse-int (env :required-days)))

(defn buffer-days-for-cancel []
  (parse-int (or (env :booking-cancel-buffer) "2")))

(defn calendar-config []
  {:first_date (env :first-booking-date)
   :last_date (env :last-booking-date)
   :required_days (required-days)})

(defn default-user []
  (env :default-user))

(defn sendgrid-email-config []
  {:api-key (env :sendgrid-api-key)
   :disabled (env :email-disabled)})

(defn email-contact-addr []
  (or (env :contact-addr) ""))

(defn email-parameters []
  {:from (env :email-confirmation-from)
   :subject (env :email-confirmation-subject)})

(defn require-tls []
  (some? (env :require-tls)))

(defn base-uri-for-updates []
  (env :base-uri-for-updates))

(defn update-uri [user-id]
  (str (base-uri-for-updates) "?user=" (:secret_id user-id)))

(defn verify-config []
  (let [cal-conf (calendar-config)]
    (when (some #(nil? %)
                [(database-uri)
                 (:first_date cal-conf)
                 (:last_date cal-conf)
                 (:required_days cal-conf)
                 (base-uri-for-updates)
                 (default-user)])
      (throw (Exception. "You must define environment variables DATABASE_URL FIRST_BOOKING_DATE, LAST_BOOKING_DATE, REQUIRED_DAYS, and BASE_URI_FOR_UPDATES DEFAULT_USER")))))

(defn is-testing []
  (some? (env :testing)))

(defn testing-date []
  (if (and (is-testing) (env :testing-date))
    (env :testing-date)
    (throw (ex-info "testing-date was called but TESTING_DATE environment variable is not set"))))
