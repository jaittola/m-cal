(ns m-cal.util)

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
       (catch Exception e nil)))
