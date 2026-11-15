package ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (c) Illyes Hamimid et al. 2026.
 *
 * Adapted from the LPPNode class of the LPP-Growth implementation by Peng Yang (SPMF).
 * 
 * Do not remove the copyright and license information.
 */

/**
 * The PPFP-tree used by {@link AlgoPPFPgrowth}: an FP-tree (Han et al., 2000)
 * whose nodes also keep the existential probability of their item, and whose
 * tail nodes keep the TIDs of the transactions ending there (as in the
 * PF-tree of Tanbeer et al., 2009).
 * <p>
 * A conditional tree (the tree of the transactions containing a suffix
 * pattern beta) has the same structure. Its nodes keep the probabilities of
 * their own items; the probability E<sub>t</sub>(beta) of the suffix in each
 * transaction t is kept separately in {@link #prefixProbabilities}, so that the
 * existential probability of beta &cup; {x} in t is
 * E<sub>t</sub>(x) &times; E<sub>t</sub>(beta) (items are independent).
 *
 * @see PPFNode
 * @see AlgoPPFPgrowth
 */
public class PPFTree {

    /** items of the header table, in the order of the tree (descending expected support) */
    List<Integer> headerList = null;

    /** first node of each item in the tree (start of its node-link chain) */
    Map<Integer, PPFNode> mapItemNodes = new HashMap<Integer, PPFNode>();

    /** last node of each item in its node-link chain */
    Map<Integer, PPFNode> mapItemLastNode = new HashMap<Integer, PPFNode>();

    /** root of the tree (null item) */
    PPFNode root = new PPFNode();

    /** number of nodes created in this tree, for statistics */
    int numberOfNodes = 0;

    /**
     * existential probability of the suffix pattern of this conditional tree in each transaction
     * (key: TID); empty for the initial tree, whose suffix is the empty pattern (probability 1)
     */
    private final Map<Integer, Double> prefixProbabilities = new HashMap<Integer, Double>();

    /** Create an empty tree. */
    public PPFTree() {
    }

    /**
     * Probability of the suffix pattern of this tree in a transaction.
     *
     * @param tid a TID of a transaction of this tree
     * @return the probability (1 for the initial tree)
     */
    double getPrefixProbability(int tid) {
        Double probability = prefixProbabilities.get(tid);
        return probability == null ? 1.0 : probability;
    }

    /**
     * Insert a transaction in the initial tree.
     *
     * @param items         items of the transaction, sorted by {@link #supportDescendingOrder}
     * @param probabilities existential probability of each item (same order)
     * @param tid           the TID of the transaction
     */
    void addTransaction(List<Integer> items, List<Double> probabilities, int tid) {
        PPFNode currentNode = root;
        for (int i = 0; i < items.size(); i++) {
            currentNode = getOrCreateChild(currentNode, items.get(i), probabilities.get(i));
        }
        currentNode.tids.add(tid);
    }

    /**
     * Insert a prefix path of a parent tree into this conditional tree.
     * Items that are not in the PPFP-list of the conditional tree are skipped.
     *
     * @param prefixPath the tail node of the suffix item in the parent tree,
     *                   followed by its ancestors (bottom-up, root excluded)
     * @param ppfpList   the PPFP-list of the conditional pattern
     * @param parentTree the tree containing the prefix path
     */
    void addPrefixPath(List<PPFNode> prefixPath, Map<Integer, PPFPListEntry> ppfpList, PPFTree parentTree) {
        PPFNode tailNode = prefixPath.get(0);
        PPFNode currentNode = root;
        for (int i = prefixPath.size() - 1; i >= 1; i--) {
            PPFNode pathItem = prefixPath.get(i);
            if (ppfpList.containsKey(pathItem.itemID)) {
                currentNode = getOrCreateChild(currentNode, pathItem.itemID, pathItem.probability);
            }
        }
        if (currentNode.itemID != PPFNode.ROOT_ITEM) {
            currentNode.tids.addAll(tailNode.tids);
            // the new suffix is the old one plus the tail item
            for (Integer tid : tailNode.tids) {
                prefixProbabilities.put(tid, tailNode.probability * parentTree.getPrefixProbability(tid));
            }
        }
    }

    /**
     * Return the child of a node for an item and probability, creating it if needed.
     *
     * @param node        the parent node
     * @param item        the item of the child
     * @param probability the existential probability of the child
     * @return the existing or new child
     */
    private PPFNode getOrCreateChild(PPFNode node, int item, double probability) {
        PPFNode child = node.getChild(item, probability);
        if (child == null) {
            numberOfNodes++;
            child = new PPFNode();
            child.itemID = item;
            child.probability = probability;
            child.parent = node;
            node.children.add(child);
            fixNodeLinks(item, child);
        }
        return child;
    }

    /**
     * Append a new node to the node-link chain of its item.
     *
     * @param item    the item of the node
     * @param newNode the new node
     */
    private void fixNodeLinks(Integer item, PPFNode newNode) {
        PPFNode lastNode = mapItemLastNode.get(item);
        if (lastNode != null) {
            lastNode.nodeLink = newNode;
        }
        mapItemLastNode.put(item, newNode);
        if (!mapItemNodes.containsKey(item)) {
            mapItemNodes.put(item, newNode);
        }
    }

    /**
     * Create the header table.
     *
     * @param parentHeaderList the header list of the parent tree, or null for the initial tree
     * @param ppfpList         the PPFP-list of the items of this tree
     */
    void createHeaderList(List<Integer> parentHeaderList, Map<Integer, PPFPListEntry> ppfpList) {
        if (parentHeaderList == null) {
            headerList = new ArrayList<Integer>(mapItemNodes.keySet());
            Collections.sort(headerList, supportDescendingOrder(ppfpList));
        } else {
            // keep the order of the parent tree, which is the order of the items on the paths
            headerList = new ArrayList<Integer>();
            for (int item : parentHeaderList) {
                if (ppfpList.containsKey(item)) {
                    headerList.add(item);
                }
            }
        }
    }

    /**
     * Remove the last item of the header table from the tree, moving the TIDs
     * of its (tail) nodes to their parents.
     */
    void removeTailItem() {
        int tail = headerList.size() - 1;
        PPFNode tailNode = mapItemNodes.remove(headerList.get(tail));
        headerList.remove(tail);
        while (tailNode != null) {
            PPFNode parent = tailNode.parent;
            parent.removeChild(tailNode);
            if (parent.itemID != PPFNode.ROOT_ITEM) {
                parent.tids.addAll(tailNode.tids);
            }
            tailNode = tailNode.nodeLink;
        }
    }

    /**
     * The order of the items in the tree: descending expected support, ties
     * broken by ascending item id. The same total order must be used to sort
     * the transactions and the header table; otherwise a tail item is not
     * always a leaf when it is removed and occurrences are lost.
     *
     * @param ppfpList the PPFP-list giving the expected support of each item
     * @return the comparator
     */
    static Comparator<Integer> supportDescendingOrder(final Map<Integer, PPFPListEntry> ppfpList) {
        return new Comparator<Integer>() {
            @Override
            public int compare(Integer item1, Integer item2) {
                int compare = Double.compare(ppfpList.get(item2).expectedSupport, ppfpList.get(item1).expectedSupport);
                return compare != 0 ? compare : Integer.compare(item1, item2);
            }
        };
    }
}
