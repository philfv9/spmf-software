package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.patterns.itemset_list_integers_without_support.Itemset;
import ca.pfv.spmf.tools.MemoryLogger;

/*
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Implementation of the BIDE+ algorithm for mining closed sequential patterns, proposed by
 * Wang & Han (2004). Items are renamed to consecutive integers before mining and projected
 * databases are represented as reusable primitive arrays to avoid per-node allocation. The
 * BackScan pruning test and the backward-extension (closure) test are each performed in
 * {@code O(S * L)} per candidate pattern using forward/backward greedy matching combined with
 * reusable stamped counters.
 *
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
 */
public class AlgoBIDEPlus {

    /** Time at which mining started. */
    long startTime;
    /** Time at which mining ended. */
    long endTime;

    /** Number of patterns found. */
    public int patternCount;

    /** Absolute minimum support (in number of sequences). */
    private int minsuppAbsolute;

    /** Writer used to write patterns to the output file, or null if kept in memory. */
    BufferedWriter writer = null;

    /** Sequential patterns found, kept in memory if no output file was given. */
    private SequentialPatterns patterns = null;

    /** Maximum pattern length (in number of items) to mine. */
    private int maximumPatternLength = 1000;

    /** If true, sequence identifiers of each pattern are shown in the output. */
    boolean showSequenceIdentifiers = false;

    /** Fixed capacity of all pattern/scratch buffers. */
    final int BUFFERS_SIZE = 2000;

    /** Buffer holding the itemset representation of the pattern currently being extended. */
    private int[] patternBuffer = new int[BUFFERS_SIZE];

    /** Number of sequences in the database. */
    int sequenceCount = 0;

    /** The sequence database being mined. */
    SequenceDatabase sequenceDatabase;

    /** True if the database contains at least one itemset with more than one item. */
    boolean containsItemsetsWithMultipleItems = false;

    /** Converts original item ids to consecutive integers (and back) before/after mining. */
    private ItemNameConverter itemNameConverter;

    /** Number of distinct frequent single items in the database. */
    private int frequentItemCount;

    /** Per-depth reusable buffer of sequence ids of the projected database. */
    private int[][] projSeqIDsBuffer;
    /** Per-depth reusable buffer of start indexes into each sequence of the projected database. */
    private int[][] projIndexesBuffer;
    /** Per-depth reusable candidate-item counters for single-item databases. */
    private ItemPairArray[] singleItemsArrays;
    /** Per-depth reusable candidate-item counters (postfix/non-postfix) for multi-item databases. */
    private ArrayFrequentPairs[] frequentPairsArrays;

    /** Start index (into patternBuffer) of each itemset of the pattern being tested. */
    private int[] patItemsetStart;
    /** End index (into patternBuffer) of each itemset of the pattern being tested. */
    private int[] patItemsetEnd;

    /** Start of each matched window, used by {@link #checkBackScanPruning}. */
    private int[] scratchL;
    /** End position of each forward-matched item, used by {@link #checkBackscanPruningSingleItems}. */
    private int[] scratchMatchEnd;
    /** Start of each backward-matched itemset window, used by {@link #checkBackScanPruning}. */
    private int[] scratchTstart;

    /** Start of each forward-matched item, used by {@link #checkBackwardExtensionSingleItems}. */
    private int[] fwdChunkStart;
    /** End of each forward-matched itemset, used by {@link #hasBackwardExtension}. */
    private int[] fwdChunkEnd;
    /** Start of each backward-matched itemset/item, used by {@link #hasBackwardExtension} and {@link #checkBackwardExtensionSingleItems}. */
    private int[] bwdChunkStart;

    /** "Seen in this window" marker used by {@link #checkBackScanPruning}. */
    private StampedIntArray backscanSeenStamp;
    /** Per insertion-slot item counters used by {@link #checkBackScanPruning}, lazily grown. */
    private StampedIntArray[] backscanSlotCounters;
    /** "Seen in this window" marker used by {@link #hasBackwardExtension}. */
    private StampedIntArray backwardExtSeenStamp;
    /** Per-slot "merge into itemset k" item counters used by {@link #hasBackwardExtension}. */
    private StampedIntArray[] mergeSlotCounters;
    /** Per-slot "insert new itemset before k" item counters used by {@link #hasBackwardExtension}. */
    private StampedIntArray[] insertSlotCounters;
    /** "Seen" marker used by the length-1 pattern backscan/backward-extension checks. */
    private StampedIntArray singleItemSeenStamp;
    /** Item support counter used by the length-1 pattern backscan/backward-extension checks. */
    private StampedIntArray singleItemSupportCounter;

    /** Creates a new instance of the BIDE+ algorithm. */
    public AlgoBIDEPlus() {
    }

    /**
     * Runs the algorithm using a relative minimum support threshold.
     * @param inputFile path to the input sequence database file
     * @param minsupRelative minimum support as a fraction of the number of sequences
     * @param outputFilePath output file path, or null to keep the patterns in memory
     * @return the patterns found, or null if they were written to a file
     * @throws IOException if an error occurs while reading or writing files
     */
    public SequentialPatterns runAlgorithm(String inputFile, double minsupRelative, String outputFilePath)
            throws IOException {
        startTime = System.currentTimeMillis();
        MemoryLogger.getInstance().reset();

        // load the database
        sequenceDatabase = new SequenceDatabase();
        sequenceDatabase.loadFile(inputFile);
        sequenceCount = sequenceDatabase.size();

        // convert relative support to absolute
        this.minsuppAbsolute = (int) Math.ceil(minsupRelative * sequenceCount);
        if (this.minsuppAbsolute == 0) {
            this.minsuppAbsolute = 1;
        }

        bide(sequenceDatabase, outputFilePath);

        // release database memory and record end time
        sequenceDatabase = null;
        endTime = System.currentTimeMillis();

        if (writer != null) {
            writer.close();
        }
        return patterns;
    }

    /**
     * Runs the algorithm using an absolute minimum support threshold.
     * @param inputFile path to the input sequence database file
     * @param outputFilePath output file path, or null to keep the patterns in memory
     * @param minsup absolute minimum support (number of sequences)
     * @return the patterns found, or null if they were written to a file
     * @throws IOException if an error occurs while reading or writing files
     */
    public SequentialPatterns runAlgorithm(String inputFile, String outputFilePath, int minsup) throws IOException {
        patternCount = 0;
        MemoryLogger.getInstance().reset();

        this.minsuppAbsolute = minsup;
        startTime = System.currentTimeMillis();

        // load the database
        sequenceDatabase = new SequenceDatabase();
        sequenceDatabase.loadFile(inputFile);

        bide(sequenceDatabase, outputFilePath);

        // release database memory and record end time
        sequenceDatabase = null;
        endTime = System.currentTimeMillis();

        if (writer != null) {
            writer.close();
        }
        return patterns;
    }

    /**
     * Prepares the output destination, renames frequent items, allocates the scratch buffers
     * and runs the mining process, releasing all buffers afterwards.
     * @param sequenceDatabase the sequence database to mine
     * @param outputFilePath output file path, or null to keep the patterns in memory
     * @throws IOException if an error occurs while writing the output file
     */
    private void bide(SequenceDatabase sequenceDatabase, String outputFilePath) throws IOException {
        // set up output destination
        if (outputFilePath == null) {
            writer = null;
            patterns = new SequentialPatterns("FREQUENT SEQUENTIAL PATTERNS");
        } else {
            patterns = null;
            writer = new BufferedWriter(new FileWriter(outputFilePath));
    		writer.write("@FILETYPE=\"Frequent closed sequential patterns\"");
    		writer.append(System.lineSeparator());
    		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
    		writer.append(System.lineSeparator());
        }

        sequenceCount = sequenceDatabase.size();

        // find items and their supporting sequences
        Map<Integer, List<Integer>> mapSequenceID = findSequencesContainingItems();

        // count frequent items and build the item-name mapping
        frequentItemCount = 0;
        for (List<Integer> seqList : mapSequenceID.values()) {
            if (seqList.size() >= minsuppAbsolute) {
                frequentItemCount++;
            }
        }
        itemNameConverter = new ItemNameConverter(frequentItemCount);
        for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
            if (entry.getValue().size() >= minsuppAbsolute) {
                itemNameConverter.assignNewName(entry.getKey());
            }
        }

        // allocate per-depth reusable buffers
        projSeqIDsBuffer = new int[maximumPatternLength + 1][];
        projIndexesBuffer = new int[maximumPatternLength + 1][];
        singleItemsArrays = new ItemPairArray[maximumPatternLength + 1];
        frequentPairsArrays = new ArrayFrequentPairs[maximumPatternLength + 1];

        // allocate scratch arrays for window matching
        int scratchSize = BUFFERS_SIZE + 2;
        patItemsetStart = new int[scratchSize];
        patItemsetEnd = new int[scratchSize];
        scratchL = new int[scratchSize];
        scratchMatchEnd = new int[scratchSize];
        scratchTstart = new int[scratchSize];
        fwdChunkStart = new int[scratchSize];
        fwdChunkEnd = new int[scratchSize];
        bwdChunkStart = new int[scratchSize];

        // allocate stamped counters for pruning and closure tests
        backscanSeenStamp = new StampedIntArray(frequentItemCount + 1);
        backscanSlotCounters = null;
        backwardExtSeenStamp = new StampedIntArray(frequentItemCount + 1);
        mergeSlotCounters = null;
        insertSlotCounters = null;
        singleItemSeenStamp = new StampedIntArray(frequentItemCount + 1);
        singleItemSupportCounter = new StampedIntArray(frequentItemCount + 1);

        // choose the appropriate mining path based on database structure
        if (containsItemsetsWithMultipleItems) {
            bideWithMultipleItems(mapSequenceID);
        } else {
            bideWithSingleItems(mapSequenceID);
        }

        // release all scratch buffers
        projSeqIDsBuffer = null;
        projIndexesBuffer = null;
        singleItemsArrays = null;
        frequentPairsArrays = null;
        itemNameConverter = null;
        patItemsetStart = null;
        patItemsetEnd = null;
        scratchL = null;
        scratchMatchEnd = null;
        scratchTstart = null;
        fwdChunkStart = null;
        fwdChunkEnd = null;
        bwdChunkStart = null;
        backscanSeenStamp = null;
        backscanSlotCounters = null;
        backwardExtSeenStamp = null;
        mergeSlotCounters = null;
        insertSlotCounters = null;
        singleItemSeenStamp = null;
        singleItemSupportCounter = null;
    }

    /**
     * Lazily allocates the reusable projected-database and candidate buffers for a given depth.
     * @param depth recursion depth to allocate buffers for
     */
    private void ensureDepthBuffers(int depth) {
        if (projSeqIDsBuffer[depth] == null) {
            projSeqIDsBuffer[depth] = new int[sequenceCount];
            projIndexesBuffer[depth] = new int[sequenceCount];
        }
        if (singleItemsArrays[depth] == null) {
            singleItemsArrays[depth] = new ItemPairArray(frequentItemCount);
        }
        if (frequentPairsArrays[depth] == null) {
            frequentPairsArrays[depth] = new ArrayFrequentPairs(frequentItemCount);
        }
    }

    /**
     * Lazily grows the per-slot stamped counters used by {@link #checkBackScanPruning}.
     * @param m number of slots (itemsets of the pattern) required
     */
    private void ensureBackscanSlotCounters(int m) {
        if (backscanSlotCounters == null) {
            // first allocation
            backscanSlotCounters = new StampedIntArray[m];
            for (int i = 0; i < m; i++) {
                backscanSlotCounters[i] = new StampedIntArray(frequentItemCount + 1);
            }
        } else if (backscanSlotCounters.length < m) {
            // grow the array and fill new slots
            StampedIntArray[] newArr = new StampedIntArray[m];
            System.arraycopy(backscanSlotCounters, 0, newArr, 0, backscanSlotCounters.length);
            for (int i = backscanSlotCounters.length; i < m; i++) {
                newArr[i] = new StampedIntArray(frequentItemCount + 1);
            }
            backscanSlotCounters = newArr;
        }
    }

    /**
     * Lazily grows the per-slot stamped counters used by {@link #hasBackwardExtension}.
     * @param m number of slots (itemsets of the pattern) required
     */
    private void ensureExtensionSlotCounters(int m) {
        if (mergeSlotCounters == null) {
            // first allocation for both merge and insert counters
            mergeSlotCounters = new StampedIntArray[m];
            insertSlotCounters = new StampedIntArray[m];
            for (int i = 0; i < m; i++) {
                mergeSlotCounters[i] = new StampedIntArray(frequentItemCount + 1);
                insertSlotCounters[i] = new StampedIntArray(frequentItemCount + 1);
            }
        } else if (mergeSlotCounters.length < m) {
            // grow both arrays and fill new slots
            StampedIntArray[] newMerge = new StampedIntArray[m];
            StampedIntArray[] newInsert = new StampedIntArray[m];
            System.arraycopy(mergeSlotCounters, 0, newMerge, 0, mergeSlotCounters.length);
            System.arraycopy(insertSlotCounters, 0, newInsert, 0, insertSlotCounters.length);
            for (int i = mergeSlotCounters.length; i < m; i++) {
                newMerge[i] = new StampedIntArray(frequentItemCount + 1);
                newInsert[i] = new StampedIntArray(frequentItemCount + 1);
            }
            mergeSlotCounters = newMerge;
            insertSlotCounters = newInsert;
        }
    }

    /**
     * Renames frequent single items and mines closed frequent patterns for databases where
     * every itemset contains exactly one item.
     * @param mapSequenceID map from item to the list of sequence ids containing it
     * @throws IOException if an error occurs while writing the output file
     */
    private void bideWithSingleItems(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
        List<int[]> sequences = sequenceDatabase.getSequences();

        // rename frequent items in-place and trim each sequence
        for (int i = 0, size = sequences.size(); i < size; i++) {
            int[] sequence = sequences.get(i);
            int currentPosition = 0;

            for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
                int token = sequence[j];

                if (token > 0) {
                    int newName = itemNameConverter.toNewName(token);
                    if (newName != 0) {
                        sequence[currentPosition++] = newName;
                    }
                } else if (token == -2) {
                    if (currentPosition > 0) {
                        sequence[currentPosition] = -2;
                        int[] newSequence = new int[currentPosition + 1];
                        System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
                        sequences.set(i, newSequence);
                    } else {
                        sequences.set(i, null);
                    }
                    break;
                }
            }
        }

        // explore each frequent length-1 pattern
        for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
            List<Integer> sequenceIDList = entry.getValue();
            int support = sequenceIDList.size();
            if (support >= minsuppAbsolute) {
                int item = entry.getKey();
                int newItem = itemNameConverter.toNewName(item);
                int[] seqIdArray = toIntArray(sequenceIDList);

                // apply backscan pruning before exploring extensions
                boolean canExplore = checkBackscanPruningSingleItemsFirstTime(newItem, seqIdArray, support);

                if (canExplore) {
                    patternBuffer[0] = newItem;

                    // build the projected database for this item
                    ensureDepthBuffers(1);
                    int projSize = buildProjectedDatabaseSingleItems(newItem, seqIdArray, support,
                            projSeqIDsBuffer[1], projIndexesBuffer[1]);

                    // recursively explore extensions
                    int maxSupportExtensions = 0;
                    if (maximumPatternLength > 1) {
                        maxSupportExtensions = recursionSingleItems(projSeqIDsBuffer[1], projIndexesBuffer[1],
                                projSize, 2, 0, 1);
                    }

                    // save the pattern only if it is closed
                    if (support != maxSupportExtensions) {
                        boolean isClosed = checkBackwardExtensionSingleItemsFirstTime(newItem, seqIdArray, support);
                        if (isClosed) {
                            savePattern(item, support, sequenceIDList);
                        }
                    }
                }
            }
        }
    }

    /**
     * Renames frequent single items and mines closed frequent patterns for databases where
     * itemsets may contain more than one item.
     * @param mapSequenceID map from item to the list of sequence ids containing it
     * @throws IOException if an error occurs while writing the output file
     */
    private void bideWithMultipleItems(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
        List<int[]> sequences = sequenceDatabase.getSequences();

        // rename frequent items in-place and trim each sequence
        for (int i = 0, size = sequences.size(); i < size; i++) {
            int[] sequence = sequences.get(i);
            int currentPosition = 0;
            int currentItemsetItemCount = 0;

            for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
                int token = sequence[j];

                if (token > 0) {
                    int newName = itemNameConverter.toNewName(token);
                    if (newName != 0) {
                        sequence[currentPosition++] = newName;
                        currentItemsetItemCount++;
                    }
                } else if (token == -1) {
                    if (currentItemsetItemCount > 0) {
                        sequence[currentPosition++] = -1;
                        currentItemsetItemCount = 0;
                    }
                } else if (token == -2) {
                    if (currentPosition > 0) {
                        sequence[currentPosition] = -2;
                        int[] newSequence = new int[currentPosition + 1];
                        System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
                        sequences.set(i, newSequence);
                    } else {
                        sequences.set(i, null);
                    }
                    break;
                }
            }
        }

        // explore each frequent length-1 pattern
        for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
            List<Integer> sequenceIDList = entry.getValue();
            int support = sequenceIDList.size();
            if (support >= minsuppAbsolute) {
                int item = entry.getKey();
                int newItem = itemNameConverter.toNewName(item);
                int[] seqIdArray = toIntArray(sequenceIDList);

                patternBuffer[0] = newItem;

                // apply backscan pruning before exploring extensions
                boolean canExplore = isSafeToExplore(0, seqIdArray, support);

                if (canExplore) {
                    // build the projected database for this item
                    ensureDepthBuffers(1);
                    int projSize = buildProjectedDatabaseFirstTimeMultipleItems(newItem, seqIdArray, support,
                            projSeqIDsBuffer[1], projIndexesBuffer[1]);

                    // recursively explore extensions
                    int maxSupportExtensions = 0;
                    if (maximumPatternLength > 1) {
                        maxSupportExtensions = recursionMultipleItems(projSeqIDsBuffer[1], projIndexesBuffer[1],
                                projSize, 2, 0, 1);
                    }

                    // save the pattern only if it is closed
                    if (support != maxSupportExtensions) {
                        patternBuffer[0] = newItem;
                        if (isClosed(0, seqIdArray, support)) {
                            savePattern(item, support, sequenceIDList);
                        }
                    }
                }
            }
        }
    }

    /**
     * Converts a list of Integers to a primitive int array.
     * @param list the list to convert
     * @return the equivalent primitive int array
     */
    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * BackScan pruning check for a length-1 pattern (single-item database), performed with a
     * single forward pass per supporting sequence.
     * @param item the pattern item (renamed id)
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @return true if it is safe to keep exploring
     */
    private boolean checkBackscanPruningSingleItemsFirstTime(int item, int[] sequenceIDs, int size) {
        List<int[]> sequences = sequenceDatabase.getSequences();
        singleItemSupportCounter.nextRound();
        int highestSupportUntilNow = 0;

        for (int k = 0; k < size; k++) {
            int sid = sequenceIDs[k];
            int[] sequence = sequences.get(sid);

            singleItemSeenStamp.nextRound();

            // scan each item before the pattern item in this sequence
            for (int j = 0; sequence[j] != -2; j++) {
                int token = sequence[j];

                if (token > 0) {
                    if (token == item) {
                        break;
                    } else if (!singleItemSeenStamp.isMarkedThisRound(token)) {
                        singleItemSeenStamp.mark(token);
                        int newSupport = singleItemSupportCounter.get(token) + 1;
                        singleItemSupportCounter.set(token, newSupport);
                        if (newSupport > highestSupportUntilNow) {
                            highestSupportUntilNow = newSupport;
                        }
                        // pruning condition met: a preceding item is as frequent as the pattern
                        if (newSupport == size) {
                            return false;
                        }
                    }
                }
            }

            // early termination: remaining sequences cannot raise any counter to minsup
            if (highestSupportUntilNow + (size - k - 1) < size) {
                return true;
            }
        }
        return true;
    }

    /**
     * Backward-extension (closure) check for a length-1 pattern (single-item database),
     * performed with a single backward pass per supporting sequence.
     * @param item the pattern item (renamed id)
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @return true if the pattern is closed
     */
    private boolean checkBackwardExtensionSingleItemsFirstTime(int item, int[] sequenceIDs, int size) {
        List<int[]> sequences = sequenceDatabase.getSequences();
        singleItemSupportCounter.nextRound();
        int highestSupportUntilNow = 0;

        for (int k = 0; k < size; k++) {
            int sid = sequenceIDs[k];
            int[] sequence = sequences.get(sid);

            singleItemSeenStamp.nextRound();

            // scan backward, collecting items that appear after the pattern item
            boolean foundTheItem = false;
            for (int j = sequence.length - 1; j >= 0; j--) {
                int token = sequence[j];
                if (token > 0) {
                    if (token == item) {
                        foundTheItem = true;
                        continue;
                    }
                    if (foundTheItem) {
                        if (!singleItemSeenStamp.isMarkedThisRound(token)) {
                            singleItemSeenStamp.mark(token);
                            int newSupport = singleItemSupportCounter.get(token) + 1;
                            singleItemSupportCounter.set(token, newSupport);
                            if (newSupport > highestSupportUntilNow) {
                                highestSupportUntilNow = newSupport;
                            }
                            // a suffix item reaches minsup: pattern is not closed
                            if (newSupport == size) {
                                return false;
                            }
                        }
                    }
                }
            }

            // early termination: remaining sequences cannot raise any counter to minsup
            if (highestSupportUntilNow + (size - k - 1) < size) {
                return true;
            }
        }
        return true;
    }

    /**
     * Wrapper around {@link #checkBackScanPruning} with an unambiguous "true = proceed" polarity.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @return true if it is safe to keep exploring
     */
    private boolean isSafeToExplore(int lastBufferPosition, int[] sequenceIDs, int size) {
        return !checkBackScanPruning(lastBufferPosition, sequenceIDs, size);
    }

    /**
     * Wrapper around {@link #hasBackwardExtension} with an unambiguous "true = closed" polarity.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @return true if the pattern is closed
     */
    private boolean isClosed(int lastBufferPosition, int[] sequenceIDs, int size) {
        return !hasBackwardExtension(lastBufferPosition, sequenceIDs, size);
    }

    /**
     * Computes the [start,end] index range of each itemset of the pattern stored in
     * patternBuffer[0..lastBufferPosition], into {@link #patItemsetStart}/{@link #patItemsetEnd}.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @return the number of itemsets composing the pattern
     */
    private int computeItemsetBoundaries(int lastBufferPosition) {
        int count = 0;
        int curStart = 0;
        // scan the buffer and record start/end of each itemset
        for (int idx = 0; idx <= lastBufferPosition; idx++) {
            if (patternBuffer[idx] == -1) {
                patItemsetStart[count] = curStart;
                patItemsetEnd[count] = idx - 1;
                count++;
                curStart = idx + 1;
            }
        }
        // record the final itemset
        patItemsetStart[count] = curStart;
        patItemsetEnd[count] = lastBufferPosition;
        count++;
        return count;
    }

    /**
     * Checks whether sequence[start..end] contains every item of patternBuffer[patStart..patEnd].
     * @param sequence the sequence to search in
     * @param start start index (inclusive) of the range to search
     * @param end end index (inclusive) of the range to search
     * @param patStart start index (inclusive) of the pattern itemset
     * @param patEnd end index (inclusive) of the pattern itemset
     * @return true if all pattern items are found in the range
     */
    private boolean rangeContainsAllPatItemset(int[] sequence, int start, int end, int patStart, int patEnd) {
        for (int pi = patStart; pi <= patEnd; pi++) {
            int target = patternBuffer[pi];
            boolean found = false;
            // search for this pattern item in the window
            for (int p = start; p <= end; p++) {
                if (sequence[p] == target) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether patternBuffer[patStart..patEnd] already contains the given value.
     * @param patStart start index (inclusive) of the pattern itemset
     * @param patEnd end index (inclusive) of the pattern itemset
     * @param value the value to look for
     * @return true if the value is present
     */
    private boolean containsInPatItemset(int patStart, int patEnd, int value) {
        for (int i = patStart; i <= patEnd; i++) {
            if (patternBuffer[i] == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes, for one sequence, the leftmost ("forward") match window start of each pattern
     * itemset and the rightmost ("backward") match window start of each pattern itemset, used by
     * {@link #checkBackScanPruning}.
     * @param sequence the sequence to match against
     * @param m number of itemsets of the pattern
     * @param L output: start of the forward gap before itemset k
     * @param Tstart output: start of the backward match of itemset k
     * @return true if the pattern fully matches both forward and backward in this sequence
     */
    private boolean computeWindows(int[] sequence, int m, int[] L, int[] Tstart) {
        L[0] = 0;
        int itemsetIdx = 0;
        int pos = 0;
        int len = sequence.length;
        int lastMatchEnd = -1;

        // forward pass: find leftmost match for each pattern itemset
        while (pos < len && sequence[pos] != -2 && itemsetIdx < m) {
            int itemsetStart = pos;
            while (pos < len && sequence[pos] != -1 && sequence[pos] != -2) {
                pos++;
            }
            int itemsetEnd = pos - 1;
            if (rangeContainsAllPatItemset(sequence, itemsetStart, itemsetEnd, patItemsetStart[itemsetIdx],
                    patItemsetEnd[itemsetIdx])) {
                lastMatchEnd = pos;
                itemsetIdx++;
                L[itemsetIdx] = pos + 1;
            }
            if (pos < len && sequence[pos] == -1) {
                pos++;
            }
        }

        if (itemsetIdx < m) {
            return false;
        }

        // backward pass: find rightmost match for each pattern itemset
        int end = lastMatchEnd - 1;
        int itemsetIdx2 = m - 1;
        while (itemsetIdx2 >= 0 && end >= 0) {
            int start = end;
            while (start > 0 && sequence[start - 1] != -1 && sequence[start - 1] != -2) {
                start--;
            }
            if (rangeContainsAllPatItemset(sequence, start, end, patItemsetStart[itemsetIdx2],
                    patItemsetEnd[itemsetIdx2])) {
                Tstart[itemsetIdx2] = start;
                itemsetIdx2--;
            }
            end = start - 2;
        }

        return itemsetIdx2 < 0;
    }

    /**
     * BackScan search-space pruning test (BIDE+ Theorem 2): sound but not necessarily complete,
     * used only for pruning, never for the exact closure test.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @return true if pruning applies (caller should negate via {@link #isSafeToExplore})
     */
    private boolean checkBackScanPruning(int lastBufferPosition, int[] sequenceIDs, int size) {
        int m = computeItemsetBoundaries(lastBufferPosition);

        // reset per-slot counters for this round
        ensureBackscanSlotCounters(m);
        for (int k = 0; k < m; k++) {
            backscanSlotCounters[k].nextRound();
        }

        List<int[]> sequences = sequenceDatabase.getSequences();

        for (int idx = 0; idx < size; idx++) {
            int sid = sequenceIDs[idx];
            int[] sequence = sequences.get(sid);

            // compute the gap windows for this sequence
            boolean fullyMatched = computeWindows(sequence, m, scratchL, scratchTstart);
            if (!fullyMatched) {
                continue;
            }

            // count items appearing inside each gap
            for (int k = 0; k < m; k++) {
                backscanSeenStamp.nextRound();
                StampedIntArray counter = backscanSlotCounters[k];
                for (int p = scratchL[k]; p < scratchTstart[k]; p++) {
                    int tok = sequence[p];
                    if (tok > 0 && !backscanSeenStamp.isMarkedThisRound(tok)) {
                        backscanSeenStamp.mark(tok);
                        int c = counter.get(tok) + 1;
                        counter.set(tok, c);
                        // a gap item reaches minsup: pruning applies
                        if (c == size) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Computes, for one sequence, the leftmost ("forward") greedy match end position of each
     * pattern itemset, used by {@link #hasBackwardExtension}.
     * @param sequence the sequence to match against
     * @param m number of itemsets of the pattern
     * @param chunkEnd output: end position of the forward match of itemset k
     * @return true if all m itemsets were matched
     */
    private boolean computeForwardChunks(int[] sequence, int m, int[] chunkEnd) {
        int itemsetIdx = 0;
        int pos = 0;
        int len = sequence.length;
        // greedy left-to-right match of each pattern itemset
        while (pos < len && sequence[pos] != -2 && itemsetIdx < m) {
            int start = pos;
            while (pos < len && sequence[pos] != -1 && sequence[pos] != -2) {
                pos++;
            }
            int end = pos - 1;
            if (rangeContainsAllPatItemset(sequence, start, end, patItemsetStart[itemsetIdx],
                    patItemsetEnd[itemsetIdx])) {
                chunkEnd[itemsetIdx] = end;
                itemsetIdx++;
            }
            if (pos < len && sequence[pos] == -1) {
                pos++;
            }
        }
        return itemsetIdx >= m;
    }

    /**
     * Computes, for one sequence, the rightmost ("backward") greedy match start position of each
     * pattern itemset, used by {@link #hasBackwardExtension}.
     * @param sequence the sequence to match against
     * @param m number of itemsets of the pattern
     * @param chunkStart output: start position of the backward match of itemset k
     * @return true if all m itemsets were matched
     */
    private boolean computeBackwardChunks(int[] sequence, int m, int[] chunkStart) {
        int len = sequence.length;
        int end = len - 1;
        // skip trailing separators
        while (end >= 0 && (sequence[end] == -1 || sequence[end] == -2)) {
            end--;
        }
        // greedy right-to-left match of each pattern itemset
        int itemsetIdx = m - 1;
        while (itemsetIdx >= 0 && end >= 0) {
            int start = end;
            while (start > 0 && sequence[start - 1] != -1 && sequence[start - 1] != -2) {
                start--;
            }
            if (rangeContainsAllPatItemset(sequence, start, end, patItemsetStart[itemsetIdx],
                    patItemsetEnd[itemsetIdx])) {
                chunkStart[itemsetIdx] = start;
                itemsetIdx--;
            }
            end = start - 2;
        }
        return itemsetIdx < 0;
    }

    /**
     * Exact backward-extension (closure) test: checks, for every sequence supporting the
     * pattern, whether any item could be merged into an itemset or inserted as a new itemset
     * without lowering the pattern's support.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @return true if an extension was found (caller should negate via {@link #isClosed})
     */
    private boolean hasBackwardExtension(int lastBufferPosition, int[] sequenceIDs, int size) {
        int m = computeItemsetBoundaries(lastBufferPosition);

        // reset per-slot counters for this round
        ensureExtensionSlotCounters(m);
        for (int k = 0; k < m; k++) {
            mergeSlotCounters[k].nextRound();
            insertSlotCounters[k].nextRound();
        }

        List<int[]> sequences = sequenceDatabase.getSequences();

        for (int idx = 0; idx < size; idx++) {
            int[] sequence = sequences.get(sequenceIDs[idx]);
            int seqLen = sequence.length;

            // compute forward and backward chunk positions
            boolean fwdOk = computeForwardChunks(sequence, m, fwdChunkEnd);
            boolean bwdOk = computeBackwardChunks(sequence, m, bwdChunkStart);
            if (!fwdOk || !bwdOk) {
                continue;
            }

            for (int k = 0; k < m; k++) {
                int leftBound = (k == 0) ? 0 : fwdChunkEnd[k - 1] + 2;

                // check merge (i-extension): item added within an existing itemset
                int rightBoundMerge = (k == m - 1) ? (seqLen - 1) : bwdChunkStart[k + 1];
                if (leftBound < rightBoundMerge) {
                    backwardExtSeenStamp.nextRound();
                    int pos = leftBound;
                    while (pos < rightBoundMerge && sequence[pos] != -2) {
                        int start = pos;
                        while (pos < rightBoundMerge && sequence[pos] != -1 && sequence[pos] != -2) {
                            pos++;
                        }
                        int end = pos - 1;
                        if (end >= start && rangeContainsAllPatItemset(sequence, start, end, patItemsetStart[k],
                                patItemsetEnd[k])) {
                            StampedIntArray counter = mergeSlotCounters[k];
                            for (int p = start; p <= end; p++) {
                                int tok = sequence[p];
                                if (tok > 0 && !containsInPatItemset(patItemsetStart[k], patItemsetEnd[k], tok)
                                        && !backwardExtSeenStamp.isMarkedThisRound(tok)) {
                                    backwardExtSeenStamp.mark(tok);
                                    int c = counter.get(tok) + 1;
                                    counter.set(tok, c);
                                    // a merge item reaches minsup: pattern is not closed
                                    if (c == size) {
                                        return true;
                                    }
                                }
                            }
                        }
                        if (pos < rightBoundMerge && sequence[pos] == -1) {
                            pos++;
                        }
                    }
                }

                // check insert (s-extension): item inserted as a new itemset
                int rightBoundInsert = bwdChunkStart[k];
                if (leftBound < rightBoundInsert) {
                    backwardExtSeenStamp.nextRound();
                    StampedIntArray counter = insertSlotCounters[k];
                    for (int p = leftBound; p < rightBoundInsert; p++) {
                        int tok = sequence[p];
                        if (tok > 0 && !backwardExtSeenStamp.isMarkedThisRound(tok)) {
                            backwardExtSeenStamp.mark(tok);
                            int c = counter.get(tok) + 1;
                            counter.set(tok, c);
                            // an insert item reaches minsup: pattern is not closed
                            if (c == size) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Saves a length-1 pattern to the output file or in memory.
     * @param item the (original id) pattern item
     * @param support support of the pattern
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @throws IOException if an error occurs while writing the output file
     */
    private void savePattern(int item, int support, List<Integer> sequenceIDs) throws IOException {
        patternCount++;

        if (writer != null) {
            // write the pattern as text
            StringBuilder r = new StringBuilder();
            r.append(item);
            r.append(" -1 #SUP: ");
            r.append(support);
            if (showSequenceIdentifiers) {
                r.append(" #SID: ");
                for (Integer sid : sequenceIDs) {
                    r.append(sid);
                    r.append(" ");
                }
            }
            writer.write(r.toString());
            writer.newLine();
        } else {
            // store the pattern in memory
            SequentialPattern pattern = new SequentialPattern();
            pattern.addItemset(new Itemset(item));
            pattern.setSequenceIDs(sequenceIDs);
            patterns.addSequence(pattern, 1);
        }
    }

    /**
     * Saves a pattern of two or more items, stored in patternBuffer using renamed item ids, to
     * the output file or in memory, converting items back to their original ids.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @param projSeqIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @throws IOException if an error occurs while writing the output file
     */
    private void savePattern(int lastBufferPosition, int[] projSeqIDs, int size) throws IOException {
        patternCount++;

        if (writer != null) {
            // convert renamed items back to original ids and write as text
            StringBuilder r = new StringBuilder((lastBufferPosition + 2) * 8 + 20);
            for (int i = 0; i <= lastBufferPosition; i++) {
                int token = patternBuffer[i];
                r.append(token == -1 ? -1 : itemNameConverter.toOldName(token));
                if (i != lastBufferPosition && !containsItemsetsWithMultipleItems) {
                    r.append(" -1");
                }
                r.append(" ");
            }
            if (patternBuffer[lastBufferPosition] != -1) {
                r.append("-1 ");
            }
            r.append("#SUP: ");
            r.append(size);
            if (showSequenceIdentifiers) {
                r.append(" #SID: ");
                for (int i = 0; i < size; i++) {
                    r.append(projSeqIDs[i]);
                    r.append(" ");
                }
            }
            writer.write(r.toString());
            writer.newLine();
        } else {
            // reconstruct the pattern object and store it in memory
            SequentialPattern pattern = new SequentialPattern();
            int itemsetCount = 0;
            Itemset currentItemset = new Itemset();
            for (int i = 0; i <= lastBufferPosition; i++) {
                int token = patternBuffer[i];
                if (token > 0) {
                    currentItemset.addItem(itemNameConverter.toOldName(token));
                } else if (token == -1) {
                    pattern.addItemset(currentItemset);
                    currentItemset = new Itemset();
                    itemsetCount++;
                }
            }
            pattern.addItemset(currentItemset);
            itemsetCount++;

            List<Integer> sequencesIDs = new ArrayList<Integer>(size);
            for (int i = 0; i < size; i++) {
                sequencesIDs.add(projSeqIDs[i]);
            }
            pattern.setSequenceIDs(sequencesIDs);
            patterns.addSequence(pattern, itemsetCount);
        }
    }

    /**
     * Scans the database once to find, for each item, the list of sequences containing it, and
     * detects whether any itemset contains more than one item.
     * @return map from item to the list of sequence ids containing it
     */
    private Map<Integer, List<Integer>> findSequencesContainingItems() {
        int itemCountInCurrentItemset;

        Map<Integer, List<Integer>> mapSequenceID = new HashMap<Integer, List<Integer>>();
        List<int[]> sequences = sequenceDatabase.getSequences();

        for (int i = 0, dbSize = sequences.size(); i < dbSize; i++) {
            int[] sequence = sequences.get(i);
            itemCountInCurrentItemset = 0;

            for (int token : sequence) {
                if (token > 0) {
                    // record that this item appears in sequence i
                    List<Integer> sequenceIDs = mapSequenceID.get(token);
                    if (sequenceIDs == null) {
                        sequenceIDs = new ArrayList<Integer>();
                        mapSequenceID.put(token, sequenceIDs);
                    }
                    if (sequenceIDs.size() == 0 || sequenceIDs.get(sequenceIDs.size() - 1) != i) {
                        sequenceIDs.add(i);
                    }
                    itemCountInCurrentItemset++;
                    // flag if any itemset has more than one item
                    if (itemCountInCurrentItemset > 1) {
                        containsItemsetsWithMultipleItems = true;
                    }
                } else if (token == -1) {
                    itemCountInCurrentItemset = 0;
                }
            }
        }
        return mapSequenceID;
    }

    /**
     * Builds the projected database of a length-1 pattern for a single-item database.
     * @param item the pattern item (renamed id)
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @param outSeqIDs output array of sequence ids of the projected database
     * @param outIndexes output array of start indexes into each sequence
     * @return the size of the projected database
     */
    private int buildProjectedDatabaseSingleItems(int item, int[] sequenceIDs, int size, int[] outSeqIDs,
            int[] outIndexes) {
        List<int[]> sequences = sequenceDatabase.getSequences();
        int projSize = 0;

        loopSeq: for (int idx = 0; idx < size; idx++) {
            int sequenceID = sequenceIDs[idx];
            int[] sequence = sequences.get(sequenceID);

            // find the first occurrence of the item and project just after it
            for (int j = 0; sequence[j] != -2; j++) {
                int token = sequence[j];
                if (token == item) {
                    if (sequence[j + 1] != -2) {
                        outSeqIDs[projSize] = sequenceID;
                        outIndexes[projSize] = j + 1;
                        projSize++;
                    }
                    continue loopSeq;
                }
            }
        }
        return projSize;
    }

    /**
     * Builds the projected database of a length-1 pattern for a multi-item database.
     * @param item the pattern item (renamed id)
     * @param sequenceIDs ids of the sequences supporting the pattern
     * @param size support of the pattern
     * @param outSeqIDs output array of sequence ids of the projected database
     * @param outIndexes output array of start indexes into each sequence
     * @return the size of the projected database
     */
    private int buildProjectedDatabaseFirstTimeMultipleItems(int item, int[] sequenceIDs, int size, int[] outSeqIDs,
            int[] outIndexes) {
        List<int[]> sequences = sequenceDatabase.getSequences();
        int projSize = 0;

        loopSeq: for (int idx = 0; idx < size; idx++) {
            int sequenceID = sequenceIDs[idx];
            int[] sequence = sequences.get(sequenceID);

            // find the first occurrence of the item and project just after it
            for (int j = 0; sequence[j] != -2; j++) {
                int token = sequence[j];
                if (token == item) {
                    boolean isEndOfSequence = sequence[j + 1] == -1 && sequence[j + 2] == -2;
                    if (!isEndOfSequence) {
                        outSeqIDs[projSize] = sequenceID;
                        outIndexes[projSize] = j + 1;
                        projSize++;
                    }
                    continue loopSeq;
                }
            }
        }
        return projSize;
    }

    /**
     * Recursively extends a pattern one item at a time for single-item databases, saving each
     * closed pattern found.
     * @param projSeqIDs sequence ids of the projected database
     * @param projIndexes start indexes into each sequence of the projected database
     * @param projSize size of the projected database
     * @param k length of the pattern that would result from one more extension
     * @param lastBufferPosition index of the last item of the current pattern in patternBuffer
     * @param depth recursion depth, used to select reusable buffers
     * @return the maximum support among all extensions of the current pattern
     * @throws IOException if an error occurs while writing the output file
     */
    private int recursionSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize, int k, int lastBufferPosition,
            int depth) throws IOException {

        ensureDepthBuffers(depth);

        // find all frequent candidate items in the projected database
        ItemPairArray itemsPseudoSequences = singleItemsArrays[depth];
        findAllFrequentPairsSingleItems(projSeqIDs, projIndexes, projSize, itemsPseudoSequences);

        int nextDepth = depth + 1;
        ensureDepthBuffers(nextDepth);

        int maxSupport = 0;

        for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
            int item = itemsPseudoSequences.touchedItems[idx];
            PairData pairData = itemsPseudoSequences.dataByItem[item];
            int support = pairData.projSize;

            if (support >= minsuppAbsolute) {
                if (support > maxSupport) {
                    maxSupport = support;
                }

                patternBuffer[lastBufferPosition + 1] = item;

                // apply backscan pruning before exploring further
                boolean canExplore = checkBackscanPruningSingleItems(lastBufferPosition + 1, pairData.seqIDs,
                        support);

                if (canExplore) {
                    System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, support);
                    System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, support);

                    // recurse to find longer patterns
                    int maxSupportExtensions = 0;
                    if (k < maximumPatternLength) {
                        maxSupportExtensions = recursionSingleItems(projSeqIDsBuffer[nextDepth],
                                projIndexesBuffer[nextDepth], support, k + 1, lastBufferPosition + 1, nextDepth);
                    }

                    // save only if no extension has the same support (i.e., the pattern is closed)
                    if (support != maxSupportExtensions) {
                        boolean isClosed = checkBackwardExtensionSingleItems(lastBufferPosition + 1, pairData.seqIDs,
                                support);
                        if (isClosed) {
                            savePattern(lastBufferPosition + 1, pairData.seqIDs, support);
                        }
                    }
                }
            }
        }

        // reset touched entries for reuse at this depth
        for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
            itemsPseudoSequences.dataByItem[itemsPseudoSequences.touchedItems[idx]].reset();
        }

        return maxSupport;
    }

    /**
     * BackScan pruning check for single-item databases, using one forward greedy match per
     * sequence to derive every slot's gap at once.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @param projSeqIDs ids of the sequences supporting the pattern
     * @param projSize support of the pattern
     * @return true if it is safe to keep exploring
     */
    private boolean checkBackscanPruningSingleItems(int lastBufferPosition, int[] projSeqIDs, int projSize) {
        int m = lastBufferPosition + 1;
        ensureBackscanSlotCounters(m);
        for (int k = 0; k < m; k++) {
            backscanSlotCounters[k].nextRound();
        }

        List<int[]> sequences = sequenceDatabase.getSequences();

        for (int idx = 0; idx < projSize; idx++) {
            int[] sequence = sequences.get(projSeqIDs[idx]);

            // forward greedy match to locate each pattern item
            int matched = 0;
            for (int pos = 0; sequence[pos] != -2 && matched < m; pos++) {
                if (sequence[pos] == patternBuffer[matched]) {
                    scratchMatchEnd[matched] = pos;
                    matched++;
                }
            }
            if (matched < m) {
                continue;
            }

            // count items appearing in each gap between matched positions
            int left = 0;
            for (int k = 0; k < m; k++) {
                int right = scratchMatchEnd[k];
                if (left < right) {
                    backscanSeenStamp.nextRound();
                    StampedIntArray counter = backscanSlotCounters[k];
                    for (int p = left; p < right; p++) {
                        int tok = sequence[p];
                        if (tok > 0 && !backscanSeenStamp.isMarkedThisRound(tok)) {
                            backscanSeenStamp.mark(tok);
                            int c = counter.get(tok) + 1;
                            counter.set(tok, c);
                            // a gap item reaches minsup: pruning applies
                            if (c == projSize) {
                                return false;
                            }
                        }
                    }
                }
                left = right + 1;
            }
        }
        return true;
    }

    /**
     * Backward-extension (closure) check for single-item databases, using one forward and one
     * backward greedy match per sequence to derive every slot's gap at once.
     * @param lastBufferPosition index of the last item of the pattern in patternBuffer
     * @param projSeqIDs ids of the sequences supporting the pattern
     * @param projSize support of the pattern
     * @return true if the pattern is closed
     */
    private boolean checkBackwardExtensionSingleItems(int lastBufferPosition, int[] projSeqIDs, int projSize) {
        int m = lastBufferPosition + 1;
        ensureExtensionSlotCounters(m);
        for (int k = 0; k < m; k++) {
            insertSlotCounters[k].nextRound();
        }

        List<int[]> sequences = sequenceDatabase.getSequences();

        for (int idx = 0; idx < projSize; idx++) {
            int[] sequence = sequences.get(projSeqIDs[idx]);
            int len = sequence.length;

            // forward greedy match
            int matchedF = 0;
            for (int pos = 0; sequence[pos] != -2 && matchedF < m; pos++) {
                if (sequence[pos] == patternBuffer[matchedF]) {
                    fwdChunkStart[matchedF] = pos;
                    matchedF++;
                }
            }
            // backward greedy match
            int matchedB = m - 1;
            for (int pos = len - 2; pos >= 0 && matchedB >= 0; pos--) {
                if (sequence[pos] == patternBuffer[matchedB]) {
                    bwdChunkStart[matchedB] = pos;
                    matchedB--;
                }
            }
            if (matchedF < m || matchedB >= 0) {
                continue;
            }

            // count items appearing in each backward gap
            int left = 0;
            for (int k = 0; k < m; k++) {
                int right = bwdChunkStart[k];
                if (left < right) {
                    backwardExtSeenStamp.nextRound();
                    StampedIntArray counter = insertSlotCounters[k];
                    for (int p = left; p < right; p++) {
                        int tok = sequence[p];
                        if (tok > 0 && !backwardExtSeenStamp.isMarkedThisRound(tok)) {
                            backwardExtSeenStamp.mark(tok);
                            int c = counter.get(tok) + 1;
                            counter.set(tok, c);
                            // a suffix item reaches minsup: pattern is not closed
                            if (c == projSize) {
                                return false;
                            }
                        }
                    }
                }
                left = fwdChunkStart[k] + 1;
            }
        }
        return true;
    }

    /**
     * Recursively extends a pattern (by item-extension or sequence-extension) for multi-item
     * databases, saving each closed pattern found.
     * @param projSeqIDs sequence ids of the projected database
     * @param projIndexes start indexes into each sequence of the projected database
     * @param projSize size of the projected database
     * @param k length of the pattern that would result from one more extension
     * @param lastBufferPosition index of the last item of the current pattern in patternBuffer
     * @param depth recursion depth, used to select reusable buffers
     * @return the maximum support among all extensions of the current pattern
     * @throws IOException if an error occurs while writing the output file
     */
    private int recursionMultipleItems(int[] projSeqIDs, int[] projIndexes, int projSize, int k,
            int lastBufferPosition, int depth) throws IOException {

        ensureDepthBuffers(depth);

        // find all frequent candidate pairs (postfix and non-postfix)
        ArrayFrequentPairs mapsPairs = frequentPairsArrays[depth];
        findAllFrequentPairs(projSeqIDs, projIndexes, projSize, lastBufferPosition, mapsPairs);

        int nextDepth = depth + 1;
        ensureDepthBuffers(nextDepth);

        int maxSupport = 0;

        // explore i-extensions (merge into last itemset)
        ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
        for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
            int item = postfixArr.touchedItems[idx];
            PairData pairData = postfixArr.dataByItem[item];
            int support = pairData.projSize;

            if (support >= minsuppAbsolute) {
                if (support > maxSupport) {
                    maxSupport = support;
                }

                int newBufferPosition = lastBufferPosition + 1;
                patternBuffer[newBufferPosition] = item;

                // apply backscan pruning before exploring further
                boolean canExplore = isSafeToExplore(newBufferPosition, pairData.seqIDs, support);

                if (canExplore) {
                    System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, support);
                    System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, support);

                    // recurse to find longer patterns
                    int maxSupportExtensions = 0;
                    if (k < maximumPatternLength) {
                        maxSupportExtensions = recursionMultipleItems(projSeqIDsBuffer[nextDepth],
                                projIndexesBuffer[nextDepth], support, k + 1, newBufferPosition, nextDepth);
                    }

                    // save only if closed
                    if (support != maxSupportExtensions) {
                        patternBuffer[newBufferPosition] = item;
                        if (isClosed(newBufferPosition, pairData.seqIDs, support)) {
                            savePattern(newBufferPosition, pairData.seqIDs, support);
                        }
                    }
                }
            }
        }

        // explore s-extensions (append as new itemset)
        ItemPairArray pairsArr = mapsPairs.pairs;
        for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
            int item = pairsArr.touchedItems[idx];
            PairData pairData = pairsArr.dataByItem[item];
            int support = pairData.projSize;

            if (support >= minsuppAbsolute) {
                if (support > maxSupport) {
                    maxSupport = support;
                }

                // insert the itemset separator followed by the new item
                int newBufferPosition = lastBufferPosition + 1;
                patternBuffer[newBufferPosition] = -1;
                newBufferPosition++;
                patternBuffer[newBufferPosition] = item;

                // apply backscan pruning before exploring further
                boolean canExplore = isSafeToExplore(newBufferPosition, pairData.seqIDs, support);

                if (canExplore) {
                    System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, support);
                    System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, support);

                    // recurse to find longer patterns
                    int maxSupportExtensions = 0;
                    if (k < maximumPatternLength) {
                        maxSupportExtensions = recursionMultipleItems(projSeqIDsBuffer[nextDepth],
                                projIndexesBuffer[nextDepth], support, k + 1, newBufferPosition, nextDepth);
                    }

                    // save only if closed
                    if (support != maxSupportExtensions) {
                        patternBuffer[newBufferPosition - 1] = -1;
                        patternBuffer[newBufferPosition] = item;
                        if (isClosed(newBufferPosition, pairData.seqIDs, support)) {
                            savePattern(newBufferPosition, pairData.seqIDs, support);
                        }
                    }
                }
            }
        }

        // reset touched entries for reuse at this depth
        for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
            postfixArr.dataByItem[postfixArr.touchedItems[idx]].reset();
        }
        for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
            pairsArr.dataByItem[pairsArr.touchedItems[idx]].reset();
        }

        return maxSupport;
    }

    /** Holds the postfix and non-postfix candidate item counters used when extending a pattern in a multi-item database. */
    public static class ArrayFrequentPairs {
        /** Candidate items that would create an s-extension (new itemset). */
        public final ItemPairArray pairs;
        /** Candidate items that would create an i-extension (merged into the last itemset). */
        public final ItemPairArray pairsInPostfix;

        /**
         * Creates the candidate counters, sized for the given number of frequent items.
         * @param frequentItemCount number of distinct frequent items
         */
        public ArrayFrequentPairs(int frequentItemCount) {
            pairs = new ItemPairArray(frequentItemCount);
            pairsInPostfix = new ItemPairArray(frequentItemCount);
        }
    }

    /** Sparse, reusable array of per-item candidate data, indexed directly by item id. */
    static class ItemPairArray {
        /** Candidate data indexed by item id. */
        final PairData[] dataByItem;
        /** Items touched since the last reset, used to clear {@link #dataByItem} in O(touched). */
        final int[] touchedItems;
        /** Number of items touched since the last reset. */
        int touchedCount;

        /**
         * Creates the array, sized for the given number of frequent items.
         * @param frequentItemCount number of distinct frequent items
         */
        ItemPairArray(int frequentItemCount) {
            dataByItem = new PairData[frequentItemCount + 1];
            touchedItems = new int[frequentItemCount + 1];
            touchedCount = 0;
        }
    }

    /** Growable parallel arrays of sequence ids and start indexes forming a candidate's projected database. */
    static class PairData {
        /** Sequence ids of the projected database. */
        int[] seqIDs;
        /** Start indexes into each sequence of the projected database. */
        int[] indexes;
        /** Number of entries currently stored. */
        int projSize;

        /** Creates an empty PairData with a small initial capacity. */
        PairData() {
            seqIDs = new int[8];
            indexes = new int[8];
            projSize = 0;
        }

        /**
         * Appends one (sequence id, index) entry, growing the backing arrays if needed.
         * @param seqID the sequence id to add
         * @param index the start index into that sequence to add
         */
        void add(int seqID, int index) {
            if (projSize == seqIDs.length) {
                // double the capacity when full
                int newCap = projSize * 2;
                int[] newSeqIDs = new int[newCap];
                int[] newIndexes = new int[newCap];
                System.arraycopy(seqIDs, 0, newSeqIDs, 0, projSize);
                System.arraycopy(indexes, 0, newIndexes, 0, projSize);
                seqIDs = newSeqIDs;
                indexes = newIndexes;
            }
            seqIDs[projSize] = seqID;
            indexes[projSize] = index;
            projSize++;
        }

        /** Clears the entries without releasing the backing arrays. */
        void reset() {
            projSize = 0;
        }

        /**
         * Checks whether the last recorded sequence id equals the given one.
         * @param seqID the sequence id to compare against
         * @return true if the last entry's sequence id matches
         */
        boolean lastSeqIDEquals(int seqID) {
            return projSize > 0 && seqIDs[projSize - 1] == seqID;
        }
    }

    /** Reusable generation-stamped int array, used as an O(1)-clearable counter/set. */
    static final class StampedIntArray {
        /** Stored value for each index, valid only if {@link #stamp} matches {@link #currentStamp}. */
        private final int[] value;
        /** Generation at which each index's value was last set. */
        private final int[] stamp;
        /** Current generation number. */
        private int currentStamp = 0;

        /**
         * Creates a stamped array of the given size, all entries initially unset.
         * @param size number of indexable slots
         */
        StampedIntArray(int size) {
            value = new int[size];
            stamp = new int[size];
        }

        /** Logically clears the structure in O(1) by advancing the generation counter. */
        void nextRound() {
            currentStamp++;
        }

        /**
         * Checks whether the given index was marked/set in the current generation.
         * @param idx the index to check
         * @return true if set in the current generation
         */
        boolean isMarkedThisRound(int idx) {
            return stamp[idx] == currentStamp;
        }

        /**
         * Marks the given index as set (with value 0) in the current generation.
         * @param idx the index to mark
         */
        void mark(int idx) {
            stamp[idx] = currentStamp;
        }

        /**
         * Gets the value at the given index for the current generation.
         * @param idx the index to read
         * @return the stored value, or 0 if not set in the current generation
         */
        int get(int idx) {
            return stamp[idx] == currentStamp ? value[idx] : 0;
        }

        /**
         * Sets the value at the given index for the current generation.
         * @param idx the index to write
         * @param v the value to store
         */
        void set(int idx, int v) {
            stamp[idx] = currentStamp;
            value[idx] = v;
        }
    }

    /**
     * Finds all frequent candidate items following a length-1 pattern, for single-item
     * databases.
     * @param projSeqIDs sequence ids of the projected database
     * @param projIndexes start indexes into each sequence of the projected database
     * @param projSize size of the projected database
     * @param outArray candidate array to fill with per-item projected databases
     */
    protected void findAllFrequentPairsSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize,
            ItemPairArray outArray) {

        List<int[]> dbSequences = sequenceDatabase.getSequences();

        outArray.touchedCount = 0;

        for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
            int sequenceID = projSeqIDs[seqIdx];
            int[] sequence = dbSequences.get(sequenceID);

            // scan from the projection start to find all items
            for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
                int token = sequence[i];

                if (token > 0) {
                    PairData pairData = outArray.dataByItem[token];
                    if (pairData == null) {
                        pairData = new PairData();
                        outArray.dataByItem[token] = pairData;
                    }

                    // mark the item as touched if this is its first occurrence
                    if (pairData.projSize == 0) {
                        outArray.touchedItems[outArray.touchedCount++] = token;
                    }

                    // add projection entry if not already recorded for this sequence
                    if (!pairData.lastSeqIDEquals(sequenceID)) {
                        pairData.add(sequenceID, i + 1);
                    }
                }
            }
        }

        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Finds all frequent candidate items (postfix and non-postfix) following the current
     * pattern, for multi-item databases.
     * @param projSeqIDs sequence ids of the projected database
     * @param projIndexes start indexes into each sequence of the projected database
     * @param projSize size of the projected database
     * @param lastBufferPosition index of the last item of the current pattern in patternBuffer
     * @param mapsPairs candidate arrays to fill with per-item projected databases
     */
    protected void findAllFrequentPairs(int[] projSeqIDs, int[] projIndexes, int projSize, int lastBufferPosition,
            ArrayFrequentPairs mapsPairs) {

        // find the start of the last itemset in the pattern buffer
        int firstPositionOfLastItemsetInBuffer = lastBufferPosition;
        while (lastBufferPosition > 0) {
            firstPositionOfLastItemsetInBuffer--;
            if (firstPositionOfLastItemsetInBuffer < 0 || patternBuffer[firstPositionOfLastItemsetInBuffer] == -1) {
                firstPositionOfLastItemsetInBuffer++;
                break;
            }
        }

        List<int[]> dbSequences = sequenceDatabase.getSequences();

        ItemPairArray pairsArr = mapsPairs.pairs;
        ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
        pairsArr.touchedCount = 0;
        postfixArr.touchedCount = 0;

        for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
            int sequenceID = projSeqIDs[seqIdx];
            int[] sequence = dbSequences.get(sequenceID);

            int positionToBeMatched = firstPositionOfLastItemsetInBuffer;

            // determine whether the projected start is inside the last matched itemset
            int previousItem = sequence[projIndexes[seqIdx] - 1];
            boolean currentItemsetIsPostfix = (previousItem != -1);
            boolean isFirstItemset = true;

            for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
                int token = sequence[i];

                if (token > 0) {
                    // record this item as either a postfix or sequence extension candidate
                    ItemPairArray targetArr = currentItemsetIsPostfix ? postfixArr : pairsArr;
                    PairData pairData = targetArr.dataByItem[token];
                    if (pairData == null) {
                        pairData = new PairData();
                        targetArr.dataByItem[token] = pairData;
                    }
                    if (pairData.projSize == 0) {
                        targetArr.touchedItems[targetArr.touchedCount++] = token;
                    }

                    if (!pairData.lastSeqIDEquals(sequenceID)) {
                        pairData.add(sequenceID, i + 1);
                    }

                    // also record as s-extension when inside a non-first postfix itemset
                    if (currentItemsetIsPostfix && !isFirstItemset) {
                        PairData pairData2 = pairsArr.dataByItem[token];
                        if (pairData2 == null) {
                            pairData2 = new PairData();
                            pairsArr.dataByItem[token] = pairData2;
                        }
                        if (pairData2.projSize == 0) {
                            pairsArr.touchedItems[pairsArr.touchedCount++] = token;
                        }

                        if (!pairData2.lastSeqIDEquals(sequenceID)) {
                            pairData2.add(sequenceID, i + 1);
                        }
                    }

                    // advance the pattern match pointer when the token matches
                    if (!currentItemsetIsPostfix && patternBuffer[positionToBeMatched] == token) {
                        positionToBeMatched++;
                        if (positionToBeMatched > lastBufferPosition) {
                            currentItemsetIsPostfix = true;
                        }
                    }

                } else if (token == -1) {
                    // crossed an itemset boundary: reset postfix flag and match pointer
                    isFirstItemset = false;
                    currentItemsetIsPostfix = false;
                    positionToBeMatched = firstPositionOfLastItemsetInBuffer;
                }
            }
        }
        MemoryLogger.getInstance().checkMemory();
    }

    /** Prints run statistics (time, memory, pattern count) to standard output. */
    public void printStatistics() {
        StringBuilder r = new StringBuilder(200);
        r.append("===================  BIDE+ - 2.67 - STATISTICS ============\n Total time ~ ");
        r.append(endTime - startTime);
        r.append(" ms\n");
        r.append(" Frequent sequences count : " + patternCount);
        r.append('\n');
        r.append(" Max memory (mb) : ");
        r.append(MemoryLogger.getInstance().getMaxMemory());
        r.append('\n');
        r.append(" minsup = " + minsuppAbsolute + " sequences.");
        r.append('\n');
        r.append(" Pattern count : ");
        r.append(patternCount);
        r.append('\n');
        r.append("==========================================================\n");
        System.out.println(r.toString());
    }

    /**
     * Returns the maximum pattern length (in items) that will be mined.
     * @return the maximum pattern length
     */
    public int getMaximumPatternLength() {
        return maximumPatternLength;
    }

    /**
     * Sets the maximum pattern length (in items) to mine.
     * @param maximumPatternLength the maximum pattern length
     */
    public void setMaximumPatternLength(int maximumPatternLength) {
        this.maximumPatternLength = maximumPatternLength;
    }

    /**
     * Sets whether sequence identifiers should be included in the output for each pattern.
     * @param showSequenceIdentifiers true to include sequence ids, false otherwise
     */
    public void setShowSequenceIdentifiers(boolean showSequenceIdentifiers) {
        this.showSequenceIdentifiers = showSequenceIdentifiers;
    }
}