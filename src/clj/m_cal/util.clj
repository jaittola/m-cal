(ns m-cal.util
  (:require [m-cal.config :as c]
            [clj-time.core :as t]
            [clj-time.format :as tf]))

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
       (catch Exception e nil)))

(def helsinki-tz (t/time-zone-for-id "Europe/Helsinki"))

(defn parse-date-string [date-string]
  (let [date (tf/parse-local-date date-string)]
    (if date
      date
      (throw (ex-info (str "incorrect date string: " date-string) {})))))

(defn today []
  (if (c/is-testing)
    (c/testing-date)
    (-> (t/now)
        (t/to-time-zone helsinki-tz)
        (.toLocalDate)
        str)))
