(ns injectable.easy
  "Easy way to express injectable configurations"
  (:require [clojure.string :as str])
  #?(:clj (:import [clojure.lang ArityException])))

(def reserved-keywords
  #{:=        ; Literal: the contents of this vector will be injected as is
    :=>       ; Function: the function with the same name as the key will be called
    :=bean>}) ; Inner bean

(defn reserved-keyword? [k]
  (= \= (second (str k))))

(defn proj
  "'Projects' the list of args by taking the given indexes in the given order"
  [indexes args]
  (reduce (fn [acc i] (conj acc (nth args i))) [] indexes))

(defn index-of
  "Finds the index of the value 'val' in the collection coll, returning
   nil if the element is not contained in the collection"
  [coll val]
  (let [ind (reduce
             (fn [acc n] (if (= val n) (reduced acc) (inc acc)))
             0
             coll)]
    (if (= (count coll) ind) nil ind)))

(defn invert-permutation [permv]
  (vec (map-indexed (fn [i v] (index-of permv i)) permv)))

(defn- permuted-args [permv args]
  (when (or (not (coll? permv))
            (not (coll? args))
            (not= (count permv) (count args)))
    (throw (ex-info "permuted-args: arguments need to be collections of the same size" {})))
  (let [args (vec args)]
    (loop [i   0
           acc []]
      (if (< i (count args))
        (recur (inc i) (conj acc (nth args (nth permv i))))
        acc))))

(defn permuted-fn [f permv]
  (let [permv (invert-permutation permv)]
    (fn [& args]
      (when (not= (count args)
                  (count permv))
        #?(:clj (throw (ArityException.
                        (count args)
                        (str
                         "permuted-fn--anon-fn ("
                         "args: " args
                         "permv: " permv
                         ")")))
           :cljs (throw (ex-info "permuted-fn: arity exception" {}))))
      (apply f (permuted-args permv args)))))

(defn- spec-args-perms-reducer [acc narg]
  (let [{:keys [syms vars kws index]} acc]
    (->
     (cond
       (keyword? narg)
       (update acc :kws conj index)

       (or (= '? narg)
           (and (symbol? narg) (= \? (first (str narg)))))
       (update acc :vars conj index)

       :else
       (update acc :syms conj index))
     (update :index inc))))

(defn spec-args-perms [spec-args]
  (reduce spec-args-perms-reducer
          {:syms  []
           :vars  []
           :kws   []
           :index 0}
          spec-args))

(defn process-spec-head [f-or-kw]
  (cond
    (fn? f-or-kw)      [f-or-kw]
    (keyword? f-or-kw) [(fn [kf & args] (apply kf args)) f-or-kw]
    :default
    (throw (ex-info
            (str
             "Bean definition must start with a function or a reference "
             "to a bean containing a function") {}))))

(defn subs-bean-refs-in-funtion-pos [[f & args]]
  (let [head (process-spec-head f)]
    (into head args)))

(defn process-possible-:=> [spec k]
  (if (= :=> (first spec))
    (let [r (rest spec)
          h (->> k str (drop 1) str/join symbol)]
      (into [h] r))
    spec))

(defn process-possible-:= [spec]
  (if (= := (first spec))
    [(fn [] (second spec))]
    spec))

(defn compile-fn-spec [spec & [bean-key]]
  (let [spec (process-possible-:=> spec bean-key)
        spec (process-possible-:= spec)
        [f & args] (subs-bean-refs-in-funtion-pos spec)
        {:keys
         [syms
          kws
          vars]}   (spec-args-perms args)
        permv      (vec (concat syms kws vars))
        g          (if (or (= 0 (count permv))
                           (= permv (sort permv)))
                     f
                     (permuted-fn f permv))
        gsyms      (if (= 0 (count syms))
                     g
                     (apply partial g (proj syms args)))
        gkws       (if (= 0 (count kws))
                     (if (= 0 (count vars))
                       gsyms
                       (fn [] gsyms))
                     (fn [& kargs]
                       (when (not= (count kargs) (count kws))
                         #?(:clj (throw (ArityException.
                                         (count kargs)
                                         (str "compile-fn-spec: in bean '" bean-key "'")))
                            :cljs (throw (ex-info "compile-fn-spec: wrong arity" {}))))
                       (if (= 0 (count vars))
                         (apply gsyms kargs)
                         (apply partial gsyms kargs))))]
    (into [gkws] (proj kws args))))

(defn- normalize-bean-spec [spec]
  (cond
    (keyword? spec) {:constructor [identity spec]}
    (vector? spec) {:constructor spec}
    (map? spec)    spec
    :else          {:constructor [(fn [] spec)]}))

(defn- compile-bean-spec [k spec]
  (as-> spec it
    (update it :constructor (fn [c] (compile-fn-spec c k)))
    (if (:mutators it)
      (update it :mutators (fn [muts] (map #(compile-fn-spec % k) muts)))
      it)))

(defn- normalize-container-spec [spec]
  (into
   {}
   (for [[k v] spec]
     (do
       (when (reserved-keyword? k)
         (throw (ex-info
                 (str "Keyword '" k "' is not a valid configuration key:  "
                      "keys starting with ':=' are reserved.")
                 {})))
       {k (normalize-bean-spec v)}))))

;;
;; Inner beans
;;
(defn create-inner-bean [k v i]
  (def rdbg-create-inner-bean [k v i])
  (let [key   (keyword (str ":=inner-bean-" k "--" i))]
    {:key key :spec (normalize-bean-spec v)}))

(defn handle-inner-beans-spec-element [k el i]
  (if (and (vector? el) (= :=bean> (first el)))
    (let [ib (create-inner-bean k (second el) i)]
      {:new-element (:key ib)
       :extra-beans [{(:key ib) (normalize-bean-spec (:spec ib))}]}) ;; FIXME
    {:new-element el
     :extra-beans []}))

(defn handle-inner-beans-spec [k fspec]
  (as-> (map-indexed (fn [i el] (handle-inner-beans-spec-element k el i)) fspec) it
      (reduce (fn [acc n]
                (-> acc
                    (update :spec conj (:new-element n))
                    (update :extra-beans #(vec (concat % (:extra-beans n))))))
              {:spec [] :extra-beans []}
              it)))

(defn create-inner-beans-maybe [k v]
  (let [const       (:constructor v)
        res         (handle-inner-beans-spec k const)
        new-const   (:spec res)
        new-beans   (:extra-beans res)
        mutators    (:mutators v)
        new-spec    {:constructor new-const}
        new-spec    (if-not (= 0 (count mutators))
                      (assoc new-spec :mutators mutators)
                      new-spec)]
    (concat [[k new-spec]] new-beans)))

(defn- handle-inner-beans-impl [cspec]
  (->> (for [[k v] cspec] (create-inner-beans-maybe k v))
       (reduce concat)
       (into {})))

(defn handle-inner-beans [cspec]
  (let [nspec (handle-inner-beans-impl cspec)]
    (if (= nspec cspec)
      nspec
      (recur nspec))))

;;
;; Main entry point
;;
(defn compile-container-spec [spec]
  (as-> spec it
    (normalize-container-spec it)
    (handle-inner-beans it)
    (into {} (for [[k v] it] {k (compile-bean-spec k v)}))))
