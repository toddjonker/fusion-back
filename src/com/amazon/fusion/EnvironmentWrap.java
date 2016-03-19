// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Set;

/**
 * Syntax wrap that adds all bindings from a specific environment.
 */
class EnvironmentWrap
    extends SyntaxWrap
{
    private final Environment myEnvironment;

    EnvironmentWrap(Environment environment)
    {
        myEnvironment = environment;
    }

    Environment getEnvironment()
    {
        return myEnvironment;
    }

    @Override
    Binding resolve(BaseSymbol name,
                    Iterator<SyntaxWrap> moreWraps,
                    Set<MarkWrap> returnMarks)
    {
        Binding b;
        if (moreWraps.hasNext())
        {
            SyntaxWrap nextWrap = moreWraps.next();
            b = nextWrap.resolve(name, moreWraps, returnMarks);
            if (b != null)
            {
                return myEnvironment.substitute(b, returnMarks);
            }
        }

        // The identifier doesn't have a binding outside of this environment,
        // so look for one here.
        Binding subst = myEnvironment.substituteFree(name, returnMarks);
        return subst;
    }


    @Override
    Iterator<SyntaxWrap> iterator()
    {
        return null;
    }


    @Override
    public String toString()
    {
        return "{{{Environment renames for " + myEnvironment + "}}}";
    }

    @Override
    void dump(PrintStream out) throws IOException
    {
        out.print("EnvironmentRenameWrap::{");
        _dump(out);
        out.print("}");
    }

    void _dump(PrintStream out) throws IOException
    {
        out.print("env:");
        myEnvironment.dump(out);
    }
}
