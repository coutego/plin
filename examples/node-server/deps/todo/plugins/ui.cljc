(ns todo.plugins.ui
  "Extension point for UI implementations.

   This plugin defines the UI interface that all platform-specific UIs
   must provide. Each example will contribute an implementation appropriate
   for its platform (web, TUI, etc.).

   Extension Points:
   - :todo.ui/render-fn - Function to render the UI"
  (:require [plin.core :as pi]))

(defn collect-last
  "Extension handler that keeps only the last contribution.
   Used for selecting a single UI implementation."
  [db vals]
  (if (empty? vals)
    db
    (assoc db :ui/render-fn (last vals))))

(def plugin
  (pi/plugin
   {:id :todo/ui
    :deps []
    :extensions
    [{:key :todo.ui/render-fn
      :doc "UI render function. Contributions should be a function that takes the container and starts the UI."
      :handler collect-last}]}))
