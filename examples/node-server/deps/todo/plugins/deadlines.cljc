(ns todo.plugins.deadlines
  "Deadline management plugin for the Todo application.
   
   This plugin provides beans for working with task deadlines:
   - ::overdue-tasks - Returns all overdue tasks
   - ::tasks-due-today - Returns tasks due today
   - ::urgency-comparator - Function to compare tasks by urgency
   - ::format-due-date - Function to format due dates for display
   - ::due-date-status - Function to get status label for a task's due date
   
   Platform-specific plugins can redefine these beans to customize
   the deadline display and behavior."
  (:require [plin.core :as pi]
            [todo.domain :as domain]))

;; --- Default implementations ---

(defn default-format-due-date
  "Default due date formatter. Returns a simple string representation.
   Can be redefined by platform plugins for locale-specific formatting."
  [date]
  (if date
    (domain/format-date date)
    "No due date"))

(defn default-due-date-status
  "Returns a status keyword for the task's due date.
   :overdue - past due date, not completed
   :due-today - due today
   :upcoming - has a future due date
   :no-date - no due date set
   :completed - task is completed (regardless of due date)"
  [task]
  (cond
    (:completed task) :completed
    (domain/overdue? task) :overdue
    (domain/due-today? task) :due-today
    (:due-date task) :upcoming
    :else :no-date))

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

(defn default-urgency-comparator
  "Comparator function for sorting tasks by urgency.
   Overdue tasks first, then by days until due, then tasks without due dates."
  [task-a task-b]
  (let [days-a (when (:due-date task-a) (domain/days-until (:due-date task-a)))
        days-b (when (:due-date task-b) (domain/days-until (:due-date task-b)))]
    (cond
      ;; Both have due dates - compare by days
      (and days-a days-b) (compare days-a days-b)
      ;; Only a has due date - a comes first
      days-a -1
      ;; Only b has due date - b comes first
      days-b 1
      ;; Neither has due date - equal
      :else 0)))

;; --- Plugin Definition ---

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
     [:= default-format-due-date]
     
     ::due-date-status
     ^{:doc "Function to get the due date status for a task.
             Returns one of: :overdue, :due-today, :upcoming, :no-date, :completed"
       :api {:args [["task" {} :map]] :ret :keyword}}
     [:= default-due-date-status]
     
     ::status-label
     ^{:doc "Function to get a human-readable label for a due date status.
             Takes a status keyword, returns a string."
       :api {:args [["status" {} :keyword]] :ret :string}}
     [:= default-status-label]
     
     ::urgency-comparator
     ^{:doc "Comparator function for sorting tasks by urgency.
             Redefine this to customize sort order."
       :api {:args [["task-a" {} :map] ["task-b" {} :map]] :ret :int}}
     [:= default-urgency-comparator]}}))
