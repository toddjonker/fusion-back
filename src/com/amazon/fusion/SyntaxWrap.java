// Copyright (c) 2012-2020 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.util.Iterator;
import java.util.Set;

/**
 * A unit of lexical information wrapped around a {@link SyntaxValue}.
 */
abstract class SyntaxWrap
{
    static final SyntaxWrap[] EMPTY_ARRAY = new SyntaxWrap[0];


    /**
     * @param returnMarks <em>returns</em> the marks from this wrap and those
     * deeper. Must be mutable and not null.
     *
     * @return null indicates a free variable.
     */
    abstract Binding resolveMaybe(BaseSymbol name,
                                  Iterator<SyntaxWrap> moreWraps,
                                  Set<MarkWrap> returnMarks);


    Binding resolveTopMaybe(BaseSymbol name,
                            Iterator<SyntaxWrap> moreWraps,
                            Set<MarkWrap> returnMarks)
    {
        if (moreWraps.hasNext())
        {
            SyntaxWrap nextWrap = moreWraps.next();
            return nextWrap.resolveTopMaybe(name, moreWraps, returnMarks);
        }
        return null;
    }
}
