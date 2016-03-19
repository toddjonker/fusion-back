// Copyright (c) 2012-2016 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.FusionVoid.voidValue;
import static com.amazon.ion.util.IonTextUtils.printQuotedSymbol;
import com.amazon.fusion.FusionSymbol.BaseSymbol;
import com.amazon.ion.util.IonTextUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

final class LocalEnvironment
    implements Environment
{
    /**
     * Denotes a binding at some local scope, for example {@code lambda} and
     * {@code letrec} expressions.
     */
    static final class LocalBinding
        extends Binding
    {
        static final LocalBinding[] EMPTY_ARRAY = new LocalBinding[0];

        private final SyntaxSymbol myIdentifier;
        private final int          myDepth;
        private final int          myAddress;

        private LocalBinding(SyntaxSymbol identifier, int depth, int address)
        {
            assert depth > 0;
            myIdentifier = identifier;
            myDepth      = depth;
            myAddress    = address;
        }

        @Override
        public BaseSymbol getName()
        {
            return myIdentifier.getName();
        }

        @Override
        public boolean sameTarget(Binding other)
        {
            // Don't need to call other.originalBinding() since local bindings
            // are never renamed or wrapped (in the sense, for examples, that a
            // LanguageBinding wraps a ModuleBinding).
            return this == other;
        }


        @Override
        public Object lookup(Namespace ns)
        {
            return null;  // We're local, not a namespace binding.
        }


        @Override
        public CompiledForm compileReference(Evaluator eval, Environment env)
            throws FusionException
        {
            int rib = env.getDepth() - myDepth;
            if (rib == 0)
            {
                return new CompiledImmediateVariableReference(myAddress);
            }
            return new CompiledLocalVariableReference(rib, myAddress);
        }

        @Override
        public CompiledForm compileTopReference(Evaluator eval,
                                                Environment env,
                                                SyntaxSymbol id)
            throws FusionException
        {
            String message =
                "#%top not implemented for local binding: " + this;
            throw new SyntaxException("#%top", message, id);
        }

        @Override
        public CompiledForm compileSet(Evaluator eval, Environment env,
                                       CompiledForm valueForm)
            throws FusionException
        {
            int rib = env.getDepth() - myDepth;
            if (rib == 0)
            {
                return new CompiledImmediateVariableSet(myAddress, valueForm);
            }
            return new CompiledLocalVariableSet(rib, myAddress, valueForm);
        }


        @Override
        public boolean equals(Object other)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString()
        {
            return "{{{LocalBinding " + myIdentifier + "}}}";
        }


        /** Extract the names from an array of bindings. */
        static String[] toNames(LocalBinding[] bindings)
        {
            if (bindings == null || bindings.length == 0)
            {
                return FusionUtils.EMPTY_STRING_ARRAY;
            }
            else
            {
                String[] names = new String[bindings.length];
                for (int i = 0; i < bindings.length; i++)
                {
                    names[i] = bindings[i].myIdentifier.stringValue();
                }
                return names;
            }
        }

        @Override
        void dump(PrintStream out) throws IOException
        {
            out.print("LocalBinding::");
            IonTextUtils.printSymbol(out, myIdentifier.debugString());
            out.print("::{identifier:");
            myIdentifier.dump(out);
            out.print(",depth:" + myDepth);
            out.print(",address:" + myAddress);
            out.print("}");
        }
    }


    /** Not null */
    private final Environment    myEnclosure;
    private final Namespace      myNamespace;
    private final int            myDepth;
    private final LocalBinding[] myBindings;


    /**
     * Expand-time environment construction.
     *
     * @throws FusionException if there's a duplicate identifier.
     */
    LocalEnvironment(Environment enclosure,
                     SyntaxSymbol[] identifiers,
                     SyntaxValue formForErrors)
        throws FusionException
    {
        myEnclosure = enclosure;
        myNamespace = enclosure.namespace();
        myDepth = 1 + enclosure.getDepth();

        int count = identifiers.length;
        if (count > 1)
        {
            for (int i = 0; i < count; i++)
            {
                SyntaxSymbol idI = identifiers[i];

                for (int j = i + 1; j < count; j++)
                {
                    SyntaxSymbol idJ = identifiers[j];

                    if (idI.freeIdentifierEqual(idJ))
                    {
                        String message =
                            "duplicate binding: " +
                            printQuotedSymbol(idJ.stringValue());

                        SyntaxException ex =
                            new SyntaxException(null, message, idJ);
                        ex.addContext(formForErrors);
                        throw ex;
                    }
                }
            }
        }

        myBindings = new LocalBinding[count];
        for (int i = 0; i < count; i++)
        {
            SyntaxSymbol identifier = identifiers[i];

            // This helps make sure we're not preparing the same code twice.
            assert identifier.getBinding() == null
                : "Identifier " + identifier + " already bound to " +
                  identifier.getBinding();

            myBindings[i] = new LocalBinding(identifier, myDepth, i);
        }

    }


    /**
     * Compile-time environment construction; makes a dummy environment to
     * keep track of the current depth.
     */
    LocalEnvironment(Environment enclosure)
    {
        myEnclosure = enclosure;
        myNamespace = enclosure.namespace();
        myDepth = 1 + enclosure.getDepth();

        myBindings = null;
    }


    @Override
    public Namespace namespace()
    {
        return myNamespace;
    }

    @Override
    public int getDepth()
    {
        return myDepth;
    }


    @Override
    public Binding substitute(Binding binding, Set<MarkWrap> marks)
    {
        for (LocalBinding b : myBindings)
        {
            if (b.myIdentifier.resolvesBound(binding, marks))
            {
                return b;
            }
        }
        return binding;
    }

    @Override
    public Binding substituteFree(BaseSymbol name, Set<MarkWrap> marks)
    {
        for (LocalBinding b : myBindings)
        {
            if (b.myIdentifier.resolvesFree(name, marks))
            {
                return b;
            }
        }
        return null;
    }


    @Override
    public String toString()
    {
        StringBuilder buf = new StringBuilder("{{{LocalEnv (");
        boolean first = true;
        for (LocalBinding b : myBindings)
        {
            if (! first) buf.append(' ');
            buf.append(b.myIdentifier);
            first = false;
        }
        buf.append(")}}}");
        return buf.toString();
    }


    @Override
    public void dump(PrintStream out) throws IOException
    {
        out.print("LocalEnvironment::{id:");
        out.print(System.identityHashCode(this));
        out.print(",depth:" + myDepth);
        out.print(",myBindings:[");
        for (LocalBinding b : myBindings)
        {
            b.dump(out);
            out.print(",");
        }
        out.print("]}");
    }


    //========================================================================


    /** Reference to a var in the immediately-enclosing environment. */
    private static final class CompiledImmediateVariableReference
        implements CompiledForm
    {
        private final int myAddress;

        CompiledImmediateVariableReference(int address)
        {
            myAddress = address;
        }

        @Override
        public Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            Object result = store.lookup(myAddress);
            assert result != null
                : "No value for immediate var @" + myAddress;
            return result;
        }
    }


    private static final class CompiledLocalVariableReference
        implements CompiledForm
    {
        private final int myRib;
        private final int myAddress;

        CompiledLocalVariableReference(int rib, int address)
        {
            assert rib > 0;
            myRib     = rib;
            myAddress = address;
        }

        @Override
        public Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            Object result = store.lookup(myRib, myAddress);
            assert result != null
                : "No value for rib " + myRib + " address " + myAddress;
            return result;
        }
    }


    private static final class CompiledImmediateVariableSet
        implements CompiledForm
    {
        private final int          myAddress;
        private final CompiledForm myValueForm;

        CompiledImmediateVariableSet(int address, CompiledForm valueForm)
        {
            myAddress   = address;
            myValueForm = valueForm;
        }

        @Override
        public Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            Object value = eval.eval(store, myValueForm);
            store.set(myAddress, value);
            return voidValue(eval);
        }
    }


    private static final class CompiledLocalVariableSet
        implements CompiledForm
    {
        private final int          myRib;
        private final int          myAddress;
        private final CompiledForm myValueForm;

        CompiledLocalVariableSet(int rib, int address, CompiledForm valueForm)
        {
            assert rib > 0;
            myRib       = rib;
            myAddress   = address;
            myValueForm = valueForm;
        }

        @Override
        public Object doEval(Evaluator eval, Store store)
            throws FusionException
        {
            Object value = eval.eval(store, myValueForm);
            store.set(myRib, myAddress, value);
            return voidValue(eval);
        }
    }
}
