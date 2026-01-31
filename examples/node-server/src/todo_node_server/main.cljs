(ns todo-node-server.main
  "Main entry point for the Node.js HTTP server example.
   
   This example demonstrates Plin's plugin architecture with FULL DEPENDENCY INJECTION:
   
   1. DOMAIN PLUGIN (:todo/domain)
      - Wraps pure domain functions as injectable beans
      - No dependencies - the foundation layer
   
   2. INTERFACE PLUGINS (:todo/persistence, :todo/deadlines, :todo/calendar)
      - Define contracts/APIs as beans
      - Depend on :todo/domain for business logic
      - Provide default implementations
   
3. STORAGE PLUGINS (disk-store) - File-based storage using EDN format
       - Cross-platform plugins that can be used by any example
       - Override interface beans with file-based storage implementations
   
   4. PLATFORM PLUGINS (http-server, calendar-html)
       - Platform-specific implementations
       - Declare explicit dependencies on the interfaces they implement/use
       - All functionality comes through injected beans
   
   Key architectural points:
   - NO plugin directly requires another plugin's namespace for function calls
   - ALL dependencies are declared in :deps and injected via beans
   - Handler functions receive exactly the dependencies they need
   - The dependency graph is explicit and visible
   
   Usage:
     cd examples/node-server
     npx nbb -cp \"src:deps\" src/todo_node_server/main.cljs
   
   Then open http://localhost:3000 in your browser."
  (:require [plin.core :as pi]
            ;; Common plugins (order matters for the dependency graph)
            [todo.plugins.domain :as domain]
            [todo.plugins.persistence :as persistence]
            [todo.plugins.deadlines :as deadlines]
            [todo.plugins.calendar :as calendar]
            [todo.plugins.disk-store :as disk-store]
            ;; Platform-specific plugins
            [todo-node-server.plugins.http-server :as http-server]
            [todo-node-server.plugins.calendar-html :as calendar-html]))

(defn main
  "Bootstraps the Todo application with Node.js server plugins.
   
   Plugin dependency graph:
   
     domain (no deps)
        |
        +---> persistence ---> disk-store (overrides persistence beans with file storage)
        |
        +---> deadlines
        |        |
        +--------+---> calendar ---> calendar-html (overrides render beans)
                          |
                          +---> http-server (uses all beans)"
  []
  (println "Starting Todo Node.js Server...")
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
                 disk-store/plugin     ; Overrides persistence beans with EDN file storage
                 http-server/plugin    ; HTTP server using Node.js http module
                 calendar-html/plugin] ; Overrides calendar rendering for HTML
        container (pi/load-plugins plugins)]
    (println "Container created.")
    (when-let [boot-fn (:plin.boot/boot-fn container)]
      (boot-fn container))
    (println "Server is running. Press Ctrl+C to stop.")))

(main)
