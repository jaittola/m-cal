(ns m-cal.email-confirmation
  (:require [m-cal.config :as config]
            [m-cal.email-confirmation-sendgrid :as email-sendgrid]
            [m-cal.db-common :as db-common]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-time.core :as time]
            [clj-time.format :as timef]
            [autoclave.core :as autoclave]))

(def text-template-resource-file "templates/confirmation.txt")
(def html-template-resource-file "templates/confirmation.html")

(def ymd-formatter (timef/formatters :year-month-day))
(def fi-formatter-long (timef/formatter "dd.MM.YYYY"))
(def weekdays ["Su" "Ma" "Ti" "Ke" "To" "Pe" "La" "Su"])

(defn format-date [isodate]
  (let [date (timef/parse ymd-formatter isodate)
        weekday-str (nth weekdays (time/day-of-week date))]
    (str weekday-str " " (timef/unparse fi-formatter-long date))))

(defn get-templated-message [name email yacht-name update-link selected-dates contact]
  (let [text-message-template (slurp (io/resource text-template-resource-file))
        html-message-template (slurp (io/resource html-template-resource-file))
        formatted-selected-dates (map #(format-date %) (sort selected-dates))
        name-out (autoclave/html-sanitize name)
        email-out (autoclave/html-sanitize email)
        yacht-name-out (autoclave/html-sanitize yacht-name)
        text-message-content (-> text-message-template
                                 (.replace "[booked_dates]" (string/join "\n" formatted-selected-dates))
                                 (.replace "[name]" name-out)
                                 (.replace "[yacht_name]" yacht-name-out)
                                 (.replace "[email]" email-out)
                                 (.replace "[contact]" contact)
                                 (.replace "[update_link]" update-link))
        html-message-content (-> html-message-template
                                 (.replace "[booked_dates]" (string/join "<br>" formatted-selected-dates))
                                 (.replace "[name]" name-out)
                                 (.replace "[yacht_name]" yacht-name-out)
                                 (.replace "[email]" email-out)
                                 (.replace "[contact]" contact)
                                 (.replace "[update_link]" update-link))]
    (println "Text message content is" text-message-content)
    (println "HTML message content is" html-message-content)
    {:html html-message-content
     :text text-message-content}))

(defn send-confirmation [name email yacht-name update-link selected-dates]
  (let [message (get-templated-message name email yacht-name update-link selected-dates (config/email-contact-addr))
        {:keys [from subject]} (config/email-parameters)]
    (if (and from subject)
      (email-sendgrid/send-email-confirmation from subject email message)
      (do
        (println "Not sending e-mail confirmation because both 'from' and 'subject' must be defined")
        db-common/log-entry-email-disabled))))
