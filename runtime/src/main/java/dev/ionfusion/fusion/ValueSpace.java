// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;

/**
 * Provides the implementation of Fusion values for a single {@link FusionRuntime}.
 */
interface ValueSpace
{
    /**
     * Intern a value with the general intern table.
     * <p>
     * Membership is based on {@link Object#hashCode()} and
     * {@link Object#equals(Object)}.
     *
     * @param value must not be null.
     * @return an interned copy of the value.
     *
     * @param <T> the type of value.
     */
    <T> T intern(T value);
}
