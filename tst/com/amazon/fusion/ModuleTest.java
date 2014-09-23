// Copyright (c) 2012-2018 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 */
public class ModuleTest
    extends CoreTestCase
{
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void requires()
        throws FusionException
    {
        useTstRepo();
    }

    @Test
    public void testBadModuleSyntax()
        throws Exception
    {
        expectSyntaxExn("(module)");
        expectSyntaxExn("(module foo)");
        expectSyntaxExn("(module foo 12)");
        expectSyntaxExn("(module foo \"/fusion\")");  // No body

        expectSyntaxExn("(module \"foo\" \"/fusion\" true)");  // Bad name
        expectSyntaxExn("(module 1       \"/fusion\" true)");  // Bad name
    }



    @Test(expected = ModuleNotFoundException.class)
    public void testBadLanguageSymbolInTopLevelModule()
        throws Exception
    {
        eval("(module m '/no_such_module' (define x 1))");
    }

    @Test
    public void testBadQuotedLanguageSymbolInTopLevelModule()
        throws Exception
    {
        expectSyntaxExn("(module m (quote 'no_such_module') (define x 1))");
    }

    @Test(expected = ModuleNotFoundException.class)
    public void testBadLanguageStringInTopLevelModule()
        throws Exception
    {
        eval("(module m \"no_such_module\" (define x 1))");
    }

    @Test(expected = ModuleNotFoundException.class)
    public void testRelativeLanguageStringInTopLevelModule()
        throws Exception
    {
        // TODO FUSION-151 this isn't well-defined yet and should be rejected.
        eval("(module m \"fusion\" (define x 1))");
    }

    @Test(expected = ModuleNotFoundException.class)
    public void testTopLevelLanguageInTopLevelModule()
        throws Exception
    {
        eval("(module lang '/fusion/base' true)");

        // TODO FUSION-151 this isn't well-defined yet and should be rejected.
        eval("(module m \"lang\" (define x 1))");
    }



    @Test(expected = FusionException.class) // ModuleNotFoundException gets wrapped
    public void testBadLanguageSymbolInRepoModule()
        throws Exception
    {
        eval("(require '/module/bad_lang_symbol')");
    }

    @Test(expected = FusionException.class) // ModuleNotFoundException gets wrapped
    public void testBadLanguageSymbolInRepoModule2()
        throws Exception
    {
        eval("(require \"/module/bad_lang_symbol\")");
    }

    @Test(expected = FusionException.class) // ModuleNotFoundException gets wrapped
    public void testBadQuotedLanguageSymbolInRepoModule()
        throws Exception
    {
        eval("(require '/module/bad_quoted_lang_symbol')");
    }


    @Test
    public void testUseModuleWithNoProvides()
        throws Exception
    {
        eval("(require '/NoProvides')");
        expectUnboundIdentifierExn("X");
    }


    @Test(expected = FusionException.class)
    public void testDuplicateDefinedName()
        throws Exception
    {
        eval("(require '/module/duplicate_defined_name')");
    }

    @Test(expected = FusionException.class)
    public void testDuplicateImportedName()
        throws Exception
    {
        eval("(require \"/module/duplicate_imported_name\")");
    }

    @Test
    public void testDefineImportedName()
        throws Exception
    {
        thrown.expect(AmbiguousBindingFailure.class);

        eval("(require '/module/define_imported_name')");
    }

    @Test(expected = FusionException.class)
    public void testImportDefinedName()
        throws Exception
    {
        eval("(require '/module/import_defined_name')");
    }


    @Test
    public void testIntialModuleImportsWithNoProvides()
        throws Exception
    {
        expectUnboundIdentifierExn("(module M '/NoProvides' X)");
    }


    @Test
    public void testBadRequireSyntax()
        throws Exception
    {
        expectSyntaxExn("(require)");
        expectSyntaxExn("(require {})");
        expectSyntaxExn("(require ())");
        expectSyntaxExn("(require (lib))");
        expectSyntaxExn("(require (lib \"/fusion/list\"))");
    }

    @Test(expected = FusionException.class)
    public void testRequireNonModule()
        throws Exception
    {
        eval("(require \"/nonmodule/trivialDefine\")");
    }

    @Test
    public void testRequireUnderTop()
        throws Exception
    {
        String code =
            "(module m '/fusion'" +
            "  (if true (require '/fusion/list') false))";
        expectSyntaxExn(code);
    }

    /** Traps an infinite-loop bug in partial expansion. */
    @Test(expected = FusionException.class)
    public void testBadSexpAtModuleLevel()
        throws Exception
    {
        eval("(module m '/fusion' (1))");
        topLevel().requireModule("m");  // TODO shouldnt be reachable
    }

    @Test
    public void testModuleAtTopLevel()
        throws Exception
    {
        eval("(module mod '/fusion'" +
             "  (define M 1054)" +
            "   (define N (lambda () (+ 1 M)))" +
             "  (provide M N))");
        expectSyntaxExn("M");

        eval("(require mod)");
        assertEval(1054, "M");
    }

    /**
     * We shouldn't fail `provide` at expand-time, since that may hide the
     * compile-time errors that caused provide to fail.
     */
    @Test
    public void testProvideFailsLate()
        throws Exception
    {
        expectUnboundIdentifierExn("(module x '/fusion'" +
                                   "  (require '/fusion/experimental/syntax')" +
                                   "  (define_syntax broken (lambda (s) x))" +
                                   "  (provide broken))");
    }


    @Test
    public void testModuleCircularity()
        throws Exception
    {
        try
        {
            topLevel().requireModule("/cycle0");
            fail("Expected exception");
        }
        catch (FusionException e)
        {
            String message = e.getMessage();
            assertTrue(message.contains("/cycle1"));
            assertTrue(message.contains("/cycle2"));
            assertTrue(message.contains("/cycle3"));
        }
    }


    @Test
    public void testTopLevelLocalModuleIsolation()
        throws Exception
    {
        TopLevel top1 = topLevel();
        TopLevel top2 = runtime().makeTopLevel();

        top1.eval("(module M '/fusion' (define v 1) (provide v))");
        top2.eval("(module M '/fusion' (define v 2) (provide v))");

        top1.eval("(require M)");
        top2.eval("(require M)");

        assertEval(top1, 1, "v");
        assertEval(top2, 2, "v");
    }
}
