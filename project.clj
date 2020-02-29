(defproject m-cal "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.597"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [reagent "0.9.1"]
                 [reagent-utils "0.3.3"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [cljs-http "0.1.46"]
                 [ring/ring-core "1.8.0"]
                 [ring/ring-jetty-adapter "1.8.0"]
                 [ring/ring-devel "1.8.0"]
                 [ring/ring-json "0.5.0"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-ssl "0.3.0"]
                 [compojure "1.6.1"]
                 [environ "1.1.0"]
                 [com.layerware/hugsql "0.5.1"]
                 [org.postgresql/postgresql "42.2.10"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [clojure.jdbc/clojure.jdbc-c3p0 "0.3.3"]
                 [com.cemerick/url "0.1.1"]
                 [cljsjs/babel-polyfill "6.20.0-2"]
                 [clj-http "3.10.0"]
                 [clj-time "0.15.2"]
                 [org.clojure/data.json "1.0.0"]
                 [alxlit/autoclave "0.2.0" :exclusions [com.google.guava/guava]]
                 [crypto-random "1.2.0"]
                 [org.apache.poi/poi-ooxml "4.1.2"]]
  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-less "1.7.5"]
            [lein-ring "0.12.5"]
            [lein-ancient "0.6.15"]
            [lein-auto "0.1.3"]]

  :min-lein-version "2.5.3"
  :uberjar-name "m-cal-standalone.jar"
  :ring {:init m-cal.handler/setup
         :handler m-cal.handler/app
         :reload-paths ["src/clj"]}
  :source-paths ["src/clj"]
  :resource-paths [ "resources" "src/db/queries" ]
  :main m-cal.handler

  :clean-targets ^{:protect false} ["resources/public/js"
                                    "target"]

  :auto {"test" {:file-pattern #"\.(clj)$"}}

  :figwheel {:ring-handler m-cal.handler/app
             :css-dirs ["resources/public/css"]}
  :less {:source-paths ["less"]
         :target-path  "resources/public/css"}

  :profiles {:dev
             {:dependencies [[figwheel-sidecar "0.5.19"]
                             [binaryage/devtools "1.0.0"]
                             [cider/piggieback "0.4.2"]
                             [ring/ring-mock "0.4.0"]]
              :plugins      [[lein-figwheel "0.5.19"]
                             [cider/cider-nrepl "0.24.0"]]}

             :test
             {:test-paths ["test/clj"]}

             :uberjar {
                       :prep-tasks [["cljsbuild" "once" "min"]
                                    ["cljsbuild" "once" "admin-min"]
                                    ["less" "once"]
                                    "compile"]
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

               {:id           "admin-min"
                :source-paths ["src/cljs"]
                :compiler     {:main            m-cal.admin
                               :optimizations   :advanced
                               :output-to       "resources/public/js/admin/admin-app.js"
                               :output-dir      "resources/public/js/admin"
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

               {:id           "admin-dev"
                :source-paths ["src/cljs"]
                :figwheel     {:on-jsload "m-cal.admin/reload"}
                :compiler     {:main                 m-cal.admin
                               :optimizations        :none
                               :output-to            "resources/public/js/admin/admin-app.js"
                               :output-dir           "resources/public/js/admin/dev"
                               :asset-path           "js/admin/dev"
                               :source-map-timestamp true
                               :preloads             [devtools.preload]
                    :external-config
                               {:devtools/config
                                {:features-to-install    [:formatters :hints]
                                 :fn-symbol              "F"
                                 :print-config-overrides true}}}}

               ]}
)
