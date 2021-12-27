(ns knight.parser
  (:require [knight.lexer :as lexer]
            [knight.data :as data]
            [knight.ast :as ast]))

(defn parse [lex eof_ok]
    (let [tok (:token (lexer/advance lex))]
      (cond
        (:invalid tok) (throw (Exception. "Syntax error: invalid token found"))
        (and (:eof tok) (not eof_ok)) (throw (Exception. "Syntax error: unexpected EOF"))
        (and (:eof tok) eof_ok) nil
        (some? (:string tok)) (data/->KnString (subs (:string tok) 1 (- (count (:string tok)) 1)))
        (some? (:number tok)) (data/->KnNumber (Integer/parseInt (:number tok)))
        (some? (:ident tok)) (data/->KnIdent (:ident tok))
        (= \T (:word tok)) (data/->KnBoolean true)
        (= \F (:word tok)) (data/->KnBoolean false)
        (= \N (:word tok)) (data/->KnNull)
        (= \P (:word tok)) (ast/->Prompt)
        (= \R (:word tok)) (ast/->Rand)
        (= \: (:word tok)) (ast/->Nop (parse lex false))
        (= \E (:word tok)) (ast/->Eval (parse lex false))
        (= \B (:word tok)) (ast/->Block (parse lex false))
        (= \C (:word tok)) (ast/->Call (parse lex false))
        (= \` (:word tok)) (ast/->Shell (parse lex false))
        (= \Q (:word tok)) (ast/->Quit (parse lex false))
        (= \! (:word tok)) (ast/->Not (parse lex false))
        (= \L (:word tok)) (ast/->Length (parse lex false))
        (= \D (:word tok)) (ast/->Dump (parse lex false))
        (= \O (:word tok)) (ast/->Output (parse lex false))
        (= \A (:word tok)) (ast/->Ascii (parse lex false))
        (= "XGENSYM" (:word tok)) (ast/->GenSym (parse lex false))
        (= \+ (:word tok)) (ast/->Add (parse lex false) (parse lex false))
        (= \- (:word tok)) (ast/->Sub (parse lex false) (parse lex false))
        (= \* (:word tok)) (ast/->Mul (parse lex false) (parse lex false))
        (= \/ (:word tok)) (ast/->Div (parse lex false) (parse lex false))
        (= \% (:word tok)) (ast/->Rem (parse lex false) (parse lex false))
        (= \^ (:word tok)) (ast/->Pow (parse lex false) (parse lex false))
        (= \< (:word tok)) (ast/->Lt (parse lex false) (parse lex false))
        (= \> (:word tok)) (ast/->Gt (parse lex false) (parse lex false))
        (= \? (:word tok)) (ast/->Eq (parse lex false) (parse lex false))
        (= \& (:word tok)) (ast/->And (parse lex false) (parse lex false))
        (= \| (:word tok)) (ast/->Or (parse lex false) (parse lex false))
        (= \; (:word tok)) (ast/->Semi (parse lex false) (parse lex false))
        (= \= (:word tok)) (ast/->Assign (parse lex false) (parse lex false))
        (= \W (:word tok)) (ast/->While (parse lex false) (parse lex false))
        (= \I (:word tok)) (ast/->If (parse lex false) (parse lex false) (parse lex false))
        (= \G (:word tok)) (ast/->Get (parse lex false) (parse lex false) (parse lex false))
        (= \S (:word tok)) (ast/->Substitute (parse lex false) (parse lex false) (parse lex false) (parse lex false))
        (= \V (:word tok)) (ast/->Value (parse lex false))
        (= \~ (:word tok)) (ast/->UnaryMinus (parse lex false))
        (= \U (:word tok)) (ast/->Use (parse lex false))
        :else (throw (Exception. "Syntax error: invalid expression")))))
