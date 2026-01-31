#!/usr/bin/env bb
;; Script to generate index.html from source files
;; Usage: bb generate.bb
;;
;; This script reads all ClojureScript source files and inlines them into
;; a single HTML file that can be opened directly in a browser using Scittle.

(require '[clojure.string :as str]
         '[babashka.fs :as fs])

(def base-dir (str (fs/parent *file*)))

(defn read-file [path]
  (let [full-path (str base-dir "/" path)]
    (if (fs/exists? full-path)
      (slurp full-path)
      (do
        (println "WARNING: File not found:" full-path)
        ""))))

(defn process-reader-conditionals
  "Process reader conditionals for Scittle (CLJS-only environment).
   
   Scittle handles #?(:cljs ...) reader conditionals natively, so we just
   need to remove #?(:clj ...) forms that would cause errors in Scittle.
   We keep all #?(:cljs ...) forms as-is."
  [source]
  (-> source
      ;; Remove standalone #?(:clj ...) forms (like imports in ns)
      (str/replace #"#\?\(:clj\s+\([^)]*\)\)" "")
      ;; Handle #?(:clj X :cljs Y) -> Y (keep only cljs)
      (str/replace #"#\?\(:clj\s+[^\s)]+\s+:cljs\s+([^\s)]+)\)" "$1")
      ;; Handle #?(:cljs Y :clj X) -> Y (keep only cljs)
      (str/replace #"#\?\(:cljs\s+([^\s)]+)\s+:clj\s+[^\s)]+\)" "$1")))

;; Files to include, in dependency order
;; Each entry is [namespace-comment path]
(def deps-files
  [;; Malli stubs (must be first - other libs depend on it)
   ["Malli Core (stubs)" "deps/malli/core.cljc"]
   ["Malli Error (stubs)" "deps/malli/error.cljc"]
   
   ;; Injectable library
   ["Injectable Easy" "../shared/src/injectable/easy.cljc"]
   ["Injectable Container" "../shared/src/injectable/container.cljc"]
   ["Injectable Core" "../shared/src/injectable/core.cljc"]
   
   ;; Pluggable library
   ["Pluggable Root Plugin" "../shared/src/pluggable/root_plugin.cljc"]
   ["Pluggable Container" "../shared/src/pluggable/container.cljc"]
   ["Pluggable Core" "../shared/src/pluggable/core.cljc"]
   
   ;; Plin framework
   ["Plin Core" "../shared/src/plin/core.cljc"]
   ["Plin Boot" "../shared/src/plin/boot.cljc"]
   
   ;; Todo domain and plugins (from shared)
   ["Todo Domain" "../shared/src/todo/domain.cljc"]
   ["Todo Domain Plugin" "../shared/src/todo/plugins/domain.cljc"]
   ["Todo Persistence Plugin" "../shared/src/todo/plugins/persistence.cljc"]
   ["Todo Deadlines Plugin" "../shared/src/todo/plugins/deadlines.cljc"]
   ["Todo Calendar Plugin" "../shared/src/todo/plugins/calendar.cljc"]])

(def src-files
  [;; Browser-specific plugins
   ["Local Storage Plugin" "src/todo_browser/plugins/local_storage.cljs"]
   ["Browser Boot Plugin" "src/todo_browser/plugins/browser_boot.cljs"]
   
   ;; Main entry point (must be last)
   ["Main Application" "src/todo_browser/main.cljs"]])

(defn make-script-tag [comment source]
  (str "  <!-- " comment " -->\n"
       "  <script type='application/x-scittle'>\n"
       (process-reader-conditionals source)
       "\n  </script>\n"))

(defn generate-scripts []
  (str/join "\n"
            (for [[comment path] (concat deps-files src-files)]
              (let [source (read-file path)]
                (if (empty? source)
                  (str "  <!-- " comment " - FILE NOT FOUND: " path " -->\n")
                  (make-script-tag comment source))))))

(def css-styles
  "body { font-family: system-ui, sans-serif; max-width: 700px; margin: 40px auto; padding: 0 20px; background: #f5f5f5; }
    h1 { color: #333; text-align: center; }
    .app-container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
    
    /* Navigation */
    .nav { display: flex; justify-content: center; gap: 20px; margin-bottom: 20px; padding-bottom: 15px; border-bottom: 1px solid #eee; }
    .nav a { color: #666; text-decoration: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
    .nav a:hover { background: #f0f0f0; }
    .nav a.active { background: #4CAF50; color: white; }
    
    /* Add form */
    .add-form { display: flex; gap: 10px; margin-bottom: 20px; }
    .add-form input[type='text'] { flex: 1; padding: 10px; font-size: 16px; border: 1px solid #ddd; border-radius: 4px; }
    .add-form input[type='date'] { padding: 10px; font-size: 14px; border: 1px solid #ddd; border-radius: 4px; }
    .add-form button { padding: 10px 20px; font-size: 16px; background: #4CAF50; color: white; border: none; border-radius: 4px; cursor: pointer; }
    .add-form button:hover { background: #45a049; }
    
    /* Filters */
    .filters { display: flex; justify-content: center; gap: 10px; margin-bottom: 20px; }
    .filters a { color: #666; text-decoration: none; padding: 5px 12px; border-radius: 4px; cursor: pointer; }
    .filters a:hover { background: #f0f0f0; }
    .filters a.active { background: #4CAF50; color: white; }
    
    /* Task list */
    .tasks { }
    .task { display: flex; align-items: center; padding: 12px; border-bottom: 1px solid #eee; gap: 10px; }
    .task:last-child { border-bottom: none; }
    .task input[type='checkbox'] { width: 20px; height: 20px; cursor: pointer; }
    .task .task-title { flex: 1; font-size: 16px; }
    .task .task-due { font-size: 12px; color: #666; margin-right: 10px; }
    .task .due-input { font-size: 12px; padding: 4px; border: 1px solid #ddd; border-radius: 4px; }
    .task button { background: #ff4444; color: white; border: none; padding: 6px 12px; border-radius: 4px; cursor: pointer; }
    .task button:hover { background: #cc0000; }
    
    /* Task status styling */
    .task.completed .task-title { text-decoration: line-through; opacity: 0.6; }
    .task.overdue { background: #fff0f0; }
    .task.overdue .task-due { color: #cc0000; font-weight: bold; }
    .task.due-today { background: #fff8e0; }
    .task.due-today .task-due { color: #cc7700; font-weight: bold; }
    
    /* Empty state */
    .empty { text-align: center; color: #999; padding: 40px; }
    
    /* Actions */
    .actions { text-align: center; margin-top: 20px; padding-top: 20px; border-top: 1px solid #eee; }
    .actions button { background: #666; color: white; border: none; padding: 8px 16px; border-radius: 4px; cursor: pointer; }
    .actions button:hover { background: #444; }
    
    /* Stats */
    .stats { text-align: center; color: #666; margin-top: 10px; font-size: 14px; }
    .stats .overdue { color: #cc0000; }
    
    /* Calendar view */
    .calendar-view { }
    .calendar-stats { display: flex; justify-content: center; gap: 15px; margin-bottom: 20px; flex-wrap: wrap; }
    .calendar-stats .stat { font-size: 14px; color: #666; }
    .calendar-stats .stat strong { color: #333; }
    .calendar-stats .stat.overdue strong { color: #cc0000; }
    .calendar-stats .stat.due-today strong { color: #cc7700; }
    
    .calendar-section { margin-bottom: 20px; }
    .calendar-section h3 { color: #666; font-size: 14px; text-transform: uppercase; margin-bottom: 10px; }
    .calendar-section.overdue h3 { color: #cc0000; }
    .calendar-section.due-today h3 { color: #cc7700; }
    
    .calendar-task { padding: 8px 12px; background: #f9f9f9; border-radius: 4px; margin-bottom: 5px; display: flex; justify-content: space-between; }
    .calendar-task .task-title { }
    .calendar-task .task-due { color: #666; font-size: 12px; }
    
    .date-group { margin-bottom: 15px; }
    .date-group h4 { color: #333; font-size: 14px; margin-bottom: 5px; }
    
    .calendar-empty { text-align: center; color: #999; padding: 40px; }")

(defn generate-html []
  (str "<!DOCTYPE html>
<html>
<head>
  <meta charset='utf-8'>
  <title>Todo App - Browser (Scittle + Plin)</title>
  <!-- Scittle: ClojureScript in the browser -->
  <script src='https://cdn.jsdelivr.net/npm/scittle@0.6.17/dist/scittle.js'></script>
  <script src='https://cdn.jsdelivr.net/npm/scittle@0.6.17/dist/scittle.nrepl.js'></script>
  <!-- React -->
  <script src='https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js'></script>
  <script src='https://cdn.jsdelivr.net/npm/react-dom@18/umd/react-dom.production.min.js'></script>
  <!-- Reagent for Scittle -->
  <script src='https://cdn.jsdelivr.net/npm/scittle@0.6.17/dist/scittle.reagent.js'></script>
  <style>
" css-styles "
  </style>
</head>
<body>
  <div id='app'>
    <div class='app-container'>
      <h1>Loading...</h1>
      <p style='text-align: center; color: #666;'>Initializing Plin framework...</p>
    </div>
  </div>

" (generate-scripts) "
</body>
</html>"))

(defn -main []
  (println "Generating index.html from source files...")
  (println "Base directory:" base-dir)
  (let [html (generate-html)
        output-path (str base-dir "/index.html")]
    (spit output-path html)
    (println "Done! Written to:" output-path)
    (println "File size:" (count html) "bytes")))

(-main)
