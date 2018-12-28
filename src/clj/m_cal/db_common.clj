(ns m-cal.db-common
  (:require [m-cal.pg-types]
            [jdbc.pool.c3p0 :as pool]
            [hugsql.core :as hugsql]))

(def log-entry-booking-book 1)
(def log-entry-booking-release 2)
(def log-entry-email 3)
(def log-entry-email-disabled 4)

(def psql-unique-constraint-sqlstate 23505)

(hugsql/def-db-fns "app_queries/queries.sql")

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

(defn database-insert-booking-log [connection dates-to-booking-ids user-id op]
  (doall (map (fn [id-date]
                (db-insert-booking-log connection
                                       {:booked_date (:booked_date id-date)
                                        :users_id (:id user-id)
                                        :booking_id (:booking_id id-date)
                                        :operation op}))
              dates-to-booking-ids)))
