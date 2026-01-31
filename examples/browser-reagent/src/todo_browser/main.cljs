(ns todo-browser.main
  "Main entry point for the browser Todo app.
   
   This example demonstrates Plin's plugin architecture with FULL DEPENDENCY INJECTION:
   
   1. DOMAIN PLUGIN (:todo/domain)
      - Wraps pure domain functions as injectable beans
      - No dependencies - the foundation layer
   
   2. INTERFACE PLUGINS (:todo/persistence)
      - Define contracts/APIs as beans
      - Depend on :todo/domain for business logic
      - Provide trivial default implementations
   
   3. REUSABLE PLUGINS (local-storage)
      - Cross-platform plugins that can be used by browser apps
      - Override interface beans with localStorage implementation
   
   4. PLATFORM PLUGINS (browser-boot)
      - Platform-specific implementations (Reagent UI)
      - Declare explicit dependencies on the interfaces they use
      - All functionality comes through injected beans
   
   Key architectural points:
   - NO plugin directly requires another plugin's namespace for function calls
   - ALL dependencies are declared in :deps and injected via beans
   - Reagent component receives dependencies via factory closure pattern
   - The dependency graph is explicit and visible
   
   Usage:
     cd examples/browser-reagent
     npx shadow-cljs watch app
   
   Then open http://localhost:8080 in your browser."
  (:require [plin.boot :as boot]
            ;; Common plugins (order matters for the dependency graph)
            [todo.plugins.domain :as domain]
            [todo.plugins.persistence :as persistence]
            ;; Reusable plugins
            [todo-browser.plugins.local-storage :as local-storage]
            ;; Platform-specific plugins
            [todo-browser.plugins.browser-boot :as browser-boot]))

(defn ^:dev/after-load init
  "Initializes the Todo application.
   Called on page load and after hot reload.
   
   Plugin dependency graph:
   
     domain (no deps)
        |
        +---> persistence ---> local-storage (overrides persistence beans)
        |
        +---> browser-boot (uses domain and persistence beans)"
  []
  (println "Initializing Todo Browser App...")
  
  (let [plugins [;; === Layer 1: Core Domain ===
                 ;; Pure business logic, no dependencies
                 domain/plugin

                 ;; === Layer 2: Interfaces ===
                 ;; Define contracts, depend on domain
                 persistence/plugin    ; Defines load-fn, store-fn, delete-fn

                 ;; === Layer 3: Storage Implementation ===
                 ;; Override persistence with localStorage
                 local-storage/plugin  ; Overrides persistence beans with localStorage

                 ;; === Layer 4: Platform UI ===
                 ;; Reagent-based browser UI
                 browser-boot/plugin]] ; Reagent UI using domain and persistence beans
    
    ;; Bootstrap the system
    (boot/bootstrap! plugins))
  
  (println "Todo Browser App initialized!"))

;; Auto-initialize when script loads
(init)
