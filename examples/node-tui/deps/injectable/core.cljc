(ns injectable.core
  "Injectable is a(nother) lightweight IoC container for Clojure(script).
   It is very similar to Integrant in scope. Similar to Integrant, it removes some
   limitations from Component linked to the decision to favour inmutable records.
   Injectable, like Integrant, doesn't impose this limitation, giving a more natural
   injection mechanism and even allowing for circular dependencies.

   Unlike Integrant, the implementation does not depend on multimethods, but in
   plain functions. This is a matter of style and it doesn't prevent the mechanism
   for building the objects to be externalised from the configuration of the system.

   This library takes inspiration in the Spring container. The configurable elements are named
   'beans' in the codebase due to this fact."
  (:require
   [injectable.container :as c]
   [injectable.easy :as e]))

(defn create-container
  "Main entry point to injectable. It takes a container definition and returns a container
   with all its beans built"
  [container]
  (-> container
      (e/compile-container-spec)
      (c/create)))

