(ns examples.guard)

(def
  ^{:macro true}
  and (fn* [_&form _&env x y] `(if ~x ~y false)))

(def
  ^{:macro true}
  or (fn* [_&form _&env x y] `(if ~x true ~y)))

(def assert (fn* [bool] (if bool :ok (throw "Assertion failed"))))

(def simple-guard
  (fn*
   ([x] {:when (erlang/is_tuple x)}
    [:tuple x])
   ([x] {:when (erlang/is_binary x)}
    [:string x])))

(assert (clj_core/equiv (simple-guard #erl []) [:tuple #erl []]))
(assert (clj_core/equiv (simple-guard "hello") [:string "hello"]))
(assert (try (simple-guard 1) false (catch _ e true)))

(def simple-and-fn
  (fn*
   ([x] {:when (erlang/and (erlang/is_integer x)
                             (erlang/> x 2))}
    :more-than-2)
   ([x] {:when (erlang/is_integer x)}
    :less-than-2)))

(assert (clj_core/equiv (simple-and-fn 3) :more-than-2))
(assert (clj_core/equiv (simple-and-fn -1) :less-than-2))
(assert (try (simple-and-fn "2") false (catch _ e  true)))

(def simple-and-macro
  (fn*
   ([x] {:when (and (erlang/is_integer x)
                    (erlang/> x 2))}
    :more-than-2)
   ([x] {:when (erlang/is_integer x)}
    :less-than-2)))

(assert (clj_core/equiv (simple-and-macro 3) :more-than-2))
(assert (clj_core/equiv (simple-and-macro -1) :less-than-2))
(assert (try (simple-and-macro "2") false (catch _ e  true)))

(def simple-or-fn
  (fn* [x] {:when (erlang/or (erlang/is_integer x)
                               (erlang/is_binary x))}
   :integer-or-binary))

(assert (clj_core/equiv (simple-or-fn 3) :integer-or-binary))
(assert (clj_core/equiv (simple-or-fn "three") :integer-or-binary))
(assert (try (simple-or-fn :three) false (catch _ e true)))

(def simple-or-macro
  (fn* [x] {:when (or (erlang/is_integer x)
                      (erlang/is_binary x))}
   :integer-or-binary))

(assert (clj_core/equiv (simple-or-macro 3) :integer-or-binary))
(assert (clj_core/equiv (simple-or-macro "three") :integer-or-binary))
(assert (try (simple-or-macro :three) false (catch _ e true)))

(def nested-and-or
  (fn* [x] {:when (and (or (erlang/is_integer x)
                           (erlang/is_float x))
                       (erlang/> x 1))}
   :integer-or-float-more-than-1))

(assert (clj_core/equiv (nested-and-or 3) :integer-or-float-more-than-1))
(assert (clj_core/equiv (nested-and-or 3.0) :integer-or-float-more-than-1))
(assert (try (nested-and-or 1) false (catch _ e true)))
(assert (try (nested-and-or 1.0) false (catch _ e true)))
(assert (try (nested-and-or "one") false (catch _ e true)))
(assert (try (nested-and-or :one) false (catch _ e true)))

(def simple-case
  (fn* [x]
       (case x
         _ {:when (erlang/=:= x 1)}
         :one

         _ {:when (erlang/=:= x 2)}
         :two

         :default)))

(assert (clj_core/equiv (simple-case 1) :one))
(assert (clj_core/equiv (simple-case 2) :two))
(assert (clj_core/equiv (simple-case 3) :default))
(assert (clj_core/equiv (simple-case "hello") :default))

(def simple-try-catch
  (fn* [x]
       (try
         (throw x)
         (catch _ e {:when (erlang/=:= e 1)}
           :one)
         (catch _ e {:when (erlang/=:= e 2)}
           :two)
         (catch _ e
           :default))))

(assert (clj_core/equiv (simple-try-catch 1) :one))
(assert (clj_core/equiv (simple-try-catch 2) :two))
(assert (clj_core/equiv (simple-try-catch 3) :default))
(assert (clj_core/equiv (simple-try-catch "hello") :default))
