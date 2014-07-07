// Copyright (c) 2014 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion;

import static com.amazon.fusion.SyntaxValue.ourAddMarkCounter;
import static com.amazon.fusion.SyntaxWraps.ourAddWrapsCounter;
import static com.amazon.fusion.SyntaxWraps.ourAddWrapsDupeCounter;
import static com.amazon.fusion.SyntaxWraps.ourAddWrapsPrefixLenCounter;
import static java.lang.System.out;
import java.util.Date;

/**
 *
 */
public class MacroMetrics
{
    public static void main(String[] args)
        throws Exception
    {
        out.println(new Date());

        FusionRuntimeBuilder b = FusionRuntimeBuilder.standard();

        b = b.withConfigProperties(MacroMetrics.class, "/fusion.properties");

        FusionRuntime runtime = b.build();

        out.println("addOrRemoveMark calls: " + ourAddMarkCounter.get());
        out.println("addWrap calls: " + SyntaxWraps.ourAddWrapCounter.get());
        out.println("dupes: " + SyntaxWraps.ourAddWrapDupeCounter.get());
        out.println("addWraps calls: " + ourAddWrapsCounter.get());
        out.println("dupes: " + ourAddWrapsDupeCounter.get());

        out.println("avg prefix: " +
                    ourAddWrapsPrefixLenCounter.get() / ourAddWrapsCounter.get());

    }
}
