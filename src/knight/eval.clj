(ns knight.eval
  (:require [clojure.string :as string]
            [clojure.java.shell :refer [sh]]
            [knight.data :as data]
            [knight.ast :as ast]
            [knight.parser :as parser]
            [knight.lexer :as lexer])
  (:import  [knight.ast Prompt Rand Nop
             Block Eval Call Shell
             Quit Not Length Dump Output Ascii
             Value UnaryMinus Use Add Sub Mul
             Div Rem Pow Lt Gt Eq And Or
             Semi Assign While If Get Substitute GenSym]
            [knight.data KnBlock KnString
             KnNumber KnBoolean KnNull KnIdent]
            [java.util.concurrent ThreadLocalRandom]))

(defprotocol Context
  (ctx-put [this, key, value])
  (ctx-get [this, key])
  (ctx-contains [this, key]))

(deftype MapContext [map]
  Context
  (ctx-put [_, key, value] (swap! map #(assoc % key value)))
  (ctx-get [_, key] (get @map key))
  (ctx-contains [_, key] (contains? @map key)))

(defn ctx-empty [] (->MapContext (atom {})))

(defprotocol Evalable
  (kn-eval-step [this ctx]))

(defn kn-eval [expr ctx]
  (loop [expr expr]
    (if (satisfies? data/KnDatum expr)
      expr
      (recur (kn-eval-step expr ctx)))))

;; TODO: line+col errors
(defn run-with-ctx [contents ctx]
  (let [lex (atom (lexer/tokens contents))
        ast (parser/parse lex true)]
    (if (:eof (:token (lexer/advance lex)))
      (kn-eval ast ctx)
      (throw (Exception. "Syntax error: Did not expect an expression here")))))

(defn run [contents]
  (run-with-ctx contents (ctx-empty)))

(defn main-binding [ctx this [name kind]]
  (if (= kind :unevaluated)
    [name `(. ~this ~name)]
    [name `(kn-eval (. ~this ~name) ~ctx)]))

(defn coerce [first current]
  (cond 
    (instance? KnBoolean first)
    (data/to-boolean current)
    (instance? KnString first)
    (data/to-string current)
    (instance? KnNumber first)
    (data/to-number current)
    :else (throw (IllegalArgumentException. (str "Cannot coerce to a `" (type first) "`.")))))

(defn ctx-binding [first [name type]]
  [name
   (condp = type
     :unevaluated name
     :unchanged name
     :string `(data/to-string ~name)
     :boolean `(data/to-boolean ~name)
     :number `(data/to-number ~name)
     :block `(if (instance? KnBlock ~name)
               ~name
               (throw (IllegalArgumentException.
                       (str "Expected a block, found a `" (type ~name) "`."))))
     :coerced `(coerce ~first ~name)
     (throw (IllegalArgumentException.
             (str "Invalid argument type `" type "`."))))])

(defn create-glue [ctx this args body]
  (let [main_bindings (apply concat (map (partial main-binding ctx this) args))
        ctx_bindings (apply concat (map (partial ctx-binding (first (first args))) args))]
    `(let [~@main_bindings ~@ctx_bindings] ~body)))

(defmacro kn-create-fn [clazz [& args] [this ctx] body]
  (let [glue (create-glue ctx this args body)]
    `(extend-type ~clazz
       Evalable
       (kn-eval-step [~this ~ctx] ~glue))))

;; Nullary words
(kn-create-fn Prompt [] [this ctx]
              (data/->KnString (string/trim-newline (or (read-line) ""))))
(kn-create-fn Rand [] [this ctx]
              (data/->KnNumber (Math/abs (.nextInt (ThreadLocalRandom/current)))))

;; Unary words
(kn-create-fn Nop [[expr :unchanged]] [this ctx] expr)
(kn-create-fn Block [[expr :unevaluated]] [this ctx] (data/->KnBlock expr))
(kn-create-fn Eval [[expr :string]] [this ctx]
              (run-with-ctx (.inner expr) ctx))
(kn-create-fn Call [[block :block]] [this ctx] (.inner block))
(kn-create-fn Shell [[cmd :string]] [this ctx]
              (let [cmd (.inner cmd)
                    out (sh "sh" "-c" cmd)
                    code (:exit out)]
                (ctx-put ctx "sh_stderr" (data/->KnString (:err out)))
                (if (= 0 code)
                  (data/->KnString (:out out))
                  (do
                    (ctx-put ctx "sh_stdout" (data/->KnString (:out out)))
                    (data/->KnNumber code)))))
(kn-create-fn Quit [[code :number]] [this ctx] (System/exit (.inner code)))
(kn-create-fn Not [[bool :boolean]] [this ctx] (data/->KnBoolean (not (.inner bool))))
(kn-create-fn Length [[string :string]] [this ctx]
              (data/->KnNumber (count (.getBytes (.inner string) "UTF-8"))))
(kn-create-fn Dump [[expr :unchanged]] [this ctx]
              (do (println (data/dump expr))
                  expr))
(kn-create-fn Output [[string :string]] [this ctx]
              (let [inner (.inner string)]
                (if (.endsWith inner "\\")
                  (print (subs inner 0
                               (- (.length inner) 1)))
                  (println inner))
                (flush)
                (data/->KnNull)))
(kn-create-fn Ascii [[arg :unchanged]] [this ctx]
              (condp instance? arg
                KnNumber (data/->KnString (Character/toString (.inner arg)))
                KnString (data/->KnNumber
                          (try
                            (long (.codePointAt (.inner arg) 0))
                            (catch Exception _ 0)))
                (throw (IllegalArgumentException.
                        (str "Expected a string or a number, found a `"
                             (type arg) "`.")))))
(kn-create-fn Value [[string :string]] [this ctx]
              (data/->KnIdent (.inner string)))
(kn-create-fn UnaryMinus [[number :number]] [this ctx]
              (data/->KnNumber (- (.inner number))))
(kn-create-fn Use [[string :string]] [this ctx]
              (ast/->Eval
               (data/->KnString
                (slurp (.inner string)))))
(kn-create-fn GenSym [[string :string]] [this ctx]
              (loop [n 1000]
                (if (= n 0)
                  (throw (Exception. "ran out of allowed steps (1000) in XGENSYM"))
                  (let [num (.nextLong (ThreadLocalRandom/current) 100000 1000000)
                        name (str (.inner string) num)]
                    (if (ctx-contains ctx name)
                      (recur (dec n))
                      (data/->KnString name))))))

(kn-create-fn Add [[e1 :unchanged] [e2 :coerced]] [this ctx]
              (condp instance? e1
                KnNumber (data/->KnNumber (+ (.inner e1) (.inner e2)))
                KnString (data/->KnString (str (.inner e1) (.inner e2)))
                (throw (IllegalArgumentException.
                        (str "Expected a string or a number, found a `"
                        (type e1) "`.")))))
(kn-create-fn Sub [[e1 :unchanged] [e2 :number]] [this ctx]
              (do (assert (instance? KnNumber e1) (str "Expected a number, found a `" (type e1) "`."))
                  (data/->KnNumber (- (.inner e1) (.inner e2)))))
(kn-create-fn Mul [[e1 :unchanged] [e2 :number]] [this ctx]
              (condp instance? e1
                KnNumber (data/->KnNumber (* (.inner e1) (.inner e2)))
                KnString (data/->KnString (apply str (repeat (.inner e2) (.inner e1))))
                (throw (IllegalArgumentException.
                        (str "Expected a string or a number, found a `"
                             (type e1) "`.")))))
(kn-create-fn Div [[e1 :unchanged] [e2 :number]] [this ctx]
              (do (assert (instance? KnNumber e1) (str "Expected a number, found a `" (type e1) "`."))
                  (data/->KnNumber (quot (.inner e1) (.inner e2)))))
(kn-create-fn Rem [[e1 :unchanged] [e2 :number]] [this ctx]
              (do (assert (instance? KnNumber e1) (str "Expected a number, found a `" (type e1) "`."))
                  (data/->KnNumber (rem (.inner e1) (.inner e2)))))
(kn-create-fn Pow [[e1 :unchanged] [e2 :number]] [this ctx]
              (do (assert (instance? KnNumber e1) (str "Expected a number, found a `" (type e1) "`."))
                  (data/->KnNumber (int (Math/pow (.inner e1) (.inner e2))))))
(kn-create-fn Lt [[e1 :unchanged] [e2 :coerced]] [this ctx]
              (condp instance? e1
                KnNumber (data/->KnBoolean (< (.inner e1) (.inner e2)))
                KnString (data/->KnBoolean (< (compare (.inner e1) (.inner e2)) 0))
                KnBoolean (data/->KnBoolean (and (not (.inner e1)) (.inner e2)))
                (throw (IllegalArgumentException.
                        (str "Expected a string, number, or boolean, found a `"
                             (type e1) "`.")))))
(kn-create-fn Gt [[e1 :unchanged] [e2 :coerced]] [this ctx]
              (ast/->Lt e2 e1))
(kn-create-fn Eq [[e1 :unchanged] [e2 :unchanged]] [this ctx]
              (data/->KnBoolean (and (= (type e1) (type e2)) (data/kn-eq e1 e2))))
(kn-create-fn And [[e1 :unchanged] [e2 :unevaluated]] [this ctx]
              (if (.inner (data/to-boolean e1))
                e2 e1))
(kn-create-fn Or [[e1 :unchanged] [e2 :unevaluated]] [this ctx]
              (if (.inner (data/to-boolean e1))
                e1 e2))
(kn-create-fn Semi [[e1 :unchanged] [e2 :unchanged]] [this ctx] e2)
(kn-create-fn Assign [[ident :unevaluated] [expr :unchanged]] [this ctx]
              (do (if (instance? KnIdent ident)
                    (ctx-put ctx (.inner ident) expr)
                    (ctx-put ctx (.inner (data/to-string
                                          (kn-eval ident ctx))) expr))
                  expr))
(kn-create-fn While [[test :unevaluated] [expr :unevaluated]] [this ctx]
              (do (while (.inner (data/to-boolean (kn-eval test ctx)))
                    (kn-eval expr ctx))
                  (data/->KnNull)))

(kn-create-fn If [[test :boolean] [iftrue :unevaluated] [iffalse :unevaluated]] [this ctx]
              (if (.inner test) iftrue iffalse))
(kn-create-fn Get [[string :string] [start :number] [length :number]] [this ctx]
              (data/->KnString (subs (.inner string)
                                     (.inner start)
                                     (+ (.inner start)
                                        (.inner length)))))

(kn-create-fn Substitute [[string :string] [start :number]
                          [length :number] [newstr :string]] [this ctx]
              (let [string (.inner string)
                    start (.inner start)
                    length (.inner length)
                    end (+ start length)
                    newstr (.inner newstr)]
                (data/->KnString (str (subs string 0 start)
                                      newstr (subs string end)))))

(extend-protocol Evalable
  KnNull (kn-eval-step [this, _] this)
  KnNumber (kn-eval-step [this, _] this)
  KnBoolean (kn-eval-step [this, _] this)
  KnBlock (kn-eval-step [this, _] this)
  KnString (kn-eval-step [this, _] this)
  KnIdent (kn-eval-step [this, ctx]
            (if-let [expr (ctx-get ctx (.inner this))]
              expr
              (throw (IllegalArgumentException.
                      (str "Variable not found: `"
                           (.inner this) "`."))))))