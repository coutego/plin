#!/usr/bin/env bb
;; Script to generate browser-reagent/index.html from source files
;; Usage: bb bin/generate-browser-html.clj

(require '[clojure.string :as str])

(defn slurp-file [path]
  (try
    (slurp path)
    (catch Exception e
      (println "Warning: Could not read" path)
      "")))

(defn escape-for-html [s]
  (-> s
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#x27;")
      (str/replace "/" "&#x2F;")))

(defn escape-for-js-string [s]
  (-> s
      (str/replace "\\" "\\\\")
      (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n")
      (str/replace "\r" "\\r")
      (str/replace "\t" "\\t")))

(defn generate-html []
  (str "<!DOCTYPE html>
<html>
<head>
  <meta charset='utf-8'>
  <title>Todo App - Browser (Scittle)</title>
  <script src='https://cdn.jsdelivr.net/npm/scittle@0.6.17/dist/scittle.js'></script>
  <script src='https://cdn.jsdelivr.net/npm/scittle@0.6.17/dist/scittle.nrepl.js'></script>
  <script src='https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js'></script>
  <script src='https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js'></script>
  <script src='https://cdn.jsdelivr.net/npm/scittle@0.6.17/dist/scittle.reagent.js'></script>
  <style>
    body { font-family: system-ui, sans-serif; max-width: 600px; margin: 40px auto; padding: 0 20px; background: #f5f5f5; }
    h1 { color: #333; text-align: center; }
    .app-container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
    .add-form { display: flex; margin-bottom: 20px; }
    .add-form input { flex: 1; padding: 10px; font-size: 16px; border: 1px solid #ddd; border-radius: 4px 0 0 4px; }
    .add-form button { padding: 10px 20px; font-size: 16px; background: #4CAF50; color: white; border: none; border-radius: 0 4px 4px 0; cursor: pointer; }
    .add-form button:hover { background: #45a049; }
    .filters { text-align: center; margin-bottom: 20px; }
    .filters a { color: #666; text-decoration: none; margin: 0 10px; padding: 5px 10px; border-radius: 4px; }
    .filters a:hover { background: #f0f0f0; }
    .filters a.active { background: #4CAF50; color: white; }
    .task { display: flex; align-items: center; padding: 12px; border-bottom: 1px solid #eee; }
    .task:last-child { border-bottom: none; }
    .task.completed span { text-decoration: line-through; opacity: 0.6; }
    .task input[type='checkbox'] { margin-right: 12px; width: 20px; height: 20px; cursor: pointer; }
    .task span { flex: 1; font-size: 16px; }
    .task button { background: #ff4444; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; }
    .task button:hover { background: #cc0000; }
    .empty { text-align: center; color: #999; padding: 40px; }
    .actions { text-align: center; margin-top: 20px; padding-top: 20px; border-top: 1px solid #eee; }
    .actions button { background: #666; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
    .actions button:hover { background: #444; }
    .stats { text-align: center; color: #666; margin-top: 10px; font-size: 14px; }
  </style>
</head>
<body>
  <div id='app'></div>

  <!-- Domain Logic -->
  <script type='application/x-scittle'>
(ns todo.domain)

(defrecord Task [id title completed created-at])

(defrecord TaskList [tasks next-id])

(defn create-task-list []
  (->TaskList {} 1))

(defn add-task [task-list title]
  (let [id (:next-id task-list)
        task (map->Task {:id id
                        :title title
                        :completed false
                        :created-at (js/Date.)})]
    (-> task-list
        (assoc-in [:tasks id] task)
        (update :next-id inc))))

(defn toggle-task [task-list task-id]
  (update-in task-list [:tasks task-id :completed] not))

(defn delete-task [task-list task-id]
  (update task-list :tasks dissoc task-id))

(defn get-all-tasks [task-list]
  (->> (:tasks task-list)
       vals
       (sort-by :created-at)))

(defn get-active-tasks [task-list]
  (->> (get-all-tasks task-list)
       (remove :completed)))

(defn get-completed-tasks [task-list]
  (->> (get-all-tasks task-list)
       (filter :completed)))

(defn clear-completed [task-list]
  (update task-list :tasks #(into {} (remove (fn [[_ task]] (:completed task))) %)))
  </script>

  <!-- Simplified Plin Core for Scittle -->
  <script type='application/x-scittle'>
(ns plin.core)

(defn load-plugins
  [plugins]
  (let [beans {}
        merged-beans (reduce (fn [acc plugin]
                               (if-let [plugin-beans (:beans plugin)]
                                 (merge acc plugin-beans)
                                 acc))
                             beans
                             plugins)
        resolved-beans (into {} (map (fn [[k v]]
                                      (if (and (vector? v) (= := (first v)))
                                        [k (second v)]
                                        [k v]))
                                    merged-beans))]
    resolved-beans))
  </script>

  <!-- Plin Boot -->
  <script type='application/x-scittle'>
(ns plin.boot)

(defonce state
  (atom {:container nil}))

(def plugin
  {:id :plin.boot/plugin
   :beans
   {::boot-fn
    [:= (fn [_container]
          (println \"Boot complete\"))]}})

(defn reload! []
  (let [{:keys [all-plugins]} @state
        container (plin.core/load-plugins all-plugins)
        boot-fn (::boot-fn container)]
    (swap! state assoc :container container)
    (when boot-fn
      (boot-fn container))
    @state))

(defn bootstrap! [plugins]
  (let [full-list (vec (cons plugin plugins))]
    (swap! state assoc :all-plugins full-list)
    (reload!)))
  </script>

  <!-- Persistence Interface Plugin -->
  <script type='application/x-scittle'>
(ns todo.plugins.persistence)

(defonce memory-store (atom nil))

(defn- trivial-store-fn [task-list]
  (reset! memory-store task-list))

(defn- trivial-load-fn []
  (or @memory-store (todo.domain/create-task-list)))

(defn- trivial-delete-fn []
  (reset! memory-store nil))

(def plugin
  {:id :todo/persistence
   :deps []
   :beans
   {::store-fn [:= trivial-store-fn]
    ::load-fn [:= trivial-load-fn]
    ::delete-fn [:= trivial-delete-fn]}})
  </script>

  <!-- LocalStorage Persistence Plugin -->
  <script type='application/x-scittle'>
(ns todo-browser.plugins.local-storage)

(defn- load-tasks []
  (if-let [stored (.getItem js/localStorage \"todo-tasks\")]
    (let [parsed (js/JSON.parse stored)
          tasks (into {} (map (fn [[k v]] 
                                [(js/parseInt k) 
                                 (todo.domain/map->Task (js->clj v :keywordize-keys true))]) 
                              (js->clj parsed)))]
      (if (seq tasks)
        (assoc (todo.domain/create-task-list) 
               :tasks tasks 
               :next-id (inc (apply max (keys tasks))))
        (todo.domain/create-task-list)))
    (todo.domain/create-task-list)))

(defn- save-tasks [task-list]
  (let [tasks (clj->js (:tasks task-list))]
    (.setItem js/localStorage \"todo-tasks\" (js/JSON.stringify tasks))))

(defn- delete-tasks []
  (.removeItem js/localStorage \"todo-tasks\"))

(def plugin
  {:id :todo-browser/local-storage
   :deps [:todo/persistence]
   :beans
   {::store-fn [:= save-tasks]
    ::load-fn [:= load-tasks]
    ::delete-fn [:= delete-tasks]}})
  </script>

  <!-- Browser Boot Plugin -->
  <script type='application/x-scittle'>
(ns todo-browser.plugins.browser-boot)

(defn- create-app-state [container]
  (let [load-fn (get container :todo.plugins.persistence/load-fn)]
    (reagent.core/atom {:filter :all
                        :task-list (load-fn)})))

(defn- save-tasks! [container task-list]
  (let [store-fn (get container :todo.plugins.persistence/store-fn)]
    (store-fn task-list)))

(defn- task-item [app-state container task]
  [:div.task {:class (when (:completed task) \"completed\")}
   [:input {:type \"checkbox\"
            :checked (:completed task)
            :on-change #(let [new-state (swap! app-state 
                                                update :task-list 
                                                todo.domain/toggle-task (:id task))]
                          (save-tasks! container (:task-list new-state)))}]
   [:span (:title task)]
   [:button {:on-click #(let [new-state (swap! app-state 
                                                update :task-list 
                                                todo.domain/delete-task (:id task))]
                          (save-tasks! container (:task-list new-state)))} 
    \"Delete\"]])

(defn- task-list-view [app-state container]
  (let [{:keys [filter task-list]} @app-state
        tasks (case filter
                :active (todo.domain/get-active-tasks task-list)
                :completed (todo.domain/get-completed-tasks task-list)
                (todo.domain/get-all-tasks task-list))]
    (save-tasks! container task-list)
    [:div.tasks
     (if (empty? tasks)
       [:div.empty \"No tasks to display\"]
       (for [task tasks]
         ^{:key (:id task)} [task-item app-state container task]))]))

(defn- add-task-form [app-state container]
  (let [new-task (reagent.core/atom \"\")]
    (fn []
      [:form.add-form {:on-submit (fn [e]
                                    (.preventDefault e)
                                    (when (seq @new-task)
                                      (let [new-state (swap! app-state 
                                                             update :task-list 
                                                             todo.domain/add-task @new-task)]
                                        (save-tasks! container (:task-list new-state)))
                                      (reset! new-task \"\")))}
       [:input {:type \"text\"
                :placeholder \"What needs to be done?\"
                :value @new-task
                :on-change #(reset! new-task (.. % -target -value))}]
       [:button {:type \"submit\"} \"Add Task\"]])))

(defn- filter-links [app-state]
  (let [current-filter (:filter @app-state)]
    [:div.filters
     [:a {:href \"#\"
          :class (when (= current-filter :all) \"active\")
          :on-click #(swap! app-state assoc :filter :all)} \"All\"]
     [:a {:href \"#\"
          :class (when (= current-filter :active) \"active\")
          :on-click #(swap! app-state assoc :filter :active)} \"Active\"]
     [:a {:href \"#\"
          :class (when (= current-filter :completed) \"active\")
          :on-click #(swap! app-state assoc :filter :completed)} \"Completed\"]]))

(defn- app-component [app-state container]
  [:div.app-container
   [:h1 \"Todo List\"]
   [add-task-form app-state container]
   [filter-links app-state]
   [task-list-view app-state container]
   [:div.actions
    [:button {:on-click #(let [new-state (swap! app-state 
                                                 (fn [s]
                                                   (-> s
                                                       (update :task-list todo.domain/clear-completed)
                                                       (assoc :filter :all))))]
                           (save-tasks! container (:task-list new-state)))} 
     \"Clear Completed\"]]
   (let [task-list (:task-list @app-state)
         active-count (count (todo.domain/get-active-tasks task-list))
         completed-count (count (todo.domain/get-completed-tasks task-list))]
     [:div.stats (str active-count \" active, \" completed-count \" completed\")])])

(defn- mount-app [container]
  (let [app-state (create-app-state container)]
    (reagent.dom/render [app-component app-state container] 
                 (.getElementById js/document \"app\"))))

(defn- boot-fn [container]
  (println \"Booting browser Todo app...\")
  (mount-app container)
  (println \"Todo app mounted successfully!\"))

(def plugin
  {:id :todo-browser/boot
   :deps [:plin.boot/plugin]
   :beans
   {:plin.boot/boot-fn [:= boot-fn]}})
  </script>

  <!-- Main Application -->
  <script type='application/x-scittle'>
(ns todo-browser.main)

(defn init []
  (println \"Initializing Todo Browser App...\")
  
  (let [plugins [todo.plugins.persistence/plugin
                 todo-browser.plugins.local-storage/plugin
                 todo-browser.plugins.browser-boot/plugin]]
    
    (plin.boot/bootstrap! plugins))
  
  (println \"Todo Browser App initialized!\")
  (println \"Connect with nREPL on port 1337\"))

(init)
  </script>
</body>
</html>"))

(defn -main []
  (println "Generating browser-reagent/index.html...")
  (let [html (generate-html)]
    (spit "examples/browser-reagent/index.html" html)
    (println "Done! File written to examples/browser-reagent/index.html")))

(-main)
