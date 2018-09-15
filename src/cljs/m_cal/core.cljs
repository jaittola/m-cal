(ns m-cal.core
  (:require
   [reagent.core :as reagent]
   ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce app-state
  (reagent/atom {}))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn page [ratom]
  [:div
  [:div
   "Welcome to reagent-figwheel."]
  [:div "Just testing"]
  [:div "Testing a bit more"]
])



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (println "dev mode")
    ))

(defn reload []
  (reagent/render [page app-state]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (reload))
