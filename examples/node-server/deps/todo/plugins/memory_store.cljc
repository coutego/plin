(ns todo.plugins.memory-store
  "Reusable in-memory storage implementation for the Todo app.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION including the storage atom:
   - The store atom is created as a bean (::store-atom)
   - All functions receive the atom via DI rather than using defonce
   - This enables proper isolation for testing and multiple instances
   
   Dependencies:
   - :todo/persistence - the interface this plugin implements
   - :todo/domain - for create-task-list (injected)
   
   Beans defined:
   - ::store-atom - The atom used for storage (can be overridden for testing)
   - Overrides :todo.plugins.persistence/load-fn
   - Overrides :todo.plugins.persistence/store-fn
   - Overrides :todo.plugins.persistence/delete-fn
   
   This plugin can be used by any platform (JVM, Node.js, browser) as it's
   written in .cljc and has no platform-specific dependencies."
  (:require [plin.core :as pi]))

;; =============================================================================
;; Factory Functions (all receive dependencies via DI)
;; =============================================================================

(defn make-store-atom
  "Factory: creates a new atom for storage.
   This is a bean so it can be overridden for testing or shared state scenarios."
  []
  (atom nil))

(defn make-load-fn
  "Factory: creates load function with injected store-atom and create-task-list."
  [store-atom create-task-list]
  (fn []
    (or @store-atom (create-task-list))))

(defn make-store-fn
  "Factory: creates store function with injected store-atom."
  [store-atom]
  (fn [task-list]
    (reset! store-atom task-list)))

(defn make-delete-fn
  "Factory: creates delete function with injected store-atom."
  [store-atom]
  (fn []
    (reset! store-atom nil)))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo/memory-store
    :doc "In-memory persistence implementation with fully injected storage atom."
    :deps [:todo/persistence]
    
    :beans
    {;; The storage atom itself is a bean - enables testing and isolation
     ::store-atom
     ^{:doc "Atom used for in-memory storage. Can be overridden for testing."}
     [make-store-atom]

     ;; Override persistence interface beans
     :todo.plugins.persistence/load-fn
     ^{:doc "Load tasks from memory. Returns new task list if empty."
       :api {:ret :map}}
     [make-load-fn ::store-atom :todo.plugins.domain/create-task-list]

     :todo.plugins.persistence/store-fn
     ^{:doc "Save tasks to memory."
       :api {:args [["task-list" {} :map]] :ret :nil}}
     [make-store-fn ::store-atom]

     :todo.plugins.persistence/delete-fn
     ^{:doc "Clear all tasks from memory."
       :api {:ret :nil}}
     [make-delete-fn ::store-atom]}}))
