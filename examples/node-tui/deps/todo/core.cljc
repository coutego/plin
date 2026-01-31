(ns todo.core
  "Core API for the Todo application.
   
   This namespace provides the public interface for the Todo application.
   Platform-specific examples should require this namespace and add their
   own plugins for persistence and UI.")

(defn create-system
  "Creates a base Todo system with common plugins.
   
   Platform-specific examples should call this function and then
   add their own plugins using the returned plugin list.
   
   Returns a vector of plugins including:
   - todo.plugins.persistence/plugin
   - todo.plugins.ui/plugin"
  []
  [(require 'todo.plugins.persistence)
   (require 'todo.plugins.ui)
   @'todo.plugins.persistence/plugin
   @'todo.plugins.ui/plugin])
