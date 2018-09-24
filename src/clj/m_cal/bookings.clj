(ns m-cal.bookings
  (:require [hugsql.core :as hugsql]
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

;; Convert the standard dbspec to an other dbspec with `:datasource` key
(def dbspec (pool/make-datasource-spec (env/env :pg-uri)))

(hugsql/def-db-fns "app_queries/queries.sql")

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
       (catch Exception e nil)))

(defn load-all-bookings []
  (jdbc/with-db-connection [connection dbspec]
    (db-list-all-bookings connection)))

(defn error-reply [code msg & bookings-body]
  (let [error {:status code
               :body {:error_result msg}}]
    (if (some? bookings-body)
      (assoc error :all_bookings bookings-body)
      error)))

(defn list-bookings []
  {:body {:all_bookings (load-all-bookings)}})

(defn insert-booking [{:keys [name yacht_name email selected_dates]}]
  (cond
    (or (nil? name) (nil? yacht_name) (nil? email) (nil? selected_dates)
        (not (vector? selected_dates))) (error-reply 400 "Mandatory parameters missing.")
    (not (== required-days (count selected_dates))) (error-reply 400 (str "You must book " required-days " days."))
    :else (try
            (jdbc/with-db-transaction [t-con dbspec]
              (let [user-id (first (db-insert-user t-con
                                                   {:name name
                                                    :yacht_name yacht_name
                                                    :email email}))
                    booking_id_containers (doall (mapcat (fn [day]
                                                           (db-insert-booking t-con
                                                                              {:booked_date day
                                                                               :users_id (:id user-id)}))
                                                         selected_dates))
                    booking_ids_to_dates (map (fn [booking_id_container date]
                                                {:booking_id (:id booking_id_container) :date date})
                                              booking_id_containers selected_dates)]
                (doall (map (fn [id-date]
                              (db-insert-booking-log t-con {:booked_date (:date id-date)
                                                            :users_id (:id user-id)
                                                            :booking_id (:booking_id id-date)
                                                            :booking_or_release log-entry-booking}))
                            booking_ids_to_dates))
              {:status 200
               :body {:user {:id (:id user-id)
                             :key (:secret_id user-id)
                             :name name
                             :yacht_name yacht_name
                             :email email}
                      :selected_dates selected_dates
                      :all_bookings (db-list-all-bookings t-con)}}))
            (catch PSQLException pse
              (let [se (.getServerErrorMessage pse)
                    sqlstate (.getSQLState se)]
                (println "Storing bookings to database failed: " (.toString pse) "; sqlstate: " sqlstate)
                (if (= (parse-int sqlstate) psql-unique-constraint-sqlstate)
                  (error-reply 409 "The dates you selected were already booked" (load-all-bookings))
                  (error-reply 500 "Storing bookings to database failed")))))))
