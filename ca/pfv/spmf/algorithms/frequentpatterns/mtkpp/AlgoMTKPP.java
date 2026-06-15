package ca.pfv.spmf.algorithms.frequentpatterns.mtkpp;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
* Do not remove copyright and license information from this file.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the MTKPP algorithm for Mining Top-K Periodic Patterns.
 * It discovers the top-K periodic patterns satisfying a maximum periodicity constraint,
 * without requiring the user to set a minimum support threshold.
 * All patterns tied at the K-th rank are returned.
 *
 * @see TIDList
 * @author Philippe Fournier-Viger 2016
 */
public class AlgoMTKPP {

    /** the number of periodic patterns generated */
    public int patternCount = 0;

    /** the number of candidate patterns explored */
    public int candidateCount = 0;

    /** map to remember the support and periodicity of each item */
    static Map<Integer, ItemInfo> mapItemToItemInfo;

    /** writer to write the output file */
    BufferedWriter writer = null;

    /** flag for debug mode */
    boolean DEBUG = false;

    /** buffer for storing the current itemset during mining */
    final int BUFFERS_SIZE = 200;
    private int[] itemsetBuffer = null;

    /** buffer for storing the current transaction */
    final int TRANSACTION_BUFFERS_SIZE = 1000;
    private int[] transactionBuffer = null;

    /** the database size (number of transactions) */
    protected int databaseSize = 0;

    /** maximum periodicity threshold */
    int maxPeriodicity;

    /** the number of top-K patterns to find */
    int k;

    /** minimum number of items that patterns should contain */
    int minimumLength = 0;

    /** maximum number of items that patterns should contain */
    int maximumLength = Integer.MAX_VALUE;

    /** the support pruning threshold raised dynamically as top-K fills */
    protected double supportPruningThreshold = 1;

    /** the k-th highest support seen so far among candidates */
    private int kthHighestSupport = 0;

    /** the total execution time */
    public double totalExecutionTime = 0;

    /** the maximum memory usage */
    public double maximumMemoryUsage = 0;

    /** all candidate patterns collected during mining */
    private List<PatternEntry> allCandidates;

    /**
     * min-heap of size k tracking the k highest supports seen so far.
     * The root is always the smallest of the top-k supports.
     */
    private PriorityQueue<Integer> topKHeap;

    /**
     * This inner class represents information about a single item.
     */
    class ItemInfo {
        /** the support of the item */
        int support = 0;
        /** the largest periodicity of the item */
        int largestPeriodicity = 0;
        /** the last transaction where the item was seen */
        int lastSeenTransaction = 0;
    }

    /**
     * This inner class represents a pattern stored in the candidate result set.
     */
    class PatternEntry {
        /** the items in the pattern */
        int[] items;
        /** the support of the pattern */
        int support;
        /** the largest periodicity of the pattern */
        int largestPeriodicity;

        /**
         * Constructor for a pattern entry.
         * @param items the items in the pattern
         * @param support the support of the pattern
         * @param largestPeriodicity the largest periodicity of the pattern
         */
        PatternEntry(int[] items, int support, int largestPeriodicity) {
            this.items = items;
            this.support = support;
            this.largestPeriodicity = largestPeriodicity;
        }
    }

    /**
     * Default constructor.
     */
    public AlgoMTKPP() {
    }

    /**
     * Run the algorithm.
     * @param input the input file path
     * @param output the output file path
     * @param k the number of top-K patterns to find
     * @param maxPeriodicity the maximum periodicity threshold
     * @throws IOException exception if error while writing the file
     */
    public void runAlgorithm(String input, String output, int k, int maxPeriodicity)
            throws IOException {

        MemoryLogger.getInstance().reset();
        long startTimestamp = System.currentTimeMillis();

        this.k = k;
        this.maxPeriodicity = maxPeriodicity;

        itemsetBuffer = new int[BUFFERS_SIZE];
        allCandidates = new ArrayList<PatternEntry>();
        
        // min-heap: root is the smallest of the top-k supports
        topKHeap = new PriorityQueue<Integer>();
        kthHighestSupport = 0;
        supportPruningThreshold = 1;

        writer = new BufferedWriter(new FileWriter(output));

        mapItemToItemInfo = new HashMap<Integer, ItemInfo>();

        BufferedReader myInput = null;
        databaseSize = 0;
        String thisLine = null;

        // first database scan: compute support and largest periodicity per item
        try {
            myInput = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(input))));
            // For each line from the input file
            while ((thisLine = myInput.readLine()) != null) {
            	// Skip metadata lines
                if (thisLine.isEmpty() ||
                        thisLine.charAt(0) == '#' ||
                        thisLine.charAt(0) == '%' ||
                        thisLine.charAt(0) == '@') {
                    continue;
                }
                databaseSize++;
                // Split the transaction
                String[] items = thisLine.split(" ");
                for (int i = 0; i < items.length; i++) {
                	// Parse the item
                    Integer item = Integer.parseInt(items[i]);
                    ItemInfo itemInfo = mapItemToItemInfo.get(item);
                    if (itemInfo == null) {
                        itemInfo = new ItemInfo();
                        mapItemToItemInfo.put(item, itemInfo);
                    }
                    itemInfo.support++;
                    int periodicity = databaseSize - itemInfo.lastSeenTransaction;
                    if (itemInfo.largestPeriodicity < periodicity) {
                        itemInfo.largestPeriodicity = periodicity;
                    }
                    itemInfo.lastSeenTransaction = databaseSize;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // update largest periodicity using the tail period for each item
        for (Entry<Integer, ItemInfo> entry : mapItemToItemInfo.entrySet()) {
            ItemInfo itemInfo = entry.getValue();
            int periodicity = databaseSize - itemInfo.lastSeenTransaction;
            if (itemInfo.largestPeriodicity < periodicity) {
                itemInfo.largestPeriodicity = periodicity;
            }
        }

        // build TID lists for items satisfying maxPeriodicity
        List<TIDList> listOfTIDLists = new ArrayList<TIDList>();
        Map<Integer, TIDList> mapItemToTIDList = new HashMap<Integer, TIDList>();

        for (Entry<Integer, ItemInfo> entry : mapItemToItemInfo.entrySet()) {
            ItemInfo itemInfo = entry.getValue();
            if (itemInfo.largestPeriodicity <= maxPeriodicity) {
                int item = entry.getKey();
                TIDList tidList = new TIDList(item);
                mapItemToTIDList.put(item, tidList);
                listOfTIDLists.add(tidList);
                tidList.largestPeriodicity = itemInfo.largestPeriodicity;
            }
        }

        // sort items by support ascending (then by item id for ties)
        Collections.sort(listOfTIDLists, new Comparator<TIDList>() {
            public int compare(TIDList o1, TIDList o2) {
                return compareItems(o1.item, o2.item);
            }
        });

        // second database scan: build TID lists
        try {
            transactionBuffer = new int[TRANSACTION_BUFFERS_SIZE];
            myInput = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(input))));
            int tid = 0;
            // For each line
            while ((thisLine = myInput.readLine()) != null) {
            	// Ignore metadata
                if (thisLine.isEmpty() ||
                        thisLine.charAt(0) == '#' ||
                        thisLine.charAt(0) == '%' ||
                        thisLine.charAt(0) == '@') {
                    continue;
                }
                // For each item
                String[] items = thisLine.split(" ");
                int sizeNewTransaction = 0;
                for (int i = 0; i < items.length; i++) {
                    int item = Integer.parseInt(items[i]);
                    ItemInfo itemInfo = mapItemToItemInfo.get(item);
                    if (itemInfo.largestPeriodicity <= maxPeriodicity) {
                        transactionBuffer[sizeNewTransaction++] = item;
                    }
                }
                // Sort the transaction
                insertionSort(transactionBuffer, sizeNewTransaction);

                for (int i = 0; i < sizeNewTransaction; i++) {
                    int item = transactionBuffer[i];
                    TIDList tidListOfItem = mapItemToTIDList.get(item);
                    tidListOfItem.addElement(tid);
                }
                tid++;
            }
            transactionBuffer = null;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
            transactionBuffer = null;
        }

        mapItemToItemInfo = null;
        mapItemToTIDList = null;

        MemoryLogger.getInstance().checkMemory();

        // mine patterns recursively
        search(itemsetBuffer, 0, null, listOfTIDLists);

        // extract top-K patterns with proper tie handling
        List<PatternEntry> topKPatterns = extractTopKWithTies();

        // write results to output file
        writeTopKToFile(topKPatterns);

        MemoryLogger.getInstance().checkMemory();
        writer.close();
        totalExecutionTime = System.currentTimeMillis() - startTimestamp;
        maximumMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
    }

    /**
     * Extract all top-K patterns including all patterns tied at the K-th rank.
     * @return list of all patterns with support >= k-th highest support
     */
    private List<PatternEntry> extractTopKWithTies() {
        if (allCandidates.isEmpty()) {
            patternCount = 0;
            return new ArrayList<PatternEntry>();
        }

        // sort all candidates by support descending
        Collections.sort(allCandidates, new Comparator<PatternEntry>() {
            public int compare(PatternEntry p1, PatternEntry p2) {
                return p2.support - p1.support;
            }
        });

        List<PatternEntry> result = new ArrayList<PatternEntry>();

        if (allCandidates.size() <= k) {
            // fewer than k candidates: return all of them
            result.addAll(allCandidates);
        } else {
            // find the k-th highest support (1-indexed, sorted descending)
            int kthSupport = allCandidates.get(k - 1).support;

            // include ALL patterns with support >= kthSupport (handles ties)
            for (PatternEntry pattern : allCandidates) {
                if (pattern.support >= kthSupport) {
                    result.add(pattern);
                } else {
                    break; // sorted descending, safe to stop
                }
            }
        }

        patternCount = result.size();
        return result;
    }

    /**
     * Write the top-K patterns to the output file.
     * @param topKPatterns the list of top-K patterns to write
     * @throws IOException exception if error while writing the file
     */
    private void writeTopKToFile(List<PatternEntry> topKPatterns) throws IOException {
        for (PatternEntry entry : topKPatterns) {
            StringBuilder buffer = new StringBuilder();
            for (int i = 0; i < entry.items.length; i++) {
                buffer.append(entry.items[i]);
                if (i < entry.items.length - 1) {
                    buffer.append(' ');
                }
            }
            buffer.append(" #SUP: ");
            buffer.append(entry.support);
            buffer.append(" #MAXPER: ");
            buffer.append(entry.largestPeriodicity);
            writer.write(buffer.toString());
            writer.newLine();
        }
    }

    /**
     * Implementation of insertion sort for integers using item comparison order.
     * @param a array of integers to sort
     * @param size the number of elements to sort
     */
    public static void insertionSort(int[] a, int size) {
        for (int j = 1; j < size; j++) {
            int key = a[j];
            int i = j - 1;
            for (; i >= 0 && compareItems(a[i], key) > 0; i--) {
                a[i + 1] = a[i];
            }
            a[i + 1] = key;
        }
    }

    /**
     * Compare two items by support ascending then by item id for ties.
     * @param item1 an item
     * @param item2 another item
     * @return 0 if equal, positive if item1 is larger, negative otherwise
     */
    private static int compareItems(int item1, int item2) {
        int compare = mapItemToItemInfo.get(item1).support
                - mapItemToItemInfo.get(item2).support;
        return (compare == 0) ? item1 - item2 : compare;
    }

    /**
     * Recursive method to find all top-K periodic patterns.
     * @param prefix the current prefix itemset buffer
     * @param prefixLength the current prefix length
     * @param pUL the TID list of the prefix
     * @param ULs the TID lists of all extensions of the prefix
     * @throws IOException exception if error while writing the file
     */
    private void search(int[] prefix, int prefixLength, TIDList pUL, List<TIDList> ULs)
            throws IOException {

        int patternSize = prefixLength + 1;

        for (int i = 0; i < ULs.size(); i++) {
            TIDList X = ULs.get(i);

            if (X.getSupport() > 0 && X.largestPeriodicity <= maxPeriodicity) {
                if (patternSize >= minimumLength && patternSize <= maximumLength) {
                    addCandidate(prefix, prefixLength, X);
                }
            }

            if (patternSize < maximumLength) {
                List<TIDList> exULs = new ArrayList<TIDList>();
                for (int j = i + 1; j < ULs.size(); j++) {
                    TIDList Y = ULs.get(j);
                    candidateCount++;
                    TIDList temp = construct(prefixLength == 0, X, Y);
                    if (temp != null) {
                        exULs.add(temp);
                    }
                }
                itemsetBuffer[prefixLength] = X.item;
                search(itemsetBuffer, prefixLength + 1, X, exULs);
            }
        }

        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Add a pattern to the candidate set and update the pruning threshold.
     * The pruning threshold is raised to kthHighestSupport - 1 so that patterns
     * strictly below the k-th support are pruned, but ties at the k-th support are kept.
     * @param prefix the current prefix buffer
     * @param prefixLength the length of the prefix
     * @param tidList the TID list of the pattern
     */
    private void addCandidate(int[] prefix, int prefixLength, TIDList tidList) {
        int support = tidList.getSupport();

        if (support <= 0) {
            return;
        }

        int[] patternItems = new int[prefixLength + 1];
        for (int i = 0; i < prefixLength; i++) {
            patternItems[i] = prefix[i];
        }
        patternItems[prefixLength] = tidList.item;

        allCandidates.add(new PatternEntry(patternItems, support, tidList.largestPeriodicity));

        // maintain the min-heap of the top-k supports seen so far
        if (topKHeap.size() < k) {
            topKHeap.offer(support);
        } else if (support > topKHeap.peek()) {
            // new support is strictly better than the current worst in top-k
            topKHeap.poll();
            topKHeap.offer(support);
        }

        // update kthHighestSupport
        if (topKHeap.size() == k) {
            kthHighestSupport = topKHeap.peek();
            // prune only patterns STRICTLY below the k-th support
            // so ties at the k-th support are never pruned
            double newThreshold = kthHighestSupport - 1;
            if (newThreshold > supportPruningThreshold) {
                supportPruningThreshold = newThreshold;
            }
        }
    }

    /**
     * Construct the TID list of itemset pXY from the TID lists of pX and pY.
     * @param firstTime true if the prefix P is empty
     * @param px the TID list of pX
     * @param py the TID list of pY
     * @return the TID list of pXY, or null if pruned
     */
    private TIDList construct(boolean firstTime, TIDList px, TIDList py) {
        TIDList pxyUL = new TIDList(py.item);

        int lastTid = -1;

        for (Integer ex : px.elements) {
            Integer ey = findElementWithTID(py, ex);
            if (ey == null) {
                continue;
            }

            int periodicity = ex - lastTid;
            if (periodicity > maxPeriodicity) {
                return null;
            }
            if (periodicity > pxyUL.largestPeriodicity) {
                pxyUL.largestPeriodicity = periodicity;
            }
            lastTid = ex;
            pxyUL.addElement(ex);
        }

        // check the tail period
        int tailPeriodicity = (databaseSize - 1) - lastTid;
        if (tailPeriodicity > maxPeriodicity) {
            return null;
        }
        if (tailPeriodicity > pxyUL.largestPeriodicity) {
            pxyUL.largestPeriodicity = tailPeriodicity;
        }

        // prune if support is strictly below the dynamic threshold
        if (pxyUL.getSupport() < supportPruningThreshold) {
            return null;
        }

        return pxyUL;
    }

    /**
     * Perform a binary search to find the element with a given tid in a TID list.
     * @param ulist the TID list to search
     * @param tid the transaction id to find
     * @return the tid value if found, or null if not found
     */
    private Integer findElementWithTID(TIDList ulist, int tid) {
        List<Integer> list = ulist.elements;
        int first = 0;
        int last = list.size() - 1;
        while (first <= last) {
            int middle = (first + last) >>> 1;
            if (list.get(middle) < tid) {
                first = middle + 1;
            } else if (list.get(middle) > tid) {
                last = middle - 1;
            } else {
                return list.get(middle);
            }
        }
        return null;
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  MTKPP ALGORITHM v2.66 =====");
        System.out.println(" Database size: " + databaseSize + " transactions");
        System.out.println(" K: " + k);
        System.out.println(" Max periodicity: " + maxPeriodicity);
//        System.out.println(" Candidate count : " + candidateCount);
        System.out.println(" Support pruning threshold: " + supportPruningThreshold);
        System.out.println(" Time : " + totalExecutionTime + " ms");
        System.out.println(" Memory ~ " + maximumMemoryUsage + " MB");
        System.out.println(" Periodic patterns count : " + patternCount);
       System.out.println("===================================================");
    }

    /**
     * Set the minimum length for patterns to be found.
     * @param minimumLength the minimum number of items in a pattern
     */
    public void setMinimumLength(int minimumLength) {
        this.minimumLength = minimumLength;
    }

    /**
     * Set the maximum length for patterns to be found.
     * @param maximumLength the maximum number of items in a pattern
     */
    public void setMaximumLength(int maximumLength) {
        this.maximumLength = maximumLength;
    }
}