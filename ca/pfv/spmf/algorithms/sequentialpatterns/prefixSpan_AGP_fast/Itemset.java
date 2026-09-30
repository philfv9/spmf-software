package ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import ca.pfv.spmf.datastructures.collections.map.MapIntToInt;
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
/**
 * This class represents an itemset from a sequence.
 * The itemset consists of a list of items.
 * 
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * @author agomariz
 */
public final class Itemset{

    /**
     * Items that compose the itemset
     */
    private int[] items = new int[4];
    /**
     * Number of items actually stored in the backing array
     */
    private int size = 0;

    /**
     * Constructor of an itemset from a single item.
     * @param item the item that compose the itemset.
     */
    public Itemset(int item) {
        addItem(item);
    }

    /**
     * Constructor of an itemset from a collection of items.
     * @param collection Items that compose the itemset.
     */
    public Itemset(Collection<Integer> collection) {
        items = new int[Math.max(collection.size(), 1)];
        for (int item : collection) {
            addItem(item);
        }
    }

    /**
     * Constructor of an itemset from an array of items.
     * @param collection Items that compose the itemset.
     */
    public Itemset(int[] collection) {
        items = new int[Math.max(collection.length, 1)];
        for (int item : collection) {
            addItem(item);
        }
    }

    /**
     * Standard Itemset constructor
     */
    public Itemset() {
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
     * It adds an item to the itemset. The item is inserted in the last position.
     * @param value The item to add
     */
    public void addItem(int value) {
        ensureCapacity(size + 1);
        items[size++] = value;
    }

    /**
     * It adds an item to the itemset. The item is inserted in the specified
     * position.
     * @param i index where we want to add the item
     * @param value The item to add
     */
    public void addItem(int i, int value) {
        items[i] = value;
    }

    /**
     * It removes the specified item.
     * @param value The item to remove
     */
    public void removeItem(int value) {
        for (int i = 0; i < size; i++) {
            if (items[i] == value) {
                removeItemAt(i);
                return;
            }
        }
    }

    /**
     * It removes the item which appears in the specified index.
     * @param i the index
     * @return the removed item
     */
    public int removeItemAt(int i) {
        int removed = items[i];
        int numMoved = size - i - 1;
        if (numMoved > 0) {
            System.arraycopy(items, i + 1, items, i, numMoved);
        }
        size--;
        return removed;
    }

    /**
     * It returns the items that compose the itemset.
     * @return the list of items
     */
    public List<Integer> getItems() {
        List<Integer> result = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            result.add(items[i]);
        }
        return result;
    }

    /**
     * It returns the item from the specified position.
     * @param index The index where is the item in which we are interested.
     * @return the item
     */
    public int get(int index) {
        return items[index];
    }

    /**
     * Get the string representation of this itemset.
     * @return the string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append(items[i]);
            sb.append(' ');
        }
        return sb.toString();
    }
    
    /**
     * It clones the itemset.
     * @return The clone itemset.
     */
    public Itemset cloneItemset() {
        Itemset itemset = new Itemset();
        itemset.items = Arrays.copyOf(items, size);
        itemset.size = size;
        return itemset;
    }

    /**
     * It returns the number of items that compose the itemset.
     * @return the number of items 
     */
    public int size() {
        return size;
    }

    /**
     * It clones the itemset, translating each remaining item to its dense
     * id and ignoring the items that are non-frequent (i.e. absent from the
     * remap). This replaces the former lookup against a Map<Integer,BitSet>
     * used only to test presence, since we now also need the translation.
     * @param itemRemap map from original item id to its dense id
     * @return the clone
     */
    Itemset cloneItemSetMinusItems(MapIntToInt itemRemap) {
        Itemset itemset = new Itemset();
        for (int i = 0; i < size; i++) {
            int denseId = itemRemap.get(items[i]);
            if (denseId != -1) {
                itemset.addItem(denseId);
            }
        }
        return itemset;
    }

    /**
     * It gives direct access to the backing array (only the first size()
     * positions are valid). Used in hot paths (e.g. binary search) to avoid
     * boxing and defensive copies.
     * @return the backing array
     */
    int[] getRawItems() {
        return items;
    }
    
    /**
     * Check if this itemset is equal to another
     * Two itemsets are equals if in each position they have the same item.
     * @param arg the other itemset
     * @return true if equal, otherwise false.
     */
    @Override
    public boolean equals(Object arg){
        Itemset param=(Itemset)arg;
        if((param.size())!=(this.size())) {
            return false;
        }
        for(int i=0;i<size;i++){
            if(items[i] != param.items[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Arrays.hashCode(Arrays.copyOf(items, size));
        return hash;
    }
}