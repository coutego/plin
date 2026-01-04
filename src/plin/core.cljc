(ns plin.core
  "The core namespace for the Plin library, which integrates the `pluggable` and `injectable` libraries
   to provide a robust, data-driven plugin system with dependency injection.

   ## Core Concepts

   *   **Plugin**: A map defining a unit of functionality. It can define **Beans** (internal logic)
       and **Extensions** (hooks for other plugins), and make **Contributions** to other plugins.
   *   **Bean**: A component managed by the dependency injection container. Beans can be values,
       functions, or Reagent components.
   *   **Extension**: A named hook defined by a plugin that allows other plugins to contribute data or logic.
   *   **Contribution**: Data provided by a plugin to satisfy an extension defined by another plugin.

   ## Usage

   Users primarily interact with this namespace via the `plugin` macro to define plugins,
   and the `collect-*` helper functions to define extension handlers."
  (:require [injectable.core :as inj]
            [pluggable.core :as plug]
            [pluggable.root-plugin :as root-plug])
  #?(:cljs (:require-macros [plin.core])))

;; --- Helpers ---

(defn- debug [& args] (println args))

(defn- process-bean-with-api [[k v]]
  (let [m (meta v)
        api (:api m)]
    (if api
      (let [spec (if (map? v) v {:constructor v})
            spec (with-meta spec m)]
        [k (assoc spec :api api)])
      [k v])))

(defn- process-plugin-for-api [plugin-map]
  ;; We no longer process the API here because metadata inheritance 
  ;; needs to happen during the merge phase in the handler.
  plugin-map)

;; --- Dependency Validation ---

(defn- get-defined-keys [plugin-map]
  (let [bean-keys (set (keys (:beans plugin-map)))
        ext-keys (set (map :key (:extensions plugin-map)))]
    (into bean-keys ext-keys)))

(defn validate-dependencies 
  "Validates that a plugin explicitly declares dependencies for any fully-qualified keys it uses.
   
   This function checks keys used in `:beans` and `:contributions`. If a key belongs to another
   namespace (plugin), that plugin must be present in the `:deps` vector.
   
   *   `plugin-map`: The plugin definition map.
   
   Returns the `plugin-map` unchanged if validation passes, or throws an `ex-info` if validation fails."
  [plugin-map]
  ;; We can only validate if we have the resolved maps (runtime/CLJS)
  ;; If deps contains symbols (macro/CLJ), we skip validation.
  (let [deps (:deps plugin-map)]
    (when (and (vector? deps) (every? map? deps))
      (let [;; Implicit dependencies that are always available
            implicit-deps [root-plug/plugin] 
            ;; Note: injectable-plugin is defined in this file. 
            ;; We assume its keys (:beans) are available.
            
            all-deps (concat deps implicit-deps)
            
            ;; Collect all keys defined by dependencies + self
            allowed-keys (reduce (fn [acc dep] (into acc (get-defined-keys dep)))
                                 (get-defined-keys plugin-map) ;; Add self
                                 all-deps)
            
            ;; We also need to allow the :beans extension key itself
            allowed-keys (conj allowed-keys :beans)

            ;; Keys to check: Beans and Contributions
            keys-to-check (concat (keys (:beans plugin-map))
                                  (keys (:contributions plugin-map)))]

        (doseq [k keys-to-check]
          (when (and (keyword? k) (namespace k)) ;; Only check qualified keys
            (when-not (contains? allowed-keys k)
              (throw (ex-info (str "Plugin '" (:id plugin-map) "' uses key '" k
                                   "' but does not depend on a plugin defining it.\n"
                                   "Please add the plugin that defines '" k "' to the :deps vector.")
                              {:plugin-id (:id plugin-map)
                               :missing-key k}))))))))
  plugin-map)

;; --- Re-export Macros ---

#?(:cljs
   (defn plugin 
     "Defines a plugin map.
      
      This function (in CLJS) or macro (in CLJ/CLJC) wraps `pluggable.core/plugin` to provide:
      1.  **Automatic ID Generation**: If `:id` is missing, it is generated from the current namespace
          (e.g., `my.ns` -> `:my.ns/plugin`).
      2.  **Dependency Validation**: Ensures that the plugin explicitly lists dependencies for any
          foreign keys it references in `:beans` or `:contributions`.
      
      **Arguments:**
      *   `plugin-map`: A map describing the plugin. Supported keys:
          *   `:id` (optional): Keyword ID for the plugin.
          *   `:doc` (optional): Documentation string.
          *   `:deps` (optional): Vector of other plugin maps this plugin depends on.
          *   `:beans` (optional): Map of bean definitions `{::bean-key definition}`.
          *   `:contributions` (optional): Map of contributions to other plugins `{::other/extension value}`.
          *   `:extensions` (optional): Vector of extension definitions `[{:key ::my-ext :handler fn ...}]`.
      
      **Returns:**
      *   The processed plugin map."
     [plugin-map]
     (let [ns-str (str *ns*)
           default-id (keyword ns-str "plugin")
           p-with-id (if (:id plugin-map) plugin-map (assoc plugin-map :id default-id))]
       (plug/plugin (process-plugin-for-api (validate-dependencies p-with-id)))))
   :default
   (defmacro plugin
     "Defines a plugin map.
      
      This function (in CLJS) or macro (in CLJ/CLJC) wraps `pluggable.core/plugin` to provide:
      1.  **Automatic ID Generation**: If `:id` is missing, it is generated from the current namespace
          (e.g., `my.ns` -> `:my.ns/plugin`).
      2.  **Dependency Validation**: Ensures that the plugin explicitly lists dependencies for any
          foreign keys it references in `:beans` or `:contributions`.
      
      **Arguments:**
      *   `plugin-map`: A map describing the plugin. Supported keys:
          *   `:id` (optional): Keyword ID for the plugin.
          *   `:doc` (optional): Documentation string.
          *   `:deps` (optional): Vector of other plugin maps this plugin depends on.
          *   `:beans` (optional): Map of bean definitions `{::bean-key definition}`.
          *   `:contributions` (optional): Map of contributions to other plugins `{::other/extension value}`.
          *   `:extensions` (optional): Vector of extension definitions `[{:key ::my-ext :handler fn ...}]`.
      
      **Returns:**
      *   The processed plugin map."
     [plugin-map]
     (let [ns-str (str *ns*)
           default-id (keyword ns-str "plugin")]
       `(let [p# ~plugin-map
              p-with-id# (if (:id p#) p# (assoc p# :id ~default-id))]
          (plug/plugin (validate-dependencies p-with-id#))))))

(defn- merge-beans [existing-beans new-beans]
  (reduce (fn [acc [k v]]
            (let [old-v (get acc k)
                  ;; Merge metadata so implementation inherits from interface
                  merged-meta (merge (meta old-v) (meta v))
                  ;; Merge the map itself to preserve keys like :debug/doc
                  ;; v overrides old-v keys.
                  merged-v (if (and (map? old-v) (map? v))
                             (merge old-v v)
                             v)]
              (assoc acc k (with-meta merged-v merged-meta))))
          existing-beans
          new-beans))

(defn- beans-handler-impl [key db vals]
  (let [existing-beans (get db key {})
        ;; 1. Merge all bean definitions and their metadata
        merged (reduce merge-beans existing-beans vals)
        ;; 2. Re-process every bean to ensure metadata promotion (e.g. :reagent-component)
        ;;    is recalculated based on the merged metadata.
        re-processed (into {} (map #(plug/process-bean-entry % false) merged))
        ;; 3. Process the merged results to extract API metadata into the bean spec
        processed-merged (into {} (map process-bean-with-api re-processed))]
    (assoc db key processed-merged)))

(defn- beans-handler [db vals]
  (beans-handler-impl :beans db vals))

(def injectable-plugin
  "Internal plugin that defines the `:beans` extension point.
   This allows other plugins to define beans that will be loaded into the Injectable container."
  (plug/plugin
   {:id :injectable-plugin
    :extensions
    [{:key :beans
      :doc "Map containing a valid configuration map for Injectable.
           The configuration map may refer to keys that are not defined.
           This is fine, as long as some other plugin defines them."
      :handler beans-handler
      :spec map?}]}))

;; =============================================================================
;; Bean Redefinition Logic (moved from plin.bean-redefs)
;; =============================================================================

(defn- replace-placeholder
  "Recursively replaces a placeholder keyword in a bean specification."
  [spec placeholder replacement]
  (cond
    (vector? spec)
    (mapv (fn [arg] (if (= arg placeholder) replacement arg)) spec)

    (and (map? spec) (:constructor spec))
    (update spec :constructor replace-placeholder placeholder replacement)

    :else
    spec))

(defn- process-single-bean-redef
  "Process a single bean redefinition."
  [current-beans target-key redef-config]
  (let [config (if (and (map? redef-config)
                        (or (:spec redef-config) (:placeholder redef-config)))
                 redef-config
                 {:spec redef-config})
        spec (:spec config)
        placeholder (or (:placeholder config) :orig)
        original-def (get current-beans target-key)]

    (if-not original-def
      (do
        (println "WARNING: Cannot redefine bean" target-key
                 "- original not found. Skipping redefinition.")
        current-beans)

      (let [orig-key (keyword (namespace target-key)
                              (str (name target-key) "-ORIG-" (rand-int 100000)))
            new-spec (replace-placeholder spec placeholder orig-key)]

        (-> current-beans
            (assoc orig-key original-def)
            (assoc target-key new-spec))))))

(defn- get-bean-redefs-from-plugin
  "Extract bean-redefs from a plugin, checking both root level and contributions."
  [plugin]
  (or (get-in plugin [:contributions :bean-redefs])
      (get plugin :bean-redefs)))

(defn- apply-bean-redefs
  "Apply all bean redefinitions from plugins to the beans map."
  [beans plugins]
  (let [all-redefs (keep get-bean-redefs-from-plugin plugins)]
    (reduce (fn [current-beans redefs-map]
              (if (and redefs-map (map? redefs-map))
                (reduce-kv process-single-bean-redef current-beans redefs-map)
                current-beans))
            beans
            all-redefs)))

;; =============================================================================

(defn load-plugins
  "Loads a list of plugins and creates a new Injectable container.
   
   This is the core function for bootstrapping the application. It performs three main steps:
   1.  **Pluggable Phase**: It uses `pluggable.core/load-plugins` to process the plugin list,
       resolve dependencies, and execute extension handlers (including the `:beans` handler).
   2.  **Bean Redefinition Phase**: Processes any bean redefinitions to wrap existing beans.
   3.  **Injectable Phase**: It takes the final bean definitions and uses
       `injectable.core/create-container` to build the dependency injection container.
   
   It also injects a special bean `::definitions` containing the raw bean definitions,
   which is useful for debugging tools.
   
   **Arguments:**
   *   `plugins`: A sequence of plugin maps.
   
   **Returns:**
   *   The constructed Injectable container (a map of instantiated beans)."
  [plugins]
  (let [;; Add injectable-plugin first
        plugins-with-injectable (vec (concat [injectable-plugin] plugins))
        ;; Run pluggable to process extensions and collect beans
        db (plug/load-plugins plugins-with-injectable)
        ;; Apply bean redefinitions after all beans are collected
        beans-with-redefs (apply-bean-redefs (:beans db) plugins)
        ;; Inject the definitions map into the container so tools can inspect it
        beans-with-defs (assoc beans-with-redefs ::definitions [:= beans-with-redefs])
        container (inj/create-container beans-with-defs)]
    container))

(defonce cached-container (atom {}))

(defn push-plugins!
  "Loads the provided plugins, creates a container, and caches it in `cached-container`.
   Useful for hot-reloading or testing scenarios.
   
   **Arguments:**
   *   `plugins`: A sequence of plugin maps.
   
   **Returns:**
   *   The new Injectable container."
  [plugins]
  (let [new-container (load-plugins plugins)]
    (reset! cached-container new-container)
    new-container) )

(defn collect-last
  "Extension Handler Helper.
   
   Returns a handler function that keeps only the **last** value contributed to an extension.
   Useful for 'singleton' extensions where the last plugin to load wins (e.g., a default configuration).
   
   **Arguments:**
   *   `bean-key`: The key in the DB where the result should be stored.
   
   **Returns:**
   *   A handler function `(fn [db vals] ...)`."
  [bean-key]
  (fn [db vals]
    (if (empty? vals)
      db
      (assoc-in db
                [:beans bean-key]
                (last vals)))))

(defn collect-vec [& args] (vec args))

(defn collect-all
  "Extension Handler Helper.
   
   Returns a handler function that collects **all** values contributed to an extension.
   It assumes the contributions are collections (e.g., vectors) and flattens them into a single vector.
   The result is stored as a bean definition that resolves to that vector.
   
   Useful for 'registry' style extensions (e.g., a list of menu items).
   
   **Arguments:**
   *   `bean-key`: The key in the DB where the result should be stored.
   
   **Returns:**
   *   A handler function `(fn [db vals] ...)`."
  [bean-key]
  (fn [db values]
    (let [flat-values (apply concat values)]
      (assoc-in db
                [:beans bean-key :constructor]
                (into [collect-vec] flat-values)))))

(defn collect-data
  "Extension Handler Helper.
   
   Similar to `collect-all`, but stores the result as a **literal value** (`[:= ...]`) 
   instead of a constructor. This is slightly more efficient if the data is static 
   and doesn't need to be built by the container.
   
   **Arguments:**
   *   `bean-key`: The key in the DB where the result should be stored.
   
   **Returns:**
   *   A handler function `(fn [db vals] ...)`."
  [bean-key]
  (fn [db values]
    (let [flat-values (vec (apply concat values))]
      (assoc-in db
                [:beans bean-key]
                [:= flat-values]))))
