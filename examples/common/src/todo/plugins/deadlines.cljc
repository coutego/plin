(ns todo.plugins.deadlines
  "Deadline management plugin for the Todo application.
   
   This plugin provides beans for working with task deadlines.
   All domain dependencies are injected rather than directly required.
   
   Dependencies:
   - :todo/domain - for overdue?, due-today?, format-date, days-until
   
   Beans provided:
   - ::format-due-date - Function to format due dates for display
   - ::due-date-status - Function to get status keyword for a task
   - ::status-label - Function to get human-readable status label
   - ::urgency-comparator - Comparator for sorting by urgency
   
   Platform-specific plugins can redefine these beans to customize
   the deadline display and behavior."
  (:require [plin.core :as pi]))

;; === Factory Functions ===
;; These create the actual bean implementations with injected dependencies

(defn make-format-due-date
  "Factory: creates format-due-date function with injected format-date."
  [format-date]
  (fn [date]
    (if date
      (format-date date)
      "No due date")))

(defn make-due-date-status
  "Factory: creates due-date-status function with injected predicates."
  [overdue? due-today?]
  (fn [task]
    (cond
      (:completed task) :completed
      (overdue? task) :overdue
      (due-today? task) :due-today
      (:due-date task) :upcoming
      :else :no-date)))

(defn default-status-label
  "Returns a human-readable label for the due date status.
   Can be redefined by platform plugins for different text or styling."
  [status]
  (case status
    :overdue "Overdue"
    :due-today "Due Today"
    :upcoming "Upcoming"
    :no-date ""
    :completed "Done"
    ""))

(defn make-urgency-comparator
  "Factory: creates urgency comparator with injected days-until."
  [days-until]
  (fn [task-a task-b]
    (let [days-a (when (:due-date task-a) (days-until (:due-date task-a)))
          days-b (when (:due-date task-b) (days-until (:due-date task-b)))]
      (cond
        ;; Both have due dates - compare by days
        (and days-a days-b) (compare days-a days-b)
        ;; Only a has due date - a comes first
        days-a -1
        ;; Only b has due date - b comes first
        days-b 1
        ;; Neither has due date - equal
        :else 0))))

;; === Plugin Definition ===

(def plugin
  (pi/plugin
   {:id :todo/deadlines
    :doc "Deadline management for tasks. Provides beans for deadline calculations and formatting."
    :deps []
    
    :beans
    {::format-due-date
     ^{:doc "Function to format a due date for display.
             Takes a date and returns a string.
             Redefine this in platform plugins for custom formatting."
       :api {:args [["date" {} :any]] :ret :string}}
     [make-format-due-date :todo.plugins.domain/format-date]
     
     ::due-date-status
     ^{:doc "Function to get the due date status for a task.
             Returns one of: :overdue, :due-today, :upcoming, :no-date, :completed"
       :api {:args [["task" {} :map]] :ret :keyword}}
     [make-due-date-status
      :todo.plugins.domain/overdue?
      :todo.plugins.domain/due-today?]
     
     ::status-label
     ^{:doc "Function to get a human-readable label for a due date status.
             Takes a status keyword, returns a string."
       :api {:args [["status" {} :keyword]] :ret :string}}
     [:= default-status-label]
     
     ::urgency-comparator
     ^{:doc "Comparator function for sorting tasks by urgency.
             Redefine this to customize sort order."
       :api {:args [["task-a" {} :map] ["task-b" {} :map]] :ret :int}}
     [make-urgency-comparator :todo.plugins.domain/days-until]}}))
