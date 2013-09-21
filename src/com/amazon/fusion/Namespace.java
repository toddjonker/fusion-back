// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionVoid.voidValue;
import static com.amazon.fusion.FusionWrite.safeWriteToString;
import com.amazon.fusion.ModuleNamespace.ModuleBinding;
import com.amazon.fusion.TopLevelNamespace.TopLevelBinding;
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
        public final String getName()
        {
            return myIdentifier.stringValue();
        }

        final SyntaxSymbol getIdentifier()
        {
            return myIdentifier;
        }

        @Override
        public boolean sameTarget(Binding other)
        {
            return this == other.originalBinding();
        }

        @Override
        public Object lookup(Environment env)
        {
            // Skip over any lexical environments and go straight to the top.
            return env.namespace().lookup(this);
        }

        CompiledForm compileLocalTopReference(Evaluator eval)
            throws FusionException
        {
            return new CompiledTopVariableReference(myAddress);
        }

        @Override
        public CompiledForm compileSet(Evaluator eval, Environment env,
                                       CompiledForm valueForm)
            throws FusionException
        {
            String message =
                "Mutation of top-level variables is not supported";
            throw new ContractException(message);
        }

        @Override
        public boolean equals(Object other)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public abstract String toString(); // Force subclasses to implement
    }

    private final ModuleRegistry myRegistry;
            final ModuleIdentity myModuleId;

    /**
     * Assigns required modules to integer addresses, for use in compiled
     * forms.
     */
    private final HashMap<ModuleIdentity,Integer> myRequiredModules =
        new HashMap<>();
    private final ArrayList<ModuleStore> myRequiredModuleStores =
        new ArrayList<>();

    private SyntaxWraps myWraps;
    private final ArrayList<NsBinding> myBindings = new ArrayList<>();
    private final ArrayList<Object>    myValues   = new ArrayList<>();
    private ArrayList<BindingDoc> myBindingDocs;


    Namespace(ModuleRegistry registry, ModuleIdentity id)
    {
        myRegistry = registry;
        myModuleId = id;

        SyntaxWrap wrap = new EnvironmentRenameWrap(this);
        myWraps = SyntaxWraps.make(wrap);
    }

    Namespace(ModuleRegistry registry, ModuleIdentity id, SyntaxWrap... wraps)
    {
        myRegistry = registry;
        myModuleId = id;
        myWraps = SyntaxWraps.make(wraps);
    }


    @Override
    public ModuleRegistry getRegistry()
    {
        return myRegistry;
    }

    @Override
    public Namespace namespace()
    {
        return this;
    }

    @Override
    public int getDepth()
    {
        return 0;
    }

    @Override
    public Object lookup(int rib, int address)
    {
        throw new IllegalStateException("Rib not found");
    }

    @Override
    public void set(int rib, int address, Object value)
    {
        throw new IllegalStateException("Rib not found");
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
    Collection<NsBinding> getBindings()
    {
        return Collections.unmodifiableCollection(myBindings);
    }

    //========================================================================

    /**
     * Adds wraps to the syntax object to give it the bindings of this
     * namespace and of required modules.
     */
    SyntaxValue syntaxIntroduce(SyntaxValue source)
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
    NsBinding localSubstitute(Binding binding, Set<Integer> marks)
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
    public Binding substitute(Binding binding, Set<Integer> marks)
    {
        Binding subst = localSubstitute(binding, marks);
        if (subst == null) subst = binding;
        return subst;
    }

    @Override
    public NsBinding substituteFree(String name, Set<Integer> marks)
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
        Set<Integer> marks = identifier.computeMarks();
        if (resolvedRequestedId instanceof FreeBinding)
        {
            return substituteFree(identifier.stringValue(), marks);
        }
        return localSubstitute(resolvedRequestedId, marks);
    }


    /**
     * @param name must be non-empty.
     * @return null is equivalent to a {@link FreeBinding}.
     */
    Binding resolve(String name)
    {
        return myWraps.resolve(name);
    }


    abstract NsBinding newBinding(SyntaxSymbol identifier, int address);


    NsBinding addBinding(SyntaxSymbol identifier)
        throws FusionException
    {
        int address = myBindings.size();
        NsBinding binding = newBinding(identifier, address);
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
    void bind(NsBinding binding, Object value)
    {
        set(binding.myAddress, value);

        if (value instanceof NamedValue)
        {
            String inferredName = binding.getName();
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
        else // We need to grow myValues. Annoying lack of API to do this.
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
    public void set(int address, Object value)
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
    public void bind(String name, Object value)
        throws FusionException
    {
        if (name == null || name.length() == 0)
        {
            String message = "bound name must be non-null and non-empty";
            throw new IllegalArgumentException(message);
        }

        SyntaxSymbol identifier = SyntaxSymbol.make(name);
        identifier = predefine(identifier, null);
        NsBinding binding = (NsBinding) identifier.getBinding();
        bind(binding, value);
    }


    void require(Evaluator eval, String modulePath)
        throws FusionException
    {
        RequireForm requireForm = eval.getGlobalState().myRequireForm;
        requireForm.require(eval, this, modulePath);
    }

    void require(Evaluator eval, ModuleIdentity id)
        throws FusionException
    {
        ModuleInstance module = myRegistry.instantiate(eval, id);
        require(module);
    }

    abstract void require(ModuleInstance module)
        throws FusionException;

    void addWrap(SyntaxWrap wrap)
    {
        myWraps = myWraps.addWrap(wrap);
    }


    boolean ownsBinding(NsBinding binding)
    {
        int address = binding.myAddress;
        return (address < myBindings.size()
                && binding == myBindings.get(address));
    }

    boolean ownsBinding(Binding binding)
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


    abstract CompiledForm compileDefineSyntax(Evaluator eval,
                                              FreeBinding binding,
                                              SyntaxSymbol id,
                                              CompiledForm valueForm)
        throws FusionException;

    abstract CompiledForm compileDefineSyntax(Evaluator eval,
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


    @Override
    public Object lookup(Binding binding)
    {
        if (binding instanceof NsBinding)    // else it can't possibly be ours
        {
            return lookup((NsBinding) binding);
        }
        return null;
    }

    public Object lookup(NsBinding binding)
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

    Object lookup(String name)
    {
        Binding b = resolve(name);
        return lookup(b);
    }


    @Override
    public Object lookup(int address)
    {
        return myValues.get(address);
    }

    @Override
    public Object lookupImport(int moduleAddress, int bindingAddress)
    {
        ModuleStore store = myRequiredModuleStores.get(moduleAddress);
        return store.lookup(bindingAddress);
    }

    Object[] extractValues()
    {
        Object[] values = new Object[myValues.size()];
        myValues.toArray(values);
        return values;
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
    synchronized int requiredModuleAddress(ModuleIdentity moduleId)
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

    synchronized ModuleIdentity[] requiredModuleIds()
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
    public ModuleStore lookupRequiredModule(int moduleAddress)
    {
        return myRequiredModuleStores.get(moduleAddress);
    }


    //========================================================================
    // Documentation


    void setDoc(String name, BindingDoc.Kind kind, String doc)
    {
        BindingDoc bDoc = new BindingDoc(name, kind,
                                         null, // usage
                                         doc);
        setDoc(name, bDoc);
    }

    void setDoc(String name, BindingDoc doc)
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

    BindingDoc document(int address)
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
    BindingDoc[] extractBindingDocs()
    {
        if (myBindingDocs == null) return BindingDoc.EMPTY_ARRAY;

        BindingDoc[] docs = new BindingDoc[myBindingDocs.size()];
        myBindingDocs.toArray(docs);
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


    protected static class CompiledTopDefine
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
        public Object doEval(Evaluator eval, Store store)
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


    protected static final class CompiledTopDefineSyntax
        extends CompiledTopDefine
    {
        CompiledTopDefineSyntax(String name, int address,
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
                value = new MacroTransformer(xformProc);
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
