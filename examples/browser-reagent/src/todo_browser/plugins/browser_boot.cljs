(ns todo-browser.plugins.browser-boot
  "Browser-specific boot implementation for the Todo app.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - NO direct requires of domain, deadlines, or calendar namespaces
   - ALL functionality comes through injected beans
   - UI components receive exactly the dependencies they need via factory closure
   - The boot function receives a pre-configured mount-app function
   
   Dependencies:
   - :todo/persistence - for load-fn, store-fn
   - :todo/domain - for task operations (add, toggle, delete, etc.)
   - :todo/deadlines - for due-date-status, format-due-date
   - :todo/calendar - for calendar-data
   
   Features:
   - Task list with checkboxes and due dates
   - Add tasks with optional due dates
   - Toggle, delete, clear completed
   - Filter by All/Active/Overdue/Completed
   - Calendar/agenda view
   - Overdue task highlighting"
  (:require [plin.core :as plin]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [todo.plugins.deadlines :as deadlines]
            [todo.plugins.calendar :as calendar]))

;; =============================================================================
;; Factory: Creates the entire UI with dependencies closed over
;; =============================================================================

(defn make-app-component
  "Factory: creates the app component with all dependencies injected.
   
   Dependencies (injected):
   - load-fn, store-fn: persistence operations
   - get-all-tasks, get-active-tasks, get-completed-tasks, get-overdue-tasks: query fns
   - sort-by-urgency: sorting fn
   - add-task, toggle-task, delete-task, clear-completed, set-due-date: mutation fns
   - parse-date, format-date: date utilities
   - due-date-status: status calculation
   - calendar-data-fn: calendar data preparation"
  [load-fn store-fn
   get-all-tasks get-active-tasks get-completed-tasks get-overdue-tasks
   sort-by-urgency
   add-task toggle-task delete-task clear-completed set-due-date
   parse-date format-date
   due-date-status calendar-data-fn]
  
  (let [;; Helper to save tasks
        save-tasks! (fn [task-list] (store-fn task-list))
        
        ;; Task item component
        task-item (fn [app-state task]
                    (let [status (due-date-status task)
                          status-class (name status)
                          due-date-str (when (:due-date task) (format-date (:due-date task)))]
                      [:div.task {:class status-class}
                       [:input {:type "checkbox"
                                :checked (:completed task)
                                :on-change #(let [new-state (swap! app-state 
                                                                   update :task-list 
                                                                   toggle-task (:id task))]
                                              (save-tasks! (:task-list new-state)))}]
                       [:span.task-title (:title task)]
                       (when due-date-str
                         [:span.task-due {:class status-class} due-date-str])
                       [:input.due-input {:type "date"
                                          :value (or due-date-str "")
                                          :on-change #(let [new-date (parse-date (.. % -target -value))
                                                            new-state (swap! app-state 
                                                                             update :task-list 
                                                                             set-due-date (:id task) new-date)]
                                                        (save-tasks! (:task-list new-state)))}]
                       [:button {:on-click #(let [new-state (swap! app-state 
                                                                   update :task-list 
                                                                   delete-task (:id task))]
                                              (save-tasks! (:task-list new-state)))} 
                        "Delete"]]))
        
        ;; Task list view component
        task-list-view (fn [app-state]
                         (let [{:keys [filter task-list]} @app-state
                               tasks (case filter
                                       :active (get-active-tasks task-list)
                                       :completed (get-completed-tasks task-list)
                                       :overdue (get-overdue-tasks task-list)
                                       (get-all-tasks task-list))
                               sorted-tasks (sort-by-urgency tasks)]
                           [:div.tasks
                            (if (empty? sorted-tasks)
                              [:div.empty "No tasks to display"]
                              (for [task sorted-tasks]
                                ^{:key (:id task)} [task-item app-state task]))]))
        
        ;; Add task form component
        add-task-form (fn [app-state]
                        (let [new-task (reagent.core/atom "")
                              new-due-date (reagent.core/atom "")]
                          (fn []
                            [:form.add-form {:on-submit (fn [e]
                                                          (.preventDefault e)
                                                          (when (seq @new-task)
                                                            (let [due-date (parse-date @new-due-date)
                                                                  new-state (swap! app-state 
                                                                                   update :task-list 
                                                                                   add-task @new-task due-date)]
                                                              (save-tasks! (:task-list new-state)))
                                                            (reset! new-task "")
                                                            (reset! new-due-date "")))}
                             [:input {:type "text"
                                      :placeholder "What needs to be done?"
                                      :value @new-task
                                      :on-change #(reset! new-task (.. % -target -value))}]
                             [:input {:type "date"
                                      :value @new-due-date
                                      :on-change #(reset! new-due-date (.. % -target -value))}]
                             [:button {:type "submit"} "Add Task"]])))
        
        ;; Filter links component
        filter-links (fn [app-state]
                       (let [current-filter (:filter @app-state)]
                         [:div.filters
                          [:a {:class (when (= current-filter :all) "active")
                               :on-click #(swap! app-state assoc :filter :all)} "All"]
                          [:a {:class (when (= current-filter :active) "active")
                               :on-click #(swap! app-state assoc :filter :active)} "Active"]
                          [:a {:class (when (= current-filter :overdue) "active")
                               :on-click #(swap! app-state assoc :filter :overdue)} "Overdue"]
                          [:a {:class (when (= current-filter :completed) "active")
                               :on-click #(swap! app-state assoc :filter :completed)} "Completed"]]))
        
        ;; Navigation links component
        nav-links (fn [app-state]
                    (let [current-view (:view @app-state)]
                      [:nav.nav
                       [:a {:class (when (= current-view :list) "active")
                            :on-click #(swap! app-state assoc :view :list)} "Task List"]
                       [:a {:class (when (= current-view :calendar) "active")
                            :on-click #(swap! app-state assoc :view :calendar)} "Calendar View"]]))
        
        ;; Calendar section component
        calendar-section (fn [title section-class tasks]
                           (when (seq tasks)
                             [:div.calendar-section {:class section-class}
                              [:h3 title]
                              (for [task tasks]
                                ^{:key (:id task)}
                                [:div.calendar-task
                                 [:span.task-title (:title task)]
                                 (when (:due-date task)
                                   [:span.task-due (format-date (:due-date task))])])]))
        
        ;; Calendar view component
        calendar-view (fn [app-state]
                        (let [task-list (:task-list @app-state)
                              cal-data (calendar-data-fn task-list due-date-status)
                              {:keys [overdue today-tasks upcoming no-date stats]} cal-data]
                          [:div.calendar-view
                           [:div.calendar-stats
                            [:span.stat "Total: " [:strong (:total stats)]]
                            [:span.stat.overdue "Overdue: " [:strong (:overdue stats)]]
                            [:span.stat.due-today "Due today: " [:strong (:due-today stats)]]
                            [:span.stat "Upcoming: " [:strong (:upcoming stats)]]
                            [:span.stat "No date: " [:strong (:no-date stats)]]]
                           
                           [calendar-section "Overdue" "overdue" overdue]
                           [calendar-section "Due Today" "due-today" today-tasks]
                           
                           (when (seq upcoming)
                             [:div.calendar-section.upcoming
                              [:h3 "Upcoming"]
                              (for [[date tasks] upcoming]
                                ^{:key (str date)}
                                [:div.date-group
                                 [:h4 (format-date date)]
                                 (for [task tasks]
                                   ^{:key (:id task)}
                                   [:div.calendar-task
                                    [:span.task-title (:title task)]])])])
                           
                           [calendar-section "No Due Date" "no-date" no-date]
                           
                           (when (zero? (:total stats))
                             [:div.calendar-empty "No active tasks"])]))]
    
    ;; Return the main app component function
    (fn []
      (let [app-state (reagent.core/atom {:view :list
                               :filter :all
                               :task-list (load-fn)})]
        (fn []
          (let [task-list (:task-list @app-state)
                active-count (count (get-active-tasks task-list))
                completed-count (count (get-completed-tasks task-list))
                overdue-count (count (get-overdue-tasks task-list))
                current-view (:view @app-state)]
            [:div.app-container
             [:h1 "Todo List"]
             [nav-links app-state]
             
             (if (= current-view :calendar)
               [calendar-view app-state]
               [:<>
                [add-task-form app-state]
                [filter-links app-state]
                [task-list-view app-state]
                [:div.actions
                 [:button {:on-click #(let [new-state (swap! app-state 
                                                              (fn [s]
                                                                (-> s
                                                                    (update :task-list clear-completed)
                                                                    (assoc :filter :all))))]
                                        (save-tasks! (:task-list new-state)))} 
                  "Clear Completed"]]])
             
             [:div.stats 
              (str active-count " active, " completed-count " completed")
              (when (pos? overdue-count)
                [:span.overdue (str ", " overdue-count " overdue")])]]))))))

;; =============================================================================
;; Mount Function Factory
;; =============================================================================

(defn make-mount-app
  "Factory: creates mount-app function with injected app component."
  [app-component]
  (fn []
    (reagent.dom/render [app-component] 
                        (.getElementById js/document "app"))))

;; =============================================================================
;; Boot Function
;; =============================================================================

(defn boot-fn
  "Boot function - receives mount-app (injected) and container (from plin.boot)."
  [mount-app _container]
  (println "Booting browser Todo app...")
  (mount-app)
  (println "Todo app mounted successfully!"))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  (plin.core/plugin
   {:id :todo-browser/boot
    :doc "Browser boot with fully injected dependencies. Demonstrates proper DI with Reagent."
     :deps [deadlines/plugin calendar/plugin]
     
     :beans
     {;; === App Component ===
     ;; Factory creates the Reagent component with all dependencies closed over
     ::app-component
     ^{:doc "Reagent app component with all dependencies injected."}
     [make-app-component
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
      :todo.plugins.domain/set-due-date
      :todo.plugins.domain/parse-date
      :todo.plugins.domain/format-date
      :todo.plugins.deadlines/due-date-status
      :todo.plugins.calendar/calendar-data]
     
     ;; === Mount Function ===
     ::mount-app
     ^{:doc "Function to mount the Reagent app to the DOM."}
     [make-mount-app ::app-component]
     
     ;; === Boot Function ===
     :plin.boot/boot-fn
     ^{:doc "Boot function that mounts the Reagent UI."}
     [partial boot-fn ::mount-app]}}))
