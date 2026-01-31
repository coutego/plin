# PLIN Framework Programming Guide

A practical guide for building modular applications with PLIN (Pluggable + Injectable).

## Overview

PLIN is a data-driven plugin architecture for Clojure/ClojureScript that combines two libraries:

- **Pluggable**: A plugin system for defining extension points and aggregating contributions
- **Injectable**: A dependency injection container for wiring components

### Key Principle

Dependencies are passed as arguments rather than imported globally. Functions should be pure - they receive everything they need through their parameter list.

### **CRITICAL: Keep Functions Short**

**Functions must be very short - ideally 5-15 lines, maximum 30 lines.** This is not a suggestion, it is a requirement.

**Why short functions are mandatory:**
- Parentheses balancing errors are nearly impossible in short functions
- Each function does one thing - easier to understand and test
- Function calls in Clojure are extremely cheap - no performance penalty
- Easier to reuse and compose
- Prevents the "pyramid of doom" nesting problem

**When to split a function:**
- If a function has more than 2-3 levels of nesting (`let`, `when`, `if`, `doseq`, etc.)
- If you need to scroll to see the whole function
- If the function does more than one logical operation
- If counting parentheses becomes difficult

**Example - WRONG (too long):**
```clojure
(defn draw-ui [deps state]
  (let [screen (:screen state)
        tasks (:tasks state)]
    (doseq [task tasks]
      (when (:visible task)
        (if (:selected task)
          (do
            (.setColor graphics :highlight)
            (.draw graphics (str "> " (:title task))))
          (do
            (.setColor graphics :normal)
            (.draw graphics (str "  " (:title task))))))))
```

**Example - CORRECT (short functions):**
```clojure
(defn draw-task [graphics task selected?]
  (if selected?
    (draw-selected-task graphics task)
    (draw-normal-task graphics task)))

(defn draw-selected-task [graphics task]
  (.setColor graphics :highlight)
  (.draw graphics (str "> " (:title task))))

(defn draw-normal-task [graphics task]
  (.setColor graphics :normal)
  (.draw graphics (str "  " (:title task))))

(defn draw-ui [deps state]
  (doseq [task (:tasks state)]
    (when (:visible task)
      (draw-task graphics task (:selected task)))))
```

## Architecture

### Two-Phase Boot Process

1. **Pluggable Phase (Wiring)**: Processes plugins, resolves dependencies, and aggregates extensions
2. **Injectable Phase (Instantiation)**: Creates the DI container and injects dependencies

```clojure
(ns my-app.main
  (:require [plin.boot :as boot]
            [my-app.plugins.persistence :as persistence]
            [my-app.plugins.ui :as ui]))

(defn -main []
  (boot/bootstrap!
   [persistence/plugin
    ui/plugin]))
```

## Core Concepts

### Plugin

A Plugin is the atomic unit of modularity. It's a map containing:

| Key | Description |
|-----|-------------|
| `:id` | Unique identifier (auto-generated from namespace if omitted) |
| `:doc` | Documentation string |
| `:deps` | Vector of plugin dependencies (other plugin vars) |
| `:beans` | Map of bean definitions (components to inject) |
| `:extensions` | Hooks that other plugins can contribute to |
| `:contributions` | Data contributed to other plugins' extensions |

### Bean

A Bean is a component managed by the DI container. Beans can be:
- Values (maps, strings, numbers)
- Functions (with dependencies injected)
- Reagent components

### Extension

An Extension is a named hook defined by a plugin that allows other plugins to contribute data or logic.

### Contribution

A Contribution is data provided by a plugin to satisfy an extension defined by another plugin.

## Bean Definition Syntax

The bean definition DSL uses vectors with specific patterns:

### 1. Literal Value: `[value]`

The simplest form - injects the literal value directly.

```clojure
:beans
{::config
 ^{:doc "Configuration map"}
 [{:port 3000 :host "localhost"}]

 ::greeting
 ["Hello, World!"]}
```

### 2. Factory Function: `[fn arg1 arg2]`

Calls `(fn arg1 arg2)` at container build time. Arguments can be:
- Other bean keys (keywords) - resolved from the container
- Literal values - passed as-is

```clojure
(defn make-database [{:keys [host port]}]
  {:host host :port port :connected? true})

:beans
{::db-config [{:host "localhost" :port 5432}]
 ::database
 ^{:doc "Database connection"}
 [make-database ::db-config]}  ; Passes the config map
```

### 3. Partial Application: `[partial fn arg1 arg2]`

Returns `(partial fn arg1 arg2)`. **CRITICAL for Reagent components** - preserves reactivity.

```clojure
(defn task-list-view [load-fn store-fn]
  (let [tasks (load-fn)]  ; Called at render time
    [:div
     [:h2 "Tasks"]
     [:ul
      (for [task tasks]
        [:li {:key (:id task)} (:title task)])]]))

:beans
{::task-list-ui
 ^{:reagent-component true}
 ;; WRONG - loses reactivity:
 ;; [task-list-view ::load-fn ::store-fn]
 ;; This calls task-list-view ONCE at build time

 ;; CORRECT - preserves reactivity:
 [partial task-list-view ::load-fn ::store-fn]}
 ;; This stores a function. When Reagent renders, it calls the function
 ;; and establishes reactive bindings correctly.
```

### 4. Function Reference: `::=>`

Calls a function with the same name as the bean key.

```clojure
:beans
{::load-tasks
 ^{:doc "Function to load tasks"}
 [::=> ::store-atom]}  ; Calls (load-tasks ::store-atom)
```

### 5. Inner Beans: `[:=bean> spec]`

Defines an inline bean that gets its own key.

```clojure
:beans
{::service
 [make-service
  [:=bean> [make-config "dev"]]  ; Creates inner bean
  ::other-dep]}
```

### Argument Resolution Rules

| Argument Type | Example | Resolution |
|---------------|---------|------------|
| Keyword (qualified) | `::other/bean` | Resolved as bean reference from container |
| Keyword (same ns) | `::my-bean` | Resolved as bean reference from container |
| Symbol | `my-value` | Passed as-is (literal) |
| String | `"hello"` | Passed as-is (literal) |
| Number | `42` | Passed as-is (literal) |
| Map/Vector | `{:a 1}` | Passed as-is (literal) |

**Important**: Only keywords are resolved as bean references. Everything else is passed literally.

## Plugin Patterns

### Pattern 1: Interface Plugin

Defines a contract with default no-op implementations.

```clojure
(ns my-app.i-persistence
  (:require [plin.core :as pi]))

(defn make-load-fn [store-atom]
  (fn [] @store-atom))

(defn make-store-fn [store-atom]
  (fn [data] (reset! store-atom data)))

(defn make-store-atom []
  (atom nil))

(def plugin
  (pi/plugin
   {:id :my-app/persistence
    :doc "Persistence interface"

    :beans
    {::store-atom
     ^{:doc "Storage atom"}
     [make-store-atom]

     ::load-fn
     ^{:doc "Load data. Returns map or nil."}
     [make-load-fn ::store-atom]

     ::store-fn
     ^{:doc "Save data. Takes map, returns nil."}
     [make-store-fn ::store-atom]}}))
```

### Pattern 2: Implementation Plugin

Provides concrete implementation by overriding interface beans.

```clojure
(ns my-app.p-local-storage
  (:require [plin.core :as pi]
            [my-app.i-persistence :as i-persistence]))

(defn make-load-fn []
  (fn []
    (-> (js/localStorage.getItem "data")
        (js/JSON.parse)
        (js->clj :keywordize-keys true))))

(defn make-store-fn []
  (fn [data]
    (js/localStorage.setItem "data" (js/JSON.stringify (clj->js data)))))

(def plugin
  (pi/plugin
   {:id :my-app/local-storage
    :doc "LocalStorage implementation of persistence"
    :deps [i-persistence/plugin]  ; Declare dependency!

    :beans
    {;; Override interface beans with concrete implementations
     ::i-persistence/load-fn
     ^{:doc "Load from localStorage"}
     [make-load-fn]

     ::i-persistence/store-fn
     ^{:doc "Save to localStorage"}
     [make-store-fn]}}))
```

### Pattern 3: Consumer Plugin

Depends on interface, not implementation.

```clojure
(ns my-app.p-dashboard
  (:require [plin.core :as pi]
            [my-app.i-persistence :as i-persistence]))

(defn dashboard-view [load-fn store-fn]
  (let [data (load-fn)]
    [:div.dashboard
     [:h1 "Dashboard"]
     [:p (str "Items: " (count data))]]))

;; WRONG - don't do this:
;; (:require [my-app.p-local-storage :as storage])
;; This couples you to a specific implementation

;; CORRECT - depend only on interface:
(def plugin
  (pi/plugin
   {:id :my-app/dashboard
    :doc "Dashboard component"
    :deps [i-persistence/plugin]  ; Only declare interface dependency

    :beans
    {::ui
     ^{:reagent-component true}
     [partial dashboard-view
      ::i-persistence/load-fn    ; Inject from interface
      ::i-persistence/store-fn]}}))
```

## Extensions and Contributions

### Defining an Extension

```clojure
(def plugin
  (pi/plugin
   {:id :my-app/menu
    :extensions
    [{:key ::menu-items
      :doc "Registry for menu items"
      :handler (pi/collect-all ::menu-items)}]  ; Collects all contributions

    :beans
    {::menu-data
     [[]]}}))  ; Default empty, will be populated
```

### Contributing to an Extension

```clojure
(def plugin
  (pi/plugin
   {:id :my-app/dashboard-module
    :deps [:my-app/menu]

    :contributions
    {::menu/items [{:label "Dashboard" :route "/dashboard"}]
     ::menu/items [{:label "Settings" :route "/settings"}]}}))
```

### Extension Handlers

| Handler | Behavior | Use Case |
|---------|----------|----------|
| `(pi/collect-all key)` | Flattens all contributions into vector | Menu items, routes, listeners |
| `(pi/collect-last key)` | Keeps only last contribution | Default config, singleton |
| `(pi/collect-data key)` | Like collect-all but as literal value | Static data |

## Reagent/React Integration

### Critical Pattern: Use `partial` for Components

Reagent components need to be functions called at render time, not build time.

```clojure
(defn todo-app [app-state load-fn store-fn]
  (let [tasks (:tasks @app-state)]
    [:div
     [:h1 "Todo List"]
     [task-list tasks]
     [add-task-form store-fn]]))

(def plugin
  (pi/plugin
   {:id :my-app/ui
    :deps [:my-app/state :my-app/persistence]

    :beans
    {::app
     ^{:reagent-component true
       :doc "Main application component"}
     ;; CORRECT: partial defers execution until render
     [partial todo-app ::app-state ::load-fn ::store-fn]

     ;; WRONG: would execute once at build time, losing reactivity
     ;; [todo-app ::app-state ::load-fn ::store-fn]
     }}))
```

### Accessing Container in Components

The container definitions are available as `::definitions` for debugging:

```clojure
(defn debug-panel [definitions]
  [:div.debug
   [:h3 "Beans:"]
   [:pre (pr-str (keys definitions))]])

(def plugin
  (pi/plugin
   {:beans
    {::debug
     ^{:reagent-component true}
     [partial debug-panel ::pi/definitions]}}))
```

## Bootstrap and Lifecycle

### Using `plin.boot/bootstrap!`

```clojure
(ns my-app.main
  (:require [plin.boot :as boot]
            [my-app.plugins.domain :as domain]
            [my-app.plugins.persistence :as persistence]
            [my-app.plugins.ui :as ui]))

(defn -main [& args]
  (let [plugins [;; Order by dependency layers
                 domain/plugin      ; No deps - foundation
                 persistence/plugin ; Depends on domain
                 ui/plugin]]        ; Depends on persistence

    ;; Bootstrap creates container and calls ::boot-fn
    (boot/bootstrap! plugins)))
```

### The `::boot-fn` Bean

Platform plugins should override `::boot-fn` to start their platform:

```clojure
;; In a browser plugin:
(defn mount-reagent-app [container]
  (let [root-component (::app container)]
    (r/render [root-component] (.getElementById js/document "app"))))

(def plugin
  (pi/plugin
   {:id :my-app/browser
    :deps [my-app.ui/plugin]

    :beans
    {::boot-fn
     ^{:doc "Mount the Reagent app to DOM"}
     [mount-reagent-app]}}))  ; Function that receives container

;; In a server plugin:
(defn start-server [handler port]
  (jetty/run-jetty handler {:port port}))

(def plugin
  (pi/plugin
   {:id :my-app/server
    :beans
    {::boot-fn
     [partial start-server ::http-handler 3000]}}))
```

### Plugin Management at Runtime

The boot system provides functions to enable/disable plugins dynamically:

```clojure
;; Get the system API
(def api (::boot/api container))

;; Disable a plugin
(boot/disable-plugin! :my-app/ui/plugin)

;; Enable a plugin
(boot/enable-plugin! :my-app/ui/plugin)

;; Toggle a plugin
(boot/toggle-plugin! :my-app/ui/plugin)

;; Register a new plugin
(boot/register-plugin! new-plugin)
```

## Testing

### Testing with Explicit Dependencies

Since dependencies are explicit, testing requires no mocking framework:

```clojure
;; Function under test
(defn add-task-handler [load-fn store-fn request]
  (let [title (get-in request [:params :title])
        task-list (load-fn)
        new-list (domain/add-task task-list title)]
    (store-fn new-list)
    {:status 302 :headers {"Location" "/"}}))

;; Test
(deftest test-add-task-handler
  (let [stored-value (atom nil)
        mock-load (fn [] {:tasks {} :next-id 1})
        mock-store (fn [v] (reset! stored-value v))
        request {:params {:title "Test Task"}}]

    ;; Just call with mock functions
    (add-task-handler mock-load mock-store request)

    ;; Verify
    (is (= "Test Task" (get-in @stored-value [:tasks 1 :title])))))
```

### Testing with Test Containers

Create a minimal container for integration tests:

```clojure
(deftest test-with-container
  (let [test-plugins [domain/plugin
                      persistence/plugin  ; In-memory default
                      test-utils/plugin]  ; Test overrides
        container (pi/load-plugins test-plugins)]

    ;; Use container beans directly
    (let [store-fn (::persistence/store-fn container)
          load-fn (::persistence/load-fn container)]
      (store-fn {:tasks {1 {:title "Test"}} :next-id 2})
      (is (= "Test" (get-in (load-fn) [:tasks 1 :title]))))))
```

## Dependency Validation

PLIN validates that plugins declare dependencies for all foreign keys they use.

### Correct Pattern

```clojure
(ns my-app.feature
  (:require [plin.core :as pi]
            [other-app.interface :as other]))

(def plugin
  (pi/plugin
   {:id :my-app/feature
    :deps [other/plugin]  ; MUST declare dependency!

    :beans
    {::my-bean
     ;; Can use ::other/some-bean because other/plugin is in :deps
     [my-fn ::other/some-bean]}}))
```

### Error You'll See

If you forget to add to `:deps`:

```
Plugin ':my-app/feature' uses key ':other/some-bean' but does not depend
on a plugin defining it.
Please add the plugin that defines ':other/some-bean' to the :deps vector.
```

## Best Practices

### 1. Naming Conventions

- Interface plugins: `i-feature` or `:my-app/feature-interface`
- Implementation plugins: `p-feature-impl` or `:my-app/feature-impl`
- Use fully qualified keywords: `::my-bean` expands to `:current.ns/my-bean`

### 2. File Organization

```
src/
├── my_app/
│   ├── i_persistence.clj       ; Interface
│   ├── p_local_storage.clj     ; Implementation 1
│   ├── p_database.clj          ; Implementation 2
│   └── main.clj                ; Bootstrap
```

### 3. No Cross-Implementation Imports

**WRONG:**
```clojure
(ns my-app.dashboard
  (:require [my-app.p-local-storage :as storage]))
```

**CORRECT:**
```clojure
(ns my-app.dashboard
  (:require [my-app.i-persistence :as i-persistence]))
```

### 4. Prefer Factory Functions

**WRONG:**
```clojure
(defonce global-state (atom {}))  ; Hidden global state

(defn get-state []
  @global-state)

:beans
{::state [global-state]}  ; Exposing global atom
```

**CORRECT:**
```clojure
(defn make-state []  ; Factory creates fresh instance
  (atom {}))

:beans
{::state [make-state]}  ; Each container gets its own atom
```

### 5. Document Your Beans

Use metadata to document beans:

```clojure
::load-fn
^{:doc "Load tasks from storage. Returns TaskList record."
  :api {:ret :map}}  ; Optional: Malli schema
[make-load-fn ::store-atom]
```

### 6. Use Bean Metadata

Mark Reagent components with `:reagent-component` metadata:

```clojure
::my-ui
^{:reagent-component true
  :doc "My component"}
[partial my-component ::dep]
```

## Common Pitfalls

### Pitfall 1: Losing Reactivity

**Problem:** Component doesn't re-render when state changes.

**Cause:** Used `[my-fn deps]` instead of `[partial my-fn deps]`

**Solution:** Always use `partial` for Reagent components.

### Pitfall 2: "Bean not found" Errors

**Problem:** Container throws "No definition found for bean :x/y"

**Cause:** Forgot to include the plugin that defines :x/y in the plugins vector.

**Solution:** Check that all dependencies are in the bootstrap plugins vector.

### Pitfall 3: Dependency Validation Failure

**Problem:** "Plugin uses key but does not depend on a plugin defining it"

**Cause:** Using a foreign key without declaring the plugin in `:deps`

**Solution:** Add the plugin that defines the key to `:deps`.

### Pitfall 4: Circular Dependencies

**Problem:** "Circular dependencies: :x/a depends on itself"

**Cause:** Bean A depends on B, B depends on C, C depends on A.

**Solution:** Refactor to use an interface/implementation split, or introduce an intermediary.

### Pitfall 5: Arguments Not Being Injected

**Problem:** Bean receives keyword instead of resolved value.

**Cause:** Arguments must be keywords to be resolved. Symbols are passed literally.

**Example:**
```clojure
;; WRONG - 'config' is a symbol, passed literally
[make-db config]

;; CORRECT - ::config is a keyword, resolved from container
[make-db ::config]
```

### Pitfall 6: Using Wrong Bean Syntax

**Problem:** Bean value not injected correctly.

**Cause:** Incorrect use of vector syntax.

**Example:**
```clojure
;; WRONG - using := which doesn't exist
[:= {:key "value"}]

;; CORRECT - just use the value directly
[{:key "value"}]

;; Or for functions:
[make-fn ::arg1 "literal"]
```

## Complete Example

```clojure
;; ===== i_state.clj =====
(ns todo.i-state
  (:require [plin.core :as pi]))

(defn make-app-state []
  (atom {:tasks []}))

(def plugin
  (pi/plugin
   {:id :todo/state
    :beans
    {::app-state
     ^{:doc "Application state atom"}
     [make-app-state]}}))

;; ===== i_persistence.clj =====
(ns todo.i-persistence
  (:require [plin.core :as pi]))

(defn make-store-atom []
  (atom nil))

(defn make-load-fn [store-atom create-task-list]
  (fn []
    (or @store-atom (create-task-list))))

(defn make-store-fn [store-atom]
  (fn [task-list]
    (reset! store-atom task-list)))

(def plugin
  (pi/plugin
   {:id :todo/persistence
    :beans
    {::store-atom
     [make-store-atom]

     ::load-fn
     [make-load-fn ::store-atom :todo.plugins.domain/create-task-list]

     ::store-fn
     [make-store-fn ::store-atom]}}))

;; ===== p_memory_store.clj =====
(ns todo.p-memory-store
  (:require [plin.core :as pi]
            [todo.i-state :as i-state]
            [todo.i-persistence :as i-persistence]))

;; Override with in-memory implementation using shared state
(def plugin
  (pi/plugin
   {:id :todo/memory-store
    :deps [:todo/state :todo/persistence]

    :beans
    {::i-persistence/load-fn
     [i-persistence/make-load-fn ::i-state/app-state :todo.plugins.domain/create-task-list]

     ::i-persistence/store-fn
     [i-persistence/make-store-fn ::i-state/app-state]}}))

;; ===== p_ui.clj =====
(ns todo.p-ui
  (:require [plin.core :as pi]
            [reagent.core :as r]
            [todo.i-state :as i-state]
            [todo.i-persistence :as i-persistence]))

(defn task-list [app-state load-tasks]
  (let [tasks (load-tasks)]
    [:div
     [:h2 "Tasks"]
     [:ul
      (for [task tasks]
        [:li {:key (:id task)} (:title task)])]]))

(def plugin
  (pi/plugin
   {:id :todo/ui
    :deps [:todo/state :todo/persistence]
    :beans
    {::task-list-component
     ^{:reagent-component true}
     [partial task-list ::i-state/app-state ::i-persistence/load-fn]}}))

;; ===== main.clj =====
(ns todo.main
  (:require [plin.boot :as boot]
            [todo.i-state :as state]
            [todo.i-persistence :as persistence]
            [todo.p-memory-store :as memory-store]
            [todo.p-ui :as ui]))

(defn -main []
  (boot/bootstrap!
   [state/plugin
    persistence/plugin
    memory-store/plugin
    ui/plugin]))
```

## Summary

1. **Use `partial` for React/Reagent components** - preserves reactivity
2. **Define interfaces first** - use factories with atoms for state
3. **Implement by overriding** - depend on interface, override its beans
4. **Only depend on interfaces** - never on implementations
5. **Declare all `:deps`** - validation will catch missing ones
6. **Pass dependencies as arguments** - pure functions are testable
7. **Use factories, not globals** - each container gets fresh instances
8. **Bootstrap with layers** - domain → interfaces → implementations → UI

This architecture enables true modularity: you can swap implementations, test in isolation, and reason about your system by looking at explicit dependency graphs.
