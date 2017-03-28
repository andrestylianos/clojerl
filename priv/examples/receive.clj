(ns examples.receive)

(def loop
  (fn* [state]
    (receive*
     x {:when (erlang/=:= x :inc)}
     (-> state inc loop)

     x {:when (erlang/=:= x :dec)}
     (-> state dec loop)

     x {:when (erlang/=:= x :print)}
     (loop (doto state prn))

     _
     (loop state))))

(def spawn
  (fn* [f & args]
       (erlang/spawn :clj_core :apply (clj_core/to_list [f args]))))

(let [x (spawn loop 42)]
  (erlang/send x :inc)
  (erlang/send x :inc)
  (erlang/send x :inc)
  (erlang/send x :print)
  (erlang/send x :dec)
  (erlang/send x :dec)
  (erlang/send x :dec)
  (erlang/send x :print))
