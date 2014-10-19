(ns m-cal.google-auth
  (:import (java.io File)
           (java.util Date)
           (java.util Collections)
           (com.google.api.client.googleapis.auth.oauth2 GoogleCredential)
           (com.google.api.client.googleapis.auth.oauth2 GoogleCredential$Builder)
           (com.google.api.client.googleapis.javanet GoogleNetHttpTransport)
           (com.google.api.client.http HttpTransport)
           (com.google.api.client.json JsonFactory)
           (com.google.api.client.json.jackson2 JacksonFactory)
           (com.google.api.services.calendar CalendarScopes)
           (com.google.api.services.calendar Calendar$Builder)
           (com.google.api.services.calendar.model Event)
           (com.google.api.services.calendar.model EventDateTime)
           (com.google.api.client.util DateTime)
           (java.util.logging Logger)
           (java.util.logging Level)
           (java.util.logging ConsoleHandler)))

(def jsonFactory (JacksonFactory/getDefaultInstance))
(def httpTransport (GoogleNetHttpTransport/newTrustedTransport))
(def applicationName "M-Calendar/0.1")
(def serviceAccountEmail (System/getenv "SERVICE_ACCOUNT_EMAIL"))
(def calendarId (System/getenv "CALENDAR_ID"))

(defn authenticated-calendar
  []
  (let
      [credential (.. (GoogleCredential$Builder.)
                      (setTransport httpTransport)
                      (setJsonFactory jsonFactory)
                      (setServiceAccountId serviceAccountEmail)
                      (setServiceAccountPrivateKeyFromP12File
                       (File. "../keys/M-Calendar-d2ecb70936ae.p12"))
                      (setServiceAccountScopes (Collections/singleton CalendarScopes/CALENDAR))
                      (build))]
    (.. (Calendar$Builder. httpTransport jsonFactory credential)
        (setApplicationName applicationName)
        (build))))

(def default-calendar (authenticated-calendar))

(defn print-calendar-list
  []
  (let [calendars (.. default-calendar
                      (calendarList)
                      (list)
                      (execute)
                      (getItems))]
    (when calendars
      (doseq [calendar calendars]
        (println calendar)))))

(defn list-bookings
  []
  (let [events (.. default-calendar
                   (events)
                   (list calendarId)
                   (execute)
                   (getItems))]
    (when events
      (doseq [event events]
        (println event)))))

(defn make-event-date
  [booking-date]
    (. (EventDateTime.) (setDate (DateTime. booking-date))))

(defn add-booking
  [booking-date name email boat]
  (let [event (Event.)
        event-date (make-event-date booking-date)]
    (doto event
      (.setSummary name)
      (.setDescription (str boat " " email))
      (.setStart event-date)
      (.setEnd event-date)
      (.setStatus "confirmed"))
    (let [result (.. default-calendar
                     (events)
                     (insert calendarId event)
                     (execute))]
      (println "Event result" result))))
