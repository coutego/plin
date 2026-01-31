(ns todo-node-server.plugins.calendar-html
  "HTML calendar renderer plugin for the Node.js HTTP server.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - NO direct requires of domain or deadlines namespaces
   - ALL functionality comes through injected beans
   - Rendering functions receive exactly the dependencies they need
   
   This plugin REDEFINES the calendar rendering beans from todo.plugins.calendar
   to provide HTML-based rendering suitable for the web interface."
  (:require [plin.core :as pi]
            [todo.plugins.calendar :as calendar]
            [clojure.string :as str]))

;; =============================================================================
;; HTML Renderers (dependencies passed as arguments)
;; =============================================================================

(defn html-render-task-impl
  "Renders a task as an HTML list item with status styling.
   Dependencies: due-date-status
   Data: task, format-due-date-fn, status-label-fn"
  [due-date-status task format-due-date-fn status-label-fn]
  (let [status (due-date-status task)
        status-class (name status)
        label (status-label-fn status)]
    (str "<li class='calendar-task " status-class "'>"
         "<span class='task-title'>" (str/escape (:title task) {\< "&lt;" \> "&gt;" \& "&amp;"}) "</span>"
         (when (:due-date task)
           (str "<span class='task-due'>" (format-due-date-fn (:due-date task)) "</span>"))
         (when (seq label)
           (str "<span class='task-status " status-class "'>" label "</span>"))
         "</li>")))

(defn html-render-calendar
  "Renders the calendar as HTML with sections for overdue, today, upcoming, and no-date tasks."
  [calendar-data render-task-fn format-date-fn]
  (let [{:keys [overdue today-tasks upcoming no-date stats]} calendar-data]
    (str
     "<div class='calendar-view'>"
     
     ;; Stats header
     "<div class='calendar-stats'>"
     "<span class='stat'>Total: <strong>" (:total stats) "</strong></span>"
     "<span class='stat overdue'>Overdue: <strong>" (:overdue stats) "</strong></span>"
     "<span class='stat due-today'>Due today: <strong>" (:due-today stats) "</strong></span>"
     "<span class='stat upcoming'>Upcoming: <strong>" (:upcoming stats) "</strong></span>"
     "<span class='stat no-date'>No date: <strong>" (:no-date stats) "</strong></span>"
     "</div>"
     
     ;; Overdue section
     (when (seq overdue)
       (str "<div class='calendar-section overdue'>"
            "<h3>Overdue</h3>"
            "<ul>" (apply str (map render-task-fn overdue)) "</ul>"
            "</div>"))
     
     ;; Today section
     (when (seq today-tasks)
       (str "<div class='calendar-section due-today'>"
            "<h3>Due Today</h3>"
            "<ul>" (apply str (map render-task-fn today-tasks)) "</ul>"
            "</div>"))
     
     ;; Upcoming section
     (when (seq upcoming)
       (str "<div class='calendar-section upcoming'>"
            "<h3>Upcoming</h3>"
            (apply str
                   (for [[date tasks] upcoming]
                     (str "<div class='date-group'>"
                          "<h4>" (format-date-fn date) "</h4>"
                          "<ul>" (apply str (map render-task-fn tasks)) "</ul>"
                          "</div>")))
            "</div>"))
     
     ;; No date section
     (when (seq no-date)
       (str "<div class='calendar-section no-date'>"
            "<h3>No Due Date</h3>"
            "<ul>" (apply str (map render-task-fn no-date)) "</ul>"
            "</div>"))
     
     ;; Empty state
     (when (zero? (:total stats))
       "<div class='calendar-empty'>No active tasks</div>")
     
     "</div>")))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo-node-server/calendar-html
    :doc "HTML calendar renderer for Node.js HTTP server. Redefines calendar rendering beans with proper DI."
    :deps []
    
    :beans
    {;; Redefine render-task to produce HTML
     ;; Partially applied with due-date-status dependency
     ::calendar/render-task
     ^{:doc "Renders a task as HTML for the calendar view. Pre-injected with due-date-status."}
     [partial html-render-task-impl :todo.plugins.deadlines/due-date-status]
     
     ;; Redefine render-calendar to produce HTML
     ::calendar/render-calendar
     ^{:doc "Renders the full calendar view as HTML."}
     [:= html-render-calendar]}}))
