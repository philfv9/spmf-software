package ca.pfv.spmf.algorithms.frequentpatterns.meta;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is an implementation of the META algorithm (Deng et al., 2009) for erasable itemset mining.
 * META (Mining Erasable iTemsets with the Anti-monotone property) uses a level-wise
 * Apriori-style search. 
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoMETA {

    /** the list of transactions: each entry is the set of items in one product */
    private List<int[]> transactions = new ArrayList<int[]>();

    /** key: product index (0-based), value: profit of that product */
    private List<Integer> productValues = new ArrayList<Integer>();

    /** start time of the latest execution */
    long startTimestamp = 0;

    /** end time of the latest execution */
    long endTimeStamp = 0;

    /** the maximum gain allowed for an itemset to be erasable (sumVal * threshold) */
    double maxGain = 0;

    /** the total profit (Sum_val) of all products in the database */
    double sumVal = 0;

    /** the number of erasable itemsets found */
    private int erasableItemsetCount = 0;

    /** object to write the output file */
    BufferedWriter writer = null;

    /** the maximum size of itemsets to be discovered */
    int maxItemsetSize = Integer.MAX_VALUE;

    /**
     * Default constructor.
     */
    public AlgoMETA() {
    }

    /**
     * Run the META algorithm on a product database.
     *
     * @param input     path to the input file
     * @param output    path to the output file
     * @param threshold the gain threshold xi (e.g. 0.15 for 15%)
     * @throws IOException           if an error occurs reading or writing files
     * @throws NumberFormatException if the file contains invalid numbers
     */
    public void runAlgorithm(String input, String output, double threshold)
            throws NumberFormatException, IOException {

        startTimestamp = System.currentTimeMillis();
        writer = new BufferedWriter(new FileWriter(output));
        erasableItemsetCount = 0;
        sumVal = 0;
        transactions.clear();
        productValues.clear();

        // --- SCAN DATABASE: compute Sum_val and load transactions into memory ---
        BufferedReader reader = new BufferedReader(new FileReader(input));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#'
                    || line.charAt(0) == '%' || line.charAt(0) == '@') {
                continue;
            }
            String[] tokens = line.split(" ");
            int val = Integer.parseInt(tokens[0]);
            sumVal += val;
            productValues.add(val);

            // parse and store the items of this product
            int[] items = new int[tokens.length - 1];
            for (int j = 1; j < tokens.length; j++) {
                items[j - 1] = Integer.parseInt(tokens[j]);
            }
            transactions.add(items);
        }
        reader.close();

        // compute the maximum allowed gain: Sum_val * xi
        maxGain = sumVal * threshold;

        // --- FIND ERASABLE 1-ITEMSETS (E1) ---
        // Gain of item A = sum of Val of products that contain A (Definition 1)
        // Build a map: item -> gain
        Map<Integer, Double> itemGainMap = new HashMap<Integer, Double>();
        for (int t = 0; t < transactions.size(); t++) {
            int[] items = transactions.get(t);
            int val = productValues.get(t);
            for (int item : items) {
                Double g = itemGainMap.get(item);
                itemGainMap.put(item, (g == null ? 0.0 : g) + val);
            }
        }

        // collect erasable 1-itemsets: those with gain <= maxGain
        List<int[]> currentLevel = new ArrayList<int[]>();
        for (Map.Entry<Integer, Double> entry : itemGainMap.entrySet()) {
            if (entry.getValue() <= maxGain && maxItemsetSize >= 1) {
                int[] itemset = new int[]{entry.getKey()};
                currentLevel.add(itemset);
                saveItemsetToFile(itemset, entry.getValue());
            }
        }

        // sort erasable 1-itemsets by item value for consistent lexicographic join
        Collections.sort(currentLevel, new Comparator<int[]>() {
            public int compare(int[] a, int[] b) {
                return a[0] - b[0];
            }
        });

        // build a set of erasable 1-itemsets for fast subset checking
        // represented as sorted int arrays converted to strings
        Set<String> erasableSet = buildErasableSet(currentLevel);

        // --- LEVEL-WISE ITERATION ---
        // For k = 2, 3, ... until no candidates or maxItemsetSize reached
        int k = 2;
        while (!currentLevel.isEmpty() && k <= maxItemsetSize) {

            // Gen_Candidate: generate candidate k-itemsets from erasable (k-1)-itemsets
            List<int[]> candidates = genCandidate(currentLevel, erasableSet, k);

            if (candidates.isEmpty()) {
                break;
            }

            // scan the database to compute gain of each candidate
            // gain(C) = sum of Val of products P such that C ∩ P.Items != empty
            double[] gains = new double[candidates.size()];
            for (int t = 0; t < transactions.size(); t++) {
                int[] productItems = transactions.get(t);
                int val = productValues.get(t);
                // convert product items to a set for fast intersection check
                Set<Integer> productItemSet = new HashSet<Integer>();
                for (int item : productItems) {
                    productItemSet.add(item);
                }
                for (int c = 0; c < candidates.size(); c++) {
                    if (intersects(candidates.get(c), productItemSet)) {
                        gains[c] += val;
                    }
                }
            }

            // Ek = candidates whose gain <= maxGain
            List<int[]> nextLevel = new ArrayList<int[]>();
            for (int c = 0; c < candidates.size(); c++) {
                if (gains[c] <= maxGain) {
                    nextLevel.add(candidates.get(c));
                    saveItemsetToFile(candidates.get(c), gains[c]);
                }
            }

            // rebuild erasable set for next level's subset check
            erasableSet = buildErasableSet(nextLevel);
            currentLevel = nextLevel;
            k++;
        }

        writer.close();
        endTimeStamp = System.currentTimeMillis();
    }

    /**
     * Generate candidate k-itemsets from erasable (k-1)-itemsets using the
     * Apriori join step followed by the No_Unerasable_Subset pruning step.
     *
     * @param levelK_1   the list of erasable (k-1)-itemsets sorted lexicographically
     * @param erasableSet the set of erasable (k-1)-itemsets as strings for fast lookup
     * @param k          the size of candidates to generate
     * @return the list of candidate k-itemsets that pass the subset check
     */
    private List<int[]> genCandidate(List<int[]> levelK_1,
                                      Set<String> erasableSet, int k) {
        List<int[]> candidates = new ArrayList<int[]>();

        loop1:
        for (int i = 0; i < levelK_1.size(); i++) {
            int[] a1 = levelK_1.get(i);
            loop2:
            for (int j = i + 1; j < levelK_1.size(); j++) {
                int[] a2 = levelK_1.get(j);

                // join condition: first k-2 items must be equal,
                // last item of a1 must be strictly less than last item of a2
                for (int m = 0; m < a1.length; m++) {
                    if (m == a1.length - 1) {
                        // last position: a1[m] must be < a2[m]
                        if (a1[m] >= a2[m]) {
                            continue loop1;
                        }
                    } else {
                        // non-last positions: must be equal
                        if (a1[m] < a2[m]) {
                            continue loop2;
                        } else if (a1[m] > a2[m]) {
                            continue loop1;
                        }
                    }
                }

                // build the candidate: a1 items + last item of a2
                int[] candidate = new int[a1.length + 1];
                System.arraycopy(a1, 0, candidate, 0, a1.length);
                candidate[a1.length] = a2[a2.length - 1];

                // No_Unerasable_Subset: all (k-1)-subsets must be in erasableSet
                if (noUnerasableSubset(candidate, erasableSet)) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    /**
     * Check that all (k-1)-subsets of a candidate k-itemset exist in the erasable set.
     * Returns true only if every subset is erasable, false as soon as one is not found.
     *
     * @param candidate   the candidate k-itemset
     * @param erasableSet the set of erasable (k-1)-itemsets as strings
     * @return true if no unerasable subset is found, false otherwise
     */
    private boolean noUnerasableSubset(int[] candidate, Set<String> erasableSet) {
        // generate all (k-1)-subsets by omitting one item at a time
        for (int skip = 0; skip < candidate.length; skip++) {
            int[] subset = new int[candidate.length - 1];
            int idx = 0;
            for (int m = 0; m < candidate.length; m++) {
                if (m != skip) {
                    subset[idx++] = candidate[m];
                }
            }
            if (!erasableSet.contains(itemsetToKey(subset))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build a HashSet of string keys from a list of itemsets for O(1) subset lookup.
     * Each itemset is converted to a canonical string key via itemsetToKey.
     *
     * @param itemsets the list of itemsets to index
     * @return a set of string keys representing all itemsets in the list
     */
    private Set<String> buildErasableSet(List<int[]> itemsets) {
        Set<String> set = new HashSet<String>();
        for (int[] itemset : itemsets) {
            set.add(itemsetToKey(itemset));
        }
        return set;
    }

    /**
     * Convert a sorted itemset to a canonical string key for use in the erasable set.
     * Items are separated by commas to avoid ambiguity between e.g. {1,2} and {12}.
     *
     * @param itemset the sorted itemset array
     * @return a comma-separated string representation of the itemset
     */
    private String itemsetToKey(int[] itemset) {
        StringBuilder sb = new StringBuilder();
        for (int m = 0; m < itemset.length; m++) {
            sb.append(itemset[m]);
            if (m < itemset.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    /**
     * Check if a candidate itemset shares at least one item with a product's item set.
     *
     * @param candidate      the candidate itemset
     * @param productItemSet the set of items in a product
     * @return true if at least one item of candidate appears in productItemSet
     */
    private boolean intersects(int[] candidate, Set<Integer> productItemSet) {
        for (int item : candidate) {
            if (productItemSet.contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Save an erasable itemset to the output file with its gain value.
     *
     * @param itemset the erasable itemset to save
     * @param gain    the gain of the itemset
     * @throws IOException if an error occurs writing to the output file
     */
    private void saveItemsetToFile(int[] itemset, double gain) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int m = 0; m < itemset.length; m++) {
            sb.append(itemset[m]);
            if (m < itemset.length - 1) {
                sb.append(" ");
            }
        }
        sb.append(" #LOSS: ").append(gain);
        writer.write(sb.toString());
        writer.newLine();
        erasableItemsetCount++;
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  META 2.66 - STATS =============");
        long time = endTimeStamp - startTimestamp;
        System.out.println("Total profit (Sum_val): " + sumVal);
        System.out.println("Maximum gain allowed (Sum_val x threshold): " + maxGain);
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
}