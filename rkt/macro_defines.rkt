#lang racket

(define-syntax define_macro_introduced_name
    (lambda (stx)
      (let [(content (cdr (syntax-e stx)))]
          (let [(name (syntax-e (car content)))
                (content_tail (cdr content))]
            (datum->syntax
              (quote-syntax context)
              (cons (quote-syntax define) (cons name content_tail))
              stx
              )))))
(provide define_macro_introduced_name)

(define-syntax defpub_macro_introduced_name
    (lambda (stx)
      (let [(content (cdr (syntax-e stx)))]
          (let [(name (syntax-e (car content)))
                (content_tail (cdr content))]
            (datum->syntax
              (quote-syntax context)
              (list (quote-syntax begin)
                (cons (quote-syntax define) (cons name content_tail))
                (list (quote-syntax provide) name))
              stx
              )))))
(provide defpub_macro_introduced_name)
