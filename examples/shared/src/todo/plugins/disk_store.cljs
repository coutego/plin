(ns todo.plugins.disk-store
  "File-based storage implementation for Node.js (nbb).
   
   This plugin provides file-based persistence using EDN format for Node.js
   applications (node-tui and node-server).
   
   Data is stored in examples/shared/todo-data.edn so all Node.js examples
   share the same data file.
   
   Dependencies:
   - :todo/persistence - the interface this plugin implements
   - :todo/domain - for create-task-list (injected)
   
   Beans defined:
   - ::data-file - Path to the data file (default: examples/shared/todo-data.edn)
   - Overrides :todo.plugins.persistence/load-fn
   - Overrides :todo.plugins.persistence/store-fn
   - Overrides :todo.plugins.persistence/delete-fn"
  (:require ["fs" :as fs]
            ["path" :as path]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [plin.core :as pi]))

;; =============================================================================
;; Serialization Helpers
;; =============================================================================

(defn- serialize-task
  "Converts a task to a serializable map, converting dates to ISO strings."
  [task]
  (-> (into {} task)
      (update :due-date #(when % (.toISOString %)))))

(defn- serialize-task-list
  "Converts a task-list to a serializable map."
  [task-list]
  {:tasks (into {} (map (fn [[k v]] [k (serialize-task v)]) (:tasks task-list)))
   :next-id (:next-id task-list)})

(defn- deserialize-task
  "Converts a serialized task back to proper types, parsing date strings."
  [task]
  (-> task
      (update :due-date #(when % (js/Date. %)))))

(defn- deserialize-task-list
  "Converts a serialized task-list back to proper types."
  [data]
  (-> data
      (update :tasks #(into {} (map (fn [[k v]] [k (deserialize-task v)]) %)))))

;; =============================================================================
;; Factory Functions
;; =============================================================================

(defn make-data-file
  "Factory: creates the data file path.
   Uses examples/shared/todo-data.edn so all Node.js examples share the same file."
  []
  (let [shared-dir (path/resolve ".." "shared")]
    (path/join shared-dir "todo-data.edn")))

(defn make-load-fn
  "Factory: creates load function with injected data-file and create-task-list."
  [data-file create-task-list]
  (fn []
    (if (fs/existsSync data-file)
      (try
        (let [content (fs/readFileSync data-file "utf8")]
          (-> (edn/read-string content)
              (deserialize-task-list)))
        (catch :default e
          (println "Warning: Could not read" data-file "- starting fresh:" (.-message e))
          (create-task-list)))
      (create-task-list))))

(defn make-store-fn
  "Factory: creates store function with injected data-file."
  [data-file]
  (fn [task-list]
    (let [serializable (serialize-task-list task-list)
          content (pr-str serializable)]
      (fs/writeFileSync data-file content "utf8"))))

(defn make-delete-fn
  "Factory: creates delete function with injected data-file."
  [data-file]
  (fn []
    (when (fs/existsSync data-file)
      (fs/unlinkSync data-file))))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo/disk-store
    :doc "File-based persistence using EDN format for Node.js."
    :deps [:todo/persistence]
    
    :beans
    {;; The data file path is a bean - can be overridden for different locations
     ::data-file
     ^{:doc "Path to the EDN data file. Default: examples/shared/todo-data.edn"}
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
