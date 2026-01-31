#!/usr/bin/env bb
;; Interactive script to run Plin examples
;; Usage: bb bin/run-example.clj

(require '[clojure.string :as str])
(require '[babashka.process :as p])

(def examples
  [{:id :browser-reagent
    :name "Browser (Reagent)"
    :description "Todo app in browser using Scittle + Reagent + LocalStorage"
    :type :browser
    :path "examples/browser-reagent"
    :cmd ["open" "index.html"]}
   
   {:id :jvm-tui
    :name "JVM Terminal UI"
    :description "Todo app in terminal using Clojure + Lanterna TUI library"
    :type :terminal
    :path "examples/jvm-tui"
    :cmd ["clojure" "-M:run"]}
   
   {:id :jvm-server
    :name "JVM HTTP Server"
    :description "Todo app as HTTP server using Clojure + Ring/Jetty"
    :type :server
    :path "examples/jvm-server"
    :cmd ["clojure" "-M:run"]
    :url "http://localhost:3000"}
   
   {:id :node-tui
    :name "Node.js Terminal UI"
    :description "Todo app in terminal using nbb (native ClojureScript for Node.js)"
    :type :terminal
    :path "examples/node-tui"
    :cmd ["npx" "nbb" "-cp" "src:deps" "src/todo_node_tui/main.cljs"]}
   
   {:id :node-server
    :name "Node.js HTTP Server"
    :description "Todo app as HTTP server using nbb + Node.js http module"
    :type :server
    :path "examples/node-server"
    :cmd ["npx" "nbb" "-cp" "src:deps" "src/todo_node_server/main.cljs"]
    :url "http://localhost:3000"}])

(defn print-header []
  (println)
  (println "╔═══════════════════════════════════════════════════════════════╗")
  (println "║              Plin Examples Runner                             ║")
  (println "║         Plugin-based Todo App Examples                      ║")
  (println "╚═══════════════════════════════════════════════════════════════╝")
  (println))

(defn print-examples []
  (println "Available examples:")
  (println)
  (doseq [[idx example] (map-indexed vector examples)]
    (let [num (inc idx)
          name (:name example)
          type-str (case (:type example)
                     :browser "🌐 Browser"
                     :terminal "💻 Terminal" 
                     :server "🖥️  Server"
                     "❓ Unknown")
          desc (:description example)]
      (println (format "  [%d] %-25s %s" num name type-str))
      (println (format "      %s" desc))
      (println)))
  (println "  [q] Quit")
  (println))

(defn get-user-choice []
  (print "Enter your choice: ")
  (flush)
  (let [input (str/trim (read-line))]
    (cond
      (= input "q") :quit
      (re-matches #"^\d+$" input)
      (let [num (Integer/parseInt input)]
        (if (<= 1 num (count examples))
          (nth examples (dec num))
          (do (println "Invalid choice. Please enter a number between 1 and" (count examples))
              nil)))
      :else
      (do (println "Invalid input. Please enter a number or 'q' to quit.")
          nil))))

(defn check-prerequisites [example]
  (case (:id example)
    :browser-reagent
    (do
      (println "✓ No prerequisites needed - just opening browser...")
      true)
    
    (:jvm-tui :node-tui)
    (do
      ;; Check if stdin is a TTY
      (println "⚠️  IMPORTANT: TUI examples require direct terminal access.")
      (println "   They cannot be run through this menu.")
      (println "")
      (println "   Please run directly from a terminal:")
      (if (= :node-tui (:id example))
        (do
          (println "     cd examples/node-tui")
          (println "     npx nbb -cp \"src:deps\" src/todo_node_tui/main.cljs"))
        (do
          (println "     cd examples/jvm-tui")
          (println "     clojure -M:run")))
      (println "")
      (println "   Press Enter to continue anyway (will likely fail)...")
      (read-line)
      (case (:id example)
        :jvm-tui
        (do
          (print "Checking for Clojure CLI... ")
          (if-let [clojure-cmd (or (fs/which "clojure") (fs/which "clj"))]
            (do (println "✓ Found" (str clojure-cmd))
                true)
            (do (println "✗ Not found")
                (println "Please install Clojure CLI tools: https://clojure.org/guides/getting_started")
                false)))
        :node-tui
        (do
          (print "Checking for Node.js... ")
          (if (fs/which "node")
            (do (println "✓ Found")
                (print "Checking for npm... ")
                (if (fs/which "npm")
                  (do (println "✓ Found")
                      true)
                  (do (println "✗ Not found")
                      (println "Please install Node.js and npm: https://nodejs.org/")
                      false)))
            (do (println "✗ Not found")
                (println "Please install Node.js: https://nodejs.org/")
                false)))))

    :jvm-server
    (do
      (print "Checking for Clojure CLI... ")
      (if-let [clojure-cmd (or (fs/which "clojure") (fs/which "clj"))]
        (do (println "✓ Found" (str clojure-cmd))
            true)
        (do (println "✗ Not found")
            (println "Please install Clojure CLI tools: https://clojure.org/guides/getting_started")
            false)))

    :node-server
    (do
      (print "Checking for Node.js... ")
      (if (fs/which "node")
        (do (println "✓ Found")
            (print "Checking for npm... ")
            (if (fs/which "npm")
              (do (println "✓ Found")
                  true)
              (do (println "✗ Not found")
                  (println "Please install Node.js and npm: https://nodejs.org/")
                  false)))
        (do (println "✗ Not found")
            (println "Please install Node.js: https://nodejs.org/")
            false)))

    ;; Default case
    true))

(defn setup-example [example]
  (case (:id example)
    :browser-reagent
    (do
      (println "Checking if HTML needs regeneration...")
      (let [html-path (str (:path example) "/index.html")
            gen-script "bin/generate-browser-html.clj"]
        (if (fs/exists? gen-script)
          (do
            (println "Regenerating HTML from source files...")
            (let [result (p/sh "bb" gen-script)]
              (if (= 0 (:exit result))
                (do (println "✓ HTML regenerated successfully")
                    true)
                (do (println "✗ Failed to regenerate HTML:")
                    (println (:err result))
                    false))))
          (do
            (println "✓ Using existing HTML file")
            true))))
    
    (:node-tui :node-server)
    (do
      (println "Setting up Node.js example...")
      (let [deps-dir (str (:path example) "/deps")
            ;; Check if deps folder exists AND has content
            deps-populated? (and (fs/exists? deps-dir)
                                 (seq (fs/list-dir deps-dir)))]
        (if deps-populated?
          (do (println "✓ Dependencies already set up")
              true)
          (do
            (println "Creating deps directory...")
            (fs/create-dirs deps-dir)
            (println "Copying dependencies...")
            (let [copy-result (p/sh "bash" "-c" 
                                    (str "cp -r ../injectable/src/injectable " deps-dir "/ && "
                                         "cp -r ../pluggable/src/pluggable " deps-dir "/ && "
                                         "cp -r src/plin " deps-dir "/ && "
                                         "cp -r examples/common/src/todo " deps-dir "/"))]
              (if (= 0 (:exit copy-result))
                (do (println "✓ Dependencies copied")
                    (println "Installing npm packages...")
                    (let [npm-result (p/sh {:dir (:path example)} "npm" "install")]
                      (if (= 0 (:exit npm-result))
                        (do (println "✓ npm packages installed")
                            true)
                        (do (println "✗ npm install failed:")
                            (println (:err npm-result))
                            false))))
                (do (println "✗ Failed to copy dependencies:")
                    (println (:err copy-result))
                    false)))))))
    
    true))

(defn run-example [example]
  (println)
  (println (str "═══ Running: " (:name example) " ═══"))
  (println)
  
  (when-not (check-prerequisites example)
    (println "Prerequisites not met. Aborting.")
    (System/exit 1))
  
  (when-not (setup-example example)
    (println "Setup failed. Aborting.")
    (System/exit 1))
  
  (println)
  (println (str "Launching in: " (:path example)))
  (println (str "Command: " (str/join " " (:cmd example))))
  
  (when (:url example)
    (println (str "URL: " (:url example))))
  
  (println)
  (println "Press Ctrl+C to stop" (if (= :server (:type example)) "the server" "the application"))
  (println)
  
  ;; Run the command in the foreground with inherited stdin/stdout
  (let [cmd (:cmd example)
        process (apply p/process (concat [{:inherit true :dir (:path example)}] cmd))]
    ;; Wait for the process to complete
    @process))

(defn -main []
  (print-header)
  
  (loop []
    (print-examples)
    
    (let [choice (get-user-choice)]
      (cond
        (= choice :quit)
        (do (println)
            (println "Goodbye!")
            (System/exit 0))
        
        (nil? choice)
        (recur)
        
        :else
        (run-example choice)))))

(-main)
