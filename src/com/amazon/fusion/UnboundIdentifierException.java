// Copyright (c) 2012-2013 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import com.amazon.fusion.FusionSymbol.BaseSymbol;
import java.io.IOException;

/**
 * Indicates a reference to an unbound identifier.
 */
@SuppressWarnings("serial")
public final class UnboundIdentifierException
    extends SyntaxException
{
    private final String myText;


    /**
     * @param identifier must not be null.
     */
    UnboundIdentifierException(SyntaxSymbol identifier)
    {
        super(null, "unbound identifier", identifier);
        myText = identifier.stringValue();

        try
        {
            System.out.println("\n--------");
            identifier.dump(System.out);
            System.out.println("\n--------");
            BaseSymbol.dumpInternMap(System.out);
            System.out.println("\n--------\n");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    /**
     * Gets the text of the unbound identifier.
     */
    public String getIdentifierString()
    {
        return myText;
    }
}
