(ns m-cal.bookings
  (:require [m-cal.google-cal-api :as cal]
            [postmark.core :as postmark :refer [postmark]]
            [clojure.string :as string :refer [join blank?]]
            [clj-time.format :as f]))

(def machine-formatter (f/formatters :year-month-day))
(def dmy-formatter (f/formatter "d.M.YYYY"))

(defn reformat-to-dmy
  [date]
  (f/unparse dmy-formatter (f/parse machine-formatter date)))

(defn format-dates
  [dates]
  (join "\n"
        (map reformat-to-dmy dates)))

(def mail-template
  "Hei,

Olet varannut Särkälle vartiovuorot seuraaviksi päiviksi:

[booked-dates]

Varaajan nimi: [name]
Veneen nimi: [boat]
Varaajan sähköpostiosoite: [email]

Jos haluat lisätietoja tai vartiovuoroissa on muuten epäselvyyksiä,
ota yhteyttä Merenkävijöiden toimistoon joko puhelimitse tai
sähköpostitse.
")

(defn send-confirm-email
  [{name :name
    email :email
    boat :boat
    dates :dates}]
  (let [postmark-api-key (System/getenv "POSTMARK_API_KEY")
        sender-addr (System/getenv "EMAIL_SENDER_ADDR")
        substituted (-> mail-template
                        (.replace "[booked-dates]" (format-dates dates))
                        (.replace "[name]" name)
                        (.replace "[boat]" boat)
                        (.replace "[email]" email))]
    (println substituted)
    (if (or (blank? postmark-api-key)
            (blank? sender-addr))
      (println "POSTMARK_API_KEY or EMAIL_SENDER_ADDR is empty. Mail not sent.")
      ((postmark postmark-api-key sender-addr)
       {
        :to email
        :reply-to sender-addr
        :subject "Vartiovuorovaraukset"
        :text substituted
        }))))

(defn insert-bookings
  [msg-body]
  (let [insert-result (cal/insert-bookings msg-body)]
    (when (= (-> insert-result :body :result) "Ok")
      (send-confirm-email (insert-result :body)))
    insert-result))
