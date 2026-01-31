(ns todo-node-tui.plugins.terminal-ui
  "Terminal UI plugin for Node.js using Ink (React for terminals).
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - NO direct requires of domain, deadlines, or calendar namespaces
   - ALL functionality comes through injected beans
   - Factory pattern creates React component with dependencies closed over
   - The boot function receives a pre-configured start-tui function
   
   Dependencies:
   - :todo/persistence - for load-fn, store-fn
   - :todo/domain - for task operations (add, toggle, delete, etc.)
   - :todo/deadlines - for due-date-status
   - :todo/calendar - for calendar-data
   
   Provides a robust text-based user interface with deadline support:
   - List of tasks with checkboxes and due dates
   - Add new tasks with optional due dates
   - Toggle task completion
   - Delete tasks
   - Filter by All/Active/Completed/Overdue
   - Calendar view showing tasks grouped by date
   - Keyboard navigation"
  (:require ["ink" :refer [render Text Box useInput useApp]]
            ["react" :as React]
            [clojure.string :as str]
            [plin.core :as pi]
            [todo.plugins.deadlines :as deadlines]
            [todo.plugins.calendar :as calendar]))

;; =============================================================================
;; Pure Utility Functions (no dependencies needed)
;; =============================================================================

(defn $ 
  "Helper to create React elements."
  [component props & children]
  (apply React/createElement component (clj->js props) children))

(defn status-color 
  "Returns color for a given status."
  [status]
  (case status
    :overdue "red"
    :due-today "yellow"
    :upcoming "green"
    :completed "gray"
    "white"))

(defn status-badge 
  "Returns badge text for a given status."
  [status]
  (case status
    :overdue " [OVERDUE]"
    :due-today " [TODAY]"
    ""))

;; =============================================================================
;; Factory: Creates TodoApp component with dependencies closed over
;; =============================================================================

(defn make-todo-app
  "Factory: creates the TodoApp React component with all dependencies injected.
   
   Dependencies (injected):
   - load-fn, store-fn: persistence operations
   - get-all-tasks, get-active-tasks, get-completed-tasks, get-overdue-tasks: query fns
   - sort-by-urgency: sorting fn
   - add-task, toggle-task, delete-task, clear-completed: mutation fns
   - parse-date, format-date: date utilities
   - due-date-status: status calculation
   - calendar-data-fn: calendar preparation"
  [load-fn store-fn
   get-all-tasks get-active-tasks get-completed-tasks get-overdue-tasks
   sort-by-urgency add-task toggle-task delete-task clear-completed
   parse-date format-date due-date-status calendar-data-fn]
  
  ;; Return a React component function
  (fn TodoApp []
    (let [app (useApp)
          ;; Local state
          [state setState] (React/useState 
                            {:task-list (load-fn)
                             :filter :all
                             :view :list
                             :selected-index 0
                             :input-mode nil
                             :input-value ""
                             :pending-title ""})
          
          ;; Destructure state
          {:keys [task-list filter view selected-index input-mode input-value pending-title]} state
          
          ;; Calculate filtered tasks using injected functions
          all-tasks (when task-list (get-all-tasks task-list))
          active-tasks (when task-list (get-active-tasks task-list))
          completed-tasks (when task-list (get-completed-tasks task-list))
          overdue-tasks (when task-list (get-overdue-tasks task-list))
          
          visible-tasks (case filter
                          :active (or active-tasks [])
                          :completed (or completed-tasks [])
                          :overdue (or overdue-tasks [])
                          (or all-tasks []))
          
          ;; Sort using injected function
          sorted-tasks (sort-by-urgency visible-tasks)
          
          ;; Actions using injected functions
          reload-tasks! (fn []
                          (setState #(assoc % :task-list (load-fn))))
          
          add-task! (fn [title due-date-str]
                      (when (and (seq title) task-list)
                        (let [due-date (parse-date due-date-str)
                              new-list (add-task task-list title due-date)]
                          (store-fn new-list)
                          (setState #(assoc % 
                                            :task-list (load-fn)
                                            :input-mode nil
                                            :input-value ""
                                            :pending-title "")))))
          
          toggle-task! (fn []
                         (when (and (seq sorted-tasks)
                                    (< selected-index (count sorted-tasks)))
                           (let [task (nth sorted-tasks selected-index)
                                 new-list (toggle-task task-list (:id task))]
                             (store-fn new-list)
                             (reload-tasks!))))
          
          delete-task! (fn []
                         (when (and (seq sorted-tasks)
                                    (< selected-index (count sorted-tasks)))
                           (let [task (nth sorted-tasks selected-index)
                                 new-list (delete-task task-list (:id task))]
                             (store-fn new-list)
                             (setState #(assoc %
                                               :selected-index (max 0 (min selected-index (- (count sorted-tasks) 2)))
                                               :task-list (load-fn))))))
          
          clear-completed! (fn []
                             (when task-list
                               (let [new-list (clear-completed task-list)]
                                 (store-fn new-list)
                                 (setState #(assoc %
                                                   :selected-index 0
                                                   :task-list (load-fn))))))]
      
      ;; Handle keyboard input
      (useInput
       (fn [input key]
         (cond
           ;; Title input mode
           (= input-mode :title)
           (cond
             (.-escape key)
             (setState #(assoc % :input-mode nil :input-value "" :pending-title ""))
             
             (.-return key)
             (if (seq input-value)
               ;; Move to date input
               (setState #(assoc % :pending-title input-value :input-mode :date :input-value ""))
               ;; Empty title, cancel
               (setState #(assoc % :input-mode nil :input-value "")))
             
             (.-backspace key)
             (setState #(update % :input-value (fn [v] (subs v 0 (max 0 (dec (count v)))))))
             
             (and input (= (count input) 1))
             (setState #(update % :input-value str input)))
           
           ;; Date input mode
           (= input-mode :date)
           (cond
             (.-escape key)
             (add-task! pending-title nil) ;; Add without date
             
             (.-return key)
             (add-task! pending-title input-value) ;; Add with date
             
             (.-backspace key)
             (setState #(update % :input-value (fn [v] (subs v 0 (max 0 (dec (count v)))))))
             
             (and input (= (count input) 1))
             (setState #(update % :input-value str input)))
           
           ;; Normal mode
           :else
           (cond
             (= input "q")
             (.exit app)
             
             (= input "n")
             (setState #(assoc % :input-mode :title :input-value ""))
             
             (= input "t")
             (toggle-task!)
             
             (= input "d")
             (delete-task!)
             
             (= input "c")
             (clear-completed!)
             
             (= input "v")
             (setState #(update % :view (fn [v] (if (= v :list) :calendar :list))))
             
             (= input "a")
             (setState #(assoc % :filter :all :selected-index 0))
             
             (= input "A")
             (setState #(assoc % :filter :active :selected-index 0))
             
             (= input "O")
             (setState #(assoc % :filter :overdue :selected-index 0))
             
             (= input "C")
             (setState #(assoc % :filter :completed :selected-index 0))
             
             (.-upArrow key)
             (setState #(update % :selected-index (fn [i] (max 0 (dec i)))))
             
             (.-downArrow key)
             (setState #(update % :selected-index (fn [i] (min (dec (count sorted-tasks)) (inc i)))))))))
      
      ;; Render UI
      ($ Box {:flexDirection "column" :padding 1}
         ;; Header
         ($ Box {:borderStyle "round" :borderColor "cyan" :paddingX 1 :marginBottom 1}
            ($ Text {:bold true :color "cyan"} " TODO LIST ")
            ($ Text {:color "gray"} "- Node.js TUI (Ink) ")
            ($ Text {:color "magenta"} (if (= view :list) "[List View]" "[Calendar View]")))
         
         ;; Status bar
         ($ Box {:marginBottom 1}
            ($ Text {:color "yellow"} (str "Filter: " (str/upper-case (name filter))))
            ($ Text {:color "gray"} " | ")
            ($ Text {:color "green"} (str "Active: " (count active-tasks)))
            ($ Text {:color "gray"} " | ")
            ($ Text {:color "red"} (str "Overdue: " (count overdue-tasks)))
            ($ Text {:color "gray"} " | ")
            ($ Text {:color "blue"} (str "Done: " (count completed-tasks))))
         
         ;; Main content - List or Calendar view
         (if (= view :calendar)
           ;; Calendar view - use injected calendar-data-fn
           (let [cal-data (calendar-data-fn task-list due-date-status)]
             ($ Box {:flexDirection "column" :paddingY 1}
                ;; Overdue section
                (when (seq (:overdue cal-data))
                  ($ Box {:flexDirection "column" :marginBottom 1}
                     ($ Text {:color "red" :bold true} "!! OVERDUE !!")
                     (for [task (:overdue cal-data)]
                       ($ Box {:key (str (:id task))}
                          ($ Text {:color "red"} 
                             (str "  - " (:title task) 
                                  (when (:due-date task) 
                                    (str " (" (format-date (:due-date task)) ")"))))))))
                
                ;; Today section
                (when (seq (:today-tasks cal-data))
                  ($ Box {:flexDirection "column" :marginBottom 1}
                     ($ Text {:color "yellow" :bold true} "TODAY")
                     (for [task (:today-tasks cal-data)]
                       ($ Box {:key (str (:id task))}
                          ($ Text {:color "yellow"} (str "  - " (:title task)))))))
                
                ;; Upcoming section
                (when (seq (:upcoming cal-data))
                  ($ Box {:flexDirection "column" :marginBottom 1}
                     ($ Text {:color "green" :bold true} "UPCOMING")
                     (for [[date tasks] (:upcoming cal-data)]
                       ($ Box {:key (str date) :flexDirection "column"}
                          ($ Text {:color "green"} (str "  " (format-date date)))
                          (for [task tasks]
                            ($ Box {:key (str (:id task))}
                               ($ Text {:color "white"} (str "    - " (:title task)))))))))
                
                ;; No date section
                (when (seq (:no-date cal-data))
                  ($ Box {:flexDirection "column"}
                     ($ Text {:color "gray" :bold true} "NO DUE DATE")
                     (for [task (:no-date cal-data)]
                       ($ Box {:key (str (:id task))}
                          ($ Text {:color "gray"} (str "  - " (:title task)))))))))
           
           ;; List view
           ($ Box {:flexDirection "column" :paddingY 1}
              (if (empty? sorted-tasks)
                ($ Box {:flexDirection "column" :paddingX 2}
                   ($ Text {:color "gray" :italic true} "No tasks to display.")
                   ($ Text {:color "gray"} "Press 'n' to add a new task."))
                (for [[idx task] (map-indexed vector sorted-tasks)]
                  (let [selected? (= idx selected-index)
                        status (due-date-status task)
                        checkbox (if (:completed task) "[X]" "[ ]")
                        due-str (when (:due-date task) 
                                  (str " (" (format-date (:due-date task)) ")"))
                        badge (status-badge status)]
                    ($ Box {:key (str (:id task))}
                       (if selected?
                         ($ Text {:backgroundColor "blue" :color "white"}
                            (str " > " checkbox " " (:title task) due-str badge " "))
                         ($ Text {:color (status-color status)} 
                            (str "   " checkbox " " (:title task) due-str badge)))))))))
         
         ;; Input or help bar
         (cond
           (= input-mode :title)
           ($ Box {:borderStyle "single" :borderColor "green" :paddingX 1 :marginTop 1}
              ($ Text {:color "green"} "Task title: ")
              ($ Text {} input-value)
              ($ Text {:color "gray"} "_"))
           
           (= input-mode :date)
           ($ Box {:borderStyle "single" :borderColor "yellow" :paddingX 1 :marginTop 1
                   :flexDirection "column"}
              ($ Text {:color "gray"} (str "Title: " pending-title))
              ($ Box {}
                 ($ Text {:color "yellow"} "Due date (YYYY-MM-DD, or Enter to skip): ")
                 ($ Text {} input-value)
                 ($ Text {:color "gray"} "_")))
           
           :else
           ($ Box {:borderStyle "single" :borderColor "gray" :paddingX 1 :marginTop 1
                   :flexDirection "column"}
              ($ Box {}
                 ($ Text {:color "cyan"} "n")
                 ($ Text {:color "gray"} ":new  ")
                 ($ Text {:color "cyan"} "t")
                 ($ Text {:color "gray"} ":toggle  ")
                 ($ Text {:color "cyan"} "d")
                 ($ Text {:color "gray"} ":delete  ")
                 ($ Text {:color "cyan"} "c")
                 ($ Text {:color "gray"} ":clear  ")
                 ($ Text {:color "cyan"} "v")
                 ($ Text {:color "gray"} ":view"))
              ($ Box {}
                 ($ Text {:color "cyan"} "a")
                 ($ Text {:color "gray"} ":all  ")
                 ($ Text {:color "cyan"} "A")
                 ($ Text {:color "gray"} ":active  ")
                 ($ Text {:color "cyan"} "O")
                 ($ Text {:color "gray"} ":overdue  ")
                 ($ Text {:color "cyan"} "C")
                 ($ Text {:color "gray"} ":completed  ")
                 ($ Text {:color "cyan"} "q")
                 ($ Text {:color "gray"} ":quit  ")
                 ($ Text {:color "cyan"} "up/dn")
                 ($ Text {:color "gray"} ":nav"))))))))

;; =============================================================================
;; TUI Starter Factory
;; =============================================================================

(defn make-start-tui
  "Factory: creates start-tui function with all dependencies injected."
  [TodoApp]
  (fn []
    ;; Check for TTY
    (if (not (.-isTTY js/process.stdin))
      (do
        (println "ERROR: Terminal UI requires an interactive terminal (TTY).")
        (println "Please run directly from a terminal:")
        (println "  cd examples/node-tui")
        (println "  npx nbb -cp \"src:deps\" src/todo_node_tui/main.cljs")
        (js/process.exit 1))
      
      ;; Render the app
      (do
        (render ($ TodoApp {}))
        (println "Application started.")))))

;; =============================================================================
;; Boot Function
;; =============================================================================

(defn boot-fn
  "Boot function - receives start-tui (injected) and container (from plin.boot)."
  [start-tui _container]
  (start-tui))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo-node-tui/terminal
    :doc "Terminal UI with fully injected dependencies. Demonstrates proper DI architecture with React/Ink."
    :deps []

    :beans
    {;; === TodoApp Component ===
     ;; Factory creates the React component with all dependencies closed over
     ::todo-app
     ^{:doc "React component for the Todo app. All dependencies are closed over."}
     [make-todo-app
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      :todo.plugins.domain/get-all-tasks
      :todo.plugins.domain/get-active-tasks
      :todo.plugins.domain/get-completed-tasks
      :todo.plugins.domain/get-overdue-tasks
      :todo.plugins.domain/sort-by-urgency
      :todo.plugins.domain/add-task
      :todo.plugins.domain/toggle-task
      :todo.plugins.domain/delete-task
      :todo.plugins.domain/clear-completed
      :todo.plugins.domain/parse-date
      :todo.plugins.domain/format-date
      ::deadlines/due-date-status
      ::calendar/calendar-data]

     ;; === TUI Starter ===
     ::start-tui
     ^{:doc "Function to start the TUI."}
     [make-start-tui ::todo-app]

     ;; === Boot Function ===
     :plin.boot/boot-fn
     ^{:doc "Boot function that starts the Ink terminal UI."}
     [partial boot-fn ::start-tui]}}))
