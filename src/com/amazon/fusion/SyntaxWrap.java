// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import java.io.PrintStream;
import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Linked-list of lexical information wrapped around a {@link SyntaxValue}
 * hierarchy.
 */
abstract class SyntaxWrap
{
    abstract void dump(PrintStream out) throws IOException;


    /**
     * @param returnMarks <em>returns</em> the marks from this wrap and those
     * deeper. Must be mutable and not null.
     *
     * @return null indicates a free variable.
     */
    abstract Binding resolve(BaseSymbol name,
                             Iterator<SyntaxWrap> moreWraps,
                             Set<MarkWrap> returnMarks);


    Binding resolveTop(BaseSymbol name,
                       Iterator<SyntaxWrap> moreWraps,
                       Set<MarkWrap> returnMarks)
    {
        if (moreWraps.hasNext())
        {
            SyntaxWrap nextWrap = moreWraps.next();
            return nextWrap.resolveTop(name, moreWraps, returnMarks);
        }
        return resolve(name, moreWraps, returnMarks);
    }

    /**
     * Returns an iterator over sub-wraps, if this is a composite, or null
     * otherwise.
     *
     * @return a non-empty iterator, or null.
     */
    abstract Iterator<SyntaxWrap> iterator();
}
