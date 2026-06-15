package ca.pfv.spmf.algorithms.frequentpatterns.clostream;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;

/**
 * This is an implementation of the CloStream algorithm for mining closed
 * itemsets from a stream as proposed by S.J Yen et al. (2009) in the
 * proceedings of the IEA-AIE 2009 conference, pp. 773.
 * <br/><br/>
 *
 * It is a very simple algorithm that does not use a minimum support threshold.
 * It thus finds all closed itemsets.
 * <br/><br/>
 *
 * This implementation includes the following optimizations:
 * <ul>
 *   <li>A dedicated {@link ItemsetKey} class wrapping a sorted int[] with
 *       proper hashCode/equals to allow O(1) HashMap lookups.</li>
 *   <li>The cid-processing loop is inlined into the item-scan loop,
 *       eliminating a separate collection-and-iterate pass.</li>
 * </ul>
 *
 * @see Itemset
 * @author Philippe Fournier-Viger
 */
public class AlgoCloSteam {
	
    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /**
     * The table of closed itemsets discovered so far.
     * Position 0 is always the empty set (whose support equals the number
     * of transactions seen).
     */
    List<Itemset> tableClosed = new ArrayList<Itemset>();

    /**
     * Maps each item to the list of cids (indices into {@link #tableClosed})
     * of the closed itemsets that contain that item.
     */
    Map<Integer, List<Integer>> cidListMap = new HashMap<Integer, List<Integer>>();

    /**
     * Reusable set of cids already processed during the current call to
     * Declared as a field to avoid allocating a new HashSet on every
     * transaction. It is cleared at the start of each call. Its size at any
     * point is bounded by the number of distinct cids reachable from the
     * items of the current transaction, which is small regardless of how
     * long the stream has been running.
     */
    private final Set<Integer> visitedCids = new HashSet<Integer>();

    // -----------------------------------------------------------------------
    // Inner class
    // -----------------------------------------------------------------------

    /**
     * Immutable key wrapping a sorted int[] itemset for use in a
     * {@link HashMap}.
     * <br/>
     * {@link Arrays#hashCode} and {@link Arrays#equals} are used for hashing
     * and equality, which is correct because itemsets are assumed to be stored
     * in ascending sorted order.
     */
    private static final class ItemsetKey {

        /** The underlying sorted item array. */
        final int[] items;

        /** Pre-computed hash code, cached to avoid recomputation on every lookup. */
        final int hash;

        /**
         * Constructs an ItemsetKey for the given sorted item array.
         *
         * @param items a sorted array of integer items
         */
        ItemsetKey(int[] items) {
            this.items = items;
            this.hash  = Arrays.hashCode(items);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return hash;
        }

        /**
         * Two ItemsetKey instances are equal iff their underlying int[]
         * arrays are equal element-by-element (i.e. they represent the same
         * sorted itemset).
         *
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemsetKey)) return false;
            return Arrays.equals(items, ((ItemsetKey) o).items);
        }
    }

    // -----------------------------------------------------------------------
    // Methods
    // -----------------------------------------------------------------------

    /**
     * Constructs and initialises the CloStream algorithm.
     * The empty set is inserted into the closed-itemset table with support 0;
     * its support is incremented by one for every transaction processed.
     */
    public AlgoCloSteam() {
        Itemset emptySet = new Itemset(new int[]{});
        emptySet.setAbsoluteSupport(0);
        tableClosed.add(emptySet);
    }

    /**
     * Processes a new transaction arriving from the stream and updates the
     * set of closed itemsets accordingly.
     * <br/>
     * This method implements the CloStream update procedure (Algorithm 1 in
     * the original paper) with the optimizations described in the class
     * Javadoc.
     *
     * @param transaction the new transaction to process, whose items must be
     *                    stored in ascending sorted order
     */
    public void processNewTransaction(Itemset transaction) {

        // Get the items from the transaction
        int[] txItems = transaction.getItems();

        // Build tableTemp: maps each distinct intersection found so far to
        // the cid of the highest-support closed itemset that produced it.
        // Seeded with the transaction itself mapped to cid 0 (the empty set).
        Map<ItemsetKey, Integer> tableTemp = new HashMap<ItemsetKey, Integer>();
        tableTemp.put(new ItemsetKey(txItems), 0);

        // Clear the reusable visited-cid set from the previous call.
        visitedCids.clear();

        // Iterate over every item in the transaction; for each cid in that
        // item's cid-list, process the cid exactly once (guarded by
        // visitedCids). 
        for (int item : txItems) {

            List<Integer> cidlist = cidListMap.get(item);
            if (cidlist == null) {
                continue;
            }

            for (int cid : cidlist) {

                // Skip this cid if it was already processed in this call.
                if (!visitedCids.add(cid)) {
                    continue;
                }

                // Compute the intersection of the stored closed itemset
                // and the current transaction.
                Itemset cti           = tableClosed.get(cid);
                Itemset intersectionS = (Itemset) transaction.intersection(cti);
                ItemsetKey key        = new ItemsetKey(intersectionS.getItems());

                // O(1) lookup: is this intersection already in tableTemp?
                Integer existingCid = tableTemp.get(key);

                if (existingCid != null) {
                    // Already present: keep whichever cid has higher support.
                    if (cti.getAbsoluteSupport() > tableClosed.get(existingCid).getAbsoluteSupport()) {
                        tableTemp.put(key, cid);
                    }
                } else {
                    // New intersection: record it.
                    tableTemp.put(key, cid);
                }
            }
        }

        // Apply the updates described by tableTemp to tableClosed.
        for (Map.Entry<ItemsetKey, Integer> xc : tableTemp.entrySet()) {

            int[]   xItems = xc.getKey().items;
            Itemset ctc    = tableClosed.get(xc.getValue());

            if (Arrays.equals(xItems, ctc.getItems())) {
                // The intersection equals an existing closed itemset:
                // simply increment its support.
                ctc.increaseTransactionCount();

            } else {
                // The intersection is a genuinely new closed itemset.
                Itemset x = new Itemset(xItems);
                x.setAbsoluteSupport(ctc.getAbsoluteSupport() + 1);
                tableClosed.add(x);

                int newCid = tableClosed.size() - 1;

                // Register the new cid in the cid-list of every transaction item.
                for (int item : txItems) {
                    List<Integer> cidlist = cidListMap.get(item);
                    if (cidlist == null) {
                        cidlist = new ArrayList<Integer>();
                        cidListMap.put(item, cidlist);
                    }
                    cidlist.add(newCid);
                }
            }
        }
    }

    /**
     * Returns the current list of closed itemsets, excluding the empty set.
     * <br/>
     * Note: this method modifies {@link #tableClosed} in place by removing
     * the empty set if it is still present at position 0.
     *
     * @return the list of non-empty closed itemsets discovered so far
     */
    public List<Itemset> getClosedItemsets() {
        if (tableClosed.get(0).size() == 0) {
            tableClosed.remove(0);
        }
        return tableClosed;
    }
}