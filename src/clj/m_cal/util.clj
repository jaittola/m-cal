(ns m-cal.util
  (:require [environ.core :refer [env]]
            [clj-time.core :as t]
            [clj-time.format :as tf]))

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
       (catch Exception e nil)))

(def helsinki-tz (t/time-zone-for-id "Europe/Helsinki"))

(defn parse-date-string [date-string]
  (tf/parse-local-date date-string))

(defn today-as-date []
  (if (env :testing)
    (-> (env :testing-date)
        parse-date-string)
    (-> (t/now)
        (t/to-time-zone helsinki-tz)
        (.toLocalDate))))
