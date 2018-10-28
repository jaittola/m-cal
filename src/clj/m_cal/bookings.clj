(ns m-cal.bookings
  (:require [m-cal.config :as config]
            [m-cal.util :refer [parse-int]]
            [m-cal.email-confirmation :as email-confirmation]
            [m-cal.db-common :as db-common]
            [m-cal.util :refer [parse-date-string]]
            [m-cal.validation :as validation]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s])
  (:import [org.postgresql.util PSQLException]
           [java.util UUID]))

(hugsql/def-db-fns "app_queries/queries.sql")

(defn database-insert-user [connection name yacht_name email]
  (first (db-insert-user connection
                         {:name name
                          :yacht_name yacht_name
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

(defn success-booking-reply [connection user-id name yacht_name email selected_dates]
  {:status 200
   :body {:user {:id (:id user-id)
                 :secret_id (:secret_id user-id)
                 :name name
                 :yacht_name yacht_name
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
                                          selected-dates))))
           (catch PSQLException pse
             (handle-psql-error pse)))))

(defn assert-is-in-allowed-range [date]
  (let [{:keys [first_date last_date]} (config/calendar-config)]
    (if (not (and (<= (.compareTo first_date date) 0)
                  (<= (.compareTo date last_date) 0)))
      (throw (ex-info (str "date out of range: " date) {})))))

(defn string-of-at-least [n] (s/and string?
                                    #(>= (.length %) n)))
;; TODO: share this with frontend code
(def email-validation-regex #"(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])")

(def local-date-string-regex #"[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]") ; just a format check

(s/def ::name (string-of-at-least 5))
(s/def ::yacht_name (string-of-at-least 5))
(s/def ::email (s/and (string-of-at-least 5)
                      #(re-matches email-validation-regex %)))
(s/def ::local-date (s/and string?
                           #(re-matches local-date-string-regex %)))
(s/def ::selected_dates (s/tuple ::local-date ::local-date))
(s/def ::booking (s/keys :req-un [::name ::yacht_name ::email ::selected_dates]))

(def booking-spec-validator (validation/spec-validator ::booking))
(def booking-date-parser-validator (validation/assert-validator
                                     (fn [params]
                                       (doall (map parse-date-string (:selected_dates params))))))
(def booking-date-range-validator (validation/assert-validator
                                    (fn [{:keys [selected_dates]}]
                                      (doall (map assert-is-in-allowed-range selected_dates)))))

;; when inserting booking we validate that dates are in proper range.
(def insert-booking-validator (validation/chain [booking-spec-validator
                                                 booking-date-parser-validator
                                                 booking-date-range-validator]))
;; when updating bookings one of the dates can be in history, too
(def update-booking-validator (validation/chain [booking-spec-validator
                                                 booking-date-parser-validator]))


(defn validate-booking-parameters [name yacht_name email selected_dates]
  (let [required-days (config/required-days)]
    (cond
      (or (nil? name) (nil? yacht_name) (nil? email) (nil? selected_dates)
          (not (vector? selected_dates))) (error-reply 400 "Mandatory parameters missing.")
      (not (== required-days (count selected_dates))) (error-reply 400 (str "You must book " required-days " days."))
      :else nil)))

(defn update-booking-with-validated-params [connection user-id name yacht_name email selected_dates]
  (try
    (db-update-user connection
                    {:name name
                     :yacht_name yacht_name
                     :email email
                     :id (:id user-id)})
    (let [db-selected-dates (db-select-user-bookings-for-update connection
                                                              {:user_id (:id user-id)})
          db-selected-dates-values (map #(:booked_date %) db-selected-dates)
          bookings-to-delete (filter (fn [booking] (not (some #(= (:booked_date booking) %) selected_dates))) db-selected-dates)
          bookings-to-add (filter (fn [booking] (not (some #(= booking %) db-selected-dates-values)))
                                  selected_dates)
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
                             selected_dates))
    (catch PSQLException pse
      (handle-psql-error pse (:id user-id)))))

(defn insert-booking [params]
  (let [{:keys [name yacht_name email selected_dates :m-cal.validation/validation-error] :as params} (insert-booking-validator params)]
    (if validation-error
      (error-reply 400 validation-error)
      (try (jdbc/with-db-transaction [connection @db-common/dbspec]
             (let [user-id (database-insert-user connection
                                                 name
                                                 yacht_name
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
                                      selected_dates)))
           (catch PSQLException pse
             (handle-psql-error pse))))))

(defn update-booking [secret_id {:keys [name yacht_name email selected_dates]}]
  (let [validation-err (validate-booking-parameters name
                                                    yacht_name
                                                    email
                                                    selected_dates)]
    (cond
      validation-err validation-err
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
                                                               selected_dates))))
                 (catch PSQLException pse
                   (handle-psql-error pse))))))
