// Copyright (c) 2012-2020 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionStruct.immutableStruct;
import static com.amazon.fusion.SyntaxWraps.EMPTY_WRAPS;
import com.amazon.fusion.FusionStruct.ImmutableStruct;
import com.amazon.fusion.FusionStruct.NonNullImmutableStruct;
import com.amazon.fusion.FusionSymbol.BaseSymbol;
import com.amazon.ion.IonException;
import com.amazon.ion.IonWriter;
import java.io.IOException;
import java.util.Map;

final class SyntaxStruct
    extends SyntaxContainer
{
    private ImmutableStruct myStruct;


    /**
     * @param struct must not be null.
     */
    private SyntaxStruct(SourceLocation  loc,
                         Object[] properties,
                         SyntaxWraps wraps,
                         ImmutableStruct struct)
    {
        super(loc, properties, wraps);
        myStruct = struct;
    }

    /**
     * @param struct must not be null.
     */
    private SyntaxStruct(SourceLocation  loc,
                         ImmutableStruct struct)
    {
        super(loc);
        myStruct = struct;
    }



    static SyntaxStruct makeOriginal(Evaluator       eval,
                                     SourceLocation  loc,
                                     ImmutableStruct struct)
    {
        return new SyntaxStruct(loc, ORIGINAL_STX_PROPS, EMPTY_WRAPS, struct);
    }


    /**
     * @param datum must be an immutable struct
     */
    static SyntaxStruct make(Evaluator eval,
                             SourceLocation loc,
                             Object datum)
    {
        return new SyntaxStruct(loc, (ImmutableStruct) datum);
    }


    //========================================================================


    @Override
    Object visit(Visitor v) throws FusionException
    {
        return v.accept(this);
    }


    @Override
    boolean hasNoChildren()
    {
        return myStruct.size() == 0;
    }


    @Override
    SyntaxStruct copyReplacingProperties(Object[] properties)
    {
        return new SyntaxStruct(getLocation(), properties, myWraps, myStruct);
    }

    @Override
    SyntaxStruct copyReplacingWraps(SyntaxWraps wraps)
    {
        return new SyntaxStruct(getLocation(), getProperties(), wraps,
                                myStruct);
    }


    @Override
    SyntaxStruct stripWraps(Evaluator eval)
        throws FusionException
    {
        if (hasNoChildren()) return this;  // No children, no marks, all okay!

        // Even if we have no marks, some children may have them.
        boolean mustReplace = (myWraps != null);  // TODO optimize further

        // Replace children in map as necessary.
        FunctionalHashTrie<String, Object> existingMap = ((NonNullImmutableStruct) myStruct).getMap(eval);
        FunctionalHashTrie<String, Object> newMap = existingMap;

        for (Map.Entry<String, Object> entry : existingMap.entrySet())
        {
            Object value = entry.getValue();
            if (! (value instanceof Object[]))
            {
                SyntaxValue child = (SyntaxValue) value;
                SyntaxValue stripped = child.stripWraps(eval);
                if (stripped != child)
                {
                    newMap = newMap.with(entry.getKey(), stripped);
                    mustReplace = true;
                }
            }
            else
            {
                Object[] children = (Object[]) value;
                int childCount = children.length;

                boolean mustReplaceArray = false;
                Object[] newChildren = new Object[childCount];
                for (int i = 0; i < childCount; i++)
                {
                    SyntaxValue child = (SyntaxValue) children[i];
                    SyntaxValue stripped = child.stripWraps(eval);
                    if (stripped != child)
                    {
                        mustReplaceArray = true;
                    }
                    newChildren[i] = stripped;
                }

                if (mustReplaceArray)
                {
                    newMap = newMap.with(entry.getKey(), newChildren);
                    mustReplace = true;
                }
            }
        }

        if (! mustReplace) return this;

        BaseSymbol[] annotations = myStruct.getAnnotations();
        int size = myStruct.size();
        ImmutableStruct s = immutableStruct(newMap, annotations, size);
        return new SyntaxStruct(getLocation(), getProperties(), EMPTY_WRAPS, s);
    }


    SyntaxValue get(Evaluator eval, String fieldName)
        throws FusionException
    {
        // This should only be called at runtime, after wraps are pushed.
        assert myWraps == null;

        return (SyntaxValue) myStruct.elt(eval, fieldName);
    }


    @Override
    Object unwrap(Evaluator eval)
        throws FusionException
    {
        if (myWraps == EMPTY_WRAPS)
        {
            return myStruct;
        }

        // We have wraps to propagate (and therefore children).

        // Replace children in map as necessary.
        FunctionalHashTrie<String, Object> existingMap = ((NonNullImmutableStruct) myStruct).getMap(eval);
        FunctionalHashTrie<String, Object> newMap = existingMap;

        // TODO optimize this to not allocate new objects when nothing changes.
        // Idea: keep track of when there are symbols contained (recursively),
        // when there's not, maybe we can skip all this.

        for (Map.Entry<String, Object> entry : existingMap.entrySet())
        {
            Object value = entry.getValue();
            if (! (value instanceof Object[]))
            {
                SyntaxValue child = (SyntaxValue) value;
                Object childValue = child.addWraps(myWraps);
                newMap = newMap.with(entry.getKey(), childValue);
            }
            else
            {
                Object[] children = (Object[]) value;
                Object[] childValues = new Object[children.length];

                int cPos = 0;
                for (Object c : children)
                {
                    SyntaxValue child = (SyntaxValue) c;
                    Object childValue = child.addWraps(myWraps);
                    childValues[cPos++] = childValue;
                }
                newMap = newMap.with(entry.getKey(), childValues);
            }
        }

        BaseSymbol[] annotations = myStruct.getAnnotations();
        int size = myStruct.size();
        myStruct = immutableStruct(newMap, annotations, size);
        myWraps = EMPTY_WRAPS;

        return myStruct;
    }


    @Override
    Object syntaxToDatum(Evaluator eval)
        throws FusionException
    {
        if (myStruct.size() == 0)
        {
            return myStruct;
        }

        // We have children, and wraps to propagate (when not recursing)

        // Replace children in map as necessary.
        FunctionalHashTrie<String, Object> existingMap = ((NonNullImmutableStruct) myStruct).getMap(eval);
        FunctionalHashTrie<String, Object> newMap = existingMap;

        // TODO optimize this to not allocate new objects when nothing changes.

        for (Map.Entry<String, Object> entry : existingMap.entrySet())
        {
            Object value = entry.getValue();
            if (! (value instanceof Object[]))
            {
                SyntaxValue child = (SyntaxValue) value;
                Object childValue = child.syntaxToDatum(eval);
                newMap = newMap.with(entry.getKey(), childValue);
            }
            else
            {
                Object[] children = (Object[]) value;
                Object[] childValues = new Object[children.length];

                int cPos = 0;
                for (Object c : children)
                {
                    SyntaxValue child = (SyntaxValue) c;
                    Object childValue = child.syntaxToDatum(eval);
                    childValues[cPos++] = childValue;
                }
                newMap = newMap.with(entry.getKey(), childValues);
            }
        }

        BaseSymbol[] annotations = myStruct.getAnnotations();
        int size = myStruct.size();
        return immutableStruct(newMap, annotations, size);
    }


    @Override
    SyntaxValue doExpand(Expander expander, Environment env)
        throws FusionException
    {
        if (myStruct.size() == 0)
        {
            return this;
        }

        Evaluator eval = expander.getEvaluator();
        // Replace children in map as necessary.
        FunctionalHashTrie<String, Object> existingMap = ((NonNullImmutableStruct) myStruct).getMap(eval);
        FunctionalHashTrie<String, Object> newMap = existingMap;

        for (Map.Entry<String, Object> entry : existingMap.entrySet())
        {
            Object value = entry.getValue();
            if (! (value instanceof Object[]))
            {
                SyntaxValue subform = (SyntaxValue) value;
                if (myWraps != null)
                {
                    subform = subform.addWraps(myWraps);
                }
                subform = expander.expandExpression(env, subform);
                newMap = newMap.with(entry.getKey(), subform);
            }
            else
            {
                Object[] children = (Object[]) value;
                int childCount = children.length;

                Object[] newChildren = new Object[childCount];
                for (int i = 0; i < childCount; i++)
                {
                    SyntaxValue subform = (SyntaxValue) children[i];
                    if (myWraps != null)
                    {
                        subform = subform.addWraps(myWraps);
                    }
                    newChildren[i] = expander.expandExpression(env, subform);
                }
                newMap = newMap.with(entry.getKey(), newChildren);
            }
        }


        // Wraps have been pushed down so the copy doesn't need them.
        BaseSymbol[] annotations = myStruct.getAnnotations();
        int size = myStruct.size();
        ImmutableStruct s = immutableStruct(newMap, annotations, size);
        return new SyntaxStruct(getLocation(), s);
    }


    @Override
    void ionize(Evaluator eval, IonWriter writer)
        throws IOException, IonException, FusionException, IonizeFailure
    {
        myStruct.ionize(eval, writer);
    }

    @Override
    final void write(Evaluator eval, Appendable out)
        throws IOException, FusionException
    {
        myStruct.write(eval, out);
    }
}
