(ns knight.eval
  (:require [clojure.string :as string]
            [clojure.java.shell :refer [sh]]
            [knight.data :as data]
            [knight.ast :as ast])
  (:import  [knight.ast Prompt Rand Nop
             Block Eval Call Shell
             Quit Not Length Dump Output Ascii
             Value UnaryMinus Use]
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