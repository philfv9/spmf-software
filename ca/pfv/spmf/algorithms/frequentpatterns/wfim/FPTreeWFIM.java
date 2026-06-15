package ca.pfv.spmf.algorithms.frequentpatterns.wfim;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
*
* Do not remove copyright or license information from this file.
*/

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class represents an FP-tree used by the WFIM algorithm.
 *
 * Items are inserted in weight-ascending order as required by the WFIM
 * paper. The lowest-weight item therefore appears closest
 * to the root, and the highest-weight item appears deepest. Mining proceeds
 * bottom-up (highest-weight item first), which guarantees that the minimum
 * weight (MinW) of any conditional pattern prefix is always greater than or
 * equal to the weights of all items in its conditional database.
 *
 * Each node stores an item identifier, a count, a reference to its parent,
 * a map of child nodes keyed by item, and a node-link to the next FP-tree
 * node containing the same item (used during conditional path collection).
 *
 * A header table maps each item to the first node in its node-link chain,
 * and a companion tail map enables O(1) chain appends during insertion.
 * Per-item support totals are maintained incrementally for O(1) lookup.
 *
 * @see AlgoWFIM
 * @author Philippe Fournier-Viger
 */
public class FPTreeWFIM {

    /**
     * A single node in the FP-tree.
     */
    static class FPNode {

        /** the item stored at this node (-1 for the root) */
        final int item;

        /** the accumulated count at this node */
        int count;

        /** the parent node (null for the root) */
        final FPNode parent;

        /** child nodes keyed by item identifier */
        final Map<Integer, FPNode> children;

        /** node-link to the next FP-tree node with the same item */
        FPNode nodeLink;

        /**
         * Constructs an FP-tree node with the given item, count, and parent.
         *
         * @param item   the item stored at this node (-1 for the root)
         * @param count  the initial count
         * @param parent the parent node
         */
        FPNode(int item, int count, FPNode parent) {
            this.item     = item;
            this.count    = count;
            this.parent   = parent;
            this.children = new HashMap<>();
        }
    }

    /** the root node of the FP-tree (item = -1, count = 0) */
    private final FPNode root;

    /** header table: maps each item to the first node in its node-link chain */
    private final Map<Integer, FPNode> headerTable;

    /** tail map: maps each item to the last node in its node-link chain */
    private final Map<Integer, FPNode> headerTail;

    /** per-item support totals, maintained incrementally during insertions */
    private final Map<Integer, Integer> itemSupport;

    /**
     * Constructs an empty FP-tree initialized for the given ordered list of items.
     *
     * @param items       the items that may appear in this tree
     * @param weightTable the item weight table (unused at construction; reserved for future use)
     */
    public FPTreeWFIM(List<Integer> items, Map<Integer, Double> weightTable) {
        int capacity = items.size() * 2;
        root        = new FPNode(-1, 0, null);
        headerTable = new HashMap<>(capacity);
        headerTail  = new HashMap<>(capacity);
        itemSupport = new HashMap<>(capacity);
        for (int item : items) itemSupport.put(item, 0);
    }

    /**
     * Inserts a transaction pre-sorted in weight-ascending order with count 1.
     *
     * @param transaction the sorted, filtered list of items to insert
     */
    public void insertTransaction(List<Integer> transaction) {
        insertTransactionWithCount(transaction, 1);
    }

    /**
     * Inserts a transaction pre-sorted in weight-ascending order with the given multiplicity count.
     *
     * @param transaction the sorted, filtered list of items to insert
     * @param count       the multiplicity count for this transaction
     */
    public void insertTransactionWithCount(List<Integer> transaction, int count) {
        FPNode current = root;
        for (int item : transaction) {
            FPNode child = current.children.get(item);
            if (child == null) {
                child = new FPNode(item, count, current);
                current.children.put(item, child);
                FPNode tail = headerTail.get(item);
                if (tail == null) {
                    headerTable.put(item, child);
                } else {
                    tail.nodeLink = child;
                }
                headerTail.put(item, child);
            } else {
                child.count += count;
            }
            itemSupport.merge(item, count, Integer::sum);
            current = child;
        }
    }

    /**
     * Returns the total support count of the given item across all nodes in the tree.
     *
     * @param item the item to query
     * @return the support count, or 0 if the item is not present
     */
    public int getSupport(int item) {
        Integer sup = itemSupport.get(item);
        return (sup != null) ? sup : 0;
    }

    /**
     * Builds the conditional database (projected path list) for the given item.
     *
     * <p>For each FP-tree node containing the item (traversed via node links),
     * the prefix path from that node's parent up to (but not including) the
     * root is collected. The returned list contains one int[] per path, where
     * index 0 holds the path multiplicity count and indices 1.. hold item
     * identifiers in root-to-node order (weight-ascending, as inserted).</p>
     *
     * @param item the item whose conditional database is to be built
     * @return list of conditional paths, each as int[] {count, item1, item2, ...}
     */
    public List<int[]> buildConditionalPaths(int item) {
        List<int[]> paths = new ArrayList<>();
        FPNode node = headerTable.get(item);
        while (node != null) {
            if (node.count > 0) {
                // Walk from node.parent up to (not including) the root,
                // collecting items in leaf-to-root order.
                List<Integer> path = new ArrayList<>();
                FPNode ancestor = node.parent;
                while (ancestor != null && ancestor.item != -1) {
                    path.add(ancestor.item);
                    ancestor = ancestor.parent;
                }
                if (!path.isEmpty()) {
                    // path is currently in leaf-to-root order; reverse to root-to-leaf
                    int[] pathArr = new int[path.size() + 1];
                    pathArr[0] = node.count;
                    for (int k = 0; k < path.size(); k++) {
                        pathArr[k + 1] = path.get(path.size() - 1 - k);
                    }
                    paths.add(pathArr);
                }
            }
            node = node.nodeLink;
        }
        return paths;
    }
}