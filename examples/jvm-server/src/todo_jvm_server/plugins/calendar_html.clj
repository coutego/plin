(ns todo-jvm-server.plugins.calendar-html
  "HTML calendar renderer plugin for the JVM HTTP server.
   
   This plugin OVERRIDES the calendar rendering beans from todo.plugins.calendar
   to provide HTML-based rendering suitable for the web interface.
   
   Dependencies:
   - :todo/calendar - the interface this plugin overrides
   - :todo/deadlines - for due-date-status (injected into render-task)
   
   This demonstrates how platform-specific plugins can customize the output
   format by overriding beans defined in common plugins."
  (:require [plin.core :as pi]
            [clojure.string :as str]))

;; =============================================================================
;; HTML Renderers
;; =============================================================================

(defn make-html-render-task
  "Factory: creates HTML task renderer with injected due-date-status."
  [due-date-status]
  (fn [task format-due-date-fn status-label-fn]
    (let [status (due-date-status task)
          status-class (name status)
          label (status-label-fn status)]
      (str "<li class='calendar-task " status-class "'>"
           "<span class='task-title'>"
           (str/escape (:title task) {\< "&lt;" \> "&gt;" \& "&amp;"})
           "</span>"
           (when (:due-date task)
             (str "<span class='task-due'>" (format-due-date-fn (:due-date task)) "</span>"))
           (when (seq label)
             (str "<span class='task-status " status-class "'>" label "</span>"))
           "</li>"))))

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
   {:id :todo-jvm-server/calendar-html
    :doc "HTML calendar renderer. Overrides default text-based calendar rendering."
    :deps []

    :beans
    {;; Override render-task to produce HTML
     ;; Uses factory to inject due-date-status
     :todo.plugins.calendar/render-task
     ^{:doc "Renders a task as HTML for the calendar view."}
     [make-html-render-task :todo.plugins.deadlines/due-date-status]

     ;; Override render-calendar to produce HTML
     :todo.plugins.calendar/render-calendar
     ^{:doc "Renders the full calendar view as HTML."}
     [:= html-render-calendar]}}))
