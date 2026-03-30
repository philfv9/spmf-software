package ca.pfv.spmf.algorithms.frequentpatterns.tm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.frequentpatterns.tm.TransactionTree.TransactionTreeNode;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;
/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
*/
/**
 * This is an implementation of the TM (Transaction Mapping) algorithm
 * for mining frequent itemsets with optimizations.
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoTM {

    /** Start time of the algorithm */
    private long startTimestamp;

    /** End time of the algorithm */
    private long endTimestamp;

    /** Number of frequent itemsets found */
    private int frequentItemsetCount;

    /** Minimum support threshold (absolute) */
    private int minsupRelative;

    /** The transaction tree */
    private TransactionTree transactionTree;

    /** Mapping from item to its interval list */
    private Map<Integer, List<Interval>> itemIntervalLists;

    /** Frequent 1-itemsets sorted by frequency (descending) */
    private List<Integer> frequentItemsFrequencyOrder;

    /** Support of each item */
    private Map<Integer, Integer> itemSupports;

    /** Result itemsets */
    private Itemsets frequentItemsets;

    /** Number of transactions in the database */
    private int transactionCount;

    /** Writer for output file */
    private BufferedWriter writer;

    /** Flag to indicate if results should be saved to memory */
    private boolean saveToMemory;

    /** Counter for early stopping triggers (for statistics) */
    private long earlyStopCount;

    /** Counter for combinations saved via all-same optimization */
    private long allCombinationsSavedCount;

    /** Comparator for sorting items by support descending, then by item ID */
    private Comparator<Integer> supportDescendingComparator;

    /** Compression coefficient threshold - can be configured */
    private double compressionThreshold = 2.0;

    /** Maximum itemset size for the buffer (adjust if needed) */
    private static final int MAX_ITEMSET_BUFFER_SIZE = 1000;

    /** Reusable buffer for building itemsets */
    private int[] itemsetBuffer;

    /** Pre-computed item rank array for O(1) sorting comparisons */
    private int[] itemRank;

    /** Maximum item ID in the dataset */
    private int maxItemId;

    /** Reusable buffer for remaining support calculation (list 1) */
    private int[] remainingBuffer1;

    /** Reusable buffer for remaining support calculation (list 2) */
    private int[] remainingBuffer2;

    /** Reusable result holder for interval intersections */
    private final IntersectionResultHolder reusableResultHolder = new IntersectionResultHolder();

    /** Reusable StringBuilder for output formatting */
    private final StringBuilder outputBuilder = new StringBuilder(256);

    /** Reusable buffer for filtered transactions */
    private int[] filteredTransactionBuffer;

    /** Initial capacity for remaining support buffers */
    private static final int INITIAL_REMAINING_BUFFER_SIZE = 1024;

    /**
     * Result holder for interval intersection - avoids creating new objects
     */
    private static class IntersectionResultHolder {
        List<Interval> intervals;
        int support;

        void set(List<Interval> intervals, int support) {
            this.intervals = intervals;
            this.support = support;
        }
    }

    /**
     * Default constructor
     */
    public AlgoTM() {
        remainingBuffer1 = new int[INITIAL_REMAINING_BUFFER_SIZE];
        remainingBuffer2 = new int[INITIAL_REMAINING_BUFFER_SIZE];
        filteredTransactionBuffer = new int[256];
    }

    /**
     * Set the compression coefficient threshold
     * @param threshold the threshold
     */
    public void setCompressionThreshold(double threshold) {
        this.compressionThreshold = threshold;
    }

    /**
     * Run the TM algorithm
     * @param input input file path
     * @param output  output file path
     * @param minsupp minimum support threshold
     */
    public Itemsets runAlgorithm(String input, String output, double minsupp) throws IOException {
        // Initialize
        MemoryLogger.getInstance().reset();
        startTimestamp = System.currentTimeMillis();
        frequentItemsetCount = 0;
        earlyStopCount = 0;
        allCombinationsSavedCount = 0;
        saveToMemory = (output == null);

        itemsetBuffer = new int[MAX_ITEMSET_BUFFER_SIZE];

        if (saveToMemory) {
            frequentItemsets = new Itemsets("FREQUENT ITEMSETS");
        } else {
            writer = new BufferedWriter(new FileWriter(output));
        }

        try {
            // ========== STEP 1: Single pass - read database and count items ==========
            itemSupports = new HashMap<>(1024);
            List<int[]> database = new ArrayList<>(1024);
            maxItemId = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#' || 
                        line.charAt(0) == '%' || line.charAt(0) == '@') {
                        continue;
                    }
                    int[] transaction = parseTransaction(line);
                    if (transaction.length > 0) {
                        database.add(transaction);
                        for (int item : transaction) {
                            itemSupports.merge(item, 1, Integer::sum);
                            if (item > maxItemId) {
                                maxItemId = item;
                            }
                        }
                    }
                }
            }
            
            transactionCount = database.size();

            // Calculate minimum support as absolute value
            minsupRelative = (int) Math.ceil(minsupp * transactionCount);
            if (minsupRelative == 0) {
                minsupRelative = 1;
            }

            // Filter infrequent items and build frequency-ordered list
            frequentItemsFrequencyOrder = new ArrayList<>(itemSupports.size());
            Iterator<Map.Entry<Integer, Integer>> it = itemSupports.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Integer> entry = it.next();
                if (entry.getValue() >= minsupRelative) {
                    frequentItemsFrequencyOrder.add(entry.getKey());
                } else {
                    it.remove();
                }
            }

            // Create comparator for sorting by support descending
            supportDescendingComparator = (a, b) -> {
                int cmp = Integer.compare(itemSupports.get(b), itemSupports.get(a));
                return (cmp != 0) ? cmp : Integer.compare(a, b);
            };

            frequentItemsFrequencyOrder.sort(supportDescendingComparator);

            // Pre-compute item rank array for O(1) sorting comparisons
            itemRank = new int[maxItemId + 1];
            for (int i = 0; i < frequentItemsFrequencyOrder.size(); i++) {
                itemRank[frequentItemsFrequencyOrder.get(i)] = i;
            }

            // Output frequent 1-itemsets
            for (int item : frequentItemsFrequencyOrder) {
                saveItemset(item, itemSupports.get(item));
            }

            // ========== STEP 2: Build transaction tree from in-memory database ==========
            transactionTree = new TransactionTree();
            
            // Ensure filteredTransactionBuffer is large enough
            int maxTransactionSize = 0;
            for (int[] transaction : database) {
                if (transaction.length > maxTransactionSize) {
                    maxTransactionSize = transaction.length;
                }
            }
            if (filteredTransactionBuffer.length < maxTransactionSize) {
                filteredTransactionBuffer = new int[maxTransactionSize];
            }
            
            for (int[] transaction : database) {
                int filteredCount = 0;
                
                for (int item : transaction) {
                    if (itemSupports.containsKey(item)) {
                        filteredTransactionBuffer[filteredCount++] = item;
                    }
                }
                
                if (filteredCount > 0) {
                    // Sort using pre-computed ranks (primitive array sort with custom comparator)
                    sortByRank(filteredTransactionBuffer, filteredCount);
                    transactionTree.insertTransaction(filteredTransactionBuffer, filteredCount);
                }
            }
            
            database = null;

            // ========== STEP 3: Construct transaction interval lists ==========
            itemIntervalLists = new HashMap<>(frequentItemsFrequencyOrder.size());
            for (int item : frequentItemsFrequencyOrder) {
                itemIntervalLists.put(item, new ArrayList<>());
            }
            
            MemoryLogger.getInstance().checkMemory();

            transactionTree.root.startId = 1;
            transactionTree.root.endId = transactionCount;

            mapTransactionIntervals(transactionTree.root);

            for (int item : frequentItemsFrequencyOrder) {
                List<Interval> intervals = itemIntervalLists.get(item);
                itemIntervalLists.put(item, mergeContiguousIntervals(intervals));
            }
            
            MemoryLogger.getInstance().checkMemory();

            // ========== STEP 4: Mine frequent itemsets using depth-first search ==========
            int frequentItemCount = frequentItemsFrequencyOrder.size();
            
            // Reusable lists for extension items and lists
            List<Integer> extensionItems = new ArrayList<>(frequentItemCount);
            List<TransactionList> extensionLists = new ArrayList<>(frequentItemCount);
            
            for (int i = 0; i < frequentItemCount; i++) {
                int item = frequentItemsFrequencyOrder.get(i);
                int support = itemSupports.get(item);
                List<Interval> intervals = itemIntervalLists.get(item);

                itemsetBuffer[0] = item;

                extensionItems.clear();
                extensionLists.clear();

                for (int j = i + 1; j < frequentItemCount; j++) {
                    int otherItem = frequentItemsFrequencyOrder.get(j);
                    List<Interval> otherIntervals = itemIntervalLists.get(otherItem);

                    if (intersectIntervalListsWithEarlyStopping(intervals, otherIntervals)) {
                        extensionItems.add(otherItem);
                        extensionLists.add(new TransactionList(
                                reusableResultHolder.intervals, 
                                reusableResultHolder.support, 
                                transactionCount));
                    }
                }

                if (!extensionItems.isEmpty()) {
                    depthFirstSearch(itemsetBuffer, 1, support, extensionItems, extensionLists, true);
                }
            }

            MemoryLogger.getInstance().checkMemory();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        endTimestamp = System.currentTimeMillis();

        return frequentItemsets;
    }

    /**
     * Sort primitive int array by pre-computed rank using insertion sort
     * (efficient for small arrays which transactions typically are)
     * @param array array of items to be sorted
     * @param length the length of the array
     */
    private void sortByRank(int[] array, int length) {
        for (int i = 1; i < length; i++) {
            int key = array[i];
            int keyRank = itemRank[key];
            int j = i - 1;
            while (j >= 0 && itemRank[array[j]] > keyRank) {
            	array[j + 1] = array[j];
                j--;
            }
            array[j + 1] = key;
        }
    }

    /**
     * Parses a transaction line into an array of item IDs.
     * <p>
     * This method uses an optimized two-pass parsing approach:
     * <ul>
     *   <li>First pass: counts the number of integers to allocate the exact array size</li>
     *   <li>Second pass: extracts the integer values</li>
     * </ul>
     * This avoids the overhead of using String.split() and Integer.parseInt(),
     * as well as dynamic resizing of collections.
     * </p>
     * 
     * @param line a transaction line containing space-separated non-negative integers
     * @return an array of item IDs parsed from the line; an empty array if no valid integers found
     */
    private int[] parseTransaction(String line) {
        int count = 0;
        int len = line.length();
        boolean inNumber = false;

        for (int i = 0; i <= len; i++) {
            char c = (i < len) ? line.charAt(i) : ' ';
            if (c >= '0' && c <= '9') {
                inNumber = true;
            } else if (inNumber) {
                count++;
                inNumber = false;
            }
        }

        int[] result = new int[count];
        int idx = 0;
        int num = 0;
        boolean hasNum = false;

        for (int i = 0; i <= len; i++) {
            char c = (i < len) ? line.charAt(i) : ' ';
            if (c >= '0' && c <= '9') {
                num = num * 10 + (c - '0');
                hasNum = true;
            } else if (hasNum) {
                result[idx++] = num;
                num = 0;
                hasNum = false;
            }
        }

        return result;
    }

    /**
     * Recursively assigns transaction ID intervals to nodes in the transaction tree
     * and populates the interval lists for each item.
     * <p>
     * This method performs a depth-first traversal of the transaction tree. For each node,
     * it assigns a contiguous range of transaction IDs [startId, endId] based on the node's
     * count (number of transactions passing through it). The interval is then added to the
     * corresponding item's interval list in {@link #itemIntervalLists}.
     * </p>
     * <p>
     * The transaction IDs are assigned sequentially, so sibling nodes receive non-overlapping
     * consecutive intervals.
     * </p>
     * 
     * @param node the current node in the transaction tree to process
     */
    private void mapTransactionIntervals(TransactionTreeNode node) {
        int currentStart = node.startId;

        for (TransactionTreeNode child : node.children) {
            child.startId = currentStart;
            child.endId = currentStart + child.count - 1;
            currentStart = child.endId + 1;

            itemIntervalLists.get(child.itemId).add(new Interval(child.startId, child.endId));

            mapTransactionIntervals(child);
        }
    }

    /**
     * Merges contiguous or overlapping intervals into a single interval.
     * <p>
     * Given a list of intervals (assumed to be sorted by start position), this method
     * combines intervals that are adjacent (end + 1 == next.start) or overlapping
     * into single intervals. This reduces the number of intervals and improves
     * the efficiency of subsequent intersection operations.
     * </p>
     * <p>
     * Example: [(1,3), (4,6), (10,12)] becomes [(1,6), (10,12)]
     * </p>
     * 
     * @param intervals the list of intervals to merge (must be sorted by start position)
     * @return a new list containing the merged intervals; returns the original list 
     *         if it contains 0 or 1 intervals
     */
    private List<Interval> mergeContiguousIntervals(List<Interval> intervals) {
        if (intervals.size() <= 1) {
            return intervals;
        }

        List<Interval> merged = new ArrayList<>(intervals.size());
        Interval current = intervals.get(0);
        int currentStart = current.start;
        int currentEnd = current.end;

        for (int i = 1; i < intervals.size(); i++) {
            Interval next = intervals.get(i);
            if (currentEnd + 1 >= next.start) {
                currentEnd = Math.max(currentEnd, next.end);
            } else {
                merged.add(new Interval(currentStart, currentEnd));
                currentStart = next.start;
                currentEnd = next.end;
            }
        }
        merged.add(new Interval(currentStart, currentEnd));

        return merged;
    }

    /**
     * Computes the intersection of two interval lists with early stopping optimization.
     * <p>
     * This method finds all overlapping regions between two sorted interval lists and
     * stores them in the reusable result holder along with the total support (sum of interval lengths).
     * The resulting intervals are merged if contiguous.
     * </p>
     * <p>
     * <b>Early Stopping Optimization:</b> The method calculates the remaining potential
     * support at each step. If the current matches plus the maximum remaining support
     * cannot reach the minimum support threshold, the computation is aborted early
     * and false is returned. This significantly reduces computation for infrequent itemsets.
     * </p>
     * 
     * @param list1 the first sorted interval list
     * @param list2 the second sorted interval list
     * @return true if intersection support is >= minsupRelative, false otherwise.
     *         Results are stored in reusableResultHolder.
     */
    private boolean intersectIntervalListsWithEarlyStopping(
            List<Interval> list1, List<Interval> list2) {

        List<Interval> result = new ArrayList<>(Math.min(list1.size(), list2.size()));

        int size1 = list1.size();
        int size2 = list2.size();

        // Ensure buffers are large enough
        ensureRemainingBufferCapacity(size1 + 1, size2 + 1);

        // Calculate remaining support arrays using reusable buffers
        calculateRemainingSupportArrayInto(list1, remainingBuffer1);
        calculateRemainingSupportArrayInto(list2, remainingBuffer2);

        int matches = 0;
        int i = 0, j = 0;

        while (i < size1 && j < size2) {
            Interval a = list1.get(i);
            Interval b = list2.get(j);

            if (a.end < b.start) {
                i++;
            } else if (b.end < a.start) {
                j++;
            } else {
                int start = Math.max(a.start, b.start);
                int end = Math.min(a.end, b.end);
                
                if (!result.isEmpty()) {
                    Interval last = result.get(result.size() - 1);
                    if (last.end + 1 >= start) {
                        last.end = Math.max(last.end, end);
                    } else {
                        result.add(new Interval(start, end));
                    }
                } else {
                    result.add(new Interval(start, end));
                }
                
                matches += (end - start + 1);

                if (a.end <= b.end) i++;
                if (b.end <= a.end) j++;
                
                continue;
            }
            
            if (i < size1 && j < size2) {
                int remaining = Math.min(remainingBuffer1[i], remainingBuffer2[j]);
                if (matches + remaining < minsupRelative) {
                    earlyStopCount++;
                    return false;
                }
            }
        }

        if (matches < minsupRelative) {
            return false;
        }

        reusableResultHolder.set(result, matches);
        return true;
    }

    /**
     * Ensures the remaining support buffers are large enough
     */
    private void ensureRemainingBufferCapacity(int size1, int size2) {
        if (remainingBuffer1.length < size1) {
            remainingBuffer1 = new int[size1 * 2];
        }
        if (remainingBuffer2.length < size2) {
            remainingBuffer2 = new int[size2 * 2];
        }
    }

    /**
     * Calculates a suffix sum array of support values for an interval list into a provided buffer.
     * <p>
     * For each position i, buffer[i] contains the total support (sum of interval lengths)
     * from position i to the end of the list. This is used by the early stopping optimization
     * in {@link #intersectIntervalListsWithEarlyStopping} to quickly determine the maximum
     * possible remaining support.
     * </p>
     * <p>
     * Example: For intervals with lengths [5, 3, 2], the result is [10, 5, 2, 0]
     * </p>
     * 
     * @param intervals the list of intervals
     * @param buffer the buffer to store results (must be at least size n+1)
     */
    private void calculateRemainingSupportArrayInto(List<Interval> intervals, int[] buffer) {
        int n = intervals.size();
        buffer[n] = 0;

        for (int i = n - 1; i >= 0; i--) {
            buffer[i] = buffer[i + 1] + intervals.get(i).length();
        }
    }

    /**
     * Computes the intersection of two BitSets with early stopping optimization.
     * <p>
     * This method performs a logical AND operation between two transaction ID BitSets
     * to find common transactions. Before performing the intersection, it checks if
     * the upper bound (minimum of both supports) can meet the minimum support threshold.
     * </p>
     * <p>
     * <b>Early Stopping:</b> If the upper bound of possible support is below the minimum
     * support threshold, the method returns null immediately without computing the intersection.
     * </p>
     * 
     * @param tids1 the first transaction ID BitSet
     * @param tids2 the second transaction ID BitSet
     * @param support1 the support (cardinality) of the first BitSet
     * @param support2 the support (cardinality) of the second BitSet
     * @return a new BitSet containing the intersection, or {@code null} if the result
     *         support is below {@link #minsupRelative}
     */
    private BitSet intersectBitSetsWithEarlyStopping(
            BitSet tids1, BitSet tids2, int support1, int support2) {

        int upperBound = Math.min(support1, support2);
        if (upperBound < minsupRelative) {
            earlyStopCount++;
            return null;
        }

        BitSet result = (BitSet) tids1.clone();
        result.and(tids2);

        if (result.cardinality() < minsupRelative) {
            return null;
        }

        return result;
    }

    /**
     * Calculates the average compression coefficient for a list of transaction lists.
     * <p>
     * The compression coefficient measures how efficiently the interval representation
     * compresses transaction data. It is defined as the average ratio of support to
     * list size across all transaction lists:
     * </p>
     * <pre>
     *     coefficient = (1/m) * Σ(support_i / listSize_i)
     * </pre>
     * <p>
     * A higher coefficient indicates better compression (fewer intervals represent
     * more transactions), suggesting that interval-based operations will be more efficient.
     * A lower coefficient suggests that BitSet representation may be more efficient.
     * </p>
     * <p>
     * This value is compared against {@link #compressionThreshold} to decide whether
     * to continue using interval representation or convert to BitSets.
     * </p>
     * 
     * @param transactionLists the list of transaction lists to analyze
     * @return the average compression coefficient, or 0 if the list is null or empty
     */
    private double calculateCompressionCoefficient(List<TransactionList> transactionLists) {
        if (transactionLists == null || transactionLists.isEmpty()) {
            return 0;
        }

        double sum = 0;
        int m = transactionLists.size();

        for (int i = 0; i < m; i++) {
            TransactionList tl = transactionLists.get(i);
            int si = tl.getSupport();
            int li = tl.getListSize();

            if (li > 0) {
                sum += (double) si / li;
            }
        }

        return sum / m;
    }

    /**
     * Depth-first search to find frequent itemsets.
     * Uses a reusable buffer for building itemsets.
     * 
     * @param prefix the current prefix buffer
     * @param prefixLength the current prefix length
     * @param prefixSupport the support of the prefix
     * @param extensionItems the items that can extend the prefix
     * @param extensionLists the transaction lists for each extension item
     * @param currentlyUsingIntervals whether we're currently using interval representation
     */
    private void depthFirstSearch(int[] prefix,
            int prefixLength,
            int prefixSupport,
            List<Integer> extensionItems,
            List<TransactionList> extensionLists,
            boolean currentlyUsingIntervals) throws IOException {

        // ============ ALL SAME SUPPORT OPTIMIZATION ============
        // Check if ALL extension items have the same support as the prefix.
        // If so, all their tidsets/intervals are identical to the prefix,
        // meaning we can output all 2^n - 1 combinations without any intersections.
        boolean allSameSupportAsPrefix = true;
        int extensionSize = extensionLists.size();
        for (int k = 0; k < extensionSize; k++) {
            if (extensionLists.get(k).getSupport() != prefixSupport) {
                allSameSupportAsPrefix = false;
                break;
            }
        }
        
        if (allSameSupportAsPrefix && !extensionItems.isEmpty()) {
            // All extensions have identical support to prefix - save all combinations
            saveAllCombinations(prefix, prefixLength, extensionItems, prefixSupport);
            return;
        }
        // ========================================================

        double coeff = calculateCompressionCoefficient(extensionLists);
        boolean shouldUseIntervals = (coeff >= compressionThreshold);

        if (currentlyUsingIntervals && !shouldUseIntervals) {
            for (int i = 0; i < extensionSize; i++) {
                extensionLists.get(i).convertToBitSet();
            }
            currentlyUsingIntervals = false;
        }

        int extensionCount = extensionItems.size();

        // Reusable lists for new extensions
        List<Integer> newExtensionItems = new ArrayList<>(extensionCount);
        List<TransactionList> newExtensionLists = new ArrayList<>(extensionCount);

        for (int i = 0; i < extensionCount; i++) {
            int extensionItem = extensionItems.get(i);
            TransactionList currentList = extensionLists.get(i);
            int support = currentList.getSupport();

            // Add extension item to prefix buffer and save
            prefix[prefixLength] = extensionItem;
            saveItemsetFromBuffer(prefix, prefixLength + 1, support);

            newExtensionItems.clear();
            newExtensionLists.clear();

            for (int j = i + 1; j < extensionCount; j++) {
                TransactionList otherList = extensionLists.get(j);
                TransactionList resultList;
                int newSupport;

                if (currentlyUsingIntervals && currentList.isUsingIntervals() && otherList.isUsingIntervals()) {
                    if (!intersectIntervalListsWithEarlyStopping(
                            currentList.intervals, otherList.intervals)) {
                        continue;
                    }
                    
                    newSupport = reusableResultHolder.support;
                    resultList = new TransactionList(reusableResultHolder.intervals, newSupport, transactionCount);
                } else {
                    if (currentList.isUsingIntervals()) {
                        currentList.convertToBitSet();
                    }

                    if (otherList.isUsingIntervals()) {
                        otherList.convertToBitSet();
                    }

                    int otherSupport = otherList.getSupport();

                    BitSet intersectionBitSet = intersectBitSetsWithEarlyStopping(
                            currentList.tidBitSet, otherList.tidBitSet, support, otherSupport);

                    if (intersectionBitSet == null) {
                        continue;
                    }
                    
                    newSupport = intersectionBitSet.cardinality();
                    resultList = new TransactionList(intersectionBitSet, newSupport);
                }

                newExtensionItems.add(extensionItems.get(j));
                newExtensionLists.add(resultList);
            }

            // Recursively mine with the extended prefix
            if (!newExtensionItems.isEmpty()) {
                depthFirstSearch(prefix, prefixLength + 1, support, 
                        newExtensionItems, newExtensionLists, currentlyUsingIntervals);
            }
        }
    }

    /**
     * Save all 2^n - 1 non-empty combinations of extension items when all have
     * identical support/tidsets as the prefix.
     * 
     * @param prefix the current prefix buffer
     * @param prefixLength the current prefix length
     * @param items the extension items (all with same support as prefix)
     * @param support the common support value
     */
    private void saveAllCombinations(int[] prefix, int prefixLength,
            List<Integer> items, int support) throws IOException {
        int n = items.size();
        
        long max = 1L << n;
        
        for (long mask = 1L; mask < max; mask++) {
            int newLen = prefixLength;

            // Build the itemset by adding items according to the bitmask
            for (int j = 0; j < n; j++) {
                if ((mask & (1L << j)) != 0L) {
                    prefix[newLen++] = items.get(j);
                }
            }

            // Save the itemset
            saveItemsetFromBuffer(prefix, newLen, support);
            allCombinationsSavedCount++;
        }
    }


    /**
     * Save a frequent 1-itemset (single item, no prefix)
     * 
     * @param item the single item
     * @param support the support
     */
    private void saveItemset(int item, int support) throws IOException {
        frequentItemsetCount++;

        if (saveToMemory) {
            Itemset itemsetObj = new Itemset(new int[]{item});
            itemsetObj.setAbsoluteSupport(support);
            frequentItemsets.addItemset(itemsetObj, 1);
        } else {
            outputBuilder.setLength(0);
            outputBuilder.append(item).append(" #SUP: ").append(support);
            writer.write(outputBuilder.toString());
            writer.newLine();
        }
    }

    /**
     * Save an itemset from the buffer.
     * 
     * @param buffer the buffer containing the itemset
     * @param length the length of the itemset in the buffer
     * @param support the support
     */
    private void saveItemsetFromBuffer(int[] buffer, int length, int support) throws IOException {
        frequentItemsetCount++;

        if (saveToMemory) {
            // Create a copy for storage
            int[] itemset = new int[length];
            System.arraycopy(buffer, 0, itemset, 0, length);
            
            Itemset itemsetObj = new Itemset(itemset);
            itemsetObj.setAbsoluteSupport(support);
            frequentItemsets.addItemset(itemsetObj, length);
        } else {
            // Write using StringBuilder to batch the output
            outputBuilder.setLength(0);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    outputBuilder.append(' ');
                }
                outputBuilder.append(buffer[i]);
            }
            outputBuilder.append(" #SUP: ").append(support);
            writer.write(outputBuilder.toString());
            writer.newLine();
        }
    }

    /**
     * Print statistics about the algorithm execution
     */
    public void printStats() {
        System.out.println("============= TM ALGORITHM 2.65 - STATS =============");
        System.out.println(" Frequent itemsets count: " + frequentItemsetCount);
        System.out.println(" Maximum memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
        System.out.println("    Transactions count: " + transactionCount);
        System.out.println("    Early stopping triggers: " + earlyStopCount);
        System.out.println("    All-combinations saved (identical tidsets): " + allCombinationsSavedCount);
        System.out.println("    Compression threshold: " + compressionThreshold);
        System.out.println("===================================================");
    }

    public int getTransactionCount() { return transactionCount; }
    public int getFrequentItemsetCount() { return frequentItemsetCount; }
    public long getExecutionTime() { return endTimestamp - startTimestamp; }
    public long getEarlyStopCount() { return earlyStopCount; }
    public long getAllCombinationsSavedCount() { return allCombinationsSavedCount; }
}