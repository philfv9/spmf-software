package ca.pfv.spmf.algorithms.frequentpatterns.uhmine;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger

 This file is part of the SPMF DATA MINING SOFTWARE
 (http://www.philippe-fournier-viger.com/spmf).
 SPMF is free software: you can redistribute it and/or modify it under the
 terms of the GNU General Public License as published by the Free Software
 Foundation, either version 3 of the License, or (at your option) any later
 version.
 SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 SPMF. If not, see http://www.gnu.org/licenses/.
 Do not remove copyright and license information.
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * An implementation of the UH-Mine algorithm for mining frequent itemsets
 * from uncertain transaction databases.UH-Mine extends H-Mine by storing existential probabilities alongside item
 * ids in the hyper-linked array (H-Struct).  
 * 
 * Based on: Aggarwal et al. (2009) Frequent Pattern Mining with Uncertain
 * Data. KDD 2009, Paris, France.
 *
 * @see UncertainTransactionDatabaseUHMine
 * @see RowUHMine
 * @see ItemUHMine
 * @author Philippe Fournier-Viger, 2015
 */
public class AlgoUHMine {

    /** start time of the latest execution */
    long startTimestamp = 0;

    /** end time of the latest execution */
    long endTimestamp = 0;

    /** number of frequent itemsets found */
    int patternCount = 0;

    /** writer to write results to the output file */
    BufferedWriter writer = null;

    /** buffer for storing the current prefix itemset during mining */
    final int BUFFERS_SIZE = 200;
    private int[] itemsetBuffer = null;

    /**
     * Flat array storing item ids for all transactions.
     * Items of a transaction are stored consecutively, followed by SENTINEL (-1).
     * Parallel to probCells: itemCells[i] is the item id at slot i.
     */
    int[] itemCells;

    /** existential probabilities, one per slot, parallel to itemCells */
    double[] probCells;

    /** sentinel value in itemCells marking the end of a transaction */
    static final int SENTINEL = -1;

    /**
     * Absolute minimum expected support threshold.
     * Set directly from the minSupport parameter passed to runAlgorithm.
     */
    double minSupport = 0;

    /** maximum itemset size allowed */
    int maxItemsetSize = Integer.MAX_VALUE;

    /**
     * Default constructor.
     */
    public AlgoUHMine() {
    }

    /**
     * Run the UH-Mine algorithm on an uncertain transaction database.
     *
     * @param database   the uncertain transaction database
     * @param minSupport the absolute minimum expected support threshold
     * @param output     the path of the output file
     * @throws IOException if an error occurs while writing the output file
     */
    public void runAlgorithm(UncertainTransactionDatabaseUHMine database,
                             double minSupport, String output) throws IOException {

        itemsetBuffer = new int[BUFFERS_SIZE];
        MemoryLogger.getInstance().reset();
        startTimestamp = System.currentTimeMillis();
        writer = new BufferedWriter(new FileWriter(output));

        // store the absolute minimum expected support directly
        this.minSupport = minSupport;

        // --- First pass: compute absolute expected support of each item ---
        // E[s({i})] = sum over all transactions T of p(i, T)
        final Map<Integer, Double> mapItemExpSup = new HashMap<Integer, Double>();
        for (List<ItemUHMine> transaction : database.getTransactions()) {
            for (ItemUHMine item : transaction) {
                Double cur = mapItemExpSup.get(item.getId());
                mapItemExpSup.put(item.getId(),
                        (cur == null ? 0.0 : cur) + item.getProbability());
            }
        }

        // --- Build initial row list, keeping only frequent items ---
        List<RowUHMine> rowList = new ArrayList<RowUHMine>();
        final Map<Integer, RowUHMine> globalMapItemRow =
                new HashMap<Integer, RowUHMine>();

        for (Entry<Integer, Double> entry : mapItemExpSup.entrySet()) {
            if (entry.getValue() >= this.minSupport) {
                RowUHMine row = new RowUHMine(entry.getKey());
                row.expectedSupport = entry.getValue();
                rowList.add(row);
                globalMapItemRow.put(entry.getKey(), row);
            }
        }

        // sort by ascending expected support, ties broken by item id
        Collections.sort(rowList, new Comparator<RowUHMine>() {
            public int compare(RowUHMine o1, RowUHMine o2) {
                int cmp = Double.compare(
                        mapItemExpSup.get(o1.item),
                        mapItemExpSup.get(o2.item));
                return (cmp == 0) ? o1.item - o2.item : cmp;
            }
        });

        // --- Allocate cell arrays ---
        // total slots = (number of frequent item occurrences) + (one sentinel per transaction
        //                that has at least one frequent item)
        int totalSlots = 0;
        for (List<ItemUHMine> transaction : database.getTransactions()) {
            int freqCount = 0;
            for (ItemUHMine item : transaction) {
                if (globalMapItemRow.containsKey(item.getId())) {
                    freqCount++;
                }
            }
            if (freqCount > 0) {
                totalSlots += freqCount + 1; // items + sentinel
            }
        }
        itemCells = new int[totalSlots];
        probCells = new double[totalSlots];

        // --- Second pass: fill cell arrays and build pointers ---
        int currentSlot = 0;
        for (List<ItemUHMine> transaction : database.getTransactions()) {

            // collect frequent items from this transaction
            List<ItemUHMine> freqItems = new ArrayList<ItemUHMine>();
            for (ItemUHMine item : transaction) {
                if (globalMapItemRow.containsKey(item.getId())) {
                    freqItems.add(item);
                }
            }
            if (freqItems.isEmpty()) {
                continue;
            }

            // sort this transaction's items by ascending expected support
            // (same global order as rowList)
            Collections.sort(freqItems, new Comparator<ItemUHMine>() {
                public int compare(ItemUHMine a, ItemUHMine b) {
                    int cmp = Double.compare(
                            mapItemExpSup.get(a.getId()),
                            mapItemExpSup.get(b.getId()));
                    return (cmp == 0) ? a.getId() - b.getId() : cmp;
                }
            });

            int transBegin = currentSlot;

            // write items into the cell arrays
            for (ItemUHMine item : freqItems) {
                itemCells[currentSlot] = item.getId();
                probCells[currentSlot] = item.getProbability();
                currentSlot++;
            }
            // write sentinel
            itemCells[currentSlot] = SENTINEL;
            probCells[currentSlot] = 0.0;
            currentSlot++;

            // register pointer for each item in the global row list
            for (int i = transBegin; i < currentSlot - 1; i++) {
                RowUHMine row = globalMapItemRow.get(itemCells[i]);
                row.pointers.add(i);
            }
        }

        MemoryLogger.getInstance().checkMemory();

        // --- Recursive mining ---
        if (maxItemsetSize >= 1) {
            uhmine(itemsetBuffer, 0, rowList);
        }

        MemoryLogger.getInstance().checkMemory();
        writer.close();
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Recursively mine frequent itemsets using the UH-Mine H-Struct.
     * For each item row, build the projected conditional database on-the-fly,
     * output the current itemset, and recurse on frequent extensions.
     *
     * When minSupport is exactly 0.0, candidate extensions are pre-seeded with
     * all items after the current row to ensure zero-support itemsets are
     * enumerated. Otherwise, only items actually encountered in transactions
     * are added to the candidate map (standard H-Mine behavior).
     *
     * @param prefix       the current prefix item buffer
     * @param prefixLength the number of items currently in the prefix
     * @param rowList      the rows of the current H-Struct (frequent items only)
     * @throws IOException if an error occurs while writing output
     */
    private void uhmine(int[] prefix, int prefixLength,
                        List<RowUHMine> rowList) throws IOException {

        // Determine if we need to handle zero-support itemsets
        // Use a small epsilon to handle floating-point comparison
        final boolean handleZeroSupport = (minSupport < 1e-10);

        for (int rowIdx = 0; rowIdx < rowList.size(); rowIdx++) {
            RowUHMine row = rowList.get(rowIdx);

            Map<Integer, RowUHMine> newMapItemRow =
                    new HashMap<Integer, RowUHMine>();

            // Pre-seed only when minSupport = 0.0 to enumerate zero-support itemsets
            if (handleZeroSupport) {
                for (int k = rowIdx + 1; k < rowList.size(); k++) {
                    int candidateItem = rowList.get(k).item;
                    newMapItemRow.put(candidateItem, new RowUHMine(candidateItem));
                }
            }

            // for each transaction containing row.item (after the prefix items)
            for (int pointer : row.pointers) {

                // compute p(prefix, T) by scanning back to the transaction start
                double prefixProb = computePrefixProb(prefix, prefixLength, pointer);

                // p(prefix ∪ {row.item}, T)
                double extendedProb = (prefixLength == 0)
                        ? probCells[pointer]
                        : prefixProb * probCells[pointer];

                // scan items after pointer in this transaction
                int pos = pointer + 1;
                while (pos < itemCells.length && itemCells[pos] != SENTINEL) {
                    int nextItem = itemCells[pos];
                    double nextProb = probCells[pos];

                    RowUHMine nextRow = newMapItemRow.get(nextItem);
                    if (nextRow == null) {
                        // Only create on-demand if not pre-seeded
                        if (!handleZeroSupport) {
                            nextRow = new RowUHMine(nextItem);
                            newMapItemRow.put(nextItem, nextRow);
                        } else {
                            // Item not in our candidate set (appears before row)
                            pos++;
                            continue;
                        }
                    }
                    
                    // contribution of T to E[s(prefix ∪ {row.item} ∪ {nextItem})]
                    nextRow.expectedSupport += extendedProb * nextProb;
                    nextRow.pointers.add(pos);
                    pos++;
                }
            }

            // collect frequent extensions
            List<RowUHMine> newRowList = new ArrayList<RowUHMine>();
            for (Entry<Integer, RowUHMine> entry : newMapItemRow.entrySet()) {
                if (entry.getValue().expectedSupport >= minSupport) {
                    newRowList.add(entry.getValue());
                }
            }

            // output the current frequent itemset: prefix[0..prefixLength-1] + row.item
            writeOut(prefix, prefixLength, row.item, row.expectedSupport);

            if (!newRowList.isEmpty()) {
                // sort extensions by ascending expected support
                Collections.sort(newRowList, new Comparator<RowUHMine>() {
                    public int compare(RowUHMine o1, RowUHMine o2) {
                        int cmp = Double.compare(
                                o1.expectedSupport, o2.expectedSupport);
                        return (cmp == 0) ? o1.item - o2.item : cmp;
                    }
                });

                // extend the prefix and recurse
                prefix[prefixLength] = row.item;
                if (prefixLength + 2 <= maxItemsetSize) {
                    uhmine(prefix, prefixLength + 1, newRowList);
                }
            }
        }

        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Compute the probability of the current prefix occurring in a transaction,
     * by scanning backward from pointer to the transaction start and
     * multiplying the probabilities of each prefix item found there.
     * Returns 1.0 if the prefix is empty.
     *
     * @param prefix       the prefix item buffer
     * @param prefixLength the number of items in the prefix
     * @param pointer      the slot index of the current item in the cell arrays
     * @return the product of probabilities of all prefix items in this transaction
     */
    private double computePrefixProb(int[] prefix, int prefixLength, int pointer) {
        if (prefixLength == 0) {
            return 1.0;
        }
        // find the start of this transaction by scanning left
        // stop when we hit index 0 or when the cell to our left is a sentinel
        int start = pointer - 1;
        while (start > 0 && itemCells[start - 1] != SENTINEL) {
            start--;
        }
        // start is now the index of the first item in this transaction

        // multiply the probabilities of all prefix items
        double prob = 1.0;
        for (int pi = 0; pi < prefixLength; pi++) {
            int prefixItem = prefix[pi];
            for (int i = start; i < pointer; i++) {
                if (itemCells[i] == prefixItem) {
                    prob *= probCells[i];
                    break;
                }
            }
        }
        return prob;
    }

    /**
     * Write a frequent itemset to the output file.
     *
     * @param prefix          the prefix items
     * @param prefixLength    the number of items in the prefix
     * @param item            the last item appended to the prefix
     * @param expectedSupport the expected support of this itemset
     * @throws IOException if an error occurs while writing
     */
    private void writeOut(int[] prefix, int prefixLength, int item,
                          double expectedSupport) throws IOException {
        patternCount++;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < prefixLength; i++) {
            buffer.append(prefix[i]);
            buffer.append(' ');
        }
        buffer.append(item);
        buffer.append(" #SUP: ");
        buffer.append(expectedSupport);
        writer.write(buffer.toString());
        writer.newLine();
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println(
                "============= UH-MINE v 2.66 ALGORITHM - STATS =============");
        System.out.println(" Total time ~ "
                + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Max Memory ~ "
                + MemoryLogger.getInstance().getMaxMemory() + " MB");
        System.out.println(" Frequent itemsets count : " + patternCount);
        System.out.println(
                "===================================================");
    }

    /**
     * Set the maximum pattern length (number of items per itemset).
     *
     * @param length the maximum length
     */
    public void setMaximumPatternLength(int length) {
        this.maxItemsetSize = length;
    }
}