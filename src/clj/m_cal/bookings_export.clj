(ns m-cal.bookings-export
  (:require [m-cal.db-common :as db-common]
            [m-cal.config :as config]
            [ring.util.io :as ring-io]
            [ring.util.response :as ring-response]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as clj-java-io]
            [clj-time.format :as tf]
            [clj-time.local :as tl])
  (:import [org.apache.poi.xssf.usermodel XSSFWorkbook]
           [org.postgresql.util PSQLException]
           [java.util Date]))

(hugsql/def-db-fns "app_queries/queries.sql")

(def date-format-db (tf/formatter :date))
(def date-formats {:fi "dd.MM.YYYY"
                   :en "MM/dd/YYYY"})
(def export-columns [[:booked_date {:titles {:en "Booked date" :fi "Varattu päivä"}
                                    :cell-style :date}]
                     [:user_id {:titles {:en "User ID" :fi "Käyttäjän ID"}
                                :cell-style :number}]
                     [:name {:titles {:en "Name" :fi "Nimi"}}]
                     [:yacht_name {:titles {:en "Name of yacht" :fi "Veneen nimi"}}]
                     [:email {:titles {:en "e-mail" :fi "Sähköposti"}}]
                     [:phone {:titles {:en "Phone" :fi "Puhelinnumero"}}]])

(def default-lang :fi)

(defn set-cell-value [cell value & [style]]
  (.setCellValue cell value)
  (when style
    (.setCellStyle cell style)))

(defn format-value [value style cell-styles]
  {:value (case style
            :date (-> (tf/parse date-format-db value)
                      (.toDate))
            :number (double value)
            (str value))
   :style (when style
            (style cell-styles))})

(defn user-language-or-default [lang-str]
  (let [lang (keyword lang-str)]
    (if (contains? date-formats lang)
      lang
      default-lang)))

(defn create-export-excel [bookings lang-str]
  (let [lang (user-language-or-default lang-str)
        date-format (tf/formatter (lang date-formats))
        workbook (XSSFWorkbook.)
        sheet (.createSheet workbook "Bookings")
        heading-rows (doall (->> (map-indexed (fn [idx identifier]
                                                {identifier (.createRow sheet idx)})
                                              [:date :i1 :column-titles :i2])
                                 (into {})))
        first-actual-row (count heading-rows)
        creation-helper (.getCreationHelper workbook)
        cell-styles {:date (.createCellStyle workbook)}]

    (.setDataFormat (:date cell-styles) (-> creation-helper
                                            (.createDataFormat)
                                            (.getFormat (lang date-formats))))

    (set-cell-value (.createCell (:date heading-rows) 0)
                    (-> (tl/local-now)
                        (.toDate))
                    (:date cell-styles))

    (doall (map-indexed (fn [idx [db-column parammap]]
                          (set-cell-value (.createCell (:column-titles heading-rows) idx)
                                          (get-in parammap [:titles lang])))
                        export-columns))
    (doall (map-indexed (fn [idx booking-row]
                          (let [rownumber (+ first-actual-row idx)
                                row (.createRow sheet rownumber)]
                            (doall (map-indexed (fn [column-idx [db-column parammap]]
                                                  (let [{:keys [value style]} (format-value (db-column booking-row)
                                                                                            (:cell-style parammap)
                                                                                            cell-styles)]
                                                    (set-cell-value (.createCell row column-idx)
                                                                    value
                                                                    style)))
                                                export-columns))))
                        bookings))
    (doall (map-indexed (fn [idx column-content]
                          (.autoSizeColumn sheet idx))
                        export-columns))
    workbook))

(defn export-all [lang]
  {:headers {"Content-Type" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
             "Content-Disposition" "attachment; filename=\"bookings.xlsx\""}
   :body (ring-io/piped-input-stream
          (fn [output-stream]
            (try (jdbc/with-db-connection [connection @db-common/dbspec]
                   (let [bookings (db-list-all-bookings-for-admin connection)
                         excel (create-export-excel bookings lang)]
                     (.write excel output-stream)))
                 (catch Exception e
                   (println "Failed exporting to excel: " e)))))})
