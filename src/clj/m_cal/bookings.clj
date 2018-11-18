(ns m-cal.bookings
  (:require [m-cal.config :as config]
            [m-cal.util :refer [parse-int]]
            [m-cal.db-common :as db-common]
            [m-cal.util :refer [parse-date-string today]]
            [m-cal.validation :as validation]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s])
  (:import [org.postgresql.util PSQLException]
           [java.util UUID]))

(hugsql/def-db-fns "app_queries/queries.sql")

(defn database-insert-user [connection name yacht_name phone email]
  (first (db-insert-user connection
                         {:name name
                          :yacht_name yacht_name
                          :phone phone
                          :email email})))

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

(defn success-booking-reply [connection user-id name yacht_name email phone selected_dates]
  {:status 200
   :body {:user {:id (:id user-id)
                 :secret_id (:secret_id user-id)
                 :name name
                 :yacht_name yacht_name
                 :phone phone
                 :email email}
          :selected_dates selected_dates
          :all_bookings (db-list-all-bookings connection)
          :calendar_config (config/calendar-config)
          :update_uri (config/update-uri user-id)}})

(defn error-reply [code msg & [bookings-body selected-dates]]
  (let [body (merge {:error_result msg}
                    (when bookings-body) {:all_bookings bookings-body}
                    (when selected-dates {:selected_dates selected-dates}))]
    {:status code
     :body body}))

(defn load-all-bookings []
  (jdbc/with-db-connection [connection @db-common/dbspec]
    (db-list-all-bookings connection)))

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

(defn list-bookings []
  {:body {:all_bookings (load-all-bookings)
          :calendar_config (config/calendar-config)}})

(defn list-bookings-with-user [id]
  (if
      (nil? id) (error-reply 400 "Mandatory parameters missing.")
      (try (jdbc/with-db-transaction [connection @db-common/dbspec]
             (let [user (first (db-find-user-by-secret-id connection
                                                          {:user_secret_id (UUID/fromString id)}))
                   selected-dates (database-get-user-selections connection (:id user))]
               (if (nil? user) (error-reply 400 "No such user")
                   (success-booking-reply connection
                                          user
                                          (:name user)
                                          (:yacht_name user)
                                          (:email user)
                                          (:phone user)
                                          selected-dates))))
           (catch PSQLException pse
             (handle-psql-error pse)))))

(defn date-str-less-or-eq
  "return true iff date-1 is less or equal than date-2. Both are given as date strings (e.g. '2018-03-22')"
  [date-1 date-2]
  (<= (.compareTo date-1 date-2) 0))

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

(defn assert-bookings-not-in-the-past
  [bookings]
  (doseq [booked-date bookings]
    (if (date-str-less-or-eq booked-date (today))
      (throw (IllegalArgumentException. "date in the past")))))

(defn update-booking-with-validated-params [connection user-id name yacht_name email phone selected_dates]
  (try
    (db-update-user connection
                    {:name name
                     :yacht_name yacht_name
                     :email email
                     :phone phone
                     :id (:id user-id)})
    (let [db-selected-dates (db-select-user-bookings-for-update connection
                                                              {:user_id (:id user-id)})
          db-selected-dates-values (map #(:booked_date %) db-selected-dates)
          bookings-to-delete (filter (fn [booking] (not (some #(= (:booked_date booking) %) selected_dates))) db-selected-dates)
          bookings-to-add (filter (fn [booking] (not (some #(= booking %) db-selected-dates-values)))
                                  selected_dates)
          _ (assert-bookings-not-in-the-past bookings-to-add)
          _ (database-delete-bookings connection bookings-to-delete)

          inserted-bookings-ids (database-insert-bookings connection
                                                          bookings-to-add
                                                          user-id)
          dates-to-inserted-booking-ids (map-dates-to-booking-ids inserted-bookings-ids
                                                                  bookings-to-add)

          _ (db-common/database-insert-booking-log connection
                                                   bookings-to-delete
                                                   user-id
                                                   db-common/log-entry-booking-release)
          _ (db-common/database-insert-booking-log connection
                                                   dates-to-inserted-booking-ids
                                                   user-id
                                                   db-common/log-entry-booking-book)]
      (db-add-to-confirmation-queue connection {:users_id (:id user-id)})
      (success-booking-reply connection
                             user-id
                             name
                             yacht_name
                             email
                             phone
                             selected_dates))
    (catch PSQLException pse
      (handle-psql-error pse (:id user-id)))
    (catch IllegalArgumentException e
      (error-reply 400 "trying to modify bookings in the past"))))

(defn insert-booking [params]
  (let [{:keys [name yacht_name email phone selected_dates :m-cal.validation/validation-error]} (booking-validator params)]
    (if validation-error
      (error-reply 400 validation-error)
      (try (jdbc/with-db-transaction [connection @db-common/dbspec]
             (assert-bookings-not-in-the-past selected_dates)
             (let [user-id (database-insert-user connection
                                                 name
                                                 yacht_name
                                                 phone
                                                 email)
                   bookings-ids (database-insert-bookings connection
                                                          selected_dates
                                                          user-id)
                   dates-to-booking-ids (map-dates-to-booking-ids bookings-ids
                                                                  selected_dates)
                   _ (db-common/database-insert-booking-log connection
                                                            dates-to-booking-ids
                                                            user-id
                                                            db-common/log-entry-booking-book)]
               (db-add-to-confirmation-queue connection {:users_id (:id user-id)})
               (success-booking-reply connection
                                      user-id
                                      name
                                      yacht_name
                                      email
                                      phone
                                      selected_dates)))
           (catch PSQLException pse
             (handle-psql-error pse))
           (catch IllegalArgumentException e
             (error-reply 400 "Trying to make booking in the past"))))))

(defn update-booking [secret_id params]
  (let [{:keys [name yacht_name email phone selected_dates :m-cal.validation/validation-error]} (booking-validator params)]
    (cond
      validation-error (error-reply 400 validation-error)
      (nil? secret_id) (error-reply 400 "Mandatory parameters missing.")
      :else (try (jdbc/with-db-transaction [connection @db-common/dbspec]
                   (let [user (first (db-find-user-by-secret-id connection
                                                                {:user_secret_id (UUID/fromString secret_id)}))]
                     (if (nil? user) (error-reply 400 "No such user")
                         (update-booking-with-validated-params connection
                                                               user
                                                               name
                                                               yacht_name
                                                               email
                                                               phone
                                                               selected_dates))))
                 (catch PSQLException pse
                   (handle-psql-error pse))))))
