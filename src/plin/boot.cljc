(ns plin.boot
  "A minimal, platform-agnostic bootstrapper for Plin applications.
   
   This module is responsible for:
   1. Receiving a list of plugins
   2. Creating the DI container
   3. Extracting and calling the ::boot-fn bean
   
   It knows NOTHING about Reagent, DOM, HTTP servers, etc.
   All platform-specific behavior is delegated to plugins."
  (:require 
   [plin.core :as pi]
   [promesa.core :as p]
   [clojure.string :as str]))

;; --- Helpers ---

(defn- log [& args]
  #?(:clj (apply println args)
     :default (apply js/console.log args)))

(defn- log-error [& args]
  #?(:clj (apply println "ERROR:" args)
     :default (apply js/console.error args)))

(defn- manifest-id->plugin-id
  "Converts a manifest ID (e.g., :plinpt.p-devdoc) to a plugin ID (e.g., :plinpt.p-devdoc/plugin).
   If the ID already ends with /plugin, returns it unchanged."
  [manifest-id]
  (when (keyword? manifest-id)
    (let [ns-part (namespace manifest-id)
          name-part (name manifest-id)]
      (if (and ns-part (= "plugin" name-part))
        ;; Already in plugin ID format (e.g., :plinpt.p-devdoc/plugin)
        manifest-id
        ;; Convert manifest ID to plugin ID format
        ;; :plinpt.p-devdoc -> :plinpt.p-devdoc/plugin
        (keyword name-part "plugin")))))

(defn- normalize-disabled-ids
  "Normalizes a set of disabled IDs from manifest format to plugin ID format."
  [disabled-ids]
  (set (keep manifest-id->plugin-id disabled-ids)))

;; --- State ---
;; Atom holding the system state. Accessible to plugins via ::api bean.

(defonce state 
  (atom {:all-plugins []
         :disabled-ids #{}
         :container nil
         :last-error nil}))

;; --- Logic ---

(defn get-cascading-disabled
  "Calculates the set of effectively disabled plugins based on dependencies."
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

(defn- update-plugin-list [plugins new-plugin]
  (let [target-id (:id new-plugin)
        idx (reduce-kv (fn [_ i p] 
                         (if (= (:id p) target-id) (reduced i) nil)) 
                       nil 
                       plugins)]
    (if idx
      (assoc plugins idx new-plugin)
      (conj plugins new-plugin))))

(defn register-plugin!
  "Registers a new plugin definition (or updates an existing one) and reloads the system.
   Returns a Promise."
  [plugin-def]
  (swap! state update :all-plugins update-plugin-list plugin-def)
  (reload!))

(defn enable-plugin!
  "Enables a plugin by removing it from disabled-ids and reloading.
   Returns a Promise."
  [plugin-id]
  (swap! state update :disabled-ids disj plugin-id)
  (reload!))

(defn disable-plugin!
  "Disables a plugin by adding it to disabled-ids and reloading.
   Returns a Promise."
  [plugin-id]
  (swap! state update :disabled-ids conj plugin-id)
  (reload!))

(defn toggle-plugin!
  "Toggles a plugin's enabled state and reloads.
   Returns a Promise."
  [plugin-id]
  (let [currently-disabled? (contains? (:disabled-ids @state) plugin-id)]
    (if currently-disabled?
      (enable-plugin! plugin-id)
      (disable-plugin! plugin-id))))

;; --- Plugin Definition ---

(def plugin
  "The Bootstrapper Plugin.
   
   Defines the `::boot-fn` bean which should be overridden by platform-specific
   plugins to provide post-bootstrap behavior (e.g., mount UI, start server)."
  (pi/plugin
   {:doc "System Bootstrapper. Manages the plugin lifecycle and system state."
    :deps []
    
    :beans
    {::api
     ^{:doc "System Control API.
             Returns a map with state atom and control functions."
       :api {:ret :map}}
     [(fn [] {:state state
              :reload! reload!
              :register-plugin! register-plugin!
              :enable-plugin! enable-plugin!
              :disable-plugin! disable-plugin!
              :toggle-plugin! toggle-plugin!
              :enable-plugin-no-reload! (fn [plugin-id]
                                          (swap! state update :disabled-ids disj plugin-id)
                                          nil)
              :disable-plugin-no-reload! (fn [plugin-id]
                                           (swap! state update :disabled-ids conj plugin-id)
                                           nil)})]
     
     ::boot-fn
     ^{:doc "Function called after the container is created.
             Receives the container as argument.
             Override this in platform-specific plugins to mount UI, start servers, etc.
             Default: no-op that just logs."
       :api {:args [["container" {} :map]] :ret :any}}
     [:= (fn [_container] 
           (log "Boot complete. No ::boot-fn override provided."))]}}))

;; --- Reload Implementation ---

(defn reload!
  "Reloads the system based on the current state.

   1. Calculates the active plugins (filtering out disabled ones).
   2. Calls `plin.core/load-plugins` to create a new container.
   3. Extracts `::boot-fn` from the container and calls it.
   4. Updates the `state` atom with the new container.

   Returns a Promise that resolves to the state."
  []
  (-> (p/delay 0)
      (p/then (fn [_]
                (log "System: Reloading...")
                (try
                  (let [{:keys [all-plugins disabled-ids]} @state
                        ;; Calculate which plugins to actually load
                        final-disabled (get-cascading-disabled all-plugins disabled-ids)
                        plugins-to-load (filter #(not (contains? final-disabled (:id %))) all-plugins)

                        ;; Create Container
                        container (pi/load-plugins (vec plugins-to-load))

                        ;; Get the boot function from the container
                        boot-fn (::boot-fn container)]

                    ;; Update State
                    (swap! state assoc :container container :last-error nil)

                    ;; Call the boot function with the container
                    (when boot-fn
                      (boot-fn container))

                    (log "System: Reload complete. Active plugins:" (count plugins-to-load))
                    @state)
                  (catch #?(:clj Exception :default :default) e
                    (log-error e)
                    (swap! state assoc :last-error e)
                    (throw e)))))))

;; --- Bootstrap ---

(defn bootstrap! 
  "Bootstraps the system with the given plugins.
   
   Arguments:
   - plugins: Vector of plugin definitions
   - initially-disabled-ids: (Optional) Set of plugin IDs to start disabled
                             These can be in manifest format (e.g., :plinpt.p-devdoc)
                             and will be normalized to plugin ID format (e.g., :plinpt.p-devdoc/plugin)
   
   Adds the boot plugin itself to the list, then calls reload!.
   Returns a Promise that resolves to the state."
  ([plugins]
   (bootstrap! plugins #{}))
  ([plugins initially-disabled-ids]
   (let [full-list (conj (vec plugins) plugin)
         disabled-set (if (set? initially-disabled-ids)
                        initially-disabled-ids
                        (set initially-disabled-ids))
         ;; Normalize the disabled IDs to match plugin ID format
         normalized-disabled (normalize-disabled-ids disabled-set)]
     (swap! state assoc 
            :all-plugins full-list
            :disabled-ids normalized-disabled)
     (reload!))))
