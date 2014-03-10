// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionString.makeString;
import static com.amazon.fusion.FusionValue.UNDEF;
import com.amazon.ion.IonSystem;

/**
 * The core set of objects from that are needed by other parts of the
 * implementation.
 */
final class GlobalState
{
    static final String FUSION_SOURCE_EXTENSION = ".fusion";

    static final String KERNEL_MODULE_NAME = "/fusion/private/kernel";
    static final ModuleIdentity KERNEL_MODULE_IDENTITY =
        ModuleIdentity.forAbsolutePath(KERNEL_MODULE_NAME);

    static final String ALL_DEFINED_OUT = "all_defined_out";
    static final String BEGIN           = "begin";
    static final String DEFINE          = "define";
    static final String DEFINE_SYNTAX   = "define_syntax";
    static final String EOF             = "eof";
    static final String LAMBDA          = "lambda";
    static final String LETREC          = "letrec";
    static final String MODULE          = "module";
    static final String PROVIDE         = "provide";
    static final String REQUIRE         = "require";
    static final String TOP_INTERACTION = "#%top_interaction";

    final IonSystem          myIonSystem;
    final ModuleInstance     myKernelModule;
    final ModuleNameResolver myModuleNameResolver;
    final LoadHandler        myLoadHandler;
    final DynamicParameter   myCurrentNamespaceParam;

    final SyntaxSymbol       myKernelBeginIdentifier;
    final SyntaxSymbol       myKernelLambdaIdentifier;
    final SyntaxSymbol       myKernelLetrecIdentifier;
    final SyntaxSymbol       myKernelModuleIdentifier;

    final Binding myKernelAllDefinedOutBinding;
    final Binding myKernelBeginBinding;
    final Binding myKernelDefineBinding;
    final Binding myKernelDefineSyntaxBinding;
    final Binding myKernelProvideBinding;
    final Binding myKernelRequireBinding;

    private GlobalState(IonSystem          ionSystem,
                        ModuleInstance     kernel,
                        ModuleNameResolver resolver,
                        LoadHandler        loadHandler,
                        DynamicParameter   currentNamespaceParam)
    {
        myIonSystem             = ionSystem;
        myKernelModule          = kernel;
        myModuleNameResolver    = resolver;
        myLoadHandler           = loadHandler;
        myCurrentNamespaceParam = currentNamespaceParam;

        SyntaxWrap wrap = new ModuleRenameWrap(kernel);

        // WARNING: We pass null evaluator because we know its not used.
        //          That is NOT SUPPORTED for user code!
        myKernelBeginIdentifier  = SyntaxSymbol.make(null, wrap, BEGIN);
        myKernelLambdaIdentifier = SyntaxSymbol.make(null, wrap, LAMBDA);
        myKernelLetrecIdentifier = SyntaxSymbol.make(null, wrap, LETREC);
        myKernelModuleIdentifier = SyntaxSymbol.make(null, wrap, MODULE);

        myKernelAllDefinedOutBinding = kernelBinding(ALL_DEFINED_OUT);
        myKernelBeginBinding         = kernelBinding(BEGIN);
        myKernelDefineBinding        = kernelBinding(DEFINE);
        myKernelDefineSyntaxBinding  = kernelBinding(DEFINE_SYNTAX);
        myKernelProvideBinding       = kernelBinding(PROVIDE);
        myKernelRequireBinding       = kernelBinding(REQUIRE);
    }


    static GlobalState initialize(IonSystem system,
                                  FusionRuntimeBuilder builder,
                                  ModuleRegistry registry,
                                  Namespace initialCurrentNamespace)
        throws FusionException
    {
        // WARNING: We pass null evaluator because we know its not used.
        //          That is NOT SUPPORTED for user code!
        Object userDir =
            makeString(null, builder.getInitialCurrentDirectory().toString());

        DynamicParameter currentDirectory =
            new DynamicParameter(userDir);
        DynamicParameter currentLoadRelativeDirectory =
            new DynamicParameter(UNDEF);
        DynamicParameter currentModuleDeclareName =
            new DynamicParameter(UNDEF);
        DynamicParameter currentNamespaceParam =
            new DynamicParameter(initialCurrentNamespace);
        LoadHandler loadHandler =
            new LoadHandler(currentLoadRelativeDirectory, currentDirectory);
        ModuleNameResolver resolver =
            new ModuleNameResolver(loadHandler,
                                   currentLoadRelativeDirectory,
                                   currentDirectory,
                                   currentModuleDeclareName,
                                   builder.buildModuleRepositories());

        ModuleBuilderImpl ns =
            new ModuleBuilderImpl(resolver, registry, KERNEL_MODULE_IDENTITY);

        ns.define(ALL_DEFINED_OUT, new ProvideForm.AllDefinedOutForm());
        ns.define(BEGIN, new BeginForm());    // Needed by hard-coded macro

        ns.define("current_directory", currentDirectory,
                  "A [parameter](fusion/parameter.html) holding the thread-local working directory.");
        ns.define("current_ion_reader", new CurrentIonReaderParameter());
        ns.define("current_namespace", currentNamespaceParam,
                  "A [parameter](fusion/parameter.html) holding the thread-local namespace.  This value has no direct\n" +
                  "relationship to the namespace lexically enclosing the parameter call.");

        ns.define(DEFINE, new DefineForm());
        ns.define(DEFINE_SYNTAX, new DefineSyntaxForm());
        ns.define(EOF, FusionIo.eof(null),
                  "A unique value that denotes an end-of-file condition.");
        ns.define("if", new IfForm());          // Needed by hard-coded macro
        ns.define("java_new", new JavaNewProc());
        ns.define(LAMBDA, new LambdaForm());    // Needed by hard-coded macro
        ns.define(LETREC, new LetrecForm());    // Needed by hard-coded macro
        ns.define("load", new LoadProc(loadHandler));
        ns.define(MODULE, new ModuleForm(resolver, currentModuleDeclareName));
        ns.define(PROVIDE, new ProvideForm());
        ns.define("quote_syntax", new QuoteSyntaxForm()); // For fusion/syntax
        ns.define(REQUIRE, new RequireForm(resolver));
        ns.define(TOP_INTERACTION, new TopInteractionForm());

        ns.define("is_list",   new FusionList.IsListProc());
        ns.define("is_sexp",   new FusionSexp.IsSexpProc());
        ns.define("is_string", new FusionString.IsStringProc());
        ns.define("is_struct", new FusionStruct.IsStructProc());

        ns.define("=", new FusionCompare.EqualProc());

        ns.instantiate();

        ModuleInstance kernel = registry.lookup(KERNEL_MODULE_IDENTITY);

        GlobalState globals =
            new GlobalState(system, kernel, resolver, loadHandler,
                            currentNamespaceParam);
        return globals;
    }


    /** Helper to ensure we have a real binding. */
    private Binding kernelBinding(String name)
    {
        Binding b = myKernelModule.resolveProvidedName(name).originalBinding();
        assert b != null;
        return b;
    }
}
