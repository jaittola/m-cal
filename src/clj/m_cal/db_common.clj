(ns m-cal.db-common
  (:require [m-cal.pg-types]
            [m-cal.config :as config]
            [jdbc.pool.c3p0 :as pool]
            [hugsql.core :as hugsql]))

(def log-entry-booking-book 1)
(def log-entry-booking-release 2)
(def log-entry-email 3)
(def log-entry-email-disabled 4)
(def log-entry-contact-update 5)
(def log-entry-admin-booking-delete 6)
(def log-entry-admin-booking-params-modify 7)

(def psql-unique-constraint-sqlstate 23505)

(hugsql/def-db-fns "app_queries/queries.sql")

;; Database spec. Based on the Heroku c3p0 sample code
(defn db-uri []
  (java.net.URI. (config/database-uri)))

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

(defn user-data-for-store
  [user-details]
  (select-keys user-details [:name :yacht_name :phone :email :number_of_paid_bookings]))

(defn database-insert-booking-log [connection
                                   dates-to-booking-ids
                                   user-details
                                   op
                                   & [user-login-id]]
  (doall (map (fn [id-date]
                (db-insert-booking-log connection
                                       {:booked_date (:booked_date id-date)
                                        :users_id (:id user-details)
                                        :booking_id (:booking_id id-date)
                                        :operation op
                                        :user_data (user-data-for-store user-details)
                                        :user_login_id user-login-id}))
              dates-to-booking-ids)))

(defn database-insert-booking-log-without-date [connection
                                                user-details
                                                op
                                                & [user-login-id]]
  (db-insert-booking-log connection
                         {:booked_date nil
                          :users_id (:id user-details)
                          :booking_id nil
                          :operation op
                          :user_data (user-data-for-store user-details)
                          :user_login_id user-login-id}))
