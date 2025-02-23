// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;

import static dev.ionfusion.fusion.FusionString.makeString;
import static dev.ionfusion.fusion.FusionVoid.voidValue;

import dev.ionfusion.embed.ResourceName;


class SyntaxSourceProc
    extends Procedure1
{
    @Override
    Object doApply(Evaluator eval, Object arg)
        throws FusionException
    {
        SyntaxValue stx = checkSyntaxArg(0, arg);
        SourceLocation location = stx.getLocation();
        if (location != null)
        {
            ResourceName name = location.getSourceName();
            if (name != null)
            {
                return makeString(eval, name.display());
            }
        }
        return voidValue(eval);
    }
}
