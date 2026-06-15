package ca.pfv.spmf.algorithms.frequentpatterns.dmerit;
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
 * This is an implementation of the dMERIT+ algorithm for erasable itemset mining.
 * dMERIT+ (Le et al., 2014) improves upon MERIT+ by replacing NC_Sets with
 * dNC'_Sets (difference node-code sets without stored weights) and maintaining
 * a separate index-of-weight array W indexed by pre-order number.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoDMERIT {

    // -----------------------------------------------------------------------
    //  Inner classes
    // -----------------------------------------------------------------------

    /**
     * A node in the WPPC-tree, identical in structure to the one used by MERIT+.
     * Each node stores item name, accumulated weight, traversal orders, and links.
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

        /** the list of child nodes */
        List<WPPCNode> children = new ArrayList<WPPCNode>();

        /**
         * Create a WPPC-tree node.
         *
         * @param itemName the item stored at this node
         * @param weight   the profit weight from the inserting product
         * @param parent   the parent node
         */
        WPPCNode(int itemName, double weight, WPPCNode parent) {
            this.itemName = itemName;
            this.weight   = weight;
            this.parent   = parent;
        }
    }

    /**
     * A node code NC' storing only pre-order and post-order (no weight).
     * The weight is retrieved from the index-of-weight array W via W[pre].
     * This is the NC' structure from Definition 9 / dMERIT+ paper.
     */
    static class NodeCodePrime {
        /** pre-order traversal number (also the index into W) */
        int pre;

        /** post-order traversal number (used for ancestor checks) */
        int post;

        /**
         * Create a node code without weight storage.
         *
         * @param pre  pre-order number
         * @param post post-order number
         */
        NodeCodePrime(int pre, int post) {
            this.pre  = pre;
            this.post = post;
        }
    }

    /**
     * Represents one erasable itemset with its dNC'_Set and current gain.
     * The dNC'_Set is the difference set relative to the parent itemset,
     * not the full NC'_Set. The gain is maintained incrementally.
     */
    static class ErasableItemset {
        /** the items in this erasable itemset */
        List<Integer> items;

        /**
         * the dNC'_Set of this itemset relative to its parent in the search tree.
         * For 1-itemsets this holds the full NC'_Set (since there is no parent).
         */
        List<NodeCodePrime> dncSet;

        /** the current gain computed incrementally via Theorem 6 */
        double gain;

        /**
         * Create an erasable itemset with the given items, dNC'_Set, and gain.
         *
         * @param items  the item list
         * @param dncSet the dNC'_Set (or full NC'_Set for 1-itemsets)
         * @param gain   the precomputed gain
         */
        ErasableItemset(List<Integer> items, List<NodeCodePrime> dncSet, double gain) {
            this.items  = items;
            this.dncSet = dncSet;
            this.gain   = gain;
        }
    }

    // -----------------------------------------------------------------------
    //  Fields
    // -----------------------------------------------------------------------

    /** start time of the latest execution */
    long startTimestamp = 0;

    /** end time of the latest execution */
    long endTimeStamp = 0;

    /** the overall profit of all products (Sum_val = T) */
    double totalProfit = 0;

    /** the maximum gain allowed for an itemset to be erasable (T * xi) */
    double maxGain = 0;

    /** the index of weight: W[preOrder] = weight of node with that pre-order */
    double[] W;

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

    /**
     * Default constructor.
     */
    public AlgoDMERIT() {
    }

    /**
     * Run the dMERIT+ algorithm on a product database.
     * Builds a WPPC-tree, creates the index of weight W, generates NC'_Sets
     * for erasable 1-itemsets, then mines all erasable itemsets depth-first
     * using dNC'_Sets for memory-efficient gain computation.
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
            totalProfit += Integer.parseInt(line.split(" ")[0]);
        }
        reader.close();
        maxGain = totalProfit * threshold;

        // --- SECOND SCAN: load database, compute item gains and frequencies ---
        Map<Integer, Double>  itemGain = new HashMap<Integer, Double>();
        Map<Integer, Integer> itemFreq = new HashMap<Integer, Integer>();
        List<int[]>    rawItems   = new ArrayList<int[]>();
        List<Double>   rawProfits = new ArrayList<Double>();

        reader = new BufferedReader(new FileReader(input));
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
                Double  g = itemGain.get(item);
                Integer f = itemFreq.get(item);
                itemGain.put(item, (g == null ? 0.0 : g) + profit);
                itemFreq.put(item, (f == null ? 0   : f) + 1);
            }
        }
        reader.close();

        // collect erasable 1-itemsets sorted by ascending frequency
        List<Integer> erasable1 = new ArrayList<Integer>();
        for (Map.Entry<Integer, Double> e : itemGain.entrySet()) {
            if (e.getValue() <= maxGain) erasable1.add(e.getKey());
        }
        Collections.sort(erasable1, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) {
                int fa = itemFreq.getOrDefault(a, 0);
                int fb = itemFreq.getOrDefault(b, 0);
                return fa != fb ? fa - fb : a - b;
            }
        });

        // H1: item -> position in erasable1 (used for ordering in the tree)
        final Map<Integer, Integer> h1 = new HashMap<Integer, Integer>();
        for (int i = 0; i < erasable1.size(); i++) h1.put(erasable1.get(i), i);

        // save erasable 1-itemsets to output
        for (int item : erasable1) {
            List<Integer> s = new ArrayList<Integer>();
            s.add(item);
            saveItemsetToFile(s, itemGain.get(item));
        }

        if (erasable1.isEmpty()) {
            writer.close();
            endTimeStamp = System.currentTimeMillis();
            return;
        }

        // --- BUILD WPPC-TREE ---
        WPPCNode root = new WPPCNode(-1, 0, null);
        for (int t = 0; t < rawItems.size(); t++) {
            List<Integer> filtered = new ArrayList<Integer>();
            for (int item : rawItems.get(t)) {
                if (h1.containsKey(item)) filtered.add(item);
            }
            if (filtered.isEmpty()) continue;
            Collections.sort(filtered, new Comparator<Integer>() {
                public int compare(Integer a, Integer b) { return h1.get(a) - h1.get(b); }
            });
            insertIntoTree(root, filtered, 0, rawProfits.get(t));
        }

        // assign pre/post orders
        assignOrders(root);

        // --- BUILD INDEX OF WEIGHT W ---
        // W[preOrder] = weight of that node; W is 1-indexed (preOrder starts at 1)
        W = new double[preCounter + 1];
        buildWeightIndex(root);

        // --- GENERATE NC'_SETS FOR ERASABLE 1-ITEMSETS ---
        // For 1-itemsets the dNC'_Set IS the full NC'_Set (no parent to subtract).
        Map<Integer, List<NodeCodePrime>> ncPrimeSets = new HashMap<Integer, List<NodeCodePrime>>();
        for (int item : erasable1) ncPrimeSets.put(item, new ArrayList<NodeCodePrime>());
        collectNCPrime(root, ncPrimeSets);

        // sort each NC'_Set in descending pre-order (matching Definition 6 ordering)
        for (List<NodeCodePrime> nc : ncPrimeSets.values()) {
            Collections.sort(nc, new Comparator<NodeCodePrime>() {
                public int compare(NodeCodePrime a, NodeCodePrime b) { return b.pre - a.pre; }
            });
        }

        // build ErasableItemset objects for 1-itemsets
        // for 1-itemsets, dNC'_Set = full NC'_Set and gain = itemGain
        List<ErasableItemset> e1List = new ArrayList<ErasableItemset>();
        for (int item : erasable1) {
            List<Integer>      its  = new ArrayList<Integer>(); its.add(item);
            List<NodeCodePrime> nc  = ncPrimeSets.get(item);
            double             gain = itemGain.get(item);
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
     * Insert a filtered sorted item list for one product into the WPPC-tree.
     *
     * @param node   the current node
     * @param items  the filtered sorted item list
     * @param idx    the current index in the item list
     * @param profit the profit of the product
     */
    private void insertIntoTree(WPPCNode node, List<Integer> items,
                                int idx, double profit) {
        if (idx >= items.size()) return;
        int item = items.get(idx);
        for (WPPCNode child : node.children) {
            if (child.itemName == item) {
                child.weight += profit;
                insertIntoTree(child, items, idx + 1, profit);
                return;
            }
        }
        WPPCNode newNode = new WPPCNode(item, profit, node);
        node.children.add(newNode);
        insertIntoTree(newNode, items, idx + 1, profit);
    }

    /**
     * Assign pre-order and post-order numbers to all nodes via recursive DFS.
     *
     * @param node the current node
     */
    private void assignOrders(WPPCNode node) {
        if (node.parent != null) node.preOrder = ++preCounter;
        for (WPPCNode child : node.children) assignOrders(child);
        if (node.parent != null) node.postOrder = ++postCounter;
    }

    /**
     * Populate the index-of-weight array W by visiting every non-root node.
     *
     * @param node the current node
     */
    private void buildWeightIndex(WPPCNode node) {
        if (node.parent != null) {
            W[node.preOrder] = node.weight;
        }
        for (WPPCNode child : node.children) buildWeightIndex(child);
    }

    /**
     * Recursively collect NC' entries (pre, post only) for erasable 1-itemsets.
     *
     * @param node      the current WPPC-tree node
     * @param ncPrimeSets map from item to its growing NC'_Set list
     */
    private void collectNCPrime(WPPCNode node,
                                 Map<Integer, List<NodeCodePrime>> ncPrimeSets) {
        if (node.parent != null && ncPrimeSets.containsKey(node.itemName)) {
            ncPrimeSets.get(node.itemName).add(
                    new NodeCodePrime(node.preOrder, node.postOrder));
        }
        for (WPPCNode child : node.children) collectNCPrime(child, ncPrimeSets);
    }

    // -----------------------------------------------------------------------
    //  dNC'_Set operations
    // -----------------------------------------------------------------------

    /**
     * Compute the difference NC'_Set: dNC'(XAB) = NC'(XB) \ NC'(XA).
     * Also returns the sum of W[pre] for the resulting difference set,
     * so the caller can update the gain incrementally.
     *
     * @param ncA the NC'_Set (or dNC'_Set for 1-items = full NC'_Set) of XA,
     *            descending pre-order
     * @param ncB the NC'_Set (or dNC'_Set for 1-items = full NC'_Set) of XB,
     *            descending pre-order
     * @return a two-element array: [0] = List&lt;NodeCodePrime&gt; (the dNC'_Set),
     *         [1] = Double (the incremental gain contribution)
     */
    Object[] dncSubtract(List<NodeCodePrime> ncA, List<NodeCodePrime> ncB) {
        // convert both to ascending pre-order for the merge
        List<NodeCodePrime> ascA = new ArrayList<NodeCodePrime>(ncA);
        List<NodeCodePrime> ascB = new ArrayList<NodeCodePrime>(ncB);
        Collections.reverse(ascA);
        Collections.reverse(ascB);

        List<NodeCodePrime> diff = new ArrayList<NodeCodePrime>();
        double gainContrib = 0.0;
        int    aIdx = 0;

        // for each entry in ascB, check if any entry in ascA is its ancestor
        outer:
        for (NodeCodePrime cb : ascB) {
            // advance aIdx past entries in ascA that cannot be ancestors of cb
            // ancestor: ca.pre <= cb.pre AND ca.post >= cb.post
            for (int a = 0; a < ascA.size(); a++) {
                NodeCodePrime ca = ascA.get(a);
                if (ca.pre <= cb.pre && ca.post >= cb.post) {
                    // ca is an ancestor of cb: cb is dominated, skip it
                    continue outer;
                }
            }
            // cb is not dominated by any node in ncA: include in diff
            diff.add(cb);
            gainContrib += W[cb.pre];
        }

        // convert back to descending pre-order
        Collections.sort(diff, new Comparator<NodeCodePrime>() {
            public int compare(NodeCodePrime a, NodeCodePrime b) { return b.pre - a.pre; }
        });

        return new Object[]{diff, gainContrib};
    }

    // -----------------------------------------------------------------------
    //  Depth-first mining
    // -----------------------------------------------------------------------

    /**
     * Recursively mine all erasable itemsets using dMERIT+ depth-first strategy.
     *
     * @param ec the current equivalence class
     * @throws IOException if an error occurs writing to the output file
     */
    private void miningE(List<ErasableItemset> ec) throws IOException {
        for (int k = 0; k < ec.size(); k++) {
            ErasableItemset ek     = ec.get(k);
            List<ErasableItemset> ecNext = new ArrayList<ErasableItemset>();

            int nextSize = ek.items.size() + 1;
            if (nextSize > maxItemsetSize) continue;

            for (int j = k + 1; j < ec.size(); j++) {
                ErasableItemset ej = ec.get(j);

                // determine which item comes first in h1 ordering
                // the paper uses H1[e1] < H1[e2] to decide the direction
                // here ek always precedes ej in the sorted e1List so
                // EI.Items = ek.items + last-item-of-ej (Theorem 5 direction)
                List<Integer> newItems = new ArrayList<Integer>(ek.items);
                newItems.add(ej.items.get(ej.items.size() - 1));

                // dNC'(XAB) = dNC'(XB) \ dNC'(XA)  (Theorem 5)
                // for 1-itemsets the dNC'_Set IS the full NC'_Set
                Object[] res = dncSubtract(ek.dncSet, ej.dncSet);
                @SuppressWarnings("unchecked")
                List<NodeCodePrime> newDNC = (List<NodeCodePrime>) res[0];
                double gainContrib         = (Double) res[1];

                // gain(XAB) = gain(XA) + sum(W[pre] for entries in dNC'(XAB))
                // Theorem 6
                double newGain = ek.gain + gainContrib;

                if (newGain <= maxGain) {
                    ErasableItemset ei = new ErasableItemset(newItems, newDNC, newGain);
                    ecNext.add(ei);
                    saveItemsetToFile(newItems, newGain);
                }
            }

            if (ecNext.size() > 1 && (nextSize + 1) <= maxItemsetSize) {
                miningE(ecNext);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Utilities
    // -----------------------------------------------------------------------

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
        System.out.println("=============  dMERIT+ v 2.66 - STATS =============");
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