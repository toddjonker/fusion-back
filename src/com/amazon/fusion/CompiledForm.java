// Copyright (c) 2012-2020 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

/**
 * The results of the syntax preparation phase, ready for execution.
 */
interface CompiledForm
{
    /** A zero-length array. */
    CompiledForm[] EMPTY_ARRAY = new CompiledForm[0];


    /** Don't call directly! Go through the evaluator.
     *
     * @return not null.
     */
    Object doEval(Evaluator eval, Store store)
        throws FusionException;
}
