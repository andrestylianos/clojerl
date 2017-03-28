(ns examples.loop)

;; Use loop and recur

(loop* [x 1]
       (if (erlang/< x 10)
         (do
           (clojure.core/prn [:recur x])
           (recur (erlang/+ x 1)))
         (clojure.core/prn [:end x])))

;; Nested loops

(loop* [x 1]
       (if (erlang/< x 10)
         (do
           (clojure.core/prn [:outer-recur x])
           (recur (erlang/+ x 1)))
         (loop* [x :b x :a]
                (if (clj_core/equiv x :a)
                  (do
                    (clojure.core/prn [:inner-recur x])
                    (recur :b :c))
                  (clojure.core/prn [:inner-end x])))))

(let* [f (fn* [x]
              (if (clojure.core/< 0 x)
                (do
                  (clojure.core/prn x)
                  (recur (erlang/- x 1)))
                x))]
      (clojure.core/prn (f 10)))

(clojure.core/defn f-recur
  [x]
  (if (clojure.core/< 0 x)
    (do
      (clojure.core/prn [:f-recur x])
      (recur (erlang/- x 1)))
    x))

(f-recur 5)
