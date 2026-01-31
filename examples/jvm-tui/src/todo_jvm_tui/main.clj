(ns todo-jvm-tui.main
  "Main entry point for the JVM Terminal UI example.
   
   This example demonstrates Plin's plugin architecture with FULL DEPENDENCY INJECTION:
   
   1. DOMAIN PLUGIN (:todo/domain)
      - Wraps pure domain functions as injectable beans
      - No dependencies - the foundation layer
   
   2. INTERFACE PLUGINS (:todo/persistence)
      - Define contracts/APIs as beans
      - Depend on :todo/domain for business logic
   
3. STORAGE PLUGINS (disk-store)
       - JVM-specific file-based persistence using EDN format
       - Data persists across application restarts
   
   4. PLATFORM PLUGINS (lanterna-ui)
       - Platform-specific implementations
       - Declare explicit dependencies on the interfaces they implement/use
       - All functionality comes through injected beans
   
   Key architectural points:
   - NO plugin directly requires another plugin's namespace for function calls
   - ALL dependencies are declared in :deps and injected via beans
   - Handler functions receive exactly the dependencies they need
   
   Usage:
     cd examples/jvm-tui
     clojure -M:run
   
   Controls:
     ↑/↓      - Navigate tasks
     n        - Add new task
     t        - Toggle task completion
     d        - Delete task
     c        - Clear completed tasks
     a/A/C    - Filter All/Active/Completed
     q/ESC    - Quit"
  (:require [plin.boot :as boot]
            ;; Common plugins (order matters for the dependency graph)
            [todo.plugins.domain :as domain]
            [todo.plugins.persistence :as persistence]
            [todo.plugins.disk-store :as disk-store]
            ;; Platform-specific plugins
            [todo-jvm-tui.plugins.lanterna-ui :as lanterna-ui]
            [clojure.core.async :refer [<!!]]))

(defn -main
  "Bootstraps the Todo application with JVM TUI plugins.
   
   Plugin dependency graph:
   
     domain (no deps)
        |
        +---> persistence ---> disk-store (overrides persistence beans, uses EDN file)
        |
        +---> lanterna-ui (uses domain and persistence beans)"
  [& _args]
  (println "Starting Todo JVM TUI...")
  (println "Loading...")
  (let [plugins [;; === Layer 1: Core Domain ===
                 ;; Pure business logic, no dependencies
                 domain/plugin

                 ;; === Layer 2: Interfaces ===
                 ;; Define contracts, depend on domain
                 persistence/plugin    ; Defines load-fn, store-fn, delete-fn

                 ;; === Layer 3: Storage Implementation ===
                 ;; Override persistence with file-based storage
                 disk-store/plugin     ; Overrides persistence beans with EDN file storage

                 ;; === Layer 4: Platform UI ===
                 ;; Terminal interface using Lanterna
                 lanterna-ui/plugin]]  ; Terminal UI using Lanterna
    (<!! (boot/bootstrap! plugins))))
