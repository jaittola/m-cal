(ns m-cal.users
  (:require [m-cal.config :as config]
            [m-cal.db-common :as db-common]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc])
  (:import [org.postgresql.util PSQLException]))

(hugsql/def-db-fns "app_queries/queries.sql")

(defn login [body]
  {:status 200
   :body {:token "1"}})

(defn logout [body]
  {:status 200})
