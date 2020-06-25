(defproject masyst "1.0.78-SNAPSHOT"
  :description "Information and document server system"
  :min-lein-version "2.0.0"
  :jvm-opts ["-Duser.timezone=UTC"]
  :dependencies [[cljs-ajax "0.5.2"]
                 [clj-http "3.5.0"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [com.cognitect/transit-clj "0.8.295"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.datomic/datomic-pro "0.9.5394"
                  :exclusions [com.google.guava/guava joda-time]]
                 [com.stuartsierra/component "0.3.1"]
                 [compojure "1.5.1"]
                 [crypto-password "0.2.0"]
                 [duct "0.5.10"]
                 [environ "1.0.2"]
                 [hiccup "1.0.5"]
                 [meta-merge "0.1.1"]
                 [org.apache.httpcomponents/httpclient "4.5.1"]
                 [org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/clojurescript "1.9.456"]
                 [org.clojure/core.async "0.3.443"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.slf4j/slf4j-nop "1.7.14"]
                 [org.webjars/normalize.css "3.0.2"]
                 [prismatic/schema "1.0.4"]
                 [re-com "2.0.0"]
                 [re-frame "0.9.2"]
                 [ring "1.5.0"]
                 [ring-jetty-component "0.3.0"]
                 [ring-middleware-format "0.7.0"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-defaults "0.2.1"]
                 [ring/ring-session-timeout "0.1.0"]
                 [secretary "1.2.3"]
                 [org.clojure/data.csv "0.1.3"]
                 [clj-time "0.11.0"]
                 [com.taoensso/truss "1.2.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [io.rkn/conformity "0.4.0"]
                 [image-resizer "0.1.9"]
                 [com.microsoft.sqlserver/sqljdbc4 "4.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [binaryage/devtools "0.9.1"]
                 [com.draines/postal "2.0.2"]
                 [cljsjs/dygraph "1.1.1-1"]]
  :plugins [[lein-environ "1.0.1"]
            [lein-gen "0.2.2"]
            [lein-cljsbuild "1.1.5"]
            [jonase/eastwood "0.2.4"]
            [lein-kibit "0.1.5"]]
  :generators [[duct/generators "0.5.10"]]
  :duct {:ns-prefix masyst}
  :main ^:skip-aot masyst.main
  :target-path "target/%s/"
  :resource-paths ["resources" "target/cljsbuild"]
  :prep-tasks [["javac"] ["cljsbuild" "once"] ["compile"]]
  :cljsbuild
  {:builds
   {:main {:jar true
           :source-paths ["src"]
           :compiler {:output-to "target/cljsbuild/masyst/public/js/main.js"
                      :closure-defines {"goog.DEBUG" false} ;; vypnuti re-frame debug middleware pro produkci
                      :optimizations :advanced
                      :checked-arrays :warn}}}}
  :aliases {"gen"   ["generate"]
            "setup" ["do" ["generate" "locals"]]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :repl {:resource-paths ^:replace ["resources" "target/figwheel"]
          :prep-tasks     ^:replace [["javac"] ["compile"]]}
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [duct/figwheel-component "0.3.2"]
                                  [eftest "0.1.1"]
                                  [figwheel "0.5.4-7"]
                                  [kerodon "0.7.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [reloaded.repl "0.2.3"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :env {:dev "true"
                         :port "3002"
                         :datomic-uri "datomic:dev://localhost:4334/"
                         :app-domains "localhost"
                         :app-dbs     "masyst"}}
   :project/test  {}}
  :release-tasks
  [["vcs" "assert-committed"]
   ["change" "version" "leiningen.release/bump-version" "release"]
   ["vcs" "commit"]
   ["vcs" "tag" "--no-sign"]
   ["uberjar"]
   ["change" "version" "leiningen.release/bump-version"]
   ["vcs" "commit"]])
