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

(deftype Add [e1, e2] Expr)
(deftype Sub [e1, e2] Expr)
(deftype Mul [e1, e2] Expr)
(deftype Div [e1, e2] Expr)
(deftype Rem [e1, e2] Expr)
(deftype Pow [e1, e2] Expr)
(deftype Lt [e1, e2] Expr)
(deftype Gt [e1, e2] Expr)
(deftype Eq [e1, e2] Expr)
(deftype And [e1, e2] Expr)
(deftype Or [e1, e2] Expr)
(deftype Semi [e1, e2] Expr)
(deftype Assign [ident, expr] Expr)
(deftype While [test, expr] Expr)

(deftype If [test, iftrue, iffalse] Expr)
(deftype Get [string, start, length] Expr)

(deftype Substitute [string, start, length, newstr] Expr)