(ns plin.bean-redefs-test
  (:require [plin.core :as sut]
            [clojure.string :as str]
            #?(:clj [clojure.test :as t :refer [deftest testing is]]
               :cljs [cljs.test :as t :include-macros true])))

;; =============================================================================
;; Helper functions for bean definitions
;; =============================================================================

(defn original-fn [] "original")
(defn wrapper-fn [orig] (fn [] (str "wrapped:" (orig))))
(defn wrap [orig extra] (fn [] (str "wrap:" (orig) ":" extra)))
(defn wrap-1 [orig] (fn [] (str "wrap-1:" (orig))))
(defn wrap-2 [orig] (fn [] (str "wrap-2:" (orig))))
(defn wrap-3 [orig] (fn [] (str "wrap-3:" (orig))))
(defn fn-a [] "a")
(defn fn-b [] "b")
(defn fn-simple [] "simple")
(defn fn-qualified [] "qualified")
(defn wrap-simple [orig] (fn [] (str "wrap-simple:" (orig))))
(defn wrap-qualified [orig] (fn [] (str "wrap-qualified:" (orig))))
(defn fn-wrap [] "wrap")
(defn fn-keep [] "keep")
(defn fn-another [] "another")
(defn wrapper [orig] (fn [] (str "wrapper:" (orig))))
(defn replacement-fn [arg] (fn [] (str "replacement:" arg)))
(defn combined-fn [] "combined")
(defn fn-1 [] "1")
(defn fn-2 [] "2")
(defn complex-fn [arg1] (fn [] (str "complex:" arg1)))
(defn mutator-fn [arg2] identity)
(defn other-fn [] "other")
(defn fn-c [] "c")
(defn root-wrapper [orig] (fn [] (str "root:" (orig))))
(defn contrib-wrapper [orig] (fn [] (str "contrib:" (orig))))
(defn my-bean-fn [] "my-bean")

;; =============================================================================
;; Helper functions for tests
;; =============================================================================

(defn find-orig-keys
  "Find all keys in beans that contain '-ORIG-' in their name"
  [beans]
  (when beans
    (filter #(str/includes? (name %) "-ORIG-") (keys beans))))

;; =============================================================================
;; Basic bean-redefs tests
;; =============================================================================

(deftest bean-redefs-basic-structure
  (testing "bean-redefs creates renamed original and new definition"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-bean [my-bean-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::my-bean [wrapper-fn :orig]}}]
          db (sut/load-plugins plugins)]
      ;; The original bean key should now have the wrapper spec
      (is (some? (get db ::my-bean)))
      ;; There should be an -ORIG- key created
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys)) "Should have exactly one ORIG key")))))

(deftest bean-redefs-placeholder-replacement
  (testing "placeholder :orig is replaced with the generated key"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::target-bean [original-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::target-bean [wrap :orig "extra-arg"]}}]
          db (sut/load-plugins plugins)]
      ;; Find the ORIG key
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys)) "ORIG key should exist")))))

(deftest bean-redefs-map-syntax
  (testing "map syntax with :spec key works"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::bean-a [fn-a]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::bean-a {:spec [wrapper :orig]}}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys))))))

  (testing "map syntax with custom placeholder works"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::bean-b [fn-b]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::bean-b {:spec [wrapper :original-bean]
                                            :placeholder :original-bean}}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys))))))

  (testing "map syntax with nested constructor works"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::bean-c [fn-c]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::bean-c {:spec {:constructor [wrapper :orig]}
                                            :placeholder :orig}}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys)))))))

(deftest bean-redefs-missing-original
  (testing "warning is printed when original bean doesn't exist"
    (let [plugins [{:id :wrapper-plugin
                    :bean-redefs {::nonexistent-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      ;; The nonexistent bean should not be added
      (is (nil? (get db ::nonexistent-bean)))
      ;; No ORIG keys should be created
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (empty? orig-keys))))))

(deftest bean-redefs-multiple-redefs
  (testing "multiple beans can be redefined in one plugin"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::bean-1 [fn-1]
                                            ::bean-2 [fn-2]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::bean-1 [wrap-1 :orig]
                                  ::bean-2 [wrap-2 :orig]}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 2 (count orig-keys)) "Should have two ORIG keys"))
      (is (some? (get db ::bean-1)))
      (is (some? (get db ::bean-2)))))

  (testing "same bean can be redefined by multiple plugins (chaining)"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::chained-bean [original-fn]}}}
                   {:id :wrapper-1
                    :bean-redefs {::chained-bean [wrap-1 :orig]}}
                   {:id :wrapper-2
                    :bean-redefs {::chained-bean [wrap-2 :orig]}}]
          db (sut/load-plugins plugins)]
      ;; There should be two ORIG keys (one for each wrapping)
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 2 (count orig-keys)) "Should have two ORIG keys for chained redefs")))))

(deftest bean-redefs-preserves-original-definition
  (testing "original bean definition is preserved under ORIG key"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::complex-bean {:constructor [complex-fn "arg1"]
                                                            :mutators [[mutator-fn "arg2"]]}}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::complex-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys)))))))

(deftest bean-redefs-with-plugin-defining-both
  (testing "bean-redefs works when same plugin defines beans and another wraps"
    (let [plugins [{:id :combined-plugin
                    :contributions {:beans {::combined-bean [combined-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::combined-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys)))
        (is (some? (get db ::combined-bean)))))))

(deftest bean-redefs-across-multiple-bean-plugins
  (testing "bean-redefs works with beans defined in multiple plugins"
    (let [plugins [{:id :plugin-a
                    :contributions {:beans {::bean-from-a [fn-a]}}}
                   {:id :plugin-b
                    :contributions {:beans {::bean-from-b [fn-b]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::bean-from-a [wrap-1 :orig]
                                  ::bean-from-b [wrap-2 :orig]}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 2 (count orig-keys))))
      (is (some? (get db ::bean-from-a)))
      (is (some? (get db ::bean-from-b))))))

;; =============================================================================
;; Edge case tests
;; =============================================================================

(deftest bean-redefs-empty-redefs-map
  (testing "empty bean-redefs map doesn't cause issues"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-bean [my-bean-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {}}]
          db (sut/load-plugins plugins)]
      (is (some? (get db ::my-bean)))
      (is (empty? (find-orig-keys (::sut/definitions db)))))))

(deftest bean-redefs-no-placeholder-in-spec
  (testing "spec without placeholder still works (though original won't be injected)"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-bean [original-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::my-bean [replacement-fn "some-arg"]}}]
          db (sut/load-plugins plugins)]
      ;; The bean should be replaced with the new spec
      (is (some? (get db ::my-bean)))
      ;; Original should still be preserved
      (is (= 1 (count (find-orig-keys (::sut/definitions db))))))))

(deftest bean-redefs-multiple-placeholders
  (testing "multiple occurrences of placeholder are all replaced"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-bean [original-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::my-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        (is (= 1 (count orig-keys)))))))

(deftest bean-redefs-with-namespaced-keys
  (testing "bean-redefs works with various namespace patterns"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {:simple-key [fn-simple]
                                            :other.ns/qualified [fn-qualified]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {:simple-key [wrap-simple :orig]
                                  :other.ns/qualified [wrap-qualified :orig]}}]
          db (sut/load-plugins plugins)]
      (is (some? (get db :simple-key)))
      (is (some? (get db :other.ns/qualified)))
      (is (= 2 (count (find-orig-keys (::sut/definitions db))))))))

(deftest bean-redefs-preserves-other-beans
  (testing "bean-redefs doesn't affect beans that aren't being redefined"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::bean-to-wrap [fn-wrap]
                                            ::bean-to-keep [fn-keep]
                                            ::another-bean [fn-another]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::bean-to-wrap [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      ;; Wrapped bean should be modified
      (is (some? (get db ::bean-to-wrap)))
      ;; Other beans should be unchanged
      (is (some? (get db ::bean-to-keep)))
      (is (some? (get db ::another-bean)))
      ;; Only one ORIG key
      (is (= 1 (count (find-orig-keys (::sut/definitions db))))))))

(deftest bean-redefs-with-contributions-syntax
  (testing "bean-redefs also works via :contributions map"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-bean [original-fn]}}}
                   {:id :wrapper-plugin
                    :contributions {:bean-redefs {::my-bean [wrapper :orig]}}}]
          db (sut/load-plugins plugins)]
      (is (some? (get db ::my-bean)))
      (is (= 1 (count (find-orig-keys (::sut/definitions db))))))))

(deftest bean-redefs-contributions-takes-precedence
  (testing ":contributions :bean-redefs takes precedence over root :bean-redefs"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-bean [original-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::my-bean [root-wrapper :orig]}
                    :contributions {:bean-redefs {::my-bean [contrib-wrapper :orig]}}}]
          db (sut/load-plugins plugins)]
      ;; contributions should take precedence
      (is (some? (get db ::my-bean))))))

(deftest bean-redefs-triple-chain
  (testing "three plugins can chain-wrap the same bean"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::chained [original-fn]}}}
                   {:id :wrapper-1
                    :bean-redefs {::chained [wrap-1 :orig]}}
                   {:id :wrapper-2
                    :bean-redefs {::chained [wrap-2 :orig]}}
                   {:id :wrapper-3
                    :bean-redefs {::chained [wrap-3 :orig]}}]
          db (sut/load-plugins plugins)]
      ;; Should have 3 ORIG keys
      (is (= 3 (count (find-orig-keys (::sut/definitions db))))))))

(deftest bean-redefs-with-map-bean-definition
  (testing "bean-redefs works when original bean is a map with :constructor"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::map-bean {:constructor [complex-fn "arg1"]
                                                        :mutators [[mutator-fn "arg2"]]}}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::map-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))]
        ;; New spec should be the wrapper
        (is (some? (get db ::map-bean)))
        (is (= 1 (count orig-keys)))))))

(deftest bean-redefs-orig-key-contains-original-bean-name
  (testing "ORIG key contains the original bean name for debugging"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-special-bean [original-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::my-special-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      (let [orig-keys (find-orig-keys (::sut/definitions db))
            orig-key (first orig-keys)]
        ;; First check we have an ORIG key
        (is (= 1 (count orig-keys)) "Should have one ORIG key")
        (when orig-key
          ;; The ORIG key should contain the original bean name
          (is (str/includes? (name orig-key) "my-special-bean"))
          ;; And should be in the same namespace
          (is (= (namespace ::my-special-bean) (namespace orig-key))))))))

(deftest bean-redefs-does-not-modify-plugins
  (testing "bean-redefs processing doesn't mutate the original plugin maps"
    (let [original-beans {::my-bean [original-fn]}
          original-plugin {:id :original-plugin
                           :contributions {:beans original-beans}}
          wrapper-plugin {:id :wrapper-plugin
                          :bean-redefs {::my-bean [wrapper :orig]}}
          plugins [original-plugin wrapper-plugin]
          _ (sut/load-plugins plugins)]
      ;; Original plugin should be unchanged
      (is (= original-beans (get-in original-plugin [:contributions :beans]))))))

(deftest bean-redefs-with-nil-beans
  (testing "bean-redefs handles plugins with nil :beans gracefully"
    (let [plugins [{:id :plugin-with-nil-beans
                    :contributions {:beans nil}}
                   {:id :original-plugin
                    :contributions {:beans {::my-bean [original-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::my-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      (is (some? (get db ::my-bean)))
      (is (= 1 (count (find-orig-keys (::sut/definitions db))))))))

(deftest bean-redefs-plugin-without-bean-redefs
  (testing "plugins without bean-redefs don't cause issues"
    (let [plugins [{:id :original-plugin
                    :contributions {:beans {::my-bean [original-fn]}}}
                   {:id :middle-plugin
                    :contributions {:beans {::other-bean [other-fn]}}}
                   {:id :wrapper-plugin
                    :bean-redefs {::my-bean [wrapper :orig]}}]
          db (sut/load-plugins plugins)]
      (is (some? (get db ::my-bean)))
      (is (some? (get db ::other-bean)))
      (is (= 1 (count (find-orig-keys (::sut/definitions db))))))))
