package ca.pfv.spmf.algorithms.frequentpatterns.merit;
/*
 * This file is copyright (c) 2008-2013 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 * Do not remove copyright or license information from this file.
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is an implementation of the MERIT+ algorithm for erasable itemset mining.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoMERIT {

    // -----------------------------------------------------------------------
    //  Inner classes
    // -----------------------------------------------------------------------

    /**
     * A node in the WPPC-tree.
     * Each node stores the item name, accumulated gain weight, pre-order and
     * post-order traversal numbers, and references to parent and children.
     */
    static class WPPCNode {
        /** the item identifier stored at this node */
        int itemName;

        /** the accumulated profit weight of products routed through this node */
        double weight;

        /** the pre-order traversal number (top-down, left-to-right) */
        int preOrder;

        /** the post-order traversal number (bottom-up, left-to-right) */
        int postOrder;

        /** the parent node in the WPPC-tree, null for root */
        WPPCNode parent;

        /** the list of child nodes in the WPPC-tree */
        List<WPPCNode> children = new ArrayList<WPPCNode>();

        /**
         * Create a WPPC-tree node with the given item name, weight, and parent.
         *
         * @param itemName the item stored at this node
         * @param weight   the profit weight contributed by the inserting product
         * @param parent   the parent node in the tree
         */
        WPPCNode(int itemName, double weight, WPPCNode parent) {
            this.itemName = itemName;
            this.weight   = weight;
            this.parent   = parent;
        }
    }

    /**
     * A node code representing one occurrence of an item in the WPPC-tree.
     */
    static class NodeCode {
        /** pre-order traversal number of the WPPC-tree node */
        int pre;

        /** post-order traversal number of the WPPC-tree node */
        int post;

        /** profit weight of the WPPC-tree node */
        double weight;

        /**
         * Create a node code with the given pre-order, post-order, and weight.
         *
         * @param pre    pre-order number
         * @param post   post-order number
         * @param weight profit weight
         */
        NodeCode(int pre, int post, double weight) {
            this.pre    = pre;
            this.post   = post;
            this.weight = weight;
        }
    }

    /**
     * Represents one erasable itemset together with its NC_Set and current gain.
     * Used as elements of the equivalence classes during depth-first search.
     */
    static class ErasableItemset {
        /** the items in this erasable itemset as a sorted integer list */
        List<Integer> items;

        /** the NC_Set: list of node codes sorted in descending order of pre-order */
        List<NodeCode> ncSet;

        /** the current gain (sum of weights in ncSet) */
        double gain;

        /**
         * Create an erasable itemset with the given items, NC_Set, and gain.
         *
         * @param items the item list (will be stored by reference)
         * @param ncSet the NC_Set (will be stored by reference)
         * @param gain  the precomputed gain
         */
        ErasableItemset(List<Integer> items, List<NodeCode> ncSet, double gain) {
            this.items = items;
            this.ncSet = ncSet;
            this.gain  = gain;
        }
    }

    // -----------------------------------------------------------------------
    //  Fields
    // -----------------------------------------------------------------------

    /** start time of the latest execution */
    long startTimestamp = 0;

    /** end time of the latest execution */
    long endTimeStamp = 0;

    /** the overall profit of all products (Sum_val) */
    double totalProfit = 0;

    /** the maximum gain allowed for an itemset to be erasable */
    double maxGain = 0;

    /** the number of erasable itemsets found */
    private int erasableCount = 0;

    /** object to write the output file */
    BufferedWriter writer = null;

    /** the maximum size of itemsets to be discovered */
    int maxItemsetSize = Integer.MAX_VALUE;

    /** pre-order counter used during WPPC-tree traversal */
    private int preCounter = 0;

    /** post-order counter used during WPPC-tree traversal */
    private int postCounter = 0;

    // -----------------------------------------------------------------------
    //  Constructor
    // -----------------------------------------------------------------------

    /**
     * Default constructor.
     */
    public AlgoMERIT() {
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    /**
     * Run the MERIT+ algorithm on a product database.
     *
     * @param input     path to the input file
     * @param output    path to the output file
     * @param threshold the profit-loss threshold xi (e.g. 0.15 for 15%)
     * @throws IOException           if an error occurs reading or writing files
     * @throws NumberFormatException if the file contains invalid numbers
     */
    public void runAlgorithm(String input, String output, double threshold)
            throws NumberFormatException, IOException {

        startTimestamp = System.currentTimeMillis();
        writer         = new BufferedWriter(new FileWriter(output));
        erasableCount  = 0;
        totalProfit    = 0;
        preCounter     = 0;
        postCounter    = 0;

        // --- FIRST SCAN: compute total profit ---
        BufferedReader reader = new BufferedReader(new FileReader(input));
        String line;
        while ((line = reader.readLine()) != null) {
            if (isComment(line)) continue;
            String[] tok = line.split(" ");
            totalProfit += Integer.parseInt(tok[0]);
        }
        reader.close();
        maxGain = totalProfit * threshold;

        // --- SECOND SCAN: find erasable 1-itemsets (items whose individual
        //     gain <= maxGain) and compute item frequencies for ordering ---
        // item gain = sum of profits of products containing that item
        Map<Integer, Double> itemGain = new HashMap<Integer, Double>();
        // we also need item frequency (number of products) for ordering
        Map<Integer, Integer> itemFreq = new HashMap<Integer, Integer>();

        reader = new BufferedReader(new FileReader(input));
        List<int[]>    rawItems   = new ArrayList<int[]>();
        List<Double>   rawProfits = new ArrayList<Double>();
        while ((line = reader.readLine()) != null) {
            if (isComment(line)) continue;
            String[] tok    = line.split(" ");
            double   profit = Integer.parseInt(tok[0]);
            int[]    items  = new int[tok.length - 1];
            for (int j = 1; j < tok.length; j++) {
                items[j - 1] = Integer.parseInt(tok[j]);
            }
            rawProfits.add(profit);
            rawItems.add(items);
            for (int item : items) {
                Double g = itemGain.get(item);
                itemGain.put(item, (g == null ? 0.0 : g) + profit);
                Integer f = itemFreq.get(item);
                itemFreq.put(item, (f == null ? 0 : f) + 1);
            }
        }
        reader.close();

        // collect erasable 1-itemsets
        List<Integer> erasable1 = new ArrayList<Integer>();
        for (Map.Entry<Integer, Double> e : itemGain.entrySet()) {
            if (e.getValue() <= maxGain) {
                erasable1.add(e.getKey());
            }
        }

        // sort erasable 1-itemsets in ascending order of frequency (paper's ordering)
        Collections.sort(erasable1, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int fa = itemFreq.get(a) == null ? 0 : itemFreq.get(a);
                int fb = itemFreq.get(b) == null ? 0 : itemFreq.get(b);
                if (fa != fb) return fa - fb;
                return a - b; // tie-break by item id
            }
        });

        // position map: item -> index in erasable1 (used as H1 in the paper)
        final Map<Integer, Integer> h1 = new HashMap<Integer, Integer>();
        for (int i = 0; i < erasable1.size(); i++) {
            h1.put(erasable1.get(i), i);
        }

        // save erasable 1-itemsets to output
        for (int item : erasable1) {
            List<Integer> singleton = new ArrayList<Integer>();
            singleton.add(item);
            saveItemsetToFile(singleton, itemGain.get(item));
        }

        if (erasable1.isEmpty()) {
            writer.close();
            endTimeStamp = System.currentTimeMillis();
            return;
        }

        // --- BUILD WPPC-TREE ---
        // Root node: item=-1, weight=0
        WPPCNode root = new WPPCNode(-1, 0, null);

        for (int t = 0; t < rawItems.size(); t++) {
            int[]  items  = rawItems.get(t);
            double profit = rawProfits.get(t);

            // keep only erasable items and sort by ascending frequency (h1 order)
            List<Integer> filteredSorted = new ArrayList<Integer>();
            for (int item : items) {
                if (h1.containsKey(item)) {
                    filteredSorted.add(item);
                }
            }
            if (filteredSorted.isEmpty()) continue;

            Collections.sort(filteredSorted, new Comparator<Integer>() {
                public int compare(Integer a, Integer b) {
                    return h1.get(a) - h1.get(b);
                }
            });

            // insert the sorted item list into the WPPC-tree
            insertIntoTree(root, filteredSorted, 0, profit);
        }

        // assign pre-order and post-order numbers via DFS
        assignOrders(root);

        // --- GENERATE NC_SETS FOR ERASABLE 1-ITEMSETS ---
        // For each erasable 1-itemset, collect all nodes in the tree with that
        // item name, sorted in descending order of pre-order.
        Map<Integer, List<NodeCode>> ncSets = new HashMap<Integer, List<NodeCode>>();
        for (int item : erasable1) {
            ncSets.put(item, new ArrayList<NodeCode>());
        }
        collectNodeCodes(root, ncSets);

        // sort each NC_Set in descending pre-order (Definition 6)
        for (List<NodeCode> ncSet : ncSets.values()) {
            Collections.sort(ncSet, new Comparator<NodeCode>() {
                public int compare(NodeCode a, NodeCode b) {
                    return b.pre - a.pre; // descending
                }
            });
        }

        // build ErasableItemset objects for the 1-itemsets
        List<ErasableItemset> e1List = new ArrayList<ErasableItemset>();
        for (int item : erasable1) {
            List<Integer>  its   = new ArrayList<Integer>();
            its.add(item);
            List<NodeCode> nc    = ncSets.get(item);
            double         gain  = computeGain(nc);
            e1List.add(new ErasableItemset(its, nc, gain));
        }

        // --- DEPTH-FIRST MINING ---
        if (e1List.size() > 1 && maxItemsetSize >= 2) {
            miningE(e1List);
        }

        writer.close();
        endTimeStamp = System.currentTimeMillis();
    }

    // -----------------------------------------------------------------------
    //  WPPC-tree construction
    // -----------------------------------------------------------------------

    /**
     * Insert a filtered and sorted item list for one product into the WPPC-tree.
     * Follows the path as far as it matches, then creates new child nodes.
     *
     * @param node    the current node in the tree
     * @param items   the filtered sorted item list for the product
     * @param idx     the current position in the item list
     * @param profit  the profit of the product
     */
    private void insertIntoTree(WPPCNode node, List<Integer> items,
                                int idx, double profit) {
        if (idx >= items.size()) return;
        int item = items.get(idx);

        // look for an existing child with this item name
        for (WPPCNode child : node.children) {
            if (child.itemName == item) {
                child.weight += profit;
                insertIntoTree(child, items, idx + 1, profit);
                return;
            }
        }
        // no matching child: create a new node
        WPPCNode newNode = new WPPCNode(item, profit, node);
        node.children.add(newNode);
        insertIntoTree(newNode, items, idx + 1, profit);
    }

    /**
     * Assign pre-order and post-order numbers to all nodes via recursive DFS.
     * The root is skipped (pre/post are only meaningful for item nodes).
     *
     * @param node the current node to process
     */
    private void assignOrders(WPPCNode node) {
        if (node.parent != null) {
            node.preOrder = ++preCounter;
        }
        for (WPPCNode child : node.children) {
            assignOrders(child);
        }
        if (node.parent != null) {
            node.postOrder = ++postCounter;
        }
    }

    /**
     * Recursively traverse the WPPC-tree and collect a NodeCode for every
     * node whose item name belongs to an erasable 1-itemset.
     *
     * @param node   the current node
     * @param ncSets map from item to its growing list of NodeCodes
     */
    private void collectNodeCodes(WPPCNode node,
                                   Map<Integer, List<NodeCode>> ncSets) {
        if (node.parent != null && ncSets.containsKey(node.itemName)) {
            ncSets.get(node.itemName).add(
                    new NodeCode(node.preOrder, node.postOrder, node.weight));
        }
        for (WPPCNode child : node.children) {
            collectNodeCodes(child, ncSets);
        }
    }

    // -----------------------------------------------------------------------
    //  NC_Set operations
    // -----------------------------------------------------------------------

    /**
     * Compute the gain of an itemset from its NC_Set as the sum of node weights.
     * @param ncSet the NC_Set of the itemset
     * @return the gain value
     */
    private double computeGain(List<NodeCode> ncSet) {
        double g = 0;
        for (NodeCode nc : ncSet) g += nc.weight;
        return g;
    }

    /**
     * Combine two NC_Sets using the NC_Combination procedure.
     * NL1 and NL2 must each be sorted in descending pre-order.
     *
     * @param nl1 NC_Set of XA, sorted descending by pre-order
     * @param nl2 NC_Set of XB, sorted descending by pre-order
     * @return the combined NC_Set sorted descending by pre-order
     */
    List<NodeCode> ncCombination(List<NodeCode> nl1, List<NodeCode> nl2) {
        List<NodeCode> asc1 = new ArrayList<NodeCode>(nl1);
        List<NodeCode> asc2 = new ArrayList<NodeCode>(nl2);
        Collections.reverse(asc1); // now ascending pre-order
        Collections.reverse(asc2); // now ascending pre-order

        List<NodeCode> result = new ArrayList<NodeCode>();
        int c = 0;
        int n2 = asc2.size();

        for (int j = 0; j < asc1.size(); j++) {
            NodeCode nc1 = asc1.get(j);

            // insert all NL2 entries whose pre-order < nc1.pre (they are not
            // dominated by nc1, and no earlier NL1 entry dominated them)
            while (c < n2 && asc2.get(c).pre < nc1.pre) {
                result.add(asc2.get(c));
                c++;
            }

            // insert nc1 itself
            result.add(nc1);

            // skip all NL2 entries that are descendants of nc1 (ancestor check:
            // nc1.pre <= cj.pre AND nc1.post >= cj.post)
            while (c < n2
                    && asc2.get(c).pre >= nc1.pre
                    && asc2.get(c).post <= nc1.post) {
                c++;
            }
        }

        // append remaining NL2 entries
        while (c < n2) {
            result.add(asc2.get(c));
            c++;
        }

        // convert back to descending pre-order (Definition 6)
        Collections.sort(result, new Comparator<NodeCode>() {
            public int compare(NodeCode a, NodeCode b) {
                return b.pre - a.pre;
            }
        });
        return result;
    }

    /**
     * Recursively mine all erasable itemsets using the MERIT+ depth-first strategy.
     * For each pair (EC[k], EC[j]) in the equivalence class EC, combine their
     * NC_Sets and check erasability. 
     *
     * @param ec the current equivalence class (list of erasable itemsets)
     * @throws IOException if an error occurs writing to the output file
     */
    private void miningE(List<ErasableItemset> ec) throws IOException {
        for (int k = 0; k < ec.size(); k++) {
            ErasableItemset ek     = ec.get(k);
            List<ErasableItemset> ecNext = new ArrayList<ErasableItemset>();

            int currentSize = ek.items.size() + 1;

            for (int j = k + 1; j < ec.size(); j++) {
                ErasableItemset ej = ec.get(j);

                if (currentSize > maxItemsetSize) break;

                // combine: new itemset = ek.items + last item of ej
                List<Integer> newItems = new ArrayList<Integer>(ek.items);
                newItems.add(ej.items.get(ej.items.size() - 1));

                // NC_Combination (Definition 8 / Figure 6)
                List<NodeCode> newNC = ncCombination(ek.ncSet, ej.ncSet);
                double         gain  = computeGain(newNC);

                if (gain <= maxGain) {
                    ErasableItemset ei = new ErasableItemset(newItems, newNC, gain);
                    ecNext.add(ei);
                    saveItemsetToFile(newItems, gain);
                }
            }

            if (ecNext.size() > 1 && (ek.items.size() + 2) <= maxItemsetSize) {
                miningE(ecNext);
            } else if (ecNext.size() == 1
                    && (ek.items.size() + 2) <= maxItemsetSize) {
                // single element: no further combination possible, already saved
            }
        }
    }

    /**
     * Return true if the line is a comment, empty, or a metadata marker.
     *
     * @param line the line read from the input file
     * @return true if the line should be skipped
     */
    private boolean isComment(String line) {
        return line.isEmpty()
                || line.charAt(0) == '#'
                || line.charAt(0) == '%'
                || line.charAt(0) == '@';
    }

    /**
     * Save an erasable itemset to the output file with its gain.
     *
     * @param items the list of items in the erasable itemset
     * @param gain  the gain (loss of profit) of the itemset
     * @throws IOException if an error occurs writing to the output file
     */
    private void saveItemsetToFile(List<Integer> items, double gain) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(items.get(i));
        }
        sb.append(" #LOSS: ").append(gain);
        writer.write(sb.toString());
        writer.newLine();
        erasableCount++;
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  MERIT+ v 2.66 - STATS =============");
        long time = endTimeStamp - startTimestamp;
        System.out.println("Total profit: " + totalProfit);
        System.out.println("Maximum gain allowed (total profit x threshold): " + maxGain);
        System.out.println(" Erasable itemset count : " + erasableCount);
        System.out.println(" Total time ~ " + time + " ms");
        System.out.println("===================================================");
    }

    /**
     * Set the maximum pattern length (number of items per itemset).
     *
     * @param length the maximum number of items per itemset
     */
    public void setMaximumPatternLength(int length) {
        this.maxItemsetSize = length;
    }
}