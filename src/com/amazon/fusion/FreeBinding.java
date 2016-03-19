// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.io.IOException;
import java.io.PrintStream;


final class FreeBinding
    extends Binding
{
    private final BaseSymbol myName;

    FreeBinding(BaseSymbol name)
    {
        myName = name;
    }

    @Override
    public BaseSymbol getName()
    {
        return myName;
    }


    @Override
    public boolean isFree(BaseSymbol name)
    {
        // This works due to symbol interning.
        return myName == name;
    }


    @Override
    public boolean sameTarget(Binding other)
    {
        if (this == other) return true;
        return other.isFree(myName);
    }


    @Override
    public Object lookup(Namespace ns)
    {
        return null;
    }


    @Override
    public String mutationSyntaxErrorMessage()
    {
         return "identifier has no binding";
    }


    @Override
    CompiledForm compileDefine(Evaluator eval,
                               Environment env,
                               SyntaxSymbol id,
                               CompiledForm valueForm)
        throws FusionException
    {
        return env.namespace().compileDefine(eval, this, id, valueForm);
    }

    @Override
    public CompiledForm compileReference(Evaluator eval, Environment env)
        throws FusionException
    {
        String message =
            "Free binding shouldn't make it to compilation: " + this;
        throw new IllegalStateException(message);
    }

    @Override
    public CompiledForm compileTopReference(Evaluator eval, Environment env,
                                            SyntaxSymbol id)
        throws FusionException
    {
        return env.namespace().compileFreeTopReference(id);
    }

    @Override
    public CompiledForm compileSet(Evaluator eval, Environment env,
                                   CompiledForm valueForm)
        throws FusionException
    {
        String message =
            "Free binding shouldn't make it to compilation: " + this;
        throw new IllegalStateException(message);
    }


    @Override
    public boolean equals(Object other)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public String toString()
    {
        return "{{FreeBinding " + myName + "}}";
    }

    @Override
    void dump(PrintStream out) throws IOException
    {
        out.print("FreeBinding::{name:");
        myName.dump(out);
        out.print("}");
    }
}
