// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;

import dev.ionfusion.fusion.FusionSymbol.BaseSymbol;
import dev.ionfusion.fusion._private.InternMap;


/**
 * The central factory for producing Fusion values.  Long term, all value
 * creation should flow through here, so implementations can be swapped,
 * instrumented, etc. without requiring broad changes.
 * <p>
 * Ideally, the implementations of various Fusion value types would not be
 * coupled to a specific ValueSpace implementation. However, most types are tied
 * to {@link BaseSymbol} for annotations, so things are tightly coupled.
 */
class StandardValueSpace
    implements ValueSpace
{
    /**
     * General interning table for non-symbol values.
     * Symbol interning is high traffic, so we separate it to reduce contention.
     */
    private static final
    InternMap<Object, Object> ourInternedValues = new InternMap<>(o -> o, 256);

    // TODO PERF: Perhaps add a method to sweep the intern tables.
    // Because entries are only purged on access, we could end up with
    // a bunch of garbage in there after code compilation is done, and unless
    // new symbols are instantiated there won't be any access to the map and no
    // garbage released. Perhaps it's worth expunging the map (via size())
    // after significant processing events like compiling code.


    @SuppressWarnings("unchecked")
    public <T> T intern(T value)
    {
        return (T) ourInternedValues.intern(value);
    }


    //=========================================================================
    // Symbols

    /**
     * Interning table for unannotated, non-null symbols.
     */
    private static final
    InternMap<String, BaseSymbol> ourActualSymbols =
        new InternMap<>(FusionSymbol::makeActualSymbol, 256);


    @Override
    public BaseSymbol makeSymbol(String text)
    {
        return text != null ? makeActualSymbol(text) : FusionSymbol.NULL_SYMBOL;
    }

    /**
     * @param text must not be null.
     */
    public BaseSymbol makeActualSymbol(String text)
    {
        // InternMap enforces the non-null key.
        return ourActualSymbols.intern(text);
    }


    public BaseSymbol[] makeSymbols(String... texts)
    {
        int len = texts.length;
        if (len == 0) return BaseSymbol.EMPTY_ARRAY;

        BaseSymbol[] syms = new BaseSymbol[len];
        for (int i = 0; i < len; i++)
        {
            syms[i] = makeSymbol(texts[i]);
        }
        return syms;
    }

    public BaseSymbol[] makeActualSymbols(String... texts)
    {
        int len = texts.length;
        if (len == 0) return BaseSymbol.EMPTY_ARRAY;

        BaseSymbol[] syms = new BaseSymbol[len];
        for (int i = 0; i < len; i++)
        {
            syms[i] = makeActualSymbol(texts[i]);
        }
        return syms;
    }


    /**
     * @param text may be null to make {@code null.symbol}.
     * @param annotations must not be null and must not contain null elements.
     *
     * @return not null.
     */
    public BaseSymbol makeAnnotatedSymbol(String text, String... annotations)
    {
        return makeSymbol(text).annotate(this, makeActualSymbols(annotations));
    }


    //=========================================================================
    // Syntax symbols

    @Override
    public SyntaxSymbol makeSyntaxSymbol(String text)
    {
        return makeSyntaxSymbol(makeSymbol(text), null);
    }

    SyntaxSymbol makeSyntaxSymbol(BaseSymbol datum)
    {
        return makeSyntaxSymbol(datum, null);
    }

    SyntaxSymbol makeSyntaxSymbol(String text, SourceLocation loc)
    {
        return makeSyntaxSymbol(makeSymbol(text), loc);
    }

    SyntaxSymbol makeSyntaxSymbol(BaseSymbol datum, SourceLocation loc)
    {
        return SyntaxSymbol.make(loc, datum);
    }
}
