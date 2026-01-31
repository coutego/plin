(ns todo-jvm-tui.plugins.lanterna-ui
  "Terminal UI plugin using Lanterna for the JVM.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION with short functions.
   
   Provides an enhanced text-based user interface with:
   - List of tasks with checkboxes and due dates
   - Add new tasks with optional due dates
   - Toggle task completion
   - Delete tasks
   - Filter by All/Active/Overdue/Completed
   - Calendar/agenda view
   - Keyboard navigation
   - Color-coded due date status indicators"
  (:require [clojure.string :as str]
            [plin.core :as pi])
  (:import [com.googlecode.lanterna SGR TextColor TextColor$ANSI]
           [com.googlecode.lanterna.screen TerminalScreen]
           [com.googlecode.lanterna.terminal DefaultTerminalFactory]
           [com.googlecode.lanterna.input KeyType]))

;; =============================================================================
;; State
;; =============================================================================

(defonce screen-ref (atom nil))
(defonce terminal-ref (atom nil))
(defonce app-state (atom {:filter :all
                          :view :list
                          :selected-index 0
                          :input-mode nil
                          :input-value ""
                          :pending-title ""}))

;; =============================================================================
;; Color Helpers
;; =============================================================================

(defn status-color [status]
  (case status
    :overdue TextColor$ANSI/RED
    :due-today TextColor$ANSI/YELLOW
    :upcoming TextColor$ANSI/GREEN
     :completed TextColor$ANSI/WHITE
    TextColor$ANSI/WHITE))

(defn muted-color []
  TextColor$ANSI/BLUE)

;; =============================================================================
;; UI Drawing - Low Level
;; =============================================================================

(defn draw-separator [graphics y width]
  (.setForegroundColor graphics (muted-color))
  (.putString graphics 0 y (apply str (repeat width "─"))))

(defn draw-text [graphics x y color text]
  (.setForegroundColor graphics color)
  (.putString graphics x y text))

(defn draw-bold-text [graphics x y color text]
  (.setForegroundColor graphics color)
  (.enableModifiers graphics (into-array SGR [SGR/BOLD]))
  (.putString graphics x y text)
  (.clearModifiers graphics))

(defn draw-highlighted [graphics x y text]
  (.enableModifiers graphics (into-array SGR [SGR/REVERSE]))
  (.putString graphics x y text)
  (.clearModifiers graphics))

;; =============================================================================
;; Header Drawing
;; =============================================================================

(defn draw-title [graphics width view]
  (.setForegroundColor graphics TextColor$ANSI/CYAN)
  (.enableModifiers graphics (into-array SGR [SGR/BOLD]))
  (.putString graphics 2 0 " TODO LIST - JVM TUI (Lanterna) ")
  (.clearModifiers graphics)
  (.setForegroundColor graphics TextColor$ANSI/MAGENTA)
  (.putString graphics (- width 20) 0 (if (= view :list) "[List View] " "[Calendar] ")))

(defn draw-filter-indicator [graphics filter-type]
  (draw-text graphics 2 1 TextColor$ANSI/YELLOW (str "Filter: " (str/upper-case (name filter-type)))))

(defn draw-status-count [graphics x y color label count]
  (draw-text graphics x y color (str label ": " count)))

(defn draw-status-bar [graphics width y active-count overdue-count completed-count]
  (draw-text graphics 2 y (muted-color) "| ")
  (draw-status-count graphics (+ 4 0) y TextColor$ANSI/GREEN "Active" active-count)
  (draw-text graphics (+ 4 12) y (muted-color) " | ")
  (draw-status-count graphics (+ 4 16) y TextColor$ANSI/RED "Overdue" overdue-count)
  (draw-text graphics (+ 4 29) y (muted-color) " | ")
  (draw-status-count graphics (+ 4 33) y (muted-color) "Done" completed-count))

;; =============================================================================
;; Task Drawing
;; =============================================================================

(defn format-task-line [task due-date-status format-date]
  (let [status (due-date-status task)
        checkbox (if (:completed task) "[X]" "[ ]")
        due-str (when (:due-date task)
                  (str " (" (format-date (:due-date task)) ")"))
        badge (case status
                :overdue " [OVERDUE]"
                :due-today " [TODAY]"
                "")]
    (str checkbox " " (:title task) due-str badge)))

(defn draw-task [graphics y task selected? due-date-status format-date]
  (let [line (format-task-line task due-date-status format-date)
        status (due-date-status task)
        color (status-color status)]
    (if selected?
      (draw-highlighted graphics 4 y (str " " line " "))
      (draw-text graphics 4 y color line))))

(defn draw-task-list [graphics tasks selected-index due-date-status format-date start-y height]
  (doseq [[idx task] (map-indexed vector tasks)]
    (let [y (+ start-y idx)]
      (when (< y (- height 6))
        (draw-task graphics y task (= idx selected-index) due-date-status format-date)))))

(defn draw-empty-message [graphics]
  (draw-text graphics 4 6 (muted-color) "No tasks to display.")
  (draw-text graphics 4 7 (muted-color) "Press 'n' to add a new task."))

;; =============================================================================
;; Calendar Drawing
;; =============================================================================

(defn draw-calendar-section-header [graphics y color title]
  (draw-bold-text graphics 2 y color title))

(defn draw-calendar-task [graphics y color task format-date]
  (let [due-str (when (:due-date task)
                  (str " (" (format-date (:due-date task)) ")"))]
    (draw-text graphics 4 y color (str "  - " (:title task) due-str))))

(defn draw-overdue-section [graphics y tasks format-date max-y]
  (if (seq tasks)
    (do
      (draw-calendar-section-header graphics y TextColor$ANSI/RED "!! OVERDUE !!")
      (doseq [[idx task] (map-indexed vector tasks)]
        (let [task-y (+ y 1 idx)]
          (when (< task-y max-y)
            (draw-calendar-task graphics task-y TextColor$ANSI/RED task format-date))))
      (+ y 1 (count tasks)))
    y))

(defn draw-today-section [graphics y tasks max-y]
  (if (seq tasks)
    (do
      (draw-calendar-section-header graphics y TextColor$ANSI/YELLOW "TODAY")
      (doseq [[idx task] (map-indexed vector tasks)]
        (let [task-y (+ y 1 idx)]
          (when (< task-y max-y)
            (draw-calendar-task graphics task-y TextColor$ANSI/YELLOW task nil))))
      (+ y 1 (count tasks)))
    y))

(defn draw-upcoming-date [graphics y date tasks format-date max-y]
  (if (< y max-y)
    (do
      (draw-text graphics 4 y TextColor$ANSI/GREEN (str "  " (format-date date)))
      (loop [task-y (+ y 1)
             remaining tasks]
        (when (and (seq remaining) (< task-y max-y))
          (draw-text graphics 6 task-y TextColor$ANSI/WHITE (str "- " (:title (first remaining))))
          (recur (+ task-y 1) (rest remaining))))
      (+ y 1 (count tasks)))
    y))

(defn draw-upcoming-section [graphics y upcoming format-date max-y]
  (if (seq upcoming)
    (do
      (draw-calendar-section-header graphics y TextColor$ANSI/GREEN "UPCOMING")
      (loop [current-y (+ y 1)
             dates upcoming]
        (if (and (seq dates) (< current-y max-y))
          (let [[date tasks] (first dates)
                next-y (draw-upcoming-date graphics current-y date tasks format-date max-y)]
            (recur next-y (rest dates)))
          current-y)))
    y))

(defn draw-no-date-section [graphics y tasks max-y]
  (if (seq tasks)
    (do
      (draw-calendar-section-header graphics y (muted-color) "NO DUE DATE")
      (doseq [[idx task] (map-indexed vector tasks)]
        (let [task-y (+ y 1 idx)]
          (when (< task-y max-y)
            (draw-calendar-task graphics task-y TextColor$ANSI/WHITE task nil))))
      (+ y 1 (count tasks)))
    y))

;; =============================================================================
;; Help Drawing
;; =============================================================================

(defn draw-key-hint [graphics x y key label]
  (draw-text graphics x y TextColor$ANSI/CYAN key)
  (draw-text graphics (+ x 1) y TextColor$ANSI/WHITE label))

(defn draw-help-line [graphics y hints]
  (loop [x 2
         remaining hints]
    (when (seq remaining)
      (let [[key label] (first remaining)]
        (draw-key-hint graphics x y key label)
        (recur (+ x (count key) (count label) 2) (rest remaining))))))

;; =============================================================================
;; Main Drawing Functions
;; =============================================================================

(defn draw-list-view [get-active-tasks get-completed-tasks get-all-tasks get-overdue-tasks
                      due-date-status sort-by-urgency format-date
                      graphics screen task-list app-state-val]
  (let [width (.getColumns (.getTerminalSize screen))
        height (.getRows (.getTerminalSize screen))
        filter (:filter app-state-val)
        tasks (case filter
                :active (get-active-tasks task-list)
                :completed (get-completed-tasks task-list)
                :overdue (get-overdue-tasks task-list)
                (get-all-tasks task-list))
        sorted-tasks (sort-by-urgency tasks)]
    (draw-title graphics width (:view app-state-val))
    (draw-filter-indicator graphics filter)
    (draw-separator graphics 2 width)
    (draw-status-bar graphics width 3
                     (count (get-active-tasks task-list))
                     (count (get-overdue-tasks task-list))
                     (count (get-completed-tasks task-list)))
    (draw-separator graphics 4 width)
    (if (empty? sorted-tasks)
      (draw-empty-message graphics)
      (draw-task-list graphics sorted-tasks (:selected-index app-state-val)
                      due-date-status format-date 6 height))
    (draw-separator graphics (- height 5) width)))

(defn draw-calendar-view [calendar-data due-date-status format-date
                          graphics screen task-list app-state-val]
  (let [width (.getColumns (.getTerminalSize screen))
        height (.getRows (.getTerminalSize screen))
        cal-data (calendar-data task-list due-date-status)
        {:keys [overdue today-tasks upcoming no-date]} cal-data
        start-y 4
        max-y (- height 4)]
    (draw-title graphics width (:view app-state-val))
    (draw-filter-indicator graphics (:filter app-state-val))
    (let [after-overdue (draw-overdue-section graphics start-y overdue format-date max-y)
          after-today (draw-today-section graphics after-overdue today-tasks max-y)
          after-upcoming (draw-upcoming-section graphics after-today upcoming format-date max-y)]
      (draw-no-date-section graphics after-upcoming no-date max-y))
    (draw-separator graphics (- height 5) width)))

(defn draw-input-prompt [graphics height input-mode input-value pending-title]
  (cond
    (= input-mode :title)
    (draw-text graphics 2 (- height 2) TextColor$ANSI/GREEN (str "Task title: " input-value "_"))
    
    (= input-mode :date)
    (do
      (draw-text graphics 2 (- height 3) TextColor$ANSI/WHITE (str "Title: " pending-title))
      (draw-text graphics 2 (- height 2) TextColor$ANSI/YELLOW (str "Due date (YYYY-MM-DD, or Enter to skip): " input-value "_")))))

(defn draw-screen-impl [draw-list-view draw-calendar-view
                        screen task-list app-state-val]
  (.clear screen)
  (let [graphics (.newTextGraphics screen)
        height (.getRows (.getTerminalSize screen))]
    (if (= (:view app-state-val) :calendar)
      (draw-calendar-view graphics screen task-list app-state-val)
      (draw-list-view graphics screen task-list app-state-val))
    (draw-help-line graphics (- height 4) [["n" ":new "] ["t" ":toggle "] ["d" ":delete "]
                                            ["c" ":clear "] ["v" ":view "] ["q" ":quit"]])
    (draw-input-prompt graphics height (:input-mode app-state-val)
                       (:input-value app-state-val) (:pending-title app-state-val))
    (.refresh screen)))

;; =============================================================================
;; Input Handling
;; =============================================================================

(defn handle-title-input [key key-type app-state-val]
  (cond
    (= key-type KeyType/Enter)
    (let [title (str/trim (:input-value app-state-val))]
      (if (seq title)
        (do (swap! app-state assoc :input-mode :date :pending-title title :input-value "")
            :reload)
        (do (swap! app-state assoc :input-mode nil :input-value "")
            :reload)))
    
    (= key-type KeyType/Backspace)
    (do (swap! app-state update :input-value #(subs % 0 (max 0 (dec (count %)))))
        :continue)
    
    (= key-type KeyType/Character)
    (do (swap! app-state update :input-value str (.getCharacter key))
        :continue)
    
    :else :continue))

(defn handle-date-input [key key-type app-state-val store-fn task-list add-task parse-date]
  (cond
    (= key-type KeyType/Enter)
    (let [title (:pending-title app-state-val)
          date-str (str/trim (:input-value app-state-val))
          due-date (when (seq date-str) (parse-date date-str))
          new-list (add-task task-list title due-date)]
      (store-fn new-list)
      (swap! app-state assoc :input-mode nil :input-value "" :pending-title "")
      :reload)
    
    (= key-type KeyType/Backspace)
    (do (swap! app-state update :input-value #(subs % 0 (max 0 (dec (count %)))))
        :continue)
    
    (= key-type KeyType/Character)
    (do (swap! app-state update :input-value str (.getCharacter key))
        :continue)
    
    :else :continue))

(defn get-visible-tasks [task-list filter-type get-active get-completed get-overdue get-all]
  (case filter-type
    :active (get-active task-list)
    :completed (get-completed task-list)
    :overdue (get-overdue task-list)
    (get-all task-list)))

(defn handle-task-toggle [task-list store-fn toggle-task selected-index tasks]
  (when (and (> (count tasks) 0) (< selected-index (count tasks)))
    (let [task (nth tasks selected-index)
          new-list (toggle-task task-list (:id task))]
      (store-fn new-list)))
  :reload)

(defn handle-task-delete [task-list store-fn delete-task selected-index tasks]
  (when (and (> (count tasks) 0) (< selected-index (count tasks)))
    (let [task (nth tasks selected-index)
          new-list (delete-task task-list (:id task))]
      (store-fn new-list)
      (swap! app-state update :selected-index #(min % (dec (count tasks))))))
  :reload)

(defn handle-clear-completed [task-list store-fn clear-completed]
  (let [new-list (clear-completed task-list)]
    (store-fn new-list)
    (swap! app-state assoc :selected-index 0))
  :reload)

(defn handle-navigation [direction task-count]
  (swap! app-state update :selected-index
         #(case direction
            :up (max 0 (dec %))
            :down (min (dec task-count) (inc %))))
  :continue)

(defn handle-filter-change [new-filter]
  (swap! app-state assoc :filter new-filter :selected-index 0)
  :reload)

(defn handle-view-toggle []
  (swap! app-state update :view #(if (= % :list) :calendar :list))
  :reload)

(defn handle-input-impl [get-active-tasks get-completed-tasks get-all-tasks get-overdue-tasks
                         add-task toggle-task delete-task clear-completed parse-date
                         screen task-list store-fn app-state-val]
  (let [key (.readInput screen)
        key-type (.getKeyType key)
        filter (:filter app-state-val)
        tasks (get-visible-tasks task-list filter
                                 get-active-tasks get-completed-tasks get-overdue-tasks get-all-tasks)]
    (cond
      ;; Quit
      (or (= key-type KeyType/Escape)
          (and (= key-type KeyType/Character) (= (.getCharacter key) \q)))
      :quit
      
      ;; Input modes
      (= (:input-mode app-state-val) :title)
      (handle-title-input key key-type app-state-val)
      
      (= (:input-mode app-state-val) :date)
      (handle-date-input key key-type app-state-val store-fn task-list add-task parse-date)
      
      ;; Normal mode
      (and (= key-type KeyType/Character) (= (.getCharacter key) \n))
      (do (swap! app-state assoc :input-mode :title :input-value "")
          :reload)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \t))
      (handle-task-toggle task-list store-fn toggle-task (:selected-index app-state-val) tasks)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \d))
      (handle-task-delete task-list store-fn delete-task (:selected-index app-state-val) tasks)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \c))
      (handle-clear-completed task-list store-fn clear-completed)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \v))
      (handle-view-toggle)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \a))
      (handle-filter-change :all)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \A))
      (handle-filter-change :active)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \O))
      (handle-filter-change :overdue)
      
      (and (= key-type KeyType/Character) (= (.getCharacter key) \C))
      (handle-filter-change :completed)
      
      (= key-type KeyType/ArrowUp)
      (handle-navigation :up (count tasks))
      
      (= key-type KeyType/ArrowDown)
      (handle-navigation :down (count tasks))
      
      :else :continue)))

;; =============================================================================
;; TUI Main Loop
;; =============================================================================

(defn create-terminal []
  (-> (DefaultTerminalFactory.)
      (.setForceTextTerminal true)
      (.createTerminal)))

(defn check-terminal []
  (when-not (System/console)
    (println "ERROR: Terminal UI requires an interactive terminal.")
    (println "Please run directly from a terminal:")
    (println "  cd examples/jvm-tui")
    (println "  clojure -M:run")
    (System/exit 1)))

(defn make-start-tui [load-fn store-fn draw-screen handle-input]
  (fn []
    (check-terminal)
    (let [terminal (create-terminal)
          screen (TerminalScreen. terminal)]
      (reset! terminal-ref terminal)
      (reset! screen-ref screen)
      (.startScreen screen)
      (try
        (loop []
          (let [task-list (load-fn)
                state @app-state]
            (draw-screen screen task-list state)
            (case (handle-input screen task-list store-fn state)
              :quit nil
              :reload (recur)
              :continue (recur))))
        (finally
          (.stopScreen screen)
          (reset! terminal-ref nil)
          (reset! screen-ref nil)
          (println "Goodbye!"))))))

(defn boot-fn [start-tui _container]
  (start-tui))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (pi/plugin
   {:id :todo-jvm-tui/lanterna
    :doc "Terminal UI with short functions and full DI."
    :deps []
    :beans
    {::draw-list-view
     [partial draw-list-view
      :todo.plugins.domain/get-active-tasks
      :todo.plugins.domain/get-completed-tasks
      :todo.plugins.domain/get-all-tasks
      :todo.plugins.domain/get-overdue-tasks
      :todo.plugins.deadlines/due-date-status
      :todo.plugins.domain/sort-by-urgency
      :todo.plugins.domain/format-date]
     
     ::draw-calendar-view
     [partial draw-calendar-view
      :todo.plugins.calendar/calendar-data
      :todo.plugins.deadlines/due-date-status
      :todo.plugins.domain/format-date]
     
     ::draw-screen
     [partial draw-screen-impl
      ::draw-list-view
      ::draw-calendar-view]
     
     ::handle-input
     [partial handle-input-impl
      :todo.plugins.domain/get-active-tasks
      :todo.plugins.domain/get-completed-tasks
      :todo.plugins.domain/get-all-tasks
      :todo.plugins.domain/get-overdue-tasks
      :todo.plugins.domain/add-task
      :todo.plugins.domain/toggle-task
      :todo.plugins.domain/delete-task
      :todo.plugins.domain/clear-completed
      :todo.plugins.domain/parse-date]
     
     ::start-tui
     [make-start-tui
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      ::draw-screen
      ::handle-input]
     
     :plin.boot/boot-fn
     [partial boot-fn ::start-tui]}}))
