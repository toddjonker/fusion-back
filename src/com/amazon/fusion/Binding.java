// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

/**
 * Bindings are used during expansion and compilation to identify a specific
 * binding site.
 * They are compiled away and are not used at eval-time.
 */
abstract class Binding
{
    abstract String getName();

    /**
     * Determines whether this is a {@link FreeBinding} with the given name.
     * This implementation returns false.
     */
    boolean isFree(String name)
    {
        return false;
    }

    /**
     * Gets the original binding this represents.
     * If this is a free, lexical, or namespace-level binding, return this.
     * If this is an exported module binding, return the internal
     * namespace-level binding.
     *
     * @return not null.
     */
    Binding originalBinding()
    {
        return this;
    }

    /**
     * Determines whether two bindings refer to the same
     * {@link #originalBinding()}, despite any renames on import or export.
     *
     * @param other must not be null.
     */
    abstract boolean sameTarget(Binding other);

    /**
     * @return null if there's no value associated with the binding.
     */
    abstract Object lookup(Environment store);


    CompiledForm compileDefine(Evaluator eval,
                               Environment env,
                               SyntaxSymbol id,
                               CompiledForm valueForm)
        throws FusionException
    {
        throw new IllegalStateException("Unexpected `define` context.");
    }

    CompiledForm compileDefineSyntax(Evaluator eval,
                                     Environment env,
                                     SyntaxSymbol id,
                                     CompiledForm valueForm)
        throws FusionException
    {
        throw new IllegalStateException("Unexpected `define_syntax` context.");
    }


    /** Compile a reference to the variable denoted by this binding. */
    abstract CompiledForm compileReference(Evaluator eval,
                                           Environment env)
        throws FusionException;

    /** Compile a #%top reference. */
    abstract CompiledForm compileTopReference(Evaluator eval,
                                              Environment env,
                                              SyntaxSymbol id)
        throws FusionException;

    /** Compile a mutation of the variable denoted by this binding. */
    abstract CompiledForm compileSet(Evaluator eval,
                                     Environment env,
                                     CompiledForm valueForm)
        throws FusionException;
}
