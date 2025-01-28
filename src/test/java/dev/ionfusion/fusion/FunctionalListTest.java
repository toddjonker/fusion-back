// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package dev.ionfusion.fusion;

import static dev.ionfusion.fusion.FunctionalList.InternalNode;
import static dev.ionfusion.fusion.FunctionalList.LeafNode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;


public class FunctionalListTest
{
    ArrayList<Object> arrayList;
    FunctionalList<Object> functionalList;

    private void setup(int size)
    {
        arrayList = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            String value = UUID.randomUUID().toString();
            arrayList.add(value);
        }

        functionalList = FunctionalList.create(arrayList);
    }

    private void checkEqualsArray()
    {
        assertEquals(arrayList.size(), functionalList.size());

        ArrayList<Integer> unequalIndices = new ArrayList<>();
        for (int i = 0; i < arrayList.size(); i++)
        {
            if (!Objects.equals(arrayList.get(i), functionalList.get(i)))
            {
                unequalIndices.add(i);
            }
        }

        assertTrue(0 == unequalIndices.size(),
                    "These indices did not match: " + unequalIndices.toString());
    }

    private void checkIterator()
    {
        Iterator arrayIterator = arrayList.iterator();
        Iterator functionalIterator = functionalList.iterator();

        while (arrayIterator.hasNext())
        {
            Object arrVal = arrayIterator.next();
            Object funVal = functionalIterator.next();
            assertEquals(arrVal, funVal);
        }
    }

    private void checkSequentialCreation()
    {
        FunctionalList<Object> asSequential = FunctionalList.EMPTY;
        for (Object o : arrayList)
        {
            asSequential = asSequential.add(o);
        }
        assertEquals(functionalList, asSequential);
    }

    @Test
    public void checkZero()
    {
        setup(0);
        checkEqualsArray();
        checkIterator();
        checkSequentialCreation();
    }

    @Test
    public void checkOne()
    {
        setup(1);
        checkEqualsArray();
        checkIterator();
        checkSequentialCreation();
    }

    @Test
    public void checkSmall()
    {
        setup(10);
        checkEqualsArray();
        checkIterator();
        checkSequentialCreation();
    }

    @Test
    public void checkMedium()
    {
        setup(100);
        checkEqualsArray();
        checkIterator();
        checkSequentialCreation();
    }

    @Test
    public void checkLarge()
    {
        setup(1000);
        checkEqualsArray();
        checkIterator();
        checkSequentialCreation();
    }

    @Test
    public void checkVeryLarge()
    {
        setup(10000);
        checkEqualsArray();
        checkIterator();
        checkSequentialCreation();
    }

    @Test
    public void checkSingleFocusBehavior()
    {
        setup(32);
        assertTrue(functionalList.getRoot() instanceof LeafNode);
        assertEquals(0, functionalList.getLevel());
        assertTrue(functionalList.getRoot() == functionalList.getFocus());
    }

    @Test
    public void checkMultiLevelFocusBehavior()
    {
        setup(33);
        assertTrue(functionalList.getRoot() instanceof InternalNode);
        assertEquals(5, functionalList.getLevel());

        // Focus should be at second leaf node 32-63
        LeafNode oldFocus = functionalList.getFocus();
        assertEquals(32, functionalList.getFocusStart());

        // This should move focus to first leaf node indices 0-31
        functionalList.get(12);

        LeafNode newFocus = functionalList.getFocus();

        assertEquals(0, functionalList.getFocusStart());
        assertFalse(oldFocus == newFocus);
    }

    @Test
    public void checkImmutability()
    {
        setup(100);

        FunctionalList stretchedList = functionalList.add(UUID.randomUUID().toString());
        assertEquals(100, functionalList.size());
        assertEquals(101, stretchedList.size());
        assertFalse(stretchedList == functionalList);

        Object oldValue = functionalList.get(50);
        String newValue = UUID.randomUUID().toString();

        FunctionalList modifiedList = functionalList.modify(50, newValue);
        assertEquals(oldValue, functionalList.get(50));
        assertEquals(newValue, modifiedList.get(50));
        assertFalse(functionalList == modifiedList);
    }

    @Test
    public void checkFocusModificationBehavior()
    {
        setup(100);

        String newValue = UUID.randomUUID().toString();
        FunctionalList modifiedList = functionalList.modify(96, newValue);

        assertEquals(newValue, modifiedList.get(96));
        assertNotEquals(newValue, functionalList.get(96));

        // We expect the focus to be functionally modified
        assertFalse(modifiedList.getFocus() == functionalList.getFocus());
        // But the root to stay the same for now.
        assertTrue(modifiedList.getRoot() == functionalList.getRoot());
        assertTrue(modifiedList.isDirty());

        modifiedList.get(50); // shift the focus, which should clean the tree

        // Make sure the value at location hasn't changed
        assertEquals(newValue, modifiedList.get(96));
        assertNotEquals(newValue, functionalList.get(96));
        // but make sure the root has changed and been cleaned
        assertFalse(modifiedList.getRoot() == functionalList.getRoot());
        assertFalse(modifiedList.isDirty());
    }

    @Test
    public void checkConstantFocusSwapping()
    {
        setup(1056); // 3 full new levels.
        assertEquals(1056, functionalList.size());
        assertEquals(10, functionalList.getLevel());
        assertEquals(1024, functionalList.getFocusStart());

        Random random = new Random();
        FunctionalList newList = functionalList;
        // there should be 33 leaf nodes (1056 / 32 = 33).
        for (int i = 0; i < 33; i++)
        {
            String newVal = UUID.randomUUID().toString();
            int modIndex = i * 32 + random.nextInt(32);
            newList = newList.modify(modIndex, newVal);
            assertEquals(newVal, newList.get(modIndex));
            assertEquals(newVal, newList.getFocus().get(modIndex % 32));
            assertEquals(modIndex & (~(0x1f)), newList.getFocusStart());
        }
    }
}
