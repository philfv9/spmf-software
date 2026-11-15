package ca.pfv.spmf.algorithms.frequentpatterns.ubpminer;

/* This file is copyright (c) Philippe Fournier-Viger
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
* Do not remove copyright and license.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "UBP-Miner" algorithm for High-Utility
 * Itemsets Mining using utility bit partitions, utility-list joins, EUCS,
 * PU-Prune, and LA-Prune as described in the UBP-Miner paper.
 * 
 * Note: The implementation is not the original implementation but it tries to follow the paper
 * as closely as possible. Due to some ambiguities and design decisions, the implementation
 * may have differences from the original.
 *
 * <p>Two streaming database passes initialize TWU and then EUCS with the
 * singleton UBPs. The default partition capacity is 63 transactions, and
 * input/output use the standard SPMF utility-database and high-utility-itemset
 * formats.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoUBPMiner {
	/** Flag to indicate if debug information should be presented */
	private static final boolean DEBUG = false;
	
    /** The time at which the algorithm started. */
    public long startTimestamp;

    /** The time at which the algorithm ended. */
    public long endTimestamp;

    /** The number of high-utility itemsets generated. */
    public int huiCount;

    /** The number of candidate itemsets examined. */
    public long candidateCount;

    /** The default number of transactions stored in each bit partition. */
    private static final int DEFAULT_PARTITION_SIZE = 63;

    /** The maximum number of item pairs for which EUCS is stored as a triangular matrix. */
    private static final long MAX_EUCS_MATRIX_ENTRIES = 1L << 23;

    /** The absolute minimum-utility threshold. */
    private long minUtility;

    /** The number of transactions stored in each bit partition. */
    private int partitionSize;

    /** The number of partitions required by the database. */
    private int partitionCount;

    /** A reusable buffer containing the itemset currently being mined. */
    private int[] itemsetBuffer;

    /** The writer for the SPMF high-utility-itemset output file. */
    private BufferedWriter writer;

    /** A reusable builder used to format one output line. */
    private final StringBuilder lineBuilder = new StringBuilder();

    /** A reusable character buffer used to hand one output line to the writer without creating a string. */
    private char[] lineChars = new char[256];

    /** The number of joined utility lists constructed. */
    private long generatedUtilityLists;

    /** The number of nonempty partitions examined by pruning and joins. */
    private long visitedPartitions;

    /** The number of recursions skipped by the extension upper bound. */
    private long skippedByUpperBound;

    /** The number of joins rejected by PU-Prune. */
    private long puPrunedJoinCount;

    /** The number of joins rejected by LA-Prune. */
    private long laPrunedJoinCount;

    /** A map from each item to its transaction-weighted utility. */
    private LongLongMap itemToTWU;

    /** The estimated utility co-occurrence hash structure keyed by order pairs, used when the matrix is too large. */
    private LongLongMap eucs;

    /** The estimated utility co-occurrence triangular matrix indexed by order pairs, or null if the hash map is used. */
    private long[] eucsMatrix;

    /** The number of retained items, which is the dimension of the EUCS structure. */
    private int eucsItemCount;

    /** Shared contiguous element storage matching Algorithm 4's single list. */
    private ElementStore elementStore;

    /** The reusable transaction object filled by the streaming parser. */
    private final Transaction transaction = new Transaction();

    /** Reusable per-depth buffers holding the extension utility lists of each recursion level, including their recycled utility list objects. */
    private UtilityList[][] levelBuffers;
    
    /** peak memory of last execution **/
    private double peakMemory;

    /** Construct a UBP-Miner algorithm instance. */
    public AlgoUBPMiner() {
    }

    /**
     * Run UBP-Miner with 63 transactions per partition.
     *
     * @param input the input file path in SPMF utility-database format
     * @param output the output file path for SPMF high-utility itemsets
     * @param minUtility the absolute minimum-utility threshold
     * @throws IOException if an input or output file cannot be read or written
     */
    public void runAlgorithm(String input, String output, int minUtility) throws IOException {
        runAlgorithm(input, output, minUtility, DEFAULT_PARTITION_SIZE);
    }

    /**
     * Run UBP-Miner with a caller-selected partition capacity.
     *
     * @param input the input file path in SPMF utility-database format
     * @param output the output file path for SPMF high-utility itemsets
     * @param minUtility the positive absolute minimum-utility threshold
     * @param partitionSize the transactions per bit partition, from 1 through 63
     * @throws IOException if an input or output file cannot be read or written
     */
    public void runAlgorithm(String input, String output, int minUtility, int partitionSize)
            throws IOException {
        if (minUtility <= 0) throw new IllegalArgumentException("minUtility must be positive");
        if (partitionSize < 1 || partitionSize > 63)
            throw new IllegalArgumentException("partitionSize must be in [1, 63]");
        this.minUtility = minUtility;
        this.partitionSize = partitionSize;
        huiCount = 0;
        candidateCount = 0;
        generatedUtilityLists = 0;
        visitedPartitions = 0;
        skippedByUpperBound = 0;
        peakMemory = 0;
        puPrunedJoinCount = 0;
        laPrunedJoinCount = 0;
        MemoryLogger.getInstance().reset();
        startTimestamp = System.currentTimeMillis();

        int transactionCount = readDatabaseMetadata(input);
        partitionCount = (int) (((long) transactionCount + partitionSize - 1) / partitionSize);

        // Keep only promising items, then sort them by ascending TWU (ties by item id).
        // The TWU values are copied to arrays once so the comparator needs no hash lookups.
        int retainedCount = 0;
        for (int slot = 0; slot < itemToTWU.keys.length; slot++) {
            if (itemToTWU.values[slot] >= minUtility) retainedCount++;
        }
        int[] candidateItems = new int[retainedCount];
        long[] candidateTWU = new long[retainedCount];
        int fill = 0;
        for (int slot = 0; slot < itemToTWU.keys.length; slot++) {
            if (itemToTWU.values[slot] >= minUtility) {
                candidateItems[fill] = (int) itemToTWU.keys[slot];
                candidateTWU[fill] = itemToTWU.values[slot];
                fill++;
            }
        }
        Integer[] indexes = new Integer[retainedCount];
        for (int i = 0; i < retainedCount; i++) indexes[i] = i;
        Arrays.sort(indexes, (a, b) -> {
            int byTWU = Long.compare(candidateTWU[a], candidateTWU[b]);
            return byTWU != 0 ? byTWU : Integer.compare(candidateItems[a], candidateItems[b]);
        });
        int[] items = new int[retainedCount];
        for (int i = 0; i < retainedCount; i++) items[i] = candidateItems[indexes[i]];

        LongLongMap itemToOrder = new LongLongMap(items.length);
        for (int i = 0; i < items.length; i++) itemToOrder.put(items[i], i);
        elementStore = new ElementStore(1024);
        UtilityList[] initialLists = initializeUtilityLists(
                input, transactionCount, items, itemToOrder);
        itemToTWU = null;
        itemsetBuffer = new int[items.length];
        levelBuffers = new UtilityList[items.length + 2][];
        try (BufferedWriter out = new BufferedWriter(new FileWriter(output), 1 << 16)) {
            writer = out;
            writer.write("@FILETYPE=\"High-utility itemsets\"");
            writer.newLine();
            writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
            writer.newLine();
            search(0, null, initialLists, initialLists.length);
        }
        MemoryLogger.getInstance().checkMemory();
        peakMemory = MemoryLogger.getInstance().getMaxMemory();
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Compute TWU and the transaction count in the first database pass.
     *
     * @param input the input file path in SPMF utility-database format
     * @return the number of transactions in the database
     * @throws IOException if the input file cannot be read or parsed
     */
    private int readDatabaseMetadata(String input) throws IOException {
        itemToTWU = new LongLongMap(1024);
        itemToTWU.trackStamps();
        int transactionCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(input), 1 << 16)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCommentOrEmpty(line)) continue;
                Transaction parsed = parseTransaction(line, true);
                transactionCount++;
                long transactionUtility = parsed.transactionUtility;
                int[] parsedItems = parsed.items;
                int length = parsed.length;
                // The stamp of the map detects an item repeated inside one transaction
                // during the same lookup that accumulates its TWU.
                for (int i = 0; i < length; i++) {
                    if (!itemToTWU.addOnce(parsedItems[i], transactionUtility, transactionCount))
                        throw new IOException("Duplicate item in transaction: " + parsedItems[i]);
                }
            }
        }
        return transactionCount;
    }

    /**
     * Parse one SPMF utility transaction into the reusable transaction object,
     * validating it only when requested (first database pass).
     *
     * @param line the transaction line to parse
     * @param validate true to enforce the supported input contract, false for the second pass over already validated data
     * @return the reusable parsed transaction with aligned item and utility arrays
     * @throws IOException if the transaction violates the supported input contract
     */
    private Transaction parseTransaction(String line, boolean validate) throws IOException {
        int colon1 = line.indexOf(':');
        int colon2 = colon1 < 0 ? -1 : line.indexOf(':', colon1 + 1);
        if (colon2 < 0) throw new IOException("Invalid utility transaction: " + line);
        int colon3 = line.indexOf(':', colon2 + 1);
        int utilEnd = colon3 < 0 ? line.length() : colon3;
        Transaction t = transaction;

        int length = 0;
        int i = 0;
        while (true) {
            while (i < colon1 && line.charAt(i) <= ' ') i++;
            if (i >= colon1) break;
            int start = i;
            while (i < colon1 && line.charAt(i) > ' ') i++;
            long value = parseLong(line, start, i);
            if (value != (int) value)
                throw new NumberFormatException("For input string: \"" + line.substring(start, i) + "\"");
            if (length == t.items.length) t.ensureCapacity(length + 1);
            t.items[length++] = (int) value;
        }
        if (length == 0)
            throw new IOException("Transaction must contain at least one item: " + line);

        long[] utilities = t.utilities;
        long itemUtilitySum = 0;
        int count = 0;
        i = colon2 + 1;
        while (true) {
            while (i < utilEnd && line.charAt(i) <= ' ') i++;
            if (i >= utilEnd) break;
            int start = i;
            while (i < utilEnd && line.charAt(i) > ' ') i++;
            if (count == length) throw new IOException("Item/utility counts differ: " + line);
            long utility = parseLong(line, start, i);
            utilities[count++] = utility;
            if (validate) {
                if (utility < 0)
                    throw new IOException("Negative item utility is not supported: " + line);
                try {
                    itemUtilitySum = addExact(itemUtilitySum, utility);
                } catch (ArithmeticException overflow) {
                    throw new IOException("Sum of item utilities overflows signed long: " + line, overflow);
                }
            }
        }
        if (count != length) {
            if (count == 0)
                throw new IOException("Transaction must contain at least one item: " + line);
            throw new IOException("Item/utility counts differ: " + line);
        }
        t.length = length;

        long transactionUtility = parseLong(line, colon1 + 1, colon2);
        if (validate) {
            if (transactionUtility < 0) throw new IOException("Negative transaction utility: " + line);
            if (transactionUtility < itemUtilitySum)
                throw new IOException("Transaction utility is below the sum of item utilities: " + line);
        }
        t.transactionUtility = transactionUtility;
        return t;
    }

    /**
     * Parse a signed decimal long from a range of a string without allocating.
     *
     * @param s the string containing the number
     * @param from the inclusive start of the range
     * @param to the exclusive end of the range
     * @return the parsed value
     */
    private static long parseLong(String s, int from, int to) {
        while (from < to && s.charAt(from) <= ' ') from++;
        while (to > from && s.charAt(to - 1) <= ' ') to--;
        int len = to - from;
        if (len == 0 || len > 18) return Long.parseLong(s.substring(from, to));
        int i = from;
        boolean negative = false;
        char first = s.charAt(i);
        if (first == '-' || first == '+') {
            negative = first == '-';
            i++;
            if (i == to) throw new NumberFormatException("For input string: \"" + s.substring(from, to) + "\"");
        }
        long value = 0;
        for (; i < to; i++) {
            int digit = s.charAt(i) - '0';
            if (digit < 0 || digit > 9)
                throw new NumberFormatException("For input string: \"" + s.substring(from, to) + "\"");
            value = value * 10 + digit;
        }
        return negative ? -value : value;
    }

    /**
     * Build EUCS and singleton UBPs transaction-by-transaction in the second database pass.
     *
     * @param input the input file path in SPMF utility-database format
     * @param transactionCount the transaction count found by the first pass
     * @param items the retained items in ascending total order
     * @param itemToOrder the map from each retained item to its total-order position
     * @return the root extension array containing the singleton utility lists
     * @throws IOException if the input file cannot be read or changes between passes
     */
    private UtilityList[] initializeUtilityLists(String input, int transactionCount,
            int[] items, LongLongMap itemToOrder) throws IOException {
        UtilityList[] singletons = new UtilityList[items.length];
        for (int i = 0; i < items.length; i++) {
            singletons[i] = new UtilityList(items[i], partitionCount, elementStore);
            singletons[i].order = i;
        }
        eucsItemCount = items.length;
        long pairCount = (long) eucsItemCount * (eucsItemCount - 1) / 2;
        if (pairCount <= MAX_EUCS_MATRIX_ENTRIES) {
            // Triangular matrix of Property 5, indexed by the total-order positions.
            eucsMatrix = new long[(int) pairCount];
            eucs = null;
        } else {
            eucsMatrix = null;
            eucs = new LongLongMap(4096);
        }

        long[] sortKeys = new long[16];
        int[] sortedOrders = new int[16];
        int tid = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(input), 1 << 16)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCommentOrEmpty(line)) continue;
                if (tid >= transactionCount) throw new IOException("Input changed between database scans");
                Transaction parsed = parseTransaction(line, false);
                int length = parsed.length;
                int[] parsedItems = parsed.items;
                long[] parsedUtilities = parsed.utilities;
                if (sortKeys.length < length) {
                    sortKeys = new long[length];
                    sortedOrders = new int[length];
                }
                int retained = 0;
                for (int i = 0; i < length; i++) {
                    long order = itemToOrder.get(parsedItems[i]);
                    if (order >= 0) sortKeys[retained++] = (order << 32) | i;
                }
                Arrays.sort(sortKeys, 0, retained);
                // The first pass already verified that item utilities sum to at most the
                // transaction utility, so this running sum cannot overflow.
                long remaining = 0;
                for (int k = 0; k < retained; k++) {
                    int index = (int) (sortKeys[k] & 0xffffffffL);
                    remaining += parsedUtilities[index];
                    sortedOrders[k] = (int) (sortKeys[k] >>> 32);
                }
                long transactionUtility = parsed.transactionUtility;
                if (eucsMatrix != null) {
                    // Every cell is bounded by the TWU of its items, which the first pass
                    // already accumulated with overflow checking.
                    long[] matrix = eucsMatrix;
                    for (int a = 0; a < retained; a++) {
                        int rowBase = eucsRowBase(sortedOrders[a]);
                        for (int b = a + 1; b < retained; b++)
                            matrix[rowBase + sortedOrders[b]] += transactionUtility;
                    }
                } else {
                    for (int a = 0; a < retained; a++) {
                        for (int b = a + 1; b < retained; b++)
                            eucs.add(pairKey(sortedOrders[a], sortedOrders[b]), transactionUtility);
                    }
                }
                int partition = tid / partitionSize;
                int localPosition = tid - partition * partitionSize;
                long bit = 1L << localPosition;
                for (int k = 0; k < retained; k++) {
                    int index = (int) (sortKeys[k] & 0xffffffffL);
                    int order = sortedOrders[k];
                    long utility = parsedUtilities[index];
                    remaining -= utility;
                    singletons[order].append(partition, bit, tid, utility, remaining);
                }
                tid++;
            }
        }
        if (tid != transactionCount) throw new IOException("Input changed between database scans");
        for (UtilityList singleton : singletons) singleton.finish();
        return singletons;
    }

    /**
     * Search one utility-list extension group depth first.
     *
     * @param prefixLength the number of items in the current prefix
     * @param prefix the current prefix utility list, or null at the root
     * @param extensions the extension utility lists available at this level
     * @param extensionCount the number of valid utility lists in the extension array
     * @throws IOException if a high-utility itemset cannot be written
     */
    private void search(int prefixLength, UtilityList prefix, UtilityList[] extensions, int extensionCount)
            throws IOException {
        final long minUtil = minUtility;
        final long[] matrix = eucsMatrix;
        final ElementStore store = elementStore;
        for (int i = 0; i < extensionCount; i++) {
            UtilityList px = extensions[i];
            itemsetBuffer[prefixLength] = px.item;
            candidateCount++;
            long sumIutil = px.sumIutil;
            if (sumIutil >= minUtil) writeOut(prefixLength + 1, sumIutil);
            if (sumIutil + px.sumRutil < minUtil) {
                skippedByUpperBound++;
                continue;
            }

            // Elements of the children are stored after this mark and are released
            // once the subtree of Px has been explored, as in a utility-list buffer.
            int mark = store.size;
            int remainingCount = extensionCount - i - 1;
            int childCount = 0;
            UtilityList[] children = null;
            if (remainingCount > 0) {
                children = levelBuffers[prefixLength + 1];
                if (children == null || children.length < remainingCount) {
                    int oldLength = children == null ? 0 : children.length;
                    UtilityList[] grown = new UtilityList[Math.max(remainingCount, oldLength * 2)];
                    if (children != null) System.arraycopy(children, 0, grown, 0, oldLength);
                    children = grown;
                    levelBuffers[prefixLength + 1] = children;
                }
                int xOrder = px.order;
                int rowBase = matrix != null ? eucsRowBase(xOrder) : 0;
                for (int j = i + 1; j < extensionCount; j++) {
                    UtilityList py = extensions[j];
                    long estimate = matrix != null ? matrix[rowBase + py.order] : getEucs(xOrder, py.order);
                    if (estimate < minUtil) continue;
                    // The utility list object in the next free slot is recycled across
                    // siblings, because the subtrees of earlier siblings are already finished.
                    UtilityList spare = children[childCount];
                    if (spare == null) {
                        spare = new UtilityList(py.item, partitionCount, store);
                        children[childCount] = spare;
                    }
                    if (construct(prefix, px, py, spare) != null) childCount++;
                }
            }
            if (childCount > 0) search(prefixLength + 1, px, children, childCount);
            if ((candidateCount & 1023L) == 0) MemoryLogger.getInstance().checkMemory();
            store.truncate(mark);
        }
    }

    /**
     * Construct a joined utility list using PU-Prune, LA-Prune, and bitset intersections.
     *
     * @param prefix the shared prefix utility list, or null for a two-item join
     * @param px the left extension utility list
     * @param py the right extension utility list
     * @return the joined utility list, or null if a pruning bound rejects it
     */
    UtilityList construct(UtilityList prefix, UtilityList px, UtilityList py) {
        return construct(prefix, px, py, null);
    }

    /**
     * Construct a joined utility list using PU-Prune, LA-Prune, and bitset intersections,
     * optionally writing the result into a recycled utility list object.
     *
     * @param prefix the shared prefix utility list, or null for a two-item join
     * @param px the left extension utility list
     * @param py the right extension utility list
     * @param target the utility list object to overwrite with the result, or null to allocate a new one
     * @return the joined utility list, or null if a pruning bound rejects it
     */
    UtilityList construct(UtilityList prefix, UtilityList px, UtilityList py, UtilityList target) {
        // Property 4 (PU-Prune): if Py has any support in a partition, all
        // Suru(Px,Pk) there is a safe upper bound for the possible joint
        // utility in that partition. Aggregate across those partitions before
        // comparing with the global threshold; pruning partitions individually
        // against minUtility would not be safe. The scan stops as soon as the
        // aggregate reaches minUtility because the join can no longer be pruned.
        // Both nonempty-partition arrays are sorted, so they are merged with two pointers.
        // Utility sums below are bounded by the TWU of an item, which the first
        // database pass already accumulated with overflow checking, so they need
        // no further overflow checks inside the join.
        final long minUtil = minUtility;
        int[] yPartitions = py.nonEmptyPartitions;
        int yCount = py.nonEmptyPartitionCount;
        int[] xPartitions = px.nonEmptyPartitions;
        int xCount = px.nonEmptyPartitionCount;
        long[] pxSuru = px.suru;
        long visited = 0;
        long puUpperBound = 0;
        int xAt = 0;
        for (int i = 0; i < yCount && xAt < xCount; i++) {
            int p = yPartitions[i];
            while (xAt < xCount && xPartitions[xAt] < p) xAt++;
            visited++;
            if (xAt < xCount && xPartitions[xAt] == p) {
                puUpperBound += pxSuru[xAt];
                if (puUpperBound >= minUtil) break;
            }
        }
        if (puUpperBound < minUtil) {
            visitedPartitions += visited;
            puPrunedJoinCount++;
            return null;
        }

        // Property 3 (LA-Prune): subtracting the Px upper-bound contributions
        // from transactions that do not contain Py leaves exactly the bound
        // over the common support. If it is below minutil, no joint extension
        // of Px and Py can be an HUI, so this join need not be materialized.
        // The bound is maintained in subtraction form while the partitions are
        // joined, so it is checked per partition (Algorithm 4, line 5) and the
        // partial result is discarded from the shared list as soon as it fails.
        ElementStore store = elementStore;
        int mark = store.size;
        long[] xTidsets = px.tidsets;
        long[] yTidsets = py.tidsets;
        int[] xStarts = px.partitionStarts;
        int[] yStarts = py.partitionStarts;
        int[] prefixPartitions = null;
        long[] prefixTidsets = null;
        int[] prefixStarts = null;
        boolean hasPrefix = prefix != null;
        if (hasPrefix) {
            prefixPartitions = prefix.nonEmptyPartitions;
            prefixTidsets = prefix.tidsets;
            prefixStarts = prefix.partitionStarts;
        }
        if (target == null) target = new UtilityList(py.item, partitionCount, store);
        int[] resultPartitions = target.nonEmptyPartitions;
        long[] resultTidsets = target.tidsets;
        long[] resultSuru = target.suru;
        int[] resultStarts = target.partitionStarts;
        int prefixAt = 0;
        long potential = px.sumIutil + px.sumRutil;
        xAt = 0;
        int resultCount = 0;
        long resultIutil = 0;
        long resultRutil = 0;
        boolean pruned = false;
        for (int i = 0; i < yCount; i++) {
            int p = yPartitions[i];
            while (xAt < xCount && xPartitions[xAt] < p) {
                potential -= pxSuru[xAt];
                xAt++;
            }
            visited++;
            if (potential < minUtil) {
                pruned = true;
                break;
            }
            if (xAt == xCount) break;
            if (xPartitions[xAt] != p) continue;
            long xBits = xTidsets[xAt];
            long yBits = yTidsets[i];
            long common = xBits & yBits;
            long resultBits = common;
            long contribution = 0;
            if (common != 0) {
                int xBase = xStarts[xAt];
                int yBase = yStarts[i];
                long prefixBits = 0;
                int prefixBase = 0;
                if (hasPrefix) {
                    while (prefixPartitions[prefixAt] < p) prefixAt++;
                    prefixBits = prefixTidsets[prefixAt];
                    prefixBase = prefixStarts[prefixAt];
                }
                int partitionStart = store.size;
                store.ensureCapacity(partitionStart + Long.bitCount(common));
                int[] tids = store.tids;
                long[] iutils = store.iutils;
                long[] rutils = store.rutils;
                int at = partitionStart;
                long partitionSuru = 0;
                while (common != 0) {
                    long below = (common & -common) - 1;
                    int xIndex = xBase + Long.bitCount(xBits & below);
                    int yIndex = yBase + Long.bitCount(yBits & below);
                    long xIutil = iutils[xIndex];
                    contribution += xIutil + rutils[xIndex];
                    long itemUtility;
                    if (hasPrefix) {
                        // Ex - P is nonnegative because the prefix is a subset of Px,
                        // so adding Ey afterwards stays within the transaction utility.
                        itemUtility = (xIutil - iutils[prefixBase + Long.bitCount(prefixBits & below)])
                                + iutils[yIndex];
                    } else {
                        itemUtility = xIutil + iutils[yIndex];
                    }
                    long yRutil = rutils[yIndex];
                    tids[at] = tids[xIndex];
                    iutils[at] = itemUtility;
                    rutils[at] = yRutil;
                    at++;
                    partitionSuru += itemUtility + yRutil;
                    resultIutil += itemUtility;
                    resultRutil += yRutil;
                    common &= common - 1;
                }
                store.size = at;
                if (resultCount == resultPartitions.length) {
                    target.growPartitionIndex();
                    resultPartitions = target.nonEmptyPartitions;
                    resultTidsets = target.tidsets;
                    resultSuru = target.suru;
                    resultStarts = target.partitionStarts;
                }
                resultPartitions[resultCount] = p;
                resultTidsets[resultCount] = resultBits;
                resultSuru[resultCount] = partitionSuru;
                resultStarts[resultCount] = partitionStart;
                resultCount++;
            }
            potential -= pxSuru[xAt] - contribution;
            xAt++;
            if (potential < minUtil) {
                pruned = true;
                break;
            }
        }
        if (!pruned) {
            while (xAt < xCount) {
                potential -= pxSuru[xAt];
                xAt++;
            }
            pruned = potential < minUtil;
        }
        visitedPartitions += visited;
        if (pruned) {
            laPrunedJoinCount++;
            store.truncate(mark);
            return null;
        }

        target.item = py.item;
        target.order = py.order;
        target.nonEmptyPartitionCount = resultCount;
        target.finishDirect(resultIutil, resultRutil);
        generatedUtilityLists++;
        return target;
    }

    /**
     * Write one high-utility itemset using the SPMF #UTIL output format.
     *
     * @param length the number of items currently stored in the itemset buffer
     * @param utility the exact utility of the itemset
     * @throws IOException if the output file cannot be written
     */
    private void writeOut(int length, long utility) throws IOException {
        StringBuilder line = lineBuilder;
        line.setLength(0);
        for (int i = 0; i < length; i++) {
            if (i > 0) line.append(' ');
            line.append(itemsetBuffer[i]);
        }
        line.append(" #UTIL: ").append(utility).append('\n');
        int size = line.length();
        if (lineChars.length < size) lineChars = new char[Math.max(size, lineChars.length * 2)];
        line.getChars(0, size, lineChars, 0);
        writer.write(lineChars, 0, size);
        huiCount++;
    }

    /**
     * Get the offset of a row of the triangular EUCS matrix, so that the cell of
     * the pair (a, b) with a below b is the row offset plus b.
     *
     * @param a the smaller total-order position of the pair
     * @return the row offset of a, which may be negative for the first rows
     */
    private int eucsRowBase(int a) {
        return (int) ((long) a * (2L * eucsItemCount - a - 1) / 2) - a - 1;
    }

    /**
     * Get the estimated utility co-occurrence value of a pair of items.
     *
     * @param a the total-order position of the first item, which must be smaller than b
     * @param b the total-order position of the second item
     * @return the pair's EUCS value, or zero if the pair never occurs
     */
    private long getEucs(int a, int b) {
        if (eucsMatrix != null) return eucsMatrix[eucsRowBase(a) + b];
        long value = eucs.get(pairKey(a, b));
        return value < 0 ? 0 : value;
    }

    /**
     * Encode an unordered pair of integer identifiers in one long key.
     *
     * @param a the first identifier
     * @param b the second identifier
     * @return a unique key for the unordered pair
     */
    private static long pairKey(int a, int b) {
        int low = Math.min(a, b), high = Math.max(a, b);
        return ((long) low << 32) | (high & 0xffffffffL);
    }

    /**
     * Add two utility values without allowing silent signed-long overflow.
     *
     * @param a the first utility value
     * @param b the second utility value
     * @return the exact signed-long sum
     */
    private static long addExact(long a, long b) {
        return Math.addExact(a, b);
    }

    /**
     * Check whether a database line is empty, a comment, or SPMF metadata.
     *
     * @param line the database line to inspect
     * @return true if the line should be skipped, and false otherwise
     */
    private static boolean isCommentOrEmpty(String line) {
        int length = line.length();
        int start = 0;
        while (start < length && line.charAt(start) <= ' ') start++;
        if (start == length) return true;
        char first = line.charAt(start);
        return first == '#' || first == '%' || first == '@';
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  UBP-Miner 2.68 - STATS =============");
        System.out.println(" High utility itemset count: " + huiCount);
        if(DEBUG) {
            System.out.println(" Candidate itemsets: " + candidateCount);
            System.out.println(" Constructed utility lists: " + generatedUtilityLists);
            System.out.println(" Nonempty partitions visited: " + visitedPartitions);
            System.out.println(" Joins pruned by PU-Prune: " + puPrunedJoinCount);
            System.out.println(" Joins pruned by LA-Prune: " + laPrunedJoinCount);
            System.out.println(" Recursions pruned by utility upper bound: " + skippedByUpperBound);
        }
        System.out.println(" Maximum memory usage: " + peakMemory + " MB");
        System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
        System.out.println("===============================================");
    }


    /**
     * Get the number of joined utility lists constructed by the latest run.
     *
     * @return the generated utility-list count
     */
    public long getGeneratedUtilityListCount() { return generatedUtilityLists; }

    /**
     * Get the number of nonempty partitions visited by the latest run.
     *
     * @return the visited partition count
     */
    public long getVisitedPartitionCount() { return visitedPartitions; }

    /**
     * Get the number of recursions skipped by the extension upper bound.
     *
     * @return the upper-bound prune count
     */
    public long getUpperBoundPruneCount() { return skippedByUpperBound; }

    /**
     * Get the number of joins rejected by PU-Prune.
     *
     * @return the PU-Prune join count
     */
    public long getPUPruneCount() { return puPrunedJoinCount; }

    /**
     * Get the number of joins rejected by LA-Prune.
     *
     * @return the LA-Prune join count
     */
    public long getLAPruneCount() { return laPrunedJoinCount; }

    /** A reusable parsed utility transaction used during the streaming database passes. */
    private static final class Transaction {
        /** The item identifiers in input order, valid up to the length field. */
        int[] items = new int[32];

        /** The item utilities aligned with the item identifiers, valid up to the length field. */
        long[] utilities = new long[32];

        /** The number of items in the current transaction. */
        int length;

        /** The transaction utility declared in the input. */
        long transactionUtility;

        /**
         * Grow the item and utility arrays so that at least the requested number of entries fit.
         *
         * @param required the number of entries that must fit
         */
        void ensureCapacity(int required) {
            if (required <= items.length) return;
            int newCapacity = Math.max(required, items.length * 2);
            items = Arrays.copyOf(items, newCapacity);
            utilities = Arrays.copyOf(utilities, newCapacity);
        }
    }

    /**
     * An open-addressing hash map from long keys to nonnegative long values.
     * It avoids the boxing and per-entry objects of the standard hash maps.
     */
    private static final class LongLongMap {
        /** The keys of the occupied slots. */
        long[] keys;

        /** The values of the slots, where a negative value marks an empty slot. */
        long[] values;

        /** The last stamp recorded for each slot, or null if stamps are not tracked. */
        int[] stamps;

        /** The number of occupied slots. */
        int size;

        /** The bit mask used to wrap slot indexes. */
        int mask;

        /**
         * Create an empty map.
         *
         * @param expectedSize the number of entries the map is expected to hold
         */
        LongLongMap(int expectedSize) {
            int capacity = 16;
            while (capacity < (long) expectedSize * 2) capacity <<= 1;
            keys = new long[capacity];
            values = new long[capacity];
            Arrays.fill(values, -1L);
            mask = capacity - 1;
        }

        /** Enable the per-slot stamps used by addOnce on this map. */
        void trackStamps() {
            stamps = new int[keys.length];
        }

        /**
         * Find the slot holding a key, or the empty slot where it would be inserted.
         *
         * @param key the key to locate
         * @return the slot index of the key or of the empty slot ending its probe sequence
         */
        private int find(long key) {
            int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 32) & mask;
            while (values[slot] >= 0 && keys[slot] != key) slot = (slot + 1) & mask;
            return slot;
        }

        /**
         * Get the value stored for a key.
         *
         * @param key the key to look up
         * @return the stored value, or -1 if the key is absent
         */
        long get(long key) {
            int slot = find(key);
            return values[slot];
        }

        /**
         * Store a nonnegative value for a key, replacing any previous value.
         *
         * @param key the key to store
         * @param value the nonnegative value to associate with the key
         */
        void put(long key, long value) {
            int slot = find(key);
            if (values[slot] < 0) {
                keys[slot] = key;
                values[slot] = value;
                if (++size > (keys.length >> 1)) grow();
            } else {
                values[slot] = value;
            }
        }

        /**
         * Add a nonnegative amount to the value of a key, inserting the key if absent.
         *
         * @param key the key to update
         * @param delta the nonnegative amount to add
         */
        void add(long key, long delta) {
            int slot = find(key);
            if (values[slot] < 0) {
                keys[slot] = key;
                values[slot] = delta;
                if (++size > (keys.length >> 1)) grow();
            } else {
                values[slot] = addExact(values[slot], delta);
            }
        }

        /**
         * Add a nonnegative amount to the value of a key unless the key was already
         * added with the same stamp, which signals a repeated key within one group.
         *
         * @param key the key to update
         * @param delta the nonnegative amount to add
         * @param stamp the identifier of the current group, which must be positive
         * @return true if the amount was added, or false if the key repeated within the group
         */
        boolean addOnce(long key, long delta, int stamp) {
            int slot = find(key);
            if (values[slot] < 0) {
                keys[slot] = key;
                values[slot] = delta;
                stamps[slot] = stamp;
                if (++size > (keys.length >> 1)) grow();
                return true;
            }
            if (stamps[slot] == stamp) return false;
            stamps[slot] = stamp;
            values[slot] = addExact(values[slot], delta);
            return true;
        }

        /** Double the capacity of the map and reinsert all entries. */
        private void grow() {
            long[] oldKeys = keys;
            long[] oldValues = values;
            int[] oldStamps = stamps;
            int capacity = oldKeys.length << 1;
            keys = new long[capacity];
            values = new long[capacity];
            if (oldStamps != null) stamps = new int[capacity];
            Arrays.fill(values, -1L);
            mask = capacity - 1;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldValues[i] >= 0) {
                    int slot = find(oldKeys[i]);
                    keys[slot] = oldKeys[i];
                    values[slot] = oldValues[i];
                    if (oldStamps != null) stamps[slot] = oldStamps[i];
                }
            }
        }
    }

    /** A utility-list element containing itemset and remaining utilities. */
    static final class Element {
        /** The transaction identifier associated with this element. */
        final int tid;

        /** The itemset utility in the transaction. */
        final long iutil;

        /** The remaining utility of later items in the transaction. */
        final long rutil;

        /**
         * Create a utility-list element.
         *
         * @param tid the transaction identifier
         * @param iutil the itemset utility in the transaction
         * @param rutil the remaining utility of later items
         */
        Element(int tid, long iutil, long rutil) {
            this.tid = tid;
            this.iutil = iutil;
            this.rutil = rutil;
        }
    }

    /**
     * The shared contiguous element list of Algorithm 4, stored as parallel
     * primitive arrays so that elements need no per-element objects.
     */
    static final class ElementStore {
        /** The transaction identifiers of the stored elements. */
        int[] tids;

        /** The itemset utilities of the stored elements. */
        long[] iutils;

        /** The remaining utilities of the stored elements. */
        long[] rutils;

        /** The number of elements currently stored. */
        int size;

        /**
         * Create an empty element store.
         *
         * @param capacity the initial number of elements the store can hold
         */
        ElementStore(int capacity) {
            tids = new int[capacity];
            iutils = new long[capacity];
            rutils = new long[capacity];
        }

        /**
         * Grow the arrays so that at least the requested number of elements fit.
         *
         * @param required the number of elements that must fit
         */
        void ensureCapacity(int required) {
            if (required <= tids.length) return;
            long grown = Math.max((long) required, (long) tids.length * 2);
            int newCapacity = (int) Math.min(grown, Integer.MAX_VALUE - 8);
            if (newCapacity < required)
                throw new OutOfMemoryError("Element store exceeds the maximum array size");
            tids = Arrays.copyOf(tids, newCapacity);
            iutils = Arrays.copyOf(iutils, newCapacity);
            rutils = Arrays.copyOf(rutils, newCapacity);
        }

        /**
         * Release all elements stored at or after the given position.
         *
         * @param newSize the number of elements to keep
         */
        void truncate(int newSize) {
            size = newSize;
        }

        /**
         * Retrieve an element by its absolute position.
         *
         * @param position the absolute position in the store
         * @return the element stored at the requested position
         */
        Element get(int position) {
            return new Element(tids[position], iutils[position], rutils[position]);
        }
    }

    /** A utility bit partition index into the shared transaction-element list. */
    static final class UtilityList {
        /** The last item of the itemset represented by this utility list. */
        int item;

        /** The total-order position of the last item of the itemset. */
        int order;

        /** The transaction bitset stored for each nonempty partition, aligned with the partition identifiers. */
        long[] tidsets;

        /** The sum of itemset and remaining utilities for each nonempty partition, aligned with the partition identifiers. */
        long[] suru;

        /** The identifiers of nonempty partitions in transaction order. */
        int[] nonEmptyPartitions;

        /** The number of identifiers stored in the nonempty-partition array. */
        int nonEmptyPartitionCount;

        /** Absolute per-partition offsets into the shared element list, aligned with the partition identifiers. */
        int[] partitionStarts;

        /** The shared contiguous list containing finished UBP elements. */
        private final ElementStore elementStore;

        /** The temporary transaction identifiers used while this UBP is being built. */
        private int[] buildTids;

        /** The temporary itemset utilities used while this UBP is being built. */
        private long[] buildIutils;

        /** The temporary remaining utilities used while this UBP is being built. */
        private long[] buildRutils;

        /** The number of elements in the temporary construction buffers. */
        private int buildSize;

        /** Whether this UBP has been finished and can no longer be appended to. */
        private boolean finished;

        /** The sum of itemset utilities over all elements. */
        long sumIutil;

        /** The sum of remaining utilities over all elements. */
        long sumRutil;

        /** The partition of the most recently appended element. */
        private int lastAppendedPartition = -1;

        /**
         * Create an empty utility list with compact per-partition index arrays.
         *
         * @param item the last item of the represented itemset
         * @param partitionCount the number of partitions in the database
         * @param elementStore the shared contiguous element list
         */
        UtilityList(int item, int partitionCount, ElementStore elementStore) {
            this.item = item;
            this.elementStore = elementStore;
            int capacity = Math.max(1, Math.min(partitionCount, 4));
            tidsets = new long[capacity];
            suru = new long[capacity];
            nonEmptyPartitions = new int[capacity];
            partitionStarts = new int[capacity];
        }

        /**
         * Create a utility list whose partition index arrays are already complete.
         *
         * @param item the last item of the represented itemset
         * @param order the total-order position of the last item
         * @param elementStore the shared contiguous element list
         * @param partitions the nonempty partition identifiers in transaction order
         * @param tidsets the transaction bitsets aligned with the partitions
         * @param suru the Suru values aligned with the partitions
         * @param starts the absolute element offsets aligned with the partitions
         */
        UtilityList(int item, int order, ElementStore elementStore, int[] partitions,
                long[] tidsets, long[] suru, int[] starts) {
            this.item = item;
            this.order = order;
            this.elementStore = elementStore;
            this.nonEmptyPartitions = partitions;
            this.nonEmptyPartitionCount = partitions.length;
            this.tidsets = tidsets;
            this.suru = suru;
            this.partitionStarts = starts;
        }

        /** Double the capacity of the per-partition index arrays, keeping their content. */
        void growPartitionIndex() {
            int nextCapacity = Math.max(1, nonEmptyPartitions.length * 2);
            nonEmptyPartitions = Arrays.copyOf(nonEmptyPartitions, nextCapacity);
            tidsets = Arrays.copyOf(tidsets, nextCapacity);
            suru = Arrays.copyOf(suru, nextCapacity);
            partitionStarts = Arrays.copyOf(partitionStarts, nextCapacity);
        }

        /**
         * Get the number of supporting transactions in a partition.
         *
         * @param partition the partition index
         * @return the number of elements stored for the partition
         */
        int support(int partition) {
            int slot = Arrays.binarySearch(nonEmptyPartitions, 0, nonEmptyPartitionCount, partition);
            return slot < 0 ? 0 : Long.bitCount(tidsets[slot]);
        }

        /**
         * Append one element in transaction order and update its partition index.
         *
         * @param partition the partition containing the transaction
         * @param bit the single transaction bit within the partition
         * @param element the utility element to append
         */
        void append(int partition, long bit, Element element) {
            append(partition, bit, element.tid, element.iutil, element.rutil);
        }

        /**
         * Append one element in transaction order and update its partition index.
         *
         * @param partition the partition containing the transaction
         * @param bit the single transaction bit within the partition
         * @param tid the transaction identifier of the element
         * @param iutil the itemset utility of the element
         * @param rutil the remaining utility of the element
         */
        void append(int partition, long bit, int tid, long iutil, long rutil) {
            if (finished) throw new IllegalStateException("Cannot append to a finished UBP");
            if (partition != lastAppendedPartition) {
                if (nonEmptyPartitionCount == nonEmptyPartitions.length) growPartitionIndex();
                nonEmptyPartitions[nonEmptyPartitionCount++] = partition;
                lastAppendedPartition = partition;
            }
            int slot = nonEmptyPartitionCount - 1;
            tidsets[slot] |= bit;
            suru[slot] += iutil + rutil;
            if (buildTids == null) {
                buildTids = new int[8];
                buildIutils = new long[8];
                buildRutils = new long[8];
            } else if (buildSize == buildTids.length) {
                int nextCapacity = buildSize * 2;
                buildTids = Arrays.copyOf(buildTids, nextCapacity);
                buildIutils = Arrays.copyOf(buildIutils, nextCapacity);
                buildRutils = Arrays.copyOf(buildRutils, nextCapacity);
            }
            buildTids[buildSize] = tid;
            buildIutils[buildSize] = iutil;
            buildRutils[buildSize] = rutil;
            buildSize++;
        }

        /**
         * Move construction elements into shared storage and finalize all offsets.
         */
        void finish() {
            if (finished) throw new IllegalStateException("UBP already finished");
            int at = elementStore.size;
            for (int k = 0; k < nonEmptyPartitionCount; k++) {
                partitionStarts[k] = at;
                at += Long.bitCount(tidsets[k]);
            }

            long iutilTotal = 0;
            long rutilTotal = 0;
            if (buildSize > 0) {
                elementStore.ensureCapacity(at);
                System.arraycopy(buildTids, 0, elementStore.tids, elementStore.size, buildSize);
                System.arraycopy(buildIutils, 0, elementStore.iutils, elementStore.size, buildSize);
                System.arraycopy(buildRutils, 0, elementStore.rutils, elementStore.size, buildSize);
                for (int i = 0; i < buildSize; i++) {
                    iutilTotal += buildIutils[i];
                    rutilTotal += buildRutils[i];
                }
                elementStore.size = at;
            }
            sumIutil = iutilTotal;
            sumRutil = rutilTotal;
            // Drop the per-UBP construction buffers: all persistent elements now
            // occupy the shared contiguous list used by Algorithm 4's offsets.
            buildTids = null;
            buildIutils = null;
            buildRutils = null;
            buildSize = 0;
            finished = true;
            if (nonEmptyPartitions.length != nonEmptyPartitionCount) {
                nonEmptyPartitions = Arrays.copyOf(nonEmptyPartitions, nonEmptyPartitionCount);
                tidsets = Arrays.copyOf(tidsets, nonEmptyPartitionCount);
                suru = Arrays.copyOf(suru, nonEmptyPartitionCount);
                partitionStarts = Arrays.copyOf(partitionStarts, nonEmptyPartitionCount);
            }
        }

        /**
         * Finish a UBP whose elements were written directly into the shared list.
         *
         * @param sumIutil the sum of itemset utilities over all elements
         * @param sumRutil the sum of remaining utilities over all elements
         */
        void finishDirect(long sumIutil, long sumRutil) {
            this.sumIutil = sumIutil;
            this.sumRutil = sumRutil;
            finished = true;
        }

        /**
         * Retrieve an element by its absolute shared-list position.
         *
         * @param absolutePosition the absolute position in the shared element list
         * @return the element stored at the requested position
         */
        Element elementAt(int absolutePosition) {
            return elementStore.get(absolutePosition);
        }

        /**
         * Copy this UBP's elements into transaction order for verification.
         *
         * @return an array containing this UBP's elements in transaction order
         */
        Element[] elementsInTidOrder() {
            int total = 0;
            for (int k = 0; k < nonEmptyPartitionCount; k++) total += Long.bitCount(tidsets[k]);
            Element[] result = new Element[total];
            int at = 0;
            for (int k = 0; k < nonEmptyPartitionCount; k++) {
                int support = Long.bitCount(tidsets[k]);
                for (int i = 0; i < support; i++)
                    result[at++] = elementAt(partitionStarts[k] + i);
            }
            return result;
        }
    }
}