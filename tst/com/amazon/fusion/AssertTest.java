// Copyright (c) 2012-2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.Matchers.endsWith;

public class AssertTest
    extends CoreTestCase
{
    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Before
    public void requires()
        throws Exception
    {
        topLevel().requireModule("/fusion/exception");
    }


    private void expectAssertFailure(String expr)
        throws Exception
    {
        try
        {
            eval("(assert " + expr + ")");
            Assert.fail("Expected exception");
        }
        catch (FusionAssertionException e)
        {
            Assert.assertEquals(null, e.getUserMessage());
        }

        try
        {
            eval("(assert " + expr + " \"barney\")");
            Assert.fail("Expected exception");
        }
        catch (FusionAssertionException e)
        {
            Assert.assertEquals("barney", e.getUserMessage());
        }

        try
        {
            eval("(assert " + expr + " \"barney\" 13)");
            Assert.fail("Expected exception");
        }
        catch (FusionAssertionException e)
        {
            Assert.assertEquals("barney13", e.getUserMessage());
        }
    }

    @Test
    public void testAssertFailure()
        throws Exception
    {
        for (String form : BooleanTest.UNTRUTHY_EXPRESSIONS)
        {
            expectAssertFailure(form);
        }
    }

    private void expectAssertSuccess(String expr)
        throws Exception
    {
        assertEval(1, "(begin (assert " + expr + " \"barney\") 1)");
    }

    @Test
    public void testAssertSuccess()
        throws Exception
    {
        for (String form : BooleanTest.TRUTHY_EXPRESSIONS)
        {
            expectAssertSuccess(form);
        }
    }

    @Test(expected = ExitException.class)
    public void testAssertFailureWithExitingMessage()
        throws Exception
    {
        eval("(assert false (exit))");
    }

    @Test
    public void testAssertSuccessWithExitingMessage()
        throws Exception
    {
        eval("(assert true (exit))");
    }

    @Test
    public void testAssertFailureLocation()
        throws Exception
    {
        thrown.expect(FusionAssertionException.class);
        thrown.expectMessage(endsWith("  ...at 2nd line, 3rd column\n" +
                                      "  ...at 1st line, 1st column"));
        eval("(begin \n" +
             "  (assert (not true) \"msg\"))");
    }

    @Test
    public void testAssertArity()
        throws Exception
    {
        expectSyntaxExn("(assert)");
    }
}
