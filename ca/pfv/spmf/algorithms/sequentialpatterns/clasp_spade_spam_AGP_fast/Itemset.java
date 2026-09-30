package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.util.Arrays;
/*
 * Copyright Antonio Gomariz Peñalver 2013
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
 * Do not remove copyright and license.
 */
/**
 * Implementation of an itemset.
 *
 * @author agomariz
 */
public final class Itemset {

    /** Items stored as plain ints; eliminates all Item-wrapper boxing overhead. */
    private int[] items;
    
    /** Current number of items stored in this itemset. */
    private int size;

    /** Constructs an empty itemset with a small initial capacity. */
    public Itemset() {
        this.items = new int[4];
        this.size = 0;
    }

    /**
     * Private constructor used by {@link #cloneItemSet()} to build a copy whose
     * backing array is already correctly sized, instead of allocating a
     * throwaway default-capacity array via {@link #Itemset()} only to
     * immediately overwrite it.
     * 
     * @param items backing array (not defensively copied; caller must hand
     *              over an array it no longer holds a mutable reference to)
     * @param size  number of valid elements in items
     */
    private Itemset(int[] items, int size) {
        this.items = items;
        this.size = size;
    }

    /**
     * Adds an item id to this itemset.
     * @param itemId the item to add
     */
    public void addItem(int itemId) {
        if (size == items.length) {
            // size == 0 grow to a positive capacity of 4
            items = Arrays.copyOf(items, size == 0 ? 4 : size * 2);
        }
        items[size++] = itemId;
    }

    /**
     * Returns the item id at the given index.
     * @param index the index
     * @return the item id
     */
    public int get(int index) {
        return items[index];
    }

    /**
     * Removes and returns the item id at the given index.
     * @param index the index of the item to remove
     * @return the removed item id
     */
    public int removeItem(int index) {
        int val = items[index];
        System.arraycopy(items, index + 1, items, index, size - index - 1);
        size--;
        return val;
    }

    /**
     * Returns the number of items in this itemset.
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Creates a deep copy of this itemset.
     * @return the cloned itemset
     */
    public Itemset cloneItemSet() {
        return new Itemset(Arrays.copyOf(this.items, this.size), this.size);
    }

    /**
     * Returns the string representation of this itemset.
     * @return the string representation
     */
    @Override
    public String toString() {
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < size; i++) {
            r.append(items[i]);
            r.append(' ');
        }
        return r.toString();
    }
}