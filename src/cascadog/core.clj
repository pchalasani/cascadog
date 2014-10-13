(ns cascadog.core
  "Friendly variants of Cascalog macros"
  (:use cascalog.api)
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.set :as set]
            [jackknife.seq :as js]
            [cascalog.logic
             [fn :as cfn] ;; cascalog version of fn
             [ops :as c]
             [def :as d]
             [vars :as v]
             [parse :as parse]
             [predicate :as p]]))

(defn has-casca-var
  "does form contain a cascalog var?"
  [form]
  (->> (js/flatten form)
       (filter v/logic-sym?)
       count
       (< 0)))


(defn casca-num?
  "Is the symbol a number-variable like ?#blah or !#blah ?"
  [sym]
  (->> sym str name ( re-find #"^[\?!]#") boolean))


(defn find-casca-nums
  "find all vars of the form ?#a or !#a"
  [form]
  (->> (js/flatten form)
       (filter v/logic-sym?)
       (distinct)
       (filter casca-num?)))

(defn find-casca-vars
  "find all vars of the form ?a or !a"
  [form]
  (->> (js/flatten form)
       (filter v/logic-sym?)
       (distinct)))

(defn casca-read-clause
  "symbol ?#a => (read-string ?a :> ?#a)"
  [x]
  (let
      [sym (symbol (str/replace (name x) #"#" "")) ]
    `(read-string ~sym :> ~x)))

(defn insert-casca-read-nums
  "insert Cascalog read-string clauses defining numerical vars"
  [forms]
  (let
      [cvars (find-casca-nums forms)]
    (concat forms
            (map casca-read-clause cvars))))

(defn get-casca-read-nums
  "get Cascalog read-string clauses defining numerical vars"
  [forms]
  (let
      [cvars (find-casca-nums forms)]
    (map casca-read-clause cvars)))

(defn get-type
  "get type of a function if possible"
  [f]
  (try (str (type (eval f)))
       (catch Exception _ "")))

(defn get-vars
  "get a map {:form, :vars, :vars_} where:
    :vars is the (sorted) set of variables in function-arg position in the form,
    :vars__ are the corresponding vars suffixed with __
    :form is new form with all vars suffixed by __"
  [form]
  (if (or (not (coll? form))     ;; not a coll
          (= (first form) 'var)) ;; not a var
    (if (symbol? form)
      {:form (symbol (str form "__"))
       :vars #{form}
       :vars__ #{(symbol (str form "__"))}}
      {:form form :vars #{}})
    ;; might be either a function-form or a data-structure: map or vector!
    (if (vector? form)
      (let
          [form-vars (map get-vars form)]
        {:form (vec (map :form form-vars))
         :vars (into (sorted-set) (reduce set/union #{} (map :vars form-vars)))
         :vars__ (into (sorted-set) (reduce set/union #{} (map :vars__ form-vars)))})
      (if (map? form)
        (let [f-v (get-vars (into [] form))]
          {:form (into {} (f-v :form))
           :vars (f-v :vars)
           :vars__ (f-v :vars__)})
        (let [[fn & args] form     ;; else handle it like a function form, cross fingers
              form-vars (map get-vars args)]
          {:form (cons fn (map :form form-vars))
           :is-filt (or (->> fn get-type (re-find #"cascalog.*filter") boolean)
                     (some true? (map :is-filt form-vars)))
           :is-map (or (->> fn get-type (re-find #"cascalog.*map$") boolean)
                    (some true? (map :is-map form-vars)))
           :is-mapcat (or (->> fn get-type (re-find #"cascalog.*mapcat$") boolean)
                          (some true? (map :is-mapcat form-vars)))
           :vars (into (sorted-set) (reduce set/union #{} (map :vars form-vars)))
           :vars__ (into (sorted-set) (reduce set/union #{} (map :vars__ form-vars))) })))))


(defn anonymize
  "Anonymize a form non-recursively, pulling all vars out"
  [form]
  (let
      [{:keys [is-filt is-map is-mapcat vars vars__ form]} (get-vars form)
       vars (into [] vars)  ;; the order of vars and vars__ MUST MATCH!
       vars__ (into [] vars__)
       ]
    (if (= 0 (count vars))
      form
      (cond is-map `((d/mapfn ~vars__ ~form) ~@vars)
            is-mapcat `((d/mapcatfn ~vars__ ~form) ~@vars)
            is-filt `((d/filterfn ~vars__ ~form) ~@vars)
            true `((cfn/fn ~vars__ ~form) ~@vars)))))


(defn predicate-parts
  "parts of a predicate:
   :pre = before first operator
   :rest = including first operator plus rest"
  [pred]
  {:pre (take-while #(not (v/selector? %) ) pred)
   :rest (drop-while #(not (v/selector? %)) pred)})

(defn not-flatten-able?
  "detect form that should not be flattened"
  [form]
  (or
   (not (coll? form)) ;; not clear how this can happen
   (keyword? (first form)) ;; e.g. (:trap ... )
   ;; generator ((q ?a ) :> ?b) or ( gen ?a ) with implicit :> op
   (and (= 1 (-> form predicate-parts :pre count))
        (not (-> form predicate-parts :pre has-casca-var)))  ;; likely a generator like ((blah junk) :> ?x ?y)
   (try (p/can-generate? (eval (first form)) )
        (catch Exception e nil))
   (try (get-out-fields (eval (first form)))
        (catch Exception e nil))
   (let  ;; unhandled cascalog fn types
       [fn-type (get-type (first form))]
     (and (re-find #"cascalog\." fn-type)
          (not (some identity (map #(re-find % fn-type) [#"filter$" #"map$" #"mapcat$"])) )))))

(defn flatten-predicate
  "flatten predicate using anon fn"
  [form]
  (if (vector? form) ;; simply change [...] => (...)
    (concat '() form)
    (if (not-flatten-able? form)
      form
      (if (some v/selector? form)
        ;; contains an operator like :>  so looks like
        ;; ( (+ ?a ?b) :> ?c ) or (* 2 (+ ?a 5) :> ?c)
        (let
            [pre-op (take-while #(not (v/selector? %) ) form)
             post-op (drop-while #(not (v/selector? %)) form)
             ]
          (if (= 1 (count pre-op))
            (if-not (list? (first form))  ;; ([[1] [2]] :> ?a)
              form
              `(~@(anonymize (first form))  ~@(rest form))) ;; ((q 10) :> ?a) or (f x)
            `(~@(anonymize pre-op)  ~@post-op))) ;; (* ?a 10 :> ?b)
        (anonymize form))))) ;; (#'and (< ?a 10) (> ?a 1))


(defmacro ??<< [outvars & predicates]
  (concat `(??<- ~outvars)
          (map flatten-predicate predicates)
          (get-casca-read-nums predicates)))

(defmacro ?<< [sink vars & predicates]
  (concat `(?<- ~sink ~vars)
          (map flatten-predicate predicates)
          (get-casca-read-nums predicates)))

(defmacro << [outvars & predicates]
  (concat `(<- ~outvars)
          (map flatten-predicate predicates)
          (get-casca-read-nums predicates)))


;; (defn -main [& args]
;;   (?<< (hfs-textline "junk" :sinkmode :replace)
;;        [?y]
;;         ([[0] [1] [2]] :> ?x)
;;         ;;  (rpn-eval ?x 1 {:f (fn [x y] (+ x y)) :a 2}   :> ?y)
;;         (+ (* ?x 10) 1 :> ?y)))

;; (defmain TestDog []
;;   (?<< (hfs-textline "junk" :sinkmode :replace)
;;        [?y]
;;        ([[0] [1] [2]] :> ?x)
;;        ;;  (rpn-eval ?x 1 {:f (fn [x y] (+ x y)) :a 2}   :> ?y)
;;        (+ (* ?x 10) 1 :> ?y)))



(comment

  ;; various examples to play with in a repl

  (??<< [?a]
        ([[1] [2] [3] [4] [5]] :> ?a))

  (macroexpand-1
   '(??<< [?a]
          ([[1] [2] [3] [4] [5]] :> ?a)))

  ;; test generator within let scope
  (let [src [[1] [2] [3]]]
    (??<< [?a] [src ?a]))



  (pp/pprint
   (macroexpand-1
    '(??<< [?b]
           ([[1] [2] [3] [4] [5]] :> ?a)
           (+ (* 2 ?a) 10 :> ?b))))


  (??<< [?b]
        ([[1] [2] [3] [4] [5]] :> ?a)
        (+ (* 2 ?a) 10 :> ?b))

  (let
      [v "?a"]
    (macroexpand-1
     '(??<< [?b]
           ([[1] [2] [3] [4] [5]] :> v)
           (+ (* 2 v) 10 :> ?b))))

  (let
      [v "?a"]
    (??<< [?b]
          ([[1] [2] [3] [4] [5]] :> v)
          (+ (* 2 v) 10 :> ?b)))


  ;; see what it expands to
  (pp/pprint
   (macroexpand-1
    '(??<< [?c]
           ([[1] [2] [3] [4] [5]] :> ?a)
           (+ ?a 10 :> ?b)
           (* (+ ?a ?b) (- ?b 3) :> ?c)
           (or (and (> ?a 1) (< ?a 4))
                 (< ?b 15)))))



;; actually do the eval
  (??<< [?c]
        ([[1] [2] [3] [4] [5]] :> ?a)
        (+ ?a 10 :> ?b)
        (* (+ ?a ?b) (- ?b 3) :> ?c)
        (not (< ?a 3))
        (or (and (> ?a 1) (< ?a 4))
              (< ?b 15)))


  (??<< [?b]
        ([[1] [2] [3] [4] [5]] :> ?a)
        (-> ?a inc (+ 10) :> ?b))

  (let
      [src [[[1 2 3]]  [[3 4 5]]]]
    (??<- [?s]
          [src ?vec]
          (reduce #'+ ?vec :> ?s)))


  (??<< [?b ?broot ?p]
        ([["1" "john"] ["2" "joe"]] :> ?a ?name)
        (+ ?#a 10  :> ?b)
        (Math/sqrt ?b :> ?broot)
        (.indexOf ?name "e" :> ?p))


  (??<< [?b ?broot ?p]
        ([["1" "john"] ["2" "joe"]] :> ?a ?name)
        (+ ?#a 10  :> ?b)
        (* (Math/sqrt ?b) 2.0 :> ?broot)
        (.indexOf ?name "e" :> ?p))


  (??<< [?b ?p]
        ([["1" "john"] ["2" "joe"]] :> ?a ?name)
        (+ ?#a 10  :> ?b)
        (.indexOf ?name "e" :> ?p))


  (macroexpand-1
   '(??<< [?f ?tot]
          ([[1 10] [2 20] [3 30] [1 11] [2 15]] :> ?f ?u)
          [c/count  :> ?n]
          (c/sum ?u :> ?tot)))

  (??<< [?f ?tot ?n]
         ([[1 10] [2 20] [3 30] [1 11] [2 15]] :> ?f ?u)
         [c/count :> ?n]
         (c/sum ?u :> ?tot))

  (defn qq [] (<- [?a] ([[1] [2]] :> ?a)))


  (macroexpand-1
   '(??<< [?a]
          ((qq):>  ?a)))

  (??<< [?a]
        ((qq)   :>  ?a))


  (let
      [agen [[1] [2] [3]]]
    (??<< [?a] [agen ?a] ))


  (let
      [is-even (fn [x] (even? x))]
    (??<< [?a] ([[1] [2] [3]] :> ?a) (is-even ?a) ))

  )
