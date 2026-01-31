(ns todo.plugins.calendar
  "Calendar/Agenda view plugin for the Todo application.
   
   This plugin provides beans for displaying tasks in a calendar/agenda format.
   All domain and deadline dependencies are injected.
   
   Dependencies:
   - :todo/domain - for get-active-tasks, days-until, today
   - :todo/deadlines - for due-date-status
   
   Beans provided:
   - ::calendar-data - Prepares task data grouped by date for rendering
   - ::render-calendar - Renders the calendar view (default: simple text list)
   - ::render-task - Renders a single task within the calendar
   
   Platform-specific plugins should REDEFINE ::render-calendar and ::render-task
   to provide appropriate rendering for their platform (HTML, TUI, Reagent, etc.)."
  (:require [plin.core :as pi]))

;; === Factory Functions ===

(defn make-prepare-calendar-data
  "Factory: creates calendar data preparation function with injected dependencies."
  [get-active-tasks days-until today]
  (fn [task-list due-date-status-fn]
    (let [all-tasks (get-active-tasks task-list)
          grouped (group-by due-date-status-fn all-tasks)
          overdue (sort-by #(days-until (:due-date %)) (:overdue grouped))
          today-tasks (:due-today grouped)
          upcoming-tasks (:upcoming grouped)
          no-date (:no-date grouped)
          ;; Group upcoming by date
          upcoming-by-date (->> upcoming-tasks
                                (group-by :due-date)
                                (sort-by (fn [[date _]]
                                           #?(:clj (.toEpochDay date)
                                              :cljs (.getTime date))))
                                (into {}))]
      {:today (today)
       :overdue (vec overdue)
       :today-tasks (vec today-tasks)
       :upcoming upcoming-by-date
       :no-date (vec no-date)
       :stats {:total (count all-tasks)
               :overdue (count overdue)
               :due-today (count today-tasks)
               :upcoming (count upcoming-tasks)
               :no-date (count no-date)}})))

(defn make-render-task
  "Factory: creates task renderer with injected due-date-status function."
  [due-date-status]
  (fn [task format-due-date-fn status-label-fn]
    (let [status (due-date-status task)
          label (status-label-fn status)]
      (str "  - " (:title task)
           (when (seq label) (str " [" label "]"))
           (when (:due-date task)
             (str " (due: " (format-due-date-fn (:due-date task)) ")"))))))

(defn default-render-calendar
  "Default calendar renderer. Returns a plain text representation.
   
   This is the main bean that platform-specific plugins should REDEFINE
   to provide HTML, TUI, or Reagent-based rendering.
   
   Arguments:
   - calendar-data: The prepared calendar data from prepare-calendar-data
   - render-task-fn: Function to render a single task
   - format-date-fn: Function to format dates
   
   Returns a string representation of the calendar."
  [calendar-data render-task-fn format-date-fn]
  (let [{:keys [overdue today-tasks upcoming no-date stats]} calendar-data]
    (str
     "=== Task Calendar ===\n"
     "Total active: " (:total stats)
     " | Overdue: " (:overdue stats)
     " | Due today: " (:due-today stats)
     " | Upcoming: " (:upcoming stats)
     " | No date: " (:no-date stats)
     "\n\n"

     ;; Overdue section
     (when (seq overdue)
       (str "!! OVERDUE !!\n"
            (apply str (map #(str (render-task-fn %) "\n") overdue))
            "\n"))

     ;; Today section
     (when (seq today-tasks)
       (str "TODAY\n"
            (apply str (map #(str (render-task-fn %) "\n") today-tasks))
            "\n"))

     ;; Upcoming section (grouped by date)
     (when (seq upcoming)
       (apply str
              (for [[date tasks] upcoming]
                (str (format-date-fn date) "\n"
                     (apply str (map #(str (render-task-fn %) "\n") tasks))
                     "\n"))))

     ;; No date section
     (when (seq no-date)
       (str "NO DUE DATE\n"
            (apply str (map #(str (render-task-fn %) "\n") no-date)))))))

;; === Plugin Definition ===

(def plugin
  (pi/plugin
   {:id :todo/calendar
    :doc "Calendar/Agenda view for tasks. Groups tasks by due date and renders them."
    :deps []

    :beans
    {::calendar-data
     ^{:doc "Function to prepare calendar data from a task list.
             Takes task-list and due-date-status-fn, returns structured data."
       :api {:args [["task-list" {} :map] ["due-date-status-fn" {} :fn]]
             :ret :map}}
     [make-prepare-calendar-data
      :todo.plugins.domain/get-active-tasks
      :todo.plugins.domain/days-until
      :todo.plugins.domain/today]

     ::render-task
     ^{:doc "Function to render a single task in the calendar view.
             Takes task, format-due-date-fn, and status-label-fn.
             Returns a renderable representation (string by default).
             Redefine in platform plugins for HTML/TUI rendering."
       :api {:args [["task" {} :map] ["format-due-date-fn" {} :fn] ["status-label-fn" {} :fn]]
             :ret :any}}
     [make-render-task :todo.plugins.deadlines/due-date-status]

     ::render-calendar
     ^{:doc "Function to render the full calendar view.
             Takes calendar-data, render-task-fn, and format-date-fn.
             Returns a renderable representation (string by default).

             THIS IS THE MAIN BEAN TO REDEFINE in platform-specific plugins
             to provide HTML tables, TUI grids, or Reagent components."
       :api {:args [["calendar-data" {} :map] ["render-task-fn" {} :fn] ["format-date-fn" {} :fn]]
             :ret :any}}
     [:= default-render-calendar]}}))
