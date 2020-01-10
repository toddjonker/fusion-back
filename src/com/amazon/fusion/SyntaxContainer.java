// Copyright (c) 2012-2020 Amazon.com, Inc. All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionUtils.EMPTY_OBJECT_ARRAY;
import static com.amazon.fusion.SyntaxWraps.EMPTY_WRAPS;

abstract class SyntaxContainer
    extends SyntaxValue
{
    /**
     * The sequence of wraps around this value; not null.
     * Semantically, wraps only affect symbols and they should act as if they
     * are always pushed down immediately.  However, we cache them at
     * containers as an optimization. Any wraps are just being held here
     * lazily, waiting to be pushed down to all children once one is requested.
     */
    SyntaxWraps myWraps;

    SyntaxContainer(SourceLocation loc, Object[] properties, SyntaxWraps wraps)
    {
        super(loc, properties);

        assert wraps != null;
        myWraps = wraps;
    }

    SyntaxContainer(SourceLocation loc)
    {
        super(loc, EMPTY_OBJECT_ARRAY);
        myWraps = EMPTY_WRAPS;
    }


    /**
     * Equivalent to size() == 0.  Computing size of sexp is
     * expensive so we do this instead.
     */
    abstract boolean hasNoChildren()
        throws FusionException;

    /**
     * Only called when this container has children.
     * @param wraps is not null.
     */
    abstract SyntaxValue copyReplacingWraps(SyntaxWraps wraps)
        throws FusionException;


    @Override
    final SyntaxValue addWrap(SyntaxWrap wrap)
        throws FusionException
    {
        // Don't bother if this is an empty container.
        if (hasNoChildren()) return this;

        return copyReplacingWraps(myWraps.addWrap(wrap));
    }

    /**
     * Prepends a sequence of wraps onto our existing wraps.
     */
    @Override
    final SyntaxValue addWraps(SyntaxWraps wraps)
        throws FusionException
    {
        // Don't bother if this is an empty container.
        if (hasNoChildren()) return this;

        return copyReplacingWraps(myWraps.addWraps(wraps));
    }


    @Override
    boolean hasMarks(Evaluator eval)
    {
        return myWraps.hasMarks(eval);
    }
}
