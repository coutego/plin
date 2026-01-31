(ns todo-node-tui.main
  "Main entry point for the Node.js Terminal UI example.
   
   This example demonstrates Plin's plugin architecture with FULL DEPENDENCY INJECTION:
   
   1. DOMAIN PLUGIN (:todo/domain)
      - Wraps pure domain functions as injectable beans
      - No dependencies - the foundation layer
   
   2. INTERFACE PLUGINS (:todo/persistence, :todo/deadlines, :todo/calendar)
      - Define contracts/APIs as beans
      - Depend on :todo/domain for business logic
      - Provide default implementations
   
3. REUSABLE PLUGINS (memory-store)
       - Cross-platform plugins that can be used by any example
       - Override interface beans with reusable implementations
   
   4. PLATFORM PLUGINS (terminal-ui)
       - Platform-specific implementations
       - Declare explicit dependencies on the interfaces they implement/use
       - All functionality comes through injected beans
   
   Key architectural points:
   - NO plugin directly requires another plugin's namespace for function calls
   - ALL dependencies are declared in :deps and injected via beans
   - React component receives dependencies via factory closure pattern
   - The dependency graph is explicit and visible
   
   Usage:
     cd examples/node-tui
     npx nbb -cp \"src:deps\" src/todo_node_tui/main.cljs
   
   Controls:
      up/dn    - Navigate tasks
      n        - Add new task (with optional due date)
      t        - Toggle task completion
      d        - Delete task
      c        - Clear completed tasks
      v        - Toggle List/Calendar view
      a/A/O/C  - Filter All/Active/Overdue/Completed
      q        - Quit"
  (:require [plin.core :as pi]
            ;; Common plugins (order matters for the dependency graph)
            [todo.plugins.domain :as domain]
            [todo.plugins.persistence :as persistence]
            [todo.plugins.deadlines :as deadlines]
            [todo.plugins.calendar :as calendar]
            [todo.plugins.memory-store :as memory-store]
            ;; Platform-specific plugins
            [todo-node-tui.plugins.terminal-ui :as terminal-ui]))

(defn main
  "Bootstraps the Todo application with Node.js TUI plugins.
   
   Plugin dependency graph:
   
     domain (no deps)
        |
        +---> persistence ---> memory-store (overrides persistence beans)
        |
        +---> deadlines
        |        |
        +--------+---> calendar
                          |
                          +---> terminal-ui (uses domain, persistence, deadlines, calendar beans)"
  []
  (println "Starting Todo Node.js TUI...")
  (let [plugins [;; === Layer 1: Core Domain ===
                 ;; Pure business logic, no dependencies
                 domain/plugin

                 ;; === Layer 2: Interfaces & Utilities ===
                 ;; Define contracts, depend on domain
                 persistence/plugin    ; Defines load-fn, store-fn, delete-fn
                 deadlines/plugin      ; Defines due-date-status, format-due-date, etc.
                 calendar/plugin       ; Defines calendar-data, render-calendar, etc.

                 ;; === Layer 3: Platform Implementations ===
                 ;; Override beans with Node.js-specific implementations
                 memory-store/plugin   ; Overrides persistence beans with atom storage
                 terminal-ui/plugin]   ; Terminal UI using Ink (React for terminals)
        container (pi/load-plugins plugins)]
    (println "Container created with keys:" (keys container))
    (when-let [boot-fn (:plin.boot/boot-fn container)]
      (boot-fn container))
    (println "Application started.")))

(main)
