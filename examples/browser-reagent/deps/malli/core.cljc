(ns malli.core
  "Malli stubs for Scittle compatibility.
   
   Scittle doesn't support malli, so we provide no-op stubs.
   Schema validation is skipped at runtime in the browser.")

(defn explain
  "No-op explain - always returns nil (valid)."
  [_schema _value]
  nil)
