;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

; Author: Stuart Halloway

(ns clojure.test-clojure.protocols
  (:use clojure.test clojure.test-clojure.protocols.examples)
  (:require [clojure.test-clojure.protocols.more-examples :as other]
            [clojure.set :as set]
            [clojure.string :as str]
            clojure.test-helper)
  (:import [clojure.test-clojure.protocols.examples
            ExampleProtocol
            MarkerProtocol
            MarkerProtocol2]))

(defn delete-module [module]
  (doto module
    code/purge.e
    code/delete.e
    (-> code/which.e file/delete.e)))

(defn find-protocol-impls
  [protocol]
  (let [type->str #(subs (str %) 1)
        prefix (type->str protocol)
        f (fn [[module path]]
            (str/starts-with? (type->str module) prefix))]
    (->> (code/all_loaded.e)
         (filter f)
         (map first))))

(defn clean-protocol [protocol]
  (doseq [m (find-protocol-impls protocol)]
    (delete-module m)))

;; temporary hack until I decide how to cleanly reload protocol
;; this no longer works
(defn reload-example-protocols
  []
  ;; (clean-protocol ExampleProtocol)
  ;; (clean-protocol other/SimpleProtocol)

  ;; (alter-var-root #'clojure.test-clojure.protocols.examples/ExampleProtocol
  ;;                 assoc :impls {})
  ;; (alter-var-root #'clojure.test-clojure.protocols.more-examples/SimpleProtocol
  ;;                 assoc :impls {})

  #_(require :reload
           'clojure.test-clojure.protocols.examples
           'clojure.test-clojure.protocols.more-examples))

(defn method-names
  "return sorted list of method names on a module"
  [m]
  (->> (erlang/get_module_info.e m :functions)
       (map (comp str first))
       (map #(subs % 1))
       (filter (comp not #{"$_clj_on_load" "module_info" "module_info"}))
       (filter #(not (str/ends-with? % "__val")))
       sort))

(defrecord EmptyRecord [])
(defrecord TestRecord [a b])
(defn r
  ([a b] (new TestRecord a b))
  ([a b meta ext] (new TestRecord a b meta ext)))

(defrecord MapEntry [k v])

(defn subset-map? [a b]
  (set/subset? (set a) (set b)))

(deftest protocols-test
  (testing "protocol fns have useful metadata"
    (let [common-meta {:ns 'clojure.test-clojure.protocols.examples
                       :protocol 'clojure.test-clojure.protocols.examples.ExampleProtocol}]
      (are [m f] (subset-map? (merge (quote m) common-meta)
                              (meta (var f)))
           {:name foo :arglists ([a]) :doc "method with one arg"} foo
           {:name bar :arglists ([a b]) :doc "method with two args"} bar
           {:name baz :arglists ([a] [a b]) :doc "method with multiple arities" :tag String} baz
           {:name with-quux :arglists ([a]) :doc "method name with a hyphen"} with-quux)))
  (testing "protocol fns throw IllegalArgumentException if no impl matches"
    (is (thrown-with-msg?
          :error
          #"No implementation of method: :foo of protocol: :clojure.test-clojure.protocols.examples.ExampleProtocol found for type: :clojerl.Integer"
          (foo 10))))
  (testing "protocols generate a corresponding interface using _ instead of - for method names"
    (is (= ["bar" "baz" "baz" "foo" "with-quux"] (method-names :clojure.test-clojure.protocols.examples))))
  (testing "error conditions checked when defining protocols"
    (is (thrown-with-msg?
         :throw
         #"Definition of function m in protocol badprotdef must take at least one arg."
         (eval '(defprotocol badprotdef (m [])))))
    (is (thrown-with-msg?
         :throw
         #"Function m in protocol badprotdef was redefined. Specify all arities in single definition."
         (eval '(defprotocol badprotdef (m [this arg]) (m [this arg1 arg2]))))))
  (testing "you can redefine a protocol with different methods"
    (eval '(defprotocol Elusive (old-method [x])))
    (eval '(defprotocol Elusive (new-method [x])))))

(deftype HasMarkers []
  ExampleProtocol
  (foo [this] "foo")
  MarkerProtocol
  MarkerProtocol2)

(deftype WillGetMarker []
  ExampleProtocol
  (foo [this] "foo"))

(extend-type WillGetMarker MarkerProtocol)

(deftest marker-tests
  (testing "That a marker protocol has no methods"
    (is (= '() (method-names clojure.test-clojure.protocols.examples.MarkerProtocol))))
  (testing "That types with markers are reportedly satifying them."
    (let [hm (new HasMarkers)
          wgm (new WillGetMarker)]
      (is (satisfies? MarkerProtocol hm))
      (is (satisfies? MarkerProtocol2 hm))
      (is (satisfies? MarkerProtocol wgm)))))

(deftype ExtendTestWidget [name])
(deftype HasProtocolInline []
  ExampleProtocol
  (foo [this] :inline))

(deftest record-marker-interfaces
  (testing "record? and type? return expected result for IRecord and IType"
    (let [r (new TestRecord 1 2)]
      (is (record? r)))))

(deftype ExtendsTestWidget []
  ExampleProtocol)
(deftest extends?-test
  (reload-example-protocols)
  (testing "returns false if a type does not implement the protocol at all"
    (is (false? (extends? other/SimpleProtocol ExtendsTestWidget))))
  (testing "returns true if a type implements the protocol directly" ;; semantics changed 4/15/2010
    (is (true? (extends? ExampleProtocol ExtendsTestWidget)))))

(deftype ExtendersTestWidget [])
#_(deftest extenders-test
  (reload-example-protocols)
  (testing "a fresh protocol has no extenders"
    (is (nil? (extenders ExampleProtocol))))
  (testing "extending with no methods doesn't count!"
    (deftype Something [])
    (extend ::Something ExampleProtocol)
    (is (nil? (extenders ExampleProtocol))))
  (testing "extending a protocol (and including an impl) adds an entry to extenders"
    (extend ExtendersTestWidget ExampleProtocol {:foo identity})
    (is (= [ExtendersTestWidget] (extenders ExampleProtocol)))))

(deftype SatisfiesTestWidget []
  ExampleProtocol)
(deftest satisifies?-test
  (reload-example-protocols)
  (let [whatzit (new SatisfiesTestWidget)]
    (testing "returns false if a type does not implement the protocol at all"
      (is (false? (satisfies? other/SimpleProtocol whatzit))))
    (testing "returns true if a type implements the protocol directly"
      (is (true? (satisfies? ExampleProtocol whatzit))))
    (testing "returns true if a type explicitly extends protocol"
      (extend-type SatisfiesTestWidget
        other/SimpleProtocol
        (foo [this] this))
      (is (true? (satisfies? other/SimpleProtocol whatzit))))))

(deftype ReExtendingTestWidget [])
(deftest re-extending-test
  (reload-example-protocols)
  (extend-type ReExtendingTestWidget
    ExampleProtocol
    (foo [_] "first foo")
    (baz [_] "first baz"))
  (testing "if you re-extend, the old implementation is replaced (not merged!)"
    (extend-type ReExtendingTestWidget
      ExampleProtocol
      (baz [_] "second baz")
      (bar [_ _] "second bar"))
    (let [whatzit (new ReExtendingTestWidget)]
      (is (thrown? :error (foo whatzit)))
      (is (= "second bar" (bar whatzit nil)))
      (is (= "second baz" (baz whatzit))))))

(defrecord DefrecordObjectMethodsWidgetA [a])
(defrecord DefrecordObjectMethodsWidgetB [a])
(deftest defrecord-object-methods-test
  (testing "= depends on fields and type"
    (is (true? (= (new DefrecordObjectMethodsWidgetA 1)
                  (new DefrecordObjectMethodsWidgetA 1))))
    (is (false? (= (new DefrecordObjectMethodsWidgetA 1)
                   (new DefrecordObjectMethodsWidgetA 2))))
    (is (false? (= (new DefrecordObjectMethodsWidgetA 1)
                   (new DefrecordObjectMethodsWidgetB 1))))))

(deftest defrecord-acts-like-a-map
  (let [rec (r 1 2)]
    (is (= (r 1 3 {} {:c 4}) (merge rec {:b 3 :c 4})))
    (is (= {:foo 1 :b 2} (set/rename-keys rec {:a :foo})))
    (is (= {:a 11 :b 2 :c 10} (merge-with + rec {:a 10 :c 10})))))

(deftest degenerate-defrecord-test
  (let [empty (new EmptyRecord)]
    (is (nil? (seq empty)))
    #_(is (not (.containsValue empty :a)))))

;; (deftest defrecord-interfaces-test
;;   (testing "java.util.Map"
;;     (let [rec (r 1 2)]
;;       (is (= 2 (.size rec)))
;;       (is (= 3 (.size (assoc rec :c 3))))
;;       (is (not (.isEmpty rec)))
;;       (is (.isEmpty (EmptyRecord.)))
;;       (is (.containsKey rec :a))
;;       (is (not (.containsKey rec :c)))
;;       (is (.containsValue rec 1))
;;       (is (not (.containsValue rec 3)))
;;       (is (= 1 (.get rec :a)))
;;       (is (thrown? UnsupportedOperationException (.put rec :a 1)))
;;       (is (thrown? UnsupportedOperationException (.remove rec :a)))
;;       (is (thrown? UnsupportedOperationException (.putAll rec {})))
;;       (is (thrown? UnsupportedOperationException (.clear rec)))
;;       (is (= #{:a :b} (.keySet rec)))
;;       (is (= #{1 2} (set (.values rec))))
;;       (is (= #{[:a 1] [:b 2]} (.entrySet rec)))

;;       ))
;;   (testing "IPersistentCollection"
;;     (testing ".cons"
;;       (let [rec (r 1 2)]
;;         (are [x] (= rec (.cons rec x))
;;              nil {})
;;         (is (= (r 1 3) (.cons rec {:b 3})))
;;         (is (= (r 1 4) (.cons rec [:b 4])))
;;         (is (= (r 1 5) (.cons rec (MapEntry. :b 5))))))))

;; ;; (defrecord RecordWithSpecificFieldNames [this that k m o])
;; (deftest defrecord-with-specific-field-names
;;   (let [rec (new RecordWithSpecificFieldNames 1 2 3 4 5)]
;;     (is (= rec rec))
;;     (is (= 1 (:this (with-meta rec {:foo :bar}))))
;;     (is (= 3 (get rec :k)))
;;     (is (= (seq rec) '([:this 1] [:that 2] [:k 3] [:m 4] [:o 5])))
;;     (is (= (dissoc rec :k) {:this 1, :that 2, :m 4, :o 5}))))

;; (defrecord RecordToTestStatics1 [a])
;; (defrecord RecordToTestStatics2 [a b])
;; (defrecord RecordToTestStatics3 [a b c])
;; (defrecord RecordToTestBasis [a b c])
;; (defrecord RecordToTestBasisHinted [^String a ^Long b c])
;; (defrecord RecordToTestHugeBasis [a b c d e f g h i j k l m n o p q r s t u v w x y z])
;; (defrecord TypeToTestBasis [a b c])
;; (defrecord TypeToTestBasisHinted [^String a ^Long b c])

;; (deftest test-statics
;;   (testing "that a record has its generated static methods"
;;     (let [r1 (RecordToTestStatics1. 1)
;;           r2 (RecordToTestStatics2. 1 2)
;;           r3 (RecordToTestStatics3. 1 2 3)
;;           rn (RecordToTestStatics3. 1 nil nil)]
;;       (testing "that a record created with the ctor equals one by the static factory method"
;;         (is (= r1    (RecordToTestStatics1/create {:a 1})))
;;         (is (= r2    (RecordToTestStatics2/create {:a 1 :b 2})))
;;         (is (= r3    (RecordToTestStatics3/create {:a 1 :b 2 :c 3})))
;;         (is (= rn    (RecordToTestStatics3/create {:a 1}))))
;;       (testing "that a literal record equals one by the static factory method"
;;         (is (= #clojure.test-clojure.protocols.RecordToTestStatics1{:a 1} (RecordToTestStatics1/create {:a 1})))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestStatics2{:a 1 :b 2} (RecordToTestStatics2/create {:a 1 :b 2})))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestStatics3{:a 1 :b 2 :c 3} (RecordToTestStatics3/create {:a 1 :b 2 :c 3})))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestStatics3{:a 1} (RecordToTestStatics3/create {:a 1})))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestStatics3{:a 1 :b nil :c nil} (RecordToTestStatics3/create {:a 1}))))))
;;   (testing "that records and types have a sane generated basis method"
;;     (let [rb  (clojure.test-clojure.protocols.RecordToTestBasis/getBasis)
;;           rbh (clojure.test-clojure.protocols.RecordToTestBasisHinted/getBasis)
;;           rhg (clojure.test-clojure.protocols.RecordToTestHugeBasis/getBasis)
;;           tb (clojure.test-clojure.protocols.TypeToTestBasis/getBasis)
;;           tbh (clojure.test-clojure.protocols.TypeToTestBasisHinted/getBasis)]
;;       (is (= '[a b c] rb))
;;       (is (= '[a b c] rb))
;;       (is (= '[a b c d e f g h i j k l m n o p q r s t u v w x y z] rhg))
;;       (testing "that record basis hinting looks as we expect"
;;         (is (= (:tag (meta (rbh 0))) 'String))
;;         (is (= (:tag (meta (rbh 1))) 'Long))
;;         (is (nil? (:tag (meta (rbh 2))))))
;;       (testing "that type basis hinting looks as we expect"
;;         (is (= (:tag (meta (tbh 0))) 'String))
;;         (is (= (:tag (meta (tbh 1))) 'Long))
;;         (is (nil? (:tag (meta (tbh 2)))))))))

;; (defrecord RecordToTestFactories [a b c])
;; (defrecord RecordToTestA [a])
;; (defrecord RecordToTestB [b])
;; (defrecord RecordToTestHugeFactories [a b c d e f g h i j k l m n o p q r s t u v w x y z])
;; (defrecord RecordToTestDegenerateFactories [])

;; (deftest test-record-factory-fns
;;   (testing "if the definition of a defrecord generates the appropriate factory functions"
;;     (let [r    (RecordToTestFactories. 1 2 3)
;;           r-n  (RecordToTestFactories. nil nil nil)
;;           huge (RecordToTestHugeFactories. 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26)
;;           r-a  (map->RecordToTestA {:a 1 :b 2})
;;           r-b  (map->RecordToTestB {:a 1 :b 2})
;;           r-d  (RecordToTestDegenerateFactories.)]
;;       (testing "that a record created with the ctor equals one by the positional factory fn"
;;         (is (= r    (->RecordToTestFactories 1 2 3)))
;;         (is (= huge (->RecordToTestHugeFactories 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26))))
;;       (testing "that a record created with the ctor equals one by the map-> factory fn"
;;         (is (= r    (map->RecordToTestFactories {:a 1 :b 2 :c 3})))
;;         (is (= r-n  (map->RecordToTestFactories {})))
;;         (is (= r    (map->RecordToTestFactories (map->RecordToTestFactories {:a 1 :b 2 :c 3}))))
;;         (is (= r-n  (map->RecordToTestFactories (map->RecordToTestFactories {}))))
;;         (is (= r-d  (map->RecordToTestDegenerateFactories {})))
;;         (is (= r-d  (map->RecordToTestDegenerateFactories
;;                      (map->RecordToTestDegenerateFactories {})))))
;;       (testing "that ext maps work correctly"
;;         (is (= (assoc r :xxx 42)  (map->RecordToTestFactories {:a 1 :b 2 :c 3 :xxx 42})))
;;         (is (= (assoc r :xxx 42)  (map->RecordToTestFactories (map->RecordToTestFactories
;;                                                                {:a 1 :b 2 :c 3 :xxx 42}))))
;;         (is (= (assoc r-n :xxx 42) (map->RecordToTestFactories {:xxx 42})))
;;         (is (= (assoc r-n :xxx 42) (map->RecordToTestFactories (map->RecordToTestFactories
;;   {:xxx 42}))))
;;         (is (= (assoc r-d :xxx 42) (map->RecordToTestDegenerateFactories {:xxx 42})))
;;         (is (= (assoc r-d :xxx 42) (map->RecordToTestDegenerateFactories
;;                                     (map->RecordToTestDegenerateFactories {:xxx 42})))))
;;       (testing "record equality"
;;         (is (not= r-a r-b))
;;         (is (= (into {} r-a) (into {} r-b)))
;;         (is (not= (into {} r-a) r-b))
;;         (is (= (map->RecordToTestA {:a 1 :b 2})
;;                (map->RecordToTestA (map->RecordToTestB {:a 1 :b 2}))))
;;         (is (= (map->RecordToTestA {:a 1 :b 2 :c 3})
;;                (map->RecordToTestA (map->RecordToTestB {:a 1 :b 2 :c 3}))))
;;         (is (= (map->RecordToTestA {:a 1 :d 4})
;;                (map->RecordToTestA (map->RecordToTestDegenerateFactories {:a 1 :d 4}))))
;;         (is (= r-n (map->RecordToTestFactories (java.util.HashMap.))))
;;         (is (= r-a (map->RecordToTestA (into {} r-b))))
;;         (is (= r-a (map->RecordToTestA r-b)))
;;         (is (not= r-a (map->RecordToTestB r-a)))
;;         (is (= r (assoc r-n :a 1 :b 2 :c 3)))
;;         (is (not= r-a (assoc r-n :a 1 :b 2)))
;;         (is (not= (assoc r-b :c 3 :d 4) (assoc r-n :a 1 :b 2 :c 3 :d 4)))
;;         (is (= (into {} (assoc r-b :c 3 :d 4)) (into {} (assoc r-n :a 1 :b 2 :c 3 :d 4))))
;;         (is (= (assoc r :d 4) (assoc r-n :a 1 :b 2 :c 3 :d 4))))
;;       (testing "that factory functions have docstrings"
;;         ;; just test non-nil to avoid overspecifiying what's in the docstring
;;         (is (false? (-> ->RecordToTestFactories var meta :doc nil?)))
;;         (is (false? (->  map->RecordToTestFactories var meta :doc nil?))))
;;       (testing "that a literal record equals one by the positional factory fn"
;;         (is (= #clojure.test-clojure.protocols.RecordToTestFactories{:a 1 :b 2 :c 3} (->RecordToTestFactories 1 2 3)))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestFactories{:a 1 :b nil :c nil} (->RecordToTestFactories 1 nil nil)))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestFactories{:a [] :b {} :c ()} (->RecordToTestFactories [] {} ()))))
;;       (testing "that a literal record equals one by the map-> factory fn"
;;         (is (= #clojure.test-clojure.protocols.RecordToTestFactories{:a 1 :b 2 :c 3} (map->RecordToTestFactories {:a 1 :b 2 :c 3})))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestFactories{:a 1 :b nil :c nil} (map->RecordToTestFactories {:a 1})))
;;         (is (= #clojure.test-clojure.protocols.RecordToTestFactories{:a nil :b nil :c nil} (map->RecordToTestFactories {})))))))

;; (defn compare-huge-types
;;   [hugeL hugeR]
;;   (and
;;    (= (.a hugeL) (.a hugeR))
;;    (= (.b hugeL) (.b hugeR))
;;    (= (.c hugeL) (.c hugeR))
;;    (= (.d hugeL) (.d hugeR))
;;    (= (.e hugeL) (.e hugeR))
;;    (= (.f hugeL) (.f hugeR))
;;    (= (.g hugeL) (.g hugeR))
;;    (= (.h hugeL) (.h hugeR))
;;    (= (.i hugeL) (.i hugeR))
;;    (= (.j hugeL) (.j hugeR))
;;    (= (.k hugeL) (.k hugeR))
;;    (= (.l hugeL) (.l hugeR))
;;    (= (.m hugeL) (.m hugeR))
;;    (= (.n hugeL) (.n hugeR))
;;    (= (.o hugeL) (.o hugeR))
;;    (= (.p hugeL) (.p hugeR))
;;    (= (.q hugeL) (.q hugeR))
;;    (= (.r hugeL) (.r hugeR))
;;    (= (.s hugeL) (.s hugeR))
;;    (= (.t hugeL) (.t hugeR))
;;    (= (.u hugeL) (.u hugeR))
;;    (= (.v hugeL) (.v hugeR))
;;    (= (.w hugeL) (.w hugeR))
;;    (= (.x hugeL) (.x hugeR))
;;    (= (.y hugeL) (.y hugeR))
;;    (= (.z hugeL) (.z hugeR))))

;; (deftype TypeToTestFactory [a])
;; (defrecord TypeToTestHugeFactories [a b c d e f g h i j k l m n o p q r s t u v w x y z])

;; (deftest deftype-factory-fn
;;   (testing "that the ->T factory is gen'd for a deftype and that it works"
;;     (is (= (.a (TypeToTestFactory. 42)) (.a (->TypeToTestFactory 42))))
;;     (is (compare-huge-types
;;          (TypeToTestHugeFactories.  1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26)
;;          (->TypeToTestHugeFactories 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26))))
;;   (testing "that the generated factory checks arity constraints"
;;     (is (thrown? clojure.lang.ArityException (->TypeToTestHugeFactories 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25)))
;;     (is (thrown? clojure.lang.ArityException (->TypeToTestHugeFactories 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27)))))

;; (deftest test-ctor-literals
;;   (testing "that constructor calls to print-dup'able classes are supported as literals"
;;     (is (= "Hi" #java.lang.String["Hi"]))
;;     (is (= 42 #java.lang.Long[42]))
;;     (is (= 42 #java.lang.Long["42"]))
;;     (is (= [:a 42] #clojure.lang.MapEntry[:a 42])))
;;   (testing "that constructor literals are embeddable"
;;     (is (= 42 #java.lang.Long[#java.lang.String["42"]])))
;;   (testing "that constructor literals work for deftypes too"
;;     (is (= (.a (TypeToTestFactory. 42)) (.a #clojure.test-clojure.protocols.TypeToTestFactory[42])))
;;     (is (compare-huge-types
;;          (TypeToTestHugeFactories.  1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26)
;;          #clojure.test-clojure.protocols.TypeToTestHugeFactories[1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26]))))

;; (defrecord RecordToTestLiterals [a])
;; (defrecord TestNode [v l r])
;; (deftype TypeToTestLiterals [a])
;; (def lang-str "en")
;; (deftest exercise-literals
;;   (testing "that ctor literals can be used in common 'places'"
;;     (is (= (RecordToTestLiterals. ()) #clojure.test-clojure.protocols.RecordToTestLiterals[()]))
;;     (is (= (.a (TypeToTestLiterals. ())) (.a #clojure.test-clojure.protocols.TypeToTestLiterals[()])))
;;     (is (= (RecordToTestLiterals. 42) (into #clojure.test-clojure.protocols.RecordToTestLiterals[0] {:a 42})))
;;     (is (= (RecordToTestLiterals. (RecordToTestLiterals. 42))  (RecordToTestLiterals. #clojure.test-clojure.protocols.RecordToTestLiterals[42])))
;;     (is (= (RecordToTestLiterals. (RecordToTestLiterals. 42))  (->RecordToTestLiterals #clojure.test-clojure.protocols.RecordToTestLiterals[42])))
;;     (is (= (RecordToTestLiterals. (RecordToTestLiterals. 42))
;;            #clojure.test-clojure.protocols.RecordToTestLiterals[#clojure.test-clojure.protocols.RecordToTestLiterals[42]]))
;;     (is (= (TestNode. 1
;;                       (TestNode. 2
;;                                  (TestNode. 3
;;                                             nil
;;                                             nil)
;;                                  nil)
;;                       (TestNode. 4
;;                                  (TestNode. 5
;;                                             (TestNode. 6
;;                                                        nil
;;                                                        nil)
;;                                             nil)
;;                                  (TestNode. 7
;;                                             nil
;;                                             nil)))
;;            #clojure.test-clojure.protocols.TestNode{:v 1
;;                                                     :l #clojure.test-clojure.protocols.TestNode{:v 2
;;                                                                                                 :l #clojure.test-clojure.protocols.TestNode{:v 3 :l nil :r nil}
;;                                                                                                 :r nil}
;;                                                     :r #clojure.test-clojure.protocols.TestNode{:v 4
;;                                                                                                 :l #clojure.test-clojure.protocols.TestNode{:v 5
;;                                                                                                                                             :l #clojure.test-clojure.protocols.TestNode{:v 6 :l nil :r nil}
;;                                                                                                                                             :r nil}
;;                                                                                                 :r #clojure.test-clojure.protocols.TestNode{:v 7 :l nil :r nil}}})))

;;   (testing "that records and types are evalable"
;;     (is (= (RecordToTestLiterals. 42) (eval #clojure.test-clojure.protocols.RecordToTestLiterals[42])))
;;     (is (= (RecordToTestLiterals. 42) (eval #clojure.test-clojure.protocols.RecordToTestLiterals{:a 42})))
;;     (is (= (RecordToTestLiterals. 42) (eval (RecordToTestLiterals. 42))))
;;     (is (= (RecordToTestLiterals. (RecordToTestLiterals. 42))
;;            (eval #clojure.test-clojure.protocols.RecordToTestLiterals[#clojure.test-clojure.protocols.RecordToTestLiterals[42]])))
;;     (is (= (RecordToTestLiterals. (RecordToTestLiterals. 42))
;;            (eval #clojure.test-clojure.protocols.RecordToTestLiterals[#clojure.test-clojure.protocols.RecordToTestLiterals{:a 42}])))
;;     (is (= (RecordToTestLiterals. (RecordToTestLiterals. 42))
;;            (eval #clojure.test-clojure.protocols.RecordToTestLiterals{:a #clojure.test-clojure.protocols.RecordToTestLiterals[42]})))
;;     (is (= 42 (.a (eval #clojure.test-clojure.protocols.TypeToTestLiterals[42])))))

;;   (testing "that ctor literals only work with constants or statics"
;;     (is (thrown? Exception (read-string "#java.util.Locale[(str 'en)]")))
;;     (is (thrown? Exception (read-string "(let [s \"en\"] #java.util.Locale[(str 'en)])")))
;;     (is (thrown? Exception (read-string "#clojure.test-clojure.protocols.RecordToTestLiterals{(keyword \"a\") 42}"))))

;;   (testing "that ctors can have whitespace after class name but before {"
;;     (is (= (RecordToTestLiterals. 42)
;;            (read-string "#clojure.test-clojure.protocols.RecordToTestLiterals   {:a 42}"))))

;;   (testing "that the correct errors are thrown with malformed literals"
;;     (is (thrown-with-msg?
;;           Exception
;;           #"Unreadable constructor form.*"
;;           (read-string "#java.util.Locale(\"en\")")))
;;     (is (thrown-with-msg?
;;           Exception
;;           #"Unexpected number of constructor arguments.*"
;;           (read-string "#java.util.Locale[\"\" \"\" \"\" \"\"]")))
;;     (is (thrown? Exception (read-string "#java.util.Nachos(\"en\")")))))

(defrecord RecordToTestPrinting [a b])
(deftest defrecord-printing
  (testing "that the default printer gives the proper representation"
    (let [r   (new RecordToTestPrinting 1 2)]
      (is (= "#clojure.test-clojure.protocols.RecordToTestPrinting{:a 1, :b 2}"
             (pr-str r)))
      (is (= "#clojure.test-clojure.protocols.RecordToTestPrinting[1, 2]"
             (binding [*print-dup* true] (pr-str r))))
      (is (= "#clojure.test-clojure.protocols.RecordToTestPrinting{:a 1, :b 2}"
             (binding [*print-dup* true *verbose-defrecords* true] (pr-str r)))))))

;; (defrecord RecordToTest__ [__a ___b])
;; (defrecord TypeToTest__   [__a ___b])

;; (deftest test-record-and-type-field-names
;;   (testing "that types and records allow names starting with double-underscore.
;;             This is a regression test for CLJ-837."
;;     (let [r (RecordToTest__. 1 2)
;;           t (TypeToTest__. 3 4)]
;;       (are [x y] (= x y)
;;            1 (:__a r)
;;            2 (:___b r)
;;            3 (.__a t)
;;            4 (.___b t)))))

;; (defrecord RecordToTestLongHint [^long a])
;; (defrecord RecordToTestByteHint [^byte a])
;; (defrecord RecordToTestBoolHint [^boolean a])
;; (defrecord RecordToTestCovariantHint [^String a]) ;; same for arrays also
;; (deftype TypeToTestLongHint [^long a])
;; (deftype TypeToTestByteHint [^byte a])

;; (deftest hinting-test
;;   (testing "that primitive hinting requiring no coercion works as expected"
;;     (is (= (RecordToTestLongHint. 42) #clojure.test-clojure.protocols.RecordToTestLongHint{:a 42}))
;;     (is (= (RecordToTestLongHint. 42) #clojure.test-clojure.protocols.RecordToTestLongHint[42]))
;;     (is (= (RecordToTestLongHint. 42) (clojure.test-clojure.protocols.RecordToTestLongHint/create {:a 42})))
;;     (is (= (RecordToTestLongHint. 42) (map->RecordToTestLongHint {:a 42})))
;;     (is (= (RecordToTestLongHint. 42) (->RecordToTestLongHint 42)))
;;     (is (= (.a (TypeToTestLongHint. 42)) (.a (->TypeToTestLongHint (long 42)))))
;;     (testing "that invalid primitive types on hinted defrecord fields fails"
;;       (is (thrown?
;;             ClassCastException
;;             (read-string "#clojure.test-clojure.protocols.RecordToTestLongHint{:a \"\"}")))
;;       (is (thrown?
;;             IllegalArgumentException
;;             (read-string "#clojure.test-clojure.protocols.RecordToTestLongHint[\"\"]")))
;;       (is (thrown?
;;             IllegalArgumentException
;;             (read-string "#clojure.test-clojure.protocols.TypeToTestLongHint[\"\"]")))
;;       (is (thrown?
;;             ClassCastException
;;             (clojure.test-clojure.protocols.RecordToTestLongHint/create {:a ""})))
;;       (is (thrown?
;;             ClassCastException
;;             (map->RecordToTestLongHint {:a ""})))
;;       (is (thrown?
;;             ClassCastException
;;             (->RecordToTestLongHint "")))))
;;   (testing "that primitive hinting requiring coercion works as expected"
;;     (is (= (RecordToTestByteHint. 42) (clojure.test-clojure.protocols.RecordToTestByteHint/create {:a (byte 42)})))
;;     (is (= (RecordToTestByteHint. 42) (map->RecordToTestByteHint {:a (byte 42)})))
;;     (is (= (RecordToTestByteHint. 42) (->RecordToTestByteHint (byte 42))))
;;     (is (= (.a (TypeToTestByteHint. 42)) (.a (->TypeToTestByteHint (byte 42))))))
;;   (testing "that primitive hinting for non-numerics works as expected"
;;     (is (= (RecordToTestBoolHint. true) #clojure.test-clojure.protocols.RecordToTestBoolHint{:a true}))
;;     (is (= (RecordToTestBoolHint. true) #clojure.test-clojure.protocols.RecordToTestBoolHint[true]))
;;     (is (= (RecordToTestBoolHint. true) (clojure.test-clojure.protocols.RecordToTestBoolHint/create {:a true})))
;;     (is (= (RecordToTestBoolHint. true) (map->RecordToTestBoolHint {:a true})))
;;     (is (= (RecordToTestBoolHint. true) (->RecordToTestBoolHint true))))
;;   (testing "covariant hints -- deferred"))

;; (deftest reify-test
;;   (testing "of an interface"
;;     (let [s :foo
;;           r (reify
;;              java.util.List
;;              (contains [_ o] (= s o)))]
;;       (testing "implemented methods"
;;         (is (true? (.contains r :foo)))
;;         (is (false? (.contains r :bar))))
;;       (testing "unimplemented methods"
;;         (is (thrown? AbstractMethodError (.add r :baz))))))
;;   (testing "of two interfaces"
;;     (let [r (reify
;;              java.util.List
;;              (contains [_ o] (= :foo o))
;;              java.util.Collection
;;              (isEmpty [_] false))]
;;       (is (true? (.contains r :foo)))
;;       (is (false? (.contains r :bar)))
;;       (is (false? (.isEmpty r)))))
;;   (testing "you can't define a method twice"
;;     (is (thrown? Exception
;;          (eval '(reify
;;                  java.util.List
;;                  (size [_] 10)
;;                  java.util.Collection
;;                  (size [_] 20))))))
;;   (testing "you can't define a method not on an interface/protocol/j.l.Object"
;;     (is (thrown? Exception
;;          (eval '(reify java.util.List (foo [_]))))))
;;   (testing "of a protocol"
;;     (let [r (reify
;;              ExampleProtocol
;;              (bar [this o] o)
;;              (baz [this] 1)
;;              (baz [this o] 2))]
;;       (= :foo (.bar r :foo))
;;       (= 1 (.baz r))
;;       (= 2 (.baz r nil))))
;;   (testing "destructuring in method def"
;;     (let [r (reify
;;              ExampleProtocol
;;              (bar [this [_ _ item]] item))]
;;       (= :c (.bar r [:a :b :c]))))
;;   (testing "methods can recur"
;;     (let [r (reify
;;              java.util.List
;;              (get [_ index]
;;                   (if (zero? index)
;;                     :done
;;                     (recur (dec index)))))]
;;       (is (= :done (.get r 0)))
;;       (is (= :done (.get r 1)))))
;;   (testing "disambiguating with type hints"
;;     (testing "you must hint an overloaded method"
;;       (is (thrown? Exception
;;             (eval '(reify clojure.test-clojure.protocols.examples.ExampleInterface (hinted [_ o]))))))
;;     (testing "hinting"
;;       (let [r (reify
;;                ExampleInterface
;;                (hinted [_ ^int i] (inc i))
;;                (hinted [_ ^String s] (str s s)))]
;;         (is (= 2 (.hinted r 1)))
;;         (is (= "xoxo" (.hinted r "xo")))))))


; see CLJ-845
(defprotocol SyntaxQuoteTestProtocol
  (sqtp [p]))

(defmacro try-extend-type [c]
  `(extend-type ~c
     SyntaxQuoteTestProtocol
     (sqtp [p#] p#)))

(defmacro try-extend-protocol [c]
  `(extend-protocol SyntaxQuoteTestProtocol
     ~c
     (sqtp [p#] p#)))

(try-extend-type clojerl.String)
(try-extend-protocol clojerl.Keyword)

(deftest test-no-ns-capture
  (is (= "foo" (sqtp "foo")))
  (is (= :foo (sqtp :foo))))

(defprotocol Dasherizer
  (-do-dashed [this]))
(deftype Dashed []
  Dasherizer
  (-do-dashed [this] 10))

(deftest test-leading-dashes
  (is (= 10 (-do-dashed (new Dashed))))
  (is (= [10] (map -do-dashed [(new Dashed)]))))
