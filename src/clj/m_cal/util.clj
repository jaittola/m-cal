(ns m-cal.util
  (:require [clj-time.format :as tf]))

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
       (catch Exception e nil)))

(defn parse-date-string [date-string]
  (let [date (tf/parse-local-date date-string)]
    (if date
      date
      (throw (ex-info (str "incorrect date string: " date-string) {})))))

