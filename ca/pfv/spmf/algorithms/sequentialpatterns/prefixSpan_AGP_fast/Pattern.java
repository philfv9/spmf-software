/*
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
package ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Implementation of pattern structure. We define it as a list of items, where
 * each item (except the first) has a flag indicating if it has an equal
 * relation with the previous item (i.e. occurs in the same itemset) or an after
 * relation (i.e. occurs in a later itemset).
 * Besides, a bitSet appearingIn denotes the sequences where the pattern appears.
 * 
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * @author agomariz
 */
public class Pattern implements Comparable<Pattern> {

    /**
     * Items that compose the pattern, stored as a primitive array (like
     * Itemset does) instead of a List<Integer>. A pattern is extended by one
     * item, and cloned, at every single node of the recursion for every
     * frequent pattern found, so boxing the item there and copying an
     * ArrayList's boxed backing storage on clonePattern() was a major
     * allocation hot spot.
     */
    private int[] items = new int[4];
    /**
     * Number of items actually stored in the backing array
     */
    private int size = 0;
    /**
     * Bit i is set if item i has an equal relation with the previous item
     * (i.e. occurs in the same itemset). Stored as a primitive long bitmask
     * instead of a BitSet: patterns essentially never exceed 64 items in
     * practice, and a long makes both add() and clonePattern() single
     * primitive operations instead of method calls into BitSet's
     * bounds-checked word array (which also allocated a long[] on clone).
     */
    private long equalRelations;
    /**
     * Set of sequence IDs indicating where the pattern appears
     */
    private BitSet appearingIn;

    /**
     * Standard constructor
     */
    public Pattern() {
        this.appearingIn = new BitSet();
    }

    /**
     * New pattern from a single item
     * @param item the item
     * @param equalRelation flag indicating if the item has an equal relation
     * with the previous item in the pattern
     */
    public Pattern(int item, boolean equalRelation) {
        this();
        add(item, equalRelation);
    }
    
    /**
     * Method to obtain the support of a pattern
     * @return  the support as an integer.
     */
    public int getSupport(){
        return appearingIn.cardinality();
    }

    /**
     * It grows the backing array so that it can hold at least minCapacity items.
     * @param minCapacity the minimum capacity needed
     */
    private void ensureCapacity(int minCapacity) {
        if (minCapacity > items.length) {
            int newCapacity = Math.max(items.length * 2, minCapacity);
            items = Arrays.copyOf(items, newCapacity);
        }
    }

    /**
     * Get the string representation of a pattern
     * @return a string.
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (!hasEqualRelation(i)) {
                result.append(" ->");
            }
            result.append(' ').append(items[i]);
        }
        result.append("\t(").append(getSupport()).append(')');
        result.append("\t[");
        for (int i = appearingIn.nextSetBit(0); i >=0; i=appearingIn.nextSetBit(i+1)) {
            result.append(i).append(", ");
        }
        result.deleteCharAt(result.length() - 1);
        result.deleteCharAt(result.length() - 1);
        result.append("]");
        return result.toString();
    }
    
    /**
     * Get the string representation of itemset. Adjusted to SPMF format.
     * @param outputSequenceIdentifiers if true, sequence ids of the pattern will be shown.
     * @return the string representation.
     */
    public String toStringToFile(boolean outputSequenceIdentifiers) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i == 0) {
                result.append(items[i]);
            } else {
                if (!hasEqualRelation(i)) {
                    result.append(" -1");
                }
                result.append(' ').append(items[i]);
            }
            if (i == size - 1) {
                result.append(" -1");
            }
        }
        result.append(" #SUP: ");
        result.append(appearingIn.cardinality());
        // if the user wants the sequence IDs, we will show them
        if(outputSequenceIdentifiers) {
        	result.append(" #SID: ");
        	for (int i = appearingIn.nextSetBit(0); i >= 0; i = appearingIn.nextSetBit(i+1)) {
        		result.append(i - 1);  // BUG FIX: PHILIPPE 2017 - we must decrease ids by 1
        		result.append(" ");
        	}
        }
        return result.toString();
    }

    /**
     * It clones this pattern.
     * @return a clone of the pattern.
     */
    public Pattern clonePattern() {
        Pattern clone = new Pattern();
        clone.items = Arrays.copyOf(items, size);
        clone.size = size;
        clone.equalRelations = equalRelations;
        return clone;
    }

    /**
     * It obtains the items of the pattern.
     * @return  the items contained in the pattern as a list.
     */
    public List<Integer> getItems() {
        List<Integer> result = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            result.add(items[i]);
        }
        return result;
    }

    /**
     * It obtains the Ith item of the pattern
     * @param i  the position i
     * @return the item.
     */
    public int getItem(int i) {
        return items[i];
    }

    /**
     * It informs if the Ith item has an equal relation with the previous item
     * in the pattern (i.e. occurs in the same itemset)
     * @param i the position i
     * @return true if it has an equal relation
     */
    public boolean hasEqualRelation(int i) {
        return (equalRelations & (1L << i)) != 0;
    }

    /**
     * It adds an item in the pattern. The new item is added in the last
     * position of the pattern.
     * @param item the item to add
     * @param equalRelation flag indicating if the item has an equal relation
     * with the previous item in the pattern
     */
    public void add(int item, boolean equalRelation) {
        if (equalRelation) {
            equalRelations |= (1L << size);
        }
        ensureCapacity(size + 1);
        items[size++] = item;
    }

    /**
     * It returns the number of items contained in the pattern
     * @return the size (int).
     */
    public int size() {
        return size;
    }

    /**
     * This methods compares this pattern with another pattern according to the lexical order.
     * We use a lexicographic order.
     * @param arg the other pattern
     * @return -1 if this pattern is smaller, 0 if equals, otherwise 1.
     */
    @Override
    public int compareTo(Pattern arg) {
        int[] itemsOfGreaterPattern, itemsOfSmallerPattern;
        long relationsOfGreaterPattern, relationsOfSmallerPattern;
        int greaterSize, smallerSize;
        if (size >= arg.size) {
            itemsOfGreaterPattern = items;
            relationsOfGreaterPattern = equalRelations;
            greaterSize = size;
            itemsOfSmallerPattern = arg.items;
            relationsOfSmallerPattern = arg.equalRelations;
            smallerSize = arg.size;
        } else {
            itemsOfSmallerPattern = items;
            relationsOfSmallerPattern = equalRelations;
            smallerSize = size;
            itemsOfGreaterPattern = arg.items;
            relationsOfGreaterPattern = arg.equalRelations;
            greaterSize = arg.size;
        }
        for (int i = 0; i < smallerSize; i++) {
            int comparison = compareElement(itemsOfSmallerPattern[i],
                    (relationsOfSmallerPattern & (1L << i)) != 0,
                    itemsOfGreaterPattern[i],
                    (relationsOfGreaterPattern & (1L << i)) != 0);
            if (comparison != 0) {
                return comparison;
            }
        }
        if (greaterSize == smallerSize) {
            return 0;
        }
        if (size < arg.size) {
            return -1;
        }
        return 1;
    }

    /**
     * It compares two elements <item, equalRelation> as in a pattern.
     * @return -1, 0 or 1
     */
    private static int compareElement(int item1, boolean equalRelation1, int item2, boolean equalRelation2) {
        int comparisonItems = -Integer.compare(item1, item2);
        if (comparisonItems != 0) {
            return comparisonItems;
        }
        if (equalRelation1 == equalRelation2) {
            return 0;
        }
        return equalRelation1 ? -1 : 1;
    }

    @Override
    public boolean equals(Object arg) {
        if (arg instanceof Pattern) {
            Pattern pattern = (Pattern) arg;
            if (this.compareTo(pattern) == 0) {
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + Arrays.hashCode(Arrays.copyOf(items, size));
        hash = 67 * hash + (int) (equalRelations ^ (equalRelations >>> 32));
        return hash;
    }

    /**
     * It returns the list of sequence IDs where the pattern appears.
     * @return the list of sequence IDs.
     */
    public BitSet getAppearingIn() {
        return appearingIn;
    }

    /**
     * It sets the list of sequence IDs where the pattern appears.
     * @param appearingIn the list of sequence ids as a bitset.
     */
    public void setAppearingIn(BitSet appearingIn) {
        this.appearingIn = appearingIn;
    }
}