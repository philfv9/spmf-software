package ca.pfv.spmf.algorithms.frequentpatterns.emaui;

/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license information.
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the EMAUI algorithm for High Average-Utility
 * Itemset Mining as described in the paper:
 * Gufeng Li, Tao Shang and Yinling Zhang (2022). Efficient mining high
 * average-utility itemsets with effective pruning strategies and novel list
 * structure. Applied Intelligence. DOI: 10.1007/s10489-022-03722-x.
 *
 * <p>The threshold is absolute, as in HAUI-Miner. Item utilities must be
 * nonnegative, items must be distinct within each transaction, and utility
 * sums must fit in a signed long. The input must remain stable between scans.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoEMAUI {
	/** If true, debug information is shown **/
	public boolean DEBUG_MODE = false;
	
    /** The maximum observed used Java heap in megabytes. */
    public double maxMemory;

    /** The time at which the algorithm started. */
    public long startTimestamp;

    /** The time at which the algorithm ended. */
    public long endTimestamp;

    /** The number of high average-utility itemsets generated. */
    public long huiCount;

    /** The number of attempted KFAU-list joins. */
    public long candidateCount;

    /** The number of completed nonempty joined KFAU-lists. */
    public long generatedUtilityListCount;

    /** The number of itemsets whose extensions were pruned by EMU. */
    public long emuPrunedCount;

    /** The number of joins stopped by early abandoning. */
    public long earlyAbandonedCount;

    /** The number of joins completed without early abandoning, including empty joins. */
    public long completedJoinCount;

    /** The number of joined elements built, including partially abandoned lists. */
    public long joinElementCount;

    /** The number of transaction elements evaluated by EMU. */
    public long emuElementCount;

    /** The number of times RMUB was sufficient (EqRmub in the paper). */
    public long rmubCount;

    /** The number of times the second-maximum fallback was needed (NEqRmub). */
    public long revisedBoundCount;

    /** The number of decreasing-average transaction bounds. */
    public long decreasingBoundCount;

    /** The number of transaction elements without a remaining item. */
    public long terminalBoundCount;

    /** The number of input transactions. */
    public int transactionCount;

    /** The absolute minimum average utility. */
    private long minAUtility;

    /** A lower floating-point bound on the threshold, used only for pruning. */
    private double minAUtilityLower;

    /** A map from each item to its average-utility upper bound. */
    private Map<Integer, Long> mapItemToAUUB;

    /** A buffer containing the current prefix. */
    private int[] itemsetBuffer;

    /** The output file writer. */
    private BufferedWriter writer;

    /** A reusable output buffer. */
    private StringBuilder outputBuffer;

    /** Whether to prune extensions using EMU. */
    private boolean upperBoundPruning = true;

    /** Whether to abandon unpromising joins early. */
    private boolean earlyAbandoning = true;

    /** An item and its utility in a transaction. */
    static class Pair {
        /** The item identifier. */
        final int item;
        /** The utility of the item. */
        final long utility;
        /** The item's global AUUB rank. */
        final int rank;

        Pair(int item, long utility) {
            this(item, utility, 0);
        }

        Pair(int item, long utility, int rank) {
            this.item = item;
            this.utility = utility;
            this.rank = rank;
        }
    }

    /** Reusable parsed transaction storage for one database scan. */
    private static class Transaction {
        /** The item identifiers. */
        int[] items = new int[16];
        /** The corresponding item utilities. */
        long[] utilities = new long[16];
        /** Scratch item identifiers for duplicate detection. */
        int[] sortedItems = new int[16];
        /** The number of items in the current transaction. */
        int size;
        /** The maximum item utility. */
        long maximum;

        void ensureCapacity(int size) {
            if (size > items.length) {
                int capacity = Math.max(size, items.length + items.length / 2);
                items = Arrays.copyOf(items, capacity);
                utilities = Arrays.copyOf(utilities, capacity);
                sortedItems = Arrays.copyOf(sortedItems, capacity);
            }
        }
    }

    /** An element of a key feature average-utility list. */
    static class Element {
        /** The transaction identifier. */
        final int tid;
        /** The utility of the itemset in the transaction. */
        final long iu;
        /** The maximum utility strictly after the last item. */
        final long mu;
        /** The distance to the rightmost remaining maximum. */
        final int ln;
        /** The maximum utility strictly after that maximum. */
        final long smu;
        /** The number of remaining items. */
        final int rn;
        /** An upper-rounded estimate for proper extensions. */
        double emu;

        Element(int tid, long iu, long mu, int ln, long smu, int rn) {
            this.tid = tid;
            this.iu = iu;
            this.mu = mu;
            this.ln = ln;
            this.smu = smu;
            this.rn = rn;
        }
    }

    /** The KFAU-list of an itemset ending with one item. */
    static class UtilityList {
        /** The last item of the itemset. */
        final int item;
        /** The elements in ascending transaction order. */
        final List<Element> elements = new ArrayList<>();
        /** The sum of itemset utilities. */
        long sumIutils;
        /** An upper-rounded sum of extension estimates. */
        double sumEmu;

        UtilityList(int item) {
            this.item = item;
        }

        /** Add an element and update the utility sum. */
        void addElement(Element element) {
            sumIutils = Math.addExact(sumIutils, element.iu);
            elements.add(element);
        }
    }

    /** Construct an EMAUI algorithm instance. */
    public AlgoEMAUI() {
    }

    /**
     * Run the algorithm using the same threshold API as HAUI-Miner.
     *
     * @param input the input utility database path
     * @param output the output itemset file path
     * @param minAUtility the positive absolute minimum average utility
     * @throws IOException if an input or output file cannot be read or written
     */
    public void runAlgorithm(String input, String output, int minAUtility) throws IOException {
        runAlgorithm(input, output, (long) minAUtility);
    }

    /**
     * Run the algorithm with a long-valued absolute threshold.
     *
     * @param input the input utility database path
     * @param output the output itemset file path
     * @param minAUtility the positive absolute minimum average utility
     * @throws IOException if a file cannot be read or written, or input is invalid
     * @throws ArithmeticException if a utility sum overflows a signed long
     */
    public void runAlgorithm(String input, String output, long minAUtility) throws IOException {
        if (minAUtility <= 0) {
            throw new IllegalArgumentException("minAUtility must be positive");
        }
        this.minAUtility = minAUtility;
        minAUtilityLower = Math.nextDown((double) minAUtility);
        maxMemory = 0;
        huiCount = 0;
        candidateCount = 0;
        generatedUtilityListCount = 0;
        emuPrunedCount = 0;
        earlyAbandonedCount = 0;
        completedJoinCount = 0;
        joinElementCount = 0;
        emuElementCount = 0;
        rmubCount = 0;
        revisedBoundCount = 0;
        decreasingBoundCount = 0;
        terminalBoundCount = 0;
        transactionCount = 0;
        startTimestamp = System.currentTimeMillis();
        endTimestamp = startTimestamp;

    	MemoryLogger.getInstance().reset();

        try {
            // First scan: calculate AUUB and remove unpromising items.
            readAUUB(input);
            List<Integer> items = new ArrayList<>();
            for (Map.Entry<Integer, Long> entry : mapItemToAUUB.entrySet()) {
                if (entry.getValue() >= minAUtility) items.add(entry.getKey());
            }
            items.sort((a, b) -> {
                int compare = Long.compare(mapItemToAUUB.get(a), mapItemToAUUB.get(b));
                return compare == 0 ? Integer.compare(a, b) : compare;
            });

            // Second scan: initialize the singleton KFAU-lists.
            List<UtilityList> lists = initializeLists(input, items);
            itemsetBuffer = new int[items.size()];
            outputBuffer = new StringBuilder();
            MemoryLogger.getInstance().checkMemory();
            try (BufferedWriter out = new BufferedWriter(new FileWriter(output))) {
                writer = out;
                writer.write("@FILETYPE=\"High average-utility itemsets\"");
                writer.newLine();
                writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
                writer.newLine();
                mine(0, null, lists);
            }
        } finally {
            writer = null;
            MemoryLogger.getInstance().checkMemory();
            maxMemory = MemoryLogger.getInstance().getMaxMemory();
            endTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Calculate each item's AUUB in the first database scan.
     *
     * @param input the input file path
     * @throws IOException if the file cannot be read or contains invalid input
     */
    private void readAUUB(String input) throws IOException {
        mapItemToAUUB = new HashMap<>();
        Transaction transaction = new Transaction();
        try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCommentOrEmpty(line)) continue;
                parseTransaction(line, transaction);
                transactionCount = Math.incrementExact(transactionCount);
                for (int i = 0; i < transaction.size; i++) {
                    int item = transaction.items[i];
                    Long auub = mapItemToAUUB.get(item);
                    mapItemToAUUB.put(item, auub == null ? transaction.maximum
                            : Math.addExact(auub, transaction.maximum));
                }
                if ((transactionCount & 1023) == 0) MemoryLogger.getInstance().checkMemory();
            }
        }
    }

    /**
     * Construct singleton KFAU-lists in the second database scan.
     *
     * @param input the input file path
     * @param items the promising items in ascending AUUB order
     * @return the singleton lists in that order
     * @throws IOException if the file cannot be read or contains invalid input
     */
    private List<UtilityList> initializeLists(String input, List<Integer> items) throws IOException {
        Map<Integer, Integer> order = new HashMap<>();
        Map<Integer, UtilityList> byItem = new HashMap<>();
        List<UtilityList> lists = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            int item = items.get(i);
            order.put(item, i);
            UtilityList list = new UtilityList(item);
            byItem.put(item, list);
            lists.add(list);
        }
        int tid = 0;
        Transaction transaction = new Transaction();
        List<Pair> revised = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCommentOrEmpty(line)) continue;
                parseTransaction(line, transaction);
                revised.clear();
                for (int i = 0; i < transaction.size; i++) {
                    int item = transaction.items[i];
                    Integer rank = order.get(item);
                    if (rank != null) {
                        revised.add(new Pair(item, transaction.utilities[i], rank));
                    } else if (!mapItemToAUUB.containsKey(item)) {
                        throw new IOException("The input database changed between scans");
                    }
                }
                revised.sort((a, b) -> Integer.compare(a.rank, b.rank));
                addTransactionElements(tid, revised, byItem);
                tid = Math.incrementExact(tid);
                if ((tid & 1023) == 0) MemoryLogger.getInstance().checkMemory();
            }
        }
        if (tid != transactionCount) throw new IOException("The input database changed between scans");
        return lists;
    }

    /**
     * Capture the suffix features of one revised transaction in a backward scan.
     *
     * @param tid the transaction identifier
     * @param revised the transaction in ascending AUUB order
     * @param byItem the singleton lists to fill
     */
    static void addTransactionElements(int tid, List<Pair> revised, Map<Integer, UtilityList> byItem) {
        long maximum = 0;
        long secondMaximum = 0;
        int maximumPosition = -1;
        for (int i = revised.size() - 1; i >= 0; i--) {
            Pair pair = revised.get(i);
            int length = maximumPosition == -1 ? 0 : maximumPosition - i;
            byItem.get(pair.item).addElement(new Element(tid, pair.utility, maximum,
                    length, secondMaximum, revised.size() - i - 1));
            // Keep the rightmost maximum when several items have equal utility.
            if (maximumPosition == -1 || pair.utility > maximum) {
                secondMaximum = maximum;
                maximum = pair.utility;
                maximumPosition = i;
            }
        }
    }

    /**
     * Mine the ordered extensions of a prefix (Algorithm 2).
     *
     * @param prefixLength the number of items in the prefix buffer
     * @param prefixList the KFAU-list of the prefix, or null for the root
     * @param lists the KFAU-lists of the prefix extensions
     * @throws IOException if the output cannot be written
     */
    private void mine(int prefixLength, UtilityList prefixList, List<UtilityList> lists)
            throws IOException {
        for (int i = 0; i < lists.size(); i++) {
            UtilityList x = lists.get(i);
            int length = prefixLength + 1;
            itemsetBuffer[prefixLength] = x.item;
            // Integer division makes the integer-threshold test exact.
            if (x.sumIutils / length >= minAUtility) writeOut(length, x.sumIutils);

            calculateEMU(x, length);
            if (upperBoundPruning && x.sumEmu < minAUtilityLower) {
                emuPrunedCount++;
                lists.set(i, null);
                continue;
            }
            List<UtilityList> extensions = new ArrayList<>();
            for (int j = i + 1; j < lists.size(); j++) {
                candidateCount++;
                UtilityList joined = construct(prefixList, x, lists.get(j));
                if (joined != null) {
                    completedJoinCount++;
                    if (!joined.elements.isEmpty()) {
                        generatedUtilityListCount++;
                        extensions.add(joined);
                    }
                }
            }
            // Earlier siblings are no longer needed; x remains the child prefix.
            lists.set(i, null);
            if (!extensions.isEmpty()) mine(length, x, extensions);
            MemoryLogger.getInstance().checkMemory();
        }
    }

    /**
     * Calculate and store the EMU estimates (Definition 17 and Algorithm 3).
     *
     * @param list the KFAU-list
     * @param length the itemset length
     */
    void calculateEMU(UtilityList list, int length) {
        list.sumEmu = 0;
        for (Element element : list.elements) {
            emuElementCount++;
            element.emu = estimate(element, length, this);
            list.sumEmu = addUpper(list.sumEmu, element.emu);
        }
    }

    /**
     * Calculate an upper-rounded EMU for proper extensions in one transaction.
     *
     * @param element the transaction element
     * @param length the itemset length
     * @return an upper bound on the average utility of each proper extension
     */
    static double estimate(Element element, int length) {
        return estimate(element, length, null);
    }

    /** Evaluate Definition 17 and optionally record its selected case. */
    private static double estimate(Element element, int length, AlgoEMAUI statistics) {
        if (element.rn == 0) {
            if (statistics != null) statistics.terminalBoundCount++;
            return 0;
        }
        // Definition 17 compares with the transaction average, not au(X) in D.
        if (element.mu <= element.iu / length) {
            // This also handles remaining items whose utilities are all zero.
            if (statistics != null) statistics.decreasingBoundCount++;
            return averageUpper(element.iu, element.mu, length + 1);
        }
        int denominator = length + element.ln;
        try {
            long numerator = Math.addExact(element.iu, Math.multiplyExact(element.mu, element.ln));
            if (element.smu <= numerator / denominator) {
                if (statistics != null) statistics.rmubCount++;
                return divideUpper(numerator, denominator);
            }
            numerator = Math.addExact(numerator,
                    Math.multiplyExact(element.smu, element.rn - element.ln));
            if (statistics != null) statistics.revisedBoundCount++;
            return divideUpper(numerator, length + element.rn);
        } catch (ArithmeticException e) {
            // Inflated bound numerators can overflow even when true utilities fit.
            BigInteger numerator = BigInteger.valueOf(element.iu).add(
                    BigInteger.valueOf(element.mu).multiply(BigInteger.valueOf(element.ln)));
            if (BigInteger.valueOf(element.smu).multiply(BigInteger.valueOf(denominator))
                    .compareTo(numerator) <= 0) {
                if (statistics != null) statistics.rmubCount++;
                return divideUpper(numerator.doubleValue(), denominator);
            }
            numerator = numerator.add(BigInteger.valueOf(element.smu)
                    .multiply(BigInteger.valueOf(element.rn - element.ln)));
            if (statistics != null) statistics.revisedBoundCount++;
            return divideUpper(numerator.doubleValue(), length + element.rn);
        }
    }

    /** Average two nonnegative utilities without overflowing an intermediate. */
    private static double averageUpper(long iu, long mu, int denominator) {
        try {
            return divideUpper(Math.addExact(iu, mu), denominator);
        } catch (ArithmeticException e) {
            return divideUpper(BigInteger.valueOf(iu).add(BigInteger.valueOf(mu)).doubleValue(), denominator);
        }
    }

    /** Divide with conservative rounding, including the integer-to-double step. */
    private static double divideUpper(double numerator, int denominator) {
        return numerator == 0 ? 0 : Math.nextUp(Math.nextUp(numerator) / denominator);
    }

    /** Add two nonnegative upper bounds with upward rounding. */
    private static double addUpper(double first, double second) {
        if (first == 0) return second;
        if (second == 0) return first;
        return Math.nextUp(first + second);
    }

    /**
     * Join two sibling KFAU-lists, applying local EMU early abandoning.
     *
     * @param prefix the common prefix list, or null for singleton joins
     * @param x the first sibling list, with its EMU values already calculated
     * @param y the later sibling list
     * @return the joined list, or null when early abandoning prunes it
     */
    UtilityList construct(UtilityList prefix, UtilityList x, UtilityList y) {
        UtilityList result = new UtilityList(y.item);
        double localEmu = x.sumEmu;
        int j = 0;
        int p = 0;
        for (Element ex : x.elements) {
            while (j < y.elements.size() && y.elements.get(j).tid < ex.tid) j++;
            if (j == y.elements.size() || y.elements.get(j).tid != ex.tid) {
                if (earlyAbandoning) {
                    if (ex.emu != 0) localEmu = Math.max(0, Math.nextUp(localEmu - ex.emu));
                    if (localEmu < minAUtilityLower) {
                        earlyAbandonedCount++;
                        return null;
                    }
                }
                continue;
            }
            Element ey = y.elements.get(j++);
            long utility;
            if (prefix == null) {
                utility = Math.addExact(ex.iu, ey.iu);
            } else {
                while (p < prefix.elements.size() && prefix.elements.get(p).tid < ex.tid) p++;
                if (p == prefix.elements.size() || prefix.elements.get(p).tid != ex.tid) {
                    throw new IllegalStateException("A sibling transaction is missing from its prefix");
                }
                // Subtract first to avoid an overflowing temporary prefix sum.
                utility = Math.addExact(Math.subtractExact(ex.iu, prefix.elements.get(p).iu), ey.iu);
            }
            result.addElement(new Element(ex.tid, utility, ey.mu, ey.ln, ey.smu, ey.rn));
            joinElementCount++;
        }
        return result;
    }

    /**
     * Parse and validate one transaction in SPMF utility format.
     *
     * @param line the transaction line
     * @param transaction the reusable transaction storage to fill
     * @throws IOException if the transaction is malformed
     */
    private static void parseTransaction(String line, Transaction transaction) throws IOException {
        int first = line.indexOf(':');
        int second = first < 0 ? -1 : line.indexOf(':', first + 1);
        if (first < 0 || second < 0 || line.indexOf(':', second + 1) >= 0) {
            throw new IOException("Invalid utility transaction: " + line);
        }
        StringTokenizer items = new StringTokenizer(line.substring(0, first), " \t\r\n\f\013");
        StringTokenizer utilities = new StringTokenizer(line.substring(second + 1), " \t\r\n\f\013");
        int size = items.countTokens();
        if (size == 0 || size != utilities.countTokens()) {
            throw new IOException("Different numbers of items and utilities: " + line);
        }
        transaction.ensureCapacity(size);
        transaction.size = size;
        transaction.maximum = 0;
        try {
            long transactionUtility = Long.parseLong(line.substring(first + 1, second).trim());
            long sum = 0;
            for (int i = 0; i < size; i++) {
                int item = Integer.parseInt(items.nextToken());
                long utility = Long.parseLong(utilities.nextToken());
                if (utility < 0) throw new IOException("Negative utility: " + line);
                sum = Math.addExact(sum, utility);
                transaction.maximum = Math.max(transaction.maximum, utility);
                transaction.items[i] = item;
                transaction.utilities[i] = utility;
                transaction.sortedItems[i] = item;
            }
            if (transactionUtility < sum) {
                throw new IOException("Transaction utility is smaller than the item utility sum: " + line);
            }
            Arrays.sort(transaction.sortedItems, 0, size);
            for (int i = 1; i < size; i++) {
                if (transaction.sortedItems[i] == transaction.sortedItems[i - 1]) {
                    throw new IOException("Duplicate item: " + line);
                }
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid number in utility transaction: " + line, e);
        }
    }

    /** Return whether a line is empty, a comment, or SPMF metadata. */
    private static boolean isCommentOrEmpty(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '%'
                || trimmed.charAt(0) == '@';
    }

    /**
     * Write a high average-utility itemset in HAUI-Miner format.
     *
     * @param length the number of items in the prefix buffer
     * @param utility the total utility, before dividing by itemset length
     * @throws IOException if the output cannot be written
     */
    private void writeOut(int length, long utility) throws IOException {
        StringBuilder buffer = outputBuffer;
        buffer.setLength(0);
        for (int i = 0; i < length; i++) {
            if (i != 0) buffer.append(' ');
            buffer.append(itemsetBuffer[i]);
        }
        buffer.append(" #AUTIL: ");
        buffer.append(utility / (double) length);
        writer.write(buffer.toString());
        writer.newLine();
        huiCount++;
    }

    /**
     * Enable or disable EMU extension pruning for verification.
     *
     * @param enabled whether to enable EMU pruning, true by default
     */
    public void setUpperBoundPruning(boolean enabled) {
        upperBoundPruning = enabled;
    }

    /**
     * Enable or disable the early-abandoning strategy for verification.
     *
     * @param enabled whether to enable early abandoning, true by default
     */
    public void setEarlyAbandoning(boolean enabled) {
        earlyAbandoning = enabled;
    }

    /** Print statistics about the latest execution. */
    public void printStats() {
        System.out.println("=============  EMAUI ALGORITHM v2.68 - STATS =============");
        System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Memory ~ " + maxMemory + " MB");
        System.out.println(" High average-utility itemsets count : " + huiCount);
        if(DEBUG_MODE) {
	        System.out.println(" KFAU-list join attempts : " + candidateCount);
	        System.out.println(" Completed joins (including empty) : " + completedJoinCount);
	        System.out.println(" Completed nonempty joins : " + generatedUtilityListCount);
	        System.out.println(" Joined elements built : " + joinElementCount);
	        System.out.println(" EMU-pruned extensions : " + emuPrunedCount);
	        System.out.println(" Early-abandoned joins : " + earlyAbandonedCount);
	        System.out.println(" RMUB evaluations (EqRmub / NEqRmub) : " + rmubCount + " / " + revisedBoundCount);
	        System.out.println(" Decreasing / terminal EMU cases : " + decreasingBoundCount + " / " + terminalBoundCount);
	        long rmubEvaluations = rmubCount + revisedBoundCount;
	        System.out.println(" RMUB success % : " + (rmubEvaluations == 0 ? 0 : 100d * rmubCount / rmubEvaluations));
	        System.out.println(" EA % : " + (candidateCount == 0 ? 0 : 100d * earlyAbandonedCount / candidateCount));
        }
        System.out.println("===================================================");
    }
}
