(ns knight.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [knight.lexer :as lexer]
            [knight.eval :as eval]
            [knight.ast :as ast])
  (:gen-class))

(def cli-options
  [["-e" "--expr EXPR" "Expression to evaluate"]
   ["-f" "--file FILE" "File to run"]
   ["-h" "--help" "Show help"]])

(defn usage [summary]
  (->> ["sampersand's Knight, implemented in Clojure."
        ""
        "Options:"
        summary]
       (string/join \newline)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn error-msg [errors]
  (str "Error while parsing options:\n"
       (string/join \newline errors)
       "\nHelp: Use `-h` to see options."))

(defn validate-args [args]
  (let [{:keys [options errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (usage summary) :ok? true}
      errors {:exit-message (error-msg errors)}
      (and (:expr options) (:file options)) ; both expr and file present - invalid
      {:exit-message "error: only one of `--expr` and `--file` may be specified, not both"}
      (not (or (:expr options) (:file options))) ; neither expr and file present - invalid
      {:exit-message "error: one of `--expr` or `--file` must be specified"}
      :else {:options options :ok? true})))

(defn run [contents]
  (let [lex (atom (lexer/tokens contents))]
    (println (lexer/advance lex))))

(defn real-main [options]
  (cond
    (:expr options)
    (run (:expr options))
    (:file options)
    (run (slurp (:file options)))))

(defn -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (real-main options))))