(ns injectable.container
  "An IOC container that is able to build a set of beans out of a definition expressed
   as a map."
  (:require [malli.core :as m]
            [malli.error :as me]))

(def bean-def-schema
  [:map
   [:constructor vector?]
   [:mutators {:optional true} [:sequential vector?]]])

(def container-schema
  [:map-of keyword? bean-def-schema])

(defn- validate-bean-def [bean-def]
  (when-let [errors (m/explain bean-def-schema bean-def)]
    (let [human-errors (me/humanize errors)]
      (throw (ex-info "Invalid bean definition"
                     {:errors human-errors})))))

(defn- validate-container [container]
  (when-let [errors (m/explain container-schema container)]
    (let [human-errors (me/humanize errors)]
      (throw (ex-info "Invalid container definition"
                     {:errors human-errors})))))

(defn- bean-constructor [container-def key]
  (-> container-def key :constructor))

(defn- ensure-bean-built [cont key & [parents]]
  (when (some #(= key %) parents)
    (throw (ex-info (str "Circular dependencies: " key " depends on itself through " (rest parents))
                    {:cause "Circular dependencies" :data parents})))
  (cond
    (-> cont key :bean) cont ;; Bean already built
    (not (key cont))    (throw (ex-info (str "No definition found for bean " key)
                                        {:cause "Not bean definition" :data key}))
    :else
    (let [bean-def (key cont)
          [fun & bean-deps] (:constructor bean-def)
          ret (reduce #(ensure-bean-built %1 %2 (conj (or parents []) key))
                      cont
                      bean-deps)
          built-bean-deps (map #(-> ret % :bean)
                               bean-deps)
          
          ;; 1. Build-time error catching
          raw-instance (try
                         (apply fun built-bean-deps)
                         #?(:clj (catch Throwable e
                                   (println "ERROR constructing bean:" key)
                                   (println "Bean definition:" bean-def)
                                   (println "Resolved dependencies:" built-bean-deps)
                                   (throw (ex-info (str "Error constructing bean " key ": " (.getMessage e))
                                                   {:key key
                                                    :bean-def bean-def
                                                    :deps built-bean-deps}
                                                   e))))
                         #?(:cljs (catch :default e
                                    (println "ERROR constructing bean:" key)
                                    (println "Bean definition:" bean-def)
                                    (println "Resolved dependencies:" built-bean-deps)
                                    (let [msg (or (.-message e) (str e))]
                                      (throw (ex-info (str "Error constructing bean " key ": " msg)
                                                      {:key key
                                                       :bean-def bean-def
                                                       :deps built-bean-deps}
                                                      e))))))

          ;; 2. Runtime error catching (if the bean is a function)
          instance (if (fn? raw-instance)
                     (fn [& args]
                       (try
                         (apply raw-instance args)
                         #?(:clj (catch Throwable e
                                   (println "ERROR executing bean:" key)
                                   (println "Arguments count:" (count args))
                                   (throw e)))
                         #?(:cljs (catch :default e
                                    (println "ERROR executing bean:" key)
                                    (println "Arguments count:" (count args))
                                    (throw e)))))
                     raw-instance)]
      
      (assoc-in ret [key :bean] instance))))

(defn- apply-mutator! [cont mut]
  (let [[fun & deps] mut
         ret (reduce ensure-bean-built cont deps)
         built-deps (map #(-> ret % :bean) deps)]
    (apply fun built-deps)))

(defn- apply-mutators-key! [cont key]
  (reduce apply-mutator! cont (-> cont key :mutators)))

(defn- apply-mutators! [cont]
  (doall (for [k (keys cont)] (apply-mutators-key! cont k))) cont)

(defn- build-beans-in-container [cont]
  (-> (reduce ensure-bean-built cont (keys cont))
      (apply-mutators!)))

(defn create
  "Given a container definition (list of beans conforming to :pluggable.ioc-container/container-definition)
   returns a map of constructed beans of the form {key bean}.
   It detects circular dependencies and duplicate bean definitions or constructors"
  [container]
  (validate-container container)
  (doseq [[_ bean-def] container]
    (validate-bean-def bean-def))
  (as-> (build-beans-in-container container) it
    (into {} (for [[k v] it] [k (:bean v)]))))
