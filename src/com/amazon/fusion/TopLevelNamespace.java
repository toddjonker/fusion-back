// Copyright (c) 2013-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionVoid.voidValue;
import com.amazon.fusion.FusionSymbol.BaseSymbol;
import com.amazon.fusion.ModuleNamespace.ModuleBinding;
import com.amazon.fusion.ModuleNamespace.ProvidedBinding;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


/**
 * Extended prepare-time {@link Namespace} that knows it's a top-level, and not
 * a {@linkplain ModuleNamespace module}.
 * <p>
 * The tricky part here is getting the precedence correct between imports and
 * top-level definitions.  The rule is that the last occurrence wins.  To
 * implement this we keep a counter of the number of times that
 * {@link #require} has been called, record that on bindings
 * when they are defined or redefined, and compare those numbers to determine
 * which binding has precedence.
 */
final class TopLevelNamespace
    extends Namespace
{
    /**
     * Maps from top-level names to definitions and/or module bindings.
     */
    static final class TopLevelBinding
        extends NsBinding
    {
        // Value is arbitrary and (hopefully) unique to aid in debugging.
        static final int REQUIRED_FROM_ELSEWHERE = -20130802;

        /**
         * Reference to the active binding for my identifier, either this
         * instance (when the top-level definition is active) or a binding
         * from an import.
         * This member is mutated whenever the identifier's meaning changes due
         * to a {@code define} overriding a {@code require}, or vice versa.
         */
        private NsBinding myTarget;

        /**
         * The precedence of the top-level definition (stored at my address).
         * This member is mutated whenever the identifier's meaning changes due
         * to a {@code define} overriding a {@code require}, or vice versa.
         */
        private int myPrecedence;

        private TopLevelBinding(SyntaxSymbol identifier, int address,
                                int precedence)
        {
            super(identifier, address);
            myTarget = this;
            myPrecedence = precedence;
        }

        /**
         * Creates a temporary binding for a top-level identifier that's
         * required, but not (currently) defined in the namespace.
         */
        private TopLevelBinding(TopLevelRequireBinding required)
        {
            super(required.getIdentifier(), REQUIRED_FROM_ELSEWHERE);
            myTarget = required.target();
            myPrecedence = REQUIRED_FROM_ELSEWHERE;
        }

        @Override
        public Binding target()
        {
            return myTarget;
        }

        @Override
        public Object lookup(Namespace ns)
        {
            if (myTarget == this)
            {
                return ns.lookupDefinition(this);
            }
            return myTarget.lookup(ns);
        }


        @Override
        public String mutationSyntaxErrorMessage()
        {
            boolean required =
                (myPrecedence == REQUIRED_FROM_ELSEWHERE || myTarget != this);

             return required
                 ? "cannot mutate imported binding"
                 : "mutation of top-level variables is not yet supported";
        }


        @Override
        public CompiledForm compileReference(Evaluator eval, Environment env)
            throws FusionException
        {
            if (myTarget == this)
            {
                // TODO FUSION-117 This should be pushed down but it fails there.
                assert (env.namespace().ownsBinding(this));
                return compileLocalTopReference(eval, env);
            }
            return myTarget.compileReference(eval, env);
        }

        @Override
        public CompiledForm compileTopReference(Evaluator eval,
                                                Environment env,
                                                SyntaxSymbol id)
            throws FusionException
        {
            return new CompiledTopVariableReference(myAddress);
        }

        @Override
        CompiledForm compileDefine(Evaluator eval,
                                   Environment env,
                                   SyntaxSymbol id,
                                   CompiledForm valueForm)
            throws FusionException
        {
            if (myTarget == this)
            {
                return env.namespace().compileDefine(eval, this,
                                                     id, valueForm);
            }

            // Override of visible module binding.
            return myTarget.compileDefine(eval, env, id, valueForm);
        }

        @Override
        CompiledForm compileDefineSyntax(Evaluator eval,
                                         Environment env,
                                         CompiledForm valueForm)
        {
            // TODO FUSION-192 This should bind after evaluation, as 'define'.
            assert myTarget == this;
            return super.compileDefineSyntax(eval, env, valueForm);
        }

        @Override
        public String toString()
        {
            return "{{{TopLevelBinding " +
                (myTarget == this ? getIdentifier().debugString() : myTarget) +
                "}}}";
        }

        @Override
        void dump(PrintStream out) throws IOException
        {
            out.print("TopLevelBinding::{");
            _dump(out);
            out.print(",target:");
            myTarget.dump(out);
            out.print(",precedence:" + myPrecedence);
            out.print("}");
        }
    }


    private static final class TopLevelWrap
        extends EnvironmentWrap
    {
        private final TopLevelNamespace myTopNs;

        TopLevelWrap(TopLevelNamespace ns)
        {
            super(ns);
            myTopNs = ns;
        }


        @Override
        Binding resolveTop(BaseSymbol           name,
                           Iterator<SyntaxWrap> moreWraps,
                           Set<MarkWrap>         returnMarks)
        {
            if (moreWraps.hasNext())
            {
                SyntaxWrap nextWrap = moreWraps.next();
                return nextWrap.resolve(name, moreWraps, returnMarks);
            }
            return null;
        }

        @Override
        Binding resolve(BaseSymbol           name,
                        Iterator<SyntaxWrap> moreWraps,
                        Set<MarkWrap>        returnMarks)
        {
            if (moreWraps.hasNext())
            {
                SyntaxWrap nextWrap = moreWraps.next();
                // Prior bindings never "leak through" a top-level, so we ignore
                // this binding.  We still want to collect the marks though.
                nextWrap.resolve(name, moreWraps, returnMarks);
            }

            // Check our environment directly. This will handle identifiers
            // that have top-level definitions, but not those that only map to
            // module bindings.
            TopLevelBinding definedBinding = (TopLevelBinding)
                getEnvironment().substituteFree(name, returnMarks);

            // NOTE: Adding static import for REQUIRED_FROM_ELSEWHERE caused
            //       bogus compiler errors in JDK 1.7u25
            assert (definedBinding == null
                    || definedBinding.myAddress == TopLevelBinding.REQUIRED_FROM_ELSEWHERE
                    || myTopNs.ownsBinding(definedBinding));

            // Look for an imported binding, then decide which one wins.
            // We lookup required bindings as if they were free, since top-level
            // and free bindings are equivalent.  Otherwise we'd have problems
            // since we may not have found a TopLevelBinding here, but one
            // is created later.
            TopLevelRequireBinding requiredBinding =
                myTopNs.substituteFreeImport(name, returnMarks);

            if (requiredBinding == null)
            {
                // No matching import, so use any top-level definition.
                return definedBinding;
            }
            if (definedBinding == null)
            {
                // We have an import, but no top-level definition.
                // TODO this is too common to throw away binding instance?
                //    Perhaps, but this binding doesn't have a local address
                //    and we may need to assign one later.
                return new TopLevelBinding(requiredBinding);

                // TODO FUSION-117 Just return requiredBinding.myBinding?
                // That doesn't work after namespace-syntax-introduce
            }

            if (definedBinding.myPrecedence <= requiredBinding.myPrecedence)
            {
                definedBinding.myTarget = requiredBinding.target();
            }

            return definedBinding;
        }

        @Override
        Iterator<SyntaxWrap> iterator()
        {
            return null;
        }

        @Override
        public String toString()
        {
            return "{{{TopLevelWrap}}}";
        }

        @Override
        void dump(PrintStream out) throws IOException
        {
            out.print("TopLevelWrap::{");
            _dump(out);
            out.print("}");
        }
    }


    /**
     * Denotes a module-level binding imported into a top-level namespace via
     * {@code require}.
     */
    static final class TopLevelRequireBinding
        extends Binding
    {
        private final int myPrecedence;
        private final SyntaxSymbol myIdentifier;
        private final ProvidedBinding myTarget;

        private TopLevelRequireBinding(int precedence,
                                       SyntaxSymbol identifier,
                                       ProvidedBinding target)
        {
            myPrecedence = precedence;
            myIdentifier = identifier;
            myTarget = target;
        }

        @Override
        public final BaseSymbol getName()
        {
            return myIdentifier.getName();
        }

        SyntaxSymbol getIdentifier()
        {
            return myIdentifier;
        }

        @Override
        public ModuleBinding target()
        {
            return myTarget.target();
        }

        @Override
        public Object lookup(Namespace ns)
        {
            return myTarget.lookup(ns);
        }

        @Override
        public CompiledForm compileReference(Evaluator eval, Environment env)
            throws FusionException
        {
            return myTarget.compileReference(eval, env);
        }

        @Override
        public CompiledForm compileTopReference(Evaluator eval,
                                                Environment env,
                                                SyntaxSymbol id)
            throws FusionException
        {
            String message =
                "#%top not implemented for top-require binding: " + this;
            throw new IllegalStateException(message);
        }

        @Override
        public CompiledForm compileSet(Evaluator eval, Environment env,
                                       CompiledForm valueForm)
            throws FusionException
        {
            return myTarget.compileSet(eval, env, valueForm);
        }

        @Override
        public boolean equals(Object other)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString()
        {
            return "{{{TopLevelRequireBinding "
                 + target().myModuleId.absolutePath()
                 + ' ' + getName() + "}}}";
        }

        @Override
        void dump(PrintStream out) throws IOException
        {
            out.print("TopLevelRequireBinding::{target:");
            myTarget.dump(out);
            out.print(",precedence:" + myPrecedence);
            out.print("}");
        }
    }



    /**
     * Maps each top-level name to the bindings associated with it.
     * There may be multiple variants since the same name may occur with
     * different marks.
     */
    private final Map<BaseSymbol,TopLevelRequireBinding[]> myRequiredBindings =
        new IdentityHashMap<>();

    /**
     * Every `require` gets its own precedence level, starting at zero (which
     * is usually the language). Imports win over definitions at the same
     * precedence.
     */
    private int myCurrentPrecedence = 0;


    /**
     * Constructs a top-level namespace. Any bindings will need to be
     * {@code require}d or {@code define}d.
     */
    TopLevelNamespace(ModuleRegistry registry)
    {
        super(registry, ModuleIdentity.forTopLevel(),
              new Function<Namespace, SyntaxWraps>()
              {
                  @Override
                  public SyntaxWraps apply(Namespace _this) {
                      TopLevelNamespace __this = (TopLevelNamespace) _this;
                      return SyntaxWraps.make(new TopLevelWrap(__this));
                  }
              });
    }


    @Override
    NsBinding newDefinedBinding(SyntaxSymbol identifier, int address)
    {
        return new TopLevelBinding(identifier, address, myCurrentPrecedence);
    }


    @Override
    SyntaxSymbol predefine(SyntaxSymbol identifier, SyntaxValue formForErrors)
        throws FusionException
    {
        identifier = identifier.copyAndResolveTop();

        Binding binding = localResolve(identifier);
        if (binding == null)
        {
            binding = addDefinedBinding(identifier);
        }
        else
        {
            TopLevelBinding topBinding = (TopLevelBinding) binding;

            // Give this pre-existing binding precedence over any imports that
            // may have happened since the last time it was defined.
            topBinding.myPrecedence = myCurrentPrecedence;

            // Swing the namespace mapping back to this definition
            topBinding.myTarget = topBinding;
        }

        return identifier.copyReplacingBinding(binding);
    }


    @Override
    void require(Evaluator eval, ModuleInstance module)
        throws FusionException
    {
        int precedence = myCurrentPrecedence++;

        // We assume that a module doesn't export a duplicate name.
        for (ProvidedBinding binding : module.providedBindings())
        {
            SyntaxSymbol id = SyntaxSymbol.make(eval, null, binding.getName());
            installRequiredBinding(precedence, id, binding);
        }
    }

    private void installRequiredBinding(int             precedence,
                                        SyntaxSymbol    localId,
                                        ProvidedBinding binding)
    {
        BaseSymbol name = binding.getName();

        TopLevelRequireBinding topBinding =
            new TopLevelRequireBinding(precedence, localId, binding);

        TopLevelRequireBinding[] variants = myRequiredBindings.get(name);
        if (variants == null)
        {
            variants = new TopLevelRequireBinding[] { topBinding };
        }
        else
        {
            // Do we already have a required binding that matches?
            boolean matched = false;

            int len = variants.length;
            for (int i = 0; i < len; i++)
            {
                TopLevelRequireBinding b = variants[i];
                if (localId.freeIdentifierEqual(b.myIdentifier))
                {
                    variants[i] = topBinding;
                    matched = true;
                    break;
                }
            }

            if (! matched)
            {
                variants = Arrays.copyOf(variants, len + 1);
                variants[len] = topBinding;
            }
        }
        myRequiredBindings.put(name, variants);
    }


    private final TopLevelRequireBinding substituteFreeImport(BaseSymbol name,
                                                              Set<MarkWrap> marks)
    {
        TopLevelRequireBinding[] variants = myRequiredBindings.get(name);
        if (variants != null)
        {
            for (TopLevelRequireBinding b : variants)
            {
                assert name == b.myIdentifier.getName();
                if (b.myIdentifier.resolvesFree(name, marks))
                {
                    return b;
                }
            }
        }
        return null;
    }


    @Override
    public void dump(PrintStream out) throws IOException
    {
        out.print("TopLevelNamespace::{");
        _dump(out);
        out.print("}");
    }


    @Override
    CompiledForm compileDefine(Evaluator eval,
                               FreeBinding binding,
                               SyntaxSymbol id,
                               CompiledForm valueForm)
        throws FusionException
    {
        return new CompiledFreeDefine(id, valueForm);
    }


    @Override
    CompiledForm compileDefine(Evaluator eval,
                               TopLevelBinding binding,
                               SyntaxSymbol id,
                               CompiledForm valueForm)
        throws FusionException
    {
        // We can't trust the identifier in the binding, since it may have
        // resolved to an id with a different set of marks.
        return new CompiledFreeDefine(id, valueForm);
    }


    @Override
    CompiledForm compileDefine(Evaluator eval,
                               ModuleBinding binding,
                               SyntaxSymbol id,
                               CompiledForm valueForm)
        throws FusionException
    {
        // The lexical context of the bound identifier resolves to some module.
        // We'll use a top-level binding instead.
        return new CompiledFreeDefine(id, valueForm);
    }


    @Override
    CompiledForm compileFreeTopReference(SyntaxSymbol identifier)
    {
        return new CompiledFreeVariableReference(identifier);
    }


    //========================================================================
    // Compiled Forms

    private static final class CompiledFreeDefine
        implements CompiledForm
    {
        private final SyntaxSymbol myId;
        private final CompiledForm myValueForm;

        CompiledFreeDefine(SyntaxSymbol id, CompiledForm valueForm)
        {
            myId = id;
            myValueForm = valueForm;
        }

        @Override
        public Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            Object value = eval.eval(store, myValueForm);

            value = processValue(eval, store, value);

            TopLevelNamespace ns = (TopLevelNamespace) store.namespace();
            SyntaxSymbol boundId = ns.predefine(myId, myId);
            TopLevelBinding binding = (TopLevelBinding) boundId.getBinding();

            ns.set(binding.myAddress, value);

            if (value instanceof NamedValue)
            {
                ((NamedValue)value).inferName(myId.stringValue());
            }

            return voidValue(eval);
        }

        Object processValue(Evaluator eval, Store store, Object value)
            throws FusionException
        {
            return value;
        }
    }


    /**
     * A reference to a top-level variable in the lexically-enclosing
     * namespace, when the binding isn't known at compile-time.
     * In other words, a forward reference.
     * <p>
     * The first time the variable is dereferenced, we have to resolve the
     * binding. We cache the resulting address so we only have to perform the
     * expensive work once.
     * <p>
     * This uses a rare safe instance of the double-checked locking idiom:
     * http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html
     */
    private static final class CompiledFreeVariableReference
        implements CompiledForm
    {
        private SyntaxSymbol myId;
        private volatile int myAddress = -1;

        CompiledFreeVariableReference(SyntaxSymbol id)
        {
            myId = id;
        }

        @Override
        public Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            Namespace ns = (Namespace) store.namespace();

            int address = myAddress;
            if (address < 0)
            {
                synchronized (this)
                {
                    if (myAddress < 0)
                    {
                        SyntaxSymbol topId = myId.copyAndResolveTop();

                        NsBinding binding = ns.localResolve(topId);
                        if (binding == null)
                        {
                            throw new UnboundIdentifierException(myId);
                        }

                        myAddress = binding.myAddress;
                        myId = null;
                    }
                }

                address = myAddress;
            }

            Object result = ns.lookup(address);

            // There's a potential failure here: the binding may have had an
            // address assigned, but not yet a value. That could happen when
            // another thread is in the midst of defining the binding.
            // In such a scenario, the `result` may be null or even corrupt
            // (if we happen to read from the store while it's being updated).
            // However, that scenario is an *application* thread-safety
            // violation since Fusion doesn't promise that concurrent
            // mutation of a top-level namespace is thread-safe.

            return result;
        }
    }
}
