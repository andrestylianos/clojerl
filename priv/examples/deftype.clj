(ns examples.deftype
  (:require [examples.protocols :as other]))

(deftype SomeType [value1 value2]
  clojerl.IHash
  (hash [_] 42)
  clojerl.Stringable
  (str [_]
    (str "#SomeType[value1 = " value1 ", value2 = " value2 "]")))

(let [x (new SomeType 1 2)]
  (prn x)
  (prn (clojerl.IHash/hash x)))

(defrecord SomeRecord [x y]
  clojerl.IHash
  (hash [this] (+ x y)))

(let [x (new SomeRecord 1 2)]
  (prn x)
  (prn (clojerl.IHash/hash x))
  (prn (:x x))
  (prn (assoc x :x 42 :z *out*))
  (prn (merge x {:y 1984 :z :keyword}))
  (prn (merge {:y 1984 :z :keyword} x))
  (prn (= {:x 1 :y 2} x)))

(defprotocol SomeProtocol
  (function-one [this] [this a])
  (function-two [this a b c]))

(defprotocol OtherProtocol
  (foo [this])
  (bar [this]))

(extend-type SomeRecord
  SomeProtocol
  (function-one [this] :one)
  (function-one [this a] [:one a])
  (function-two [this a b c] #{:one a b c})
  examples.deftype.OtherProtocol
  (foo [this] {:foo (:y this)})
  (bar [this] {:bar (:x this)}))

(prn (foo (new SomeRecord 1 2)))
(prn (bar (new SomeRecord 1 2)))

other/ExampleProtocol

(prn #examples.deftype.SomeRecord{:x 1 :y 2})
(prn #examples.deftype.SomeRecord[1 2])
(prn (SomeRecord/create {:x 1 :y 2}))
