// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import java.io.IOException;


abstract class NamedValue
    extends BaseValue
{
    // TODO Should use BaseSymbol
    private String myName;


    final String getInferredName()
    {
        return myName;
    }

    final void inferName(String name)
    {
        if (myName == null)
        {
            myName = name;
            nameInferred(name);
        }
    }

    /**
     * Hook for subclass to be notified when a name has been inferred for this
     * value.
     */
    void nameInferred(String name)
    {
    }


    /**
     * Identifies this value, usually by name and type.
     * For example, a procedure with inferred name "foo" would give the result
     * {@code "procedure foo"}.
     *
     * @throws IOException
     */
    abstract void identify(Appendable out)
        throws IOException;

    /**
     * Returns the output of {@link #identify(Appendable)} as a {@link String}.
     *
     * @return not null.
     */
    String identify()
    {
        StringBuilder out = new StringBuilder();
        try
        {
            identify(out);
        }
        catch (IOException e) {}
        return out.toString();
    }


    @Override
    public final void write(Evaluator eval, Appendable out)
        throws IOException
    {
        out.append("{{{");
        identify(out);
        out.append("}}}");
    }


    //========================================================================
    // Error helpers

    /**
     * Returns a new {@link ContractException} with the given message and
     * the identification of this value. This is preferable to creating
     * the exception directly, since this method can annotate it with location
     * information.
     * <p>
     * Expected usage:
     * <pre>
     * if (somethingBadHappened)
     * {
     *   throw contractFailure("somebody screwed up");
     * }
     * </pre>
     *
     * @param message the message to render in the exception.
     * Must not be null.
     *
     * @return a new exception
     */
    ContractException contractFailure(String message)
    {
        return new ContractException(identify() + ": " + message);
    }

    /**
     * Returns a new {@link ContractException} with the given message and
     * cause, and with
     * the identification of this value. This is preferable to creating
     * the exception directly, since this method can annotate it with location
     * information.
     * <p>
     * Expected usage:
     * <pre>
     * catch (SomeException e)
     * {
     *   throw contractFailure("somebody screwed up", e);
     * }
     * </pre>
     *
     * @param message the message to render in the exception.
     * Must not be null.
     * @param cause may be null.
     *
     * @return a new exception
     */
    ContractException contractFailure(String message, Throwable cause)
    {
        return new ContractException(identify() + ": " + message, cause);
    }
}
