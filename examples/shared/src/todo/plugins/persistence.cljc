(ns todo.plugins.persistence
  "Interface plugin for task persistence.
   
   This plugin defines the persistence API as beans with trivial (atom-based)
   default implementations using proper DI (no defonce global state).
   
   Platform-specific plugins should override these beans to provide real
   persistence implementations:
   - :todo/memory-store - In-memory storage (cross-platform)
   - :todo/disk-store - File-based storage (JVM only)
   - :todo/local-storage - Browser localStorage (browser only)
   
   Dependencies:
   - :todo/domain - for create-task-list function
   
   Beans defined:
   - ::store-atom - The storage atom (can be overridden)
   - ::store-fn - Function to save tasks
   - ::load-fn - Function to load tasks  
   - ::delete-fn - Function to delete all tasks"
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
   {:id :todo/persistence
    :doc "Persistence interface for tasks with trivial in-memory defaults. Override beans to provide real storage."
    :deps []
    
    :beans
    {;; The storage atom itself is a bean - enables testing and isolation
     ::store-atom
     ^{:doc "Atom used for in-memory storage. Override for different storage backends."}
     [make-store-atom]
     
     ::store-fn
     ^{:doc "Function to save tasks. Receives a task list."
       :api {:args [["task-list" {} :map]]
             :ret :nil}}
     [make-store-fn ::store-atom]

     ::load-fn
     ^{:doc "Function to load tasks. Returns a task list."
       :api {:ret :map}}
     [make-load-fn ::store-atom :todo.plugins.domain/create-task-list]

     ::delete-fn
     ^{:doc "Function to delete all tasks."
       :api {:ret :nil}}
     [make-delete-fn ::store-atom]}}))
