// Copyright (c) 2012-2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.BindingDoc.COLLECT_DOCS_MARK;
import static com.amazon.fusion.FusionIo.safeWriteToString;
import static com.amazon.fusion.FusionVoid.voidValue;
import static com.amazon.fusion.ModuleIdentity.isValidAbsoluteModulePath;
import static com.amazon.fusion.StandardReader.readSyntax;
import static com.amazon.ion.util.IonTextUtils.printQuotedSymbol;
import static java.lang.Boolean.TRUE;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import java.io.File;


final class StandardTopLevel
    implements TopLevel
{
    private final Evaluator myEvaluator;
    private final Namespace myNamespace;


    /**
     * @param initialModulePath must be absolute.
     */
    StandardTopLevel(GlobalState globalState,
                     Namespace namespace,
                     String initialModulePath,
                     boolean documenting)
        throws FusionInterrupt, FusionException
    {
        assert ModuleIdentity.isValidAbsoluteModulePath(initialModulePath);

        _Private_CoverageCollector collector = globalState.myCoverageCollector;
        Evaluator eval = (collector == null
                            ? new Evaluator(globalState)
                            : new CoverageEvaluator(globalState, collector));

        if (documenting)
        {
            eval = eval.markedContinuation(COLLECT_DOCS_MARK, TRUE);
        }

        myEvaluator = eval;
        myNamespace = namespace;

        namespace.require(myEvaluator, initialModulePath);
    }

    /**
     * @param initialModulePath must be absolute.
     */
    StandardTopLevel(GlobalState globalState,
                     ModuleRegistry registry,
                     String initialModulePath)
        throws FusionInterrupt, FusionException
    {
        this(globalState, new TopLevelNamespace(registry), initialModulePath,
             false);
    }


    //========================================================================

    /**
     * Helper method for internal APIs
     */
    static Evaluator toEvaluator(TopLevel top)
    {
        return ((StandardTopLevel) top).myEvaluator;
    }


    // NOT PUBLIC
    Evaluator getEvaluator()
    {
        return myEvaluator;
    }


    @Override
    public Object eval(String source, SourceName name)
        throws FusionInterruptedException, FusionException
    {
        IonSystem system = myEvaluator.getGlobalState().myIonSystem;
        IonReader i = system.newReader(source);
        return eval(i, name);
    }


    @Override
    public Object eval(String source)
        throws FusionInterruptedException, FusionException
    {
        return eval(source, null);
    }


    @Override
    public Object eval(IonReader source, SourceName name)
        throws FusionInterruptedException, FusionException
    {
        try
        {
            Object result = voidValue(myEvaluator);

            if (source.getType() == null) source.next();
            while (source.getType() != null)
            {
                SyntaxValue sourceExpr = readSyntax(myEvaluator, source, name);
                result = FusionEval.eval(myEvaluator, sourceExpr, myNamespace);
                source.next();
            }

            return result;
        }
        catch (FusionException e)
        {
            throw rewriteStackTrace(e);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    @Override
    public Object eval(IonReader source)
        throws FusionInterruptedException, FusionException
    {
        return eval(source, null);
    }


    @Override
    public Object load(File source)
        throws FusionInterruptedException, FusionException
    {
        try
        {
            LoadHandler load = myEvaluator.getGlobalState().myLoadHandler;
            return load.loadTopLevel(myEvaluator,
                                     myNamespace,
                                     source.toString());
        }
        catch (FusionException e)
        {
            throw rewriteStackTrace(e);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    @Override
    public void loadModule(String     absoluteModulePath,
                           IonReader  source,
                           SourceName name)
        throws FusionInterruptedException, FusionException
    {
        if (! isValidAbsoluteModulePath(absoluteModulePath))
        {
            String message =
                "Invalid absolute module path: " + absoluteModulePath;
            throw new IllegalArgumentException(message);
        }

        try
        {
            ModuleNameResolver resolver =
                myEvaluator.getGlobalState().myModuleNameResolver;
            ModuleIdentity id =
                ModuleIdentity.forAbsolutePath(absoluteModulePath);
            ModuleLocation loc =
                new IonReaderModuleLocation(source, name);
            resolver.loadModule(myEvaluator, id, loc, true /* reload it */);
        }
        catch (FusionException e)
        {
            throw rewriteStackTrace(e);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    @Override
    public void requireModule(String modulePath)
        throws FusionInterruptedException, FusionException
    {
        try
        {
            myNamespace.require(myEvaluator, modulePath);
        }
        catch (FusionException e)
        {
            throw rewriteStackTrace(e);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    @Override
    public void define(String name, Object value)
        throws FusionException
    {
        try
        {
            Object fv = myEvaluator.injectMaybe(value);
            if (fv == null)
            {
                String expected =
                    "injectable Java type but received " +
                        value.getClass().getName();
                throw new ArgumentException("TopLevel.define", expected,
                                            -1, value);
            }

            myNamespace.bind(name, fv);
        }
        catch (FusionException e)
        {
            throw rewriteStackTrace(e);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    @Override
    public Object lookup(String name)
        throws FusionInterruptedException, FusionException
    {
        try
        {
            return myNamespace.lookup(name);
        }
        catch (FusionInterrupt e)
        {
            // I don't think this can happen, but I prefer to be consistent
            // throughout this class to be more resilient to changes.
            throw new FusionInterruptedException(e);
        }
    }


    private Procedure lookupProcedure(String procedureName)
        throws FusionInterruptedException, FusionException
    {
        try
        {
            Object proc = lookup(procedureName);
            if (proc instanceof Procedure)
            {
                return (Procedure) proc;
            }

            if (proc == null)
            {
                throw new FusionException(printQuotedSymbol(procedureName) +
                                          " is not defined");
            }

            throw new FusionException(printQuotedSymbol(procedureName) +
                                      " is not a procedure: " +
                                      safeWriteToString(myEvaluator, proc));
        }
        catch (FusionException e)
        {
            throw rewriteStackTrace(e);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    private Object call(Procedure proc, Object... arguments)
        throws FusionInterruptedException, FusionException
    {
        try
        {
            for (int i = 0; i < arguments.length; i++)
            {
                Object arg = arguments[i];
                Object fv = myEvaluator.injectMaybe(arg);
                if (fv == null)
                {
                    String expected =
                        "injectable Java type but received " +
                            arg.getClass().getName();
                    throw new ArgumentException("TopLevel.call",
                                                expected,
                                                i, arguments);
                }
                arguments[i] = fv;
            }

            return myEvaluator.callNonTail(proc, arguments);
        }
        catch (FusionException e)
        {
            throw rewriteStackTrace(e);
        }
        catch (FusionInterrupt e)
        {
            throw new FusionInterruptedException(e);
        }
    }


    @Override
    public Object call(String procedureName, Object... arguments)
        throws FusionInterruptedException, FusionException
    {
        Procedure proc = lookupProcedure(procedureName);

        return call(proc, arguments);
    }


    @Override
    public Object call(Object procedure, Object... arguments)
        throws FusionInterruptedException, FusionException
    {
        if (! (procedure instanceof Procedure))
        {
            throw new IllegalArgumentException("Not a procedure: " + procedure);
        }

        return call((Procedure) procedure, arguments);
    }


    private FusionException rewriteStackTrace(FusionException e)
    {
        e.rewriteStackTrace();
        return e;
    }
}
