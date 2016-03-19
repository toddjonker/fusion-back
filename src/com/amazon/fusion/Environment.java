// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

/**
 * Maps identifiers to {@link Binding}s.
 * Environments are compile-time entities, arranged in a hierarchy of lexical
 * scopes.
 */
interface Environment
{
    Namespace namespace();


    /** What's the lexical depth?  0 == top-level */
    int getDepth();


    /**
     * NOT RECURSIVE TO ENCLOSING ENVIRONMENTS!
     *
     * @param marks not null.
     *
     * @return given binding if not substituted here; not null.
     */
    Binding substitute(Binding binding, Set<MarkWrap> marks);


    /**
     * Finds the effective binding for a free variable.
     *
     * @param marks not null.
     *
     * @return null if the name is not substituted here.
     */
    Binding substituteFree(BaseSymbol name, Set<MarkWrap> marks);


    /**
     * @param out
     * @throws IOException
     */
    abstract void dump(PrintStream out) throws IOException;
}
