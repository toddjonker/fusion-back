// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.BindingDoc.COLLECT_DOCS_MARK;
import com.amazon.fusion.BindingDoc.Kind;
import com.amazon.fusion.Namespace.NsBinding;

final class DefineSyntaxForm
    extends SyntacticForm
{
    DefineSyntaxForm()
    {
        //    "                                                                               |
        super("id xform",
              "Binds the identifier ID to a syntax transformer XFORM. The transformer must be\n" +
              "a procedure that accepts an syntax object and returns a syntax object.");
    }


    @Override
    SyntaxValue expand(Expander expander, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        final Evaluator eval = expander.getEvaluator();

        SyntaxChecker check = check(eval, stx);
        if (! (expander.isTopLevelContext() || expander.isModuleContext()))
        {
            throw check.failure("Definition must be at top-level or module level");
        }

        int arity = check.arityAtLeast(3);

        SyntaxValue[] children = stx.extract(eval);

        SyntaxSymbol identifier = check.requiredIdentifier(1);

        // WARNING!  This isn't conditional as with 'define' since
        // 'define_syntax' doesn't get predefined by the 'module' expander.
        if (false && expander.isModuleContext())
        {
            Namespace ns = env.namespace();
            assert ns == env;
            children[1] = ns.predefine(identifier, stx);
        }

        int bodyPos;
        SyntaxValue maybeDoc = children[2];
        if (maybeDoc.getType() == SyntaxValue.Type.STRING && arity > 3)
        {
            bodyPos = 3;
        }
        else
        {
            bodyPos = 2;
        }

        if (bodyPos != arity-1)
        {
            throw check.failure("Too many subforms");
        }

        SyntaxValue valueStx = stx.get(eval, bodyPos);
        children[bodyPos] = expander.expandExpression(env, valueStx);

        stx = SyntaxSexp.make(expander, stx.getLocation(), children);
        return stx;
    }


    //========================================================================


    @Override
    CompiledForm compile(Evaluator eval, Environment env, SyntaxSexp stx)
        throws FusionException
    {
        // TODO the second time around (in a Module) this env has been changed
        //   to contain the bound syntax identifier!  Which means this second
        //   compilation could turn out differently.
        int arity = stx.size();
        SyntaxValue valueSource = stx.get(eval, arity-1);
        CompiledForm valueForm = eval.compile(env, valueSource);

        SyntaxSymbol identifier = (SyntaxSymbol) stx.get(eval, 1);
        Binding binding = identifier.resolve();
        CompiledForm compiled =
            binding.compileDefineSyntax(eval, env, identifier, valueForm);

        if (arity != 3
            && binding instanceof NsBinding  // FIXME make sure this works
            && eval.firstContinuationMark(COLLECT_DOCS_MARK) != null)
        {
            // We have documentation. Sort of.
            SyntaxString docString = (SyntaxString) stx.get(eval, 2);
            BindingDoc doc = new BindingDoc(identifier.stringValue(),
                                            Kind.SYNTAX,
                                            null, // usage
                                            docString.stringValue());
            int address = ((NsBinding) binding).myAddress;
            env.namespace().setDoc(address, doc);
        }

        return compiled;
    }
}
