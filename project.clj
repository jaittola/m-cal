(defproject m-cal "0.0.1"
  :description "Booking system with Google calendar as backend"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [ring/ring-json "0.3.1"]
                 [http.async.client "0.5.2"]  ;; might be unnecessary
                 [com.google.api-client/google-api-client "1.18.0-rc"]
                 [com.google.apis/google-api-services-calendar "v3-rev103-1.19.0"]
                 [com.google.http-client/google-http-client-jackson2 "1.18.0-rc"]
                 [com.google.oauth-client/google-oauth-client "1.18.0-rc"]]
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler m-cal.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
