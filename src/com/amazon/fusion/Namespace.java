// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionIo.safeWriteToString;
import static com.amazon.fusion.FusionVoid.voidValue;
import com.amazon.fusion.FusionSymbol.BaseSymbol;
import com.amazon.fusion.ModuleNamespace.ModuleBinding;
import com.amazon.fusion.TopLevelNamespace.TopLevelBinding;
import com.amazon.ion.util.IonTextUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Expand- and compile-time environment for all top-level sequences, and
 * eval-time store for non-module top levels.
 * <p>
 * Since top-levels and modules have different behavior around imports and
 * defines, that responsibility is delegated to subclasses.
 */
abstract class Namespace
    implements Environment, NamespaceStore
{
    /**
     * Denotes a namespace-level binding, either top-level or module-level.
     *
     * @see TopLevelBinding
     * @see ModuleBinding
     */
    abstract static class NsBinding
        extends Binding
    {
        private final SyntaxSymbol myIdentifier;
        final int myAddress;

        NsBinding(SyntaxSymbol identifier, int address)
        {
            myIdentifier = identifier;
            myAddress = address;
        }

        @Override
        public final BaseSymbol getName()
        {
            return myIdentifier.getName();
        }

        final SyntaxSymbol getIdentifier()
        {
            return myIdentifier;
        }

        final CompiledForm compileLocalTopReference(Evaluator   eval,
                                                    Environment env)
            throws FusionException
        {
            // TODO This fails when a macro references a prior local defn
            // since the defn isn't installed yet.  I think the code is bad
            // and mixes phases of macro processing.
//          assert (env.namespace().ownsBinding(this));
            return new CompiledTopVariableReference(myAddress);
        }

        @Override
        public CompiledForm compileSet(Evaluator eval, Environment env,
                                       CompiledForm valueForm)
            throws FusionException
        {
            throw new IllegalStateException("Mutation should have been rejected");
        }

        CompiledForm compileDefineSyntax(Evaluator eval,
                                         Environment env,
                                         CompiledForm valueForm)
        {
            String name = getName().stringValue();
            return new CompiledTopDefineSyntax(name, myAddress, valueForm);
        }

        @Override
        public boolean equals(Object other)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public abstract String toString(); // Force subclasses to implement

        void _dump(PrintStream out) throws IOException
        {
            out.print("ident:");
            myIdentifier.dump(out);
            out.print(",address:" + myAddress);
        }
    }

    private final ModuleRegistry myRegistry;
    private final ModuleIdentity myModuleId;

    /**
     * Assigns required modules to integer addresses, for use in compiled
     * forms.
     */
    private final HashMap<ModuleIdentity,Integer> myRequiredModules =
        new HashMap<>();
    private final ArrayList<ModuleStore> myRequiredModuleStores =
        new ArrayList<>();

    private final SyntaxWraps          myWraps;
    private final ArrayList<NsBinding> myBindings = new ArrayList<>();
    private final ArrayList<Object>    myValues   = new ArrayList<>();
    private ArrayList<BindingDoc> myBindingDocs;

    /**
     * @param registry must not be null.
     * @param id must not be null.
     * @param wraps generates the {@link SyntaxWraps} for this namespace, given
     *   a reference to {@code this}.
     */
    Namespace(ModuleRegistry                   registry,
              ModuleIdentity                   id,
              Function<Namespace, SyntaxWraps> wraps)
    {
        myRegistry = registry;
        myModuleId = id;
        myWraps    = wraps.apply(this);
    }

    @Override
    public final ModuleRegistry getRegistry()
    {
        return myRegistry;
    }

    @Override
    public final Namespace namespace()
    {
        return this;
    }

    @Override
    public final int getDepth()
    {
        return 0;
    }

    @Override
    public final Object lookup(int rib, int address)
    {
        String message = "Rib not found: " + rib + ',' + address;
        throw new IllegalStateException(message);
    }

    @Override
    public final void set(int rib, int address, Object value)
    {
        String message = "Rib not found: " + rib + ',' + address;
        throw new IllegalStateException(message);
    }

    /**
     * Gets the identity of the module associated with this namespace.
     * For non-module namespaces, this returns a synthetic identity that's
     * unique amongst all namespaces.
     *
     * @return not null.
     */
    final ModuleIdentity getModuleId()
    {
        return myModuleId;
    }


    /**
     * Collects the bindings defined in this module; does not include imported
     * bindings.
     *
     * @return not null.
     */
    final Collection<NsBinding> getBindings()
    {
        return Collections.unmodifiableCollection(myBindings);
    }

    void _dump(PrintStream out) throws IOException
    {
        out.print("module:");
        IonTextUtils.printString(myModuleId.toString());
    }

    //========================================================================

    /**
     * Adds wraps to the syntax object to give it the bindings of this
     * namespace and of required modules.
     */
    final SyntaxValue syntaxIntroduce(SyntaxValue source)
        throws FusionException
    {
        // TODO there's a case where we are applying the same wraps that are
        // already on the source.  This happens when expand-ing (and maybe when
        // eval-ing at top-level source that's from that same context.
        source = source.addWraps(myWraps);
        return source;
    }


    /**
     * @param marks not null.
     *
     * @return null if identifier isn't bound here.
     */
    final NsBinding localSubstitute(Binding binding, Set<MarkWrap> marks)
    {
        for (NsBinding b : myBindings)
        {
            if (b.myIdentifier.resolvesBound(binding, marks))
            {
                return b;
            }
        }
        return null;
    }

    @Override
    public final Binding substitute(Binding binding, Set<MarkWrap> marks)
    {
        Binding subst = localSubstitute(binding, marks);
        if (subst == null) subst = binding;
        return subst;
    }

    @Override
    public final NsBinding substituteFree(BaseSymbol name, Set<MarkWrap> marks)
    {
        for (NsBinding b : myBindings)
        {
            if (b.myIdentifier.resolvesFree(name, marks))
            {
                return b;
            }
        }
        return null;
    }


    /**
     * @return null if identifier isn't bound here.
     */
    final NsBinding localResolve(SyntaxSymbol identifier)
    {
        Binding resolvedRequestedId = identifier.resolve();
        Set<MarkWrap> marks = identifier.computeMarks();
        if (resolvedRequestedId instanceof FreeBinding)
        {
            return substituteFree(identifier.getName(), marks);
        }
        return localSubstitute(resolvedRequestedId, marks);
    }


    /**
     * @param name must be non-empty.
     *
     * @return null is equivalent to a {@link FreeBinding}.
     */
    final Binding resolve(BaseSymbol name)
    {
        return myWraps.resolve(name);
    }

    /**
     * @param name must be non-empty.
     *
     * @return null is equivalent to a {@link FreeBinding}.
     */
    final Binding resolve(String name)
    {
        BaseSymbol symbol = FusionSymbol.makeSymbol(null, name);
        return resolve(symbol);
    }


    abstract NsBinding newDefinedBinding(SyntaxSymbol identifier, int address);


    final NsBinding addDefinedBinding(SyntaxSymbol identifier)
        throws FusionException
    {
        int address = myBindings.size();
        NsBinding binding = newDefinedBinding(identifier, address);
        myBindings.add(binding);
        return binding;
    }


    /**
     * Creates a binding, but no value, for a name.
     * Used during expansion phase, before evaluating the right-hand side.
     *
     * @return a copy of the identifier that has the new binding attached.
     */
    abstract SyntaxSymbol predefine(SyntaxSymbol identifier,
                                    SyntaxValue formForErrors)
        throws FusionException;


    /**
     * Creates or updates a namespace-level binding.
     * Allows rebinding of existing names!
     *
     * @param value must not be null
     */
    final void bind(NsBinding binding, Object value)
    {
        set(binding.myAddress, value);

        if (value instanceof NamedValue)
        {
            String inferredName = binding.getName().stringValue();
            if (inferredName != null)
            {
                ((NamedValue)value).inferName(inferredName);
            }
        }
    }


    private <T> void set(ArrayList<T> list, int address, T value)
    {
        int size = list.size();
        if (address < size)
        {
            list.set(address, value);
        }
        else // We need to grow the list. Annoying lack of API to do this.
        {
            list.ensureCapacity(myBindings.size()); // Grow all at once
            for (int i = size; i < address; i++)
            {
                list.add(null);
            }
            list.add(value);
        }
    }


    /**
     * Updates a pre-defined namespace-level variable.
     * Allows rebinding of existing names!
     *
     * @param value must not be null
     */
    @Override
    public final void set(int address, Object value)
    {
        set(myValues, address, value);
    }


    /**
     * Creates or updates a namespace-level binding.
     * Allows rebinding of existing names!
     *
     * @param value must not be null
     *
     * @throws IllegalArgumentException if the name is null or empty.
     */
    public final void bind(String name, Object value)
        throws FusionException
    {
        if (name == null || name.length() == 0)
        {
            String message = "bound name must be non-null and non-empty";
            throw new IllegalArgumentException(message);
        }

        // WARNING: We pass null evaluator because we know its not used.
        //          That is NOT SUPPORTED for user code!
        SyntaxSymbol identifier = SyntaxSymbol.make(null, name);

        identifier = predefine(identifier, null);
        NsBinding binding = (NsBinding) identifier.getBinding();
        bind(binding, value);
    }


    /**
     * @param modulePath is an absolute or relative module path.
     */
    final void require(Evaluator eval, String modulePath)
        throws FusionException
    {
        ModuleNameResolver resolver =
            eval.getGlobalState().myModuleNameResolver;
        ModuleIdentity id =
            resolver.resolveModulePath(eval,
                                       getModuleId(),
                                       modulePath,
                                       true /* load */,
                                       null /* stxForErrors */);
        require(eval, id);
    }

    final void require(Evaluator eval, ModuleIdentity id)
        throws FusionException
    {
        ModuleInstance module = myRegistry.instantiate(eval, id);
        require(eval, module);
    }

    abstract void require(Evaluator eval, ModuleInstance module)
        throws FusionException;



    final boolean ownsBinding(NsBinding binding)
    {
        int address = binding.myAddress;
        return (address < myBindings.size()
                && binding == myBindings.get(address));
    }

    final boolean ownsBinding(Binding binding)
    {
        if (binding instanceof NsBinding)
        {
            return ownsBinding((NsBinding) binding);
        }
        return false;
    }



    abstract CompiledForm compileDefine(Evaluator eval,
                                        FreeBinding binding,
                                        SyntaxSymbol id,
                                        CompiledForm valueForm)
        throws FusionException;

    abstract CompiledForm compileDefine(Evaluator eval,
                                        TopLevelBinding binding,
                                        SyntaxSymbol id,
                                        CompiledForm valueForm)
        throws FusionException;

    abstract CompiledForm compileDefine(Evaluator eval,
                                        ModuleBinding binding,
                                        SyntaxSymbol id,
                                        CompiledForm valueForm)
        throws FusionException;


    /**
     * Compile a free variable reference.  These are allowed at top-level but
     * not within a module.
     */
    abstract CompiledForm compileFreeTopReference(SyntaxSymbol identifier)
        throws FusionException;


    /**
     * Looks for a definition's value in this namespace, ignoring imports.
     *
     * @return the binding's value, or null if there is none.
     */
    final Object lookupDefinition(NsBinding binding)
    {
        int address = binding.myAddress;
        if (address < myValues.size())              // for prepare-time lookup
        {
            NsBinding localBinding = myBindings.get(address);
            if (binding == localBinding)
            {
                return myValues.get(address);
            }
        }
        return null;
    }


    /**
     * Looks for a binding's value in this namespace, finding both definitions
     * and imports.
     *
     * @return the binding's value, or null if there is none.
     */
    final Object lookup(Binding binding)
    {
        return binding.lookup(this);
    }


    /**
     * Looks for a binding's value in this namespace, finding both definitions
     * and imports.
     *
     * @return the binding's value, or null if there is none.
     */
    final Object lookup(String name)
    {
        Binding b = resolve(name);
        if (b == null)
        {
            return b;
        }
        else
        {
            return lookup(b);
        }
    }


    @Override
    public final Object lookup(int address)
    {
        return myValues.get(address);
    }

    @Override
    public final Object lookupImport(int moduleAddress, int bindingAddress)
    {
        ModuleStore store = myRequiredModuleStores.get(moduleAddress);
        return store.lookup(bindingAddress);
    }

    final Object[] extractValues()
    {
        return myValues.toArray();
    }


    /**
     * Translates a required module identity into an integer address for use
     * by compiled forms.  Note that some required modules may not be
     * explicitly declared in the source of the module, since they may come in
     * via macro expansion.
     * <p>
     * Building this list is delayed to compile-time to avoid compiling
     * addresses for modules that are declared but never used.
     * This may be a useless optimization.
     * <p>
     * @return a zero-based address for the module, valid only within this
     * namespace (or its compiled form).
     */
    final synchronized int requiredModuleAddress(ModuleIdentity moduleId)
    {
        Integer id = myRequiredModules.get(moduleId);
        if (id == null)
        {
            ModuleInstance module = myRegistry.lookup(moduleId);

            id = myRequiredModules.size();
            myRequiredModules.put(moduleId, id);
            myRequiredModuleStores.add(module.getNamespace());
        }
        return id;
    }

    final synchronized ModuleIdentity[] requiredModuleIds()
    {
        ModuleIdentity[] ids = new ModuleIdentity[myRequiredModules.size()];
        for (Map.Entry<ModuleIdentity, Integer> entry
                : myRequiredModules.entrySet())
        {
            int address = entry.getValue();
            ids[address] = entry.getKey();
        }
        return ids;
    }

    @Override
    public final ModuleStore lookupRequiredModule(int moduleAddress)
    {
        return myRequiredModuleStores.get(moduleAddress);
    }


    //========================================================================
    // Documentation


    final void setDoc(String name, BindingDoc.Kind kind, String doc)
    {
        BindingDoc bDoc = new BindingDoc(name, kind,
                                         null, // usage
                                         doc);
        setDoc(name, bDoc);
    }

    final void setDoc(String name, BindingDoc doc)
    {
        NsBinding binding = (NsBinding) resolve(name);
        setDoc(binding.myAddress, doc);
    }

    public void setDoc(int address, BindingDoc doc)
    {
        if (myBindingDocs == null)
        {
            myBindingDocs = new ArrayList<BindingDoc>();
        }
        set(myBindingDocs, address, doc);
    }

    final BindingDoc document(int address)
    {
        if (myBindingDocs != null && address < myBindingDocs.size())
        {
            return myBindingDocs.get(address);
        }
        return null;
    }

    /**
     * @return may be shorter than the number of provided variables.
     */
    final BindingDoc[] extractBindingDocs()
    {
        if (myBindingDocs == null) return BindingDoc.EMPTY_ARRAY;

        BindingDoc[] docs = myBindingDocs.toArray(BindingDoc.EMPTY_ARRAY);
        myBindingDocs = null;
        return docs;
    }


    //========================================================================


    /**
     * A reference to a top-level variable in the lexically-enclosing namespace.
     */
    static final class CompiledTopVariableReference
        implements CompiledForm
    {
        final int myAddress;

        CompiledTopVariableReference(int address)
        {
            myAddress = address;
        }

        @Override
        public Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            NamespaceStore ns = store.namespace();
            Object result = ns.lookup(myAddress);
            assert result != null : "No value for namespace address " + myAddress;
            return result;
        }
    }


    static class CompiledTopDefine
        implements CompiledForm
    {
        private final String       myName;
        private final int          myAddress;
        private final CompiledForm myValueForm;

        CompiledTopDefine(String name, int address, CompiledForm valueForm)
        {
            assert name != null;
            myName      = name;
            myAddress   = address;
            myValueForm = valueForm;
        }

        @Override
        public final Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            Object value = eval.eval(store, myValueForm);

            value = processValue(eval, store, value);

            NamespaceStore ns = store.namespace();
            ns.set(myAddress, value);

            if (value instanceof NamedValue)
            {
                ((NamedValue)value).inferName(myName);
            }

            return voidValue(eval);
        }

        Object processValue(Evaluator eval, Store store, Object value)
            throws FusionException
        {
            return value;
        }
    }


    private static final class CompiledTopDefineSyntax
        extends CompiledTopDefine
    {
        private CompiledTopDefineSyntax(String name, int address,
                                        CompiledForm valueForm)
        {
            super(name, address, valueForm);
        }

        @Override
        Object processValue(Evaluator eval, Store store, Object value)
            throws FusionException
        {
            if (value instanceof Procedure)
            {
                Procedure xformProc = (Procedure) value;
                value = new MacroForm(xformProc);
            }
            else if (! (value instanceof SyntacticForm))
            {
                String message =
                    "define_syntax value is not a transformer: " +
                    safeWriteToString(eval, value);
                throw new ContractException(message);
            }

            return value;
        }
    }
}
