package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;

import java.util.Arrays;

/***
 * This is an implementation of an 2-IE-position list used by the LAPIN
 * algorithm.
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
 * 
 * Do not remove copyright and license information.
 */
public class IEPositionList {

    /** Sorted flat array of encoded pair keys: key = item1 * MULTIPLIER + item2 **/
    private long[] pairKeys;

    /** Parallel array of primitive short[] position lists, one per pair **/
    private short[][] pairPositions;

    /** Number of valid positions per pair **/
    private int[] pairSizes;

    /** Number of distinct pairs stored **/
    private int pairCount;

    /** Large prime used to encode a pair of ints as a single long **/
    private static final long MULTIPLIER = 1_000_003L;

    /**
     * Constructor
     */
    public IEPositionList() {
        pairKeys = new long[8];
        pairPositions = new short[8][];
        pairSizes = new int[8];
        pairCount = 0;
    }

    /**
     * Encodes a pair of item ids as a single long key.
     *
     * @param item1 the first item (smaller)
     * @param item2 the second item (larger)
     * @return the encoded long key
     */
    private static long encodeKey(int item1, int item2) {
        return (long) item1 * MULTIPLIER + item2;
    }

    /**
     * Sorts the internal pair arrays by key so that binary search can be used during lookups.
     */
    public void sort() {
        // insertion sort is fine; arrays are already nearly sorted as items are visited in order
        for (int i = 1; i < pairCount; i++) {
            long key = pairKeys[i];
            short[] pos = pairPositions[i];
            int sz = pairSizes[i];
            int j = i - 1;
            while (j >= 0 && pairKeys[j] > key) {
                pairKeys[j + 1] = pairKeys[j];
                pairPositions[j + 1] = pairPositions[j];
                pairSizes[j + 1] = pairSizes[j];
                j--;
            }
            pairKeys[j + 1] = key;
            pairPositions[j + 1] = pos;
            pairSizes[j + 1] = sz;
        }
    }

    /**
     * Registers a new position for the given item pair.
     *
     * @param item1    the first item (must be smaller)
     * @param item2    the second item (must be larger)
     * @param position the itemset position where this pair co-occurs
     */
    public void register(int item1, int item2, short position) {
        long key = encodeKey(item1, item2);
        // linear scan: during construction the list is small and not yet sorted
        int idx = -1;
        for (int i = pairCount - 1; i >= 0; i--) {
            if (pairKeys[i] == key) { idx = i; break; }
        }
        if (idx < 0) {
            // grow if needed
            if (pairCount == pairKeys.length) {
                int newCap = pairKeys.length * 2;
                pairKeys = Arrays.copyOf(pairKeys, newCap);
                pairPositions = Arrays.copyOf(pairPositions, newCap);
                pairSizes = Arrays.copyOf(pairSizes, newCap);
            }
            idx = pairCount;
            pairKeys[idx] = key;
            pairPositions[idx] = new short[4];
            pairSizes[idx] = 0;
            pairCount++;
        }
        int sz = pairSizes[idx];
        if (sz == pairPositions[idx].length) {
            pairPositions[idx] = Arrays.copyOf(pairPositions[idx], sz * 2);
        }
        pairPositions[idx][sz] = position;
        pairSizes[idx] = sz + 1;
    }

    /**
     * Returns the primitive short[] position array for the given pair, or null if not found.
     *
     * @param item1 the first item
     * @param item2 the second item
     * @return the raw short[] (may be larger than the valid count), or null
     */
    public short[] getPrimitiveListForPair(int item1, int item2) {
        long key = encodeKey(item1, item2);
        int idx = Arrays.binarySearch(pairKeys, 0, pairCount, key);
        return (idx >= 0) ? pairPositions[idx] : null;
    }

    /**
     * Returns the number of valid positions stored for the given pair.
     *
     * @param item1 the first item
     * @param item2 the second item
     * @return the count, or 0 if not found
     */
    public int getSizeForPair(int item1, int item2) {
        long key = encodeKey(item1, item2);
        int idx = Arrays.binarySearch(pairKeys, 0, pairCount, key);
        return (idx >= 0) ? pairSizes[idx] : 0;
    }

    /**
     * Get a string representation of this 2-IE position list.
     *
     * @return a string
     */
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < pairCount; i++) {
            int item1 = (int) (pairKeys[i] / MULTIPLIER);
            int item2 = (int) (pairKeys[i] % MULTIPLIER);
            buffer.append("  position list of pair: {").append(item1).append(",").append(item2).append("}  is: ");
            for (int j = 0; j < pairSizes[i]; j++) {
                buffer.append(pairPositions[i][j]).append(" ");
            }
            buffer.append("\n");
        }
        return buffer.toString();
    }
}