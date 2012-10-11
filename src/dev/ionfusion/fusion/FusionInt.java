// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;


/**
 *
 */
public final class FusionInt
{
    private FusionInt() {}


    static Object makeInt(EvalContext eval, long value)
    {
        return ((Evaluator)eval).newInt(value);
    }
}
