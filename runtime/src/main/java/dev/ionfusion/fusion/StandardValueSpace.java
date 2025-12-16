// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;

import dev.ionfusion.fusion.FusionSymbol.BaseSymbol;
import dev.ionfusion.fusion._private.InternMap;


/**
 * The central factory for producing Fusion values.  Long term, all value
 * creation should flow through here, so implementations can be swapped,
 * instrumented, etc. without requiring broad changes.
 * <p>
 * Ideally, the implementations of various Fusion value types would not be
 * coupled to a specific ValueSpace implementation. However, most types are tied
 * to {@link BaseSymbol} for annotations, so things are tightly coupled.
 */
class StandardValueSpace
    implements ValueSpace
{
    /**
     * General interning table for non-symbol values.
     * Symbol interning is high traffic, so we separate it to reduce contention.
     */
    private static final
    InternMap<Object, Object> ourInternedValues = new InternMap<>(o -> o, 256);

    // TODO PERF: Perhaps add a method to sweep the intern tables.
    // Because entries are only purged on access, we could end up with
    // a bunch of garbage in there after code compilation is done, and unless
    // new symbols are instantiated there won't be any access to the map and no
    // garbage released. Perhaps it's worth expunging the map (via size())
    // after significant processing events like compiling code.


    @SuppressWarnings("unchecked")
    public <T> T intern(T value)
    {
        return (T) ourInternedValues.intern(value);
    }
}
