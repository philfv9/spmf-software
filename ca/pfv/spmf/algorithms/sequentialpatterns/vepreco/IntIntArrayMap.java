package ca.pfv.spmf.algorithms.sequentialpatterns.vepreco;

import java.util.Arrays;
/*
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
 * Copyright (c) Chen Hui
 *
 * Do not remove copyright and license information.
 */
/**
 * Open-addressing int-to-IntList map that avoids Integer boxing. Keys must be
 * non-negative; empty slots are marked with -1. Uses linear probing.
 *
 * @author Chen Hui
 */
final class IntIntArrayMap {

    /** Sentinel value marking an empty slot. */
    private static final int EMPTY = -1;

    /** Key array; EMPTY marks unused slots. */
    private int[] keys;

    /** Value array parallel to keys. */
    private IntList[] vals;

    /** Number of entries currently stored in the map. */
    private int count;

    /** Maximum number of entries before the map is rehashed. */
    private int threshold;

    /**
     * Allocates the map with an initial capacity rounded up to the next power of two.
     *
     * @param cap expected number of entries
     */
    IntIntArrayMap(int cap) {
        int c = Integer.highestOneBit(Math.max(cap * 2, 4));
        keys = new int[c];
        vals = new IntList[c];
        Arrays.fill(keys, EMPTY);
        threshold = c * 3 / 4;
    }

    /**
     * Returns the IntList associated with the given key, creating an empty one if absent.
     *
     * @param key the non-negative key to look up or insert
     * @return the existing or newly created IntList for that key
     */
    IntList getOrCreate(int key) {
        int mask = keys.length - 1;
        int idx = key & mask;
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key)
                return vals[idx];
            idx = (idx + 1) & mask;
        }
        // New entry
        keys[idx] = key;
        vals[idx] = new IntList(4);
        count++;
        if (count > threshold) {
            rehash();
            // Re-find slot after rehash
            mask = keys.length - 1;
            idx = key & mask;
            while (keys[idx] != key)
                idx = (idx + 1) & mask;
        }
        return vals[idx];
    }

    /**
     * Returns the IntList associated with the given key, or null if absent.
     *
     * @param key the non-negative key to look up
     * @return the IntList for that key, or null
     */
    IntList get(int key) {
        int mask = keys.length - 1;
        int idx = key & mask;
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key)
                return vals[idx];
            idx = (idx + 1) & mask;
        }
        return null;
    }

    /** Doubles the capacity and re-inserts all existing entries. */
    private void rehash() {
        int[] oldKeys = keys;
        IntList[] oldVals = vals;
        int newCap = keys.length * 2;
        keys = new int[newCap];
        vals = new IntList[newCap];
        Arrays.fill(keys, EMPTY);
        threshold = newCap * 3 / 4;
        count = 0;
        int mask = newCap - 1;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                int idx = oldKeys[i] & mask;
                while (keys[idx] != EMPTY)
                    idx = (idx + 1) & mask;
                keys[idx] = oldKeys[i];
                vals[idx] = oldVals[i];
                count++;
            }
        }
    }

    /**
     * Iterates over all entries in the map without boxing.
     *
     * @param c the consumer to call for each key-value pair
     */
    void forEach(EntryConsumer c) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY)
                c.accept(keys[i], vals[i]);
        }
    }

    /**
     * Callback interface for iterating over map entries without boxing.
     */
    interface EntryConsumer {

        /**
         * Processes a single map entry.
         *
         * @param key the entry key
         * @param val the IntList associated with that key
         */
        void accept(int key, IntList val);
    }
}