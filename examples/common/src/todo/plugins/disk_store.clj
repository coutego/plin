(ns todo.plugins.disk-store
  "File-based storage implementation for the Todo app (JVM only).
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - The file path is a bean (::data-file) that can be overridden
   - All functions receive dependencies via DI
   - Uses EDN format for human-readable persistence
   
   Dependencies:
   - :todo/persistence - the interface this plugin implements
   - :todo/domain - for create-task-list (injected)
   
   Beans defined:
   - ::data-file - Path to the data file (default: \"todo-data.edn\")
   - Overrides :todo.plugins.persistence/load-fn
   - Overrides :todo.plugins.persistence/store-fn
   - Overrides :todo.plugins.persistence/delete-fn
   
   This plugin is JVM-only (.clj) as it uses java.io for file operations."
  (:require [plin.core :as pi]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; =============================================================================
;; Factory Functions (all receive dependencies via DI)
;; =============================================================================

(defn make-data-file
  "Factory: creates the default data file path.
   Can be overridden to use a different location."
  []
  "todo-data.edn")

(defn make-load-fn
  "Factory: creates load function with injected data-file and create-task-list."
  [data-file create-task-list]
  (fn []
    (let [file (io/file data-file)]
      (if (.exists file)
        (try
          (edn/read-string (slurp file))
          (catch Exception e
            (println "Warning: Could not read" data-file "- starting fresh:" (.getMessage e))
            (create-task-list)))
        (create-task-list)))))

(defn make-store-fn
  "Factory: creates store function with injected data-file."
  [data-file]
  (fn [task-list]
    (spit data-file (pr-str task-list))))

(defn make-delete-fn
  "Factory: creates delete function with injected data-file."
  [data-file]
  (fn []
    (let [file (io/file data-file)]
      (when (.exists file)
        (.delete file)))))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo/disk-store
    :doc "File-based persistence using EDN format (JVM only)."
    :deps [:todo/persistence]
    
    :beans
    {;; The data file path is a bean - can be overridden for different locations
     ::data-file
     ^{:doc "Path to the EDN data file. Override to use a different location."}
     [make-data-file]

     ;; Override persistence interface beans
     :todo.plugins.persistence/load-fn
     ^{:doc "Load tasks from EDN file. Returns new task list if file doesn't exist."
       :api {:ret :map}}
     [make-load-fn ::data-file :todo.plugins.domain/create-task-list]

     :todo.plugins.persistence/store-fn
     ^{:doc "Save tasks to EDN file."
       :api {:args [["task-list" {} :map]] :ret :nil}}
     [make-store-fn ::data-file]

     :todo.plugins.persistence/delete-fn
     ^{:doc "Delete the EDN data file."
       :api {:ret :nil}}
     [make-delete-fn ::data-file]}}))
