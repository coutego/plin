(ns todo-jvm-server.main
  "Main entry point for the JVM HTTP server example.
   
   This example demonstrates Plin's plugin architecture with FULL DEPENDENCY INJECTION:
   
   1. DOMAIN PLUGIN (:todo/domain)
      - Wraps pure domain functions as injectable beans
      - No dependencies - the foundation layer
   
   2. INTERFACE PLUGINS (:todo/persistence, :todo/deadlines, :todo/calendar)
      - Define contracts/APIs as beans
      - Depend on :todo/domain for business logic
      - Provide default implementations
   
3. STORAGE PLUGINS (disk-store)
       - JVM-specific file-based persistence using EDN format
       - Data persists across server restarts
   
   4. PLATFORM PLUGINS (http-server, calendar-html)
       - Platform-specific implementations
       - Declare explicit dependencies on the interfaces they implement/use
       - All functionality comes through injected beans
   
   Key architectural points:
   - NO plugin directly requires another plugin's namespace for function calls
   - ALL dependencies are declared in :deps and injected via beans
   - Handler functions receive exactly the dependencies they need
   - The dependency graph is explicit and visible"
  (:require [plin.boot :as boot]
            ;; Common plugins (order matters for the dependency graph)
            [todo.plugins.domain :as domain]
            [todo.plugins.persistence :as persistence]
            [todo.plugins.deadlines :as deadlines]
            [todo.plugins.calendar :as calendar]
            [todo.plugins.disk-store :as disk-store]
            ;; Platform-specific plugins
            [todo-jvm-server.plugins.http-server :as http-server]
            [todo-jvm-server.plugins.calendar-html :as calendar-html]))

(defn -main
  "Bootstraps the Todo application with JVM server plugins.
   
   Usage:
     cd examples/jvm-server
     clojure -M:run
   
   Then open http://localhost:3000 in your browser.
   
   Plugin dependency graph:
   
     domain (no deps)
        |
        +---> persistence ---> disk-store (overrides persistence beans, uses EDN file)
        |
        +---> deadlines
        |        |
        +--------+---> calendar ---> calendar-html (overrides render beans)
                          |
                          +---> http-server (uses all beans)"
  [& _args]
  (println "Starting Todo JVM Server...")
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
                 ;; HTTP server and rendering
                 http-server/plugin    ; HTTP server using Ring/Jetty
                 calendar-html/plugin]]  ; Overrides calendar rendering for HTML
    (boot/bootstrap! plugins)
    ;; Keep the main thread alive
    (loop []
      (Thread/sleep 1000)
      (recur))))
