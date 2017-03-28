(ns examples.try)

(try (prn 1))

(try (erlang/+ 1 :a)
     (catch :error error
       (prn [:error error])))

(try (throw :hello)
     (catch :error error
       (prn error))
     (catch :exit error
       (prn error))
     (catch :throw error
       (prn [:throw error])))

(try (throw :hello-before-finally)
     (catch :error error
       (prn error))
     (catch :exit error
       (prn error))
     (catch :throw error
       (prn [:throw error]))
     (finally
       (prn :finally)))

(try
  (erlang/error :error)
  (catch _ e
    (prn e)))

(try
  (throw :throw)
  (catch _ e
    (prn e)))

(try
  (erlang/exit :exit)
  (catch _ e
    (prn e)))
