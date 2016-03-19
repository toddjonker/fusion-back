// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import com.amazon.fusion.ModuleNamespace.ProvidedBinding;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Bindings are used during expansion and compilation to identify a specific
 * binding site.
 * They are compiled away and are not used at eval-time.
 */
abstract class Binding
{
    /** Dumps the lexical context. For debugging only. */
    abstract void dump(PrintStream out) throws IOException;

    abstract BaseSymbol getName();

    /**
     * Determines whether this is a {@link FreeBinding} with the given name.
     * The default implementation returns false.
     */
    boolean isFree(BaseSymbol name)
    {
        return false;
    }

    /**
     * Gets the original binding to which this binding refers.
     * <p>
     * Free, local, and module-level bindings are always their own target.
     * For imported bindings, the target is a module-level binding.
     * The target of a top-level binding can be either its local definition
     * or an imported module-level binding (and it can change over time).
     * <p>
     * Non-free target bindings are "physical" in that they denote storage
     * locations in a one-to-one manner. Therefore it is safe to use object
     * identity to compare them.
     *
     * @return not null.
     */
    Binding target()
    {
        return this;
    }

    /**
     * Determines whether two bindings refer to the same {@link #target()},
     * despite any renames on import or export.
     *
     * @param other must not be null.
     */
    boolean sameTarget(Binding other)
    {
        return target() == other.target();
    }

    ProvidedBinding provideAs(SyntaxSymbol exportedId)
    {
        throw new IllegalStateException("This kind of binding can't be exported");
    }

    /**
     * Don't call directly! Second half of double-dispatch from
     * {@link Namespace#lookup(Binding)}.
     *
     * @return null if there's no value associated with the binding.
     */
    abstract Object lookup(Namespace ns);

    /**
     * Checks whether this binding can be mutated.
     *
     * @return null if the binding can be mutated, else a syntax error message.
     */
    String mutationSyntaxErrorMessage()
    {
        return null;
    }


    CompiledForm compileDefine(Evaluator eval,
                               Environment env,
                               SyntaxSymbol id,
                               CompiledForm valueForm)
        throws FusionException
    {
        throw new IllegalStateException("Unexpected `define` context.");
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
