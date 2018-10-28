(ns m-cal.validation
  (:require [clojure.spec.alpha :as s]))

;; Validator is a function that returns validated and possibly transformed input.
;; Errors are signalled by returning key ::validation-error

(defn spec-validator
  "Returns a validator that returns spec-validated input or ::validation-error"
  [spec]
  (fn [input]
    (if (s/valid? spec input)
      input
      {::validation-error (s/explain-data spec input)})))

(defn func-validator
  "Returns a validator that invokes function f on the input, turning exceptions into validation errors.
   Returns the output of (f input)."
  [f]
  (fn [input]
    (try
      (f input)
    (catch Exception e
      {::validation-error {:message (.getMessage e)}}))))

(defn assert-validator
  "Returns a validator that invokes function f on the input, turning exceptions into validation errors.
   Returns the original input."
  [f]
  (fn [input]
    (try
      (f input)
      input
      (catch Exception e
        {::validation-error {:message (.getMessage e)}}))))

(defn chain
  "chain a sequence of validators, so input passes through all of them. Stop at first validation error."
  [validators]
  (fn [original-input]
    (loop [v (first validators)
           vs (rest validators)
           input original-input]
      (let [validated-input (v input)]
        (cond
          (::validation-error validated-input)
          validated-input ;; error - stop here

          (not (seq vs))
          validated-input ;; end of validators

          :else
          (recur (first vs) (rest vs) validated-input))))))