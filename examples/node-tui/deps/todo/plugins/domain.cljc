(ns todo.plugins.domain
  "Plugin that exposes domain functions as injectable beans.
   
   This plugin wraps the pure functions from todo.domain namespace,
   making them available for dependency injection. Other plugins should
   depend on :todo/domain and inject these beans rather than requiring
   the todo.domain namespace directly.
   
   This approach enables:
   - Explicit dependency declarations via :deps
   - Easy testing with mock implementations
   - Clear visualization of the dependency graph"
  (:require [plin.core :as pi]
            [todo.domain :as domain]))

(def plugin
  (pi/plugin
   {:id :todo/domain
    :doc "Core domain operations exposed as injectable beans."
    :deps []
    
    :beans
    {;; === Task List Operations ===
     
     ::create-task-list
     ^{:doc "Creates a new empty task list."
       :api {:ret :map}}
     [:= domain/create-task-list]
     
     ::add-task
     ^{:doc "Adds a new task to the task list.
             Args: task-list, title, [due-date]"
       :api {:args [["task-list" {} :map] ["title" {} :string] ["due-date" {:optional true} :any]]
             :ret :map}}
     [:= domain/add-task]
     
     ::toggle-task
     ^{:doc "Toggles the completion status of a task.
             Args: task-list, task-id"
       :api {:args [["task-list" {} :map] ["task-id" {} :int]]
             :ret :map}}
     [:= domain/toggle-task]
     
     ::complete-task
     ^{:doc "Marks a task as completed.
             Args: task-list, task-id"
       :api {:args [["task-list" {} :map] ["task-id" {} :int]]
             :ret :map}}
     [:= domain/complete-task]
     
     ::uncomplete-task
     ^{:doc "Marks a task as not completed.
             Args: task-list, task-id"
       :api {:args [["task-list" {} :map] ["task-id" {} :int]]
             :ret :map}}
     [:= domain/uncomplete-task]
     
     ::delete-task
     ^{:doc "Removes a task from the task list.
             Args: task-list, task-id"
       :api {:args [["task-list" {} :map] ["task-id" {} :int]]
             :ret :map}}
     [:= domain/delete-task]
     
     ::clear-completed
     ^{:doc "Removes all completed tasks from the list.
             Args: task-list"
       :api {:args [["task-list" {} :map]]
             :ret :map}}
     [:= domain/clear-completed]
     
     ::set-due-date
     ^{:doc "Sets or updates the due date for a task.
             Args: task-list, task-id, due-date"
       :api {:args [["task-list" {} :map] ["task-id" {} :int] ["due-date" {} :any]]
             :ret :map}}
     [:= domain/set-due-date]
     
     ::get-task
     ^{:doc "Retrieves a single task by ID.
             Args: task-list, task-id"
       :api {:args [["task-list" {} :map] ["task-id" {} :int]]
             :ret :map}}
     [:= domain/get-task]
     
     ;; === Query Operations ===
     
     ::get-all-tasks
     ^{:doc "Returns all tasks as a vector, sorted by creation time.
             Args: task-list"
       :api {:args [["task-list" {} :map]]
             :ret :vector}}
     [:= domain/get-all-tasks]
     
     ::get-active-tasks
     ^{:doc "Returns all incomplete tasks.
             Args: task-list"
       :api {:args [["task-list" {} :map]]
             :ret :vector}}
     [:= domain/get-active-tasks]
     
     ::get-completed-tasks
     ^{:doc "Returns all completed tasks.
             Args: task-list"
       :api {:args [["task-list" {} :map]]
             :ret :vector}}
     [:= domain/get-completed-tasks]
     
     ::get-overdue-tasks
     ^{:doc "Returns all overdue tasks (past due date and not completed).
             Args: task-list"
       :api {:args [["task-list" {} :map]]
             :ret :vector}}
     [:= domain/get-overdue-tasks]
     
     ::get-tasks-due-today
     ^{:doc "Returns all tasks due today.
             Args: task-list"
       :api {:args [["task-list" {} :map]]
             :ret :vector}}
     [:= domain/get-tasks-due-today]
     
     ::get-tasks-with-due-date
     ^{:doc "Returns all tasks that have a due date set, sorted by due date.
             Args: task-list"
       :api {:args [["task-list" {} :map]]
             :ret :vector}}
     [:= domain/get-tasks-with-due-date]
     
     ;; === Date Utilities ===
     
     ::today
     ^{:doc "Returns today's date.
             On JVM: java.time.LocalDate, On JS: Date object"
       :api {:ret :any}}
     [:= domain/today]
     
     ::parse-date
     ^{:doc "Parses a date string in YYYY-MM-DD format.
             Returns nil if the string is empty or invalid.
             Args: date-str"
       :api {:args [["date-str" {} :string]]
             :ret :any}}
     [:= domain/parse-date]
     
     ::format-date
     ^{:doc "Formats a date as YYYY-MM-DD string.
             Returns empty string if date is nil.
             Args: date"
       :api {:args [["date" {} :any]]
             :ret :string}}
     [:= domain/format-date]
     
     ::days-until
     ^{:doc "Calculates the number of days from today until the given date.
             Returns nil if date is nil. Negative = overdue.
             Args: date"
       :api {:args [["date" {} :any]]
             :ret :int}}
     [:= domain/days-until]
     
     ;; === Task Predicates ===
     
     ::overdue?
     ^{:doc "Returns true if the task is overdue.
             Args: task"
       :api {:args [["task" {} :map]]
             :ret :boolean}}
     [:= domain/overdue?]
     
     ::due-today?
     ^{:doc "Returns true if the task is due today.
             Args: task"
       :api {:args [["task" {} :map]]
             :ret :boolean}}
     [:= domain/due-today?]
     
     ;; === Sorting & Grouping ===
     
     ::sort-by-urgency
     ^{:doc "Sorts tasks by urgency (overdue first, then by due date).
             Args: tasks (sequence)"
       :api {:args [["tasks" {} :seq]]
             :ret :vector}}
     [:= domain/sort-by-urgency]
     
     ::group-tasks-by-date
     ^{:doc "Groups tasks by their due date.
             Args: tasks (sequence)"
       :api {:args [["tasks" {} :seq]]
             :ret :map}}
     [:= domain/group-tasks-by-date]}}))
