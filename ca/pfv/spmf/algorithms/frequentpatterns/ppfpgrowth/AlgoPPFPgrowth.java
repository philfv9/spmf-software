package ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

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
 * Adapted from the LPP-Growth implementation (AlgoLPPGrowth) by Peng Yang (SPMF, 2019).
 * 
 * Do not remove the copyright and license information.
 */

/**
 * PPFP-growth: mining probabilistic periodic-frequent patterns (PPFPs) in an
 * uncertain transaction database.
 * <p>
 * <b>Data model.</b> The database is a sequence of n transactions with TIDs
 * 1..n (the order of the transactions in the input file). Each item x of a
 * transaction t has an existential probability E<sub>t</sub>(x) in (0, 1].
 * Items and transactions are assumed independent (possible-world model), so
 * that the existential probability of an itemset X in t is
 * E<sub>t</sub>(X) = &prod;<sub>x&isin;X</sub> E<sub>t</sub>(x).
 * <p>
 * <b>Measures</b> of an itemset X, where g(X) is the list of TIDs of the
 * transactions containing all items of X:
 * <ul>
 * <li>expected support: expSup(X) = &sum;<sub>t&isin;g(X)</sub> E<sub>t</sub>(X),
 * a real number of transactions in [0, n];</li>
 * <li>periodicity: per(X) = the largest difference between consecutive TIDs of
 * g(X), counting the period from TID 0 to the first TID and from the last TID
 * to n (Tanbeer et al., 2009), in transactions. Note: this follows Algorithm 1
 * and Example 3 of the paper; its Definitions 3 and 12 use n + 1 instead of n
 * for the end boundary;</li>
 * <li>probability of periodicity: P(X) = the total probability of the possible
 * worlds in which every period of X is &lt;= maxPer, in [0, 1] (see
 * {@link PeriodicityProbability}).</li>
 * </ul>
 * X is a PPFP iff expSup(X) &gt;= minSup &times; n, per(X) &lt;= maxPer and
 * P(X) &gt;= minProbability. The three measures are anti-monotone (a superset
 * cannot have a larger expected support, a smaller periodicity or a larger
 * probability of periodicity), which allows the pattern-growth search.
 * <p>
 * <b>Algorithm.</b> (1) A first scan computes the PPFP-list of single items;
 * (2) a second scan builds the PPFP-tree with the PPFP items of each
 * transaction, sorted by descending expected support; (3) the tree is mined
 * recursively as in FP-growth (Han et al., 2000): for each item of the header
 * table (bottom-up), the prefix paths of the item give the conditional
 * database of the new pattern, whose PPFP-list and conditional tree are built
 * and mined recursively. Existential probabilities are multiplied along the
 * prefix paths, as in UF-growth (Leung et al., 2008).
 * <p>
 * <b>Reference.</b> The algorithm is described in:
 * I. Hamimid, F. Nouioua, S. Boutouhami, P. Fournier-Viger. Probabilistic
 * periodic frequent pattern mining in uncertain databases. Applied
 * Intelligence 56(15), article 469, 2026.
 * <a href="https://doi.org/10.1007/s10489-026-07388-7">https://doi.org/10.1007/s10489-026-07388-7</a>
 * <p>
 * Other references cited by that paper:
 * <ul>
 * <li>J. Han, J. Pei, Y. Yin. Mining frequent patterns without candidate
 * generation. ACM SIGMOD Record 29(2), 2000.</li>
 * <li>S. K. Tanbeer, C. F. Ahmed, B.-S. Jeong, Y.-K. Lee. Discovering
 * periodic-frequent patterns in transactional databases. PAKDD 2009.</li>
 * <li>C. K.-S. Leung, M. A. F. Mateo, D. A. Brajczuk. A tree-based approach for
 * frequent pattern mining from uncertain data. PAKDD 2008.</li>
 * </ul>
 *
 * @see PPFTree
 * @see PeriodicityProbability
 */
public class AlgoPPFPgrowth {

    /** number of significant digits of the real values written to the output file */
    private static final int OUTPUT_SIGNIFICANT_DIGITS = 12;

    /** start time of the latest execution */
    private long startTimestamp;

    /** end time of the latest execution */
    private long endTime;

    /** number of transactions n of the database */
    private int databaseSize;

    /** number of patterns found */
    private int itemsetCount;

    /** number of nodes of the initial PPFP-tree */
    private int initialTreeNodeCount;

    /** maximum periodicity threshold (in transactions) */
    private int maxPer;

    /** minimum expected support threshold, as a fraction of the number of transactions */
    private double minSup;

    /** minimum expected support threshold, in transactions (minSup &times; n) */
    private double minSupAbsolute;

    /** minimum probability of periodicity threshold (beta) */
    private double minProbability;

    /** maximum number of items of a pattern */
    private int maxPatternLength = Integer.MAX_VALUE;

    /** writer of the output file, or null if the patterns are kept in memory */
    private BufferedWriter writer = null;

    /** the patterns found, if they are kept in memory */
    private Itemsets patterns = null;

    /** the items of the pattern currently being extended (reused buffer) */
    private int[] itemsetBuffer = null;

    /** Default constructor. */
    public AlgoPPFPgrowth() {
    }

    /**
     * Run the algorithm.
     *
     * @param input          path of the uncertain transaction database (see {@link TransactionReader} for the format)
     * @param output         path of the output file, or null to keep the patterns in memory
     * @param maxPer         maximum periodicity threshold, in transactions (&gt;= 1)
     * @param minSup         minimum expected support, as a fraction of the number of transactions, in [0, 1]
     * @param minProbability minimum probability of periodicity (beta), in [0, 1]
     * @return the patterns if output is null, otherwise null
     * @throws IOException              if the input file cannot be read or the output file cannot be written
     * @throws IllegalArgumentException if a parameter is out of range or the input file is malformed
     */
    public Itemsets runAlgorithm(String input, String output, int maxPer, double minSup, double minProbability)
            throws IOException {
        validateParameters(input, maxPer, minSup, minProbability);
        startTimestamp = System.currentTimeMillis();
        this.maxPer = maxPer;
        this.minSup = minSup;
        this.minProbability = minProbability;
        itemsetCount = 0;
        databaseSize = 0;
        MemoryLogger.getInstance().reset();
        MemoryLogger.getInstance().checkMemory();

        // (1) first scan: PPFP-list of single items (Algorithm 2 of the paper)
        Map<Integer, PPFPListEntry> ppfpList = scanDatabaseForSingleItems(input);

        // (2) second scan: PPFP-tree (Algorithm 3 of the paper)
        PPFTree tree = buildTree(input, ppfpList);
        initialTreeNodeCount = tree.numberOfNodes;

        // (3) recursive mining (Algorithm 4 of the paper)
        if (output == null) {
            writer = null;
            patterns = new Itemsets("Probabilistic periodic-frequent itemsets");
        } else {
            patterns = null;
            writer = new BufferedWriter(new FileWriter(output));
    		writer.write("@FILETYPE=\"Probabilistic periodic frequent itemsets\"");
    		writer.newLine();
    		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
    		writer.newLine();
        }
        try {
            if (!tree.headerList.isEmpty()) {
                itemsetBuffer = new int[tree.headerList.size()];
                ppfpGrowth(tree, 0, ppfpList);
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        endTime = System.currentTimeMillis();
        MemoryLogger.getInstance().checkMemory();
        return patterns;
    }

    /**
     * Check the parameters before reading any file.
     *
     * @param input          path of the input file
     * @param maxPer         maximum periodicity threshold
     * @param minSup         minimum expected support, as a fraction of the number of transactions
     * @param minProbability minimum probability of periodicity
     * @throws IllegalArgumentException if a parameter is null or out of range
     */
    private static void validateParameters(String input, int maxPer, double minSup, double minProbability) {
        if (input == null) {
            throw new IllegalArgumentException("The input file path must not be null.");
        }
        if (maxPer < 1) {
            throw new IllegalArgumentException("maxPer must be a positive number of transactions, got " + maxPer + ".");
        }
        if (!(minSup >= 0 && minSup <= 1)) {
            throw new IllegalArgumentException("minSup must be a fraction of the number of transactions in [0, 1], got "
                    + minSup + ".");
        }
        if (!(minProbability >= 0 && minProbability <= 1)) {
            throw new IllegalArgumentException("The minimum probability of periodicity must be in [0, 1], got "
                    + minProbability + ".");
        }
    }

    /**
     * First database scan: collect the occurrences of each item and keep the
     * items that satisfy the three thresholds. Also sets the database size.
     *
     * @param input path of the input file
     * @return the PPFP-list of single items (key: item)
     * @throws IOException if the input file cannot be read
     */
    private Map<Integer, PPFPListEntry> scanDatabaseForSingleItems(String input) throws IOException {
        Map<Integer, Occurrences> occurrencesByItem = new HashMap<Integer, Occurrences>();
        List<Integer> items = new ArrayList<Integer>();
        List<Double> probabilities = new ArrayList<Double>();
        int tid = 0;
        TransactionReader reader = new TransactionReader(input);
        try {
            while (reader.readTransaction(items, probabilities)) {
                tid++;
                for (int i = 0; i < items.size(); i++) {
                    Occurrences occurrences = occurrencesByItem.get(items.get(i));
                    if (occurrences == null) {
                        occurrences = new Occurrences();
                        occurrencesByItem.put(items.get(i), occurrences);
                    }
                    occurrences.add(tid, probabilities.get(i));
                }
            }
        } finally {
            reader.close();
        }
        databaseSize = tid;
        minSupAbsolute = minSup * databaseSize;

        Map<Integer, PPFPListEntry> ppfpList = new HashMap<Integer, PPFPListEntry>();
        for (Map.Entry<Integer, Occurrences> e : occurrencesByItem.entrySet()) {
            PPFPListEntry entry = evaluate(e.getValue());
            if (entry != null) {
                ppfpList.put(e.getKey(), entry);
            }
        }
        return ppfpList;
    }

    /**
     * Second database scan: insert the PPFP items of each transaction, sorted
     * by the order of the tree, with their existential probabilities.
     *
     * @param input    path of the input file
     * @param ppfpList the PPFP-list of single items
     * @return the initial PPFP-tree
     * @throws IOException if the input file cannot be read
     */
    private PPFTree buildTree(String input, Map<Integer, PPFPListEntry> ppfpList) throws IOException {
        PPFTree tree = new PPFTree();
        Comparator<Integer> order = PPFTree.supportDescendingOrder(ppfpList);
        List<Integer> items = new ArrayList<Integer>();
        List<Double> probabilities = new ArrayList<Double>();
        Map<Integer, Double> probabilityOfItem = new HashMap<Integer, Double>();
        int tid = 0;
        TransactionReader reader = new TransactionReader(input);
        try {
            while (reader.readTransaction(items, probabilities)) {
                tid++;
                List<Integer> kept = new ArrayList<Integer>();
                probabilityOfItem.clear();
                for (int i = 0; i < items.size(); i++) {
                    if (ppfpList.containsKey(items.get(i))) {
                        kept.add(items.get(i));
                        probabilityOfItem.put(items.get(i), probabilities.get(i));
                    }
                }
                if (kept.isEmpty()) {
                    continue;
                }
                Collections.sort(kept, order);
                List<Double> keptProbabilities = new ArrayList<Double>(kept.size());
                for (Integer item : kept) {
                    keptProbabilities.add(probabilityOfItem.get(item));
                }
                tree.addTransaction(kept, keptProbabilities, tid);
            }
        } finally {
            reader.close();
        }
        tree.createHeaderList(null, ppfpList);
        return tree;
    }

    /**
     * Mine a (conditional) PPFP-tree. The pattern being extended is
     * itemsetBuffer[0..prefixLength-1].
     *
     * @param tree         the tree of the transactions containing the current pattern
     * @param prefixLength number of items of the current pattern
     * @param ppfpList     the PPFP-list of the items of the tree
     * @throws IOException if the output file cannot be written
     */
    private void ppfpGrowth(PPFTree tree, int prefixLength, Map<Integer, PPFPListEntry> ppfpList) throws IOException {
        while (!tree.headerList.isEmpty()) {
            int item = tree.headerList.get(tree.headerList.size() - 1);
            itemsetBuffer[prefixLength] = item;
            saveItemset(prefixLength + 1, ppfpList.get(item));

            if (prefixLength + 1 < maxPatternLength) {
                mineConditionalTree(tree, item, prefixLength);
            }
            // the tail item is now fully processed: move its TIDs to its parents
            tree.removeTailItem();
        }
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Build the conditional database of beta = (current pattern) + item from
     * the prefix paths of item, compute its PPFP-list, then build and mine its
     * conditional tree.
     *
     * @param tree         the tree containing the current pattern
     * @param item         the item appended to the current pattern
     * @param prefixLength number of items of the current pattern
     * @throws IOException if the output file cannot be written
     */
    private void mineConditionalTree(PPFTree tree, int item, int prefixLength) throws IOException {
        List<List<PPFNode>> prefixPaths = new ArrayList<List<PPFNode>>();
        Map<Integer, Occurrences> occurrencesByItem = new HashMap<Integer, Occurrences>();

        for (PPFNode node = tree.mapItemNodes.get(item); node != null; node = node.nodeLink) {
            if (node.parent.itemID == PPFNode.ROOT_ITEM) {
                continue;
            }
            // E_t(beta) for each transaction t of this path
            double[] betaProbabilities = new double[node.tids.size()];
            for (int j = 0; j < betaProbabilities.length; j++) {
                betaProbabilities[j] = node.probability * tree.getPrefixProbability(node.tids.get(j));
            }
            List<PPFNode> prefixPath = new ArrayList<PPFNode>();
            prefixPath.add(node);
            for (PPFNode ancestor = node.parent; ancestor.itemID != PPFNode.ROOT_ITEM; ancestor = ancestor.parent) {
                prefixPath.add(ancestor);
                Occurrences occurrences = occurrencesByItem.get(ancestor.itemID);
                if (occurrences == null) {
                    occurrences = new Occurrences();
                    occurrencesByItem.put(ancestor.itemID, occurrences);
                }
                // E_t(beta + ancestor) = E_t(ancestor) * E_t(beta)
                for (int j = 0; j < betaProbabilities.length; j++) {
                    occurrences.add(node.tids.get(j), ancestor.probability * betaProbabilities[j]);
                }
            }
            prefixPaths.add(prefixPath);
        }

        Map<Integer, PPFPListEntry> betaPPFPList = new HashMap<Integer, PPFPListEntry>();
        for (Map.Entry<Integer, Occurrences> e : occurrencesByItem.entrySet()) {
            e.getValue().sortByTid();
            PPFPListEntry entry = evaluate(e.getValue());
            if (entry != null) {
                betaPPFPList.put(e.getKey(), entry);
            }
        }
        if (betaPPFPList.isEmpty()) {
            return;
        }

        PPFTree betaTree = new PPFTree();
        for (List<PPFNode> prefixPath : prefixPaths) {
            betaTree.addPrefixPath(prefixPath, betaPPFPList, tree);
        }
        if (!betaTree.root.children.isEmpty()) {
            betaTree.createHeaderList(tree.headerList, betaPPFPList);
            ppfpGrowth(betaTree, prefixLength + 1, betaPPFPList);
        }
    }

    /**
     * Compute the measures of a pattern from its occurrences and check the
     * three thresholds.
     *
     * @param occurrences the occurrences of the pattern, sorted by TID
     * @return the PPFP-list entry, or null if the pattern is not a PPFP
     */
    private PPFPListEntry evaluate(Occurrences occurrences) {
        double expectedSupport = 0;
        for (int i = 0; i < occurrences.size; i++) {
            expectedSupport += occurrences.probabilities[i];
        }
        if (expectedSupport < minSupAbsolute) {
            return null;
        }
        int periodicity = 0;
        int[] periods = PeriodicityProbability.periods(occurrences.tids, occurrences.size, databaseSize);
        for (int period : periods) {
            periodicity = Math.max(periodicity, period);
        }
        if (periodicity > maxPer) {
            return null;
        }
        double probability = PeriodicityProbability.compute(occurrences.tids, occurrences.probabilities,
                occurrences.size, databaseSize, maxPer, minProbability);
        if (probability < minProbability) {
            return null;
        }
        return new PPFPListEntry(expectedSupport, periodicity, probability);
    }

    /**
     * Write a pattern (itemsetBuffer[0..length-1]) to the output file or keep it in memory.
     *
     * @param length number of items of the pattern
     * @param entry  the measures of the pattern
     * @throws IOException if the output file cannot be written
     */
    private void saveItemset(int length, PPFPListEntry entry) throws IOException {
        itemsetCount++;
        int[] items = Arrays.copyOf(itemsetBuffer, length);
        Arrays.sort(items);
        if (writer != null) {
            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    buffer.append(' ');
                }
                buffer.append(items[i]);
            }
            buffer.append(" #SUP: ").append(formatNumber(entry.expectedSupport));
            buffer.append(" #MAXPER: ").append(entry.periodicity);
            buffer.append(" #PROBPER: ").append(formatNumber(entry.periodicProbability));
            writer.write(buffer.toString());
            writer.newLine();
        } else {
            patterns.addItemset(new Itemset(items, entry.expectedSupport, entry.periodicity,
                    entry.periodicProbability), length);
        }
    }

    /**
     * Format a real value without floating-point noise (e.g. 3.6 instead of 3.5999999999999996).
     *
     * @param value the value
     * @return the value rounded to {@link #OUTPUT_SIGNIFICANT_DIGITS} significant digits, without trailing zeros
     */
    static String formatNumber(double value) {
        return new BigDecimal(value).round(new MathContext(OUTPUT_SIGNIFICANT_DIGITS)).stripTrailingZeros()
                .toPlainString();
    }

    /** Print statistics about the latest execution to System.out. */
    public void printStats() {
        System.out.println("=============  PPFP-growth v 2.68 - STATS ===============");
        System.out.println(" Transactions: " + databaseSize);
        System.out.println(" maxPer = " + maxPer + " minSup = " + minSup + " (expected support >= " + minSupAbsolute
                + ") minProbability = " + minProbability);
        System.out.println(" Nodes of the initial PPFP-tree: " + initialTreeNodeCount);
        System.out.println(" Probabilistic periodic-frequent itemsets: " + itemsetCount);
        System.out.println(" Max memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println(" Total time ~ " + (endTime - startTimestamp) + " ms");
        System.out.println("===================================================");
    }

    /**
     * Set the maximum number of items of the patterns.
     *
     * @param length a positive number of items
     */
    public void setMaximumPatternLength(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("The maximum pattern length must be >= 1, got " + length + ".");
        }
        maxPatternLength = length;
    }

    /**
     * Get the size of the database of the latest execution.
     *
     * @return the number of transactions of the database of the latest execution
     */
    public int getDatabaseSize() {
        return databaseSize;
    }

    /**
     * Get the number of patterns found by the latest execution.
     *
     * @return the number of patterns found by the latest execution
     */
    public int getPatternCount() {
        return itemsetCount;
    }

    /** Occurrences of a pattern: TIDs and existential probabilities (growable parallel arrays). */
    private static final class Occurrences {
        /** TIDs of the occurrences (only the first {@link #size} entries are used) */
        int[] tids = new int[4];
        /** existential probability of the pattern in each occurrence (same indexes as tids) */
        double[] probabilities = new double[4];
        /** number of occurrences */
        int size = 0;

        /** Create an empty list of occurrences. */
        Occurrences() {
        }

        /**
         * Append an occurrence.
         *
         * @param tid         the TID of the transaction
         * @param probability the existential probability of the pattern in that transaction
         */
        void add(int tid, double probability) {
            if (size == tids.length) {
                tids = Arrays.copyOf(tids, 2 * size);
                probabilities = Arrays.copyOf(probabilities, 2 * size);
            }
            tids[size] = tid;
            probabilities[size] = probability;
            size++;
        }

        /** Sort by TID (occurrences collected from several prefix paths are not in TID order). */
        void sortByTid() {
            boolean sorted = true;
            for (int i = 1; i < size && sorted; i++) {
                sorted = tids[i - 1] < tids[i];
            }
            if (sorted) {
                return;
            }
            Integer[] order = new Integer[size];
            for (int i = 0; i < size; i++) {
                order[i] = i;
            }
            final int[] keys = tids;
            Arrays.sort(order, new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Integer.compare(keys[a], keys[b]);
                }
            });
            int[] sortedTids = new int[tids.length];
            double[] sortedProbabilities = new double[probabilities.length];
            for (int i = 0; i < size; i++) {
                sortedTids[i] = tids[order[i]];
                sortedProbabilities[i] = probabilities[order[i]];
            }
            tids = sortedTids;
            probabilities = sortedProbabilities;
        }
    }

    /**
     * Reads the transactions of an uncertain database, one per line, and
     * validates them. Two formats are accepted, one per line:
     * <ul>
     * <li>pairs "item probability" separated by spaces, e.g. {@code 1 0.5 3 1.0};</li>
     * <li>the SPMF uncertain format "item(probability)", e.g. {@code 1(0.5) 3(1.0)}.</li>
     * </ul>
     * Items are non-negative integers, probabilities are in (0, 1] and an item
     * may appear at most once in a transaction. Empty (or blank) lines and lines
     * starting with '#', '%' or '@' are ignored and do not count as transactions.
     */
    static final class TransactionReader implements Closeable {
        /** path of the file, used in error messages */
        private final String path;
        /** reader of the file */
        private final BufferedReader reader;
        /** items already read in the current transaction (to detect duplicates) */
        private final Set<Integer> seen = new HashSet<Integer>();
        /** number of the line being read (1-based, counting skipped lines) */
        private int lineNumber = 0;

        /**
         * Open a file.
         *
         * @param path path of the file
         * @throws IOException if the file cannot be opened
         */
        TransactionReader(String path) throws IOException {
            this.path = path;
            this.reader = new BufferedReader(new FileReader(path));
        }

        /**
         * Read the next transaction.
         *
         * @param items         cleared, then filled with the items
         * @param probabilities cleared, then filled with their existential probabilities
         * @return false at the end of the file
         * @throws IOException              if the file cannot be read
         * @throws IllegalArgumentException if the line is malformed
         */
        boolean readTransaction(List<Integer> items, List<Double> probabilities) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
                    continue;
                }
                parse(trimmed.split("\\s+"), items, probabilities);
                return true;
            }
            return false;
        }

        /**
         * Parse the tokens of a transaction line, in either accepted format.
         *
         * @param tokens        the tokens of the line
         * @param items         cleared, then filled with the items
         * @param probabilities cleared, then filled with their existential probabilities
         * @throws IllegalArgumentException if the line is malformed
         */
        private void parse(String[] tokens, List<Integer> items, List<Double> probabilities) {
            items.clear();
            probabilities.clear();
            seen.clear();
            if (tokens[0].indexOf('(') >= 0) {
                for (String token : tokens) {
                    int open = token.indexOf('(');
                    if (open <= 0 || !token.endsWith(")") || token.indexOf('(', open + 1) >= 0) {
                        throw error("malformed token '" + token + "', expected item(probability)");
                    }
                    add(token.substring(0, open), token.substring(open + 1, token.length() - 1), items, probabilities);
                }
            } else {
                if (tokens.length % 2 != 0) {
                    throw error("expected pairs 'item probability' but found " + tokens.length + " values");
                }
                for (int i = 0; i < tokens.length; i += 2) {
                    add(tokens[i], tokens[i + 1], items, probabilities);
                }
            }
        }

        /**
         * Validate one item and its probability, and append them.
         *
         * @param itemToken        the item, as written in the file
         * @param probabilityToken the probability, as written in the file
         * @param items            the items of the transaction
         * @param probabilities    the probabilities of the transaction
         * @throws IllegalArgumentException if the item or the probability is invalid, or the item is repeated
         */
        private void add(String itemToken, String probabilityToken, List<Integer> items, List<Double> probabilities) {
            int item;
            try {
                item = Integer.parseInt(itemToken);
            } catch (NumberFormatException e) {
                throw error("item '" + itemToken + "' is not an integer");
            }
            if (item < 0) {
                throw error("item " + item + " is negative; items must be non-negative integers");
            }
            double probability;
            try {
                probability = Double.parseDouble(probabilityToken);
            } catch (NumberFormatException e) {
                throw error("probability '" + probabilityToken + "' of item " + item + " is not a number");
            }
            if (!(probability > 0 && probability <= 1)) {
                throw error("probability " + probabilityToken + " of item " + item + " is not in (0, 1]");
            }
            if (!seen.add(item)) {
                throw error("item " + item + " appears more than once in the transaction");
            }
            items.add(item);
            probabilities.add(probability);
        }

        /**
         * Build an error for the current line.
         *
         * @param message description of the problem
         * @return an exception whose message gives the file and the line number
         */
        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid input file '" + path + "', line " + lineNumber + ": " + message);
        }

        /**
         * Close the file.
         *
         * @throws IOException if the file cannot be closed
         */
        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
