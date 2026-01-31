(ns todo-browser.plugins.browser-boot
  "Browser-specific boot implementation for the Todo app.
   
   This plugin demonstrates FULL DEPENDENCY INJECTION:
   - NO direct requires of domain namespace
   - ALL functionality comes through injected beans
   - UI components receive exactly the dependencies they need via factory closure
   - The boot function receives a pre-configured mount-app function
   
   Dependencies:
   - :todo/persistence - for load-fn, store-fn
   - :todo/domain - for task operations (add, toggle, delete, etc.)"
  (:require [plin.core :as pi]
            [reagent.core :as r]
            [reagent.dom :as rdom]))

;; =============================================================================
;; Factory: Creates the entire UI with dependencies closed over
;; =============================================================================

(defn make-app-component
  "Factory: creates the app component with all dependencies injected.
   
   Dependencies (injected):
   - load-fn, store-fn: persistence operations
   - get-all-tasks, get-active-tasks, get-completed-tasks: query fns
   - add-task, toggle-task, delete-task, clear-completed: mutation fns"
  [load-fn store-fn
   get-all-tasks get-active-tasks get-completed-tasks
   add-task toggle-task delete-task clear-completed]
  
  ;; Helper to save tasks
  (let [save-tasks! (fn [task-list] (store-fn task-list))
        
        ;; Task item component
        task-item (fn [app-state task]
                    [:div.task {:class (when (:completed task) "completed")}
                     [:input {:type "checkbox"
                              :checked (:completed task)
                              :on-change #(let [new-state (swap! app-state 
                                                                 update :task-list 
                                                                 toggle-task (:id task))]
                                            (save-tasks! (:task-list new-state)))}]
                     [:span (:title task)]
                     [:button {:on-click #(let [new-state (swap! app-state 
                                                                 update :task-list 
                                                                 delete-task (:id task))]
                                            (save-tasks! (:task-list new-state)))} 
                      "Delete"]])
        
        ;; Task list view component
        task-list-view (fn [app-state]
                         (let [{:keys [filter task-list]} @app-state
                               tasks (case filter
                                       :active (get-active-tasks task-list)
                                       :completed (get-completed-tasks task-list)
                                       (get-all-tasks task-list))]
                           [:div.tasks
                            (if (empty? tasks)
                              [:div.empty "No tasks to display"]
                              (for [task tasks]
                                ^{:key (:id task)} [task-item app-state task]))]))
        
        ;; Add task form component
        add-task-form (fn [app-state]
                        (let [new-task (r/atom "")]
                          (fn []
                            [:form.add-form {:on-submit (fn [e]
                                                          (.preventDefault e)
                                                          (when (seq @new-task)
                                                            (let [new-state (swap! app-state 
                                                                                   update :task-list 
                                                                                   add-task @new-task)]
                                                              (save-tasks! (:task-list new-state)))
                                                            (reset! new-task "")))}
                             [:input {:type "text"
                                      :placeholder "What needs to be done?"
                                      :value @new-task
                                      :on-change #(reset! new-task (.. % -target -value))}]
                             [:button {:type "submit"} "Add Task"]])))
        
        ;; Filter links component
        filter-links (fn [app-state]
                       (let [current-filter (:filter @app-state)]
                         [:div.filters
                          [:a {:href "#"
                               :class (when (= current-filter :all) "active")
                               :on-click #(swap! app-state assoc :filter :all)} "All"]
                          [:a {:href "#"
                               :class (when (= current-filter :active) "active")
                               :on-click #(swap! app-state assoc :filter :active)} "Active"]
                          [:a {:href "#"
                               :class (when (= current-filter :completed) "active")
                               :on-click #(swap! app-state assoc :filter :completed)} "Completed"]]))]
    
    ;; Return the main app component function
    (fn []
      (let [app-state (r/atom {:filter :all
                               :task-list (load-fn)})]
        (fn []
          (let [task-list (:task-list @app-state)
                active-count (count (get-active-tasks task-list))
                completed-count (count (get-completed-tasks task-list))]
            [:div.app-container
             [:h1 "Todo List"]
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
               "Clear Completed"]]
             [:div.stats (str active-count " active, " completed-count " completed")]]))))))

;; =============================================================================
;; Mount Function Factory
;; =============================================================================

(defn make-mount-app
  "Factory: creates mount-app function with injected app component."
  [app-component]
  (fn []
    (rdom/render [app-component] 
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
  (pi/plugin
   {:id :todo-browser/boot
    :doc "Browser boot with fully injected dependencies. Demonstrates proper DI with Reagent."
    :deps []
    
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
      :todo.plugins.domain/add-task
      :todo.plugins.domain/toggle-task
      :todo.plugins.domain/delete-task
      :todo.plugins.domain/clear-completed]
     
     ;; === Mount Function ===
     ::mount-app
     ^{:doc "Function to mount the Reagent app to the DOM."}
     [make-mount-app ::app-component]
     
     ;; === Boot Function ===
     :plin.boot/boot-fn
     ^{:doc "Boot function that mounts the Reagent UI."}
     [partial boot-fn ::mount-app]}}))
