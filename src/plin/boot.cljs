(ns plin.boot
  "A standard bootstrapper for Plin applications.
   
   This namespace provides a ready-to-use mechanism for managing the application lifecycle,
   including:
   *   Holding the system state (loaded plugins, active container).
   *   Handling plugin enabling/disabling logic (cascading disables).
   *   Reloading the system when the configuration changes.
   
   It exposes a `plugin` that provides an API bean `::api` for controlling the system."
  (:require [reagent.core :as r]
            [plin.core :as pi]))

;; --- State ---

(defonce state 
  ;; Reagent atom holding the system state.
  ;; 
  ;; **Structure:**
  ;; *   `:all-plugins`: Vector of all available plugins (as passed to `bootstrap!`).
  ;; *   `:disabled-ids`: Set of plugin IDs that are manually disabled.
  ;; *   `:container`: The currently active Injectable container.
  ;; *   `:last-error`: The last exception thrown during reload, if any.
  (r/atom {:all-plugins []
           :disabled-ids #{}
           :container nil
           :last-error nil}))

;; --- Logic ---

(defn get-cascading-disabled
  "Calculates the set of effectively disabled plugins based on dependencies.
   
   If Plugin A is disabled, and Plugin B depends on A, then B must also be disabled.
   This function recursively propagates the disabled state up the dependency tree.
   
   **Arguments:**
   *   `plugins`: Sequence of all plugin maps.
   *   `disabled-ids`: Set of manually disabled plugin IDs.
   
   **Returns:**
   *   A set of all disabled plugin IDs (manual + cascaded)."
  [plugins disabled-ids]
  (loop [current-disabled disabled-ids]
    (let [newly-disabled 
          (reduce
           (fn [acc plugin]
             (if (and (not (contains? current-disabled (:id plugin)))
                      (some #(contains? current-disabled %) (:deps plugin)))
               (conj acc (:id plugin))
               acc))
           #{}
           plugins)]
      (if (empty? newly-disabled)
        current-disabled
        (recur (into current-disabled newly-disabled))))))

(declare reload!)

;; --- Plugin Definition ---

(def plugin
  "The Bootstrapper Plugin.
   
   It provides the `::api` bean, which exposes the system state and reload function.
   This allows other plugins (like debug tools) to inspect and control the system."
  (pi/plugin
   {:doc "System Bootstrapper. Manages the plugin lifecycle and system state."
    :deps []
    
    :beans
    {::api
     ^{:doc "System Control API.
             Returns a map: `{:state <atom> :reload! <fn>}`."
       :api {:ret :map}}
     ;; Use a factory function instead of [:= ...] to break the circular dependency
     ;; in the definitions map. If we used [:= {:state state}], the definitions map
     ;; would contain the state atom, which contains the container, which contains
     ;; the definitions map. By using a function, the definition contains the function,
     ;; and the printer won't traverse the closure.
     [(fn [] {:state state
              :reload! reload!})]}}))

;; --- Reload Implementation ---

(defn reload! 
  "Reloads the system based on the current state.
   
   1.  Calculates the active plugins (filtering out disabled ones).
   2.  Calls `plin.core/load-plugins` to create a new container.
   3.  Looks for a mount function in the container (specifically `:plugins.p-app-shell/mount`).
   4.  Updates the `state` atom with the new container.
   5.  Executes the mount function to render the app.
   
   This function runs asynchronously (via `setTimeout`) to allow the UI to update immediately if needed."
  []
  (js/setTimeout
   (fn []
     (println "System: Reloading...")
     (try
       (let [{:keys [all-plugins disabled-ids]} @state
             
             ;; Calculate which plugins to actually load
             final-disabled (get-cascading-disabled all-plugins disabled-ids)
             plugins-to-load (filter #(not (contains? final-disabled (:id %))) all-plugins)
             
             ;; Create Container
             ;; p-boot is already in 'all-plugins' (via bootstrap!), so it gets loaded and exposes ::api
             container (pi/load-plugins (vec plugins-to-load))
             
             ;; Resolve the mount function.
             ;; We look for the specific p-app-shell mount bean.
             ;; In a more generic system, this key could be configured.
             mount-fn (:plugins.p-app-shell/mount container)]
         
         ;; Update State
         (swap! state assoc :container container :last-error nil)
         
         ;; Mount App
         (if mount-fn
           (mount-fn)
           (println "WARNING: No :plugins.p-app-shell/mount bean found."))
         
         (println "System: Reload complete. Active plugins:" (count plugins-to-load)))
       (catch :default e
         (js/console.error e)
         (swap! state assoc :last-error e))))
   0))

;; --- Bootstrap ---

(defn bootstrap! 
  "Initializes the system with the given list of plugins.
   
   This is the main entry point for the application. It:
   1.  Appends the `plin.boot/plugin` to the provided list.
   2.  Initializes the `state` atom.
   3.  Triggers the first `reload!` to start the application.
   
   **Arguments:**
   *   `plugins`: A sequence of plugin maps to load."
  [plugins]
  ;; We append the boot plugin to the user provided list
  (let [full-list (conj (vec plugins) plugin)]
    (swap! state assoc :all-plugins full-list)
    (reload!)))
