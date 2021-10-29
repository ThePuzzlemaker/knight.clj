(ns knight.data
  (:require [clojure.string :as string]))

(defprotocol KnDatum
  (dump [this])
  (to-string [this])
  (to-boolean [this])
  (to-number [this]))

(declare ->KnBoolean)
(declare ->KnNumber)
(declare ->KnString)

(deftype KnString [inner]
  KnDatum
  (dump [_] (->KnString (str "String(" inner ")")))
  (to-string [this] this)
  (to-boolean [_] (->KnBoolean (not-empty inner)))
  (to-number [_] (->KnNumber
                  (try
                    (Integer/parseInt (string/trim inner))
                    (catch Exception _ 0)))))

(deftype KnBoolean [inner]
  KnDatum
  (dump [_] (->KnString (str "Boolean(" inner ")")))
  (to-string [_] (->KnString (str inner)))
  (to-boolean [this] this)
  (to-number [_] (->KnNumber (if inner 1 0))))

(deftype KnNumber [inner]
  KnDatum
  (dump [_] (->KnString (str "Number(" inner ")")))
  (to-string [_] (->KnString (str inner)))
  (to-boolean [_] (->KnBoolean (not= 0 inner)))
  (to-number [this] this))

(deftype KnIdent [inner])

(deftype KnBlock [inner]
  KnDatum
  (dump [_] (->KnString "Block(opaque)"))
  (to-string [_] (throw (IllegalArgumentException. "Attempted to convert a block to a string.")))
  (to-boolean [_] (throw (IllegalArgumentException. "Attempted to convert a block to a boolean.")))
  (to-number [_] (throw (IllegalArgumentException. "Attempted to convert a block to a number."))))

(deftype KnNull []
  KnDatum
  (dump [_] (->KnString "Null()"))
  (to-string [_] (->KnString "null"))
  (to-boolean [_] (->KnBoolean false))
  (to-number [_] (->KnNumber 0)))