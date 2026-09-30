package ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast;

import java.util.Arrays;
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
 * This class represents a sequence from a projected database (as based in PrefixSpan).
 * Since it is a projected sequence, it makes reference to the original sequence.
 * 
 * This class is inspired in SPMF PrefixSpan implementation.
 *
 * Copyright Antonio Gomariz Peñalver 2013
 *
 * @author agomariz
 */
public class PseudoSequence {

    /**
     * Sequence where the different projections of the pseudosequence refer
     */
    private Sequence sequence;
    /**
     * Array with the first itemset where each projection starts.
     */
    private int[] firstItemset = new int[1];
    /**
     * Array with the first item where each projection starts
     */
    private int[] firstItem = new int[1];
    /**
     * Number of projections actually stored in the arrays above
     */
    private int projectionCount = 0;

    /**
     * No-arg constructor used together with the object pool in
     * AlgoPrefixSpan_AGP: an instance created this way is not usable until
     * initFromProjection or initFromSequence is called on it.
     */
    PseudoSequence() {
    }

    /**
     * It make a pseudosequence from a previous pseudosequence. Therefore, we
     * are making a projection in a previous projection and we have to take into
     * account the previous index of that projection to establish the ours.
     * @param pseudosequence The pseudosequence where we make another projection
     * @param itemsetIndex The itemset index where our new projection is starting
     * @param itemIndex The item index where our new projection is starting
     * @param firstItemset An index that indicates in which projection of the 
     * previous pseudoseuqence we are making the new pseudosequence
     */
    public PseudoSequence(PseudoSequence pseudosequence, int itemsetIndex, int itemIndex, int firstItemset) {
        initFromProjection(pseudosequence, itemsetIndex, itemIndex, firstItemset);
    }

    /**
     * PseudoSequence made from a starndard sequence. Its purpose is make possible
     * the later projections
     * @param sequence The original standard sequence
     * @param itemsetIndex The itemset index where the projection it starts
     * @param itemIndex The item index where the projection it starts
     */
    public PseudoSequence(Sequence sequence, int itemsetIndex, int itemIndex) {
        initFromSequence(sequence, itemsetIndex, itemIndex);
    }

    /**
     * (Re)initializes this pseudosequence - possibly recycled from the
     * object pool - as a projection of a previous pseudosequence. See the
     * matching constructor for the meaning of the parameters.
     */
    void initFromProjection(PseudoSequence pseudosequence, int itemsetIndex, int itemIndex, int firstItemset) {
        this.sequence = pseudosequence.sequence;
        int base = pseudosequence.firstItemset[firstItemset];
        ensureCapacity(1);
        this.firstItemset[0] = itemsetIndex + base;
        if (this.firstItemset[0] == base) {
            this.firstItem[0] = itemIndex + pseudosequence.firstItem[firstItemset];
        } else {
            this.firstItem[0] = itemIndex;
        }
        this.projectionCount = 1;
    }

    /**
     * (Re)initializes this pseudosequence - possibly recycled from the
     * object pool - from a standard sequence. See the matching constructor
     * for the meaning of the parameters.
     */
    void initFromSequence(Sequence sequence, int itemsetIndex, int itemIndex) {
        this.sequence = sequence;
        ensureCapacity(1);
        this.firstItemset[0] = itemsetIndex;
        this.firstItem[0] = itemIndex;
        this.projectionCount = 1;
    }
    
    

    /**
     * It grows the backing arrays so they can hold at least minCapacity projections.
     * @param minCapacity the minimum capacity needed
     */
    private void ensureCapacity(int minCapacity) {
        if (minCapacity > firstItemset.length) {
            int newCapacity = Math.max(firstItemset.length * 2, minCapacity);
            firstItemset = Arrays.copyOf(firstItemset, newCapacity);
            firstItem = Arrays.copyOf(firstItem, newCapacity);
        }
    }

    /**
     * Method to make a projection in a pseudosequence already projected. 
     * In this way, for a same pseudosequence, we can have several 
     * projections. For example, let us think in a sequence A = (a) (a b) (a b c).
     * If we project by item a, we obtain three different projections:
     * 1)   (a b) (a b c)
     * 2)   (*b) (a b c)
     * 3)   (*b c)
     * That we do is to put all the three projections in the same pseudoseuqence,
     * and we refers each projection with an index. We call to every projection, 
     * projection point.
     * @param firstItemset The projection point of the given pseudosequence 
     * where we are making our new projection
     * @param pseudosequence The given pseudosequence where we are making our 
     * projection
     * @param itemsetIndex  The itemset index where our projection it starts
     * @param itemIndex The item index where our projection it starts
     */
    public void addProjectionPoint(int firstItemset, PseudoSequence pseudosequence, int itemsetIndex, int itemIndex) {
        ensureCapacity(projectionCount + 1);
        /*
         * We establish the starting itemset index (for our projection), from the
         * previous starting itemset index
         */
        int base = pseudosequence.firstItemset[firstItemset];
        int newFirstItemset = itemsetIndex + base;
        this.firstItemset[projectionCount] = newFirstItemset;
        //If our itemset index is the same index in the previous pseudosequence
        if (newFirstItemset == base) {
            //We compose the item index from the previous one
            this.firstItem[projectionCount] = itemIndex + pseudosequence.firstItem[firstItemset];
        } else {
            //Otherwise we simply use the given item index
            this.firstItem[projectionCount] = itemIndex;
        }
        projectionCount++;
    }

    /**
     * It returns the first itemset of the projection, in absolute terms
     * @param index the ith projection in which we want to know the first itemset
     * @return the item at position index in the first itemset
     */
    public int getFirstItemset(int index){
        return this.firstItemset[index];
    }

    /**
     * It returns the size for the current pseudosequence
     * @param i the ith projection in which we want to know the size
     * @return the size
     */
    public int size(int i) {
        int size = sequence.size() - firstItemset[i];
        return size;
    }

    /**
     * It returns the number of projections included in this pseudosequence
     * @return the number of projections
     */
    public int numberOfProjectionsIncluded() {
        return projectionCount;
    }

    /**
     * It returns the size of the itemset in a specified projection of the 
     * sequence
     * @param firstItemset the ith projection in which we are interested
     * @param index The ith itemset of the current pseudosequence
     * @return the size 
     */
    public int getSizeOfItemsetAt(int firstItemset, int index) {
        //we get the itemset size in the original standard sequence
        int size = sequence.getItemsets().get(index + this.firstItemset[firstItemset]).size();
        //And if that itemset if where the projection starts
        if (isFirstItemset(index)) {
            //We substract the starting index
            size -= this.firstItem[firstItemset];
        }
        return size;
    }

    /**
     * It returns the first item of the first itemset of the specified projection
     * @param firstItem the specified projection in which we are interested
     * @param itemsetIndex the itemset of the pseudosequence in which we are interested
     * @return the item
     */
    public int getBeginningOfItemset(int firstItem, int itemsetIndex){
        if(isFirstItemset(itemsetIndex)){
            return this.firstItem[firstItem];
        }
        return 0;
    }

    /**
     * It informs if a projection it starts in the midle of an itemset
     * @param firstItem The projection in which we are interested
     * @param itemsetIndex The itemset to study
     * @return true if it does. otherwise, false.
     */
    public boolean isPostfix(int firstItem, int itemsetIndex) {
        return isFirstItemset(itemsetIndex) && this.firstItem[firstItem] != 0;
    }

    /**
     * To know if an itemset is the first one
     * @param index the position of the itemset
     * @return true if it is the first one, otherwise false
     */
    public boolean isFirstItemset(int index) {
        return index == 0;
    }

    /**
     * Get a given item of an itemset
     * @param firstItem The projection in which are interested
     * @param itemIndex The item index
     * @param itemsetIndex The itemset index
     * @return the item
     */
    public int getItemAtInItemsetAt(int firstItem, int itemIndex, int itemsetIndex) {

        if (isFirstItemset(itemsetIndex)) {
            return getItemset(itemsetIndex, firstItem).get(itemIndex + this.firstItem[firstItem]);
        } else {
            return getItemset(itemsetIndex, firstItem).get(itemIndex);
        }
    }

    /**
     * It gets the itemset in the original sequence.
     * @param itemsetIndex the itemset index
     * @param firstitemset the projection in which we are interested
     * @return the itemset
     */
    public Itemset getItemset(int itemsetIndex, int firstitemset) {
        return sequence.get(itemsetIndex + firstItemset[firstitemset]);
    }

    /**
     * Get the pseudosequence ID
     * @return  the ID
     */
    public int getId() {
        return sequence.getId();
    }

    /**
     * Get the string representation of this  pseudosequence
     * @return the string representation
     */
    @Override
    public String toString() {
        StringBuilder r = new StringBuilder();
        for (int k = 0; k < projectionCount; k++) {
            for (int i = 0; i < size(k); i++) {
                r.append('{');
                for (int j = 0; j < getSizeOfItemsetAt(k, i); j++) {
                    r.append(getItemAtInItemsetAt(k, j, i));
                    if (isPostfix(k, i)) {
                        r.append('*');
                    }
                    r.append(' ');
                }
                r.append("}");
            }
            r.append("\n");
        }
        return r.toString();
    }

    /**
     * It returns the index where an item appears in the specified itemset of the
     * specified projection of the current pseudosequence. If the item does not
     * appear, it returns -1.
     * @param firstItemset the index of the firstItemset to search
     * @param itemsetIndex the current itemset index
     * @param item the item
     * @return the index or -1 if the item does not appear
     */
    public int indexOf(int firstItemset, int itemsetIndex, int item) {
        //We get the itemset
        Itemset itemset = getItemset(itemsetIndex,firstItemset);
        //We get its starting point with respect to the original itemset
        int beginning = getBeginningOfItemset(firstItemset, itemsetIndex);
        int[] raw = itemset.getRawItems();
        int n = itemset.size();
        //For small itemsets a linear scan beats Arrays.binarySearch: the
        //per-call overhead and the lack of branch prediction dominate below
        //~8 elements, which covers the overwhelming majority of itemsets
        if (n <= 8) {
            for (int i = beginning; i < n; i++) {
                int v = raw[i];
                if (v == item) {
                    //we return it, substracting the beginning of the itemset
                    return i - beginning;
                }
                if (v > item) {
                    return -1;
                }
            }
            return -1;
        }
        //We search for the item directly on the backing array, avoiding boxing
        int i = Arrays.binarySearch(raw, 0, n, item);
        //If the index exist, and it does not appear before the beginning
        if(i>=beginning) {
            //we return it, substracting the beinning of the itemset
            return i-beginning;
        }
        //Otherwise we return -1
        return -1;
    }
}