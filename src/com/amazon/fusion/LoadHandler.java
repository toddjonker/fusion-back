// Copyright (c) 2012-2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionEval.callCurrentEval;
import static com.amazon.fusion.FusionString.makeString;
import static com.amazon.fusion.FusionUtils.resolvePath;
import static com.amazon.fusion.GlobalState.MODULE;
import static com.amazon.fusion.StandardReader.readSyntax;
import com.amazon.ion.IonException;
import com.amazon.ion.IonReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Parallel to Racket's load handler.
 */
final class LoadHandler
{
    private final DynamicParameter myCurrentLoadRelativeDirectory;
    private final DynamicParameter myCurrentDirectory;

    LoadHandler(DynamicParameter currentLoadRelativeDirectory,
                DynamicParameter currentDirectory)
    {
        myCurrentLoadRelativeDirectory = currentLoadRelativeDirectory;
        myCurrentDirectory = currentDirectory;
    }


    /**
     * Reads top-level syntax forms from a file, evaluating each in sequence.
     * The file is resolved relative to {@code current_directory}.
     * Loading is parameterized to set {@code current_load_relative_directory}
     * to the parent of the resolved path.
     *
     * @param eval
     * @param namespace may be null, to use
     *   {@link Evaluator#findCurrentNamespace()}.
     * @param path the file to read; may be relative, in which case it is
     * resolved relative to the {@code current_directory} parameter.

     * @return the value of the last form in the file, or null if the file
     * contains no forms.
     */
    Object loadTopLevel(Evaluator eval, Namespace namespace, String path)
        throws FusionException
    {
        File file = resolvePath(eval, myCurrentDirectory, path);
        File parent = file.getParentFile();

        SyntaxSymbol tiSym =
            SyntaxSymbol.make(eval, GlobalState.TOP_INTERACTION);

        eval = eval.markedContinuation(myCurrentLoadRelativeDirectory,
                                       makeString(eval, parent.getAbsolutePath()));

        try
        {
            FileInputStream in = new FileInputStream(file);
            try
            {
                SourceName name = SourceName.forFile(file);
                Object result = null;

                IonReader reader = eval.getSystem().newReader(in);
                while (reader.next() != null)
                {
                    result = null;  // Don't hold onto garbage
                    SyntaxValue fileExpr = readSyntax(eval, reader, name);

                    SyntaxSexp topForm =
                        SyntaxSexp.make(eval, tiSym, fileExpr);

                    result = FusionEval.eval(eval, topForm, namespace);
                    // TODO TAIL
                }

                return result;
            }
            finally
            {
                in.close();
            }
        }
        catch (IOException | IonException e)
        {
            String message =
                "Error loading file " + file + ": " + e.getMessage();
            throw new FusionException(message, e);
        }
    }


    /**
     * If the reader is positioned on a value, it will be read; otherwise the
     * {@linkplain IonReader#next() next} value will be read.
     * <p>
     * If the reader doesn't provide exactly one top-level value, an exception
     * is thrown.
     *
     * @param sourceName may be null.
     */
    private SyntaxSexp readModuleDeclaration(Evaluator eval,
                                             ModuleIdentity id,
                                             SourceName sourceName,
                                             IonReader reader)
        throws FusionException
    {
        if (reader.getType() == null && reader.next() == null)
        {
            String message = "Module source has no top-level forms: " + id;
            throw new FusionException(message);
        }

        SyntaxValue firstTopLevel = readSyntax(eval, reader, sourceName);
        if (reader.next() != null)
        {
            String message =
                "Module source has more than one top-level form: " + id;
            throw new FusionException(message);
        }

        try
        {
            SyntaxSexp moduleDeclaration = (SyntaxSexp) firstTopLevel;
            if (moduleDeclaration.size() > 1)
            {
                SyntaxSymbol moduleSym = (SyntaxSymbol)
                    moduleDeclaration.get(eval, 0);
                if (MODULE.equals(moduleSym.stringValue()))
                {
                    return moduleDeclaration;
                }
            }
        }
        catch (ClassCastException e) { /* fall through */ }

        String message = "Top-level form isn't (module ...)";
        throw new SyntaxException("load handler", message, firstTopLevel);
    }


    private SyntaxSexp readModuleDeclaration(Evaluator eval,
                                             ModuleIdentity id,
                                             ModuleLocation loc)
        throws FusionException
    {
        try
        {
            IonReader reader = loc.read(eval);
            try
            {
                SourceName sourceName = loc.sourceName();
                return readModuleDeclaration(eval, id, sourceName, reader);
            }
            finally
            {
                reader.close();
            }
        }
        catch (IOException e)
        {
            throw new FusionException(e);
        }
    }


    private SyntaxSexp
    wrapModuleIdentifierWithKernelBindings(Evaluator eval,
                                           SyntaxSexp moduleStx)
        throws FusionException
    {
        SyntaxValue[] children = moduleStx.extract(eval);

        // We already verified this type-safety
        assert ((SyntaxSymbol) children[0]).stringValue().equals(MODULE);

        children[0] = eval.getGlobalState().myKernelModuleIdentifier;

        moduleStx = SyntaxSexp.make(eval, moduleStx.getLocation(), children);
        return moduleStx;
    }


    /**
     * Reads module source and declares it in the current namespace's registry.
     * The module is not instantiated.
     */
    private void evalModuleDeclaration(Evaluator eval,
                                       ModuleLocation loc,
                                       SyntaxSexp moduleDeclaration)
        throws FusionException
    {
        moduleDeclaration =
            wrapModuleIdentifierWithKernelBindings(eval, moduleDeclaration);

        Evaluator bodyEval = eval;
        String dirPath = loc.parentDirectory();
        if (dirPath != null)
        {
            bodyEval =
                eval.markedContinuation(myCurrentLoadRelativeDirectory,
                                        makeString(eval, dirPath));
        }

        // TODO Do we need an Evaluator with no continuation marks?

        callCurrentEval(bodyEval, moduleDeclaration);
        // TODO TAIL
    }


    /**
     * Reads module source and declares it in the current namespace's registry.
     * The module is not instantiated.
     */
    void loadModule(Evaluator eval, ModuleIdentity id, ModuleLocation loc)
        throws FusionException
    {
        try
        {
            SyntaxSexp moduleDeclaration = readModuleDeclaration(eval, id, loc);

            evalModuleDeclaration(eval, loc, moduleDeclaration);
        }
        catch (FusionException e)
        {
            String message =
                "Failure loading module " + id.absolutePath() +
                ": " + e.getMessage();
            throw new FusionException(message, e);

        }
    }
}
