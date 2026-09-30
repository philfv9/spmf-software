package ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast;
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
import java.util.Arrays;
import java.util.List;

/**
 * A sequence stored as a flat, cache-friendly primitive representation:
 * all items of all itemsets concatenated into one int[] (flatItems), with
 * each itemset's items sorted ascending; a second int[] (itemsetOffsets)
 * marks where each itemset begins within flatItems.
 * 
 * @author Antonio Gomariz Peñalver,  Philippe Fournier-Viger (some optimizations)
 */
public final class Sequence {

    /**
     * Shared empty-sequence sentinel returned by filterActiveItems() when a
     * sequence becomes too short to matter. A single instance is reused for
     * every dropped sequence instead of allocating a new zero-length pair.
     */
    public static final Sequence EMPTY = new Sequence(new int[0], new int[]{0});

    /** All items of all itemsets, concatenated in order, each itemset sorted ascending. */
    private final int[] flatItems;

    /**
     * Itemset i occupies flatItems[itemsetOffsets[i] .. itemsetOffsets[i+1]).
     * Length is (number of itemsets + 1); first entry is always 0.
     */
    private final int[] itemsetOffsets;

    /**
     * Builds a sequence directly from its already-flattened representation.
     * Ownership of both arrays is taken; callers must not mutate them.
     *
     * @param flatItems      the concatenated, per-itemset-sorted item array
     * @param itemsetOffsets the itemset boundary offsets into flatItems
     */
    public Sequence(int[] flatItems, int[] itemsetOffsets) {
        this.flatItems = flatItems;
        this.itemsetOffsets = itemsetOffsets;
    }

    /**
     * Convenience constructor for building a sequence from a list of itemsets
     * (each given as a plain int[], sorted here). Used mainly in tests.
     *
     * @param itemsets the list of itemsets composing the sequence, in order
     */
    public Sequence(List<int[]> itemsets) {
        int total = 0;
        for (int[] itemset : itemsets) {
            total += itemset.length;
        }

        int[] flat = new int[total];
        int[] offsets = new int[itemsets.size() + 1];
        int pos = 0;
        for (int i = 0; i < itemsets.size(); i++) {
            int[] itemset = itemsets.get(i).clone();
            Arrays.sort(itemset);
            offsets[i] = pos;
            System.arraycopy(itemset, 0, flat, pos, itemset.length);
            pos += itemset.length;
        }
        offsets[itemsets.size()] = pos;

        this.flatItems = flat;
        this.itemsetOffsets = offsets;
    }

    /**
     * Builds a human-readable representation of this sequence's itemsets.
     *
     * @return the string representation of this sequence
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(flatItems.length * 3 + size() * 2 + 8);
        int numItemsets = size();
        for (int i = 0; i < numItemsets; i++) {
            sb.append('{');
            for (int p = itemsetOffsets[i]; p < itemsetOffsets[i + 1]; p++) {
                sb.append(flatItems[p]).append(' ');
            }
            sb.append('}');
        }
        return sb.append("    ").toString();
    }

    /**
     * Returns the number of itemsets in this sequence.
     *
     * @return the number of itemsets
     */
    public int size() {
        return itemsetOffsets.length - 1;
    }

    /**
     * Returns the total number of items across all itemsets. Because
     * flatItems is always trimmed exactly, this doubles as the O(1)
     * fast-fail check in matchInSequence.
     *
     * @return the total item count
     */
    public int getLength() {
        return flatItems.length;
    }

    /**
     * Returns the item at a given position within a given itemset.
     *
     * @param itemsetIndex  the itemset's index
     * @param posInItemset  the item's position within that itemset
     * @return the requested item
     */
    public int getItem(int itemsetIndex, int posInItemset) {
        return flatItems[itemsetOffsets[itemsetIndex] + posInItemset];
    }

    /**
     * Returns the internal flat item array. Used directly by the
     * item-sequence flow, where each itemset holds exactly one item and the
     * flat order already represents the temporal item order. Callers must
     * not mutate the returned array.
     *
     * @return the flat item array
     */
    public int[] getFlatItems() {
        return flatItems;
    }

    /**
     * Returns the internal itemset offset array. Callers must not mutate the
     * returned array.
     *
     * @return the itemset offset array
     */
    public int[] getItemsetOffsets() {
        return itemsetOffsets;
    }

    /**
     * Builds a new sequence keeping only the items present in itemMapping
     * (values &gt;= 0), with each kept item translated through it. Itemsets
     * that become empty are dropped. Each modified itemset is re-sorted
     * after translation to preserve the ascending-order invariant required
     * by findItemsetContainingAll.
     *
     * itemMapping is a dense lookup table indexed by the original item
     * value: itemMapping[oldItem] is the renamed id, or a negative value if
     * oldItem is not frequent (and thus dropped). Using a primitive array
     * instead of a boxed Map avoids autoboxing and hashing overhead on this
     * hot, whole-database pass.
     *
     * @param itemMapping the original-item -> renamed-id lookup table
     * @return a new, filtered and renamed Sequence
     */
    public Sequence filterAndRename(int[] itemMapping) {
        int numItemsets = size();
        int[] newItems = new int[flatItems.length];
        int[] newOffsets = new int[numItemsets + 1];
        int writePos = 0;
        int offsetCount = 0;
        newOffsets[offsetCount++] = 0;
        int mappingLength = itemMapping.length;

        for (int i = 0; i < numItemsets; i++) {
            int from = itemsetOffsets[i];
            int to = itemsetOffsets[i + 1];
            int start = writePos;

            for (int p = from; p < to; p++) {
                int oldItem = flatItems[p];
                if (oldItem < mappingLength) {
                    int renamed = itemMapping[oldItem];
                    if (renamed >= 0) {
                        newItems[writePos++] = renamed;
                    }
                }
            }

            int itemsetLength = writePos - start;
            if (itemsetLength > 0) {
                if (itemsetLength > 1) {
                    Arrays.sort(newItems, start, writePos);
                }
                newOffsets[offsetCount++] = writePos;
            }
        }

        int[] trimmedItems = (writePos == newItems.length)
                ? newItems : Arrays.copyOf(newItems, writePos);
        int[] trimmedOffsets = (offsetCount == newOffsets.length)
                ? newOffsets : Arrays.copyOf(newOffsets, offsetCount);
        return new Sequence(trimmedItems, trimmedOffsets);
    }

    /**
     * Builds a new sequence keeping only items marked active in activeItemMask.
     *
     * Fast paths that avoid all allocation:
     *   - If every item survives, returns this directly.
     *   - If the result falls below minItemCount, returns EMPTY.
     *
     * The counting pre-scan means allocation only happens when an actual
     * rebuild is unavoidable, and the new arrays are sized exactly.
     *
     * @param activeItemMask the mask of items to keep
     * @param minItemCount   the minimum surviving item count for the sequence
     *                       to remain worth keeping
     * @return this sequence unchanged, a filtered copy, or EMPTY
     */
    public Sequence filterActiveItems(boolean[] activeItemMask, int minItemCount) {
        // Count survivors first, paying only read cost before any allocation.
        int survivingItems = 0;
        for (int p = 0; p < flatItems.length; p++) {
            if (activeItemMask[flatItems[p]]) {
                survivingItems++;
            }
        }

        if (survivingItems == flatItems.length) {
            return this; // nothing changed, zero allocation
        }
        if (survivingItems < minItemCount) {
            return EMPTY; // too short to matter, zero allocation
        }

        // Only reach here when a real rebuild is needed.
        int numItemsets = size();
        int[] newItems = new int[survivingItems]; // exact size, no trim needed
        int[] newOffsets = new int[numItemsets + 1];
        int writePos = 0;
        int offsetCount = 0;
        newOffsets[offsetCount++] = 0;

        for (int i = 0; i < numItemsets; i++) {
            int from = itemsetOffsets[i];
            int to = itemsetOffsets[i + 1];
            int start = writePos;

            for (int p = from; p < to; p++) {
                int item = flatItems[p];
                if (activeItemMask[item]) {
                    newItems[writePos++] = item;
                }
            }

            if (writePos > start) {
                newOffsets[offsetCount++] = writePos;
            }
        }

        int[] trimmedOffsets = (offsetCount == newOffsets.length)
                ? newOffsets : Arrays.copyOf(newOffsets, offsetCount);
        return new Sequence(newItems, trimmedOffsets);
    }

    /**
     * Finds the earliest itemset at index >= fromItemsetIndex that contains
     * ALL of pattern items [fromPatternIndex, toPatternIndexExclusive).
     * Uses binary search since each itemset's slice is sorted ascending.
     * Returns the matching itemset index, or -1 if none qualifies.
     *
     * @param pattern                  the pattern being matched
     * @param fromPatternIndex         first pattern element index to look for (inclusive)
     * @param toPatternIndexExclusive  last pattern element index to look for (exclusive)
     * @param fromItemsetIndex         first itemset index to consider (inclusive)
     * @return the matching itemset index, or -1 if none is found
     */
    public int findItemsetContainingAll(Pattern pattern, int fromPatternIndex,
            int toPatternIndexExclusive, int fromItemsetIndex) {
        int numItemsets = size();
        int start = Math.max(fromItemsetIndex, 0);

        outer:
        for (int idx = start; idx < numItemsets; idx++) {
            int from = itemsetOffsets[idx];
            int to = itemsetOffsets[idx + 1];

            for (int p = fromPatternIndex; p < toPatternIndexExclusive; p++) {
                int item = pattern.getItem(p);
                if (Arrays.binarySearch(flatItems, from, to, item) < 0) {
                    continue outer;
                }
            }
            return idx;
        }
        return -1;
    }
}