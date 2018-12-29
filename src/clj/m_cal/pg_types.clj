;; Based on src/clj_postgresql/types.clj in
;; https://github.com/remodoy/clj-postgresql

;; Copyright © 2014, Remod Oy
;; All rights reserved.

;; Redistribution and use in source and binary forms, with or without
;; modification, are permitted provided that the following conditions are
;; met:

;; 1. Redistributions of source code must retain the above copyright
;;    notice, this list of conditions and the following disclaimer.

;; 2. Redistributions in binary form must reproduce the above copyright
;;    notice, this list of conditions and the following disclaimer in the
;;    documentation and/or other materials provided with the
;;    distribution.

;; THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
;; "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
;; LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
;; A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
;; HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
;; SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
;; LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
;; DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
;; THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
;; (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
;; OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

(ns m-cal.pg-types
  "Participate in clojure.java.jdbc's ISQLValue and IResultSetReadColumn protocols
   to support the PGjson type."
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.xml :as xml]
            [clojure.data.json :as json])
  (:import [org.postgresql.util PGobject]
           [java.sql PreparedStatement ParameterMetaData]))

;;;;
;;
;; Data type conversion for SQL query parameters
;;
;;;;

;;
;; Extend clojure.java.jdbc's protocol for converting query parameters to SQL values.
;; We try to determine which SQL type is correct for which clojure structure.
;; 1. See query parameter meta data. JDBC might already know what PostgreSQL wants.
;; 2. Look into parameter's clojure metadata for type hints
;;

;; multimethod selector for conversion funcs
(defn parameter-dispatch-fn
  [_ type-name]
  (keyword type-name))

;;
;; Convert Clojure maps to SQL parameter values
;;

(defmulti map->parameter parameter-dispatch-fn)

(defn- to-pg-json [data json-type]
  (doto (PGobject.)
    (.setType (name json-type))
    (.setValue (json/write-str data))))

(defmethod map->parameter :json
  [m _]
  (to-pg-json m :json))

(defmethod map->parameter :jsonb
  [m _]
  (to-pg-json m :jsonb))
(extend-protocol jdbc/ISQLParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s ^long i]
    (let [meta (.getParameterMetaData s)]
      (if-let [type-name (keyword (.getParameterTypeName meta i))]
        (.setObject s i (map->parameter m type-name))
        (.setObject s i m)))))

;;
;; Convert clojure vectors to SQL parameter values
;;

(defmulti vec->parameter parameter-dispatch-fn)

(defmethod vec->parameter :json
  [v _]
  (to-pg-json v :json))

(defmethod vec->parameter :jsonb
  [v _]
  (to-pg-json v :jsonb))

(defmethod vec->parameter :inet
  [v _]
  (if (= (count v) 4)
    (doto (PGobject.) (.setType "inet") (.setValue (clojure.string/join "." v)))
    v))

(defmethod vec->parameter :default
  [v _]
  v)

(extend-protocol jdbc/ISQLParameter
  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s ^long i]
    (let [conn (.getConnection s)
          meta (.getParameterMetaData s)
          type-name (.getParameterTypeName meta i)]
      (if-let [elem-type (when type-name (second (re-find #"^_(.*)" type-name)))]
        (.setObject s i (.createArrayOf conn elem-type (to-array v)))
        (.setObject s i (vec->parameter v type-name))))))

;;
;; Convert all sequables to SQL parameter values by handling them like vectors.
;;

(extend-protocol jdbc/ISQLParameter
  clojure.lang.Seqable
  (set-parameter [seqable ^PreparedStatement s ^long i]
    (jdbc/set-parameter (vec (seq seqable)) s i)))

;;;;
;;
;; Data type conversions for query result set values.
;;
;;;;


;;
;; PGobject parsing magic
;;

(defn read-pg-vector
  "oidvector, int2vector, etc. are space separated lists"
  [s]
  (when (seq s) (clojure.string/split s #"\s+")))

(defn read-pg-array
  "Arrays are of form {1,2,3}"
  [s]
  (when (seq s) (when-let [[_ content] (re-matches #"^\{(.+)\}$" s)] (if-not (empty? content) (clojure.string/split content #"\s*,\s*") []))))

(defmulti read-pgobject
  "Convert returned PGobject to Clojure value."
  #(keyword (when % (.getType ^org.postgresql.util.PGobject %))))

(defmethod read-pgobject :oidvector
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (mapv read-string (read-pg-vector val))))

(defmethod read-pgobject :int2vector
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (mapv read-string (read-pg-vector val))))

(defmethod read-pgobject :anyarray
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (vec (read-pg-array val))))

(defmethod read-pgobject :json
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (json/read-str val :key-fn keyword)))

(defmethod read-pgobject :jsonb
  [^org.postgresql.util.PGobject x]
  (when-let [val (.getValue x)]
    (json/read-str val :key-fn keyword)))

(defmethod read-pgobject :default
  [^org.postgresql.util.PGobject x]
  (.getValue x))

;;
;; Extend clojure.java.jdbc's protocol for interpreting ResultSet column values.
;;
(extend-protocol jdbc/IResultSetReadColumn

  ;; Parse SQLXML to a Clojure map representing the XML content
  java.sql.SQLXML
  (result-set-read-column [val _ _]
    (xml/parse (.getBinaryStream val)))

  ;; Covert java.sql.Array to Clojure vector
  java.sql.Array
  (result-set-read-column [val _ _]
    (vec (.getArray val)))

  ;; PGobjects have their own multimethod
  org.postgresql.util.PGobject
  (result-set-read-column [val _ _]
    (read-pgobject val)))
