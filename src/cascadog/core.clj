(ns cascadog.core
  "Friendly variants of Cascalog macros"
  (:use cascalog.api
        clojure.core)
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [jackknife.seq :as js]
            [cascalog.logic
             [ops :as c]
             [vars :as v]
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


(defn lambda
  "create anonymous form of function f with arity"
  [f arity]
  (let
      [args `[~@(repeatedly arity gensym)]]
    `(fn ~args ~(cons f args))))


(defn rpn-helper-old
  "convert expression to RPN notation"
  ([] nil)
  ([expr]
     (if (or (and (not (list? expr))
                  (not (seq? expr)))
             (and (list? expr)
                  (= (first expr) 'var)))
       (list expr)
       (if (#{'-> '->>} (first expr))
         (rpn-helper-old (macroexpand expr))
         (let
             [[fn & args] expr]
           (vec
            (concat
             (mapcat rpn-helper-old args)
             [{:f (lambda fn (count args)) :a (count args) }])))))))


(defn rpn-helper
  "convert expression to RPN notation"
  ([] nil)
  ([expr]
     (if (or (and (not (list? expr))
                  (not (seq? expr)))
             (and (coll? expr)
                  (= (first expr) 'var)))
       (list expr)
       (if (#{'-> '->>} (first expr))
         (rpn-helper (macroexpand expr))
         (let
             [[fn & args] expr]
           (vec
            (concat
             (mapcat rpn-helper args)
             [{:f (lambda fn (count args)) :a (count args) }])))))))




(defn rpn-eval
  "evaluate expression in reverse polish notation"
  [& args]
  (let
      [sweep (fn [sofar x]
               (if (and (map? x)
                        (x :f))
                 (let [k (- (count sofar) (x :a))]
                   (into (vec (take k sofar))
                         [(apply (x :f) (nthrest sofar k))]))
                 (conj sofar x)))]
    (first (reduce sweep [] args))))

(defn to-rpn
  "convert to RPN but no eval"
  [form]
  `(rpn-eval ~@(rpn-helper form)))

(defn predicate-parts
  "parts of a predicate:
   :pre = before first operator
   :rest = including first operator plus rest"
  [pred]
  {:pre (take-while #(not (v/selector? %) ) pred)
   :rest (drop-while #(not (v/selector? %)) pred)})

(defn not-rpn-able?
  "detect form that should not be converted to rpn"
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
   (re-find #"cascalog\."  ;; possible aggregator, has class "..cascalog.blah.."
            (try (str (type (eval (first form))))
                 (catch Exception e "")))))

(defn clause-to-rpn
  "transform cascalog clause to rpn"
  [form]
  (if (vector? form) ;; simply change [...] => (...)
    (concat '() form)
    (if (not-rpn-able? form)
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
              `(~@(to-rpn (first form))  ~@(rest form))) ;; ((q 10) :> ?a)
            `(~@(to-rpn pre-op)  ~@post-op))) ;; (* ?a 10 :> ?b)
        (to-rpn form))))) ;; (#'and (< ?a 10) (> ?a 1))


(defmacro ??<< [outvars & predicates]
  (concat `(??<- ~outvars)
          (map clause-to-rpn predicates)
          (get-casca-read-nums predicates)))

(defmacro ?<< [sink vars & predicates]
  (concat `(?<- ~sink ~vars)
          (map clause-to-rpn predicates)
          (get-casca-read-nums predicates)))

(defmacro << [outvars & predicates]
  (concat `(<- ~outvars)
          (map clause-to-rpn predicates)
          (get-casca-read-nums predicates)))

(comment

  (do (load-file "src/cascadog/core.clj")
      (in-ns 'cascadog.core))

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
