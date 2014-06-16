// Copyright (c) 2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import java.util.List;

/**
 * Internal, temporary exception that helps us rewrite the stack of a
 * non-{@link FusionException} so that it shows Fusion frames, not Java frames.
 */
@SuppressWarnings("serial")
class StackRewriteException
    extends FusionException
{
    /**
     * Used to force initialization of this class.
     * See static initializer in {@link FusionException}.
     */
    static void initClass() {}


    StackRewriteException(Throwable cause, SourceLocation loc)
    {
        super(cause);
        assert ! (cause instanceof FusionException);
        assert (cause instanceof RuntimeException || cause instanceof Error);

        addContext(loc);
    }


    @Override
    FusionException rewriteStackTrace(int framesToDrop)
    {
        Throwable cause = getCause();

        List<StackTraceElement> fusionTrace = translateContinuation();
        int fusionSize = fusionTrace.size();
        if (fusionSize != 0)
        {
            // Determine how many frames are below the rewrite zone.
            StackTraceElement[] bottomTrace = new Exception().getStackTrace();
            int bottomSize = bottomTrace.length - framesToDrop;
            // TODO Should this just use the current common frames?

            // Determine the top of the rewrite zone.
            StackTraceElement[] origTrace = cause.getStackTrace();
            StackTraceElement[] wrapTrace =  this.getStackTrace();

            int origIndex = origTrace.length;
            int wrapIndex = wrapTrace.length;
            while (--origIndex >= 0 && --wrapIndex >= 0
                   && origTrace[origIndex].equals(wrapTrace[wrapIndex]))
            { }
            int topSize = origIndex + 1;

            StackTraceElement[] newTrace =
                new StackTraceElement[topSize + fusionSize + bottomSize];
            fusionTrace.toArray(newTrace);

            // Shift Fusion frames down to make room for the original top.
            System.arraycopy(newTrace, 0, newTrace, topSize, fusionSize);
            System.arraycopy(origTrace, 0, newTrace, 0, topSize);
            System.arraycopy(origTrace, origTrace.length - bottomSize,
                             newTrace, topSize + fusionSize, bottomSize);

            cause.setStackTrace(newTrace);

            if (cause instanceof FusionInterrupt)
            {
                return new FusionInterruptedException((FusionInterrupt) cause);
            }
            else if (cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }
            else
            {
                throw (Error) cause;
            }
        }

        return this;
    }
}
