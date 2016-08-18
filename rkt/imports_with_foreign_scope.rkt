#lang racket

; This demonstrates that foreign scope affects module-level bindings
; added by forms like only-in.  Here a macro introduces two different
; bindings named `dual`, and both with the same marks but different
; lexical context at the macro-definition site.
; The imports can't be used from elsewhere since the lexical scope is gone.

; UPDATE: This code succeeds in Racket 5.3, but fails in Racket 6.5 with:
;
; module: identifier already imported from a different source in:
;   dual
;   (rename (submod "." M) dual ten)
;   (rename (submod "." M) dual one)

(module M racket
  (define one 1)
  (define ten 10)
  (provide one ten))

(define-syntax boom
  (lambda (stx)
    (datum->syntax (quote-syntax context)
      (quasisyntax
        (begin
          (unsyntax
            (let ([dual "first"])
              (quote-syntax
                (require (only-in (submod "." M)
                                  [one dual])))))
          (unsyntax
            (let ([dual "second"])
              (quote-syntax
                (require (only-in (submod "." M)
                                  [ten dual]))))))))))

(boom what ever)

(define dual 'module)
