(ns todo-jvm-tui.main
  "Main entry point for the JVM Terminal UI example.
   
   This example demonstrates Plin's plugin architecture with FULL DEPENDENCY INJECTION:
   
   1. DOMAIN PLUGIN (:todo/domain)
      - Wraps pure domain functions as injectable beans
      - No dependencies - the foundation layer
   
   2. INTERFACE PLUGINS (:todo/persistence, :todo/deadlines, :todo/calendar)
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
     n        - Add new task (with optional due date)
     t        - Toggle task completion
     d        - Delete task
     c        - Clear completed tasks
     v        - Toggle List/Calendar view
     a/A/O/C  - Filter All/Active/Overdue/Completed
     q/ESC    - Quit"
  (:require [plin.boot :as boot]
            ;; Common plugins (order matters for the dependency graph)
            [todo.plugins.domain :as domain]
            [todo.plugins.persistence :as persistence]
            [todo.plugins.deadlines :as deadlines]
            [todo.plugins.calendar :as calendar]
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
        +---> deadlines
        |        |
        +--------+---> calendar
                          |
                          +---> lanterna-ui (uses domain, persistence, deadlines, calendar beans)"
  [& _args]
  (println "Starting Todo JVM TUI...")
  (println "Loading...")
  (let [plugins [;; === Layer 1: Core Domain ===
                 ;; Pure business logic, no dependencies
                 domain/plugin

                 ;; === Layer 2: Interfaces & Utilities ===
                 ;; Define contracts, depend on domain
                 persistence/plugin    ; Defines load-fn, store-fn, delete-fn
                 deadlines/plugin      ; Defines due-date-status, format-due-date, etc.
                 calendar/plugin       ; Defines calendar-data, render-calendar, etc.

                 ;; === Layer 3: Storage Implementation ===
                 ;; Override persistence with file-based storage
                 disk-store/plugin     ; Overrides persistence beans with EDN file storage

                 ;; === Layer 4: Platform UI ===
                 ;; Terminal interface using Lanterna
                 lanterna-ui/plugin]]  ; Terminal UI using Lanterna
    (<!! (boot/bootstrap! plugins))))
