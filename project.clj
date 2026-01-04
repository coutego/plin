(defproject plin "0.1.0-SNAPSHOT"
  :description "A Data-Driven Plugin Architecture for ClojureScript"
  :url "https://github.com/coutego/plin"
  :license {:name "EUPL-1.2"
            :url "https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12"}
  
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.132"]
                 [reagent "1.2.0"]
                 [metosin/malli "0.13.0"]
                 ;; GitHub dependencies - modify these lines to point to local versions during development
                 ;; For local development, replace with:
                 ;; [injectable "0.1.0-SNAPSHOT"] and add :source-paths ["../injectable/src"]
                 ;; [pluggable "0.1.0-SNAPSHOT"] and add :source-paths ["../pluggable/src"]
                 ;;[io.github.coutego/injectable "0.1.0"]
                 ;;[io.github.coutego/pluggable "0.1.0"]
]
  
  :source-paths ["src" "../injectable/src"]
  :test-paths ["test"]
  
  :profiles {:dev {:dependencies [[org.clojure/test.check "1.1.1"]]
                   :plugins [[lein-cljsbuild "1.1.8"]
                             [lein-doo "0.1.11"]]}}
  
  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/js/test.js"
                                   :output-dir "target/js"
                                   :main plin.test-runner
                                   :optimizations :none}}]}
  
  :doo {:build "test"
        :alias {:default [:node]}}
  
  :aliases {"test-clj" ["test"]
            "test-cljs" ["doo" "node" "once"]
            "test-all" ["do" ["test-clj"] ["test-cljs"]]})
