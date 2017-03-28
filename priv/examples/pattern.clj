(ns examples.pattern)

;; fn*

(def f (fn* ([x x] x) ([x y] y)))

(def g (fn* ([#erl{:x x} x] x)
            ([_ _] :whatever)))

(let* [:foo (g #erl{:x :foo} :foo)
       :whatever (g #erl{:x :foo} :bar)
       :whatever (g :foo :bar)

       #erl{:bar bar :baz bar} #erl{:bar 1 :baz 1 :foo 2}

       3 (case* #erl[1 2]
           #erl[one two] (erlang/+ one two)
           2 :two)])

;; let* with binary patterns

(let* [#bin[[h :type :utf8] [ello :type :binary]] "hello"
       104    h
       "ello" ello])

;; let*

(let* [#erl(a b)     #erl(1 2)
       #erl{1 2}     #erl{a b}
       tail          #erl(3 4)
       #erl(1 2 3 4) #erl(1 2 & tail)
       #erl[:badmatch, _] (try
                            (let* [#erl(1 2 3) #erl(1 2 & tail)]
                              :ok)
                            (catch :error e
                              e))])
;; catch

(try
  (throw #erl[:invalid :hello])
  (catch :throw #erl[x reason]
    (let* [:invalid x :hello reason])))

;; receive*

(def spawn
  (fn* [f & args]
       (erlang/spawn :clj_core :apply (clj_core/to_list [f args]))))

(def f
  (fn* []
    (receive*
     #erl[:ok msg pid]
     (erlang/send pid msg)
     _
     :ok)
    (f)))

(def receive-1 (fn* [] (receive* x x)))

(let* [pid  (spawn f)
       self (erlang/self)
       _    (erlang/send pid #erl[:ok :foo self])
       :foo (receive-1)
       _    (erlang/send pid #erl[:ok :bar self])
       :ok  (try
              (let* [:foo (receive-1)]
                :error)
              (catch :error #erl[:badmatch _]
                :ok))])

;; loop*

(let* [x (loop* [#erl(x & xs) #erl(1 1 1 1 1)
                 sum 0]
           (if (erlang/=:= xs #erl())
             (erlang/+ sum x)
             (recur xs (erlang/+ sum x))))
       5 x])

(let* [x (loop* [#erl(x & xs) #erl(1 40)
                 sum x]
           (if (erlang/=:= xs #erl())
             (erlang/+ sum x)
             (recur xs (erlang/+ sum x))))
       42 x])

;; alias

(let* [(erl-alias* x 1) 1
       1 x])

(let* [#as(x 1) 1
       1        x])

(let* [#as(x #erl(a b)) #erl(1 2)
       3                (erlang/+ a b)])
