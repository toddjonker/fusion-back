// Copyright (c) 2012-2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.ion.util.IonTextUtils.printString;
import com.amazon.fusion.FusionNumber.SumProc;
import org.junit.Before;
import org.junit.Test;


public class JavaFfiTest
    extends CoreTestCase
{
    @Before
    public void requires()
        throws Exception
    {
        topLevel().requireModule("/fusion/ffi/java");
    }

    public class NonStatic extends Procedure
    {
        @Override
        Object doApply(Evaluator eval, Object[] args)
            throws FusionException
        {
            return null;
        }
    }

    public abstract static class Abstract extends Procedure
    {
        @Override
        Object doApply(Evaluator eval, Object[] args)
            throws FusionException
        {
            return null;
        }
    }

    @SuppressWarnings("serial")
    static class Boom extends RuntimeException
    {
    }

    static class Uninstantiable extends Procedure
    {
        public Uninstantiable()
            throws Exception
        {
            throw new Exception("boom");
        }

        public Uninstantiable(Object arg)
            throws Exception
        {
            throw new Exception("boom");
        }

        @Override
        Object doApply(Evaluator eval, Object[] args)
            throws FusionException
        {
            return null;
        }
    }


    static class Unappliable extends Procedure
    {
        @Override
        Object doApply(Evaluator eval, Object[] args)
            throws FusionException
        {
            throw new Boom();
        }
    }


    private String name(Class<?> c)
    {
        String className = c.getName();
        return printString(className);
    }


    @Test
    public void testJavaNew()
        throws Exception
    {
        eval("(define plus (java_new " + name(SumProc.class) + "))");
        assertEval(2, "(plus 1 1)");

        eval("(define param (java_new " + name(DynamicParameter.class) + " 1))");
        assertEval(1, "(param)");
    }

    @Test
    public void testJavaNewBadArgs()
        throws Exception
    {
        expectContractExn("(define foo (java_new '''no such class'''))");
        expectContractExn("(define foo (java_new " + name(NonStatic.class) + "))");
        expectContractExn("(define foo (java_new " + name(Abstract.class) + "))");
        expectContractExn("(define foo (java_new " + name(Uninstantiable.class) + "))");
        expectContractExn("(define foo (java_new " + name(Uninstantiable.class) + " null))");
    }

    @Test(expected=Boom.class)
    public void testCrashingProc()
        throws Exception
    {
        topLevel().define("p", new Unappliable());
        topLevel().eval("(map p [1, 2])");
    }

    @Test
    public void testCrashingProcInModule()
        throws Exception
    {
        topLevel().eval("(module M '/fusion' "
                        + "(require '/fusion/ffi/java')"
                        + "(define p (java_new " + name(Uninstantiable.class) + "))"
                        + "(provide p))");
        topLevel().eval("(module N '/fusion' (require M))");
        topLevel().requireModule("N");
    }

}
