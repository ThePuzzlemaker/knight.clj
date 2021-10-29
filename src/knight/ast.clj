(ns knight.ast
  (:require [knight.data :as data])
  (:import  [knight.data
             KnNull KnIdent
             KnNumber KnBoolean
             KnBlock KnString]))

(defprotocol Expr)

(extend-protocol Expr
  KnNull KnIdent
  KnNumber KnBoolean
  KnBlock KnString)

(deftype Prompt [] Expr)
(deftype Rand [] Expr)
(deftype Nop [expr] Expr)
(deftype Block [expr] Expr)
(deftype Eval [expr] Expr)
(deftype Call [block] Expr)
(deftype Shell [cmd] Expr)
(deftype Quit [code] Expr)
(deftype Not [bool] Expr)
(deftype Length [string] Expr)
(deftype Dump [expr] Expr)
(deftype Output [string] Expr)
(deftype Ascii [arg] Expr)
(deftype Value [string] Expr)
(deftype UnaryMinus [number] Expr)
(deftype Use [string] Expr)