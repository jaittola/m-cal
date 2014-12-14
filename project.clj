(defproject m-cal "0.0.1-SNAPSHOT"
  :description "Booking system with Google calendar as backend"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [com.google.api-client/google-api-client "1.19.0"]
                 [com.google.apis/google-api-services-calendar "v3-rev109-1.19.0"]
                 [com.google.http-client/google-http-client-jackson2 "1.19.0"
                  :exclusions [[org.apache.httpcomponents/httpclient]]]
                 [com.google.oauth-client/google-oauth-client "1.19.0"]
                 [org.bouncycastle/bcprov-jdk15on "1.51"]
                 [org.bouncycastle/bcpkix-jdk15on "1.51"]
                 [environ "1.0.0"]
                 [phronmophobic/postmark "1.3.0-SNAPSHOT"]
                 [clj-time "0.8.0"]]
  :plugins [[lein-ring "0.8.11"]
            [lein-ancient "0.5.5"]]
  :min-lein-version "2.5.0"
  :ring {:init m-cal.handler/setup
         :handler m-cal.handler/app}
  :uberjar-name "m-cal-standalone.jar"
  :profiles {:production {:env {:production true}}
             :uberjar {:aot :all}}
)
