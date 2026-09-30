package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;

import java.util.Arrays;

/*
 * This is an implementation of a SE position list used by the LAPIN-SPAM
 * algorithm, to represent the positions where some items appear in a sequence.
 *
 * The LAPIN-SPAM algorithm was originally described in this paper:
 *
 * Zhenlu Yang and Masrau Kitsuregawa. LAPIN-SPAM: An improved algorithm for
 * mining sequential pattern In Proc. of Int'l Special Workshop on Databases For
 * Next Generation Researchers (SWOD'05) in conjunction with ICDE'05, pp. 8-11,
 * Tokyo, Japan, Apr. 2005.
 *
 * Copyright (c) 2008-2013 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * Do not remove copyright and license information.
 */
public class SEPositionList {

    /** Sorted array of item ids present in this sequence **/
    int[] listItems;

    /** Parallel array of primitive short[] position lists, one per item **/
    short[][] listPositions;

    /** Number of valid entries in each position list **/
    int[] listSizes;

    /**
     * Constructor that builds the SE position list from a primitive int array of seen items.
     *
     * @param itemSeenList  array of item ids seen in this sequence
     * @param itemSeenCount number of valid entries in itemSeenList
     */
    public SEPositionList(int[] itemSeenList, int itemSeenCount) {
        listItems = Arrays.copyOf(itemSeenList, itemSeenCount);
        Arrays.sort(listItems);
        listPositions = new short[itemSeenCount][];
        listSizes = new int[itemSeenCount];
        for (int i = 0; i < itemSeenCount; i++) {
            listPositions[i] = new short[4];
        }
    }

    /**
     * Adds the position of an item to this position list.
     *
     * @param item     the item id
     * @param position the itemset position where the item appears
     */
    public void register(int item, short position) {
        int index = Arrays.binarySearch(listItems, item);
        if (index < 0) return;
        int sz = listSizes[index];
        if (sz == listPositions[index].length) {
            listPositions[index] = Arrays.copyOf(listPositions[index], sz * 2);
        }
        listPositions[index][sz] = position;
        listSizes[index] = sz + 1;
    }

    /**
     * Finds the internal slot index for an item in a single binary search. Callers on
     * the hot path should use this once and then read {@link #positionsAt} /
     * {@link #sizeAt} from the returned index.
     *
     * @param item the item id
     * @return the slot index, or a negative value if the item is not present
     */
    public int indexOfItem(int item) {
        return Arrays.binarySearch(listItems, item);
    }

    /**
     * Returns the raw position array stored at the given slot index (as returned by
     * {@link #indexOfItem}). No bounds/validity checking is done here; the caller is
     * expected to have already checked that the index is non-negative.
     */
    public short[] positionsAt(int index) {
        return listPositions[index];
    }

    /**
     * Returns the number of valid positions stored at the given slot index.
     */
    public int sizeAt(int index) {
        return listSizes[index];
    }

    /**
     * Returns the primitive short[] position array for an item, or null if the item is not present.
     *
     * @param item the item id
     * @return the raw short[] array (may be larger than the valid count), or null
     */
    public short[] getPrimitiveListForItem(int item) {
        int index = Arrays.binarySearch(listItems, item);
        return (index >= 0) ? listPositions[index] : null;
    }

    /**
     * Returns the number of valid positions stored for an item.
     * 
     * @param item the item id
     * @return the count, or 0 if the item is not present
     */
    public int getSizeForItem(int item) {
        int index = Arrays.binarySearch(listItems, item);
        return (index >= 0) ? listSizes[index] : 0;
    }

    /**
     * Get a string representation of this SE position list.
     *
     * @return a string
     */
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < listItems.length; i++) {
            buffer.append("  position list of item: ").append(listItems[i]).append("  is: ");
            for (int j = 0; j < listSizes[i]; j++) {
                buffer.append(listPositions[i][j]).append(" ");
            }
            buffer.append(System.lineSeparator());
        }
        return buffer.toString();
    }
}