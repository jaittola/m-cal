(ns m-cal.email-confirmation-sender
  (:require [m-cal.db-common :as db-common]
            [m-cal.email-confirmation :as email-confirmation]
            [m-cal.config :as config]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [go-loop timeout <!]])
  (:import [org.postgresql.util PSQLException]))

(hugsql/def-db-fns "app_queries/queries.sql")

(def loop-interval 20000)

(defn send-email-confirmations []
  (go-loop []
    (try (jdbc/with-db-transaction [connection @db-common/dbspec]
           (doseq [next-confirm (db-get-email-confirmation-queue-next-entry connection)]
             (let [booked-dates (:booked_dates next-confirm)
                   dates-to-booking-ids (map (fn [id date] {:booking_id id
                                                            :booked_date date})
                                             (:booking_ids next-confirm)
                                             booked-dates)
                   confirm-result (email-confirmation/send-confirmation (:name next-confirm)
                                                                        (:email next-confirm)
                                                                        (:yacht_name next-confirm)
                                                                        (config/update-uri next-confirm)
                                                                        booked-dates)]
               (when confirm-result
                 (do
                   (db-common/database-insert-booking-log connection
                                                          dates-to-booking-ids
                                                          (assoc next-confirm
                                                                 :id (:users_id next-confirm))
                                                          confirm-result)
                   (db-delete-email-confirmation-queue-entry connection
                                                             {:id (:queue_id next-confirm)}))))))
         (catch PSQLException pse
           (let [se (.getServerErrorMessage pse)
                 sqlstate (.getSQLState se)]
             (println "Loading e-mail confirmation entries failed: " (.toString pse) "; sqlstate: " sqlstate))))
    (<! (timeout loop-interval))
    (recur)))
