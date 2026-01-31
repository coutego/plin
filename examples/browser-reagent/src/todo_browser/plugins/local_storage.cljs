(ns todo-browser.plugins.local-storage
  "LocalStorage-based persistence implementation for the browser.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - The storage key is a bean (::storage-key) that can be overridden
   - All functions receive dependencies via DI
   - Uses JSON format for browser localStorage compatibility
   
   Dependencies:
   - :todo/persistence - the interface this plugin implements
   - :todo/domain - for create-task-list and map->Task (injected)
   
   Beans defined:
   - ::storage-key - The localStorage key (default: \"todo-tasks\")
   - Overrides :todo.plugins.persistence/load-fn
   - Overrides :todo.plugins.persistence/store-fn
   - Overrides :todo.plugins.persistence/delete-fn"
  (:require [plin.core :as pi]))

;; =============================================================================
;; Factory Functions (all receive dependencies via DI)
;; =============================================================================

(defn make-storage-key
  "Factory: creates the default localStorage key.
   Can be overridden to use a different key."
  []
  "todo-tasks")

(defn make-load-fn
  "Factory: creates load function with injected storage-key and create-task-list."
  [storage-key create-task-list]
  (fn []
    (if-let [stored (.getItem js/localStorage storage-key)]
      (try
        (let [parsed (js/JSON.parse stored)
              data (js->clj parsed :keywordize-keys true)]
          ;; Reconstruct the task-list structure
          (if (and (:tasks data) (seq (:tasks data)))
            {:tasks (into {} 
                          (map (fn [[k v]]
                                 [(if (string? k) (js/parseInt k) k)
                                  (-> v
                                      (update :id #(if (string? %) (js/parseInt %) %)))])
                               (:tasks data)))
             :next-id (or (:next-id data) 
                          (inc (apply max (map #(if (string? %) (js/parseInt %) %) 
                                               (keys (:tasks data))))))}
            (create-task-list)))
        (catch :default e
          (js/console.warn "Could not parse localStorage data, starting fresh:" e)
          (create-task-list)))
      (create-task-list))))

(defn make-store-fn
  "Factory: creates store function with injected storage-key."
  [storage-key]
  (fn [task-list]
    (.setItem js/localStorage storage-key (js/JSON.stringify (clj->js task-list)))))

(defn make-delete-fn
  "Factory: creates delete function with injected storage-key."
  [storage-key]
  (fn []
    (.removeItem js/localStorage storage-key)))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo/local-storage
    :doc "Browser localStorage persistence implementation with proper DI."
    :deps [:todo/persistence]
    
    :beans
    {;; The storage key is a bean - can be overridden for different apps
     ::storage-key
     ^{:doc "LocalStorage key for storing tasks. Override for namespacing."}
     [make-storage-key]

     ;; Override persistence interface beans
     :todo.plugins.persistence/load-fn
     ^{:doc "Load tasks from localStorage. Returns new task list if not found."
       :api {:ret :map}}
     [make-load-fn ::storage-key :todo.plugins.domain/create-task-list]

     :todo.plugins.persistence/store-fn
     ^{:doc "Save tasks to localStorage as JSON."
       :api {:args [["task-list" {} :map]] :ret :nil}}
     [make-store-fn ::storage-key]

     :todo.plugins.persistence/delete-fn
     ^{:doc "Remove tasks from localStorage."
       :api {:ret :nil}}
     [make-delete-fn ::storage-key]}}))
