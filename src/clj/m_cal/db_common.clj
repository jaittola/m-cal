(ns m-cal.db-common
  (:require [jdbc.pool.c3p0 :as pool]))

(def log-entry-booking-book 1)
(def log-entry-booking-release 2)
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
