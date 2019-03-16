(ns m-cal.bookings
  (:require [m-cal.config :as config]
            [m-cal.util :refer [parse-int]]
            [m-cal.db-common :as db-common]
            [m-cal.util :refer [parse-date-string today days-from-today]]
            [m-cal.validation :as validation]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s])
  (:import [org.postgresql.util PSQLException]
           [java.util UUID]))

(hugsql/def-db-fns "app_queries/queries.sql")

(defrecord UserIDs [id secret_id])
(defrecord User [name yacht_name phone email])
(defrecord UserWithIDs [name yacht_name phone email id secret_id])
(defrecord BookingValidation [user selected_dates validation-error])

(defn user-with-ids-from-user [user user-ids]
  (map->UserWithIDs (assoc user
                           :id (:id user-ids)
                           :secret_id (:secret_id user-ids))))

(defn database-insert-user [connection user]
  (first (db-insert-user connection user)))

(defn database-insert-bookings [connection selected_dates user-id]
  (doall (mapcat (fn [day]
                   (db-insert-booking connection
                                      {:booked_date day
                                       :users_id (:id user-id)}))
                 selected_dates)))

(defn database-delete-bookings [connection bookings-to-delete]
  (when (seq bookings-to-delete)
    (let [ids (doall (map #(:booking_id %) bookings-to-delete))]
      (db-delete-booking connection {:ids ids}))))

(defn database-get-user-selections [connection user-id]
  (->> (db-select-user-bookings connection
                                {:user_id user-id})
       (map #(:booked_date %))))

(defn map-dates-to-booking-ids [booking-id-containers selected_dates]
  (map (fn [booking-id-container date]
         {:booking_id (:id booking-id-container)
          :booked_date date})
       booking-id-containers selected_dates))

(defn success-booking-reply [connection user-with-ids selected_dates]
  {:status 200
   :body {:user user-with-ids
          :selected_dates selected_dates
          :all_bookings (db-list-all-bookings connection)
          :calendar_config (config/calendar-config)
          :update_uri (config/update-uri user-with-ids)}})

(defn error-reply [code msg & [bookings-body selected-dates]]
  (let [body (merge {:error_result msg}
                    (when bookings-body) {:all_bookings bookings-body}
                    (when selected-dates {:selected_dates selected-dates}))]
    {:status code
     :body body}))

(defn load-all-bookings []
  (jdbc/with-db-connection [connection @db-common/dbspec]
    (db-list-all-bookings connection)))

(defn load-all-bookings-for-admin []
  (jdbc/with-db-connection [connection @db-common/dbspec]
    (db-list-all-bookings-for-admin connection)))

(defn error-reply-409 [& [user-id]]
  (try (jdbc/with-db-transaction [connection @db-common/dbspec]
         (error-reply 409 "The dates you selected were already booked"
                      (db-list-all-bookings connection)
                      (when user-id (database-get-user-selections connection user-id))))
       (catch PSQLException pse
         (let [se (.getServerErrorMessage pse)
               sqlstate (.getSQLState se)]
           (println "Loading booking data for 409 reply failed: " (.toString pse) "; sqlstate: " sqlstate)
           (error-reply 500 "Loading booking data from database failed")))))

(defn handle-psql-error [pse & [user-id]]
  (let [se (.getServerErrorMessage pse)
        sqlstate (.getSQLState se)]
    (println "Storing bookings to database failed: " (.toString pse) "; sqlstate: " sqlstate)
    (if (= (parse-int sqlstate) db-common/psql-unique-constraint-sqlstate)
      (error-reply-409 user-id)
      (error-reply 500 "Storing bookings to database failed"))))

(defn admin-list-eventlog []
  (try (jdbc/with-db-connection [connection @db-common/dbspec]
         (let [events (db-query-eventlog connection)]
           {:status 200
            :body {:events events}}))
       (catch PSQLException pse
             (handle-psql-error pse))))

(defn list-bookings []
  {:body {:all_bookings (load-all-bookings)
          :calendar_config (config/calendar-config)}})

(defn admin-list-bookings []
  {:body {:all_bookings (load-all-bookings-for-admin)
          :calendar_config (config/calendar-config)}})

(defn list-bookings-with-user [id]
  (if
      (nil? id) (error-reply 400 "Mandatory parameters missing.")
      (try (jdbc/with-db-transaction [connection @db-common/dbspec]
             (let [user-with-ids (first (db-find-user-by-secret-id connection
                                                                   {:secret_id (UUID/fromString id)}))
                   selected-dates (database-get-user-selections connection (:id user-with-ids))]
               (if (nil? user-with-ids) (error-reply 400 "No such user")
                   (success-booking-reply connection
                                          user-with-ids
                                          selected-dates))))
           (catch PSQLException pse
             (handle-psql-error pse)))))

(defn date-str-less-or-eq
  "return true if date-1 is less or equal than date-2. Both are given as date strings (e.g. '2018-03-22')"
  [date-1 date-2]
  (<= (.compareTo date-1 date-2) 0))

(defn date-str-less
  "return true if date-1 is less than date-2. Both are given as date strings (e.g. '2018-03-22')"
  [date-1 date-2]
  (< (.compareTo date-1 date-2) 0))

(defn assert-is-in-range [date first-date last-date]
  (if (not (and (date-str-less-or-eq first-date date)
                (date-str-less-or-eq date last-date)))
    (throw (ex-info (str "date out of range: " date) {}))))

(defn assert-is-in-calendar-range [date]
  (let [{:keys [first_date last_date]} (config/calendar-config)]
    (assert-is-in-range date first_date last_date)))

(defn string-of-at-least [n] (s/and string?
                                    #(>= (.length %) n)))
;; TODO: share this with frontend code
(def email-validation-regex #"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])")

(def local-date-string-regex #"[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]") ; just a format check
(def phone-number-string-regex #"^((\+([0-9] *){1,3})|0)([0-9)(] *){6,20}$")

(s/def ::is-integer #(re-matches #"^[0-9]+$" (str %)))
(s/def ::name (string-of-at-least 5))
(s/def ::yacht_name (string-of-at-least 5))
(s/def ::email (s/and (string-of-at-least 5)
                      #(re-matches email-validation-regex %)))
(s/def ::phone (s/and string?
                      #(re-matches phone-number-string-regex %)))
(s/def ::local-date (s/and string?
                           #(re-matches local-date-string-regex %)))
(s/def ::selected_dates (s/tuple ::local-date ::local-date))
(s/def ::booking (s/keys :req-un [::name ::yacht_name ::email ::phone ::selected_dates]))

(def int-validator (validation/spec-validator-simple ::is-integer))
(def booking-spec-validator (validation/spec-validator ::booking))
(def booking-date-parser-validator (validation/assert-validator
                                     (fn [params]
                                       (doall (map parse-date-string (:selected_dates params))))))
(def booking-date-range-validator (validation/assert-validator
                                    (fn [{:keys [selected_dates]}]
                                      (doall (map assert-is-in-calendar-range selected_dates)))))

;; when inserting or updating bookings we do basic validations for inputs.
;; Note that checking if dates are in future or not are not done here yet.
(def booking-validator (validation/chain [booking-spec-validator
                                          booking-date-parser-validator
                                          booking-date-range-validator]))

(defn validate-booking-input [params]
  (let [validation-result (booking-validator params)
        {:keys [selected_dates :m-cal.validation/validation-error]} validation-result
        user (map->User validation-result)]
    (->BookingValidation user selected_dates validation-error)))

(defn validate-int [input-int]
  (some-> input-int
          int-validator
          Integer.))

(defn assert-bookings-not-within-buffer-days
  [bookings buffer]
  (let [earliest-permitted (days-from-today buffer)]
    (doseq [booked-date bookings]
      (if (date-str-less booked-date earliest-permitted)
        (throw (IllegalArgumentException. "Date is in the past or within the buffer days"))))))

(defn assert-bookings-not-in-the-past
  [bookings]
  (assert-bookings-not-within-buffer-days bookings 0))

(defn map-booked-dates [bookings]
  (map #(:booked_date %) bookings))

(defn update-booking-with-validated-params [connection
                                            user-with-ids
                                            selected_dates
                                            user-login-info]
  (try
    (db-update-user connection
                    user-with-ids)
    (let [user-bookings-in-db (db-select-user-bookings-for-update connection
                                                                  {:user_id (:id user-with-ids)})
          user-bookings-in-db-dates (map-booked-dates user-bookings-in-db)
          bookings-to-delete (filter (fn [booking] (not (some #(= (:booked_date booking) %) selected_dates))) user-bookings-in-db)
          booking-dates-to-delete (map-booked-dates bookings-to-delete)
          bookings-to-add (filter (fn [booking] (not (some #(= booking %) user-bookings-in-db-dates)))
                                  selected_dates)
          _ (assert-bookings-not-in-the-past bookings-to-add)
          _ (assert-bookings-not-within-buffer-days booking-dates-to-delete (config/buffer-days-for-cancel))
          _ (database-delete-bookings connection bookings-to-delete)

          inserted-bookings-ids (database-insert-bookings connection
                                                          bookings-to-add
                                                          user-with-ids)
          dates-to-inserted-booking-ids (map-dates-to-booking-ids inserted-bookings-ids
                                                                  bookings-to-add)
          user-login-id (:user_login_id user-login-info)]
      (if (not (every? empty? [bookings-to-delete bookings-to-add]))
        (do
          (db-common/database-insert-booking-log connection
                                                 bookings-to-delete
                                                 user-with-ids
                                                 db-common/log-entry-booking-release
                                                 user-login-id)
          (db-common/database-insert-booking-log connection
                                                 dates-to-inserted-booking-ids
                                                 user-with-ids
                                                 db-common/log-entry-booking-book
                                                 user-login-id))
        (db-common/database-insert-booking-log-without-date connection
                                                            user-with-ids
                                                            db-common/log-entry-contact-update
                                                            user-login-id))
      (db-add-to-confirmation-queue connection user-with-ids)
      (success-booking-reply connection
                             user-with-ids
                             selected_dates))
    (catch PSQLException pse
      (handle-psql-error pse (:id user-with-ids)))
    (catch IllegalArgumentException e
      (error-reply 400 "It is not possible to modify the requested dates."))))

(defn admin-del-booking-with-validated-id [id user-login-info]
  (try (jdbc/with-db-transaction [connection @db-common/dbspec]
         (let [user-login-id (:user_login_id user-login-info)
               booking (first (db-find-booking-by-id-for-update connection
                                                                {:id id}))
               user-id (:users_id booking)
               user-with-ids (when user-id
                               (first (db-find-user-by-id connection {:id user-id})))]
           (if (and booking user-with-ids)
             (do
               (db-delete-booking connection {:ids [id]})
               (db-common/database-insert-booking-log connection
                                                      [booking]
                                                      user-with-ids
                                                      db-common/log-entry-admin-booking-delete
                                                      user-login-id)
               {:status 200
                :body {:all_bookings (db-list-all-bookings-for-admin connection)}})
             {:status 404})))
       (catch PSQLException pse
         (handle-psql-error pse))))

(defn insert-booking [params user-login-info]
  (let [{:keys [user selected_dates validation-error]} (validate-booking-input params)]
    (if validation-error
      (error-reply 400 validation-error)
      (try (jdbc/with-db-transaction [connection @db-common/dbspec]
             (assert-bookings-not-in-the-past selected_dates)
             (let [user-id (database-insert-user connection user)
                   user-login-id (:user_login_id user-login-info)
                   bookings-ids (database-insert-bookings connection
                                                          selected_dates
                                                          user-id)
                   dates-to-booking-ids (map-dates-to-booking-ids bookings-ids
                                                                  selected_dates)
                   user-details (user-with-ids-from-user user user-id)
                   _ (db-common/database-insert-booking-log connection
                                                            dates-to-booking-ids
                                                            user-details
                                                            db-common/log-entry-booking-book
                                                            user-login-id)]
               (db-add-to-confirmation-queue connection user-id)
               (success-booking-reply connection
                                      (user-with-ids-from-user user user-id)
                                      selected_dates)))
           (catch PSQLException pse
             (handle-psql-error pse))
           (catch IllegalArgumentException e
             (error-reply 400 "Trying to make booking in the past"))))))

(defn update-booking [secret_id params user-login-info]
  (let [{:keys [user selected_dates validation-error]} (validate-booking-input params)]
    (cond
      validation-error (error-reply 400 validation-error)
      (nil? secret_id) (error-reply 400 "Mandatory parameters missing.")
      :else (try (jdbc/with-db-transaction [connection @db-common/dbspec]
                   (let [user-from-db (first (db-find-user-by-secret-id connection
                                                                        {:secret_id (UUID/fromString secret_id)}))
                         new-user (user-with-ids-from-user user user-from-db)]
                     (if (nil? user-from-db)
                       (error-reply 400 "No such user")
                       (update-booking-with-validated-params connection
                                                             new-user
                                                             selected_dates
                                                             user-login-info))))
                 (catch PSQLException pse
                   (handle-psql-error pse))))))

(defn admin-del-booking [id user-login-info]
  (let [user-login-id (:user_login_id user-login-info)
        validated-id (validate-int id)]
    (if (nil? validated-id)
      (error-reply 400 "Invalid booking id")
      (admin-del-booking-with-validated-id validated-id user-login-info))))

