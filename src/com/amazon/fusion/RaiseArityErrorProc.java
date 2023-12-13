// Copyright (c) 2013-2024 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionNumber.checkIntArgToJavaInt;
import static com.amazon.fusion.FusionText.checkRequiredTextArg;
import java.util.Arrays;


final class RaiseArityErrorProc
    extends Procedure
{
    @Override
    Object doApply(Evaluator eval, Object[] args)
        throws FusionException
    {
        checkArityAtLeast(2, args);

        int arity        = checkIntArgToJavaInt(eval, this, 1, args);
        Object[] actuals = Arrays.copyOfRange(args, 2, args.length);

        Object where = args[0];
        if (where instanceof Procedure)
        {
            throw new ArityFailure((Procedure) where, arity, arity, actuals);
        }

        if (FusionText.isText(eval, where))
        {
            String name = FusionText.unsafeTextToJavaString(eval, where);
            if (name != null)
            {
                if (name.isEmpty()) name = "anonymous procedure";
                throw new ArityFailure(name, arity, arity, actuals);
            }
        }

        throw argFailure("procedure or non-null string or symbol", 0, args);
    }
}
