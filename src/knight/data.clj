(ns knight.data
  (:require [clojure.string :as string]))

(defprotocol KnDatum
  (dump [this])
  (to-string [this])
  (to-boolean [this])
  (to-number [this])
  (kn-eq [this, that]))

(declare ->KnBoolean)
(declare ->KnNumber)
(declare ->KnString)

(deftype KnString [inner]
  KnDatum
  (dump [_] (str "String(" inner ")"))
  (to-string [this] this)
  (to-boolean [_] (->KnBoolean #_{:clj-kondo/ignore [:not-empty?]} ;; idk why the hell it suggests this, it's not even CLOSE to semantically similar
                               (not (empty? inner))))
  (to-number [_] (->KnNumber
                  (try
                    (Integer/parseInt (apply str (take-while #(or (= % \+) (= % \-) (Character/isDigit %)) (string/trim inner))))
                    (catch Exception _ 0))))
  (kn-eq [_, that] (= inner (.inner that))))

(deftype KnBoolean [inner]
  KnDatum
  (dump [_] (str "Boolean(" inner ")"))
  (to-string [_] (->KnString (str inner)))
  (to-boolean [this] this)
  (to-number [_] (->KnNumber (if inner 1 0)))
  (kn-eq [_, that] (= inner (.inner that))))

(deftype KnNumber [inner]
  KnDatum
  (dump [_] (str "Number(" inner ")"))
  (to-string [_] (->KnString (str inner)))
  (to-boolean [_] (->KnBoolean (not= 0 inner)))
  (to-number [this] this)
  (kn-eq [_, that] (= inner (.inner that))))

(deftype KnIdent [inner])

(deftype KnBlock [inner]
  KnDatum
  (dump [_] "Block(opaque)")
  (to-string [_] (throw (IllegalArgumentException. "Attempted to convert a block to a string.")))
  (to-boolean [_] (throw (IllegalArgumentException. "Attempted to convert a block to a boolean.")))
  (to-number [_] (throw (IllegalArgumentException. "Attempted to convert a block to a number.")))
  (kn-eq [_, _] false))

(deftype KnNull []
  KnDatum
  (dump [_] "Null()")
  (to-string [_] (->KnString "null"))
  (to-boolean [_] (->KnBoolean false))
  (to-number [_] (->KnNumber 0))
  (kn-eq [_, _] true))