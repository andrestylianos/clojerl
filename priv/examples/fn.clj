(ns examples.fn)

(def one 1)

;; Resolve var in another ns
(clojure.core/prn {:a 2, #{1 2 2} one})

;; Provide the value of an fn-var as an argument

(clojure.core/prn clojure.core/prn)

;; Use if

(clojure.core/prn (if :test
                    (do
                      (clojure.core/prn ::then)
                      :then)
                    :else))

;; Assert uses throw

(clojure.core/assert (clojure.core/= (clojure.core/str one) "1"))

(def fixed-arity
  (fn* [x y] (clj_core/str [x y])))

;; Call a fn with single fixed arity

(clojure.core/prn (fixed-arity ::fixed ::arity))

(def multiple-fixed-arities
  (fn*
   ([x] (clj_core/str [:multiple-fixed-arities 1 x]))
   ([x y] (clj_core/str [:multiple-fixed-arities 2 x y]))
   ([x y z] (clj_core/str [:multiple-fixed-arities 3 x y z]))))

;; Call a fn with multiple fixed arities

(clojure.core/prn (multiple-fixed-arities :mult-fixed))
(clojure.core/prn (multiple-fixed-arities :a :b))
(clojure.core/prn (multiple-fixed-arities 1 2 3))

(def variadic-arity
  (fn* [& xs] (clj_core/str [:variadic-arity xs])))

(def variadic-arity-2
  (fn* [x & xs] (clj_core/str [:variadic-arity-2 x xs])))

;; Call a fn with a single variadic argument

(clojure.core/prn (variadic-arity 1 2 4))
(clojure.core/prn (variadic-arity 1 2 3))
(clojure.core/prn (variadic-arity 1 2 3 4))
(clojure.core/prn (variadic-arity))

(clojure.core/prn (variadic-arity-2 1))
(clojure.core/prn (variadic-arity-2 1 2 3))

(def multiple-variadic
  (fn*
   ([x] (clj_core/str [:multiple-variadic-1 x]))
   ([x y] (clj_core/str [:multiple-variadic-2 x y]))
   ([x y z] (clj_core/str [:multiple-variadic-3 x y z]))
   ([x y z & w] (clj_core/str [:multiple-variadic-n x y z w]))))

;; Call a  variadic fn that also has fixed arities

(clojure.core/prn (multiple-variadic 1))
(clojure.core/prn (multiple-variadic 1 2))
(clojure.core/prn (multiple-variadic 1 2 3))
(clojure.core/prn (multiple-variadic 1 2 3 4))
(clojure.core/prn (multiple-variadic 1 2 3 4 5 6 7 8 9 :a :b :c))

;; Call an anonymous fn with a single fixed arity

(clojure.core/prn ((fn* [x] x) :anon-fn-fixed))

;; Call an anonymous fn with multiple fixed arities

(clojure.core/prn ((fn* ([] :anon-fn-mult-0)
                        ([_x] :anon-fn-mult-1))))
(clojure.core/prn ((fn* ([] :anon-fn-mult-0)
                        ([_x] :anon-fn-mult-1))
                   :arg))

;; Call an anonymous fn with a single variadic arity

(clojure.core/prn ((fn* [& xs] [:anon-variadic xs]) 1 2 3))

(clojure.core/prn ((fn*
                    ([] :anon-variadic-arity-0)
                    ([& xs] xs))))

(clojure.core/prn ((fn*
                    ([] :anon-variadic-arity-0)
                    ([& xs] [:anon-variadic-arity xs])) 1))

(def apply-f
  (fn*
   ([f x] (f x))))

;; Provide an anonymous fn as an argument to be used as a function

(apply-f (fn* [x]
              (clojure.core/prn [:apply-f-anon-fixed x]))
         :apply!!!)

(clojure.core/apply (fn* [x]
                         (clojure.core/prn [:apply-anon-fixed x]))
                    [:apply!!!])

(clojure.core/apply (fn*
                     ([x] (clojure.core/prn [:apply-anon-variadic-1 x]))
                     ([x y] (clojure.core/prn [:apply-anon-variadic-2 x y]))
                     ([x y & z] (clojure.core/prn [:apply-anon-variadic-n x y z])))
                    [1])

(clojure.core/apply (fn*
                     ([x] (clojure.core/prn [:apply-anon-variadic-1 x]))
                     ([x y] (clojure.core/prn [:apply-anon-variadic-2 x y]))
                     ([x y & z] (clojure.core/prn [:apply-anon-variadic-n x y z])))
                    [1 2])

(clojure.core/apply (fn*
                     ([x] (clojure.core/prn [:apply-anon-variadic-1 x]))
                     ([x y] (clojure.core/prn [:apply-anon-variadic-2 x y]))
                     ([x y & z] (clojure.core/prn [:apply-anon-variadic-n x y z])))
                    [1 2 3 4 5])

;; Recursively call anonymous fn

(clojure.core/prn
 ((fn* count-10
        ([x]
         (if (erlang/> x 0)
           (do
             (clojure.core/prn [:anon-recur x])
             (count-10 (erlang/- x 1)))
           (clojure.core/prn [:anon-recur :done]))))
  10
  ))

;; Recursively call variadic anonymous fn

(clojure.core/prn
 ((fn* count-10-variadic
        ([x & xs]
         (if (erlang/> x 0)
           (do
             (clojure.core/prn [:variadic-anon-recur x xs])
             (count-10-variadic (erlang/- x 1) x xs))
           (clojure.core/prn [:variadic-anon-recur :done]))))
  10
  ))

;; Provide an erlang function as an argument to be used as a function

(apply-f io/format.1 "io:format/1 FTW!!!~n")
(clojure.core/apply io/format.2
                    "io:format/2 FTW!!!: ~s~n"
                    [(clojure.core/seq ["lala"])])
;; (apply-f io/format.2 "io:format/1 FTW!!!~n") ;; This should fail

;; Provide a fn var as an argument to be used as a function

(apply-f clojure.core/prn :apply!!!)

;; Provide a fn variadic var as an argument to be used as a function

(clojure.core/prn (apply-f variadic-arity :apply-f-variadic))

(clojure.core/prn (clojure.core/apply variadic-arity []))

(clojure.core/prn
 (clojure.core/apply variadic-arity
                     [:apply-variadic 1 2 3]))

(clojure.core/prn
 (clojure.core/apply variadic-arity-2
                     [:apply-variadic-2 1 2 3]))

(clojure.core/prn
 (clojure.core/apply variadic-arity-2
                     [:apply-variadic-2]))

(clojure.core/prn
 (clojure.core/apply multiple-variadic
                     [:apply-multi-variadic 1]))
(clojure.core/prn
 (clojure.core/apply multiple-variadic
                     [:apply-multi-variadic 1 2]))
(clojure.core/prn
 (clojure.core/apply multiple-variadic
                     [:apply-multi-variadic 1 2 3]))
(clojure.core/prn
 (clojure.core/apply multiple-variadic
                     [:apply-multi-variadic 1 2 3 4 5 6]))

;; Keywords as a function for maps

(clojure.core/prn (:a {:a 1}))
(clojure.core/prn (:a {:b 2} :not-found-a))
(clojure.core/prn (clojure.core/apply :b [{:b 2}]))
(clojure.core/prn (clojure.core/apply :b {:c 3} [:not-found-b]))
;; (clojure.core/prn (:a)) ;; This should fail

;; Define and call a recursive fn var

(def recursive
  (fn* [x]
       (if (erlang/> x 0)
         (do
           (clojure.core/prn [:recursive-fn x])
           (recursive (erlang/- x 1)))
         (clojure.core/prn [:recursive-fn :done]))))

(recursive 15)

(def variadic-recursive
  (fn* y [x & xs]
       (if (erlang/> x 0)
         (do
           (clojure.core/prn [:variadic-recursive-fn x xs])
           (variadic-recursive (erlang/- x 1) x xs))
         (clojure.core/prn [:variadic-recursive-fn :done xs]))))

(variadic-recursive 15)

;; Define a function with a named fn* that uses the same name

(def same-name-fn
  (fn* same-name-fn
       ([x & xs]
        (if (erlang/> x 0)
          (do
            (clojure.core/prn [:same-name-fn x xs])
            (same-name-fn (erlang/- x 1) x xs))
          (clojure.core/prn [:same-name-fn :done xs])))))

(same-name-fn 3)

;; Define a HOF that returns a function

(def dec-fn
  (fn* [] (fn* [x] (erlang/- x 1))))

(clojure.core/prn ((dec-fn) 10))


;; Use a recursive fn* inside a var fn

(clojure.core/defn recur-fn []
  (clojure.core/let [f (fn* [x]
            (if (erlang/< 0 x)
              (recur (erlang/- x 1))
              x))]
    (f 5)))

(clojure.core/prn (recur-fn))
