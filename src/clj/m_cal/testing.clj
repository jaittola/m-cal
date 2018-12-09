(ns m-cal.testing
  (:require [hugsql.core :as hugsql]
            [m-cal.bookings :as bookings]
            [m-cal.db-common :as db-common]
            [clojure.java.jdbc :as jdbc]))

(hugsql/def-db-fns "app_queries/queries.sql")

(defn reset-db []
  (jdbc/with-db-transaction [connection @db-common/dbspec]
                            (db-reset-everything-dangerously connection)))

(defn get-user [name]
  (jdbc/with-db-transaction [connection @db-common/dbspec]
                            (->> (db-get-all-users connection)
                                 (filter #(= name (:username %)))
                                 (map #(update % :secret_id str))
                                 first)))

(defn insert-booking-unchecked [params]
  (let [user (bookings/map->User params)
        {:keys [selected_dates]} params]
    (jdbc/with-db-transaction [connection @db-common/dbspec]
      (let [user-id (bookings/database-insert-user connection
                                                   user)]
        (bookings/database-insert-bookings connection
                                           selected_dates
                                  user-id)
        {:status 200 :body "ok"}))))
