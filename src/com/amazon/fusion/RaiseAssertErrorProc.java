// Copyright (c) 2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionString.checkNullableStringArg;
import static com.amazon.fusion.FusionString.checkRequiredStringArg;

final class RaiseAssertErrorProc
    extends Procedure
{
    @Override
    Object doApply(Evaluator eval, Object[] args)
        throws FusionException
    {
        checkArityExact(3, args);

        String expression = checkRequiredStringArg(eval, this, 0, args);
        Object result     = args[1];
        String message    = checkNullableStringArg(eval, this, 2, args);

        throw new FusionAssertionException(message, expression, result);
    }
}
