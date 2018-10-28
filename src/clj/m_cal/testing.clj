(ns m-cal.testing
  (:require [hugsql.core :as hugsql]
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
