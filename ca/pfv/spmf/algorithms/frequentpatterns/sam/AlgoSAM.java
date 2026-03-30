package ca.pfv.spmf.algorithms.frequentpatterns.sam;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger

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
*/
/**
 * Implementation of the SAM (Split and Merge) algorithm for mining frequent
 * itemsets.
 * <p>
 * Reference: C. Borgelt and X. Wang. SaM: A Split and Merge Algorithm for Fuzzy
 * Frequent Item Set Mining. Proc. 13th IFSA World Congress / 6th EUSFLAT
 * Conference (2009), 968-973.
 * </p>
 * @author Philippe Fournier-Viger, 2025, inspired by the C++ implementation from C.
 *         Borgelt (under the MIT license) but simplified and adapted to keep the core algorithm 
 *         and remove other features, and use other design choices.
 */
public class AlgoSAM {

    /** minimum support threshold */
    private double minSupport;

    /** minimum support as absolute value */
    private int minSupportAbsolute;

    /** Peak memory usage of the last execution of the algorithm */
    private double peakMemory;

    /** Maximum pattern length constraint (default: no limit) */
    private int maxPatternLength = Integer.MAX_VALUE;

    /** Runtime of the last algorithm execution */
    private long totalTime;

    /** Number of frequent itemsets found */
    private int itemsetCount;

    /** Buffer for writing the output */
    private BufferedWriter writer;

    /** Converter for renaming items according to support-based total order */
    private ItemNameConverter nameConverter;

    /** Number of frequent items */
    private int frequentItemCount;

    /** Reusable buffer for sorting items when writing itemsets */
    private int[] itemsetBuffer;

    /** Reusable buffer for parsing lines - avoids repeated allocation */
    private int[] parseBuffer = new int[1024];

    /**
     * Creates a new SAM algorithm instance.
     */
    public AlgoSAM() {
    }

    /**
     * Sets the maximum pattern length constraint.
     * 
     * @param maxLength the maximum number of items in a frequent itemset (must be
     *                  >= 1)
     */
    public void setMaximumPatternLength(int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("Maximum pattern length must be at least 1");
        }
        this.maxPatternLength = maxLength;
    }

    /**
     * Runs the SAM algorithm.
     */
    public void runAlgorithm(String inputPath, String outputPath, double minSupport) throws IOException {

        totalTime = 0;
        this.minSupport = minSupport;
        this.itemsetCount = 0;

        MemoryLogger.getInstance().reset();

        long startTime = System.currentTimeMillis();

        List<TransactionSAM> preprocessed = readTransactions(inputPath);

        MemoryLogger.getInstance().checkMemory();

        if (!preprocessed.isEmpty()) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {
                this.writer = bw;

                int bufferSize = Math.min(frequentItemCount, maxPatternLength);
                itemsetBuffer = new int[bufferSize];

                ArrayDeque<TransactionSAM> transactions = new ArrayDeque<>(preprocessed);
                preprocessed = null;

                MemoryLogger.getInstance().checkMemory();

                int[] prefix = new int[bufferSize];
                sam(transactions, prefix, 0);

                MemoryLogger.getInstance().checkMemory();
            }
        } else {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath))) {
                // Empty output
            }
        }

        peakMemory = MemoryLogger.getInstance().checkMemory();
        totalTime = System.currentTimeMillis() - startTime;
    }

    /**
     * Reads and preprocesses transactions from input file.
     */
    private List<TransactionSAM> readTransactions(String inputPath) throws IOException {
        Map<Integer, Integer> itemCounts = new HashMap<>();
        List<int[]> rawTransactions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isCommentLine(line))
                    continue;

                int[] transaction = parseLine(line);
                if (transaction.length > 0) {
                    rawTransactions.add(transaction);
                    for (int item : transaction) {
                        itemCounts.merge(item, 1, Integer::sum);
                    }
                }
            }
        }

        int transactionCount = rawTransactions.size();
        minSupportAbsolute = Math.max(1, (int) Math.ceil(minSupport * transactionCount));

        List<Map.Entry<Integer, Integer>> frequentItems = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : itemCounts.entrySet()) {
            if (entry.getValue() >= minSupportAbsolute) {
                frequentItems.add(entry);
            }
        }
        frequentItems.sort((a, b) -> Integer.compare(a.getValue(), b.getValue()));

        frequentItemCount = frequentItems.size();

        if (frequentItemCount == 0) {
            nameConverter = new ItemNameConverter(0, 0);
            return new ArrayList<>();
        }

        nameConverter = new ItemNameConverter(frequentItemCount, 0);

        for (Map.Entry<Integer, Integer> entry : frequentItems) {
            nameConverter.assignNewName(entry.getKey());
        }

        List<TransactionSAM> transactions = recodeTransactions(rawTransactions);
        transactions = mergeIdenticalTransactions(transactions);

        return transactions;
    }

    /**
     * Checks whether a line is empty or a comment line.
     * Comment lines start with '#', '%', or '@'.
     *
     * @param line the input line
     * @return true if the line should be ignored
     */
    private boolean isCommentLine(String line) {
        if (line.isEmpty())
            return true;
        char first = line.charAt(0);
        return first == '#' || first == '%' || first == '@';
    }

    /**
     * Parses a transaction line into an array of integers.
     * Items are separated by whitespace; non-numeric tokens are ignored.
     *
     * @param line the input line
     * @return the parsed items as an integer array
     */
    private int[] parseLine(String line) {
        int len = line.length();
        int count = 0;
        int i = 0;

        while (i < len) {
            while (i < len && line.charAt(i) <= ' ')
                i++;
            if (i >= len)
                break;

            boolean negative = false;
            if (line.charAt(i) == '-') {
                negative = true;
                i++;
            }

            int value = 0;
            int digitStart = i;
            while (i < len) {
                char c = line.charAt(i);
                if (c >= '0' && c <= '9') {
                    value = value * 10 + (c - '0');
                    i++;
                } else {
                    break;
                }
            }

            if (i > digitStart) {
                if (count >= parseBuffer.length) {
                    int[] newBuffer = new int[parseBuffer.length * 2];
                    System.arraycopy(parseBuffer, 0, newBuffer, 0, parseBuffer.length);
                    parseBuffer = newBuffer;
                }
                parseBuffer[count++] = negative ? -value : value;
            }

            while (i < len && line.charAt(i) > ' ')
                i++;
        }

        int[] result = new int[count];
        System.arraycopy(parseBuffer, 0, result, 0, count);
        return result;
    }

    /**
     * Recode a transaction
     * @param rawTransactions a list of raw transactions
     * @return a list of recoded transactions.
     */
    private List<TransactionSAM> recodeTransactions(List<int[]> rawTransactions) {
        List<TransactionSAM> result = new ArrayList<>(rawTransactions.size());

        for (int[] raw : rawTransactions) {
            int count = 0;
            for (int item : raw) {
                if (nameConverter.isOldItemExisting(item)) {
                    count++;
                }
            }

            if (count > 0) {
                int[] items = new int[count];
                int index = 0;
                for (int item : raw) {
                    if (nameConverter.isOldItemExisting(item)) {
                        items[index++] = nameConverter.toNewName(item);
                    }
                }

                // sort the transaction by ascending order of support.
                Arrays.sort(items);  

                result.add(new TransactionSAM(items, 0, 1));
            }
        }

        // Sort the transactions
        result.sort(this::compareTransactions);
        return result;
    }


    /**
     * Merges consecutive identical transactions by summing their weights.
     * Assumes the input list is sorted according to {@link #compareTransactions}.
     *
     * @param transactions the sorted list of transactions
     * @return a new list with equal transactions combined
     */
    private List<TransactionSAM> mergeIdenticalTransactions(List<TransactionSAM> transactions) {
        if (transactions.isEmpty()) return transactions;

        List<TransactionSAM> combined = new ArrayList<>();
        TransactionSAM current = transactions.get(0); // no copy here

        for (int i = 1; i < transactions.size(); i++) {
            TransactionSAM next = transactions.get(i);
            if (compareTransactions(current, next) == 0) {
                current.addWeight(next.getWeight()); // accumulate weight
            } else {
                combined.add(current);
                current = next; // just move the reference, no copy
            }
        }
        combined.add(current);

        return combined;
    }

    /**
     * Recursive SaM (Split and Merge) procedure.
     * <p>
     * Extends the current prefix by splitting transactions on their first item,
     * applies minimum support pruning, outputs frequent itemsets, and recursively 
     * processes projected transaction lists.
     * </p>
     *
     * @param transactions   the current sorted list of transactions
     * @param prefix         the current itemset prefix
     * @param prefixLength   length of the current prefix
     * @throws IOException if an output error occurs
     */
    private void sam(ArrayDeque<TransactionSAM> transactions, int[] prefix, int prefixLength) throws IOException {

        while (!transactions.isEmpty()) {

            // Get the first item of the first transaction
            int currentItem = transactions.getFirst().firstItem();

            // Split: extract all transactions starting with currentItem
            ArrayDeque<TransactionSAM> projectedTransactions = new ArrayDeque<>();
            int support = 0;

            while (!transactions.isEmpty() && transactions.getFirst().firstItem() == currentItem) {
                TransactionSAM transaction = transactions.removeFirst();
                support += transaction.getWeight();
                transaction.removeFirst();

                if (!transaction.isEmpty()) {
                    projectedTransactions.addLast(transaction);
                }
            }

            // Process frequent item
            if (support >= minSupportAbsolute) {
                prefix[prefixLength] = currentItem;
                writeItemset(prefix, prefixLength + 1, support);

                // Recursive call if we can extend further and have transactions
                if (prefixLength + 1 < maxPatternLength && !projectedTransactions.isEmpty()) {
                    ArrayDeque<TransactionSAM> projectedCopy = new ArrayDeque<>(projectedTransactions.size());
                    for (TransactionSAM transaction : projectedTransactions) {
                        projectedCopy.addLast(transaction.copy());
                    }
                    sam(projectedCopy, prefix, prefixLength + 1);
                }
            }

            // Merge: combine remaining transactions with projected transactions
            if (projectedTransactions.isEmpty()) {
                continue;
            }
            
            if (transactions.isEmpty()) {
                transactions = projectedTransactions;
                continue;
            }

            // Both non-empty: perform the merge
            ArrayDeque<TransactionSAM> mergedTransactions = 
                    new ArrayDeque<>(transactions.size() + projectedTransactions.size());

            while (!transactions.isEmpty() && !projectedTransactions.isEmpty()) {
                int compare = compareTransactions(transactions.getFirst(), projectedTransactions.getFirst());

                if (compare < 0) {
                    mergedTransactions.addLast(transactions.removeFirst());
                } else if (compare > 0) {
                    mergedTransactions.addLast(projectedTransactions.removeFirst());
                } else {
                    // Equal transactions: merge weights
                    TransactionSAM merged = projectedTransactions.removeFirst();
                    merged.addWeight(transactions.removeFirst().getWeight());
                    mergedTransactions.addLast(merged);
                }
            }

            // Append remaining elements
            while (!transactions.isEmpty()) {
                mergedTransactions.addLast(transactions.removeFirst());
            }
            while (!projectedTransactions.isEmpty()) {
                mergedTransactions.addLast(projectedTransactions.removeFirst());
            }

            transactions = mergedTransactions;
        }
    }

    /**
     * Compares two transactions using lexicographic order on their item sequences.
     * Items are compared from left to right; a negative item value indicates
     * the end of a transaction. Shorter transactions are considered greater
     * if they are a prefix of the other.
     *
     * @param a the first transaction
     * @param b the second transaction
     * @return 0 if equal, a negative value if {@code a < b}, or a positive value if {@code a > b}
     */
    private int compareTransactions(TransactionSAM a, TransactionSAM b) {
        int index = 0;
        while (true) {
            int itemA = a.getItem(index);
            int itemB = b.getItem(index);

            if (itemA < 0) {
                return (itemB < 0) ? 0 : 1;  // shorter transactions come later
            }
            if (itemB < 0) {
                return -1;
            }

            if (itemA != itemB) {
                return Integer.compare(itemA, itemB);  // NORMAL ascending order
            }
            index++;
        }
    }

    /**
     * Write an itemset to the output file.
     * @param itemset the itemset
     * @param length its length
     * @param support the support of the itemset
     * @throws IOException if error occurs while writing to the file.
     */
    private void writeItemset(int[] itemset, int length, int support) throws IOException {
        itemsetCount++;

        for (int i = 0; i < length; i++) {
        	itemsetBuffer[i] = nameConverter.toOldName(itemset[i]);
        }
        Arrays.sort(itemsetBuffer, 0, length);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0)
                sb.append(' ');
            sb.append(itemsetBuffer[i]);
        }
        sb.append(" #SUP: ").append(support);

        writer.write(sb.toString());
        writer.newLine();
    }

    /** 
     * Print statistics about the latest execution of the algorithm to the console.
     */
    public void printStats() {
        System.out.println("============= SAM ALGORITHM 2.65 - STATS =============");
        if (maxPatternLength != Integer.MAX_VALUE) {
            System.out.println(" Max pattern length: " + maxPatternLength);
        }
        System.out.println(" Frequent itemsets: " + itemsetCount);
        System.out.println(" Execution time: " + totalTime + " ms");
        System.out.println(" Max memory: " + peakMemory + " MB");
        System.out.println("==================================================");
    }
}