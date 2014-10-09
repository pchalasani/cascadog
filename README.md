### Summary

The `cascadog.core` namespace introduces new operators `??<<, ?<<,
<<` corresponding to the original `cascalog.api` macros `??<-`, `?<-`
and `<-`,  so that we can have clauses with the following features that
would normally cause errors:

- Cascalog variables can appear arbitrarily deep in nested parens,
  either in filtering clauses or operator clauses (that map a set of
  fields to define one or more new fields).
- Use syntax `?#name` or `!#name` to implicitly use string variables as
  numerical (see below).
- The boolean (and any other) macros `and,or` can be used directly
  (the original Cascalog require using the corresponding vars `#'and`
  and `#'or`).
- Use threading macros (`->`, `->>`) in Cascalog predicates, e.g.
```
(->  ?a inc double :> ?b)
```
  (does not work in original Cascalog)
- Define anonymous functions within Cascalog predicates, e.g.
```
((fn [x] (inc x)) ?a :> ?b)
```
  (Cascalog complains about this too!).
- Java methods can appear directly in clauses, whereas with the original
  cascalog operators, a function definition must be used to wrap
  them. E.g. one can have a clause like `(.indexOf Name "M" :>  ?x)`
- Static methods of Java classes can appear directly in clauses (again
  these cause errors in original Cascalog), e.g. `(Math/sqrt ?a :> ?r)`
- The above features can be combined inside arbitrarily deeply nested parens,
  e.g.
```
  (or (< (* (Math/sqrt ?b) 2.0) 4.0)
      (> ?#a 1) :> ?fun)
```
- Use square brackets (see below) to prevent transformation of any clause,
  e.g. `[c/sum ?a :> ?b]` will simply be changed to `(c/sum :> ?b)` and
  passed on to the appropriate original Cascalog macro. In this case of
  course you can't avail of any of the above features.



### Cascalog variables inside parens

Often in cascalog we would like to say something like

```
(??<<
 [?a ?c]
 ( [[1 10] [2 20]] :> ?a ?b)
 ( (and (> ?a 1) (< ?a 5)))
 ( (/ (* ?a 10) ?b ) :> ?c))
```

The last 2 clauses above will cause errors since cascalog variables can
only appear at the *top level* of a clause. To get around we go through
the tedious process of defining a separate mapop or a filter-op. This is
particularly irksome when trying to run ad-hoc queries on cluster data
for example.

Using the `cascadog.core` namespace this can be avoided.
```
(ns myproj.core
  (:use
      cascalog.api
      cascadog.core))
```

The new operator `??<<` allows nested cascalog variables as in:

```
(??<<
  [?a ?c]
  ( [[1 10] [2 20]] :> ?a ?b)
  (and (> ?a 1) (< ?a 5))
  (/ (* ?a (+ ?b 10)) ?b  :> ?c)
 )

```
A few things to note:
- the cascalog variables can appear in an *arbitrarily deeply nested*
  expression.
- the boolean functions `or,and` can be used *directly*, whereas the
  original cascalog operators require referring them via the vars `#'or`
  and `#'and` since they really are macros and not functions.
- for a clause that defines a map, the expression must be at the start
  of the clause, which is the standard way such a clause is almost
  always written.
- for those curious, the flattening is achieved by first converting to a
  form of *Reverse Polish Notation* (or postfix notation).
- any function of any arity can be used on the cascalog variables.


### Compact notation to handle fields as numerical

Another annoyance when using Cascalog is that all fields are read in as
strings, so to handle numerical fields we need to always insert clauses
that convert the field to numerical, e.g.

```
(??<<
 [?n-a ?c]
 ( [["1" "10"] ["2" "20"]] :> ?a ?b)
 (read-string ?a :> ?n-a)
 (read-string ?b :> ?n-b)
 (and (> ?n-a 1) (< ?n-a 5))
 (/ (* ?n-a 10) ?n-b :> ?c)
)

```

The `cascadog.core` namespace helps with this as well. The above
query simplies to:

```
(use 'cascalog.core)

(??<<
 [?n-a ?c]
 ( [["1" "10"] ["2" "20"]] :> ?a ?b)
 (and (> ?#a 1) (< ?#a 5))
 (/ (* ?#a 10) ?#b :> ?c)
)
```

Whenever a variable of the form `?#a` (in general starting with `?#` or
`!#`) is encountered, the new `??<<` macro automatically generates the
corresponding  `(read-string )` clause to generate the variable, in this
case:
```
(read-string ?a :> ?#a)
```

### Special syntax to prevent transformation

There may sometimes be predicates within a Cascalog query that we
don't want to be transformed by our new macros. This could be because
they might contain special syntax that's not handled by our new macros,
or they invoke other queries/aggregates that our new macros cannot
handle correctly.  It's very easy to prevent a predicate from being
handled by our macros: just use *square brackets* instead of
parens for these, as in the example below. When such a predicate is
encountered, the square brackets are replaced by parens and passed on to
the appropriate original Cascalog operator. Note that when using square
brackets you cannot use any of the special features mentioned above.

```
(??<< [?f ?tot ?n]
      ([[1 10] [2 20] [3 30] [1 11] [2 15]] :> ?f ?u)
      [c/count :> ?n] ;; changed to (c/count :> ?n)
      [c/sum ?u :> ?tot] ;; changed to (c/sum ?u :> ?to)
)
```


### Under the hood

It's possible that the new macros sometimes lead to errors or strange
results. In such cases it helps to see what these macros are doing, using
`macroexpand-1`. For example,

```
(use 'clojure.pprint)
(pprint
   (macroexpand-1
    '(??<< [?b]
           ([[1] [2] [3] [4] [5]] :> ?a)
           (+ (* 2 ?a) 10 :> ?b))))
```
which produces this output

```
(cascalog.api/??<-
 [?b]
 ([[1] [2] [3] [4] [5]] :> ?a)
 (cascadog.core/rpn-eval
  2
  ?a
  {:f
   #<cascalog$eval6648$fn__6649 cascadog.core$eval6648$fn__6649@cc9d2d3>,
   :a 2}
  10
  {:f
   #<cascalog$eval6654$fn__6655 cascadog.core$eval6654$fn__6655@2efbce1e>,
   :a 2}
  :>
  ?b))
```

This shows that the first clause was not modified, and the second clause
was *flattened* (i.e. re-written so all cascalog variables are at the
top level) into RPN (Reverse Polish Notation, or postfix) notation. The
maps `{:f ..., :a ...}` contain anonymous functions and arities so that
the RPN evaluator (`rpn-eval`) knows how many preceding arguments to
consume when evaluating each function.
