(ns todo.domain
  "Core domain entities and operations for the Todo application.
   
   This namespace defines the business logic that is shared across all platform examples.
   It contains no platform-specific code.
   
   Domain Concepts:
   - Task: A unit of work with title, completion status, due date, and timestamps
   - TaskList: A collection of tasks with operations to manipulate them")

;; Domain entities

(defrecord Task [id title completed created-at due-date])

(defrecord TaskList [tasks next-id])

;; Domain operations

(defn create-task-list
  "Creates a new empty task list."
  []
  (->TaskList {} 1))

(defn add-task
  "Adds a new task to the task list.
   
   Arguments:
   - task-list: The current TaskList
   - title: String title for the new task
   - due-date: (Optional) Due date for the task. On JVM: java.time.LocalDate or nil.
               On JS: Date object or nil.
   
   Returns updated TaskList with new task added."
  ([task-list title]
   (add-task task-list title nil))
  ([task-list title due-date]
   (let [id (:next-id task-list)
         task (map->Task {:id id
                          :title title
                          :completed false
                          :created-at #?(:clj (java.time.Instant/now)
                                         :cljs (js/Date.))
                          :due-date due-date})]
     (-> task-list
         (assoc-in [:tasks id] task)
         (update :next-id inc)))))

(defn complete-task
  "Marks a task as completed.
   
   Arguments:
   - task-list: The current TaskList
   - task-id: ID of the task to complete
   
   Returns updated TaskList."
  [task-list task-id]
  (assoc-in task-list [:tasks task-id :completed] true))

(defn uncomplete-task
  "Marks a task as not completed.
   
   Arguments:
   - task-list: The current TaskList
   - task-id: ID of the task to uncomplete
   
   Returns updated TaskList."
  [task-list task-id]
  (assoc-in task-list [:tasks task-id :completed] false))

(defn toggle-task
  "Toggles the completion status of a task.
   
   Arguments:
   - task-list: The current TaskList
   - task-id: ID of the task to toggle
   
   Returns updated TaskList."
  [task-list task-id]
  (update-in task-list [:tasks task-id :completed] not))

(defn delete-task
  "Removes a task from the task list.
   
   Arguments:
   - task-list: The current TaskList
   - task-id: ID of the task to delete
   
   Returns updated TaskList."
  [task-list task-id]
  (update task-list :tasks dissoc task-id))

(defn get-task
  "Retrieves a single task by ID.
   
   Arguments:
   - task-list: The current TaskList
   - task-id: ID of the task to retrieve
   
   Returns the Task or nil if not found."
  [task-list task-id]
  (get-in task-list [:tasks task-id]))

(defn get-all-tasks
  "Returns all tasks as a vector, sorted by creation time.
   
   Arguments:
   - task-list: The current TaskList
   
   Returns vector of Tasks."
  [task-list]
  (->> (:tasks task-list)
       vals
       (sort-by :created-at)))

(defn get-active-tasks
  "Returns all incomplete tasks.
   
   Arguments:
   - task-list: The current TaskList
   
   Returns vector of incomplete Tasks."
  [task-list]
  (->> (get-all-tasks task-list)
       (remove :completed)))

(defn get-completed-tasks
  "Returns all completed tasks.
   
   Arguments:
   - task-list: The current TaskList
   
   Returns vector of completed Tasks."
  [task-list]
  (->> (get-all-tasks task-list)
       (filter :completed)))

(defn clear-completed
  "Removes all completed tasks from the list.
   
   Arguments:
   - task-list: The current TaskList
   
   Returns updated TaskList."
  [task-list]
  (update task-list :tasks #(into {} (remove (fn [[_ task]] (:completed task))) %)))

(defn set-due-date
  "Sets or updates the due date for a task.
   
   Arguments:
   - task-list: The current TaskList
   - task-id: ID of the task to update
   - due-date: The due date (LocalDate on JVM, Date on JS, or nil to clear)
   
   Returns updated TaskList."
  [task-list task-id due-date]
  (assoc-in task-list [:tasks task-id :due-date] due-date))

;; --- Date Utilities ---
;; Cross-platform date handling for deadline calculations

(defn today
  "Returns today's date.
   On JVM: java.time.LocalDate
   On JS: Date object (with time set to midnight)"
  []
  #?(:clj (java.time.LocalDate/now)
     :cljs (let [d (js/Date.)]
             (js/Date. (.getFullYear d) (.getMonth d) (.getDate d)))))

(defn parse-date
  "Parses a date string in YYYY-MM-DD format.
   Returns nil if the string is empty or invalid."
  [date-str]
  (when (and date-str (not= date-str ""))
    #?(:clj (try
              (java.time.LocalDate/parse date-str)
              (catch Exception _ nil))
       :cljs (let [d (js/Date. date-str)]
               (when-not (js/isNaN (.getTime d))
                 d)))))

(defn format-date
  "Formats a date as YYYY-MM-DD string.
   Returns empty string if date is nil."
  [date]
  (if date
    #?(:clj (.toString date)
       :cljs (let [y (.getFullYear date)
                   m (inc (.getMonth date))
                   d (.getDate date)]
               (str y "-" (when (< m 10) "0") m "-" (when (< d 10) "0") d)))
    ""))

(defn days-until
  "Calculates the number of days from today until the given date.
   Returns nil if date is nil.
   Negative values indicate past dates (overdue)."
  [date]
  (when date
    #?(:clj (let [today (java.time.LocalDate/now)]
              (.until today date java.time.temporal.ChronoUnit/DAYS))
       :cljs (let [today-ms (.getTime (today))
                   date-ms (.getTime date)
                   diff-ms (- date-ms today-ms)]
               (js/Math.floor (/ diff-ms 86400000))))))

(defn overdue?
  "Returns true if the task is overdue (has a due date in the past and is not completed)."
  [task]
  (and (:due-date task)
       (not (:completed task))
       (let [days (days-until (:due-date task))]
         (and days (neg? days)))))

(defn due-today?
  "Returns true if the task is due today."
  [task]
  (and (:due-date task)
       (let [days (days-until (:due-date task))]
         (and days (zero? days)))))

(defn get-overdue-tasks
  "Returns all overdue tasks (past due date and not completed).
   
   Arguments:
   - task-list: The current TaskList
   
   Returns vector of overdue Tasks."
  [task-list]
  (->> (get-active-tasks task-list)
       (filter overdue?)))

(defn get-tasks-due-today
  "Returns all tasks due today.
   
   Arguments:
   - task-list: The current TaskList
   
   Returns vector of Tasks due today."
  [task-list]
  (->> (get-all-tasks task-list)
       (filter due-today?)))

(defn get-tasks-with-due-date
  "Returns all tasks that have a due date set, sorted by due date.
   
   Arguments:
   - task-list: The current TaskList
   
   Returns vector of Tasks with due dates."
  [task-list]
  (->> (get-all-tasks task-list)
       (filter :due-date)
       (sort-by :due-date)))

(defn sort-by-urgency
  "Sorts tasks by urgency (overdue first, then by due date, then no-due-date last).
   
   Arguments:
   - tasks: A sequence of tasks
   
   Returns sorted vector of Tasks."
  [tasks]
  (let [with-due (filter :due-date tasks)
        without-due (remove :due-date tasks)]
    (concat (sort-by #(days-until (:due-date %)) with-due)
            without-due)))

(defn group-tasks-by-date
  "Groups tasks by their due date.
   
   Arguments:
   - tasks: A sequence of tasks
   
   Returns a map of {date -> [tasks]} sorted by date.
   Tasks without due dates are grouped under nil."
  [tasks]
  (->> tasks
       (group-by :due-date)
       (sort-by (fn [[date _]] 
                  (if date 
                    #?(:clj (.toEpochDay date)
                       :cljs (.getTime date))
                    #?(:clj Long/MAX_VALUE
                       :cljs js/Number.MAX_SAFE_INTEGER))))
       (into {})))
