// Copyright (c) 2018-2021 Amazon.com, Inc.  All rights reserved.

package com.amazon.fusion.util.hamt;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * Implementation of the Hash Array Mapped Trie data structure, as described in the paper
 * <em>Ideal Hash Tries</em> by Phil Bagwell.
 * <p>
 * This class is an internal implementation detail of FusionJava, is not intended
 * for reuse, and does not support Java nulls as keys or values.
 * </p>
 * <p>
 * Iteration order of this data structure is undefined. All key hashes used within
 * {@link HashArrayMappedTrie} are shuffled differently in each JRE. All hashes used for
 * navigating through the node structure should be routed through {@link #hashCodeFor(Object)}.
 * <p>
 * This implementation was originally based on Clojure's PersistentHashMap and so it:
 * <ul>
 *     <li>Uses path copying for persistence.</li>
 *     <li>Uses {@link CollisionNode}s rather than extended hashing.</li>
 *     <li>Uses node polymorphism rather than conditional checks.</li>
 * </ul>
 * </p>
 *
 * @see <a href="https://infoscience.epfl.ch/record/64398/files/idealhashtrees.pdf">Ideal Hash Trees</a>
 */
@SuppressWarnings({"unchecked"})
public final class HashArrayMappedTrie
{
    // This class isn't instantiable.
    private HashArrayMappedTrie() {}

    private static final int STOP_RELYING_ON_UNDEFINED_BEHAVIOR =
        new Random().nextInt();

    /**
     * Intercepts and records changes made by one or more trie operations.
     * Instances can be reused across multiple calls into the same or different
     * tries, reducing the overall number of object allocations.
     */
    public static class Changes
    {
        private int keysAdded     = 0;
        private int keysReplaced  = 0;
        private int keysRemoved   = 0;

        public Changes()
        {
        }

        /**
         * Returns the number of individual changes made using this instance.
         * Incremented when a key/value is added or removed, or a key's value is changed.
         * Due to mutation operations, this can be non-zero even when the
         * operation returns the same root node.
         */
        public int changeCount()
        {
            return keysAdded + keysReplaced + keysRemoved;
        }

        /**
         * Indicates how the operation changed the total number of keys in the trie.
         * This may be different than the number of keys changed: if one key was added and one
         * removed, then two keys were changed but the delta would be zero.
         */
        public int keyCountDelta()
        {
            return keysAdded - keysRemoved;
        }

        /**
         * Intercepts insertions, allowing a given value to be stored in a
         * different form.
         *
         * @param givenValue the value that was requested to be inserted.
         *
         * @return the value to be stored.
         */
        protected Object inserting(Object givenValue)
        {
            return givenValue;
        }

        private Object keyAdded(Object givenValue)
        {
            keysAdded++;
            return inserting(givenValue);
        }

        /**
         * Intercepts the removal of a key-value pair.
         *
         * @param storedValue the value being removed from the trie.
         */

        protected void removing(Object storedValue)
        {
        }

        private void keyRemoved(Object storedValue)
        {
            removing(storedValue);
            keysRemoved++;
        }

        /**
         * Intercepts the replacement of a value for the same key.
         *
         * @param storedValue the value currently present in the trie.
         * @param givenValue the value that the operation is inserting.
         *
         * @return the value to be stored.
         */
        protected Object replacing(Object storedValue, Object givenValue)
        {
            return givenValue;
        }

        private void keyReplaced()
        {
            keysReplaced++;
        }
    }


    /**
     * Returns an empty, immutable trie.
     */
    public static <K, V> TrieNode<K, V> empty()
    {
        return (TrieNode<K, V>) EmptyNode.SINGLETON;
    }


    public static <K, V> TrieNode<K, V> fromMap(Map<K, V> map, Changes changes)
    {
        return fromEntries(map.entrySet().iterator(), changes);
    }

    public static <K, V> TrieNode<K, V> fromEntries(Iterator<Entry<K, V>> entries,
                                                    Changes changes)
    {
        TrieNode<K, V> trie = empty();
        return trie.mWith(entries, changes);
    }

    public static <K, V> TrieNode<K, V> fromArrays(K[] keys,
                                                   V[] values,
                                                   Changes changes)
    {
        TrieNode<K, V> trie = empty();
        return trie.mWithEntries(keys, values, changes);
    }

    /**
     * Creates a trie by copying entries for the {@code keys} from the
     * {@code origin} trie.
     */
    public static <K, V> TrieNode<K, V> fromSelectedKeys(TrieNode<K, V> origin,
                                                         K[] keys,
                                                         Changes changes)
    {
        TrieNode<K, V> trie = empty();
        return trie.mWithSelectedKeys(origin, keys, changes);
    }


    /**
     * Used to provide the hash code for a key in the {@link HashArrayMappedTrie}.
     * ALL trie node interfacing calls should use this method!!
     *
     * Visible for testing.
     * @param key Object to get the hash code for.
     * @return a consistently shuffled hash code.
     */
    static int hashCodeFor(Object key)
    {
        return key.hashCode() ^ STOP_RELYING_ON_UNDEFINED_BEHAVIOR;
    }

    // TODO: Add method that accepts a function to modify each element in the trie.


    /**
     * Abstract class interface for Nodes.
     * TODO: Convert to interface with default methods in Java 8+
     */
    public static abstract class TrieNode<K, V>
    {
        private TrieNode() {}

        /**
         * Retrieves the value for a key in this trie.
         */
        public V get(K key)
        {
            return get(hashCodeFor(key), 0, key);
        }

        /**
         * Functionally inserts or updates a key in this trie, returning the resulting root node.
         * <p>
         * The returned root can be different even if nothing was changed; this allows
         * the implementation to amortize optimizations to the trie structure.
         * </p>
         */
        public TrieNode<K, V> with(K key, V value, Changes changes)
        {
            return with(hashCodeFor(key), 0, key, value, changes);
        }

        public TrieNode<K, V> with(Entry<K, V> entry, Changes changes)
        {
            return with(entry.getKey(), entry.getValue(), changes);
        }

        public TrieNode<K, V> with(Iterator<Entry<K, V>> entries,
                                   Changes changes)
        {
            TrieNode<K, V> root = this;
            while (entries.hasNext())
            {
                root = root.with(entries.next(), changes);
            }
            return root;

        }

        /**
         * Imperatively inserts or updates a key in this trie, returning the resulting root node.
         * This means that the operation may mutate this data structure, and therefore the root
         * must not be shared across containers, lest there be undesirable aliasing effects.
         * <p>
         * The returned root can be different even if nothing was changed; this allows
         * the implementation to amortize optimizations to the trie structure.
         * </p>
         */
        public TrieNode<K, V> mWith(K key, V value, Changes changes)
        {
            return mWith(hashCodeFor(key), 0, key, value, changes);
        }

        public TrieNode<K, V> mWith(Entry<K, V> entry, Changes changes)
        {
            return mWith(entry.getKey(), entry.getValue(), changes);
        }

        public TrieNode<K, V> mWith(Iterator<Entry<K, V>> entries, Changes changes)
        {
            TrieNode<K, V> root = this;
            while (entries.hasNext())
            {
                root = root.mWith(entries.next(), changes);
            }
            return root;
        }

        public TrieNode<K, V> mWithEntries(K[] keys, V[] values, Changes changes)
        {
            if (keys.length != values.length)
            {
                throw new IllegalArgumentException("keys and values have unequal length");
            }

            TrieNode<K, V> root = this;
            for (int i = 0; i < keys.length; i++)
            {
                root = root.mWith(keys[i], values[i], changes);
            }
            return root;
        }

        /**
         * Add to this trie the values of another trie at specific keys.
         */
        public TrieNode<K, V> mWithSelectedKeys(TrieNode<K, V> origin,
                                                K[] keys,
                                                Changes changes)
        {
            TrieNode<K, V> root = this;
            for (K key : keys)
            {
                V value = origin.get(key);
                if (value != null)
                {
                    root = root.mWith(key, value, changes);
                }
            }
            return root;
        }


        // TODO: Add a variation of with[out] that returns the previous value (if any).

        /**
         * Functionally removes a key from this trie, returning the resulting root node.
         * <p>
         * The returned root can be different even if nothing was removed; this allows
         * the implementation to amortize optimizations to the trie structure.
         * </p>
         */
        public TrieNode<K, V> without(K key, Changes changes)
        {
            return without(hashCodeFor(key), 0, key, changes);
        }


        /**
         * Functionally removes multiple keys from this trie.
         *
         * @param keys must not be null.
         *
         * @return the resulting root node.
         */
        public TrieNode<K, V> withoutKeys(K[] keys, Changes changes)
        {
            TrieNode<K, V> root = this;
            for (K key : keys)
            {
                root = root.without(key, changes);
            }
            return root;
        }


        /**
         * Computes the number of unique keys in this node and its children.
         * This is an O(n) operation.
         */
        public abstract int countKeys();

        /**
         * Functionally modifies the trie to have the desired mapping from key to value.
         * @return Itself if it was not modified, else a new trie with the modification.
         */
        abstract TrieNode<K, V> with(int hash,
                                     int shift,
                                     K key,
                                     V value,
                                     Changes changes);

        /**
         * Mutates the trie to have the desired mapping from key to value.
         * This must only be called while constructing a new Trie, when its known that
         * nodes are not shared.
         * @return Itself
         */
        abstract TrieNode<K, V> mWith(int hash,
                                      int shift,
                                      K key,
                                      V value,
                                      Changes changes);

        /**
         * @return The value with the given key, or null if it does not exist in the trie.
         */
        abstract V get(int hash, int shift, K key);

        /**
         * Functionally removes the mapping associated with the key from the trie.
         * @return Itself if it was not modified, else a new trie with the modification.
         */
        abstract TrieNode<K, V> without(int hash, int shift, K key, Changes changes);

        /**
         * @return An iterator over all elements within the trie.
         */
        public abstract Iterator<Entry<K, V>> iterator();

        static int hashFragment(int hash, int shift)
        {
            assert shift <= 30;
            return hash >>> shift & 0x1f;
        }

        static int bitPosition(int hashFragment)
        {
            return 1 << hashFragment;
        }
    }


    private static final class EmptyNode<K, V>
        extends TrieNode<K, V>
    {
        private static final EmptyNode<Object, Object> SINGLETON = new EmptyNode<>();

        private EmptyNode() {}

        @Override
        public int countKeys()
        {
            return 0;
        }

        @Override
        public V get(K key)
        {
            return null;
        }

        @Override
        V get(int hash, int shift, K key)
        {
            return null;
        }

        @Override
        TrieNode<K, V> with(int hash, int shift, K key, V value, Changes changes)
        {
            Object newValue = changes.keyAdded(value);
            return new FlatNode<>(key, newValue);
        }

        @Override
        TrieNode<K, V> mWith(int hash, int shift, K key, V value, Changes changes)
        {
            Object newValue = changes.keyAdded(value);
            return new FlatNode<>(key, newValue);
        }

        @Override
        TrieNode<K, V> without(int hash, int shift, K key, Changes changes)
        {
            return this;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return Collections.emptyIterator();
        }
    }


    /**
     * A trie node storing a small number of unsorted key/value pairs, and no child nodes except
     * to hold colliding keys.
     * This is more efficient than a {@link BitMappedNode} for holding leaves:
     * empirically, a linear search is faster for nodes with <= {@value #MAX_CHILDREN} keys.
     * <p>
     * Within the {@link #kvPairs} array, keys are stored in even indices, values at odd.
     * Where there is a null key, the following value is a {@link CollisionNode}.
     * </p>
     * <p>
     * Nodes of this type are created by inserting into an {@link EmptyNode}, or by inserting a
     * non-colliding key into a {@code CollisionNode}.
     * They may grow to hold up to {@value #MAX_CHILDREN} entries before {@link #expand}ing
     * into a {@link BitMappedNode}.
     * </p>
     */
    static class FlatNode<K, V>
        extends TrieNode<K, V>
    {
        /**
         * Empirically, a linear search performs as fast or faster than a bitmapped
         * search, up to this many elements.
         */
        private static final int MAX_CHILDREN = 8;

        Object[] kvPairs;


        FlatNode()
        {
            kvPairs = new Object[0];
        }

        FlatNode(K key, Object value)
        {
            this(new Object[]{key, value});
        }


        FlatNode(K key1, V val1, K key2, V val2)
        {
            this(new Object[]{key1, val1, key2, val2});
        }


        FlatNode(Object[] kvPairs)
        {
            this.kvPairs = kvPairs;
        }


        @Override
        public int countKeys()
        {
            int count = 0;
            for (int i = 0; i < kvPairs.length; i += 2)
            {
                if (kvPairs[i] == null)
                {
                    count += ((CollisionNode<K, V>) kvPairs[i + 1]).countKeys();
                }
                else
                {
                    count++;
                }
            }
            return count;
        }

        @Override
        TrieNode<K, V> with(int hash,
                            int shift,
                            K key,
                            V value,
                            Changes changes)
        {
            int index = linearSearch(hash, key);
            if (index != -1)
            {
                Object keyOrNull = kvPairs[index];
                Object valOrNode = kvPairs[index + 1];
                if (keyOrNull == null)
                {
                    CollisionNode<K, V> node = (CollisionNode<K, V>) valOrNode;
                    TrieNode<K, V> newNode = node.with(hash, shift, key, value, changes);
                    if (newNode == node)
                    {
                        return this;
                    }
                    else
                    {
                        // Child node has updated the Changes.
                        return new FlatNode<>(cloneAndModify(kvPairs, index + 1, newNode));
                    }
                }
                else
                {
                    Object storedValue = valOrNode;
                    Object newValue = changes.replacing(storedValue, value);
                    if (storedValue == newValue)
                    {
                        return this;
                    }
                    else
                    {
                        changes.keyReplaced();
                        return new FlatNode<>(cloneAndModify(kvPairs, index + 1, newValue));
                    }
                }
            }
            else
            {
                if (kvPairs.length >= 2 * MAX_CHILDREN)
                {
                    return expand(shift).mWith(hash, shift, key, value, changes);
                }
                else
                {
                    Object newValue = changes.keyAdded(value);
                    Object[] newPairs = cloneAndExtend(kvPairs, key, newValue);
                    return new FlatNode<>(newPairs);
                }
            }
        }


        @Override
        TrieNode<K, V> mWith(int hash,
                             int shift,
                             K key,
                             V value,
                             Changes changes)
        {
            int index = linearSearch(hash, key);
            if (index != -1)
            {
                Object keyOrNull = kvPairs[index];
                Object valOrNode = kvPairs[index + 1];
                if (keyOrNull == null)
                {
                    CollisionNode<K, V> node = (CollisionNode<K, V>) valOrNode;
                    TrieNode<K, V> newNode = node.mWith(hash, shift, key, value, changes);
                    if (newNode != node)
                    {
                        kvPairs[index + 1] = newNode;
                    }
                    // Child node has updated the Changes.
                }
                else
                {
                    Object storedValue = valOrNode;
                    Object newValue = changes.replacing(storedValue, value);
                    if (storedValue != newValue)
                    {
                        changes.keyReplaced();
                        kvPairs[index + 1] = newValue;
                    }
                }
            }
            else
            {
                if (kvPairs.length >= 2 * MAX_CHILDREN)
                {
                    return expand(shift).mWith(hash, shift, key, value, changes);
                }
                else
                {
                    Object newValue = changes.keyAdded(value);
                    kvPairs = cloneAndExtend(kvPairs, key, newValue);
                }
            }

            return this;
        }


        @Override
        V get(int hash, int shift, K key)
        {
            int index = linearSearch(hash, key);
            if (index != -1)
            {
                Object keyOrNull = kvPairs[index];
                Object valOrNode = kvPairs[index + 1];
                if (keyOrNull == null)
                {
                    return ((CollisionNode<K, V>) valOrNode).get(hash, shift, key);
                }
                else
                {
                    return (V) valOrNode;
                }
            }

            return null;
        }


        @Override
        TrieNode<K, V> without(int hash,
                               int shift,
                               K key,
                               Changes changes)
        {
            int index = linearSearch(hash, key);
            if (index != -1)
            {
                Object keyOrNull = kvPairs[index];
                if (keyOrNull == null)
                {
                    CollisionNode<K, V> node = (CollisionNode<K, V>) kvPairs[index + 1];
                    TrieNode<K, V> newNode = node.without(hash, shift, key, changes);
                    if (newNode == EmptyNode.SINGLETON)
                    {
                        return withoutPair(index);
                    }
                    else
                    {
                        return new FlatNode<>(cloneAndModify(kvPairs, index + 1,
                                                             newNode));
                    }
                }
                else
                {
                    changes.keyRemoved(kvPairs[index + 1]);
                    return withoutPair(index);
                }
            }
            return this;
        }


        TrieNode<K, V> withoutPair(int index)
        {
            if (kvPairs.length == 2)
            {
                return empty();
            }
            else
            {
                return makeSimilar(cloneAndRemovePair(kvPairs, index));
            }
        }


        TrieNode<K, V> makeSimilar(Object[] kvPairs)
        {
            return new FlatNode<>(kvPairs);
        }


        @Override
        public Iterator<Entry<K, V>> iterator()
        {
            return new EntryArrayIterator<>(kvPairs);
        }


        int linearSearch(int hash, K key)
        {
            for (int i = 0; i < kvPairs.length; i += 2)
            {
                Object keyOrNull = kvPairs[i];
                if (keyOrNull == null)
                {
                    CollisionNode<K, V> node = (CollisionNode<K, V>) kvPairs[i + 1];
                    if (node.hash == hash)
                    {
                        return i;
                    }
                }
                else if (equivKeys(keyOrNull, key))
                {
                    return i;
                }
            }
            return -1;
        }


        private TrieNode<K, V> expand(int shift)
        {
            // Don't count expansion as changing the results.
            Changes changes = new Changes();

            // Give the new BitMappedNode some buffer space for faster mWith calls.
            // Supposing perfect hash distribution within the FlatNode, the new BitMappedNode's
            // array would require four extra allocations to perform expansion.
            // In the degenerate case of extremely similar hashes, this is a slight waste of memory.
            TrieNode<K, V> newNode = new BitMappedNode<>(0, new Object[8]);
            for (int i = 0; i < kvPairs.length; i += 2)
            {
                Object keyOrNull = kvPairs[i];
                Object valOrNode = kvPairs[i + 1];
                if (keyOrNull == null)
                {
                    newNode = newNode.mWith(((CollisionNode<K, V>) valOrNode).hash,
                                            shift,
                                            null,
                                            (V) valOrNode, // inaccurate, but required cast
                                            changes);
                }
                else
                {
                    newNode = newNode.mWith(hashCodeFor(keyOrNull),
                                            shift,
                                            (K) keyOrNull,
                                            (V) valOrNode,
                                            changes);
                }
            }
            return newNode;
        }
    }


    /**
     * A trie node storing key/value pairs, where all keys have the same hashcode.
     * <p>
     * Like {@link FlatNode}, the entries are not sorted, so keys are found
     * via linear search and compared using {@link #equivKeys}.
     * Unlike {@code FlatNode}, this node never has child nodes since there are
     * no more hashcode bits to use.
     * </p>
     * <p>
     * Within the {@link #kvPairs} array, keys are stored in even indices, values at odd.
     * The array is always "full", with each pair holding key/value pairs.
     * Collisions are expected to be rare, so we optimize for space.
     * </p>
     * <p>
     * Nodes of this type are created when inserting a key into a {@link BitMappedNode} or
     * {@code FlatNode} that holds a different key (per {@link #equivKeys}) with the same hashcode.
     * They are replaced by a normal {@code FlatNode} when inserting a key with
     * a different hashcode, pushing the collisions down a level in the trie.
     * They are not eliminated when they shrink to one entry (and thus without a collision);
     * this is suboptimal and should be corrected.
     * </p>
     */
    static class CollisionNode<K, V>
        extends FlatNode<K, V>
    {
        private final int hash;

        CollisionNode(int hash, Object[] kvPairs)
        {
            super(kvPairs);
            this.hash = hash;
        }

        @Override
        public int countKeys()
        {
            return kvPairs.length / 2;
        }

        @Override
        TrieNode<K, V> with(int hash,
                            int shift,
                            K key,
                            V value,
                            Changes changes)
        {
            if (hash == this.hash)
            {
                int index = linearSearch(hash, key);
                if (index != -1)
                {
                    Object storedValue = kvPairs[index + 1];
                    Object newValue = changes.replacing(storedValue, value);
                    if (storedValue == newValue)
                    {
                        return this;
                    }
                    else
                    {
                        changes.keyReplaced();
                        return makeSimilar(cloneAndModify(kvPairs,
                                                          index + 1,
                                                          newValue));
                    }
                }
                else
                {
                    Object newValue = changes.keyAdded(value);
                    Object[] newPairs = cloneAndExtend(kvPairs, key, newValue);
                    return makeSimilar(newPairs);
                }
            }

            // If it doesn't share the hash, push this collision node down a level.
            Object newValue = changes.keyAdded(value);
            return new FlatNode<>(new Object[]{null, this, key, newValue});
        }


        @Override
        TrieNode<K, V> mWith(int hash,
                             int shift,
                             K key,
                             V value,
                             Changes changes)
        {
            if (hash == this.hash)
            {
                int index = linearSearch(hash, key);
                if (index != -1)
                {
                    Object storedValue = kvPairs[index + 1];
                    Object newValue = changes.replacing(storedValue, value);
                    if (storedValue != newValue)
                    {
                        changes.keyReplaced();
                        kvPairs[index + 1] = newValue;
                    }
                }
                else
                {
                    Object newValue = changes.keyAdded(value);
                    kvPairs = cloneAndExtend(kvPairs, key, newValue);
                }

                return this;
            }

            // If it doesn't share the hash, push this collision node down a level.
            Object newValue = changes.keyAdded(value);
            return new FlatNode<>(new Object[]{null, this, key, newValue});
        }


        @Override
        int linearSearch(int hash, Object key)
        {
            for (int i = 0; i < kvPairs.length; i += 2)
            {
                Object keyOrNull = kvPairs[i];
                if (equivKeys(keyOrNull, key))
                {
                    return i;
                }
            }
            return -1;
        }


        @Override
        TrieNode<K, V> makeSimilar(Object[] kvPairs)
        {
            return new CollisionNode<>(hash, kvPairs);
        }

        @Override
        public String toString()
        {
            return "{\n" +
                   "  Node Hash: " + hash + ",\n" +
                   "  Nodes: " + Arrays.toString(kvPairs) + ",\n" +
                   "}";

        }
    }


    /**
     * A trie node storing a mix of key/value pairs and child nodes.
     * <p>
     * Within the {@link #kvPairs} array, keys are stored in even indices, values at odd.
     * Where there is a null key, the following value is either a child {@link TrieNode}
     * or null. The latter occurs due to optimizations in {@link #mWith} and {@link FlatNode#expand}.
     * </p>
     * <p>
     * Nodes of this type are created when a {@link FlatNode} grows beyond its performant capacity
     * of {@value FlatNode#MAX_CHILDREN} entries.
     * They may grow to hold up to {@value #MAX_CHILDREN} entries before being replaced by a
     * {@link HashArrayMappedNode}.
     * They are never packed back down into {@code FlatNode}s because they may have child nodes
     * that are not {@link CollisionNode}s (which would break {@code FlatNode}'s ability to
     * linear search).
     * </p>
     */
    static class BitMappedNode<K, V>
        extends TrieNode<K, V>
    {
        /**
         * We convert {@link BitMappedNode}s to {@link HashArrayMappedNode}s when they exceed
         * this many elements. Empirically, this conversion provides a performance gain of up to
         * 20% on lookup and insert operations as the trie grows with randomly distributed keys.
         */
        private static final int MAX_CHILDREN = 16;

        int bitmap;
        Object[] kvPairs; // Can be either a Key Value Pair or a Null then Node Pair.


        BitMappedNode()
        {
            bitmap  = 0;
            kvPairs = new Object[0];
        }

        BitMappedNode(int bitmap, Object[] kvPairs)
        {
            this.bitmap = bitmap;
            this.kvPairs = kvPairs;
        }

        private BitMappedNode<K, V> replace(int index, Object child)
        {
            Object[] newKvPairs = cloneAndModify(kvPairs, index, child);
            return new BitMappedNode<>(bitmap, newKvPairs);
        }

        private BitMappedNode<K, V> replace(int index1, Object child1,
                                            int index2, Object child2)
        {
            Object[] newKvPairs = cloneAndModify(kvPairs, index1, child1, index2, child2);
            return new BitMappedNode<>(bitmap, newKvPairs);
        }

        @Override
        public int countKeys()
        {
            int count = 0;
            for (int i = 0; i < kvPairs.length; i += 2)
            {
                Object keyOrNull = kvPairs[i];
                if (keyOrNull == null)
                {
                    Object node = kvPairs[i + 1];
                    if (node != null)
                    {
                        count += ((TrieNode<K, V>) node).countKeys();
                    }
                }
                else
                {
                    count++;
                }
            }
            return count;
        }

        @Override
        TrieNode<K, V> with(int hash,
                            int shift,
                            K key,
                            V value,
                            Changes changes)
        {
            int hashFragment = hashFragment(hash, shift);
            int bit = bitPosition(hashFragment);
            int keyIndex = 2 * arrayIndex(bit);
            if (isInBitmap(bit))
            {
                Object keyOrNull = kvPairs[keyIndex];
                Object valOrNode = kvPairs[keyIndex + 1];
                assert !(valOrNode instanceof EmptyNode);

                if (keyOrNull == null)
                {
                    TrieNode<K, V> node = (TrieNode<K, V>) valOrNode;
                    TrieNode<K, V> newNode = node.with(hash, shift + 5, key, value, changes);
                    if (newNode == valOrNode)
                    {
                        return this;
                    }
                    else
                    {
                        // Child node has updated the Changes.
                        return replace(keyIndex + 1, newNode);
                    }
                }
                else if (equivKeys(keyOrNull, key))
                {
                    Object storedValue = valOrNode;
                    Object newValue = changes.replacing(storedValue, value);
                    if (storedValue == newValue)
                    {
                        return this;
                    }
                    else // Same key, different value
                    {
                        changes.keyReplaced();
                        return replace(keyIndex + 1, newValue);
                    }
                }
                else // Hash fragment collision
                {
                    Object newValue = changes.keyAdded(value);
                    FlatNode<K, V> newNode = resolveCollision(hashCodeFor(keyOrNull),
                                                              (K) keyOrNull,
                                                              (V) valOrNode,
                                                              hash,
                                                              key,
                                                              (V) newValue);
                    return replace(keyIndex, null, keyIndex + 1, newNode);
                }
            }
            else // Adding a new child
            {
                Object newValue = changes.keyAdded(value);
                int numVals = Integer.bitCount(bitmap);
                if (numVals >= MAX_CHILDREN)
                {
                    return expand(key, (V) newValue, hashFragment, numVals);
                }
                else
                {
                    Object[] newPairs = cloneExtendAndInsert(kvPairs,
                                                              numVals,
                                                              2 * (numVals + 1),
                                                              keyIndex,
                                                              key,
                                                              newValue);
                    return new BitMappedNode<>(bitmap | bit, newPairs);
                }
            }
        }


        /**
         * {@inheritDoc}
         *
         * In addition to mutating the trie, mWith allocates some extra space
         * to the {@link BitMappedNode} so that it can add nodes without needing
         * to expand the array as much.
         */
        @Override
        TrieNode<K, V> mWith(int hash,
                             int shift,
                             K key,
                             V value,
                             Changes changes)
        {
            int hashFragment = hashFragment(hash, shift);
            int bit = bitPosition(hashFragment);
            int keyIndex = 2 * arrayIndex(bit);
            if (isInBitmap(bit))
            {
                Object keyOrNull = kvPairs[keyIndex];
                Object valOrNode = kvPairs[keyIndex + 1];
                if (keyOrNull == null)
                {
                    TrieNode<K, V> node = (TrieNode<K, V>) valOrNode;
                    TrieNode<K, V> newNode = node.mWith(hash, shift + 5, key, value, changes);
                    if (newNode != valOrNode)
                    {
                        kvPairs[keyIndex + 1] = newNode;
                    }
                    // Child node has updated the Changes.
                }
                else if (equivKeys(keyOrNull, key))
                {
                    Object storedValue = valOrNode;
                    Object newValue = changes.replacing(storedValue, value);
                    if (storedValue != newValue)
                    {
                        changes.keyReplaced();
                        kvPairs[keyIndex + 1] = newValue;
                    }
                }
                else // Hash fragment collision
                {
                    Object newValue = changes.keyAdded(value);
                    TrieNode<K, V> newNode = resolveCollision(hashCodeFor(keyOrNull),
                                                              (K) keyOrNull,
                                                              (V) valOrNode,
                                                              hash,
                                                              key,
                                                              (V) newValue);
                    kvPairs[keyIndex] = null;
                    kvPairs[keyIndex + 1] = newNode;
                }
            }
            else // New hash fragment
            {
                Object newValue = changes.keyAdded(value);
                int numVals = Integer.bitCount(bitmap);
                if (numVals >= MAX_CHILDREN)
                {
                    return expand(key, newValue, hashFragment, numVals);
                }
                else if (numVals * 2 < kvPairs.length)
                {
                    System.arraycopy(kvPairs, keyIndex, kvPairs, keyIndex + 2, 2 * numVals - keyIndex);
                    kvPairs[keyIndex] = key;
                    kvPairs[keyIndex + 1] = newValue;
                    bitmap |= bit;
                }
                else
                {
                    // Give the new array some extra space.
                    kvPairs = cloneExtendAndInsert(kvPairs,
                                                   numVals,
                                                   2 * (numVals + 2),
                                                   keyIndex,
                                                   key,
                                                   newValue);
                    bitmap |= bit;
                }
            }

            return this;
        }


        private HashArrayMappedNode<K, V> expand(K key,
                                                 Object value,
                                                 int hashFragment,
                                                 int numVals)
        {
            TrieNode<K, V>[] nodes = new TrieNode[32];
            nodes[hashFragment] = new FlatNode<>(key, value);

            int j = 0;
            for (int i = 0; i < 32; i++)
            {
                if (isInBitmap(1 << i))
                {
                    Object keyOrNull = kvPairs[j];
                    Object valOrNode = kvPairs[j + 1];
                    if (keyOrNull == null)
                    {
                        nodes[i] = (TrieNode<K, V>) valOrNode;
                    }
                    else
                    {
                        nodes[i] = new FlatNode<>((K) keyOrNull, (V) valOrNode);
                    }
                    j += 2;
                }
            }
            return new HashArrayMappedNode<>(numVals + 1, nodes);
        }


        @Override
        V get(int hash, int shift, K key)
        {
            int hashFragment = hashFragment(hash, shift);
            int bit = bitPosition(hashFragment);
            if (!isInBitmap(bit))
            {
                return null;
            }

            int keyIndex = 2 * arrayIndex(bit);
            Object keyOrNull = kvPairs[keyIndex];
            Object valOrNode = kvPairs[keyIndex + 1];
            if (keyOrNull == null)
            {
                return ((TrieNode<K, V>) valOrNode).get(hash, shift + 5, key);
            }
            else if (equivKeys(keyOrNull, key))
            {
                return (V) valOrNode;
            }
            else
            {
                return null;
            }
        }


        @Override
        TrieNode<K, V> without(int hash, int shift, K key, Changes changes)
        {
            int hashFragment = hashFragment(hash, shift);
            int bit = bitPosition(hashFragment);
            if (!isInBitmap(bit))
            {
                return this;
            }

            int keyIndex = 2 * arrayIndex(bit);
            Object keyOrNull = kvPairs[keyIndex];
            Object valOrNode = kvPairs[keyIndex + 1];
            if (keyOrNull == null)
            {
                TrieNode<K, V> node = (TrieNode<K, V>) valOrNode;
                TrieNode<K, V> newNode = node.without(hash, shift + 5, key, changes);
                if (newNode == valOrNode)
                {
                    return this;
                }
                else if (newNode == EmptyNode.SINGLETON)
                {
                    if (bitmap == bit)
                    {
                        return newNode;
                    }
                    else
                    {
                        return new BitMappedNode<>(bitmap ^ bit,
                                                   cloneAndRemovePair(kvPairs, keyIndex));
                    }
                }
                else
                {
                    return replace(keyIndex + 1, newNode);
                }
            }
            else if (equivKeys(keyOrNull, key))
            {
                changes.keyRemoved(valOrNode);
                if (bitmap == bit)
                {
                    return empty();
                }
                else
                {
                    return new BitMappedNode<>(bitmap ^ bit,
                                               cloneAndRemovePair(kvPairs, keyIndex));
                }
            }
            else
            {
                return this;
            }
        }


        @Override
        public Iterator<Entry<K, V>> iterator()
        {
            return new EntryArrayIterator<>(kvPairs);
        }


        /**
         * The index in the array corresponds to the number of bits "below" this one.
         */
        int arrayIndex(int bit)
        {
            return Integer.bitCount(bitmap & (bit - 1));
        }


        boolean isInBitmap(int bit)
        {
            return (bitmap & bit) != 0;
        }


        /**
         * Used for resolving hashFragment collisions within {@link BitMappedNode}s.
         *
         * Keys with hashes that are exactly the same will be placed into a {@link CollisionNode},
         * while keys with hashes that aren't exactly the same will be nested in a {@link FlatNode}.
         */
        private FlatNode<K, V> resolveCollision(int key1hash, K key1, V value1,
                                                int key2hash, K key2, V value2)
        {
            if (key1hash == key2hash)
            {
                return new CollisionNode<>(key1hash, new Object[]{key1, value1, key2, value2});
            }
            else
            {
                return new FlatNode<>(key1, value1, key2, value2);
            }
        }
    }


    /**
     * A trie node storing only child nodes, and no direct key/value pairs.
     * Storage is always full-width, so there's no bitmap to track which entries are present.
     * <p>
     * Nodes of this type are created when a {@link BitMappedNode} grows to contain more than
     * {@value BitMappedNode#MAX_CHILDREN} children.
     * Conversely, when they shrink to contain fewer than {@value #MIN_CHILDREN} child nodes,
     * they are packed back into a {@code BitMappedNode}.
     * </p>
     */
    static class HashArrayMappedNode<K, V>
        extends TrieNode<K, V>
    {
        private static final int MIN_CHILDREN = 8;

        /**
         * count refers to the number of non-null nodes in {@link #nodes},
         * not the size of the subtrie.
         */
        private int count;
        final TrieNode<K, V>[] nodes;

        HashArrayMappedNode()
        {
            count = 0;
            nodes = new TrieNode[32];
        }

        HashArrayMappedNode(int count, TrieNode<K, V>[] nodes)
        {
            this.count = count;
            this.nodes = nodes;
            assert nodes.length == 32;
        }


        @Override
        public int countKeys()
        {
            int count = 0;
            for (TrieNode<K, V> node : nodes)
            {
                if (node != null) count += node.countKeys();
            }
            return count;
        }


        @Override
        public TrieNode<K, V> with(int hash,
                                   int shift,
                                   K key,
                                   V value,
                                   Changes changes)
        {
            int index = hashFragment(hash, shift);
            TrieNode<K, V> node = nodes[index];

            if (node == null)
            {
                Object newValue = changes.keyAdded(value);
                TrieNode<K, V> newNode = new FlatNode<>(key, (V) newValue);
                return new HashArrayMappedNode<>(count + 1,
                                                 cloneAndModify(nodes,
                                                                index,
                                                                newNode));
            }
            else
            {
                TrieNode<K, V> newNode = node.with(hash, shift + 5, key, value, changes);
                if (newNode == node)
                {
                    return this;
                }
                else
                {
                    // Child node has updated the Changes.
                    return new HashArrayMappedNode<>(count,
                                                     cloneAndModify(nodes,
                                                                    index,
                                                                    newNode));
                }
            }
        }


        @Override
        TrieNode<K, V> mWith(int hash,
                             int shift,
                             K key,
                             V value,
                             Changes changes)
        {
            int index = hashFragment(hash, shift);
            TrieNode<K, V> node = nodes[index];

            if (node == null)
            {
                Object newValue = changes.keyAdded(value);
                TrieNode<K, V> newNode = new FlatNode<>(key, newValue);
                nodes[index] = newNode;
                count++;
            }
            else
            {
                TrieNode<K, V> newNode = node.mWith(hash, shift + 5, key, value, changes);
                if (newNode != node)
                {
                    nodes[index] = newNode;
                }
                // Child node has updated the Changes.
            }

            return this;
        }


        @Override
        public V get(int hash, int shift, K key)
        {
            int index = hashFragment(hash, shift);
            TrieNode<K, V> node = nodes[index];
            if (node == null)
            {
                return null;
            }
            else
            {
                return node.get(hash, shift + 5, key);
            }
        }


        @Override
        public TrieNode<K, V> without(int hash, int shift, K key, Changes changes)
        {
            int index = hashFragment(hash, shift);
            TrieNode<K, V> node = nodes[index];
            if (node == null)
            {
                return this;
            }
            else
            {
                // Child node updates Changes
                TrieNode<K, V> newNode = node.without(hash, shift + 5, key, changes);
                if (newNode == node)
                {
                    return this;
                }
                else if (newNode != EmptyNode.SINGLETON)
                {
                    return new HashArrayMappedNode<>(count,
                                                     cloneAndModify(nodes,
                                                                    index,
                                                                    newNode));
                }
                else
                {
                    if (count <= MIN_CHILDREN)
                    {
                        // TODO Can this return a non-SINGLETON empty node?
                        return removeAndPack(index);
                    }
                    else
                    {
                        return new HashArrayMappedNode<>(count - 1,
                                                         cloneAndModify(nodes,
                                                                        index,
                                                                        null));
                    }
                }
            }
        }


        @Override
        public Iterator<Entry<K, V>> iterator()
        {
            return new Iterator<Entry<K , V>>()
            {
                private final TrieNode<K, V>[] array = nodes;
                private Iterator<Entry<K, V>> childIter;
                private int i;

                private boolean getAndCacheNext()
                {
                    while (i < array.length)
                    {
                        TrieNode<K, V> node = array[i++];
                        if (node != null)
                        {
                            childIter = node.iterator();
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public boolean hasNext()
                {
                    return childIter != null || getAndCacheNext();
                }

                @Override
                public Entry<K, V> next()
                {
                    if (childIter != null)
                    {
                        Entry<K, V> ret = childIter.next();
                        if (!childIter.hasNext())
                        {
                            childIter = null;
                        }
                        return ret;
                    }
                    else if (getAndCacheNext())
                    {
                        return next();
                    }
                    else
                    {
                        throw new NoSuchElementException();
                    }
                }

                @Override
                public void remove()
                {
                    throw new UnsupportedOperationException();
                }
            };
        }


        /**
         * Removes the node at the specified index before reducing this
         * {@link HashArrayMappedNode} back down to a {@link BitMappedNode}
         */
        private BitMappedNode<K, V> removeAndPack(int index)
        {
            // This invariant ensures we'll never return an empty node.
            assert count >= MIN_CHILDREN;

            Object[] kvPairs = new Object[2 * (count - 1)];

            // We should never have to return an empty node.
            assert kvPairs.length > 1;

            int j = 1;
            int bitmap = 0;
            for (int i = 0; i < index; i++)
            {
                if (nodes[i] != null)
                {
                    kvPairs[j] = nodes[i];
                    bitmap |= 1 << i;
                    j += 2;
                }
            }
            for (int i = index + 1; i < nodes.length; i++)
            {
                if (nodes[i] != null)
                {
                    kvPairs[j] = nodes[i];
                    bitmap |= 1 << i;
                    j += 2;
                }
            }
            return new BitMappedNode<>(bitmap, kvPairs);
        }
    }

    /**
     * Iterator for {@link FlatNode}s, {@link CollisionNode}s, and {@link BitMappedNode}s,
     * since they all have nearly the same invariants.
     *
     * A minor optimization could be made for {@link CollisionNode}s similar to that done
     * for {@link HashArrayMappedNode}s, since {@link CollisionNode}s never have child nodes.
     */
    static final class EntryArrayIterator<K, V>
        implements Iterator<Entry<K, V>>
    {
        private final Object[] array;
        private int i = 0;

        // At most one of these is non-null because an array will contain
        // either a key then value pair or a null then a node pair.
        private Iterator<Entry<K, V>> childIterator;
        private Entry<K, V> nextEntry;

        private EntryArrayIterator(Object[] array)
        {
            this.array = array;
        }

        private boolean getAndCacheNext()
        {
            while (i < array.length)
            {
                Object keyOrNull = array[i++];
                Object valOrNode = array[i++];
                if (keyOrNull != null)
                {
                    nextEntry = createEntry((K) keyOrNull, (V) valOrNode);
                    return true;
                }
                else if (valOrNode != null)
                {
                    Iterator<Entry<K, V>> i = ((TrieNode<K, V>) valOrNode).iterator();
                    if (i.hasNext())
                    {
                        childIterator = i;
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean hasNext()
        {
            return childIterator != null || nextEntry != null || getAndCacheNext();
        }

        @Override
        public Entry<K, V> next()
        {
            if (childIterator != null)
            {
                Entry<K, V> ret = childIterator.next();
                if (!childIterator.hasNext())
                {
                   childIterator = null;
                }
                return ret;
            }
            else if (nextEntry != null)
            {
                Entry<K, V> ret = nextEntry;
                nextEntry = null;
                return ret;
            }
            else if (getAndCacheNext())
            {
                return next();
            }
            else
            {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }


    //==========================================================================
    // Utility methods for node classes.

    /**
     * In case key comparison needs to change in the future.
     * @return true if keys are equal (however we define that), false otherwise.
     */
    private static <K> boolean equivKeys(K key1, K key2)
    {
        return key1 == key2 || key1.equals(key2);
    }

    /**
     * In case value comparison needs to change in the future.
     *
     * @return true if the values are equal (however we define that), false otherwise.
     */
    private static <V> boolean equivVals(V val1, V val2)
    {
        return val1 == val2;
    }

    /**
     * Short, generified wrapper for AbstractMap.SimpleEntry.
     */
    private static <K, V> Entry<K, V> createEntry(K key,
                                                      V value)
    {
        return new SimpleEntry<>(key, value);
    }

    private static Object[] cloneAndExtend(Object[] array,
                                           Object key,
                                           Object value)
    {
        int len = array.length;
        Object[] newArray = Arrays.copyOf(array, len + 2, Object[].class);
        newArray[len] = key;
        newArray[len + 1] = value;
        return newArray;
    }

    private static Object[] cloneExtendAndInsert(Object[] array,
                                                 int oldNumVals,
                                                 int newLen,
                                                 int keyIndex,
                                                 Object key,
                                                 Object value)
    {
        Object[] newArray = Arrays.copyOf(array, newLen, Object[].class);
        newArray[keyIndex] = key;
        newArray[keyIndex + 1] = value;
        System.arraycopy(array, keyIndex, newArray, keyIndex + 2, 2 * oldNumVals - keyIndex);
        return newArray;
    }

    private static <K, V> TrieNode<K, V>[] cloneAndModify(TrieNode<K, V>[] array,
                                                          int i,
                                                          TrieNode<K, V> a)
    {
        TrieNode<K, V>[] clone = array.clone();
        clone[i] = a;
        return clone;
    }

    private static Object[] cloneAndModify(Object[] array, int i, Object a)
    {
        Object[] clone = array.clone();
        clone[i] = a;
        return clone;
    }

    private static Object[] cloneAndModify(Object[] array,
                                           int i,
                                           Object a,
                                           int j,
                                           Object b)
    {
        Object[] clone = array.clone();
        clone[i] = a;
        clone[j] = b;
        return clone;
    }

    private static Object[] cloneAndRemovePair(Object[] array, int keyIndex)
    {
        Object[] clone = Arrays.copyOf(array, array.length - 2, Object[].class);
        System.arraycopy(array, keyIndex + 2, clone, keyIndex, clone.length - keyIndex);
        return clone;
    }
}
