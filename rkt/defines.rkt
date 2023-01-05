#lang racket

(module M1 racket
  (require "macro_defines.rkt")
  (define multiple 1)
  (defpub_macro_introduced_name multiple 1658))

(module M2 racket
  (define multiple 2)
  (provide multiple))


(require 'M1)
;(require 'M2)

(define sum (+ multiple 1))

(define multiple 10)  ;; This is surprising and seems to contradict Racket docs.
