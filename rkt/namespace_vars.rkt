"Copyright (c) 2013-2016 Amazon.com, Inc.  All rights reserved."

(require rackunit "fusion.rkt")
(require (for-syntax "fusion.rkt"))


"Check handling of macro-introduced identifiers inside a module."
(define_syntax make_module
  (lambda (stx)
    (lets ((args (tail (syntax_unwrap stx)))
           (expr (head args)))
      (quasisyntax
        (module made racket
          (unsyntax expr))))))


"A top-level variable brought into a generated module cannot be used within it."
(define (times7 n) (* n 7))
(expect_syntax_exn
  (make_module (times7 3)))

"An imported variable brought into a generated module cannot be used within it."
"  This succeeds in Racket < 6.3"
(expect_syntax_exn
  (make_module (* 7 3)))



(define other_namespace
  (make-base-namespace))

(expect_variable_exn
  (eval (quote_syntax top) other_namespace))

(define top "this-ns")
(eval (quote (define top "other-ns")) other_namespace)


"A top-level variable brought into another top-level rebinds within it."
(check === "other-ns"
  (eval (quote_syntax top) other_namespace))

"Or not"
(eval (quote-syntax top) other_namespace)
(module M racket
  (provide M_good_eval M_do_eval)
  (define other_namespace (make-base-namespace))
  (eval (quote (define top "other-ns")) other_namespace)
  (define top "M-ns")
  (define (M_good_eval)
    (eval (quote top) other_namespace))
  (define (M_do_eval)
    (eval (quote-syntax top) other_namespace))) // Fails: namespace mismatch
(require 'M)
(check === "other-ns" (M_good_eval))
(expect_syntax_exn (M_do_eval))

"An imported variable brought into another top-level rebinds within it."
(check === 21
  (eval
    (quote_syntax (* 7 3))
    other_namespace))

(eval
  (quote (define * +))
  other_namespace)

(check === 21
  (eval
    (quote_syntax (* 7 3))
    other_namespace))


"Test a binding that's required here, but not bound there"
(module M racket
  (define splort "M splort")
  (provide splort))
(require 'M)
(check === "M splort" splort)
(expect_syntax_exn
  (eval (quote_syntax splort) other_namespace))


(eval
  (quote (define mul *))
  other_namespace)

(check === 10
  (eval
    (quote_syntax (mul 7 3))
    other_namespace))
