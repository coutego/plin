(ns todo-jvm-server.plugins.http-server
  "HTTP server plugin using Ring and Jetty.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - NO direct requires of domain, deadlines, or calendar namespaces
   - ALL functionality comes through injected beans
   - Each handler receives exactly the dependencies it needs
   - The boot function receives only the start-server function
   
   Dependencies:
   - :todo/persistence - for load-fn, store-fn
   - :todo/domain - for task operations (add, toggle, delete, etc.)
   - :todo/deadlines - for due-date-status, format-due-date, status-label
   - :todo/calendar - for calendar-data, render-calendar, render-task
   
   Routes:
   - GET  /              - Task list view
   - GET  /calendar      - Calendar/agenda view
   - POST /tasks         - Create new task (with optional due date)
   - POST /tasks/:id/toggle - Toggle task completion
   - POST /tasks/:id/delete - Delete a task
   - POST /tasks/:id/due-date - Set due date
   - POST /tasks/clear-completed - Clear completed tasks"
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.middleware.keyword-params :as kw-params]
            [clojure.string :as str]
            [plin.core :as pi]))

;; =============================================================================
;; Pure Utility Functions (no dependencies needed)
;; =============================================================================

(def css-styles
  "body { font-family: system-ui, sans-serif; max-width: 700px; margin: 40px auto; padding: 0 20px; background: #f5f5f5; }
   h1 { color: #333; text-align: center; }
   h2 { color: #555; margin-top: 30px; }
   h3 { color: #666; margin: 15px 0 10px 0; font-size: 16px; }
   h4 { color: #888; margin: 10px 0 5px 0; font-size: 14px; }
   .app-container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
   .nav { text-align: center; margin-bottom: 20px; padding-bottom: 15px; border-bottom: 1px solid #eee; }
   .nav a { color: #4CAF50; text-decoration: none; margin: 0 15px; font-weight: 500; }
   .nav a:hover { text-decoration: underline; }
   .add-form { display: flex; gap: 10px; margin-bottom: 20px; flex-wrap: wrap; }
   .add-form input[type='text'] { flex: 2; min-width: 200px; padding: 10px; font-size: 16px; border: 1px solid #ddd; border-radius: 4px; }
   .add-form input[type='date'] { flex: 1; min-width: 140px; padding: 10px; font-size: 14px; border: 1px solid #ddd; border-radius: 4px; }
   .add-form button { padding: 10px 20px; font-size: 16px; background: #4CAF50; color: white; border: none; border-radius: 4px; cursor: pointer; }
   .filters { text-align: center; margin-bottom: 20px; }
   .filters a { color: #666; text-decoration: none; margin: 0 10px; padding: 5px 10px; border-radius: 4px; }
   .filters a.active { background: #4CAF50; color: white; }
   .task { display: flex; align-items: center; padding: 12px; border-bottom: 1px solid #eee; gap: 10px; }
   .task.completed .task-title { text-decoration: line-through; opacity: 0.6; }
   .task.overdue { background: #fff5f5; }
   .task.due-today { background: #fffef5; }
   .task input[type='checkbox'] { width: 20px; height: 20px; cursor: pointer; }
   .task .task-title { flex: 1; font-size: 16px; }
   .task .task-due { font-size: 12px; color: #888; }
   .task .task-due.overdue { color: #d32f2f; font-weight: bold; }
   .task .task-due.due-today { color: #f57c00; font-weight: bold; }
   .task .due-form { display: flex; gap: 5px; }
   .task .due-form input[type='date'] { padding: 4px; font-size: 12px; border: 1px solid #ddd; border-radius: 4px; }
   .task .due-form button { padding: 4px 8px; font-size: 12px; background: #2196F3; color: white; border: none; border-radius: 4px; cursor: pointer; }
   .task button.delete { background: #ff4444; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; }
   .empty { text-align: center; color: #999; padding: 40px; }
   .actions { text-align: center; margin-top: 20px; padding-top: 20px; border-top: 1px solid #eee; }
   .actions button { background: #666; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
   .stats { text-align: center; color: #666; margin-top: 10px; font-size: 14px; }
   /* Calendar styles */
   .calendar-view { margin-top: 20px; }
   .calendar-stats { display: flex; flex-wrap: wrap; gap: 15px; justify-content: center; margin-bottom: 20px; padding: 15px; background: #f9f9f9; border-radius: 8px; }
   .calendar-stats .stat { font-size: 14px; }
   .calendar-stats .stat.overdue strong { color: #d32f2f; }
   .calendar-stats .stat.due-today strong { color: #f57c00; }
   .calendar-section { margin-bottom: 25px; }
   .calendar-section.overdue { border-left: 4px solid #d32f2f; padding-left: 15px; }
   .calendar-section.due-today { border-left: 4px solid #f57c00; padding-left: 15px; }
   .calendar-section.upcoming { border-left: 4px solid #4CAF50; padding-left: 15px; }
   .calendar-section.no-date { border-left: 4px solid #9e9e9e; padding-left: 15px; }
   .calendar-section ul { list-style: none; padding: 0; margin: 0; }
   .calendar-task { padding: 8px 0; border-bottom: 1px solid #eee; display: flex; gap: 10px; align-items: center; }
   .calendar-task .task-title { flex: 1; }
   .calendar-task .task-due { font-size: 12px; color: #888; }
   .calendar-task .task-status { font-size: 11px; padding: 2px 6px; border-radius: 3px; }
   .calendar-task .task-status.overdue { background: #ffebee; color: #c62828; }
   .calendar-task .task-status.due-today { background: #fff3e0; color: #ef6c00; }
   .date-group { margin: 10px 0; }
   .calendar-empty { text-align: center; color: #999; padding: 40px; }")

(defn html-page
  "Wraps body content in a complete HTML page."
  [body]
  (str "<!DOCTYPE html>
<html>
<head>
  <meta charset='utf-8'>
  <title>Todo App - JVM</title>
  <style>" css-styles "</style>
</head>
<body>
  <div class='app-container'>
    <nav class='nav'>
      <a href='/'>Task List</a>
      <a href='/calendar'>Calendar View</a>
    </nav>
    " body "
  </div>
</body>
</html>"))

(defn escape-html
  "Escapes HTML special characters."
  [s]
  (str/escape (str s) {\< "&lt;" \> "&gt;" \& "&amp;" \" "&quot;"}))

;; =============================================================================
;; Component Renderers (receive dependencies as first arguments)
;; =============================================================================

(defn task-html
  "Renders a single task as HTML.
   Dependencies: due-date-status, format-date
   Data: task"
  [due-date-status format-date task]
  (let [status (due-date-status task)
        status-class (name status)
        due-date-str (when (:due-date task) (format-date (:due-date task)))]
    (str "<div class='task " status-class "'>"
         "<form method='POST' action='/tasks/" (:id task) "/toggle'>"
         "<input type='checkbox' " (when (:completed task) "checked") " onChange='this.form.submit()' />"
         "</form>"
         "<span class='task-title'>" (escape-html (:title task)) "</span>"
         (when due-date-str
           (str "<span class='task-due " status-class "'>" due-date-str "</span>"))
         "<form class='due-form' method='POST' action='/tasks/" (:id task) "/due-date'>"
         "<input type='date' name='due-date' value='" (or due-date-str "") "' />"
         "<button type='submit'>Set</button>"
         "</form>"
         "<form method='POST' action='/tasks/" (:id task) "/delete'>"
         "<button type='submit' class='delete'>Delete</button>"
         "</form>"
         "</div>")))

(defn task-list-html
  "Renders the task list HTML.
   Arguments: task-html-fn, sorted-tasks, active-count, completed-count, overdue-count, filter-param"
  [task-html-fn sorted-tasks active-count completed-count overdue-count filter-param]
  (str "<h1>Todo List</h1>
        <form class='add-form' method='POST' action='/tasks'>
          <input type='text' name='title' placeholder='What needs to be done?' required />
          <input type='date' name='due-date' />
          <button type='submit'>Add</button>
        </form>
        <div class='filters'>
          <a href='/?filter=all'" (when (= filter-param "all") " class='active'") ">All</a>
          <a href='/?filter=active'" (when (= filter-param "active") " class='active'") ">Active</a>
          <a href='/?filter=overdue'" (when (= filter-param "overdue") " class='active'") ">Overdue</a>
          <a href='/?filter=completed'" (when (= filter-param "completed") " class='active'") ">Completed</a>
        </div>
        <div class='tasks'>"
       (if (empty? sorted-tasks)
         "<div class='empty'>No tasks to display</div>"
         (apply str (map task-html-fn sorted-tasks)))
       "</div>
        <div class='actions'>
          <form method='POST' action='/tasks/clear-completed'>
            <button type='submit'>Clear Completed</button>
          </form>
        </div>
        <div class='stats'>"
       active-count " active, "
       completed-count " completed"
       (when (pos? overdue-count)
         (str ", <strong style='color:#d32f2f'>" overdue-count " overdue</strong>"))
       "</div>"))

;; =============================================================================
;; Route Handlers (dependencies first, request last)
;; =============================================================================

(defn home-handler
  "Home page handler - displays task list.
   Dependencies: load-fn, get-all-tasks, get-active-tasks, get-completed-tasks,
                 get-overdue-tasks, sort-by-urgency, task-html-fn"
  [load-fn get-all-tasks get-active-tasks get-completed-tasks
   get-overdue-tasks sort-by-urgency task-html-fn request]
  (let [task-list (load-fn)
        filter-param (get-in request [:query-params "filter"] "all")
        tasks (case filter-param
                "active" (get-active-tasks task-list)
                "completed" (get-completed-tasks task-list)
                "overdue" (get-overdue-tasks task-list)
                (get-all-tasks task-list))
        sorted-tasks (sort-by-urgency tasks)
        active-count (count (get-active-tasks task-list))
        completed-count (count (get-completed-tasks task-list))
        overdue-count (count (get-overdue-tasks task-list))]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (html-page (task-list-html task-html-fn sorted-tasks
                                      active-count completed-count
                                      overdue-count filter-param))}))

(defn calendar-handler
  "Calendar view handler.
   Dependencies: load-fn, calendar-data-fn, render-calendar-fn, render-task-fn,
                 format-due-date, status-label, due-date-status"
  [load-fn calendar-data-fn render-calendar-fn render-task-fn
   format-due-date status-label due-date-status request]
  (let [task-list (load-fn)
        cal-data (calendar-data-fn task-list due-date-status)
        task-renderer (fn [task] (render-task-fn task format-due-date status-label))
        calendar-html (render-calendar-fn cal-data task-renderer format-due-date)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (html-page (str "<h1>Calendar View</h1>" calendar-html))}))

(defn create-task-handler
  "Create task handler.
   Dependencies: load-fn, store-fn, add-task, parse-date"
  [load-fn store-fn add-task parse-date request]
  (let [title (get-in request [:params :title])
        due-date-str (get-in request [:params :due-date])
        due-date (parse-date due-date-str)
        task-list (load-fn)
        new-task-list (add-task task-list title due-date)]
    (store-fn new-task-list)
    {:status 302
     :headers {"Location" "/"}
     :body ""}))

(defn toggle-task-handler
  "Toggle task completion handler.
   Dependencies: load-fn, store-fn, toggle-task"
  [load-fn store-fn toggle-task request]
  (let [task-id (Integer/parseInt (get-in request [:params :id]))
        task-list (load-fn)
        new-task-list (toggle-task task-list task-id)]
    (store-fn new-task-list)
    {:status 302
     :headers {"Location" (get-in request [:headers "referer"] "/")}
     :body ""}))

(defn delete-task-handler
  "Delete task handler.
   Dependencies: load-fn, store-fn, delete-task"
  [load-fn store-fn delete-task request]
  (let [task-id (Integer/parseInt (get-in request [:params :id]))
        task-list (load-fn)
        new-task-list (delete-task task-list task-id)]
    (store-fn new-task-list)
    {:status 302
     :headers {"Location" (get-in request [:headers "referer"] "/")}
     :body ""}))

(defn set-due-date-handler
  "Set due date handler.
   Dependencies: load-fn, store-fn, set-due-date, parse-date"
  [load-fn store-fn set-due-date parse-date request]
  (let [task-id (Integer/parseInt (get-in request [:params :id]))
        due-date-str (get-in request [:params :due-date])
        due-date (parse-date due-date-str)
        task-list (load-fn)
        new-task-list (set-due-date task-list task-id due-date)]
    (store-fn new-task-list)
    {:status 302
     :headers {"Location" (get-in request [:headers "referer"] "/")}
     :body ""}))

(defn clear-completed-handler
  "Clear completed tasks handler.
   Dependencies: load-fn, store-fn, clear-completed"
  [load-fn store-fn clear-completed request]
  (let [task-list (load-fn)
        new-task-list (clear-completed task-list)]
    (store-fn new-task-list)
    {:status 302
     :headers {"Location" "/"}
     :body ""}))

(defn not-found-handler
  "404 handler."
  [_request]
  {:status 404
   :headers {"Content-Type" "text/plain"}
   :body "Not found"})

;; =============================================================================
;; Router Factory
;; =============================================================================

(defn make-router
  "Creates the router function with all handlers pre-injected."
  [home-handler create-handler toggle-handler delete-handler
   due-date-handler clear-handler calendar-handler]
  (fn [request]
    (let [uri (:uri request)
          method (:request-method request)]
      (cond
        (and (= uri "/") (= method :get))
        (home-handler request)

        (and (= uri "/calendar") (= method :get))
        (calendar-handler request)

        (and (= uri "/tasks") (= method :post))
        (create-handler request)

        ;; Clear completed - must come before generic /tasks/:id/:action route
        (and (= uri "/tasks/clear-completed") (= method :post))
        (clear-handler request)

        (and (str/starts-with? uri "/tasks/") (= method :post))
        (let [[_ _ id action] (str/split uri #"/")]
          (case action
            "toggle" (toggle-handler (assoc-in request [:params :id] id))
            "delete" (delete-handler (assoc-in request [:params :id] id))
            "due-date" (due-date-handler (assoc-in request [:params :id] id))
            (not-found-handler request)))

        :else
        (not-found-handler request)))))

;; =============================================================================
;; Server Lifecycle (also uses DI)
;; =============================================================================

(defn make-server-atom
  "Factory: creates the server state atom."
  []
  (atom nil))

(defn make-start-server
  "Factory: creates start-server function with injected server-atom and router."
  [server-atom router]
  (fn [port]
    (let [app (-> router
                  kw-params/wrap-keyword-params
                  params/wrap-params)]
      (reset! server-atom (jetty/run-jetty app {:port port :join? false}))
      (println (str "Server started on http://localhost:" port)))))

(defn make-stop-server
  "Factory: creates stop-server function with injected server-atom."
  [server-atom]
  (fn []
    (when-let [s @server-atom]
      (.stop s)
      (reset! server-atom nil)
      (println "Server stopped"))))

(defn boot-fn
  "Boot function - receives start-server (injected) and container (from plin.boot)."
  [start-server _container]
  (start-server 3000))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo-jvm-server/http
    :doc "HTTP server with fully injected dependencies. Demonstrates proper DI architecture."
    :deps []

    :beans
    {;; === Server State ===
     ::server-atom
     ^{:doc "Atom holding the Jetty server instance."}
     [make-server-atom]

     ;; === Task HTML Renderer ===
     ;; Partially applied with due-date-status and format-date
     ::task-html-fn
     ^{:doc "Function to render a task as HTML. Pre-injected with status/format deps."}
     [partial task-html
      :todo.plugins.deadlines/due-date-status
      :todo.plugins.deadlines/format-due-date]

     ;; === Route Handlers ===
     ;; Each handler is partially applied with exactly the dependencies it needs

     ::home-handler
     ^{:doc "Home page handler - displays filtered task list."}
     [partial home-handler
      :todo.plugins.persistence/load-fn
      :todo.plugins.domain/get-all-tasks
      :todo.plugins.domain/get-active-tasks
      :todo.plugins.domain/get-completed-tasks
      :todo.plugins.domain/get-overdue-tasks
      :todo.plugins.domain/sort-by-urgency
      ::task-html-fn]

     ::calendar-handler
     ^{:doc "Calendar view handler."}
     [partial calendar-handler
      :todo.plugins.persistence/load-fn
      :todo.plugins.calendar/calendar-data
      :todo.plugins.calendar/render-calendar
      :todo.plugins.calendar/render-task
      :todo.plugins.deadlines/format-due-date
      :todo.plugins.deadlines/status-label
      :todo.plugins.deadlines/due-date-status]

     ::create-handler
     ^{:doc "Create task handler."}
     [partial create-task-handler
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      :todo.plugins.domain/add-task
      :todo.plugins.domain/parse-date]

     ::toggle-handler
     ^{:doc "Toggle task completion handler."}
     [partial toggle-task-handler
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      :todo.plugins.domain/toggle-task]

     ::delete-handler
     ^{:doc "Delete task handler."}
     [partial delete-task-handler
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      :todo.plugins.domain/delete-task]

     ::due-date-handler
     ^{:doc "Set due date handler."}
     [partial set-due-date-handler
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      :todo.plugins.domain/set-due-date
      :todo.plugins.domain/parse-date]

     ::clear-handler
     ^{:doc "Clear completed tasks handler."}
     [partial clear-completed-handler
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      :todo.plugins.domain/clear-completed]

     ;; === Router ===
     ;; Combines all handlers into a single routing function
     ::router
     ^{:doc "The main router function with all handlers injected."}
     [make-router
      ::home-handler
      ::create-handler
      ::toggle-handler
      ::delete-handler
      ::due-date-handler
      ::clear-handler
      ::calendar-handler]

     ;; === Server Lifecycle ===
     ::start-server
     ^{:doc "Function to start the HTTP server on a given port."}
     [make-start-server ::server-atom ::router]

     ::stop-server
     ^{:doc "Function to stop the HTTP server."}
     [make-stop-server ::server-atom]

     ;; === Boot Function ===
     ;; Receives only start-server - exactly what it needs
     :plin.boot/boot-fn
     ^{:doc "Boot function that starts the HTTP server on port 3000."}
     [partial boot-fn ::start-server]}}))
