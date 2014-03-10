// Copyright (c) 2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

/**
 * Implements {@code #%top_interaction}.
 */
final class TopInteractionForm
    extends SyntacticForm
{
    TopInteractionForm()
    {
        super("form", "XXX");
        // TODO Document
    }


    @Override
    SyntaxValue expand(Expander expander, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        check(expander, stx).arityExact(2);

        final Evaluator eval = expander.getEvaluator();

        // TODO this isn't right, it prevents begin-splicing from happening.

        SyntaxValue subform = stx.get(eval, 1);
        return expander.expand(env, subform);
    }


    @Override
    CompiledForm compile(Evaluator eval, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        throw new IllegalStateException();
    }
}
