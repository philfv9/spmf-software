package ca.pfv.spmf.algorithms.sequentialpatterns.spam;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.datastructures.collections.list.ArrayListInt;
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
 * Optimized implementation of the CM-SPAM (Co-occurrence MAP based SPAM) algorithm.
 * <p>
 * Extends the bitmap-based vertical representation used by SPAM2 with CMAP
 * cooccurrence pruning: for each frequent item, two maps record which items
 * can possibly follow it later in a sequence, and which items can possibly
 * appear together with it in the same itemset, along with their support.
 * These maps are used to prune candidate extensions in the depth-first search
 * before any costly bitmap intersection is performed.
 * 
 *  @author Philippe Fournier-Viger, Antonio Gomariz
 */
public class AlgoCMSPAM {

    /** Time at which the algorithm execution started (milliseconds). */
    private long startTime;
    /** Time at which the algorithm execution ended (milliseconds). */
    private long endTime;
    /** Number of frequent patterns found. */
    private int  patternCount;

    /** Minimum support threshold, as an absolute sequence count. */
    private int minsup              = 0;
    /** Minimum length (number of itemsets) a pattern must have to be output. */
    private int minimumPatternLength = 0;
    /** Maximum length (number of itemsets) a pattern may have. */
    private int maximumPatternLength = 1000;
    /** Maximum allowed gap (in itemsets) between consecutive elements of a pattern. */
    private int maxGap               = Integer.MAX_VALUE;
    /** Whether to include sequence identifiers (SIDs) in the output. */
    private boolean outputSequenceIdentifiers;

    /**
     * Items that need to appear in patterns found by the algorithm (original item ids), or null
     * if any item is allowed.
     */
    private int[] mustAppearItems;

    /**
     * Bitmask indexed by internal (dense) item id marking which items are required by the
     * "must appear" constraint. Using a plain boolean array (rather than a sorted {@code int[]}
     * searched with binary search, or a {@code Set<Integer>}) lets membership be tested with a
     * single, allocation-free, unboxed array access during the hot depth-first search loop, and
     * naturally collapses duplicate requested items instead of inflating the required count.
     * Rebuilt at the start of each run once the item remap is known. Null if mustAppearItems is
     * null.
     */
    private boolean[] mustAppearMask;

    /**
     * Number of distinct required items that were successfully resolved to a valid internal id
     * (i.e. the number of distinct {@code true} entries in {@link #mustAppearMask}); only
     * meaningful when {@link #mustAppearMask} is non-null.
     */
    private int mustAppearRequiredCount;

    /**
     * True if every item requested via {@link #mustAppearItems} is frequent and could therefore
     * appear in some pattern; false if at least one required item is infrequent (or unknown),
     * meaning no pattern can ever satisfy the constraint. When false, {@link #spam} returns
     * immediately after resolving the mask, without building the vertical database, the CMAP
     * maps, or running the depth-first search, since all of that work would be wasted.
     * <p>
     * Because {@link #spam} always returns before any pattern is produced whenever this flag is
     * false, {@link #savePattern(int, BitmapSPAM)} and {@link #savePattern(BitmapSPAM)} never
     * run while it is false, so they do not need to re-check it.
     */
    private boolean mustAppearPossible = true;

    /**
     * Running count, during the depth-first search, of how many distinct required "must appear"
     * items are currently present in the pattern being built. Maintained incrementally in
     * lockstep with pushes/pops of {@link #patternBuffer} so that checking the constraint costs
     * O(1) instead of rescanning the whole pattern buffer at every node. This is only updated on
     * the 0-&gt;1 / 1-&gt;0 transitions of {@link #mustAppearOccurrenceCount} for a given item, so
     * that a required item that is reused more than once in the same pattern (e.g. via an S-step
     * that re-selects an item already used earlier) is only ever counted once.
     */
    private int mustAppearMatchedCount;

    /**
     * Running per-(dense internal) item-id occurrence count of how many times each required
     * "must appear" item currently appears in the pattern being built ({@link #patternBuffer}).
     * A pattern may legally reuse the same item more than once, so {@link #mustAppearMatchedCount}
     * must reflect the number of <em>distinct</em> required items present, not the number of
     * occurrences; this array lets a push/pop correctly increment/decrement
     * {@link #mustAppearMatchedCount} only on the 0-&gt;1 and 1-&gt;0 transitions. Indexed by
     * internal item id; null if {@link #mustAppearMask} is null.
     */
    private int[] mustAppearOccurrenceCount;

    /** Writer used to write discovered patterns to the output file. */
    private BufferedWriter writer = null;
    /** In-memory buffer accumulating output text before it is flushed to disk (~512 KB). */
    private final StringBuilder outputBuffer = new StringBuilder(1 << 19);
    /** Output buffer size threshold that triggers a flush to disk (~256 KB). */
    private static final int OUTPUT_BUFFER_FLUSH_THRESHOLD = 1 << 18;

    /** Buffer size used when reading the input file (64 KB). */
    private static final int INPUT_BUFFER_SIZE = 1 << 16;

    /** Vertical database, indexed by contiguous internal item ids. */
    private BitmapSPAM[] vdbArray = null;

    /** Highest original item id seen in the input file. */
    private int maxItemId = 0;

    /** Maps contiguous internal item ids back to their original ids from the input file. */
    private int[] newIdToOriginal = null;

    /** Starting bit index of each sequence in the bitmap representation. */
    private int[] sequencesSize  = null;
    /** Ending bit index of each sequence in the bitmap representation. */
    private int[] sequenceEndBit = null;

    /** Resolves a bit position to its owning sequence id (SID). */
    private BitmapSPAM.SidResolver sidResolver = null;

    /** Shared scratch buffer holding the sequence ids occupied by the current prefix bitmap. */
    private int[] sidScratchBuffer = null;

    /** Shared scratch buffer holding, for each entry in sidScratchBuffer, the first set bit of the prefix bitmap in that sequence. */
    private int[] firstBitScratchBuffer = null;

    /** True if every itemset in the input file contains at most one item, allowing I-steps to be skipped entirely. */
    private boolean onlySingletonItemsets = true;

    /** For each internal item id, the items and their support that can appear in a later itemset (CMAP). */
    private Map<Integer, Integer>[] coocMapAfter;

    /** For each internal item id, the items and their support that can appear in the same itemset (CMAP). */
    private Map<Integer, Integer>[] coocMapEquals;

    /** Counter used to throttle how often memory usage is checked. */
    private int memCheckCounter = 0;
    /** Number of DFS nodes visited between memory usage checks. */
    private static final int MEM_CHECK_INTERVAL = 1000;

    /** Reusable buffer used to store integers parsed from a line of the input file while tokenizing. */
    private int[] tokenBuffer = new int[64];

    /** Reusable buffer used to build the textual representation of a pattern before output. */
    private final StringBuilder patternBuilder = new StringBuilder(64);

    /** Flat buffer holding the sequence currently being built by the DFS (items with -1 as itemset separators). */
    private int[] patternBuffer = new int[64];
    /** Number of valid tokens currently stored in patternBuffer. */
    private int patternLength = 0;

    /** Pool of reusable buffers for S-step candidates, indexed by DFS depth. */
    private final List<DfsBuffer> sBufferPool = new ArrayList<>();
    /** Pool of reusable buffers for I-step candidates, indexed by DFS depth. */
    private final List<DfsBuffer> iBufferPool = new ArrayList<>();
    /** Number of frequent items (size of the dense internal item-id space). */
    private int frequentItemCount = 0;

    /**
     * Reusable set tracking, within a single call to {@link #processTransactionForCMAP}, which
     * items have already been fully processed as a "before" item; hoisted to an instance field
     * and {@code clear()}-ed per transaction instead of being reallocated on every call.
     */
    private final Set<Integer> cmapAlreadyProcessed = new HashSet<>();
    /**
     * Reusable map tracking, within a single call to {@link #processTransactionForCMAP}, which
     * "same itemset" pairs have already been recorded for each item; hoisted to an instance
     * field and {@code clear()}-ed per transaction instead of being reallocated on every call.
     */
    private final Map<Integer, Set<Integer>> cmapEqualProcessed = new HashMap<>();
    /**
     * Reusable set tracking, within the inner loop of {@link #processTransactionForCMAP}, which
     * "later itemset" items have already been recorded for the item currently being processed;
     * hoisted to an instance field and {@code clear()}-ed per outer-loop iteration instead of
     * being reallocated.
     */
    private final Set<Integer> cmapAlreadyProcessedB = new HashSet<>();

    /** Reusable per-depth scratch storage for candidate items and their bitmaps during the DFS. */
    private static class DfsBuffer {
        /** Candidate item ids held in this buffer. */
        int[] items;
        /** Bitmaps corresponding to each candidate item, reused across DFS visits to this depth. */
        BitmapSPAM[] bitmaps;

        /**
         * Creates a buffer able to hold the given number of candidates.
         *
         * @param capacity the maximum number of candidates this buffer can hold
         */
        DfsBuffer(int capacity) {
            items   = new int[capacity];
            bitmaps = new BitmapSPAM[capacity];
        }

        /**
         * Grows this buffer in place to at least the given capacity, preserving the already
         * pooled bitmap objects so they do not need to be reallocated.
         *
         * @param capacity the new minimum capacity
         */
        void growTo(int capacity) {
            items   = new int[capacity];                 // contents are fully rewritten before being read
            bitmaps = Arrays.copyOf(bitmaps, capacity);   // preserve already-pooled bitmap objects
        }
    }

    /** Creates a new instance of the CM-SPAM2 algorithm. */
    public AlgoCMSPAM() { }

    /**
     * Runs the CM-SPAM algorithm on an input sequence database and writes the frequent sequential patterns to a file.
     *
     * @param input the path of the input file
     * @param outputFilePath the path of the output file
     * @param minsupRel the minimum support threshold, as a relative value between 0 and 1
     * @throws IOException if an error occurs while reading the input or writing the output
     */
    public void runAlgorithm(String input, String outputFilePath, double minsupRel)
            throws IOException
    {
        if (input == null || outputFilePath == null) {
            throw new IllegalArgumentException("The input and output file paths must not be null.");
        }
        if (minsupRel < 0d || minsupRel > 1d) {
            throw new IllegalArgumentException("The relative minimum support must be between 0 and 1.");
        }

        writer = new BufferedWriter(new FileWriter(outputFilePath), 1 << 16);
		writer.write("@FILETYPE=\"Frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();
		
        patternCount    = 0;
        memCheckCounter = 0;
        outputBuffer.setLength(0);
        MemoryLogger.getInstance().reset();

        startTime = System.currentTimeMillis();
        try {
            spam(input, minsupRel);
        } finally {
            // flush any remaining buffered output before closing
            if (outputBuffer.length() > 0) {
                writer.write(outputBuffer.toString());
                outputBuffer.setLength(0);
            }
            writer.close();
            clearBufferPools();
        }
        endTime = System.currentTimeMillis();
    }

    /** Clears all pooled DFS buffers so they can be garbage collected. */
    private void clearBufferPools() {
        sBufferPool.clear();
        iBufferPool.clear();
    }

    /**
     * Checks whether a line of the input file should be ignored (empty line or metadata/comment line).
     *
     * @param line the line to check
     * @return true if the line should be skipped
     */
    private static boolean isSkippableLine(String line) {
        return line.isEmpty()
                || line.charAt(0) == '#'
                || line.charAt(0) == '%'
                || line.charAt(0) == '@';
    }

    /**
     * Reads the input file once and tokenizes every non-skippable line exactly once, returning
     * the parsed integers for each line as its own {@code int[]} row (trimmed to the exact
     * number of tokens found). This in-memory, pre-tokenized representation is then reused for
     * all three logical passes needed by CM-SPAM (support counting, vertical database
     * construction, and CMAP construction), so that the file is only read from disk once
     * <em>and</em> each line is only parsed from text into integers once, instead of being
     * re-tokenized on every pass. The caller releases the returned array (sets it to null) as
     * soon as it is no longer needed, before the depth-first search starts.
     *
     * @param input the path of the input file
     * @return one {@code int[]} of parsed tokens per non-skippable line, in file order
     * @throws IOException if an error occurs while reading the input file
     */
    private int[][] loadTokenizedLines(String input) throws IOException {
        List<int[]> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(new File(input))),
                INPUT_BUFFER_SIZE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isSkippableLine(line)) {
                    continue;
                }
                int tokenCount = tokenizeLine(line, tokenBuffer);
                // tokenizeLine may have grown and reassigned the tokenBuffer field; read the
                // current field value and trim it to the exact token count for permanent storage
                result.add(Arrays.copyOf(tokenBuffer, tokenCount));
            }
        }
        return result.toArray(new int[0][]);
    }

    /**
     * Splits a line of the input file into integer tokens, growing the buffer if needed. Any
     * whitespace character (not just the plain ASCII space) is treated as a token separator.
     *
     * @param line the line to tokenize
     * @param buffer the buffer to store the parsed integers into (may be replaced if too small)
     * @return the number of tokens parsed
     */
    private int tokenizeLine(String line, int[] buffer) {
        int length = line.length();
        int count  = 0;
        int i      = 0;
        while (i < length) {
            // skip whitespace between tokens
            while (i < length && Character.isWhitespace(line.charAt(i))) i++;
            if (i >= length) break;

            // parse an optional negative sign
            boolean neg = false;
            if (line.charAt(i) == '-') { neg = true; i++; }

            // parse the digits into an integer value
            int val = 0;
            while (i < length && !Character.isWhitespace(line.charAt(i))) {
                val = val * 10 + (line.charAt(i) - '0');
                i++;
            }
            // grow the buffer if needed, then store the parsed token
            if (count == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            buffer[count++] = neg ? -val : val;
        }
        tokenBuffer = buffer;
        return count;
    }

    /**
     * Ensures the pattern buffer can hold at least the given number of tokens, growing it if necessary.
     *
     * @param minCapacity the minimum number of tokens the buffer must be able to hold
     */
    private void ensurePatternCapacity(int minCapacity) {
        if (minCapacity > patternBuffer.length) {
            patternBuffer = Arrays.copyOf(patternBuffer, Math.max(minCapacity, patternBuffer.length * 2));
        }
    }

    /**
     * Runs the main phases of CM-SPAM: counting item supports, building the vertical database,
     * building the CMAP cooccurrence maps, and performing the depth-first search for frequent
     * sequential patterns. The input file is read and tokenized into memory exactly once (see
     * {@link #loadTokenizedLines}), and that pre-parsed representation is reused for the
     * support-counting pass, the vertical-database-construction pass, and the CMAP-construction
     * pass, before being released just prior to the depth-first search.
     *
     * @param input the path of the input file
     * @param minsupRel the relative minimum support threshold
     * @throws IOException if an error occurs while reading the input file
     */
    private void spam(String input, double minsupRel) throws IOException {

        int[][] tokenizedLines = loadTokenizedLines(input);

        // Step 0: count item frequencies and detect singleton-itemset mode
        ArrayListInt sequencesSizeList = new ArrayListInt();

        int initialCapacity = 1024;
        int[] itemSupport = new int[initialCapacity];
        int[] itemLastSid = new int[initialCapacity];
        Arrays.fill(itemLastSid, -1);
        maxItemId = 0;

        int lastBitIndex = 0;
        boolean sawMultiItemItemset = false;

        {
            int bitIndex = 0;
            int sid      = 0;

            for (int[] tokens : tokenizedLines) {
                sequencesSizeList.add(bitIndex);

                int tokenCount = tokens.length;
                int itemsInCurrentItemset = 0; // reset per line (each line = one full sequence)

                for (int t = 0; t < tokenCount; t++) {
                    int token = tokens[t];
                    if (token == -1) {
                        bitIndex++;
                        itemsInCurrentItemset = 0; // reset at itemset boundary
                    } else if (token == -2) {
                        sid++;
                        bitIndex++; // FIX: end of sequence must also advance bit index to prevent overlap
                    } else {
                        if (!sawMultiItemItemset) {
                            itemsInCurrentItemset++;
                            if (itemsInCurrentItemset > 1) {
                                sawMultiItemItemset = true;
                            }
                        }

                        // grow support-tracking arrays if the item id exceeds current capacity
                        if (token >= itemSupport.length) {
                            int oldLen = itemSupport.length;
                            int newCap = Math.max(token + 1, oldLen * 2);
                            itemSupport = Arrays.copyOf(itemSupport, newCap);
                            itemLastSid = Arrays.copyOf(itemLastSid, newCap);
                            Arrays.fill(itemLastSid, oldLen, newCap, -1);
                        }
                        if (token > maxItemId) maxItemId = token;

                        // count support once per sequence, not per occurrence
                        if (itemLastSid[token] != sid) {
                            itemSupport[token]++;
                            itemLastSid[token] = sid;
                        }
                    }
                }
            }
            lastBitIndex = bitIndex - 1;
        }

        onlySingletonItemsets = !sawMultiItemItemset;

        sequencesSize = new int[sequencesSizeList.size()];
        for (int idx = 0; idx < sequencesSize.length; idx++) {
            sequencesSize[idx] = sequencesSizeList.get(idx);
        }
        sequencesSizeList = null;

        int numSequences = sequencesSize.length;

		// BUGFIX START 11-2026
		// Math.ceil() alone is wrong: for many relative thresholds minsupRel *
		// numSequences
		// evaluates to just above an integer (e.g. 0.56 * 25 == 14.000000000000002), so
		// ceil() rounds the absolute minimum support up by one and every pattern whose
		// support equals the requested threshold is silently dropped.
		// Subtracting a small epsilon makes the conversion exact.
		minsup = Math.max(1, (int) Math.ceil(minsupRel * numSequences - 1e-7));
		// BUGFIX END
        if (minsup == 0) minsup = 1;

        // precompute sequenceEndBit[] and build the SID resolver
        sequenceEndBit = BitmapSPAM.buildSequenceEndBit(sequencesSize, lastBitIndex, numSequences);

        sidResolver = new BitmapSPAM.SidResolver(sequencesSize, sequenceEndBit);
        sidScratchBuffer = new int[Math.max(numSequences, 1)];
        firstBitScratchBuffer = new int[Math.max(numSequences, 1)];

        // build contiguous item-id remap
        ArrayListInt frequentOriginalIdsList = new ArrayListInt();
        for (int origId = 0; origId <= maxItemId; origId++) {
            int cnt = itemSupport[origId];
            if (cnt >= minsup) {
                // frequent item: assign it the next dense internal id
                int newId = frequentOriginalIdsList.size();
                frequentOriginalIdsList.add(origId);
                itemSupport[origId] = newId;
            } else {
                itemSupport[origId] = -1;
            }
        }
        int[] remap = itemSupport;
        itemSupport = null;
        itemLastSid  = null;

        frequentItemCount = frequentOriginalIdsList.size();
        newIdToOriginal = new int[frequentItemCount];
        for (int i = 0; i < frequentItemCount; i++) {
            newIdToOriginal[i] = frequentOriginalIdsList.get(i);
        }
        frequentOriginalIdsList = null;

        // resolve the "must appear" items (if specified) into a boolean bitmask indexed by
        // internal dense item ids, together with a running "required count" of distinct valid
        // items and a "possible" flag; this mirrors the incremental mask + counter technique used
        // elsewhere for such constraints, allowing membership to be tested with a plain array
        // lookup and the constraint itself to be checked with an O(1) comparison rather than
        // rescanning the whole pattern buffer with a HashSet on every save
        mustAppearMask            = null;
        mustAppearPossible        = true;
        mustAppearRequiredCount   = 0;
        mustAppearOccurrenceCount = null;
        if (mustAppearItems != null) {
            mustAppearMask = new boolean[frequentItemCount];
            for (int orig : mustAppearItems) {
                int newId = (orig >= 0 && orig <= maxItemId) ? remap[orig] : -1;
                if (newId < 0) {
                    // a required item is infrequent (or unknown): the constraint can never be satisfied
                    mustAppearPossible = false;
                } else if (!mustAppearMask[newId]) {
                    // count each distinct required item only once, so duplicate entries in
                    // mustAppearItems do not inflate the required count
                    mustAppearMask[newId] = true;
                    mustAppearRequiredCount++;
                }
            }

            if (!mustAppearPossible) {
                // at least one required item is infrequent (or unknown): no pattern can ever
                // satisfy the constraint, so building the vertical database, the CMAP maps, and
                // running the depth-first search would all be wasted work
                return;
            }

            mustAppearOccurrenceCount = new int[frequentItemCount];
        }

        // Step 1: build the vertical database (reusing the pre-tokenized lines)
        vdbArray = new BitmapSPAM[frequentItemCount];

        {
            int sid2 = 0;

            for (int[] tokens : tokenizedLines) {
                int tid2 = 0;
                int tokenCount = tokens.length;
                for (int t = 0; t < tokenCount; t++) {
                    int token = tokens[t];
                    if (token == -1) {
                        tid2++;
                    } else if (token == -2) {
                        sid2++;
                        tid2 = 0;
                    } else {
                        int newId = remap[token];
                        if (newId >= 0) {
                            // register occurrence in the item's bitmap, creating it lazily
                            BitmapSPAM bm = vdbArray[newId];
                            if (bm == null) {
                                bm = new BitmapSPAM();
                                vdbArray[newId] = bm;
                            }
                            bm.registerBit(sid2, tid2, sequencesSize);
                        }
                    }
                }
            }
        }

        // Step 2: save frequent 1-patterns
        if (minimumPatternLength <= 1 && maximumPatternLength >= 1) {
            for (int newId = 0; newId < frequentItemCount; newId++) {
                savePattern(newId, vdbArray[newId]);
            }
        }

        if (maximumPatternLength == 1) return;

        // Step 3.1: build the CMAP cooccurrence maps used to prune candidates before extension
        // (reusing the same pre-tokenized lines)
        buildCoocMaps(tokenizedLines, remap);
        remap = null;

        // the pre-tokenized lines are no longer needed once both the vertical database and the
        // CMAP maps have been built; release the reference so the (potentially large) in-memory
        // copy of the file can be garbage-collected before the depth-first search starts
        tokenizedLines = null;

        int[] frequentItemsArr = new int[frequentItemCount];
        for (int i = 0; i < frequentItemCount; i++) {
            frequentItemsArr[i] = i;
        }

        // Step 3: depth-first search for longer patterns
        // (sBufferPool/iBufferPool are guaranteed empty at this point: clearBufferPools() is
        // invoked from the finally block of runAlgorithm after every previous call to spam(),
        // and spam() is only ever called from there, so no explicit clear is needed here.)
        for (int idx = 0; idx < frequentItemCount; idx++) {
            BitmapSPAM bm = vdbArray[idx];

            // start a new pattern buffer containing a single itemset with this item
            patternLength = 0;
            ensurePatternCapacity(1);
            patternBuffer[patternLength++] = idx;

            // initialize the running "must appear" match count for this new pattern prefix
            boolean idxRequired = mustAppearMask != null && mustAppearMask[idx];
            if (idxRequired) {
                mustAppearOccurrenceCount[idx] = 1;
                mustAppearMatchedCount = 1;
            } else {
                mustAppearMatchedCount = 0;
            }

            dfsPruning(bm,
                    frequentItemsArr, frequentItemCount,
                    frequentItemsArr, frequentItemCount,
                    idx, 2, 0);

            if (idxRequired) {
                mustAppearOccurrenceCount[idx] = 0;
            }
        }
    }

    /**
     * Builds the CMAP cooccurrence maps by scanning every pre-tokenized transaction, keeping
     * only frequent items remapped to their internal ids, and recording for each item which
     * items can appear later in the sequence and which items can appear in the same itemset.
     * Every transaction is scanned unconditionally, exactly like the vertical database built in
     * {@link #spam}, so that the recorded co-occurrence supports are always sound upper bounds
     * for the pruning performed in {@link #dfsPruning}; skipping transactions that do not
     * contain a "must appear" item would under-count co-occurrences relative to the (unfiltered)
     * vertical database and could cause valid patterns to be wrongly pruned.
     *
     * @param tokenizedLines the pre-tokenized, non-skippable lines of the input file
     * @param remap maps original item ids to internal ids, or -1 if the item is infrequent
     */
    @SuppressWarnings("unchecked")
    private void buildCoocMaps(int[][] tokenizedLines, int[] remap) {
        coocMapAfter  = new HashMap[frequentItemCount];
        coocMapEquals = new HashMap[frequentItemCount];

        int[] compact = new int[64];

        for (int[] tokens : tokenizedLines) {
            int tokenCount = tokens.length;
            int compactLen = 0;

            for (int t = 0; t < tokenCount; t++) {
                int token = tokens[t];
                if (token == -2) {
                    // end of a sequence: process the transaction accumulated so far
                    if (compactLen > 0) {
                        processTransactionForCMAP(compact, compactLen);
                    }
                    compactLen = 0;
                } else if (token == -1) {
                    // keep the itemset boundary in the compact representation
                    if (compactLen == compact.length) {
                        compact = Arrays.copyOf(compact, compact.length * 2);
                    }
                    compact[compactLen++] = -1;
                } else {
                    // keep only frequent items, remapped to their internal id
                    int newId = remap[token];
                    if (newId >= 0) {
                        if (compactLen == compact.length) {
                            compact = Arrays.copyOf(compact, compact.length * 2);
                        }
                        compact[compactLen++] = newId;
                    }
                }
            }
            if (compactLen > 0) {
                processTransactionForCMAP(compact, compactLen);
            }
        }
    }

    /**
     * Updates the CMAP cooccurrence maps with the pairs of items found in a single transaction,
     * counting each pair at most once per transaction. Uses reusable instance-level scratch
     * collections ({@link #cmapAlreadyProcessed}, {@link #cmapEqualProcessed},
     * {@link #cmapAlreadyProcessedB}), cleared at the start of the relevant loop, instead of
     * allocating fresh collections on every call.
     *
     * @param transaction the compact transaction (internal item ids, -1 marks itemset boundaries)
     * @param length the number of valid entries in transaction
     */
    private void processTransactionForCMAP(int[] transaction, int length) {
        cmapAlreadyProcessed.clear();
        cmapEqualProcessed.clear();

        loopI:
        for (int i = 0; i < length; i++) {
            int itemI = transaction[i];
            if (itemI == -1) {
                continue; // itemset boundary, nothing to record
            }

            Set<Integer> equalSet = cmapEqualProcessed.get(itemI);
            if (equalSet == null) {
                equalSet = new HashSet<>();
                cmapEqualProcessed.put(itemI, equalSet);
            }

            cmapAlreadyProcessedB.clear();
            boolean sameItemset = true;

            for (int j = i + 1; j < length; j++) {
                int itemJ = transaction[j];
                if (itemJ == -1) {
                    sameItemset = false;
                    continue;
                }

                if (sameItemset) {
                    // itemJ appears in the same itemset as itemI
                    if (!equalSet.contains(itemJ)) {
                        addCooccurrence(coocMapEquals, itemI, itemJ);
                        equalSet.add(itemJ);
                    }
                } else if (!cmapAlreadyProcessedB.contains(itemJ)) {
                    // itemJ appears in a later itemset than itemI
                    if (cmapAlreadyProcessed.contains(itemI)) {
                        continue loopI;
                    }
                    addCooccurrence(coocMapAfter, itemI, itemJ);
                    cmapAlreadyProcessedB.add(itemJ);
                }
            }
            cmapAlreadyProcessed.add(itemI);
        }
    }

    /**
     * Increments by one the support count recorded for the pair (itemI, itemJ) in the given map.
     *
     * @param map the cooccurrence map to update (indexed by internal item id)
     * @param itemI the source item
     * @param itemJ the cooccurring item
     */
    private void addCooccurrence(Map<Integer, Integer>[] map, int itemI, int itemJ) {
        Map<Integer, Integer> m = map[itemI];
        if (m == null) {
            m = new HashMap<>();
            map[itemI] = m;
        }
        m.merge(itemJ, 1, Integer::sum);
    }

    /**
     * Retrieves the pooled DFS buffer for the given recursion depth, growing it on demand if the
     * requested capacity exceeds what is currently allocated.
     *
     * @param pool the buffer pool to use (S-step or I-step pool)
     * @param depth the current DFS recursion depth
     * @param capacity the minimum capacity required for this node
     * @return a buffer with at least the requested capacity
     */
    private DfsBuffer getBuffer(List<DfsBuffer> pool, int depth, int capacity) {
        if (capacity < 1) capacity = 1;
        while (pool.size() <= depth) {
            pool.add(new DfsBuffer(capacity));
        }
        DfsBuffer buf = pool.get(depth);
        if (buf.items.length < capacity) {
            // grow in place so already-pooled bitmap objects are preserved
            buf.growTo(capacity);
        }
        return buf;
    }

    /**
     * Finds the index of the first entry in {@code arr[0..count)} that is strictly greater than
     * {@code value}, using binary search since the array is always kept sorted in ascending order.
     *
     * @param arr the sorted array to search
     * @param count the number of valid entries in arr
     * @param value the value to compare against
     * @return the index of the first entry strictly greater than value, or count if none is found
     */
    private static int upperBound(int[] arr, int count, int value) {
        int lo = 0, hi = count;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] <= value) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /**
     * Recursively extends the pattern currently held in {@code patternBuffer} with S-steps (new
     * itemset) and I-steps (item added to the last itemset), using the CMAP cooccurrence maps to
     * prune candidates before any bitmap intersection is computed, saving each frequent pattern
     * found.
     *
     * @param prefixBitmap the bitmap representation of the current pattern
     * @param sn candidate items usable for S-steps
     * @param snCount number of valid entries in sn
     * @param in candidate items usable for I-steps
     * @param inCount number of valid entries in in
     * @param hasToBeGreaterThanForIStep the last item appended to the pattern, used for the I-step ordering constraint and as the CMAP lookup key
     * @param m the length of the pattern that would result from the extension
     * @param depth the current DFS recursion depth, used to select the pooled buffers
     * @throws IOException if an error occurs while writing a pattern to the output
     */
    private void dfsPruning(BitmapSPAM prefixBitmap,
                             int[]  sn, int snCount,
                             int[]  in, int inCount,
                             int    hasToBeGreaterThanForIStep,
                             int    m,
                             int    depth)
            throws IOException
    {
        // CMAP for the S-step: items that can possibly follow the last appended item
        Map<Integer, Integer> mapAfter = coocMapAfter[hasToBeGreaterThanForIStep];

        DfsBuffer sBuf   = getBuffer(sBufferPool, depth, snCount);
        int       sCount = 0;

        // precompute occupied SIDs/first-bit once per node when there is no gap constraint
        int occCount = (maxGap == Integer.MAX_VALUE && snCount > 0 && mapAfter != null)
                ? prefixBitmap.collectOccupiedSidsAndFirstBit(
                        sidScratchBuffer, firstBitScratchBuffer, sidResolver)
                : 0;

        if (mapAfter != null) {
            for (int idx = 0; idx < snCount; idx++) {
                int item = sn[idx];

                // CMAP pruning: skip items that cannot reach minsup as a follower of the last item
                Integer support = mapAfter.get(item);
                if (support == null || support < minsup) {
                    continue;
                }

                BitmapSPAM itemBitmap = vdbArray[item];

                BitmapSPAM out = sBuf.bitmaps[sCount];
                if (out == null) {
                    out = new BitmapSPAM();
                    sBuf.bitmaps[sCount] = out;
                }
                out.resetForReuse(itemBitmap.wordCount);

                prefixBitmap.createNewBitmapSStep(
                        out, itemBitmap,
                        maxGap, sidResolver, sequenceEndBit,
                        sidScratchBuffer, firstBitScratchBuffer, occCount);

                // keep the candidate only if it meets minimum support
                if (out.getSupportWithoutGapTotal() >= minsup) {
                    sBuf.items[sCount] = item;
                    sCount++;
                }
            }
        }

        for (int k = 0; k < sCount; k++) {
            int    item      = sBuf.items[k];
            BitmapSPAM newBitmap = sBuf.bitmaps[k];

            if (newBitmap.getSupport() >= minsup) {
                // append a new itemset (separator + item) to the pattern buffer
                int savedLen = patternLength;
                ensurePatternCapacity(patternLength + 2);
                if (patternLength > 0) {
                    patternBuffer[patternLength++] = -1;
                }
                patternBuffer[patternLength++] = item;

                // update the running "must appear" match count for this push; only the 0->1
                // transition of this item's occurrence count affects the distinct-item count,
                // so reusing the same required item later in the pattern does not inflate it
                boolean isRequired = mustAppearMask != null && mustAppearMask[item];
                if (isRequired && ++mustAppearOccurrenceCount[item] == 1) {
                    mustAppearMatchedCount++;
                }

                if (m >= minimumPatternLength) {
                    savePattern(newBitmap);
                }
                if (maximumPatternLength > m) {
                    dfsPruning(newBitmap,
                            sBuf.items, sCount,
                            sBuf.items, sCount,
                            item, m + 1, depth + 1);
                }

                // pop: undo the occurrence-count update, then restore the buffer, for the next sibling
                if (isRequired && --mustAppearOccurrenceCount[item] == 0) {
                    mustAppearMatchedCount--;
                }
                patternLength = savedLen;
            }
        }

        // I-steps: extend the pattern by adding an item to the last itemset
        if (!onlySingletonItemsets) {
            // CMAP for the I-step: items that can possibly appear in the same itemset as the last appended item
            Map<Integer, Integer> mapEquals = coocMapEquals[hasToBeGreaterThanForIStep];

            if (mapEquals != null) {
                DfsBuffer iBuf   = getBuffer(iBufferPool, depth, inCount);
                int       iCount = 0;

                // only consider items greater than the last one added, to avoid duplicate itemsets
                int startIdx = upperBound(in, inCount, hasToBeGreaterThanForIStep);

                for (int idx = startIdx; idx < inCount; idx++) {
                    int item = in[idx];

                    // CMAP pruning: skip items that cannot reach minsup alongside the last item
                    Integer support = mapEquals.get(item);
                    if (support == null || support < minsup) {
                        continue;
                    }

                    BitmapSPAM itemBitmap = vdbArray[item];

                    BitmapSPAM out = iBuf.bitmaps[iCount];
                    if (out == null) {
                        out = new BitmapSPAM();
                        iBuf.bitmaps[iCount] = out;
                    }
                    out.resetForReuse(Math.min(prefixBitmap.wordCount, itemBitmap.wordCount));

                    prefixBitmap.createNewBitmapIStep(out, itemBitmap, sidResolver);

                    if (out.getSupport() >= minsup) {
                        iBuf.items[iCount] = item;
                        iCount++;
                    }
                }

                for (int k = 0; k < iCount; k++) {
                    int    item      = iBuf.items[k];
                    BitmapSPAM newBitmap = iBuf.bitmaps[k];

                    // append the item directly to the last itemset in the pattern buffer
                    int savedLen = patternLength;
                    ensurePatternCapacity(patternLength + 1);
                    patternBuffer[patternLength++] = item;

                    boolean isRequired = mustAppearMask != null && mustAppearMask[item];
                    if (isRequired && ++mustAppearOccurrenceCount[item] == 1) {
                        mustAppearMatchedCount++;
                    }

                    if (m >= minimumPatternLength) {
                        savePattern(newBitmap);
                    }
                    if (maximumPatternLength > m) {
                        dfsPruning(newBitmap,
                                sBuf.items, sCount,
                                iBuf.items, iCount,
                                item, m + 1, depth + 1);
                    }

                    if (isRequired && --mustAppearOccurrenceCount[item] == 0) {
                        mustAppearMatchedCount--;
                    }
                    patternLength = savedLen;
                }
            }
        }

        // periodically check memory usage without doing it on every single node
        if (++memCheckCounter >= MEM_CHECK_INTERVAL) {
            memCheckCounter = 0;
            MemoryLogger.getInstance().checkMemory();
        }
    }

    /**
     * Flushes the in-memory output buffer to the writer once it exceeds the flush threshold.
     *
     * @throws IOException if an error occurs while writing to the output file
     */
    private void maybeFlushOutputBuffer() throws IOException {
        if (outputBuffer.length() >= OUTPUT_BUFFER_FLUSH_THRESHOLD) {
            writer.write(outputBuffer.toString());
            outputBuffer.setLength(0);
        }
    }

    /**
     * Saves a frequent single-item pattern to the output buffer.
     *
     * @param item the internal id of the frequent item
     * @param bitmap the bitmap holding the pattern's support information
     * @throws IOException if an error occurs while writing to the output
     */
    private void savePattern(int item, BitmapSPAM bitmap) throws IOException {
        // First, we check if the pattern contains the desired items (optional).
        // We only do that if the user has specified some items that must appear in patterns.
        // A single-item pattern can only satisfy the constraint if exactly one distinct item is
        // required and it is this very item; this is an O(1) check against the precomputed mask.
        // (mustAppearPossible is guaranteed true here: spam() returns before producing any
        // pattern whenever it is false, so it does not need to be re-checked.)
        if (mustAppearMask != null
                && (mustAppearRequiredCount != 1 || !mustAppearMask[item])) {
            return;
        }

        patternCount++;
        patternBuilder.setLength(0);
        patternBuilder.append(newIdToOriginal[item])
                      .append(" -1 #SUP: ")
                      .append(bitmap.getSupport());

        if (outputSequenceIdentifiers) {
            patternBuilder.append(" #SID: ")
                          .append(bitmap.getSIDs(sidResolver));
        }

        outputBuffer.append(patternBuilder).append('\n');
        maybeFlushOutputBuffer();
    }

    /**
     * Saves the pattern currently held in {@code patternBuffer} (a multi-itemset pattern) to the output buffer.
     *
     * @param bitmap the bitmap holding the pattern's support information
     * @throws IOException if an error occurs while writing to the output
     */
    private void savePattern(BitmapSPAM bitmap) throws IOException {
        // First, we check if the pattern contains the desired items (optional).
        // We only do that if the user has specified some items that must appear in patterns.
        // mustAppearMatchedCount is maintained incrementally as items are pushed onto / popped
        // off patternBuffer during the DFS, so this check is an O(1) comparison rather than a
        // rescan of the whole pattern buffer.
        // (mustAppearPossible is guaranteed true here: spam() returns before producing any
        // pattern whenever it is false, so it does not need to be re-checked.)
        if (mustAppearMask != null
                && mustAppearMatchedCount != mustAppearRequiredCount) {
            return;
        }

        patternCount++;
        patternBuilder.setLength(0);

        // walk the flat buffer, translating items and turning -1 separators into itemset closers
        for (int i = 0; i < patternLength; i++) {
            int token = patternBuffer[i];
            if (token == -1) {
                patternBuilder.append("-1 ");
            } else {
                patternBuilder.append(newIdToOriginal[token]).append(' ');
            }
        }
        patternBuilder.append("-1 #SUP: ").append(bitmap.getSupport());

        if (outputSequenceIdentifiers) {
            patternBuilder.append(" #SID: ")
                          .append(bitmap.getSIDs(sidResolver));
        }

        outputBuffer.append(patternBuilder).append('\n');
        maybeFlushOutputBuffer();
    }

    /** Prints statistics about the last execution of the algorithm to standard output. */
    public void printStatistics() {
        System.out.println(
            "=============  CM-SPAM2 v2.67 - STATISTICS =============\n"
          + " Total time ~ " + (endTime - startTime) + " ms\n"
          + " Frequent sequences count : " + patternCount + "\n"
          + " Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory() + "\n"
          + " minsup " + minsup + "\n"
          + " maxItemId (original ID space) " + maxItemId
                          + " | frequentItemCount (dense vdbArray size) " + frequentItemCount
                          + " | numSequences " + sequencesSize.length + "\n"
          + " onlySingletonItemsets (I-step skipped entirely) : " + onlySingletonItemsets + "\n"
          + "===================================================");
    }

    /** @return the maximum allowed pattern length */
    public int  getMaximumPatternLength()                        { return maximumPatternLength; }
    /** @param maximumPatternLength the maximum allowed pattern length */
    public void setMaximumPatternLength(int maximumPatternLength){ this.maximumPatternLength = maximumPatternLength; }
    /** @param minimumPatternLength the minimum required pattern length */
    public void setMinimumPatternLength(int minimumPatternLength){ this.minimumPatternLength = minimumPatternLength; }
    /** @param maxGap the maximum allowed gap between consecutive elements of a pattern */
    public void setMaxGap(int maxGap)                            { this.maxGap = maxGap; }
    /** @param show whether to include sequence identifiers (SIDs) in the output */
    public void showSequenceIdentifiersInOutput(boolean show)    { this.outputSequenceIdentifiers = show; }

    /**
     * Optional method to specify the items that must appear in patterns found by the algorithm.
     * The given array is defensively copied so that later mutation or reuse of the caller's
     * array by the caller cannot affect this algorithm's behavior.
     *
     * @param mustAppearItems an array of items (original item ids)
     */
    public void setMustAppearItems(int[] mustAppearItems) {
        if (mustAppearItems == null || mustAppearItems.length == 0) {
            this.mustAppearItems = null;
        } else {
            this.mustAppearItems = Arrays.copyOf(mustAppearItems, mustAppearItems.length);
        }
    }
}