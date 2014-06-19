// Copyright (c) 2012-2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionUtils.safeEquals;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents conditions raised within Fusion code, as opposed to failures
 * within the interpreter implementation.
 */
@SuppressWarnings("serial")
public class FusionException
    extends Exception
{
    static {
        // Force the SRE class to be loaded and initialized.  Otherwise we may
        // fail to do so in dire circumstances like stack overflow.
        StackRewriteException.initClass();

        // These alternatives did not work in the stack overflow case:
        //   Class c = StackRewriteException.class;
        //   StackRewriteException.class.getName();
    }

    private List<SourceLocation> myContinuation;

    // Constructors aren't public because I don't want applications to create
    // exceptions directly or subclass them.

    FusionException(String message)
    {
        super(message);
    }

    private FusionException(String message, Throwable cause, SourceLocation location)
    {
        super(message,
              cause instanceof FusionException ? cause : new StackRewriteException(cause, null));
        addContext(location);
    }

    FusionException(String message, Throwable cause)
    {
        this(message, cause, null);
    }

    FusionException(Throwable cause)
    {
        this(cause.getMessage(), cause, null);
    }

    FusionException(Throwable cause, SourceLocation location)
    {
        this(cause.getMessage(), cause, location);
//        addContext(location);
    }


    /**
     * See {@link StandardTopLevel#exceptionForExit(Throwable)}
     * for parallel code.
     */
    static FusionException withContext(Throwable e, SourceLocation location)
    {
        FusionException fe;
        if (e instanceof FusionException)
        {
            fe = ((FusionException) e);
        }
        else if (e instanceof FusionInterrupt)
        {
            throw (FusionInterrupt) e;
        }
        else
        {
            fe = new StackRewriteException(e, location);
        }
        fe.addContext(location);
        return fe;
    }


    /**
     * Prepends a now location to the continuation of this exception.
     *
     * @param location can be null to indicate an unknown location.
     */
    void addContext(SourceLocation location)
    {
        if (myContinuation == null)
        {
            myContinuation = new ArrayList<>(32);
            myContinuation.add(location);
        }
        else
        {
            SourceLocation prev =
                myContinuation.get(myContinuation.size() - 1);
            if (! safeEquals(prev, location))
            {
                // Collapse equal adjacent locations
                myContinuation.add(location);
            }
        }

        Throwable cause = getCause();
        if (cause instanceof FusionException)
        {
            ((FusionException) cause).addContext(location);
        }
    }


    /**
     * Prepends a now location to the continuation of this exception.
     *
     * @param stx can be null to indicate an unknown location.
     */
    void addContext(SyntaxValue stx)
    {
        if (stx != null)
        {
            addContext(stx.getLocation());
        }
    }


    // Before making this public, think about whether it needs Evaluator
    // and should throw FusionException
    void displayContinuation(Appendable out)
        throws IOException
    {
        if (myContinuation != null)
        {
            for (SourceLocation loc : myContinuation)
            {
                if (loc == null)
                {
                    out.append("\n  ...");
                }
                else
                {
                    out.append("\n  ...at ");
                    loc.display(out);
                }
            }
        }
    }


    List<StackTraceElement> translateContinuation()
    {
        if (myContinuation == null) return Collections.emptyList();

        ArrayList<StackTraceElement> elts =
            new ArrayList<>(myContinuation.size());

        for (SourceLocation loc : myContinuation)
        {
            if (loc != null)
            {
                StackTraceElement e = loc.toStackTraceElement();
                elts.add(e);
            }
        }

        return elts;
    }


    FusionException rewriteStackTrace(int framesToDrop)
    {
        if (myContinuation == null) return this;

        List<StackTraceElement> elts = translateContinuation();

        int size = elts.size();
        if (size != 0)
        {
            // Determine how many frames are below the rewrite zone.
            StackTraceElement[] oldTrace = new Exception().getStackTrace();
            int oldLen = oldTrace.length - framesToDrop;

            // Now get the "real" trace.
            oldTrace = getStackTrace();

            StackTraceElement[] trace = new StackTraceElement[size + oldLen];
            elts.toArray(trace);

            System.arraycopy(oldTrace, oldTrace.length - oldLen, trace, size, oldLen);

            setStackTrace(trace);

            myContinuation = null;
        }

        Throwable cause = getCause();
        if (cause instanceof FusionException)
        {
            ((FusionException) cause).rewriteStackTrace(0);
        }

        return this;
    }


    /**
     * Returns the message string given to the exception constructor.
     * This should be used instead of {@link #getMessage()} since the latter is
     * overridden here to delegate to {@link #displayMessage}.
     */
    final String getBaseMessage()
    {
        return super.getMessage();
    }

    void displayMessage(Evaluator eval, Appendable out)
        throws IOException, FusionException
    {
        String superMessage = getBaseMessage();
        if (superMessage != null)
        {
            out.append(superMessage);
        }
    }

    /**
     * @return the result of calling {@link #displayMessage}.
     */
    @Override
    public final String getMessage()
    {
        StringBuilder out = new StringBuilder();

        try
        {
            displayMessage(null, out);
            displayContinuation(out);
        }
        catch (IOException e) {}
        catch (FusionException e) {}

        return out.toString();
    }
}
