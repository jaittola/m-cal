(ns m-cal.testing
  (:require [hugsql.core :as hugsql]
            [m-cal.db-common :as db-common]
            [clojure.java.jdbc :as jdbc]))

(hugsql/def-db-fns "app_queries/queries.sql")

(defn reset-db []
  (jdbc/with-db-transaction [connection @db-common/dbspec]
                            (db-reset-everything-dangerously connection)))

