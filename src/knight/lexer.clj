(ns knight.lexer)

(defn ws? [ch]
  (re-matches #"[\s\(\)\[\]\{\}]" (str ch)))

(defn symb-word? [ch]
  (re-matches #"[:`!+\-*/%^<>?&|;=~]" (str ch)))

(defn word? [ch]
  (re-matches #"[TFNPREBCQLDOAWIGSVU]" (str ch)))

(defn read-string-tok [s start]
  (loop [s s
         wip (str start)]
    (if (empty? s)
      {:string s :token {:string nil} :error "unclosed string"}
      (let [ch (first s)]
        (if (= ch start)
          {:string (subs s 1) :token {:string (str wip start)}}
          (recur (subs s 1) (str wip ch)))))))

(defn read-comment [s]
  (if (empty? s) s
    (let [ch (first s)]
      (cond (= ch \newline) (subs s 1)
            :else (recur (subs s 1))))))

(defn read-number [s start]
  (loop [s s
        wip (str start)]
    (if (empty? s)
      {:string s :token {:number wip}}
      (let [ch (first s)]
        (if (Character/isDigit ch)
          (recur (subs s 1) (str wip ch))
          {:string s :token {:number wip}})))))

(defn read-ident [s start]
  (loop [s s
         wip (str start)]
    (if (empty? s)
      {:string s :token {:ident wip}}
      (let [ch (first s)]
        (if (or
             (Character/isLetterOrDigit ch)
             (= ch \_))
          (recur (subs s 1) (str wip ch))
          {:string s :token {:ident wip}})))))

(defn read-word [s word]
  (loop [s s]
    (if (empty? s)
      {:string s :token {:word word}}
      (let [ch (first s)]
        (if (or
             (Character/isUpperCase ch)
             (= ch \_))
          (recur (subs s 1))
          {:string s :token {:word word}})))))

(defn read-full-word [s start]
  (loop [s s
         wip (str start)]
    (if (empty? s)
      {:string s :token {:ident wip}}
      (let [ch (first s)]
        (if (or
             (Character/isUpperCase ch)
             (= ch \_))
          (recur (subs s 1) (str wip ch))
          {:string s :token {:word wip}})))))

(defn read-token [s]
  (loop [s_orig s]
    (if (empty? s_orig)
      {:string s_orig :token {:eof true}}
      (let [ch (first s_orig)
            s (subs s_orig 1)]
        (cond
          (= ch \") (read-string-tok s \")
          (= ch \') (read-string-tok s \')
          (= ch \#) (recur (read-comment s))
          (ws? ch) (recur s)
          (Character/isDigit ch) (read-number s ch)
          (Character/isLowerCase ch) (read-ident s ch)
          (word? ch) (read-word s ch)
          (symb-word? ch) {:string s :token {:word ch}}
          (= ch \X) (read-full-word s ch)
          (= ch \_) (read-ident s \_)
          :else {:string s_orig :token {:invalid true} :error "invalid token"})))))

(defn tokens [s]
  (lazy-seq
   (let [res (read-token s)
         {:keys [string, token, error]} res]
     (if (or error (:eof token))
       [res] ; stop on error or EOF
       (cons res (tokens string))))))

(defn advance [lex]
  (let [res (first @lex)]
    (swap! lex #(rest %))
    res))