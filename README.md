### Summary

The `cascadog.core` namespace introduces new operators `??<<, ?<<,
<<` corresponding to the original `cascalog.api` macros `??<-`, `?<-`
and `<-`,  so that we can have predicates with the following features that
would normally cause errors:

- Cascalog variables can appear arbitrarily deep in nested parens,
  either in filtering predicates or operator predicates (that map a set of
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
- Java methods can appear directly in predicates, whereas with the original
  cascalog operators, a function definition must be used to wrap
  them. E.g. one can have a predicate like `(.indexOf Name "M" :>  ?x)`
- Static methods of Java classes can appear directly in predicates (again
  these cause errors in original Cascalog), e.g. `(Math/sqrt ?a :> ?r)`
- The above features can be combined inside arbitrarily deeply nested parens,
  e.g.
```
  (or (< (* (Math/sqrt ?b) 2.0) 4.0)
      (> ?#a 1) :> ?fun)
```
- Use square brackets (see below) to prevent transformation of any predicate,
  e.g. `[c/sum ?a :> ?b]` will simply be changed to `(c/sum :> ?b)` and
  passed on to the appropriate original Cascalog macro. In this case of
  course you can't avail of any of the above features.

### How to use in your project.

Say you have a project `dog`. With leiningen, you need to include the
dependency
```
[pchalasani/cascadog "0.1.5"]
```

A typical `project.clj` might look like this:

```
(defproject dog "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ["-Xmx2000m"
             "-server"
             "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
  :aot [dog.core]
  :source-paths ["src/"]
  :repositories [["conjars" "http://conjars.org/repo/"]
                 ["clojars" "http://clojars.org/repo"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [pchalasani/cascadog "0.1.4"]
                 [cascalog/cascalog-core "2.1.1"]
                 [org.apache.hadoop/hadoop-core "1.2.1"]]
  :profiles {
             :provided
             {:dependencies [[org.apache.hadoop/hadoop-core "1.2.1"]]}})
```

And your namespace declaration would look like:

```
(ns dog.core
  (:use
     cascadog.core
     cascalog.api
     [cljutils misc])
  (:require
     [cascalog.logic [fn :as s]]) ;; serializable functions
  (:gen-class))
```

### Cascalog variables inside parens

Often in cascalog we would like to say something like

```
(??<-
 [?a ?c]
 ( [[1 10] [2 20]] :> ?a ?b)
 ( (and (> ?a 1) (< ?a 5)))
 ( (/ (* ?a 10) ?b ) :> ?c))
```

The last 2 predicates above will cause errors since cascalog variables can
only appear at the *top level* of a predicate. To get around we would
normally go through the tedious process of defining a separate mapop or a filter-op. This is
particularly irksome when trying to run ad-hoc queries on cluster data
for example.

Using the `cascadog.core` namespace this can be avoided. The new operator `??<<` allows nested cascalog variables as in:

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
- for a predicate that defines a map operation, the expression must be at the start
  of the predicate, which is the standard way such a predicate is almost
  always written.

### Compact notation to handle fields as numerical

Another annoyance when using Cascalog is that all fields are read in as
strings, so to handle numerical fields we need to always insert predicates
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
corresponding  `(read-string )` predicate to generate the variable, in this
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
encountered, the square brackets are replaced by parens and passed through to
the appropriate original Cascalog operator. Note that when using square
brackets you cannot use any of the special features mentioned above.

```
(??<< [?f ?tot ?n]
      ([[1 10] [2 20] [3 30] [1 11] [2 15]] :> ?f ?u)
      [c/count :> ?n] ;; changed to (c/count :> ?n)
      [c/sum ?u :> ?tot] ;; changed to (c/sum ?u :> ?to)
)
```

### Use cascalog's serializable functions wherever possible.

Whenever you need to define a function to be used within a cascalog
predicate, you are less likely to get errors if you use the `fn`
provided by the `cascalog.logic.fn` namespace, instead of the one from
`clojure.core`. Typically you would do this:

```
(ns dog.core
  (:use
    cascadog.core
    cascalog.api
  (:require
   [cascalog.logic [fn :as s]]) ;; serializable functions
  (:gen-class))


  (let
      [myvar "?x"
       myfn (s/fn [a b] (+ a b))]
      (?<< (hfs-textline "junk" :sinkmode :replace)
           [?y]
           ([[1] [2] [3]] :> myvar)
           (+ (* (Math/pow myvar 2.0) 2)
              (Math/sqrt myvar)
              myvar 1 :> ?z)
           (myfn ?z 100 :> ?y)))))
```

### Generators are a special case

The new `cascadog` macros cannot handle a generator
that does not have an explicit `:>` operator, since it looks similar to
a filtering predicate, and there's no easy way to distinguish
these. Therefore when using the `cascadog` macros, you have 2 options:
- use square brackets so that it's passed-through to the original
cascalog macros (this is the *preferred* option), e.g.:
```
(let [src [[1][2][3]] ]
   (??<< [?y]
         [ src ?x ]
         (+ ?x 1 :> ?y)))
```
- use an explicit `:>` operator, e.g.
```
(let [src [[1][2][3]] ]
   (??<< [?y]
         (src :> ?x)
         (+ ?x 1 :> ?y)))
```


### Under the hood

The main trick is to *flatten* a predicate using a serializable anonymous
function (from the original cascalog library) so all variables (cascalog or otherwise) appear at the top level.
We can see how this is done using `macroexpand-1` (this is also useful
for debugging when the new macros produce strange results). For example,

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
 ((cascalog.logic.fn/fn [?a__] (+ (* 2 ?a__) 10)) ?a :> ?b))
```
