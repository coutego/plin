#!/usr/bin/env bb
;; Script to run tests for all examples
;; Usage: bb bin/test-all.clj

(ns test-all
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [babashka.fs :as fs]))

(defn run-test [name dir cmd]
  (println "\n" (str/join (repeat 60 "=")))
  (println "Testing:" name)
  (println (str/join (repeat 60 "=")))
  (let [result (apply sh (concat (str/split cmd #" ") [:dir dir]))]
    (if (zero? (:exit result))
      (do
        (println "✅ PASSED")
        (when (seq (:out result))
          (println (:out result)))
        true)
      (do
        (println "❌ FAILED")
        (println "Exit code:" (:exit result))
        (when (seq (:out result))
          (println "STDOUT:" (:out result)))
        (when (seq (:err result))
          (println "STDERR:" (:err result)))
        false))))

(defn check-compilation [name dir namespace]
  (println "\n" (str/join (repeat 60 "=")))
  (println "Testing:" name "(compilation check)")
  (println (str/join (repeat 60 "=")))
  (let [result (sh "clojure" "-M" "-e" (str "(require '" namespace ")") :dir dir)]
    (if (zero? (:exit result))
      (do
        (println "✅ PASSED - Code compiles successfully")
        true)
      (do
        (println "❌ FAILED - Compilation error")
        (when (seq (:err result))
          (println (:err result)))
        false))))

(defn -main []
  (println "Running all tests for Plin examples...")
  
  (let [results
        [(run-test "Core Library" "." "clojure -M:test")
         (run-test "Common Domain" "examples/common" "clojure -M:test")
         (check-compilation "JVM Server" "examples/jvm-server" "todo-jvm-server.main")
         (check-compilation "JVM TUI" "examples/jvm-tui" "todo-jvm-tui.main")]]
    
    (println "\n" (str/join (repeat 60 "=")))
    (println "Test Summary")
    (println (str/join (repeat 60 "=")))
    (let [passed (count (filter true? results))
          total (count results)]
      (println (str "Passed: " passed "/" total))
      (if (= passed total)
        (do
          (println "✅ All tests passed!")
          (System/exit 0))
        (do
          (println "❌ Some tests failed")
          (System/exit 1))))))

(-main)
