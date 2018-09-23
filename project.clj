(defproject m-cal "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.339"]
                 [reagent "0.8.1"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [ring/ring-devel "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [ring-basic-authentication "1.0.5"]
                 [compojure "1.6.1"]
                 [environ "1.1.0"]
                 [com.layerware/hugsql "0.4.9"]
                 [org.postgresql/postgresql "42.2.2"]
                 [org.clojure/java.jdbc "0.7.8"]
                 [clojure.jdbc/clojure.jdbc-c3p0 "0.3.3"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-less "1.7.5"]
            [lein-ring "0.12.1"]]

  :min-lein-version "2.5.3"
  :uberjar-names "m-cal.jar"
  :ring {:init m-cal.handler/setup
         :handler m-cal.handler/app}
  :source-paths ["src/clj"]
  :resource-paths [ "resources" "src/db/queries" ]
  :main m-cal.handler


  :clean-targets ^{:protect false} ["resources/public/js"
                                    "target"]
  :figwheel {:css-dirs ["resources/public/css"]}
  :less {:source-paths ["less"]
         :target-path  "resources/public/css"}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles {:dev
             {:dependencies [
                             [figwheel-sidecar "0.5.15"]
                             [com.cemerick/piggieback "0.2.1"]
                             [binaryage/devtools "0.9.9"]]

              :plugins      [[lein-figwheel "0.5.15"]]
              }
             :uberjar {
                       :hooks [leiningen.cljsbuild]
                       :omit-source true
                       :aot :all}}

  :cljsbuild {:builds
              [{:id           "min"
                :source-paths ["src/cljs"]
                :compiler     {:main            m-cal.core
                               :optimizations   :advanced
                               :output-to       "resources/public/js/app.js"
                               :elide-asserts   true
                               :closure-defines {goog.DEBUG false}
                               :pretty-print    false}}

               {:id           "dev"
                :source-paths ["src/cljs"]
                :figwheel     {:on-jsload "m-cal.core/reload"}
                :compiler     {:main                 m-cal.core
                               :optimizations        :none
                               :output-to            "resources/public/js/app.js"
                               :output-dir           "resources/public/js/dev"
                               :asset-path           "js/dev"
                               :source-map-timestamp true
                               :preloads             [devtools.preload]
                    :external-config
                               {:devtools/config
                                {:features-to-install    [:formatters :hints]
                                 :fn-symbol              "F"
                                 :print-config-overrides true}}}}

               ]}
)
