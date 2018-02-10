;; Copyright (c) 2014-2018 Amazon.com, Inc.  All rights reserved.

(require rackunit)


(define ns (make-base-namespace))

(eval '(define top "other-ns") ns)

(define top "this-ns")

(check-eq?
  (eval (quote top) ns)
  "other-ns")

; Fails in Racket 5.3, 6.5:
;   require: namespace mismatch; reference to a module that is not available
;
; This seems to contradict the documentation for namespace-syntax-introduce,
; which states:
;
;   The additional context is overridden by any existing top-level bindings in
;   the syntax object’s lexical information
;
; Here `top` has an existing top-level binding, yet its not being used.

(check-eq?
  (eval (quote-syntax top) ns)
  "other-ns")
