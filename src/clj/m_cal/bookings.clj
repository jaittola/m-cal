(ns m-cal.bookings
  (:require [m-cal.config :as config]
            [m-cal.util :refer [parse-int]]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [environ.core :as env]
            [clojure.java.jdbc :as jdbc]
            [jdbc.pool.c3p0 :as pool])
  (:import [org.postgresql.util PSQLException]
           [java.util UUID]))

(def log-entry-booking 1)
(def log-entry-release 2)
(def psql-unique-constraint-sqlstate 23505)

;; Database spec. Based on the Heroku c3p0 sample code
(defn db-uri []
  (java.net.URI. (System/getenv "DATABASE_URL")))

(defn db-user-and-password []
  (let [userinfo (.getUserInfo (db-uri))]
  (if (nil? userinfo)
    nil
    (let [[user pw] (clojure.string/split userinfo #":")]
      {:user user
       :password pw}))))

(def dbspec
  (delay
   (pool/make-datasource-spec
    (let [{:keys [user password]} (db-user-and-password)
          uri (db-uri)
          port (.getPort uri)
          host (.getHost uri)
          path (.getPath uri)]
      {:classname "org.postgresql.Driver"
       :subprotocol "postgresql"
       :user user
       :password password
       :subname (if (= -1 port)
                  (format "//%s%s" host path)
                  (format "//%s:%s%s" host port path))}))))

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

(defn database-insert-booking-log [connection dates-to-booking-ids user-id op]
  (doall (map (fn [id-date]
                (db-insert-booking-log connection
                                       {:booked_date (:booked_date id-date)
                                        :users_id (:id user-id)
                                        :booking_id (:booking_id id-date)
                                        :booking_or_release op}))
              dates-to-booking-ids)))

(defn map-dates-to-booking-ids [booking-id-containers selected_dates]
  (map (fn [booking-id-container date]
         {:booking_id (:id booking-id-container)
          :booked_date date})
       booking-id-containers selected_dates))

(defn load-all-bookings []
  (jdbc/with-db-connection [connection @dbspec]
    (db-list-all-bookings connection)))

(defn error-reply [code msg & [bookings-body]]
  {:status code
   :body (if bookings-body {:error_result msg
                            :all_bookings bookings-body}
             {:error_result msg})})

(defn handle-psql-error [pse]
  (let [se (.getServerErrorMessage pse)
        sqlstate (.getSQLState se)]
    (println "Storing bookings to database failed: " (.toString pse) "; sqlstate: " sqlstate)
    (if (= (parse-int sqlstate) psql-unique-constraint-sqlstate)
      (error-reply 409 "The dates you selected were already booked" (load-all-bookings))
      (error-reply 500 "Storing bookings to database failed"))))

(defn list-bookings []
  {:body {:all_bookings (load-all-bookings)
          :calendar_config (config/calendar-config)}})

(defn validate-booking-parameters [name yacht_name email selected_dates]
  (let [required-days (config/required-days)]
    (cond
      (or (nil? name) (nil? yacht_name) (nil? email) (nil? selected_dates)
          (not (vector? selected_dates))) (error-reply 400 "Mandatory parameters missing.")
      (not (== required-days (count selected_dates))) (error-reply 400 (str "You must book " required-days " days."))
      :else nil)))

(defn success-booking-reply [connection user-id name yacht_name email selected_dates]
  {:status 200
   :body {:user {:id (:id user-id)
                 :secret_id (:secret_id user-id)
                 :name name
                 :yacht_name yacht_name
                 :email email}
          :selected_dates selected_dates
          :all_bookings (db-list-all-bookings connection)
          :calendar_config (config/calendar-config)}})

(defn update-booking-with-validated-params [connection user-id name yacht_name email selected_dates]
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

        _ (database-insert-booking-log connection
                                       dates-to-inserted-booking-ids
                                       user-id
                                       log-entry-booking)
        _ (database-insert-booking-log connection
                                       bookings-to-delete
                                       user-id
                                       log-entry-release)]

  (success-booking-reply connection
                         user-id
                         name
                         yacht_name
                         email
                         selected_dates)))

(defn insert-booking [{:keys [name yacht_name email selected_dates]}]
  (let [validation-err (validate-booking-parameters name
                                                    yacht_name
                                                    email
                                                    selected_dates)]
    (if validation-err
      validation-err
      (try (jdbc/with-db-transaction [connection @dbspec]
             (let [user-id (database-insert-user connection
                                                 name
                                                 yacht_name
                                                 email)
                   bookings-ids (database-insert-bookings connection
                                                          selected_dates
                                                          user-id)
                   dates-to-booking-ids (map-dates-to-booking-ids bookings-ids
                                                                  selected_dates)
                   _ (database-insert-booking-log connection
                                                  dates-to-booking-ids
                                                  user-id
                                                  log-entry-booking)]
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
      :else (try (jdbc/with-db-transaction [connection @dbspec]
                   (let [user-id (first (db-find-user-by-secret-id connection
                                                                   {:user_secret_id (UUID/fromString secret_id)}))]
                     (println "user-id iz " user-id)
                     (if (nil? user-id) (error-reply 400 "No such user")
                         (update-booking-with-validated-params connection
                                                               user-id
                                                               name
                                                               yacht_name
                                                               email
                                                               selected_dates))))
                 (catch PSQLException pse
                   (handle-psql-error pse))))))
