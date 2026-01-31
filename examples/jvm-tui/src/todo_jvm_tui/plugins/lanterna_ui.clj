(ns todo-jvm-tui.plugins.lanterna-ui
  "Terminal UI plugin using Lanterna for the JVM.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - NO direct requires of domain namespace
   - ALL functionality comes through injected beans
   - Each handler receives exactly the dependencies it needs
   - The boot function receives pre-wired functions

   Provides a text-based user interface in the terminal:
   - List of tasks with checkboxes
   - Add new tasks
   - Toggle task completion
   - Delete tasks
   - Filter by All/Active/Completed
   - Keyboard navigation
   
   Dependencies:
   - :todo/persistence - for load-fn, store-fn
   - :todo/domain - for task operations (add, toggle, delete, etc.)"
  (:require [clojure.string :as str]
            [plin.core :as pi])
  (:import [com.googlecode.lanterna SGR]
           [com.googlecode.lanterna.screen TerminalScreen]
           [com.googlecode.lanterna.terminal DefaultTerminalFactory]
           [com.googlecode.lanterna.input KeyType]))

;; =============================================================================
;; State
;; =============================================================================

(defonce screen-ref (atom nil))
(defonce terminal-ref (atom nil))
(defonce app-state (atom {:filter :all
                          :selected-index 0
                          :input-mode false
                          :new-task-input ""}))

;; =============================================================================
;; Pure Utility Functions (no dependencies needed)
;; =============================================================================

(defn create-terminal
  "Create a text-mode terminal, preventing Swing fallback."
  []
  (-> (DefaultTerminalFactory.)
      (.setForceTextTerminal true)
      (.createTerminal)))

;; =============================================================================
;; UI Rendering (dependencies passed as arguments)
;; =============================================================================

(defn draw-task-list-impl
  "Renders the task list on screen.
   Dependencies: get-active-tasks, get-completed-tasks, get-all-tasks
   Data: screen, task-list, app-state-val"
  [get-active-tasks get-completed-tasks get-all-tasks screen task-list app-state-val]
  (let [tasks (case (:filter app-state-val)
                :active (get-active-tasks task-list)
                :completed (get-completed-tasks task-list)
                (get-all-tasks task-list))
        {:keys [selected-index input-mode new-task-input]} app-state-val
        width (.getColumns (.getTerminalSize screen))
        height (.getRows (.getTerminalSize screen))
        graphics (.newTextGraphics screen)]

    ;; Clear screen
    (.clear screen)

    ;; Title bar with background
    (.enableModifiers graphics (into-array SGR [SGR/BOLD SGR/REVERSE]))
    (.putString graphics 2 0 " TODO LIST - Lanterna TUI ")
    (.clearModifiers graphics)

    ;; Stats and filter
    (.putString graphics 2 1 (str "Filter: " (str/upper-case (name (:filter app-state-val)))
                                  " | Tasks: " (count tasks)
                                  " | [a]ll [A]ctive [C]ompleted [q]uit"))

    ;; Separator line
    (.putString graphics 0 2 (apply str (repeat width "─")))

    ;; Task list
    (if (empty? tasks)
      (do
        (.putString graphics 4 4 "No tasks to display.")
        (.putString graphics 4 5 "Press 'n' to add a new task."))

      (doseq [[idx task] (map-indexed vector tasks)]
        (let [y (+ 4 idx)
              selected? (= idx selected-index)
              checkbox (if (:completed task) "[X]" "[ ]")
              title (:title task)
              line (str checkbox " " title)]
          (when (< y (- height 5))
            (if selected?
              (do
                (.enableModifiers graphics (into-array SGR [SGR/REVERSE]))
                (.putString graphics 4 y (str " " line " "))
                (.clearModifiers graphics))
              (.putString graphics 4 y line))))))

    ;; Separator
    (let [list-end (+ 4 (count tasks) 1)]
      (when (< list-end (- height 4))
        (.putString graphics 0 list-end (apply str (repeat width "─")))))

    ;; Help text
    (let [help-row (- height 4)]
      (.putString graphics 2 help-row "Controls:")
      (.putString graphics 4 (inc help-row) "n:new  t:toggle  d:delete  c:clear-completed  up/down:navigate"))

    ;; Input prompt if in input mode
    (when input-mode
      (.putString graphics 2 (- height 2) (str "New task: " (or new-task-input ""))))

    ;; Refresh screen
    (.refresh screen)))

;; =============================================================================
;; Input Handling (dependencies passed as arguments)
;; =============================================================================

(defn handle-input-impl
  "Handles keyboard input.
   Dependencies: get-active-tasks, get-completed-tasks, get-all-tasks,
                 add-task, toggle-task, delete-task, clear-completed
   Data: screen, task-list, store-fn, app-state-val"
  [get-active-tasks get-completed-tasks get-all-tasks
   add-task toggle-task delete-task clear-completed
   screen task-list store-fn app-state-val]
  (let [key (.readInput screen)
        key-type (.getKeyType key)
        tasks (case (:filter app-state-val)
                :active (get-active-tasks task-list)
                :completed (get-completed-tasks task-list)
                (get-all-tasks task-list))
        task-count (count tasks)]

    (cond
      ;; Quit
      (or (= key-type KeyType/Escape)
          (and (= key-type KeyType/Character) (= (.getCharacter key) \q)))
      :quit

      ;; Input mode - handle text input
      (:input-mode app-state-val)
      (cond
        (= key-type KeyType/Enter)
        (let [title (str/trim (:new-task-input app-state-val))]
          (when (seq title)
            (let [new-task-list (add-task task-list title)]
              (store-fn new-task-list)))
          (swap! app-state assoc :input-mode false :new-task-input "")
          :reload)

        (= key-type KeyType/Backspace)
        (do
          (swap! app-state update :new-task-input
                 #(subs % 0 (max 0 (dec (count %)))))
          :continue)

        (= key-type KeyType/Character)
        (do
          (swap! app-state update :new-task-input str (.getCharacter key))
          :continue)

        :else :continue)

      ;; Navigation mode
      :else
      (cond
        ;; New task
        (and (= key-type KeyType/Character) (= (.getCharacter key) \n))
        (do
          (swap! app-state assoc :input-mode true)
          :continue)

        ;; Toggle task
        (and (= key-type KeyType/Character) (= (.getCharacter key) \t))
        (when (and (> task-count 0) (< (:selected-index app-state-val) task-count))
          (let [task (nth tasks (:selected-index app-state-val))
                new-task-list (toggle-task task-list (:id task))]
            (store-fn new-task-list))
          :reload)

        ;; Delete task
        (and (= key-type KeyType/Character) (= (.getCharacter key) \d))
        (when (and (> task-count 0) (< (:selected-index app-state-val) task-count))
          (let [task (nth tasks (:selected-index app-state-val))
                new-task-list (delete-task task-list (:id task))]
            (store-fn new-task-list)
            (swap! app-state update :selected-index #(min % (dec task-count))))
          :reload)

        ;; Clear completed
        (and (= key-type KeyType/Character) (= (.getCharacter key) \c))
        (let [new-task-list (clear-completed task-list)]
          (store-fn new-task-list)
          (swap! app-state assoc :selected-index 0)
          :reload)

        ;; Filter: All
        (and (= key-type KeyType/Character) (= (.getCharacter key) \a))
        (do
          (swap! app-state assoc :filter :all :selected-index 0)
          :reload)

        ;; Filter: Active
        (and (= key-type KeyType/Character) (= (.getCharacter key) \A))
        (do
          (swap! app-state assoc :filter :active :selected-index 0)
          :reload)

        ;; Filter: Completed
        (and (= key-type KeyType/Character) (= (.getCharacter key) \C))
        (do
          (swap! app-state assoc :filter :completed :selected-index 0)
          :reload)

        ;; Move up
        (= key-type KeyType/ArrowUp)
        (do
          (swap! app-state update :selected-index
                 #(max 0 (dec %)))
          :continue)

        ;; Move down
        (= key-type KeyType/ArrowDown)
        (do
          (swap! app-state update :selected-index
                 #(min (dec task-count) (inc %)))
          :continue)

        :else :continue))))

;; =============================================================================
;; TUI Main Loop Factory
;; =============================================================================

(defn make-start-tui
  "Factory: creates start-tui function with all dependencies injected."
  [load-fn store-fn draw-task-list handle-input]
  (fn []
    ;; Check if we're in a proper terminal
    (when-not (System/console)
      (println "ERROR: Terminal UI requires an interactive terminal.")
      (println "Please run directly from a terminal:")
      (println "  cd examples/jvm-tui")
      (println "  clojure -M:run")
      (System/exit 1))
    
    (let [terminal (create-terminal)
          screen (TerminalScreen. terminal)]

      (reset! terminal-ref terminal)
      (reset! screen-ref screen)

      ;; Start the screen
      (.startScreen screen)

      ;; Main loop
      (try
        (loop []
          (let [task-list (load-fn)
                state @app-state]
            (draw-task-list screen task-list state)
            (case (handle-input screen task-list store-fn state)
              :quit nil
              :reload (recur)
              :continue (recur))))

        (finally
          (.stopScreen screen)
          (reset! terminal-ref nil)
          (reset! screen-ref nil)
          (println "Goodbye!"))))))

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
   {:id :todo-jvm-tui/lanterna
    :doc "Terminal UI with fully injected dependencies. Demonstrates proper DI architecture."
    :deps []

    :beans
    {;; === UI Rendering ===
     ;; Partially applied with domain query functions
     ::draw-task-list
     ^{:doc "Function to draw the task list. Pre-injected with query deps."}
     [partial draw-task-list-impl
      :todo.plugins.domain/get-active-tasks
      :todo.plugins.domain/get-completed-tasks
      :todo.plugins.domain/get-all-tasks]

     ;; === Input Handling ===
     ;; Partially applied with domain query and mutation functions
     ::handle-input
     ^{:doc "Function to handle keyboard input. Pre-injected with domain ops."}
     [partial handle-input-impl
      :todo.plugins.domain/get-active-tasks
      :todo.plugins.domain/get-completed-tasks
      :todo.plugins.domain/get-all-tasks
      :todo.plugins.domain/add-task
      :todo.plugins.domain/toggle-task
      :todo.plugins.domain/delete-task
      :todo.plugins.domain/clear-completed]

     ;; === TUI Main Loop ===
     ;; Created with all dependencies
     ::start-tui
     ^{:doc "Function to start the TUI main loop."}
     [make-start-tui
      :todo.plugins.persistence/load-fn
      :todo.plugins.persistence/store-fn
      ::draw-task-list
      ::handle-input]

     ;; === Boot Function ===
     ;; Receives only start-tui - exactly what it needs
     :plin.boot/boot-fn
     ^{:doc "Boot function that starts the Lanterna TUI."}
     [partial boot-fn ::start-tui]}}))
