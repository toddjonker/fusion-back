// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionBool.makeBool;
import static com.amazon.fusion.FusionSymbol.makeSymbol;
import static com.amazon.fusion.FusionUtils.EMPTY_OBJECT_ARRAY;
import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;

final class SyntaxSymbol
    extends SyntaxText
{
    /** A zero-length array of {@link SyntaxSymbol}. */
    static final SyntaxSymbol[] EMPTY_ARRAY = new SyntaxSymbol[0];

    /** Extract the names from an array of symbols. */
    static String[] toNames(SyntaxSymbol[] symbols)
    {
        if (symbols == null || symbols.length == 0)
        {
            return FusionUtils.EMPTY_STRING_ARRAY;
        }
        else
        {
            String[] names = new String[symbols.length];
            for (int i = 0; i < symbols.length; i++)
            {
                names[i] = symbols[i].stringValue();
            }
            return names;
        }
    }


    /** Initialized during {@link #doExpand} */
    private Binding myBinding;

    final SyntaxWraps myWraps;

    /**
     * @param datum must not be null.
     */
    private SyntaxSymbol(Evaluator      eval,
                         SyntaxWraps    wraps,
                         SourceLocation loc,
                         Object[]       properties,
                         BaseSymbol     datum)
    {
        super(loc, properties, datum);
        myWraps = wraps;
    }



    static SyntaxSymbol makeOriginal(Evaluator      eval,
                                     SourceLocation loc,
                                     BaseSymbol     symbol)
    {
        return new SyntaxSymbol(eval, null, loc, ORIGINAL_STX_PROPS, symbol);
    }

    static SyntaxSymbol make(Evaluator      eval,
                             SourceLocation loc,
                             BaseSymbol     symbol)
    {
        return new SyntaxSymbol(eval, null, loc, EMPTY_OBJECT_ARRAY, symbol);
    }


    /**
     * @param value may be null.
     */
    static SyntaxSymbol make(Evaluator eval, SyntaxWraps wraps, String value)
    {
        BaseSymbol datum = makeSymbol(eval, value);
        return new SyntaxSymbol(eval, wraps, /*location*/ null,
                                EMPTY_OBJECT_ARRAY, datum);
    }


    /**
     * @param value may be null.
     */
    static SyntaxSymbol make(Evaluator eval, SyntaxWrap wrap, String value)
    {
        return make(eval, SyntaxWraps.make(wrap), value);
    }


    /**
     * @param value may be null.
     */
    static SyntaxSymbol make(Evaluator eval, String value)
    {
        BaseSymbol datum = makeSymbol(eval, value);
        return new SyntaxSymbol(eval, null, null, EMPTY_OBJECT_ARRAY, datum);
    }


    //========================================================================


    @Override
    SyntaxSymbol copyReplacingProperties(Object[] properties)
    {
        SyntaxSymbol id = new SyntaxSymbol(null,
                                           myWraps,
                                           getLocation(),
                                           properties,
                                           getName());
        id.myBinding = myBinding;
        return id;
    }


    /**
     * @param wraps may be null.
     */
    private SyntaxSymbol copyReplacingWraps(SyntaxWraps wraps)
    {
        // We intentionally don't copy the binding, since the wraps are
        // probably different, so the binding may be different.

        SyntaxSymbol copy =
            new SyntaxSymbol(null, wraps, getLocation(), getProperties(),
                             getName());
        return copy;
    }


    SyntaxSymbol copyReplacingBinding(Binding binding)
    {
        SyntaxSymbol copy =
            new SyntaxSymbol(null, myWraps, getLocation(), getProperties(),
                             getName());
        copy.myBinding = binding;
        return copy;
    }


    //========================================================================

    BaseSymbol getName()
    {
        return (BaseSymbol) myDatum;
    }

    @Override
    SyntaxSymbol addWrap(SyntaxWrap wrap)
    {
        SyntaxWraps newWraps;
        if (myWraps == null)
        {
            newWraps = SyntaxWraps.make(wrap);
        }
        else
        {
            newWraps = myWraps.addWrap(wrap);
        }
        return copyReplacingWraps(newWraps);
    }

    @Override
    SyntaxSymbol addWraps(SyntaxWraps wraps)
    {
        SyntaxWraps newWraps;
        if (myWraps == null)
        {
            newWraps = wraps;
        }
        else
        {
            newWraps = myWraps.addWraps(wraps);
        }
        return copyReplacingWraps(newWraps);
    }


    @Override
    SyntaxSymbol stripWraps(Evaluator eval)
    {
        if (myWraps == null) return this;
        return copyReplacingWraps(null);
    }

    /**
     * Adds the wraps on this symbol onto those already on another value.
     * @return syntax matching the source, after adding the wraps from this
     * symbol.
     */
    SyntaxValue copyWrapsTo(SyntaxValue source)
        throws FusionException
    {
        if (myWraps == null) return source;
        return source.addWraps(myWraps);
    }

    /**
     * @return not null.
     */
    Set<MarkWrap> computeMarks()
    {
        if (myWraps == null) return Collections.emptySet();
        return myWraps.computeMarks();
    }


    @Override
    boolean hasMarks(Evaluator eval)
    {
        return (myWraps == null ? false : myWraps.hasMarks(eval));
    }


    /** Not set until {@link #resolve} or {@link #doExpand}. */
    Binding getBinding()
    {
        return myBinding;
    }

    /**
     * Expand-time binding resolution.
     * As a precondition, this symbol's text must be non-empty.
     * As a postcondition, {@link #myBinding} is not null.
     *
     * @return not null.
     */
    Binding resolve()
    {
        if (myBinding == null)
        {
            BaseSymbol name = getName();
            assert name.isNonEmpty();

            if (myWraps == null)
            {
                myBinding = new FreeBinding(name);
            }
            else
            {
                myBinding = myWraps.resolve(name);
                if (myBinding == null) myBinding = new FreeBinding(name);
            }
        }
        return myBinding;
    }


    /**
     * Resolves this identifier, but doesn't cache the result if it has not
     * been previously resolved.
     *
     * @return not null, but maybe a {@link FreeBinding}.
     */
    Binding uncachedResolve()
    {
        if (myBinding != null) return myBinding;
        BaseSymbol name = getName();
        if (myWraps != null)
        {
            Binding b = myWraps.resolve(name);
            if (b != null) return b;
        }
        return new FreeBinding(name);
    }


    /**
     * Resolves this identifier, but doesn't cache the result if it has not
     * been previously resolved.
     *
     * @return null is equivalent to a {@link FreeBinding}.
     */
    Binding uncachedResolveMaybe()
    {
        if (myBinding != null) return myBinding;
        if (myWraps   == null) return null;
        return myWraps.resolve(getName());
    }


    /**
     * Copies this identifier, caching a top-resolved binding.
     * @return not null.
     */
    SyntaxSymbol copyAndResolveTop()
    {
        Binding b = null;
        if (myWraps != null)
        {
            b = myWraps.resolveTop(getName());
        }
        if (b == null)
        {
            b = new FreeBinding(getName());
        }

        return copyReplacingBinding(b);
    }


    /**
     * Determines whether this identifier resolves to a {@link FreeBinding}
     * with the given name and marks.
     */
    boolean resolvesFree(BaseSymbol name, Set<MarkWrap> marks)
    {
        Binding resolvedBoundId = resolve();
        if (resolvedBoundId.isFree(name))
        {
            Set<MarkWrap> boundMarks = computeMarks();
            if (marks.equals(boundMarks))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Determines whether this identifier resolves to a given binding and has
     * the given marks.
     */
    boolean resolvesBound(Binding binding, Set<MarkWrap> marks)
    {
        Binding resolvedBoundId = resolve();
        if (resolvedBoundId.sameTarget(binding))
        {
            Set<MarkWrap> boundMarks = computeMarks();
            if (marks.equals(boundMarks))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Checks if this symbol is bound to a {@link SyntacticForm} in the given
     * enviroment.  If so, cache the binding and return the form.  Otherwise
     * do nothing.
     *
     * @return may be null.
     */
    SyntacticForm resolveSyntaxMaybe(Environment env)
    {
        Binding b = null;
        if (myBinding != null)
        {
            b = myBinding;
        }
        else if (myWraps != null)
        {
            String name = stringValue();
            if (name != null && name.length() != 0)
            {
                b = myWraps.resolve(getName());
            }
        }

        if (b != null)
        {
            Object resolved = env.namespace().lookup(b);
            if (resolved instanceof SyntacticForm)
            {
                myBinding = b;
                return (SyntacticForm) resolved;
            }
        }
        return null;
    }


    @Override
    SyntaxValue doExpand(Expander expander, Environment env)
        throws FusionException
    {
        if (myBinding == null)        // Otherwise we've already been expanded
        {
            String text = stringValue();
            if (text == null)
            {
                String message =
                    "null.symbol is not an expression. " +
                    "You probably want to quote this.";
                throw new SyntaxException(null, message, this);
            }

            if (text.length() == 0)
            {
                String message =
                    "Not an expression. " +
                    "You probably want to quote this.";
                throw new SyntaxException(null, message, this);
            }

            resolve();

            if (myBinding instanceof FreeBinding)
            {
                Evaluator eval = expander.getEvaluator();
                BaseSymbol topSym = makeSymbol(eval, "#%top");
                SyntaxSymbol top =
                    new SyntaxSymbol(eval,
                                     myWraps,
                                     /*location*/ null,
                                     /*properties*/ EMPTY_OBJECT_ARRAY,
                                     topSym);
                if (top.resolve() instanceof FreeBinding)
                {
                    throw new UnboundIdentifierException(this);
                }

                assert ! FusionValue.isAnnotated(eval, myDatum);
                SyntaxSexp topExpr = SyntaxSexp.make(eval, top, this);

                // TODO FUSION-207 tail expand
                return expander.expandExpression(env, topExpr);
            }
        }

        return this;
    }


    boolean freeIdentifierEqual(SyntaxSymbol that)
    {
        Binding thisBinding = this.uncachedResolve();
        Binding thatBinding = that.uncachedResolve();
        return thisBinding.sameTarget(thatBinding);
    }

    Object freeIdentifierEqual(Evaluator eval, SyntaxSymbol that)
    {
        return makeBool(eval, freeIdentifierEqual(that));
    }


    /**
     * Give a debugging representation: the symbol name and all its marks.
     * For example, {@code "symbol_name#26#12"}.
     */
    String debugString()
    {
        String base = toString();
        Set<MarkWrap> marks = this.computeMarks();
        if (! marks.isEmpty())
        {
            StringBuilder buf = new StringBuilder(base);
            for (MarkWrap mark : marks)
            {
                buf.append('#');
                buf.append(mark.getMark());
            }
            base = buf.toString();
        }
        return base;
    }

    void dump(PrintStream out) throws IOException
    {
        out.print("SyntaxSymbol::{binding:");
        myBinding.dump(out);
        out.print(",wraps:");
        myWraps.dump(out);
        out.print("}");
    }


    //========================================================================


    @Override
    CompiledForm doCompile(Evaluator eval, Environment env)
        throws FusionException
    {
        assert myBinding != null : "No binding for " + this;

        if (myBinding instanceof FreeBinding)
        {
            throw new UnboundIdentifierException(this);
        }

        return myBinding.compileReference(eval, env);
    }
}
