// Copyright (c) 2012-2020 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Records the lexical information associated with a {@link SyntaxValue} during
 * expansion and compilation.  An instance may be associated with more than one
 * syntax object, so the underlying object is not referenced here.
 */
class SyntaxWraps
{
    static final SyntaxWraps EMPTY_WRAPS = new Empty();

    /** Not null. */
    private final SyntaxWrap[] myWraps;

    static SyntaxWraps make(SyntaxWrap initialWrap)
    {
        return new SyntaxWraps(initialWrap);
    }

    static SyntaxWraps make(SyntaxWrap... initialWraps)
    {
        return new SyntaxWraps(initialWraps);
    }

    /** Special constructor for {@link Empty} subclass. */
    private SyntaxWraps()
    {
        myWraps = SyntaxWrap.EMPTY_ARRAY;
    }

    private SyntaxWraps(SyntaxWrap initialWrap)
    {
        myWraps = new SyntaxWrap[] { initialWrap };
    }

    private SyntaxWraps(SyntaxWrap[] wraps)
    {
        assert wraps.length > 0;
        myWraps = wraps;
    }


    SyntaxWraps addWrap(SyntaxWrap wrap)
    {
        int suffixLen =  myWraps.length;
        int len = 1 + suffixLen;

        SyntaxWrap[] combined = new SyntaxWrap[len];
        combined[0] = wrap;
        System.arraycopy(myWraps, 0, combined, 1, suffixLen);

        return new SyntaxWraps(combined);
    }


    /**
     * Prepends a sequence of wraps onto our existing ones.
     * It is assumed that the given list will not be modified later and can
     * therefore be shared.
     */
    SyntaxWraps addWraps(SyntaxWraps wraps)
    {
        // TODO this should use a linked-list to avoid copies
        int prefixLen = wraps.myWraps.length;
        int suffixLen =  this.myWraps.length;
        int len = prefixLen + suffixLen;

        SyntaxWrap[] combined = new SyntaxWrap[len];
        System.arraycopy(wraps.myWraps, 0, combined, 0, prefixLen);
        System.arraycopy(myWraps, 0, combined, prefixLen, suffixLen);

        return new SyntaxWraps(combined);
    }


    /**
     * @return not null.
     */
    Set<MarkWrap> computeMarks()
    {
        Set<MarkWrap> marks = null;

        for (SyntaxWrap wrap : myWraps)
        {
            if (wrap instanceof MarkWrap)
            {
                MarkWrap mark = (MarkWrap) wrap;

                if (marks == null)
                {
                    marks = new HashSet<>();
                    marks.add(mark);
                }
                else if (! marks.add(mark))
                {
                    marks.remove(mark);
                }
            }
        }

        if (marks == null) marks = Collections.emptySet();
        return marks;
    }


    boolean hasMarks(Evaluator eval)
    {
        // We have to walk all wraps to match up cancelling pairs of marks.
        return ! computeMarks().isEmpty();
    }


    /**
     * Attempt to resolve the given symbol, assumed this object represents its
     * lexical information.
     *
     * @return null is equivalent to a {@link FreeBinding}, and either may be
     * returned.
     */
    Binding resolveMaybe(BaseSymbol name)
    {
        return doResolveMaybe(name, new HashSet<MarkWrap>());
    }

    private Binding doResolveMaybe(BaseSymbol name, Set<MarkWrap> marks)
    {
        Iterator<SyntaxWrap> i = Arrays.asList(myWraps).iterator();
        SyntaxWrap wrap = i.next();
        return wrap.resolveMaybe(name, i, marks);
    }

    /**
     * Like {@link #resolveMaybe}, but only resolving to a top-level binding.
     *
     * @return null is equivalent to a {@link FreeBinding}, and either may be
     * returned.
     */
    Binding resolveTopMaybe(BaseSymbol name)
    {
        Iterator<SyntaxWrap> i = Arrays.asList(myWraps).iterator();

        SyntaxWrap wrap = i.next();
        Set<MarkWrap> marks = new HashSet<>();
        return wrap.resolveTopMaybe(name, i, marks);
    }

    /**
     * @return not null.
     */
    BoundIdentifier resolveBoundIdentifier(BaseSymbol name)
    {
        Set<MarkWrap> marks = new HashSet<>();
        Binding binding = doResolveMaybe(name, marks);
        if (binding == null) binding = new FreeBinding(name);
        return new BoundIdentifier(binding, marks);
    }


    private static final class Empty extends SyntaxWraps
    {
        SyntaxWraps addWrap(SyntaxWrap wrap)
        {
            return new SyntaxWraps(new SyntaxWrap[] { wrap });
        }

        /**
         * Prepends a sequence of wraps onto our existing ones.
         * It is assumed that the given list will not be modified later and can
         * therefore be shared.
         */
        SyntaxWraps addWraps(SyntaxWraps wraps)
        {
            return wraps;
        }

        /**
         * @return not null.
         */
        Set<MarkWrap> computeMarks()
        {
            return Collections.emptySet();
        }

        boolean hasMarks(Evaluator eval)
        {
            return false;
        }

        Binding resolveMaybe(BaseSymbol name)
        {
            return null;
        }

        Binding resolveTopMaybe(BaseSymbol name)
        {
            return null;
        }

        BoundIdentifier resolveBoundIdentifier(BaseSymbol name)
        {
            return new BoundIdentifier(new FreeBinding(name),
                                       Collections.<MarkWrap>emptySet());
        }
    }
}
