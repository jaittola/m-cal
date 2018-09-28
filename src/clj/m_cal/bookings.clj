(ns m-cal.bookings
  (:require [m-cal.config :as config]
            [m-cal.util :refer [parse-int]]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [environ.core :as env]
            [clojure.java.jdbc :as jdbc]
            [jdbc.pool.c3p0 :as pool])
  (:import [org.postgresql.util PSQLException]))

;; TODO, move this into configuration
(def required-days 2)

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

(defn dbspec []
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
                (format "//%s:%s%s" host port path))})))

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

(defn database-insert-booking-log [connection dates-to-booking-ids user-id]
  (doall (map (fn [id-date]
                (db-insert-booking-log connection
                                       {:booked_date (:date id-date)
                                        :users_id (:id user-id)
                                        :booking_id (:booking_id id-date)
                                        :booking_or_release log-entry-booking}))
              dates-to-booking-ids)))

(defn map-dates-to-booking-ids [booking-id-containers selected_dates]
  (map (fn [booking-id-container date]
         {:booking_id (:id booking-id-container)
          :date date})
       booking-id-containers selected_dates))

(defn load-all-bookings []
  (jdbc/with-db-connection [connection (dbspec)]
    (db-list-all-bookings connection)))

(defn error-reply [code msg & bookings-body]
  (let [error {:status code
               :body {:error_result msg}}]
    (if (some? bookings-body)
      (assoc error :all_bookings bookings-body)
      error)))

(defn list-bookings []
  {:body {:all_bookings (load-all-bookings)
          :calendar_config (config/calendar-config)}})

(defn insert-booking [{:keys [name yacht_name email selected_dates]}]
  (cond
    (or (nil? name) (nil? yacht_name) (nil? email) (nil? selected_dates)
        (not (vector? selected_dates))) (error-reply 400 "Mandatory parameters missing.")
    (not (== required-days (count selected_dates))) (error-reply 400 (str "You must book " required-days " days."))
    :else (try
            (jdbc/with-db-transaction [connection (dbspec)]
              (let [user-id (database-insert-user connection name yacht_name email)
                    bookings-ids (database-insert-bookings connection selected_dates user-id)
                    dates-to-booking-ids (map-dates-to-booking-ids bookings-ids selected_dates)
                    _ (database-insert-booking-log connection dates-to-booking-ids user-id)]
                {:status 200
                 :body {:user {:id (:id user-id)
                               :key (:secret_id user-id)
                               :name name
                               :yacht_name yacht_name
                               :email email}
                        :selected_dates selected_dates
                        :all_bookings (db-list-all-bookings connection)
                        :calendar_config (config/calendar-config)}}))
            (catch PSQLException pse
              (let [se (.getServerErrorMessage pse)
                    sqlstate (.getSQLState se)]
                (println "Storing bookings to database failed: " (.toString pse) "; sqlstate: " sqlstate)
                (if (= (parse-int sqlstate) psql-unique-constraint-sqlstate)
                  (error-reply 409 "The dates you selected were already booked" (load-all-bookings))
                  (error-reply 500 "Storing bookings to database failed")))))))
