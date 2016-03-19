// Copyright (c) 2013-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * Syntax wrap that's an ordered, mutable sequence of other wraps.
 */
class SequenceWrap
    extends SyntaxWrap
{
    private SyntaxWrap[] myWraps;

    SequenceWrap(SyntaxWrap... wraps)
    {
        assert wraps.length != 0;
        myWraps = wraps;
    }

    /**
     * Adds a new wrap to the front of this sequence.
     */
    void addWrap(SyntaxWrap wrap)
    {
        int suffixLen =  myWraps.length;
        int len = 1 + suffixLen;

        SyntaxWrap[] combined = new SyntaxWrap[len];
        combined[0] = wrap;
        System.arraycopy(myWraps, 0, combined, 1, suffixLen);

        myWraps = combined;
    }


    @Override
    Binding resolve(BaseSymbol name,
                    Iterator<SyntaxWrap> moreWraps,
                    Set<MarkWrap> returnMarks)
    {
        throw new IllegalStateException();
    }


    @Override
    Iterator<SyntaxWrap> iterator()
    {
        return Arrays.asList(myWraps).iterator();
    }


    @Override
    public String toString()
    {
        return "{{{Sequence wrap: " + Arrays.toString(myWraps) + "}}}";
    }

    @Override
    void dump(PrintStream out) throws IOException
    {
        out.print("SequenceWrap::[");
        for (SyntaxWrap wrap : myWraps)
        {
            wrap.dump(out);
            out.print(",");
        }
        out.print("]");
    }
}
