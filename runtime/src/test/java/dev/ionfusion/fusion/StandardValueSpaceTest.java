// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;

import static dev.ionfusion.testing.Permute.generateSubsetPermutationsWithEmpty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.ionfusion.fusion.FusionSymbol.BaseSymbol;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

public class StandardValueSpaceTest
{
    private static final String[] SYMBOL_TEXTS        = { null, "", "text" };
    private static final String[] ACTUAL_SYMBOL_TEXTS = { "", "text", "a b" };


    private static Stream<Arguments> permuteTexts(String[] texts)
    {
        List<String[]> names = generateSubsetPermutationsWithEmpty(texts);
        // The cast to Object avoids the array flattening into varargs ...
        return names.stream().map(a -> arguments((Object) a));
    }


    private final StandardValueSpace vspace = new StandardValueSpace();


    //========================================================================
    // make[Actual]Symbol

    /**
     * Asserts that the actual symbol has the expected text and is interned.
     */
    private void assertInternedSymbol(String expected, BaseSymbol actual)
    {
        assertEquals(expected, actual.stringValue());

        // Interning is idempotent.
        BaseSymbol expectedSymbol = vspace.makeSymbol(expected);
        assertSame(actual, expectedSymbol);

        if (expected != null)
        {
            // Identity of the text is irrelevant.
            expected = new String(expected);
            assertSame(actual, vspace.makeSymbol(expected));
            assertSame(actual, vspace.makeActualSymbol(expected));
        }
    }

    private void assertInternedSymbols(String[] expected, BaseSymbol[] actual)
    {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertInternedSymbol(expected[i], actual[i]);
        }
    }


    @ParameterizedTest
    @FieldSource("SYMBOL_TEXTS")
    public void testMakeSymbol(String text)
    {
        BaseSymbol expected = vspace.makeSymbol(text);
        assertInternedSymbol(text, expected);
    }


    @ParameterizedTest
    @FieldSource("ACTUAL_SYMBOL_TEXTS")
    public void testMakeActualSymbol(String text)
    {
        BaseSymbol expected = vspace.makeActualSymbol(text);
        assertInternedSymbol(text, expected);
    }

    @Test
    public void makeActualSymbolRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> vspace.makeActualSymbol(null));
    }


    //========================================================================
    // make[Actual]Symbols

    private static final Supplier<Stream<Arguments>> testMakeSymbols =
        () -> permuteTexts(SYMBOL_TEXTS);

    @ParameterizedTest
    @FieldSource
    public void testMakeSymbols(String[] symbols)
    {
        assertInternedSymbols(symbols, vspace.makeSymbols(symbols));
    }

    @Test
    public void makeSymbolsRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> vspace.makeSymbols((String[]) null));
    }


    private static final Supplier<Stream<Arguments>> testMakeActualSymbols =
        () -> permuteTexts(ACTUAL_SYMBOL_TEXTS);

    @ParameterizedTest
    @FieldSource
    public void testMakeActualSymbols(String[] symbols)
    {
        assertInternedSymbols(symbols, vspace.makeActualSymbols(symbols));
    }

    @Test
    public void makeActualSymbolsRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> vspace.makeActualSymbols((String[]) null));
        assertThrows(NullPointerException.class, () -> vspace.makeActualSymbols((String) null));
        assertThrows(NullPointerException.class, () -> vspace.makeActualSymbols("a1", null, "a2"));
    }


    //========================================================================
    // makeAnnotatedSymbol

    /**
     * Produces test inputs by combining each symbol text with all permutations of annotation
     * texts.
     */
    private static Stream<Arguments> annotatedSymbolArgs()
    {
        List<String[]> annSets = generateSubsetPermutationsWithEmpty(ACTUAL_SYMBOL_TEXTS);

        return Arrays.stream(SYMBOL_TEXTS)
                     .flatMap(t -> annSets.stream().map(a -> arguments(t, a)));
    }

    @ParameterizedTest
    @MethodSource("annotatedSymbolArgs")
    public void testMakeAnnotatedSymbol(String text, String... annotations)
        throws FusionException
    {
        BaseSymbol actual = vspace.makeAnnotatedSymbol(text, annotations);

        // The whole thing is interned.
        assertSame(actual, vspace.makeAnnotatedSymbol(text, annotations));

        // Given no annotations, works just like `makeSymbol`.
        if (annotations.length == 0)
        {
            assertInternedSymbol(text, actual);
        }
        else
        {
            // Text and annotations are interned as expected.
            assertEquals(text, actual.stringValue());
            assertInternedSymbols(annotations, actual.getAnnotations());
        }
    }


    @Test
    public void makeAnnotatedSymbolRejectsNullAnnotations()
    {
        assertThrows(NullPointerException.class,
                     () -> vspace.makeAnnotatedSymbol("v", (String[]) null));
        assertThrows(NullPointerException.class,
                     () -> vspace.makeAnnotatedSymbol("v", "a1", null, "a2"));
    }
}
