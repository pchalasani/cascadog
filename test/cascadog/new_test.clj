(ns cascadog.new-test
  "Test new cascadog syntax"
  (:use clojure.test
        [midje sweet cascalog]
        [cascalog.logic.testing :exclude [test?<- thrown?<-]]
        cascalog.api
        cascadog.core)
  (:require [cascalog.logic
             [ops :as c]]))

;; Redefine test fns to use cascadog op <<

(defmacro test?<- [& args]
  (let [[begin body] (if (keyword? (first args))
                       (split-at 2 args)
                       (split-at 1 args))]
    `(test?- ~@begin (<< ~@body))))

(defmacro thrown?<- [error & body]
  `(is (~'thrown? ~error (<< ~@body))))


;; New tests: cascadog passes but cascalog fails

(deftest test-simple-gen
  (test?<- [[1] [2] [3] [4] [5]]
           [?a]
           ([[1] [2] [3] [4] [5]] :> ?a)))


(deftest test-let-gen-square
  (let [src [[1] [2] [3]]]
    (test?<- [[1] [2] [3]]
             [?a]
             [src ?a])))


(deftest test-let-gen-paren
  (let [src [[1] [2] [3]]]
    (test?<- [[1] [2] [3]]
             [?a]
             (src :> ?a))))

(deftest test-nested-var
  (test?<- [[12] [14] [16] [18] [20]]
           [?b]
           ([[1] [2] [3] [4] [5]] :> ?a)
           (+ (* 2 ?a) 10 :> ?b)))

(deftest test-nested-string-var
  (let
      [v "?a"]
    (test?<- [[12] [14] [16] [18] [20]]
             [?b]
             ([[1] [2] [3] [4] [5]] :> v)
             (+ (* 2 v) 10 :> ?b))))


(deftest test-nested-bools
  (test?<- [[160] [198]]
           [?c]
           ([[1] [2] [3] [4] [5]] :> ?a)
           (+ ?a 10 :> ?b)
           (* (+ ?a ?b) (- ?b 3) :> ?c)
           (not (< ?a 3))
           (or (and (> ?a 1) (< ?a 4))
               (< ?b 15))))


(deftest test-thread-macro
  (test?<-  [[12] [13] [14] [15] [16]]
            [?b]
            ([[1] [2] [3] [4] [5]] :> ?a)
            (-> ?a inc (+ 10) :> ?b)))


(deftest test-reduce
  (let
      [src [[[1 2 3]]  [[3 4 5]]]]
    (test?<- [[6] [12]]
             [?s]
             [src ?vec]
             (reduce #'+ ?vec :> ?s))))

(deftest test-nested-num-vars
  (test?<- [[8] [10] [12]]
           [?y]
           ([["1"] ["2"] ["3"]] ?x)
           (+ (* (- ?#x 2) 2) 10 :> ?y )))


(deftest test-java-method
  "Direct use of java methods, with nesting"
  (test?<- [[2] [0]]
           [?p]
           ([["john"] ["mary"]] :> ?name)
           (+ (.indexOf ?name "a") (.indexOf ?name "n") :> ?p)))


(deftest test-java-static
  "Direct use of java static fns, with nesting"
  (test?<- [[11] [14] [16]]
           [?p]
           ([[4] [-1] [-3]] :> ?x)
           (+ (Math/abs (- ?x 3)) 10  :> ?p)))

(defn garbage [x & stuff] (+ x 1))

(deftest test-defn
  (test?<- [[1] [11] [21]]
           [?y]
           ([[0] [1] [2]] :>  ?x)
           (garbage (* ?x 10) 2 :> ?y)))

(deftest test-let-fn
  (let [addem (fn [x y] (+ x y)) ]
    (test?<- [[1] [11] [21]]
             [?y]
             ([[0] [1] [2]] :> ?x)
             (addem (* ?x 10) 1 :> ?y))))


(deftest test-anon-fn
  (test?<- [[1] [2] [3]]
           [?y]
           ([[0] [1] [2]] :> ?x)
           ((fn [x y] (+ x y)) ?x 1 :> ?y)))
