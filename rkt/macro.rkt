;; Copyright (c) 2013 Amazon.com, Inc.  All rights reserved.

(require rackunit)

(define-syntax let   ; (let var val body)
  (lambda (stx)
    (let ((stx_list (syntax->list stx)))
      (let ((var  (cadr   stx_list))
            (val  (caddr  stx_list))
            (body (cadddr stx_list)))
        (quasisyntax
          ((lambda ((unsyntax var)) (unsyntax body))
           (unsyntax val)))))))

(check-eq? 12
  (let x 437 12))

(module Let racket
  (define-syntax let   ; (let var val body)
    (lambda (stx)
      (let ((stx_list (syntax->list stx)))
        (let ((var  (cadr   stx_list))
              (val  (caddr  stx_list))
              (body (cadddr stx_list)))
          (quasisyntax
            ((lambda ((unsyntax var)) (unsyntax body))
             (unsyntax val)))))))
  (require rackunit)
  (check-eq? 12
    (let x 437 12)))
(require 'Let)

(printf "success~n")
