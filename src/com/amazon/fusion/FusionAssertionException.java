// Copyright (c) 2012-2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionIo.safeWrite;
import java.io.IOException;

@SuppressWarnings("serial")
public final class FusionAssertionException
    extends FusionErrorException
{
    private final String myExpression;
    private final Object myResult;

    /**
     * @param message may be null.
     * @param expression must not be null.
     * @param result must not be null.
     */
    FusionAssertionException(String message,
                             String expression,
                             Object result)
    {
        super(message);
        myExpression = expression;
        myResult = result;
    }

    /**
     * Returns the formatted message as provided by the application.
     *
     * @return may be null if no message values were provided.
     */
    public String getUserMessage()
    {
        return getBaseMessage();
    }

    @Override
    void displayMessage(Evaluator eval, Appendable out)
        throws IOException, FusionException
    {
        out.append("Assertion failure: ");

        super.displayMessage(eval, out);

        out.append("\nExpression: ");
        out.append(myExpression);
        out.append("\nResult:     ");
        safeWrite(eval, out, myResult);
    }
}
