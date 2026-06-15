package ca.pfv.spmf.algorithms.frequentpatterns.mei;
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
 * Do not remove the license or copyright information.
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
 * This is an implementation of the MEI algorithm (Lê et al.) for erasable itemset mining.
 * MEI uses a dPidset (difference pidset) vertical representation and a depth-first
 * divide-and-conquer strategy to mine all erasable itemsets efficiently.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoMEI {

    /** key: item, value: pidset (sorted list of product ids containing that item) */
    Map<Integer, List<Integer>> mapItemPidset = new HashMap<Integer, List<Integer>>();

    /** key: product id (1-based), value: profit of that product */
    Map<Integer, Integer> gainIndex = new HashMap<Integer, Integer>();

    /** start time of the latest execution */
    long startTimestamp = 0;

    /** end time of the latest execution */
    long endTimeStamp = 0;

    /** the maximum gain allowed for an itemset to be erasable (T * threshold) */
    double maxGain = 0;

    /** the total profit T of all products in the database */
    double totalProfit = 0;

    /** the number of erasable itemsets found */
    private int erasableItemsetCount = 0;

    /** object to write the output file */
    BufferedWriter writer = null;

    /** the maximum size of itemsets to be discovered */
    int maxItemsetSize = Integer.MAX_VALUE;

    /**
     * Default constructor.
     */
    public AlgoMEI() {
    }

    /**
     * Run the MEI algorithm on a product database.
     * Scans the database once to compute T, G, and erasable 1-itemsets with pidsets.
     * Then runs the depth-first Expand_E procedure to find all erasable itemsets.
     *
     * @param input     path to the input file
     * @param output    path to the output file
     * @param threshold the gain threshold xi (e.g. 0.16 for 16%)
     * @throws IOException           if an error occurs reading or writing files
     * @throws NumberFormatException if the file contains invalid numbers
     */
    public void runAlgorithm(String input, String output, double threshold)
            throws NumberFormatException, IOException {

        startTimestamp = System.currentTimeMillis();
        writer = new BufferedWriter(new FileWriter(output));
        erasableItemsetCount = 0;
        totalProfit = 0;
        mapItemPidset.clear();
        gainIndex.clear();

        // --- SINGLE SCAN: compute T, G (gainIndex), and item pidsets ---
        // Products are 1-based as in the paper (P1, P2, ..., Pn)
        BufferedReader reader = new BufferedReader(new FileReader(input));
        String line;
        int pid = 1;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#'
                    || line.charAt(0) == '%' || line.charAt(0) == '@') {
                continue;
            }
            String[] tokens = line.split(" ");
            int profit = Integer.parseInt(tokens[0]);

            // G[pid] = profit of product pid
            gainIndex.put(pid, profit);
            totalProfit += profit;

            // for each item in this product, add pid to its pidset
            for (int j = 1; j < tokens.length; j++) {
                int item = Integer.parseInt(tokens[j]);
                List<Integer> pidset = mapItemPidset.get(item);
                if (pidset == null) {
                    pidset = new ArrayList<Integer>();
                    mapItemPidset.put(item, pidset);
                }
                pidset.add(pid);
            }
            pid++;
        }
        reader.close();

        // compute the maximum allowed gain: T * xi
        maxGain = totalProfit * threshold;

        // --- FIND ERASABLE 1-ITEMSETS ---
        // gain of item A = sum of G[k] for k in p(A)  (Definition 5)
        // item A is erasable if g(A) <= T * xi
        List<ErasableItemset> erasable1 = new ArrayList<ErasableItemset>();

        for (Map.Entry<Integer, List<Integer>> entry : mapItemPidset.entrySet()) {
            int item = entry.getKey();
            List<Integer> pidset = entry.getValue();
            // pidsets are built in ascending order as we scan sequentially
            // compute gain as sum of G[k] for all k in pidset
            double gain = computeGainFromPidset(pidset);
            if (gain <= maxGain && maxItemsetSize >= 1) {
                // store as ErasableItemset with its full pidset and gain
                ErasableItemset ei = new ErasableItemset(
                        new int[]{item}, pidset, null, gain);
                erasable1.add(ei);
                saveItemsetToFile(ei);
            }
        }

        // --- SORT erasable 1-itemsets in DESCENDING order of pidset size (Theorem 6) ---
        // Larger pidset first ensures smaller dPidsets when combining, saving memory
        Collections.sort(erasable1, new Comparator<ErasableItemset>() {
            public int compare(ErasableItemset o1, ErasableItemset o2) {
                // descending order of pidset size
                return o2.pidset.size() - o1.pidset.size();
            }
        });

        // --- DEPTH-FIRST EXPAND_E ---
        // combine each erasable 1-itemset with the ones after it
        if (maxItemsetSize >= 2) {
            for (int i = 0; i < erasable1.size(); i++) {
                expandE(erasable1.get(i), erasable1, i + 1);
            }
        }

        writer.close();
        endTimeStamp = System.currentTimeMillis();
    }

    /**
     * Compute the gain of an itemset from its full pidset.
     * The gain is the sum of G[k] for all k in the pidset (Definition 5).
     *
     * @param pidset sorted list of product ids
     * @return the gain value
     */
    private double computeGainFromPidset(List<Integer> pidset) {
        double gain = 0;
        for (int k : pidset) {
            gain += gainIndex.get(k);
        }
        return gain;
    }

    /**
     * Implement the Expand_E divide-and-conquer procedure.
     *
     * @param current    the current erasable itemset (prefix)
     * @param candidates the list of candidate itemsets to combine with current
     * @param startIndex the index in candidates from which to start combining
     * @throws IOException if an error occurs writing to the output file
     */
    private void expandE(ErasableItemset current, List<ErasableItemset> candidates,
                         int startIndex) throws IOException {

        // collect newly created erasable (k+1)-itemsets at this level
        List<ErasableItemset> newErasable = new ArrayList<ErasableItemset>();

        for (int i = startIndex; i < candidates.size(); i++) {
            ErasableItemset next = candidates.get(i);

            // Compute dPidset and gain of the combined itemset
            // For k=1: dPidset of XAB = p(XB) \ p(XA)  (Definition 6)
            // For k>1: dPidset of XAB = dP(XB) \ dP(XA)  (Theorem 4)
            // Gain of XAB = g(XA) + sum G[k] for k in dP(XAB)  (Theorem 5)

            SubResult sub;
            if (current.dPidset == null) {
                // both are 1-itemsets: use full pidsets
                // dP(XAB) = p(next) \ p(current)
                sub = subDPidsets(next.pidset, current.pidset);
            } else {
                // both are k-itemsets (k>=2): use dPidsets
                // dP(XAB) = dP(next) \ dP(current)
                sub = subDPidsets(next.dPidset, current.dPidset);
            }

            // gain of combined itemset = g(current) + gain contributed by dPidset elements
            double newGain = current.gain + sub.gainOfResult;

            if (newGain <= maxGain) {
                // build new item array: current.items + last item of next
                int[] newItems = buildItems(current.items, next.items[next.items.length - 1]);

                ErasableItemset combined = new ErasableItemset(
                        newItems, null, sub.result, newGain);
                newErasable.add(combined);
                saveItemsetToFile(combined);
            }
        }

        // recurse depth-first: for each newly found erasable itemset,
        // combine it with the ones after it in newErasable
        if (maxItemsetSize > currentDepth(current) + 1) {
            for (int i = 0; i < newErasable.size(); i++) {
                expandE(newErasable.get(i), newErasable, i + 1);
            }
        }
    }

    /**
     * Return the size (number of items) of an itemset, used to check maxItemsetSize.
     *
     * @param itemset the itemset
     * @return the number of items in the itemset
     */
    private int currentDepth(ErasableItemset itemset) {
        return itemset.items.length;
    }

    /**
     * Build a new item array by appending one item to an existing array.
     *
     * @param items   the existing item array
     * @param newItem the item to append
     * @return a new array containing all items plus newItem at the end
     */
    private int[] buildItems(int[] items, int newItem) {
        int[] result = new int[items.length + 1];
        System.arraycopy(items, 0, result, 0, items.length);
        result[items.length] = newItem;
        return result;
    }

    /**
     * Implement the Sub_dPidsets algorithm.
     *
     * @param d1 the sorted pidset or dPidset to subtract from
     * @param d2 the sorted pidset or dPidset to subtract
     * @return a SubResult containing d3 = d1 \ d2 and the sum of G[k] for k in d3
     */
    private SubResult subDPidsets(List<Integer> d1, List<Integer> d2) {
        List<Integer> d3 = new ArrayList<Integer>();
        double gain = 0;

        int i = 0; // index into d1
        int j = 0; // index into d2

        // O(n+m) merge-style subtraction on two sorted lists
        while (i < d1.size() && j < d2.size()) {
            int val1 = d1.get(i);
            int val2 = d2.get(j);

            if (val1 == val2) {
                // val1 exists in d2, so it is NOT added to d3 (it belongs to d1 ∩ d2)
                // Case 2 in paper: advance both pointers
                i++;
                j++;
            } else if (val1 > val2) {
                // val2 < val1: advance j to catch up
                // Case 1 in paper: val2 is bypassed
                j++;
            } else {
                // val1 < val2: val1 does not exist in d2, add to d3
                // Case 3 in paper
                d3.add(val1);
                gain += gainIndex.get(val1);
                i++;
            }
        }

        // remaining elements in d1 are not in d2, add them all to d3
        while (i < d1.size()) {
            int val1 = d1.get(i);
            d3.add(val1);
            gain += gainIndex.get(val1);
            i++;
        }

        return new SubResult(d3, gain);
    }

    /**
     * Save an erasable itemset to the output file with its gain value.
     *
     * @param itemset the erasable itemset to save
     * @throws IOException if an error occurs writing to the output file
     */
    private void saveItemsetToFile(ErasableItemset itemset) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < itemset.items.length; k++) {
            sb.append(itemset.items[k]);
            if (k < itemset.items.length - 1) {
                sb.append(" ");
            }
        }
        sb.append(" #LOSS: ").append(itemset.gain);
        writer.write(sb.toString());
        writer.newLine();
        erasableItemsetCount++;
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  MEI 2.66 - STATS =============");
        long time = endTimeStamp - startTimestamp;
        System.out.println("Total profit T: " + totalProfit);
        System.out.println("Maximum gain allowed (T x threshold): " + maxGain);
        System.out.println(" Erasable itemset count : " + erasableItemsetCount);
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

    /**
     * Represents an erasable itemset storing its items, pidset (for 1-itemsets),
     * dPidset (for k-itemsets with k>=2), and precomputed gain.
     * For 1-itemsets, dPidset is null and pidset holds the full pidset.
     * For k-itemsets (k>=2), pidset is null and dPidset holds the difference pidset.
     */
    static class ErasableItemset {

        /** the items in this itemset in the order they were combined */
        int[] items;

        /** the full pidset, used only for 1-itemsets (null for k>=2) */
        List<Integer> pidset;

        /** the difference pidset, used for k-itemsets with k>=2 (null for k=1) */
        List<Integer> dPidset;

        /** the precomputed gain of this itemset */
        double gain;

        /**
         * Create an erasable itemset with all its fields.
         *
         * @param items   the item array
         * @param pidset  the full pidset (for 1-itemsets) or null
         * @param dPidset the difference pidset (for k>=2 itemsets) or null
         * @param gain    the precomputed gain
         */
        ErasableItemset(int[] items, List<Integer> pidset,
                        List<Integer> dPidset, double gain) {
            this.items = items;
            this.pidset = pidset;
            this.dPidset = dPidset;
            this.gain = gain;
        }
    }

    /**
     * Holds the result of the Sub_dPidsets algorithm: the resulting difference
     * list d3 = d1 \ d2 and the total gain contributed by elements of d3.
     */
    static class SubResult {

        /** the resulting difference pidset d3 = d1 \ d2 */
        List<Integer> result;

        /** the sum of G[k] for all k in result, i.e. the gain contribution of d3 */
        double gainOfResult;

        /**
         * Create a SubResult with the given difference list and its gain sum.
         *
         * @param result      the list d3 = d1 \ d2
         * @param gainOfResult the sum of G[k] for k in result
         */
        SubResult(List<Integer> result, double gainOfResult) {
            this.result = result;
            this.gainOfResult = gainOfResult;
        }
    }
}