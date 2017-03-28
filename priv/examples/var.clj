(ns examples.var)

(def x 1)

(def prn
  (fn* [x]
    (io/format "~s~n" (clj_core/seq [(clj_core/str x)]))))

(prn (var x))

(prn #'x)

(prn x)

(def ^:dynamic *y* :y)

;; Return root binding
(prn *y*)

;; Return dynamic binding
(clojerl.Var/push_bindings {#'*y* :bound-y})
(prn *y*)
(clojerl.Var/pop_bindings)

;; Define unbound
(def z)

(prn z)
