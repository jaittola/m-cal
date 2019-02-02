(ns m-cal.users
  (:require [m-cal.config :as config]
            [m-cal.db-common :as db-common]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [crypto.random :as cryptorandom])
  (:import [org.postgresql.util PSQLException]))

(def token-length 64)

(hugsql/def-db-fns "app_queries/queries.sql")

(defn error-reply [status message]
  {:status status
   :body {:error_result message}})

(defn login [params]
  (try (jdbc/with-db-transaction [connection @db-common/dbspec]
         (db-clean-up-old-tokens connection)
         (let [{:keys [username password]} params
               user-or-default (or username (config/default-user))
               user-details (->> (db-check-user-credentials connection
                                                            {:username user-or-default
                                                             :password (or password "")})
                                 (first))]
           (if user-details
             (let [token (cryptorandom/base64 token-length)]
               (db-save-token connection {:user_login_id (:login_id user-details)
                                          :token token
                                          :session_duration (str 8 " hours")})
               {:status 200
                :body {:token token
                       :user-real-name (:realname user-details)}})
             (error-reply 401 "Invalid login"))))
       (catch PSQLException pse
         (let [se (.getServerErrorMessage pse)
               sqlstate (.getSQLState se)]
           (println "Login in failed: " (.toString pse) "; sqlstate: " sqlstate)
           (error-reply 500 "Login failed")))))

(defn logout [body]
  (try (jdbc/with-db-connection [connection @db-common/dbspec]
         (let [token (:token body)]
           (when (and token (string? token))
             (db-wipe-token connection {:token token})))
         {:status 200})
       (catch PSQLException pse
         (let [se (.getServerErrorMessage pse)
               sqlstate (.getSQLState se)]
           (println "Wiping token failed: " (.toString pse) "; sqlstate: " sqlstate)
           (error-reply 500 "Logging out failed")))))

(defn check-login [token]
  (try (jdbc/with-db-connection [connection @db-common/dbspec]
         (first (db-fetch-user-for-token connection {:token token})))
       (catch PSQLException pse
         (let [se (.getServerErrorMessage pse)
               sqlstate (.getSQLState se)]
           (println "Fetching user details for token failed: " (.toString pse) "; sqlstate: " sqlstate)
           nil))))
