(ns plin.bean-redefs
  "Bean redefinition functionality for the Plin library.
   
   This module handles the wrapping and redefinition of existing beans
   without replacing them entirely. It provides a clean way to extend
   or modify bean behavior through composition."
  (:require [pluggable.core :as plug]))

;; =============================================================================
;; Bean Redefinition Logic
;; =============================================================================

(defn- replace-placeholder
  "Recursively replaces a placeholder keyword in a bean specification.

   Handles both syntaxes:
   - **Vector (Easy Syntax)**: `[my-fn arg1 :orig arg2]`
   - **Map Syntax**: `{:constructor [my-fn :orig]}`

   Parameters:
   - `spec`: The bean specification (vector or map)
   - `placeholder`: The keyword to replace (e.g., `:orig`)
   - `replacement`: The value to substitute (typically the renamed original bean key)

   Returns the spec with all occurrences of placeholder replaced."
  [spec placeholder replacement]
  (cond
    ;; Handle "Easy" Syntax: [fn arg1 :orig]
    (vector? spec)
    (mapv (fn [arg] (if (= arg placeholder) replacement arg)) spec)

    ;; Handle Map Syntax: {:constructor [fn arg1 :orig]}
    (and (map? spec) (:constructor spec))
    (update spec :constructor replace-placeholder placeholder replacement)

    ;; Pass through other values unchanged
    :else
    spec))

(defn- process-single-bean-redef
  "Process a single bean redefinition.

   Parameters:
   - `current-beans`: Current beans map
   - `target-key`: The key of the bean to redefine
   - `redef-config`: The redefinition configuration (vector or map)

   Returns updated beans map."
  [current-beans target-key redef-config]
  (let [;; Normalize config: support both vector and map syntax
        config (if (and (map? redef-config)
                        (or (:spec redef-config) (:placeholder redef-config)))
                 redef-config
                 {:spec redef-config})
        spec (:spec config)
        placeholder (or (:placeholder config) :orig)

        ;; Look up the original bean definition
        original-def (get current-beans target-key)]

    (if-not original-def
      (do
        (println "WARNING: Cannot redefine bean" target-key
                 "- original not found. Skipping redefinition.")
        current-beans)

      (let [;; Generate a unique key for the original bean
            ;; The random suffix prevents collisions if the same bean
            ;; is wrapped multiple times by different plugins
            orig-key (keyword (namespace target-key)
                              (str (name target-key) "-ORIG-" (rand-int 100000)))

            ;; Substitute the placeholder with the unique key
            new-spec (replace-placeholder spec placeholder orig-key)]

        (-> current-beans
            ;; Preserve the original definition under the unique key
            (assoc orig-key original-def)
            ;; Register the wrapper at the original key
            (assoc target-key new-spec))))))

(defn- process-bean-redefs
  "Process bean-redefs from all plugins.

   Parameters:
   - `db`: Database state containing :beans
   - `bean-redefs-list`: List of bean-redefs maps from all plugins

   Returns updated db with bean redefinitions applied."
  [db bean-redefs-list]
  (let [current-beans (get db :beans {})
        ;; Filter out nil values from the list
        valid-redefs (filter some? bean-redefs-list)
        updated-beans (reduce (fn [beans redefs-map]
                                (if (map? redefs-map)
                                  (reduce-kv process-single-bean-redef beans redefs-map)
                                  beans))
                              current-beans
                              valid-redefs)]
    (assoc db :beans updated-beans)))

;; =============================================================================
;; Plugin Definition
;; =============================================================================

(def plugin
  "Plugin that handles bean redefinitions.
   This plugin is automatically loaded last by plin.core/load-plugins
   to ensure all beans are defined before redefinitions are processed."
  (plug/plugin
   {:id :plin/bean-redefs-plugin
    :extensions
    [{:key :bean-redefs
      :doc "Map of bean redefinitions that wrap existing beans.
            Format: {::target-bean-key wrapper-spec}
            The wrapper-spec can use :orig as a placeholder for the original bean."
      :handler process-bean-redefs}]}))
