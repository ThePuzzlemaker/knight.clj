(ns knight.eval
  (:require [clojure.string :as string]
            [clojure.java.shell :refer [sh]]
            [knight.data :as data]
            [knight.ast :as ast])
  (:import  [knight.ast Prompt Rand Nop
             Block Eval Call Shell
             Quit Not Length Dump Output Ascii
             Value UnaryMinus Use Add Sub Mul
             Div Rem Pow Lt Gt Eq And Or
             Semi Assign While]
            [knight.data KnBlock KnString
             KnNumber KnBoolean KnNull KnIdent]))

(defprotocol Context
  (ctx-put [this, key, value])
  (ctx-get [this, key]))

(deftype MapContext [map]
  Context
  (ctx-put [_, key, value] (swap! map #(assoc % key value)))
  (ctx-get [_, key] (get @map key)))

(defn ctx-empty [] (->MapContext (atom {})))

(defprotocol Evalable
  (kn-eval-step [this ctx]))

(defn kn-eval [expr ctx]
  (loop [expr expr]
    (if (satisfies? data/KnDatum expr)
      expr
      (recur (kn-eval-step expr ctx)))))

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
              (data/->KnNumber (rand-int 2147483647)))

;; Unary words
(kn-create-fn Nop [[expr :unchanged]] [this ctx] expr)
(kn-create-fn Block [[expr :unevaluated]] [this ctx] (data/->KnBlock expr))
(kn-create-fn Eval [[expr :string]] [this ctx] (throw (.Exception "TODO")))
(kn-create-fn Call [[block :block]] [this ctx] (kn-eval (.expr block) ctx))
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
(kn-create-fn Quit [[code :number]] [this ctx] (System/exit code))
(kn-create-fn Not [[bool :boolean]] [this ctx] (data/->KnBoolean (not (.inner bool))))
(kn-create-fn Length [[string :string]] [this ctx]
              (data/->KnNumber (count (.inner string))))
(kn-create-fn Dump [[expr :unchanged]] [this ctx] (data/dump expr))
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
                KnNumber (data/->KnString (str (char (.inner arg))))
                KnString (data/->KnNumber
                          (int (or (first (.inner arg)) 0)))
                (throw (IllegalArgumentException.
                        (str "Expected a string or a number, found a `"
                             (type arg) "`.")))))
(kn-create-fn Value [[string :string]] [this ctx]
              (kn-eval (data/->KnIdent (.inner string)) ctx))
(kn-create-fn UnaryMinus [[number :number]] [this ctx]
              (data/->KnNumber (- (.inner number))))
(kn-create-fn Use [[string :string]] [this ctx]
              (kn-eval (ast/->Eval
                        (data/->KnString
                         (slurp (.string string))))
                       ctx))

(kn-create-fn Add [[e1 :unchanged] [e2 :coerced]] [this ctx]
              (condp instance? e1
                KnNumber (data/->KnNumber (+ (.inner e1) (.inner e2)))
                KnString (data/->KnString (str (.inner e1) (.inner e2)))
                (throw (IllegalArgumentException.
                        (str "Expected a string or a number, found a `"
                        (type e1) "`.")))))
(kn-create-fn Sub [[e1 :number] [e2 :number]] [this ctx]
              (data/->KnNumber (- (.inner e1) (.inner e2))))
(kn-create-fn Mul [[e1 :unchanged] [e2 :number]] [this ctx]
              (condp instance? e1
                KnNumber (data/->KnNumber (* (.inner e1) (.inner e2)))
                KnString (data/->KnString (apply str (repeat (.inner e2) (.inner e1))))
                (throw (IllegalArgumentException.
                        (str "Expected a string or a number, found a `"
                             (type e1) "`.")))))
(kn-create-fn Div [[e1 :number] [e2 :number]] [this ctx]
              (data/->KnNumber (quot (.inner e1) (.inner e2))))
(kn-create-fn Rem [[e1 :number] [e2 :number]] [this ctx]
              (data/->KnNumber (rem (.inner e1) (.inner e2))))
(kn-create-fn Pow [[e1 :number] [e2 :number]] [this ctx]
              (data/->KnNumber (int (Math/pow (.inner e1) (.inner e2)))))
(kn-create-fn Lt [[e1 :unchanged] [e2 :coerced]] [this ctx]
              (condp instance? e1
                KnNumber (data/->KnBoolean (< (.inner e1) (.inner e2)))
                KnString (data/->KnBoolean (= -1 (compare (.inner e1) (.inner e2))))
                KnBoolean (data/->KnBoolean (and (not (.inner e1)) (.inner e2)))
                (throw (IllegalArgumentException.
                        (str "Expected a string, number, or boolean, found a `"
                             (type e1) "`.")))))
(kn-create-fn Gt [[e1 :unchanged] [e2 :coerced]] [this ctx]
              (kn-eval (ast/->Lt e2 e1) ctx))
(kn-create-fn Eq [[e1 :unchanged] [e2 :unchanged]] [this ctx]
              (data/->KnBoolean (= e1 e2)))
(kn-create-fn And [[e1 :unchanged] [e2 :unevaluated]] [this ctx]
              (if (.inner (data/to-boolean e1))
                (kn-eval e2 ctx) e1))
(kn-create-fn Or [[e1 :unchanged] [e2 :unevaluated]] [this ctx]
              (if (.inner (data/to-boolean e1))
                e1 (kn-eval e2 ctx)))
(kn-create-fn Semi [[e1 :unchanged] [e2 :unchanged]] [this ctx] e2)
(kn-create-fn Assign [[ident :unevaluated] [expr :unchanged]] [this ctx]
              (do (if (instance? KnIdent ident)
                    (ctx-put ctx (.inner ident) expr)
                    (ctx-put ctx (.inner (data/to-string
                                          (kn-eval ident ctx))) expr))
                  (data/->KnNull)))
(kn-create-fn While [[test :unevaluated] [expr :unevaluated]] [this ctx]
              (do (while (.inner (data/to-boolean (kn-eval test ctx)))
                    (kn-eval expr ctx))
                  (data/->KnNull)))

(extend-protocol Evalable
  KnNull (kn-eval-step [this, _] this)
  KnNumber (kn-eval-step [this, _] this)
  KnBoolean (kn-eval-step [this, _] this)
  KnBlock (kn-eval-step [this, _] this)
  KnString (kn-eval-step [this, _] this)
  KnIdent (kn-eval-step [this, ctx]
            (if-let [expr (ctx-get ctx (.inner this))]
              (kn-eval expr ctx)
              (throw (IllegalArgumentException.
                      (str "Variable not found: `"
                           (.inner this) "`."))))))