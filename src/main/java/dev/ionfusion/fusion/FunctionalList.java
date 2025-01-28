// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;

import dev.ionfusion.fusion.util.hamt.FunctionalHashTrie;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A functional backing for Fusion's list data type.
 *
 * Much like the {@link FunctionalHashTrie}, this is based on a 32 way trie, but instead
 * of using hash values for the keys, the indices are used. Indexing occurs from
 * the most significant bits required down to the least significant ones. The
 * number of bits required is completely determined by the size of the list.
 * Indexing is done this way so that the trie is left-wise dense.
 *
 * <br>
 * For example:
 * <pre>
 * {@code
 * A list with elements [ 0, 1, 2, ... 32 ] would result in a trie that looks like this:
 *
 *               [ leaf0 , leaf1 , null, null, ... ]  <=> root = InternalNode, level = 5
 *                  /        \
 *                 /          \
 *                /            \
 *               /              \
 *              /                \
 *             /                  \
 *            /                    \
 *           /                      \
 *   [ 0, 1, ..., 31 ]       [ 32, null, null, ... ]  <=> LeafNodes, level = 0
 *
 * Accessing the 20th element in the above trie:
 * 20 = 0b0000010100
 *
 * Starting at the root, level = 5.
 * We find the sub-index at this node:   ___
 *                                      |   |
 * sub-index = 20 & (0x1f << 5) = 0 = 0b0000010100
 *
 * Then we go to sub-node specified by sub-index, decrement level, and keep going.
 * sub-node = node located at sub-index of root
 * level -= 5
 *
 * Once level = 0, we know we're at the bottom, so return the element at the sub-index of the LeafNode
 *                                             ___
 *                                            |   |
 * sub-index = 20 & (0x1f << 0) = 20 = 0b0000010100
 * return value located at sub-index of sub-node
 *
 * Visually:
 *                  [ leaf0, leaf1, null, null, ... ] <=> root, level = 5 -> sub-index = 0
 *                      |
 *    take this branch  |
 *                      |
 *        [ 0, 1, ..., 20, ... ]                      <=> leaf0, level = 0 -> sub-index = 20
 *                      |
 *    take this branch  |
 *                      |
 *                     20
 *
 * }
 * </pre>
 *
 * Overview of implementation details:
 * <ul>
 *     <li>Values are stored at {@link LeafNode}s,
 *         unlike {@link FunctionalHashTrie} which stores some values
 *         within internal trie nodes.</li>
 *     <li>Like Scala, has the notion of a "focus", where the most recently
 *         accessed node is cached within a field to take advantage of locality
 *         for {@link #get(int)}s and faster {@link #modify(int, Object)}s.
 *         Thus, iteration (using {@link #get(int)}) is O(1), but random searching
 *         is still capped at 1/5 * O(logN).
 *         <ul>
 *             <li>A get outside of the current focus mutates the focus field.</li>
 *             <li>A modification within the focus performs a write-back style
 *                 update and returns a new {@link FunctionalList} with the new
 *                 focus, but an unmodified trie and marks the trie as dirty.
 *                 Changes to the focus on the new {@link FunctionalList} will
 *                 cause the new focus to be inserted into the trie first.
 *                 NOTE: A trie only has to be cleaned once, maintaining the same
 *                 upper bound on update speed as before (1/5 * O(logN)).</li>
 *             <li>{@link #iterator()} and {@link #subIter(int, int)} do NOT
 *                 modify the focus.</li>
 *         </ul>
 *     </li>
 *     <li>Does not implement the <a href="https://infoscience.epfl.ch/record/169879/files/RMTrees.pdf">Relaxed Radix Balanced Trie</a> optimization for appends/subseqs.</li>
 *     <li>Does not implement {@link java.util.List} due to interface conflicts.</li>
 * </ul>
 *
 * WARNING: NOT THREAD SAFE (due to focus mutation).
 */
public class FunctionalList<E>
    implements Iterable<E>
{
    //==========================================================================
    // Constants

    private static final int MASK = 0x1f;           // 5 bits
    private static final int FOCUS_MASK = ~MASK;    // drop last 5 bits.
    private static final int NODE_SIZE = 32;        // 5 bits -> 32 indices.
    static final FunctionalList EMPTY = new FunctionalList(0, 0, LeafNode.EMPTY, 0);


    //==========================================================================
    // Factory Methods

    static <E> FunctionalList<E> create(E[] array)
    {
        return create(Arrays.asList(array));
    }

    static <E> FunctionalList<E> create(Iterable<E> list)
    {
        if (list instanceof FunctionalList)
        {
            return (FunctionalList<E>) list;
        }

        MutableList<E> mutableList = MutableList.makeEmpty();
        for (E element : list)
        {
            mutableList.add(element);
        }
        return mutableList.asFunctional();
    }


    static <E> FunctionalList<E> create(Iterator<E> iterator)
    {
        MutableList<E> mutableList = MutableList.makeEmpty();
        while (iterator.hasNext())
        {
            mutableList.add(iterator.next());
        }
        return mutableList.asFunctional();
    }


    //==========================================================================
    // Class Members

    private final int level; // The level is the amount of levels of nodes within the trie, multiplied by 5.
    private final int size;
    private Node<E> root;
    private int focusStart;
    private LeafNode<E> focus;
    /** Focus was modified but not yet placed into the tree */
    private boolean isDirty; // this could be the lsb in focusStart, but a boolean seems more clear.


    private FunctionalList(int level, int size, Node<E> root, int focusStart)
    {
        this(level, size, root, focusStart, root.findLeaf(level, focusStart), false);
    }


    private FunctionalList(int level,
                   int size,
                   Node<E> root,
                   int focusStart,
                   LeafNode<E> focus,
                   boolean isDirty)
    {
        this.level = level;
        this.size = size;
        this.root = root;
        this.focus = focus;
        this.focusStart = focusStart;
        this.isDirty = isDirty;
    }


    public int size()
    {
        return size;
    }


    public E get(int index)
    {
        if (index >= size || index < 0)
        {
            throw new IndexOutOfBoundsException();
        }

        if (!withinFocus(focusStart, index))
        {
            cleanTrie();
            focusStart = index & FOCUS_MASK; // focusStart = index % 32;
            focus = root.findLeaf(level, focusStart);
        }

        return focus.get(index);
    }


    public FunctionalList<E> add(E value)
    {
        int lastIndex = size;
        int newSize = size + 1;

        if (atCapacity(level, size))
        {
            // If we're at capacity, we're not in the focus, thus we need to clean
            // the trie before inserting a new node.
            cleanTrie();

            int newLevel = level + 5;
            Node<E> newRoot = pushDown(root)
                .modify(newLevel, lastIndex, value);

            assert (lastIndex & MASK) == 0;
            return new FunctionalList<>(newLevel,
                                        newSize,
                                        newRoot,
                                        lastIndex);
        }

        return modify(lastIndex, value, newSize);
    }


    public FunctionalList<E> modify(int index, E value)
    {
        return modify(index, value, size);
    }


    private FunctionalList<E> modify(int index, E value, int newSize)
    {
        if (withinFocus(focusStart, index))
        {
            // Functionally modify focus and note that new trie is dirty.
            LeafNode<E> newFocus = (LeafNode<E>) focus.modify(0, index, value);
            return new FunctionalList<>(level, newSize, root, focusStart, newFocus, true);
        }

        // If we're not in the focus, we have to make sure our tree is clean before making changes
        cleanTrie();
        Node<E> newRoot = root.modify(level, index, value);
        int newFocusStart = index & FOCUS_MASK;

        return new FunctionalList<>(level,
                                    newSize,
                                    newRoot,
                                    newFocusStart);
    }


    public FunctionalList<E> append(Iterable<E>... iterables)
    {
        // TODO This could be optimized further with an RRB Trie
        Node<E> equivalentTrie = createNewRightMostPath();

        // This is safe because the only modifications happen to the
        // rightmost path of the trie.
        MutableList<E> newList = new MutableList<>(level, size, equivalentTrie, size);
        for (Iterable<E> iterable : iterables)
        {
            for (E element : iterable)
            {
                newList.add(element);
            }
        }

        return newList.asFunctional();
    }


    @Override
    public Iterator<E> iterator()
    {
        return subIter(0, size);
    }


    /**
     * @param start index to start iterating from
     * @param end index to iterate to (exclusive)
     * @return a new {@link Iterator} over the values with indices within [start, end)
     * @throws IndexOutOfBoundsException if the requested start or end would cause iteration out of list bounds.
     */
    public Iterator<E> subIter(final int start, final int end)
    {
        if (start < 0 || end > size)
        {
            throw new IndexOutOfBoundsException();
        }

        cleanTrie();
        return new Iterator<E>()
        {
            int i = start;
            Node<E> trieRoot = root;
            E[] values = trieRoot.findLeaf(level, i).values;

            @Override
            public boolean hasNext()
            {
                return i < end;
            }

            @Override
            public E next()
            {
                if (!hasNext())
                {
                    throw new NoSuchElementException();
                }

                E ret = values[(i++ & MASK)];
                if ((i & MASK) == 0)
                {
                    values = trieRoot.findLeaf(level, i).values;
                }
                return ret;
            }

            @Override
            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        };
    }


    public E[] toArray()
    {
        Object[] array = new Object[size];

        int i = 0;
        for (E element : this) // Using the iterator avoids mutation of the focus.
        {
            array[i++] = element;
        }

        return (E[]) array;
    }


    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }

        if (!(obj instanceof FunctionalList))
        {
            return false;
        }

        FunctionalList other = (FunctionalList) obj;
        if (other.size != size)
        {

            return false;
        }

        // Using iterators avoids changing the foci
        Iterator<E> myIter = iterator();
        Iterator<E> otherIter = other.iterator();
        while (myIter.hasNext())
        {
            // Performs reference equality checking because Fusion
            if (myIter.next() != otherIter.next())
            {
                return false;
            }
        }

        return true;
    }


    //==========================================================================
    // Utility Methods

    private static boolean withinFocus(int focusStart, int index)
    {
        return index >= focusStart && index < focusStart + NODE_SIZE;
    }


    /**
     * Cleans the Trie if dirty.
     * Functionally inserts the focus to the correct spot of the trie.
     * Mutably sets root and isDirty flag.
     */
    private void cleanTrie()
    {
        if (isDirty)
        {
            // blech mutation...
            root = root.insertLeaf(level, focus, focusStart);
            isDirty = false;
        }
    }


    /**
     * @return true if we're at capacity for the current level.
     */
    private static boolean atCapacity(int level, int currentSize)
    {
        return currentSize >= (1 << (level + 5));
    }


    /**
     * Nests a {@link Node} within an {@link InternalNode} at the 0-th index;
     * @return The new {@link InternalNode}
     */
    private static <E> Node<E> pushDown(Node<E> currentRoot)
    {
        Node<E>[] subNodes = new Node[32];
        subNodes[0] = currentRoot;
        return new InternalNode<>(subNodes);
    }


    /**
     * @return a new trie that has a new right most path but otherwise shares
     * nodes to the left of it.
     */
    private Node<E> createNewRightMostPath()
    {
        cleanTrie();
        LeafNode<E> rightMostLeaf = root.findLeaf(level, size);
        LeafNode<E> newRightMostLeaf = rightMostLeaf.clone();

        return root.insertLeaf(level, newRightMostLeaf, size);
    }


    //==========================================================================
    // Getters
    // These getters are for implementation testing purposes ONLY
    // TODO: Decide whether or not to remove them before release.

    int getLevel()
    {
        return level;
    }


    Node<E> getRoot()
    {
        return root;
    }


    int getFocusStart()
    {
        return focusStart;
    }


    LeafNode<E> getFocus()
    {
        return focus;
    }


    boolean isDirty()
    {
        return isDirty;
    }


    //==========================================================================
    // Helper Classes

    /**
     * For faster creation of new {@link FunctionalList}s
     * when mutation is guaranteed to be safe (i.e. new lists).
     *
     * Currently not meant for public consumption.
     */
    private static class MutableList<E>
    {
        private int level;
        private int size;
        private Node<E> root;
        private int focusStart;
        private LeafNode<E> focus;
        // There isn't any cleaning or dirty flag required because we can directly modify the focus

        /**
         * @return a new {@link MutableList} with a fresh {@link LeafNode} that
         * can be safely mutated.
         */
        private static MutableList makeEmpty()
        {
            LeafNode focus = new LeafNode(new Object[32]);
            return new MutableList(0, 0, focus, 0);
        }


        /**
         * Creates a new Mutable List reference that mutates members by default.
         * Assumes direct control of the root passed in, so consider copying
         * over nodes that cannot afford to be modified.
         */
        private MutableList(int level, int size, Node<E> root, int focusStart)
        {
            this.level = level;
            this.size = size;
            this.root = root;
            this.focus = root.findLeaf(level, focusStart);
        }


        /**
         * WARNING: Any further mutation to {@code this} will result in awful headaches.
         * Use this only when there are absolutely no more modifications to be done,
         * and drop the mutable reference as soon as possible.
         */
        private FunctionalList<E> asFunctional()
        {
            return new FunctionalList<>(level, size, root, focusStart, focus, false);
        }


        /**
         * Mutatably inserts the value at the end of the list.
         * @param value to insert
         * @return itself
         */
        private MutableList<E> add(E value)
        {
            int lastIndex = size;

            if (atCapacity(level, size))
            {
                assert (lastIndex & MASK) == 0;

                level += 5;
                root = pushDown(root)
                    .mModify(level, lastIndex, value);
                focusStart = lastIndex & FOCUS_MASK;
                focus = root.findLeaf(level, focusStart);
            }
            else if (withinFocus(focusStart, lastIndex))
            {
                focus.mModify(0, lastIndex, value);
            }
            else
            {
                root.mModify(level, lastIndex, value);
                focusStart = lastIndex & FOCUS_MASK;
                focus = root.findLeaf(level, focusStart);
            }

            size++;

            return this;
        }
    }


    /**
     * Abstract interface for trie node classes.
     */
    // TODO: Convert to interface with default methods in Java 8+
    private static abstract class Node<E>
    {
        /**
         * @param level of current node
         * @param index of desired node
         * @return the leaf node corresponding to given index;
         */
        abstract LeafNode<E> findLeaf(int level, int index);


        /**
         * Functionally replaces the leaf node at the given index with the one given.
         * Assumes the path to leaf node already exists.
         * @return The new trie
         */
        abstract Node<E> insertLeaf(int level, LeafNode<E> node, int index);


        /**
         * Functionally modifies the value at the given index.
         * @return the new trie or itself is nothing changed
         */
        abstract Node<E> modify(int level, int index, E value);


        /**
         * Mutates the value at the given index. Not intended for external use.
         * @return itself
         */
        abstract Node<E> mModify(int level, int index, E value);


        // TODO: Make this a general static method s.t. FunctionalHashTrie and FunctionalList can use.
        protected static <T> T[] cloneAndModify(T[] array, int index, T value)
        {
            T[] newArray = array.clone();
            newArray[index] = value;
            return newArray;
        }
    }


    /**
     * A Leaf node stores values of type {@link E}
     */
    final static class LeafNode<E>
        extends Node<E>
    {
        private static final LeafNode EMPTY = new LeafNode(new Object[32]);
        private E[] values;


        LeafNode(E[] values)
        {
            assert values.length == 32;
            this.values = values;
        }


        E get(int index)
        {
            int subIndex = index & MASK;
            return values[subIndex];
        }


        @Override
        LeafNode<E> findLeaf(int level, int index)
        {
            assert level == 0;
            return this;
        }


        /**
         * {@inheritDoc}
         *
         * Once we hit the leaf nodes, we return the focus passed in
         * because we want to replace this leaf node in the trie with the focus.
         * @param node the existing dirty focus
         * @return focus
         */
        @Override
        Node<E> insertLeaf(int level,
                           LeafNode<E> node,
                           int index)
        {
            assert level == 0;
            return node;
        }


        @Override
        Node<E> modify(int level, int index, E value)
        {
            assert level == 0; // leaves should be at the bottom.
            int subIndex = index & MASK;
            E existingValue = values[subIndex];
            if (existingValue == value)
            {
                return this;
            }
            return new LeafNode<>(cloneAndModify(values, subIndex, value));
        }


        @Override
        Node<E> mModify(int level, int index, E value)
        {
            assert level == 0; // leaves should be at the bottom.
            int subIndex = index & MASK;
            values[subIndex] = value;
            return this;
        }


        public LeafNode<E> clone()
        {
            return new LeafNode<>(values.clone());
        }
    }

    /**
     * An {@link InternalNode} stores other nodes.
     */
    final static class InternalNode<E>
        extends Node<E>
    {
        Node<E>[] nodes;

        InternalNode(Node<E>[] nodes)
        {
            assert nodes.length == 32;
            this.nodes = nodes;
        }


        @Override
        LeafNode<E> findLeaf(int level, int index)
        {
            int subIndex = subIndex(level, index);
            return nodes[subIndex].findLeaf(level - 5, index);
        }


        @Override
        Node<E> insertLeaf(int level, LeafNode<E> node, int index)
        {
            int subIndex = subIndex(level, index);
            Node<E> newNode = nodes[subIndex].insertLeaf(level - 5, node, index);

            return new InternalNode<>(cloneAndModify(nodes, subIndex, newNode));
        }


        @Override
        Node<E> modify(int level, int index, E value)
        {
            int subIndex = subIndex(level, index);

            Node<E> existingNode = nodes[subIndex];
            Node<E> newNode;
            if (existingNode == null) // This is null when we're extending the list.
            {
                newNode = allocateNewPath(level, index, value);
                return new InternalNode<>(cloneAndModify(nodes, subIndex, newNode));
            }
            else
            {
                newNode = existingNode.modify(level - 5, index, value);
                if (existingNode == newNode)
                {
                    return this;
                }
                return new InternalNode<>(cloneAndModify(nodes, subIndex, newNode));
            }
        }


        @Override
        Node<E> mModify(int level, int index, E value)
        {
            int subIndex = subIndex(level, index);
            Node<E> subNode = nodes[subIndex];

            if (subNode == null) // This is null when we're extending the list.
            {
                nodes[subIndex] = allocateNewPath(level, index, value);
            }
            else
            {
                nodes[subIndex].mModify(level - 5, index, value);
            }
            return this;
        }


        /**
         * @param index The index of the list.
         * @param level The level of the tree currently being accessed.
         * @return The index of the subtree to go into.
         */
        private static int subIndex(int level, int index)
        {
            return (index >>> level) & MASK;
        }


        /**
         * This creates a new path of nodes from any level. Generates nested
         * {@link InternalNode}s until it reaches level 0 where it generates a
         * {@link LeafNode} and inserts the value at the specified index.
         *
         * For example,
         * [ someLeaf, someLeaf, null, ... ] needs a new path at index 2.
         * this return a new path of nodes to be inserted at index 2, but does NOT
         * actually do the insertion.
         */
        static <E> Node<E> allocateNewPath(int level, int index, E value)
        {
            E[] arr = (E[]) new Object[32];
            arr[subIndex(0, index)] = value;

            Node<E> node = new LeafNode<>(arr);

            for (int i = 5; i < level; i += 5)
            {
                Node<E>[] subNodes = new Node[32];
                subNodes[0] = node;

                node = new InternalNode<>(subNodes);
            }

            return node;
        }
    }
}
