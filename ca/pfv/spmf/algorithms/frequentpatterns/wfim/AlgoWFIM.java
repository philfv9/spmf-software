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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "WFIM" algorithm for Weighted Frequent
 * Itemset Mining as described in the paper:
 *
 * Yun, U., Leggett, J.J. (2005). WFIM: Weighted Frequent Itemset Mining
 * with a weight range and a minimum weight. SIAM International Conference
 * on Data Mining (SDM 2005), pp. 636-640.
 *
 * WFIM uses wsup(X) = w(X) * sup(X) where w(X) is the average weight of
 * items in X, which is identical to the RWFIM definition.
 * The acceptance criterion is wsup(X) >= minSup (when minWeight = 0).
 *
 * An itemset X is a Weighted Frequent Itemset (WFI) if and only if it does
 * NOT satisfy pruning condition 1 AND does NOT satisfy pruning condition 2:
 *   Condition 1: (support(X) < min_sup) AND (weight(X) < min_weight)
 *   Condition 2: (support(X) * MaxW < min_sup)
 * where MaxW is the global maximum weight across all items in the database.
 *
 * Conditions 4.1 and 4.2 in the algorithm are safe upper-bound filters used
 * during conditional database construction to avoid building subtrees that
 * cannot yield any WFI. Condition 4.2 uses the global MaxW (not MinW) to
 * ensure correctness: using MinW of the conditional prefix would be a tighter
 * bound that could incorrectly prune candidates that pass condition 2.
 *
 * @see FPTreeWFIM
 * @author Philippe Fournier-Viger
 */
public class AlgoWFIM {

    /** timestamp when the algorithm started */
    public long startTimestamp = 0;

    /** timestamp when the algorithm ended */
    public long endTimestamp = 0;

    /** number of weighted frequent itemsets found */
    public int wfiCount = 0;

    /** number of candidates pruned by condition 1 / 4.1 */
    public int pruneCondition1 = 0;

    /** number of candidates pruned by condition 2 / 4.2 */
    public int pruneCondition2 = 0;

    /** minimum weighted support count threshold */
    private double minWsupCount;

    /** minimum weight threshold */
    private double minWeight;

    /** global maximum item weight across all items in the database */
    private double globalMaxW;

    /** table mapping each item identifier to its weight */
    private Map<Integer, Double> weightTable;

    /** writer for the output file */
    private BufferedWriter writer;

    /** reusable buffer for building output lines */
    private final StringBuilder outputBuffer = new StringBuilder(256);

    /** constructs a new AlgoWFIM instance */
    public AlgoWFIM() {
    }

    /**
     * Runs the WFIM algorithm on the given input files and writes results to output.
     *
     * @param inputDatabase path to the transaction database file
     * @param inputWeights  path to the item weight table file
     * @param output        path to the output file
     * @param minSupRatio   minimum support ratio in [0,1]
     * @param minWeight     minimum weight threshold
     * @throws IOException if any file cannot be read or written
     */
    public void runAlgorithm(String inputDatabase, String inputWeights,
                             String output, double minSupRatio,
                             double minWeight) throws IOException {
        MemoryLogger.getInstance().reset();
        startTimestamp = System.currentTimeMillis();

        this.minWeight       = minWeight;
        this.wfiCount        = 0;
        this.pruneCondition1 = 0;
        this.pruneCondition2 = 0;

        writer = new BufferedWriter(new FileWriter(output), 65536);

        weightTable = loadWeightTable(inputWeights);
        List<int[]> rawTransactions = loadRawTransactions(inputDatabase);
        int dbSize = rawTransactions.size();

        this.minWsupCount = Math.ceil(minSupRatio * dbSize);

        Map<Integer, Integer> itemSupport = new HashMap<>();
        for (int[] transaction : rawTransactions) {
            for (int item : transaction) {
                itemSupport.merge(item, 1, Integer::sum);
            }
        }

        globalMaxW = Double.NEGATIVE_INFINITY;
        for (int item : itemSupport.keySet()) {
            double w = getWeight(item);
            if (w > globalMaxW) globalMaxW = w;
        }

        List<Integer> globalFreqItems = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : itemSupport.entrySet()) {
            int    item = entry.getKey();
            int    sup  = entry.getValue();
            double w    = getWeight(item);

            boolean prunedBy1 = (sup < minWsupCount) && (w < minWeight);
            boolean prunedBy2 = ((double) sup * globalMaxW) < minWsupCount;

            if (!prunedBy1 && !prunedBy2) {
                globalFreqItems.add(item);
            } else {
                if (prunedBy1) pruneCondition1++;
                if (prunedBy2) pruneCondition2++;
            }
        }

        globalFreqItems.sort((a, b) -> Double.compare(getWeight(a), getWeight(b)));

        Map<Integer, Integer> itemRank = new HashMap<>(globalFreqItems.size() * 2);
        for (int i = 0; i < globalFreqItems.size(); i++) {
            itemRank.put(globalFreqItems.get(i), i);
        }

        FPTreeWFIM tree = new FPTreeWFIM(globalFreqItems, weightTable);
        for (int[] transaction : rawTransactions) {
            List<Integer> filtered = new ArrayList<>(transaction.length);
            for (int item : transaction) {
                if (itemRank.containsKey(item)) {
                    filtered.add(item);
                }
            }
            filtered.sort((a, b) -> Integer.compare(itemRank.get(a), itemRank.get(b)));
            if (!filtered.isEmpty()) {
                tree.insertTransaction(filtered);
            }
        }

        MemoryLogger.getInstance().checkMemory();

        for (int i = globalFreqItems.size() - 1; i >= 0; i--) {
            int    item    = globalFreqItems.get(i);
            double itemW   = getWeight(item);
            int    itemSup = tree.getSupport(item);

            writeOut(new int[]{item}, itemSup, itemW);
            mineConditional(tree, new int[]{item}, itemSup);
        }

        MemoryLogger.getInstance().checkMemory();
        writer.close();
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Recursively mines the conditional FP-tree rooted at the given prefix.
     *
     * @param tree      the current FP-tree to project from
     * @param prefix    the current itemset prefix
     * @param prefixSup the support count of the prefix
     * @throws IOException if writing output fails
     */
    private void mineConditional(FPTreeWFIM tree, int[] prefix, int prefixSup)
            throws IOException {

        List<int[]> condPaths = tree.buildConditionalPaths(prefix[prefix.length - 1]);
        if (condPaths.isEmpty()) return;

        Map<Integer, Integer> condSupport = new HashMap<>();
        for (int[] path : condPaths) {
            int count = path[0];
            for (int k = 1; k < path.length; k++) {
                condSupport.merge(path[k], count, Integer::sum);
            }
        }

        List<Integer> localFreqItems = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : condSupport.entrySet()) {
            int    item = entry.getKey();
            int    sup  = entry.getValue();
            double w    = getWeight(item);

            boolean prunedBy41 = (sup < minWsupCount) && (w < minWeight);
            boolean prunedBy42 = ((double) sup * globalMaxW) < minWsupCount;

            if (!prunedBy41 && !prunedBy42) {
                localFreqItems.add(item);
            } else {
                if (prunedBy41) pruneCondition1++;
                if (prunedBy42) pruneCondition2++;
            }
        }

        if (localFreqItems.isEmpty()) return;

        localFreqItems.sort((a, b) -> Double.compare(getWeight(a), getWeight(b)));

        Map<Integer, Integer> localRank = new HashMap<>(localFreqItems.size() * 2);
        for (int i = 0; i < localFreqItems.size(); i++) {
            localRank.put(localFreqItems.get(i), i);
        }

        FPTreeWFIM condTree = new FPTreeWFIM(localFreqItems, weightTable);
        for (int[] path : condPaths) {
            int count = path[0];
            List<Integer> filtered = new ArrayList<>(path.length - 1);
            for (int k = 1; k < path.length; k++) {
                if (localRank.containsKey(path[k])) {
                    filtered.add(path[k]);
                }
            }
            filtered.sort((a, b) -> Integer.compare(localRank.get(a), localRank.get(b)));
            if (!filtered.isEmpty()) {
                condTree.insertTransactionWithCount(filtered, count);
            }
        }

        MemoryLogger.getInstance().checkMemory();

        for (int i = localFreqItems.size() - 1; i >= 0; i--) {
            int    extItem   = localFreqItems.get(i);
            int    extSup    = condTree.getSupport(extItem);
            int[]  newPrefix = appendItem(prefix, extItem);
            double newAvgW   = computeAverageWeight(newPrefix);

            boolean cond1 = (extSup < minWsupCount) && (newAvgW < minWeight);
            boolean cond2 = ((double) extSup * globalMaxW) < minWsupCount;

            if (!cond1 && !cond2) {
                writeOut(newPrefix, extSup, newAvgW);
            }

            mineConditional(condTree, newPrefix, extSup);
        }
    }

    /**
     * Computes the average weight of all items in the given itemset.
     * Uses exact integer arithmetic (scaled by 10000) to compute the sum,
     * then performs a single floating-point division and rounding step.
     *
     * @param itemset the array of item identifiers
     * @return the average weight, rounded to 4 decimal places
     */
    private double computeAverageWeight(int[] itemset) {
        long sumRounded = 0L;
        for (int item : itemset) {
            sumRounded += Math.round(getWeight(item) * 10000L);
        }
        long avgRounded = Math.round((double) sumRounded / itemset.length);
        return avgRounded / 10000.0;
    }

    /**
     * Returns the weight of the given item, or 0.0 if not found in the weight table.
     *
     * @param item the item identifier
     * @return the item's weight
     */
    private double getWeight(int item) {
        Double w = weightTable.get(item);
        return (w != null) ? w : 0.0;
    }

    /**
     * Returns a new array consisting of the prefix items followed by the given item.
     *
     * @param prefix the current prefix array
     * @param item   the item to append
     * @return a new array with item appended
     */
    private int[] appendItem(int[] prefix, int item) {
        int[] result = new int[prefix.length + 1];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        result[prefix.length] = item;
        return result;
    }

    /**
     * Loads the transaction database from the given file path.
     *
     * @param filePath path to the transaction database file
     * @return list of transactions, each as an int array of item identifiers
     * @throws IOException if the file cannot be read
     */
    private List<int[]> loadRawTransactions(String filePath) throws IOException {
        List<int[]> transactions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath), 65536)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#'
                        || line.charAt(0) == '%' || line.charAt(0) == '@') continue;

                int len = line.length();
                List<Integer> items = new ArrayList<>(16);
                int number = 0;
                boolean hasNumber = false;
                for (int pos = 0; pos < len; pos++) {
                    char c = line.charAt(pos);
                    if (c >= '0' && c <= '9') {
                        number = number * 10 + (c - '0');
                        hasNumber = true;
                    } else if (hasNumber) {
                        items.add(number);
                        number = 0;
                        hasNumber = false;
                    }
                }
                if (hasNumber) items.add(number);
                if (items.isEmpty()) continue;

                int[] transaction = new int[items.size()];
                for (int i = 0; i < transaction.length; i++) transaction[i] = items.get(i);
                transactions.add(transaction);
            }
        }
        return transactions;
    }

    /**
     * Loads the item weight table from the given file path.
     *
     * @param filePath path to the weight table file
     * @return map from item identifier to weight
     * @throws IOException if the file cannot be read
     */
    private Map<Integer, Double> loadWeightTable(String filePath) throws IOException {
        Map<Integer, Double> table = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath), 65536)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#'
                        || line.charAt(0) == '%' || line.charAt(0) == '@') continue;
                int len = line.length();
                int pos = 0;
                while (pos < len && line.charAt(pos) == ' ') pos++;
                int item = 0;
                while (pos < len && line.charAt(pos) >= '0' && line.charAt(pos) <= '9') {
                    item = item * 10 + (line.charAt(pos) - '0');
                    pos++;
                }
                while (pos < len && line.charAt(pos) == ' ') pos++;
                double weight = Double.parseDouble(line.substring(pos));
                table.put(item, weight);
            }
        }
        return table;
    }

    /**
     * Writes a weighted frequent itemset to the output file.
     *
     * @param itemset the itemset to write
     * @param support the support count of the itemset
     * @param weight  the average weight of the itemset
     * @throws IOException if writing fails
     */
    private void writeOut(int[] itemset, int support, double weight) throws IOException {
        wfiCount++;
        outputBuffer.setLength(0);
        for (int i = 0; i < itemset.length; i++) {
            if (i > 0) outputBuffer.append(' ');
            outputBuffer.append(itemset[i]);
        }
        outputBuffer.append(" #SUP: ").append(support);
        outputBuffer.append(" #WAVG: ");
        appendFormatted(outputBuffer, weight);
        writer.write(outputBuffer.toString());
        writer.newLine();
    }

    /**
     * Appends a double value formatted to exactly 4 decimal places into the given StringBuilder.
     *
     * @param sb    the StringBuilder to append to
     * @param value the value to format
     */
    private static void appendFormatted(StringBuilder sb, double value) {
        long rounded  = Math.round(value * 10000L);
        long intPart  = rounded / 10000L;
        long fracPart = Math.abs(rounded % 10000L);
        sb.append(intPart).append('.');
        if (fracPart < 1000) sb.append('0');
        if (fracPart < 100)  sb.append('0');
        if (fracPart < 10)   sb.append('0');
        sb.append(fracPart);
    }

    /** prints runtime statistics for the last algorithm execution to standard output */
    public void printStats() {
        System.out.println("======  WFIM ALGORITHM - SPMF v.2.66 - STATS ======");
        System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
        System.out.println(" Weighted frequent itemsets count: " + wfiCount);
        System.out.println("   Candidates pruned (condition 1): " + pruneCondition1);
        System.out.println("   Candidates pruned (condition 2): " + pruneCondition2);
        System.out.println("=================================================");
    }
}