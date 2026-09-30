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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

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
/***
 * This is an implementation of the TKS algorithm for top-k sequential pattern
 * mining, rebuilt on top of the optimized bitmap-based vertical database
 * representation (contiguous internal item ids, {@link BitmapSPAM}) used by
 * AlgoSPAM2. Instead of a pure depth-first search, this algorithm performs a
 * best-first search driven by a priority queue of candidates, dynamically
 * raising the minimum support threshold as the top-k set of patterns fills
 * up. <br/>
 * <br/>
 *
 * Copyright (c) 2013 Philippe Fournier-Viger, Antonio Gomariz <br/>
 * @see BitmapSPAM
 * @author Philippe Fournier-Viger & Antonio Gomariz
 */
public class AlgoTKS {

    /** Time at which the algorithm execution started (milliseconds). */
    private long startTime;
    /** Time at which the mining phase (after preprocessing) started (milliseconds). */
    private long startMiningTime;
    /** Time at which the algorithm execution ended (milliseconds). */
    private long endTime;

    /** Minimum support threshold, dynamically raised during execution. */
    private int minsup = 0;
    /** The minsup value recorded right after preprocessing, for statistics. */
    private int minsupAfterPreProcessing = 0;
    /** The number of top patterns to be found. */
    private int k = 0;

    /** Vertical database, indexed by contiguous internal item ids. */
    private BitmapSPAM[] vdbArray = null;
    /** Maps contiguous internal item ids back to their original ids from the input file. */
    private int[] newIdToOriginal = null;
    /** Starting bit index of each sequence in the bitmap representation. */
    private int[] sequencesSize = null;
    /** Ending bit index of each sequence in the bitmap representation. */
    private int[] sequenceEndBit = null;
    /** Resolves a bit position to its owning sequence id (SID). */
    private BitmapSPAM.SidResolver sidResolver = null;
    /** Shared scratch buffer holding the sequence ids occupied by the current prefix bitmap. */
    private int[] sidScratchBuffer = null;
    /** Shared scratch buffer holding the first set bit of the prefix bitmap in each occupied sequence. */
    private int[] firstBitScratchBuffer = null;
    /** Number of frequent items (size of the dense internal item-id space). */
    private int frequentItemCount = 0;

    /** Reusable scratch bitmap used to evaluate S-step candidates before allocating a permanent copy. */
    private BitmapSPAM scratchBitmapS = null;

    /** Reusable scratch bitmap used to evaluate I-step candidates, kept separate from scratchBitmapS. */
    private BitmapSPAM scratchBitmapI = null;

    /** The current top-k patterns found so far, ordered with the lowest support at the head. */
    private PriorityQueue<PatternTKS2> kPatterns;
    /** The candidates waiting to be expanded, ordered with the highest support first. */
    private PriorityQueue<CandidateTKS2> candidates;

    /** Number of patterns in kPatterns sharing each support value, used to evict whole tie-groups efficiently. */
    private TreeMap<Integer, Integer> supportCounts;

    /** The maximum number of candidates held simultaneously during the last execution (for stats). */
    private int maxCandidateCount = 0;
    /** The number of candidates considered during the last execution (for stats). */
    private int candidateExplored = 0;
    /** Flags, indexed by dense item id, of items excluded from future candidate expansion. */
    private boolean[] discardedItems;

    /** Whether items evicted from the top-k set are remembered and skipped during expansion. */
    private final boolean useDiscardedItemsPruningStrategy = true;
    /** Whether branches whose bitmap support already falls below minsup are pruned early. */
    private final boolean usePruneBranchesInsideDFSPruning = true;
    /** Whether the candidate priority queue is periodically rebuilt to discard stale entries. */
    private final boolean rebuildCandidateTreeWhenTooLarge = false;
    /** Number of candidates added since the candidate queue was last rebuilt. */
    private int addedCandidatesSinceLastRebuilt = 0;
    /** Minimum candidate queue size before a rebuild is considered. */
    private static final int MIN_CANDIDATES_COUNT_BEFORE_REBUILD = 1500;
    /** Minimum number of newly added candidates since the last rebuild before rebuilding again. */
    private static final int MIN_ADDED_CANDIDATE_COUNT_SINCE_LAST_REBUILD_BEFORE_REBUILD = 400;
    /** Whether item cooccurrence information is used to prune candidate extensions early. */
    private final boolean useCooccurrenceInformation = true;

    /** For each dense item id, the items that were seen after it in some sequence, with a support count. */
    private Map<Integer, Integer>[] coocMapAfter;
    /** For each dense item id, the items that were seen in the same itemset as it, with a support count. */
    private Map<Integer, Integer>[] coocMapEquals;

    /** Minimum length (number of itemsets) a pattern must have to be output. */
    private int minimumPatternLength = 0;
    /** Maximum length (number of itemsets) a pattern may have. */
    private int maximumPatternLength = 1000;

    /** Original ids of items that must appear in every pattern found (or null if no restriction). Always
     *  kept sorted and free of duplicates (see {@link #setMustAppearItems(int[])}). */
    private int[] mustAppearItems;
    /** Dense internal ids of items that must appear in every pattern found (or null if no restriction). */
    private int[] mustAppearItemsDense;
    /** Reusable buffer used by {@link #save(PatternTKS2)} to check, without allocating a new
     *  collection on every call, whether all of the required dense items are present in a
     *  candidate pattern. Sized once {@link #mustAppearItemsDense} is known. */
    private boolean[] mustAppearFoundBufferDense;

    /** Maximum allowed gap (in itemsets) between consecutive elements of a pattern. */
    private int maxGap = Integer.MAX_VALUE;
    /** Whether to include sequence identifiers (SIDs) in the output. */
    private boolean outputSequenceIdentifiers;

    /** Reusable buffer used to store integers parsed from a line of the input file. */
    private int[] tokenBuffer = new int[64];
    /** Reusable buffer used to build the textual representation of a pattern before output. */
    private final StringBuilder patternBuilder = new StringBuilder(64);

    /** Buffer size used when reading the input file (64 KB). */
    private static final int INPUT_BUFFER_SIZE = 1 << 16;
    /** Initial capacity (in ints) of the growable flat database buffer built during preprocessing. */
    private static final int INITIAL_DATABASE_ARRAY_CAPACITY = 1 << 16;

    /** Creates a new instance of the TKS2 algorithm. */
    public AlgoTKS() { }

    /**
     * Runs the TKS algorithm to find the top-k sequential patterns, and writes them to a file.
     *
     * @param input the path of the input file
     * @param outputFilePath the path of the output file
     * @param k the number of top patterns to be found
     * @return the priority queue containing the top-k patterns found
     * @throws IOException if an error occurs while reading the input or writing the output
     */
    public PriorityQueue<PatternTKS2> runAlgorithm(String input, String outputFilePath, int k) throws IOException {
        MemoryLogger.getInstance().reset();

        // run the mining process
        tks(input, k);
        endTime = System.currentTimeMillis();

        // IMPORTANT: the patterns collected during mining store items using the
        // dense/contiguous internal item ids used internally for performance
        // reasons (see newIdToOriginal). Before the patterns are written to the
        // output file or handed back to the caller, we must translate those
        // internal ids back to the original item ids from the input file.
        // This must be done exactly once.
        translatePatternsToOriginalIds();

        // write the top-k patterns found to the output file
        writeResultTofile(outputFilePath);

        return kPatterns;
    }

    /**
     * Translates the item ids stored in every top-k pattern from the internal dense
     * representation back to their original ids from the input file. This must be
     * called exactly once, after mining has completed and before the patterns are
     * written to the output file or exposed to the caller; calling it more than
     * once would corrupt the item ids.
     */
    private void translatePatternsToOriginalIds() {
        if (kPatterns == null) return;
        for (PatternTKS2 pattern : kPatterns) {
            int[] prefix = pattern.prefix;
            for (int i = 0; i < prefix.length; i++) {
                if (prefix[i] != -1) {
                    prefix[i] = newIdToOriginal[prefix[i]];
                }
            }
        }
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
     * Splits a line of the input file into integer tokens, growing the buffer if needed.
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
            // skip spaces between tokens
            while (i < length && line.charAt(i) == ' ') i++;
            if (i >= length) break;

            // check for a negative sign
            boolean neg = false;
            if (line.charAt(i) == '-') { neg = true; i++; }

            // parse the digits of the current token
            int val = 0;
            while (i < length && line.charAt(i) != ' ') {
                val = val * 10 + (line.charAt(i) - '0');
                i++;
            }
            // grow the buffer if it is full
            if (count == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            buffer[count++] = neg ? -val : val;
        }
        tokenBuffer = buffer;
        return count;
    }

    /**
     * Runs the main phases of TKS: preprocessing the database, computing cooccurrence information,
     * and performing the best-first search for the top-k sequential patterns.
     *
     * <p>Preprocessing keeps the whole (filtered) database in memory as a <b>single flat
     * {@code int[]}</b> (with one small offsets array recording where each kept sequence
     * starts), instead of one small {@code int[]} object per sequence. This array is
     * reused for every preprocessing scan (item support counting, dense id translation
     * performed in place, vertical bitmap construction, and cooccurrence extraction) and
     * is discarded as soon as it is no longer needed, instead of allocating a second full
     * copy of the database just to translate item ids.</p>
     *
     * @param input the path of the input file
     * @param k the number of top patterns to be found
     * @throws IOException if an error occurs while reading the input file
     */
    @SuppressWarnings("unchecked")
    private void tks(String input, int k) throws IOException {
        this.k = k;
        minsup = 1;
        candidateExplored = 0;

        kPatterns  = new PriorityQueue<PatternTKS2>();
        candidates = new PriorityQueue<CandidateTKS2>();
        supportCounts = new TreeMap<Integer, Integer>();

        // Growable buffer holding the tokens of every sequence that will be kept,
        // concatenated together, forming a single flat representation of the whole
        // (filtered) database. This avoids allocating millions of tiny int[] objects
        // (one per sequence) and, later, avoids allocating a second copy of the
        // database just to translate items to dense ids (translation is done in place).
        int[] database = new int[INITIAL_DATABASE_ARRAY_CAPACITY];
        int databaseLength = 0;

        ArrayListInt sequencesSizeList     = new ArrayListInt();
        ArrayListInt sequenceTokenStartList = new ArrayListInt();

        int bitIndex        = 0;
        int maxOriginalItem = 0;

        // reusable buffer used to check, for the current line, which of the (deduplicated)
        // must-appear items have already been found in it
        boolean[] mustAppearFoundBuffer =
                (mustAppearItems != null) ? new boolean[mustAppearItems.length] : null;

        // pass 1: read the file once, keep only sequences containing ALL must-appear items
        // (a sequence missing even a single required item can never yield a valid pattern,
        // since save() requires all of them to be present -- see below)
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(new File(input))),
                INPUT_BUFFER_SIZE)) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (isSkippableLine(line)) continue;

                int tokenCount = tokenizeLine(line, tokenBuffer);
                int itemsetIncrements   = 0;
                int mustAppearFoundCount = 0;
                if (mustAppearFoundBuffer != null) {
                    Arrays.fill(mustAppearFoundBuffer, false);
                }

                for (int t = 0; t < tokenCount; t++) {
                    int token = tokenBuffer[t];
                    if (token == -1) {
                        itemsetIncrements++;
                    } else if (token != -2) {
                        if (token > maxOriginalItem) maxOriginalItem = token;
                        if (mustAppearItems != null) {
                            int pos = Arrays.binarySearch(mustAppearItems, token);
                            if (pos >= 0 && !mustAppearFoundBuffer[pos]) {
                                mustAppearFoundBuffer[pos] = true;
                                mustAppearFoundCount++;
                            }
                        }
                    }
                }

                // keep the sequence only if it contains every required item (AND semantics),
                // consistent with the requirement enforced later in save()
                boolean keepSequence = (mustAppearItems == null)
                        || (mustAppearFoundCount == mustAppearItems.length);

                if (keepSequence) {
                    sequencesSizeList.add(bitIndex);
                    sequenceTokenStartList.add(databaseLength);

                    if (databaseLength + tokenCount > database.length) {
                        int newCapacity = Math.max(database.length * 2, databaseLength + tokenCount);
                        database = Arrays.copyOf(database, newCapacity);
                    }
                    System.arraycopy(tokenBuffer, 0, database, databaseLength, tokenCount);
                    databaseLength += tokenCount;

                    bitIndex += itemsetIncrements;
                }
            }
        }
        // trim the flat database to its exact size, now that no more sequences will be appended
        database = Arrays.copyOf(database, databaseLength);
        int lastBitIndex = bitIndex - 1;

        int numSequences = sequencesSizeList.size();
        sequencesSize = new int[numSequences];
        int[] sequenceTokenStart = new int[numSequences];
        for (int idx = 0; idx < numSequences; idx++) {
            sequencesSize[idx]     = sequencesSizeList.get(idx);
            sequenceTokenStart[idx] = sequenceTokenStartList.get(idx);
        }

        sequenceEndBit        = BitmapSPAM.buildSequenceEndBit(sequencesSize, lastBitIndex, numSequences);
        sidResolver           = new BitmapSPAM.SidResolver(sequencesSize, sequenceEndBit);
        sidScratchBuffer      = new int[Math.max(numSequences, 1)];
        firstBitScratchBuffer = new int[Math.max(numSequences, 1)];

        startTime = System.currentTimeMillis();

        // count each item's support (one occurrence counted at most once per sequence),
        // scanning the flat database bounded by sequenceTokenStart for each sequence
        int[] itemSupport = new int[maxOriginalItem + 1];
        int[] itemLastSid = new int[maxOriginalItem + 1];
        Arrays.fill(itemLastSid, -1);
        for (int sid = 0; sid < numSequences; sid++) {
            int start = sequenceTokenStart[sid];
            int end   = (sid + 1 < numSequences) ? sequenceTokenStart[sid + 1] : databaseLength;
            for (int idx = start; idx < end; idx++) {
                int token = database[idx];
                if (token >= 0) {
                    if (itemLastSid[token] != sid) {
                        itemSupport[token]++;
                        itemLastSid[token] = sid;
                    }
                }
            }
        }

        // build the contiguous dense item-id remap
        ArrayListInt frequentOriginalIdsList = new ArrayListInt();
        int[] remap = new int[maxOriginalItem + 1];
        for (int origId = 0; origId <= maxOriginalItem; origId++) {
            if (itemSupport[origId] >= minsup) {
                remap[origId] = frequentOriginalIdsList.size();
                frequentOriginalIdsList.add(origId);
            } else {
                remap[origId] = -1;
            }
        }
        frequentItemCount = frequentOriginalIdsList.size();
        newIdToOriginal = new int[frequentItemCount];
        for (int i = 0; i < frequentItemCount; i++) {
            newIdToOriginal[i] = frequentOriginalIdsList.get(i);
        }

        buildMustAppearItemsDense(remap, maxOriginalItem);

        // Translate the flat database to dense internal item ids IN PLACE: since the
        // original item ids are no longer needed after this point (the mapping back to
        // original ids is kept separately in newIdToOriginal), there is no need to
        // allocate a second copy of the whole database just for this translation.
        for (int idx = 0; idx < databaseLength; idx++) {
            int token = database[idx];
            if (token >= 0) {
                database[idx] = remap[token];
            }
        }

        // build the vertical bitmap database from the dense-id flat database
        vdbArray = new BitmapSPAM[frequentItemCount];
        for (int sid = 0; sid < numSequences; sid++) {
            int start = sequenceTokenStart[sid];
            int end   = (sid + 1 < numSequences) ? sequenceTokenStart[sid + 1] : databaseLength;
            int tid = 0;
            for (int idx = start; idx < end; idx++) {
                int token = database[idx];
                if (token == -1) {
                    tid++;
                } else if (token != -2) {
                    BitmapSPAM bm = vdbArray[token];
                    if (bm == null) {
                        bm = new BitmapSPAM();
                        vdbArray[token] = bm;
                    }
                    bm.registerBit(sid, tid, sequencesSize);
                }
            }
        }

        // Allocate the two reusable scratch bitmaps now that the word count is known.
        // We size them to the largest bitmap in the vertical database so they are
        // guaranteed to be large enough for any S-step or I-step evaluation.
        int maxWordCount = 0;
        for (int id = 0; id < frequentItemCount; id++) {
            if (vdbArray[id] != null && vdbArray[id].wordCount > maxWordCount) {
                maxWordCount = vdbArray[id].wordCount;
            }
        }
        scratchBitmapS = new BitmapSPAM();
        scratchBitmapS.resetForReuse(maxWordCount);
        scratchBitmapI = new BitmapSPAM();
        scratchBitmapI.resetForReuse(maxWordCount);

        discardedItems = new boolean[frequentItemCount];

        // save frequent single-item patterns, discarding items too infrequent to ever be top-k
        for (int id = 0; id < frequentItemCount; id++) {
            BitmapSPAM bm = vdbArray[id];
            int support = bm.getSupport();
            if (support < minsup) {
                discardedItems[id] = true;
                continue;
            }
            if (minimumPatternLength <= 1 && maximumPatternLength >= 1) {
                PatternTKS2 pattern = new PatternTKS2(new int[] { id }, support);
                if (outputSequenceIdentifiers) pattern.bitmap = bm;
                save(pattern);
            }
        }

        // only search for patterns longer than a single item if allowed
        if (maximumPatternLength > 1) {
            coocMapAfter  = new HashMap[frequentItemCount];
            coocMapEquals = new HashMap[frequentItemCount];
            buildCooccurrenceMaps(database, sequenceTokenStart, numSequences, databaseLength);
            // the flat database is no longer needed: vdbArray and the cooccurrence maps
            // now hold everything the search needs, so we release it here.
            database = null;
            sequenceTokenStart = null;

            int[] allFrequentItems = null;

            // build initial length-1 candidates that will be expanded into length-2 patterns
            for (int id = 0; id < frequentItemCount; id++) {
                if (discardedItems[id]) continue;
                BitmapSPAM bm = vdbArray[id];
                if (bm.getSupport() < minsup) continue;

                candidateExplored++;
                int[] prefix = new int[] { id };

                if (useCooccurrenceInformation) {
                    Map<Integer, Integer> afterMap = coocMapAfter[id];
                    Map<Integer, Integer> equalMap = coocMapEquals[id];
                    if (afterMap != null || equalMap != null) {
                        int[] sn = (afterMap != null) ? toIntArray(afterMap.keySet()) : new int[0];
                        int[] in = (equalMap != null) ? toIntArray(equalMap.keySet()) : new int[0];
                        registerAsCandidate(new CandidateTKS2(prefix, bm, sn, in, id, 1, 1));
                    }
                } else {
                    if (allFrequentItems == null) {
                        allFrequentItems = buildFrequentDenseItemsArray();
                    }
                    registerAsCandidate(new CandidateTKS2(prefix, bm, allFrequentItems,
                            allFrequentItems, id, 1, 1));
                }
            }

            minsupAfterPreProcessing = minsup;
            startMiningTime = System.currentTimeMillis();

            // best-first search: repeatedly expand the most promising candidate
            while (!candidates.isEmpty()) {
                CandidateTKS2 cand = candidates.poll();
                if (cand.bitmap.getSupport() < minsup) break;

                candidateExplored++;
                dfsPruning(cand);

                // periodically rebuild the candidate queue to discard stale entries
                if (rebuildCandidateTreeWhenTooLarge
                        && candidates.size() > MIN_CANDIDATES_COUNT_BEFORE_REBUILD
                        && addedCandidatesSinceLastRebuilt > MIN_ADDED_CANDIDATE_COUNT_SINCE_LAST_REBUILD_BEFORE_REBUILD) {
                    PriorityQueue<CandidateTKS2> temp = new PriorityQueue<CandidateTKS2>();
                    for (CandidateTKS2 candidate : candidates) {
                        if (candidate.bitmap.getSupport() >= minsup) {
                            temp.add(candidate);
                        }
                    }
                    candidates = temp;
                    addedCandidatesSinceLastRebuilt = 0;
                }
            }
        } else {
            // patterns longer than a single item will never be searched: the flat
            // database is not needed anymore either.
            database = null;
            sequenceTokenStart = null;
        }

        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Builds the dense-id equivalent of the user-specified must-appear items,
     * once the item remap is known.
     *
     * @param remap the mapping from original item ids to dense internal ids (or -1 if infrequent)
     * @param maxOriginalItem the highest original item id encountered
     */
    private void buildMustAppearItemsDense(int[] remap, int maxOriginalItem) {
        if (mustAppearItems == null) {
            mustAppearItemsDense = null;
            mustAppearFoundBufferDense = null;
            return;
        }
        List<Integer> denseList = new ArrayList<Integer>();
        for (int origItem : mustAppearItems) {
            if (origItem >= 0 && origItem <= maxOriginalItem && remap[origItem] >= 0) {
                denseList.add(remap[origItem]);
            }
        }
        mustAppearItemsDense = toIntArray(denseList);
        Arrays.sort(mustAppearItemsDense);
        // reusable buffer for save(): avoids allocating a HashSet on every call
        mustAppearFoundBufferDense = new boolean[mustAppearItemsDense.length];
    }

    /**
     * Builds the array of all dense item ids that were not discarded, used only when cooccurrence
     * information is disabled.
     *
     * @return an array of the currently non-discarded dense item ids
     */
    private int[] buildFrequentDenseItemsArray() {
        int count = 0;
        for (int id = 0; id < frequentItemCount; id++) {
            if (!discardedItems[id]) count++;
        }
        int[] result = new int[count];
        int idx = 0;
        for (int id = 0; id < frequentItemCount; id++) {
            if (!discardedItems[id]) result[idx++] = id;
        }
        return result;
    }

    /**
     * Builds cooccurrence maps used to prune candidate extensions early, recording for each item
     * the items that follow it in a later itemset and the items appearing with it in the same itemset.
     *
     * @param database the flat, dense-id database (all kept sequences concatenated)
     * @param sequenceTokenStart the start offset, in {@code database}, of each sequence
     * @param numSequences the number of sequences
     * @param databaseLength the total number of tokens in {@code database}
     */
    private void buildCooccurrenceMaps(int[] database, int[] sequenceTokenStart,
                                        int numSequences, int databaseLength) {
        for (int sid = 0; sid < numSequences; sid++) {
            int start = sequenceTokenStart[sid];
            int end   = (sid + 1 < numSequences) ? sequenceTokenStart[sid + 1] : databaseLength;

            Set<Integer> alreadyProcessed = new HashSet<Integer>();
            Map<Integer, Set<Integer>> equalProcessed = new HashMap<Integer, Set<Integer>>();

            // compare each item of the sequence with the items that follow it
            loopI:
            for (int i = start; i < end; i++) {
                Integer itemI = database[i];

                Set<Integer> equalSet = equalProcessed.get(itemI);
                if (equalSet == null) {
                    equalSet = new HashSet<Integer>();
                    equalProcessed.put(itemI, equalSet);
                }
                if (itemI < 0) continue;
                if (vdbArray[itemI].getSupport() < minsup) continue;

                Set<Integer> alreadyProcessedB = new HashSet<Integer>();
                boolean sameItemset = true;

                for (int j = i + 1; j < end; j++) {
                    Integer itemJ = database[j];
                    if (itemJ < 0) {
                        sameItemset = false;
                        continue;
                    }
                    if (vdbArray[itemJ].getSupport() < minsup) continue;

                    if (sameItemset) {
                        // itemJ is in the same itemset as itemI
                        if (!equalSet.contains(itemJ)) {
                            Map<Integer, Integer> map = coocMapEquals[itemI];
                            if (map == null) {
                                map = new HashMap<Integer, Integer>();
                                coocMapEquals[itemI] = map;
                            }
                            Integer support = map.get(itemJ);
                            map.put(itemJ, support == null ? 1 : support + 1);
                            equalSet.add(itemJ);
                        }
                    } else if (!alreadyProcessedB.contains(itemJ)) {
                        // itemJ is in a later itemset than itemI
                        if (alreadyProcessed.contains(itemI)) continue loopI;
                        Map<Integer, Integer> map = coocMapAfter[itemI];
                        if (map == null) {
                            map = new HashMap<Integer, Integer>();
                            coocMapAfter[itemI] = map;
                        }
                        Integer support = map.get(itemJ);
                        map.put(itemJ, support == null ? 1 : support + 1);
                        alreadyProcessedB.add(itemJ);
                    }
                }
                alreadyProcessed.add(itemI);
            }
        }
    }

    /**
     * Converts a collection of boxed integers into a primitive int array.
     *
     * @param collection the collection to convert
     * @return a new int array containing the same values, in iteration order
     */
    private static int[] toIntArray(Collection<Integer> collection) {
        int[] arr = new int[collection.size()];
        int idx = 0;
        for (Integer v : collection) arr[idx++] = v;
        return arr;
    }

    /**
     * Builds a new prefix by appending a new itemset containing a single item after the given prefix.
     *
     * @param prefix the prefix to extend
     * @param item the item forming the new itemset
     * @return a new array representing the extended prefix
     */
    private static int[] appendNewItemset(int[] prefix, int item) {
        int oldLen = prefix.length;
        if (oldLen == 0) {
            return new int[] { item };
        }
        int[] result = new int[oldLen + 2];
        System.arraycopy(prefix, 0, result, 0, oldLen);
        result[oldLen]     = -1;
        result[oldLen + 1] = item;
        return result;
    }

    /**
     * Builds a new prefix by appending an item to the last itemset of the given prefix.
     *
     * @param prefix the prefix to extend
     * @param item the item to append to its last itemset
     * @return a new array representing the extended prefix
     */
    private static int[] appendToLastItemset(int[] prefix, int item) {
        int[] result = Arrays.copyOf(prefix, prefix.length + 1);
        result[prefix.length] = item;
        return result;
    }

    /**
     * Finds the starting index (in the flat prefix array) of its last itemset.
     *
     * @param prefix the prefix to inspect
     * @return the index of the first item of the last itemset, or 0 if the prefix has a single itemset
     */
    private static int findLastItemsetStart(int[] prefix) {
        for (int idx = prefix.length - 1; idx >= 0; idx--) {
            if (prefix[idx] == -1) return idx + 1;
        }
        return 0;
    }

    /**
     * Tries to insert a candidate pattern into the current top-k set, evicting whole bottom
     * tie-groups (never partially) and raising minsup as the set fills up.
     *
     * @param pattern the candidate pattern to save
     */
    private void save(PatternTKS2 pattern) {
        // reject patterns whose support is strictly below the current threshold; this cheap
        // check is performed first since it is by far the most common reason to reject a
        // pattern, and is much cheaper than the must-appear-items check below.
        if (kPatterns.size() >= k && pattern.support < this.minsup) {
            return;
        }

        // a pattern is only acceptable if it contains ALL of the must-appear items (if any
        // were specified). We use a reusable boolean buffer (indexed by the position returned
        // by binarySearch) instead of allocating a new HashSet on every call, since save() is
        // called very frequently.
        if (mustAppearItemsDense != null) {
            int foundCount = 0;
            Arrays.fill(mustAppearFoundBufferDense, false);
            for (int token : pattern.prefix) {
                if (token == -1) continue;
                int pos = Arrays.binarySearch(mustAppearItemsDense, token);
                if (pos >= 0 && !mustAppearFoundBufferDense[pos]) {
                    mustAppearFoundBufferDense[pos] = true;
                    foundCount++;
                    if (foundCount == mustAppearItemsDense.length) break;
                }
            }
            if (foundCount != mustAppearItemsDense.length) return;
        }

        kPatterns.add(pattern);
        Integer cnt = supportCounts.get(pattern.support);
        supportCounts.put(pattern.support, (cnt == null) ? 1 : cnt + 1);

        if (kPatterns.size() > k) {
            // evict whole bottom tie-groups one at a time, while at least k patterns remain
            while (kPatterns.size() > k) {
                Map.Entry<Integer, Integer> lowestEntry = supportCounts.firstEntry();
                int lowestSupport = lowestEntry.getKey();
                int lowestCount   = lowestEntry.getValue();

                if (kPatterns.size() - lowestCount < k) {
                    break; // evicting this whole group would drop below k
                }

                // remove all patterns forming the lowest-support tie group
                for (int i = 0; i < lowestCount; i++) {
                    PatternTKS2 pat = kPatterns.poll();
                    if (useDiscardedItemsPruningStrategy && pat.prefix.length == 1) {
                        discardedItems[pat.prefix[0]] = true;
                    }
                }
                supportCounts.remove(lowestSupport);
            }

            // raise minsup to the new lowest support present in the queue
            this.minsup = supportCounts.firstKey();
        }
    }

    /**
     * Adds a candidate to the set of candidates awaiting expansion.
     *
     * @param candidate the candidate to register
     */
    private void registerAsCandidate(CandidateTKS2 candidate) {
        candidates.add(candidate);
        addedCandidatesSinceLastRebuilt++;
        if (candidates.size() >= maxCandidateCount) {
            maxCandidateCount = candidates.size();
        }
    }

    /**
     * Expands a candidate with S-steps (new itemset) and I-steps (item appended to the last
     * itemset), saving each resulting pattern that meets the length constraints and registering
     * frequent extensions as new candidates.
     *
     * @param cand the candidate to expand
     * @throws IOException if an error occurs while writing a pattern to the output file
     */
    private void dfsPruning(CandidateTKS2 cand) throws IOException {
        int[]       prefix                   = cand.prefix;
        BitmapSPAM prefixBitmap             = cand.bitmap;
        int[]       sn                       = cand.sn;
        int[]       in                       = cand.in;
        int         hasToBeGreaterThanForIStep = cand.hasToBeGreaterThanForIStep;
        int         m                        = cand.candidateLength;
        int         newCandidatesLength      = m + 1;
        int         lastItemsetStart         = findLastItemsetStart(prefix);

        // use a primitive int list for candidate item ids (avoids Integer boxing on this hot path)
        ArrayListInt      sTempItems   = new ArrayListInt();
        List<BitmapSPAM> sTempBitmaps = new ArrayList<BitmapSPAM>();

        // Precompute occupied SIDs / first-bit once per node when there is no gap constraint.
        int occCount = (maxGap == Integer.MAX_VALUE && sn.length > 0)
                ? prefixBitmap.collectOccupiedSidsAndFirstBit(
                        sidScratchBuffer, firstBitScratchBuffer, sidResolver)
                : 0;

        // S-steps: extend the prefix with a new itemset
        loopi:
        for (int i : sn) {
            if (useDiscardedItemsPruningStrategy && discardedItems[i]) continue;

            if (useCooccurrenceInformation) {
                for (int idx = 0; idx < prefix.length; idx++) {
                    int itemX = prefix[idx];
                    if (itemX == -1) continue;
                    Map<Integer, Integer> mapAfter = coocMapAfter[itemX];
                    if (mapAfter == null) continue loopi;
                    Integer support = mapAfter.get(i);
                    if (support == null || support < minsup) continue loopi;
                }
            }

            BitmapSPAM itemBitmap = vdbArray[i];

            // evaluate into the reusable scratch bitmap: no heap allocation yet
            scratchBitmapS.resetForReuse(itemBitmap.wordCount);
            prefixBitmap.createNewBitmapSStep(scratchBitmapS, itemBitmap, maxGap, sidResolver,
                    sequenceEndBit, sidScratchBuffer, firstBitScratchBuffer, occCount);

            if (scratchBitmapS.getSupportWithoutGapTotal() >= minsup) {
                // survivor: allocate a permanent copy
                BitmapSPAM kept = new BitmapSPAM();
                kept.copyFrom(scratchBitmapS);
                sTempItems.add(i);
                sTempBitmaps.add(kept);
            }
            // rejected candidates: scratchBitmapS is simply overwritten next iteration
        }
        int[] sTempArr = new int[sTempItems.size()];
        for (int idx = 0; idx < sTempArr.length; idx++) sTempArr[idx] = sTempItems.get(idx);

        for (int k2 = 0; k2 < sTempArr.length; k2++) {
            BitmapSPAM newBitmap = sTempBitmaps.get(k2);
            if (usePruneBranchesInsideDFSPruning && newBitmap.getSupport() < minsup) continue;

            int   item      = sTempArr[k2];
            int[] newPrefix = appendNewItemset(prefix, item);

            if (newBitmap.getSupport() >= minsup) {
                if (newCandidatesLength >= minimumPatternLength
                        && newCandidatesLength <= maximumPatternLength) {
                    PatternTKS2 pattern = new PatternTKS2(newPrefix, newBitmap.getSupport());
                    if (outputSequenceIdentifiers) pattern.bitmap = newBitmap;
                    save(pattern);
                }
                if (newCandidatesLength + 1 <= maximumPatternLength) {
                    registerAsCandidate(new CandidateTKS2(newPrefix, newBitmap, sTempArr, sTempArr,
                            item, newCandidatesLength, cand.itemsetCount + 1));
                }
            }
        }

        // I-steps: extend the last itemset of the prefix
        ArrayListInt      iTempItems   = new ArrayListInt();
        List<BitmapSPAM> iTempBitmaps = new ArrayList<BitmapSPAM>();

        loop2:
        for (int i : in) {
            if (i <= hasToBeGreaterThanForIStep) continue;
            if (useDiscardedItemsPruningStrategy && discardedItems[i]) continue;

            if (useCooccurrenceInformation) {
                for (int idx = 0; idx < prefix.length; idx++) {
                    int itemX = prefix[idx];
                    if (itemX == -1) continue;
                    boolean isLastItemset = idx >= lastItemsetStart;
                    Map<Integer, Integer> mapToUse =
                            isLastItemset ? coocMapEquals[itemX] : coocMapAfter[itemX];
                    if (mapToUse == null) continue loop2;
                    Integer support = mapToUse.get(i);
                    if (support == null || support < minsup) continue loop2;
                }
            }

            BitmapSPAM itemBitmap = vdbArray[i];

            // evaluate into the reusable scratch bitmap: no heap allocation yet
            scratchBitmapI.resetForReuse(Math.min(prefixBitmap.wordCount, itemBitmap.wordCount));
            prefixBitmap.createNewBitmapIStep(scratchBitmapI, itemBitmap, sidResolver);

            if (scratchBitmapI.getSupport() >= minsup) {
                // survivor: allocate a permanent copy
                BitmapSPAM kept = new BitmapSPAM();
                kept.copyFrom(scratchBitmapI);
                iTempItems.add(i);
                iTempBitmaps.add(kept);
            }
            // rejected candidates: scratchBitmapI is simply overwritten next iteration
        }
        int[] iTempArr = new int[iTempItems.size()];
        for (int idx = 0; idx < iTempArr.length; idx++) iTempArr[idx] = iTempItems.get(idx);

        for (int k2 = 0; k2 < iTempArr.length; k2++) {
            BitmapSPAM newBitmap = iTempBitmaps.get(k2);
            if (usePruneBranchesInsideDFSPruning && newBitmap.getSupport() < minsup) continue;

            int   item      = iTempArr[k2];
            int[] newPrefix = appendToLastItemset(prefix, item);

            if (newCandidatesLength >= minimumPatternLength
                    && newCandidatesLength <= maximumPatternLength) {
                PatternTKS2 pattern = new PatternTKS2(newPrefix, newBitmap.getSupport());
                if (outputSequenceIdentifiers) pattern.bitmap = newBitmap;
                save(pattern);
            }
            if (newCandidatesLength + 1 <= maximumPatternLength) {
                registerAsCandidate(new CandidateTKS2(newPrefix, newBitmap, sTempArr, iTempArr,
                        item, newCandidatesLength, cand.itemsetCount));
            }
        }

        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Writes the top-k patterns found by the last execution to an output file.
     *
     * <p>Note: this method assumes that {@link #translatePatternsToOriginalIds()}
     * has already been called, so that {@code pattern.prefix} contains the
     * original item ids from the input file (not the internal dense ids).</p>
     *
     * @param path the output file path
     * @throws IOException if an error occurs while writing the file
     */
    public void writeResultTofile(String path) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		writer.write("@FILETYPE=\"Top-k frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();
		
        Iterator<PatternTKS2> iter = kPatterns.iterator();
        while (iter.hasNext()) {
            PatternTKS2 pattern = iter.next();
            patternBuilder.setLength(0);
            // build the textual representation of the pattern's items
            // (items are already expressed using their original ids, see
            // translatePatternsToOriginalIds())
            for (int token : pattern.prefix) {
                if (token == -1) {
                    patternBuilder.append("-1 ");
                } else {
                    patternBuilder.append(token).append(' ');
                }
            }
            // append the support and, if requested, the sequence identifiers
            patternBuilder.append("-1 #SUP: ").append(pattern.support);
            if (outputSequenceIdentifiers && pattern.bitmap != null) {
                patternBuilder.append(" #SID: ").append(pattern.bitmap.getSIDs(sidResolver));
            }
            writer.write(patternBuilder.toString());
            writer.newLine();
        }
        writer.close();
    }

    /** Prints statistics about the last execution of the algorithm to standard output. */
    public void printStatistics() {
        System.out.println(
            "=============  Algorithm TKS2 v2.68 - STATISTICS =============\n"
          + " Minsup after preprocessing : " + minsupAfterPreProcessing + "\n"
          + " Max candidates : " + maxCandidateCount
          + " | Candidates explored : " + candidateExplored + "\n"
          + " Pattern found count : " + kPatterns.size() + "\n"
          + " Time preprocessing : " + (startMiningTime - startTime) + " ms\n"
          + " Total time ~ " + (endTime - startTime) + " ms\n"
          + " Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory() + "\n"
          + " Final minsup value : " + minsup + "\n"
          + "===================================================");
    }

    /**
     * Sets the maximum allowed pattern length.
     *
     * @param maximumPatternLength the maximum allowed pattern length
     */
    public void setMaximumPatternLength(int maximumPatternLength) {
        this.maximumPatternLength = maximumPatternLength;
    }

    /**
     * Sets the minimum required pattern length.
     *
     * @param minimumPatternLength the minimum required pattern length
     */
    public void setMinimumPatternLength(int minimumPatternLength) {
        this.minimumPatternLength = minimumPatternLength;
    }

    /**
     * Sets the items that must appear in every pattern found. The array is copied,
     * sorted and de-duplicated internally (duplicate entries would otherwise make
     * the "all items must be present" check in {@link #save(PatternTKS2)}
     * impossible to satisfy).
     *
     * @param mustAppearItems the original item ids that must appear in every pattern found
     */
    public void setMustAppearItems(int[] mustAppearItems) {
        if (mustAppearItems == null) {
            this.mustAppearItems = null;
            return;
        }
        int[] sorted = mustAppearItems.clone();
        Arrays.sort(sorted);
        int uniqueCount = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[i - 1]) {
                sorted[uniqueCount++] = sorted[i];
            }
        }
        this.mustAppearItems = Arrays.copyOf(sorted, uniqueCount);
    }

    /**
     * Checks if an item is allowed to appear in patterns found by TKS, according to the optional
     * must-appear item restriction.
     *
     * @param item the original item id
     * @return true if the item is allowed (or if no restriction was set)
     */
    public boolean itemMustAppearInPatterns(int item) {
        return mustAppearItems == null || Arrays.binarySearch(mustAppearItems, item) >= 0;
    }

    /**
     * Sets the maximum allowed gap between consecutive elements of a pattern.
     *
     * @param maxGap the maximum allowed gap between consecutive elements of a pattern
     */
    public void setMaxGap(int maxGap) { this.maxGap = maxGap; }

    /**
     * Sets whether sequence identifiers (SIDs) should be included in the output.
     *
     * @param showSequenceIdentifiers whether to include sequence identifiers (SIDs) in the output
     */
    public void showSequenceIdentifiersInOutput(boolean showSequenceIdentifiers) {
        this.outputSequenceIdentifiers = showSequenceIdentifiers;
    }

    /**
     * Represents a candidate pattern awaiting expansion, ordered by decreasing support so
     * that the most promising candidates are expanded first.
     */
    private static final class CandidateTKS2 implements Comparable<CandidateTKS2> {
        /** The prefix (pattern) represented by this candidate. */
        final int[]       prefix;
        /** The bitmap representation of the candidate's occurrences. */
        final BitmapSPAM bitmap;
        /** Items that may extend the candidate with a new itemset (S-step). */
        final int[]       sn;
        /** Items that may extend the candidate's last itemset (I-step). */
        final int[]       in;
        /** The minimum dense item id an I-step extension item must exceed. */
        final int         hasToBeGreaterThanForIStep;
        /** The length (number of items) of the candidate's prefix. */
        final int         candidateLength;
        /** The number of itemsets in the candidate's prefix. */
        final int         itemsetCount;

        /**
         * Creates a new candidate.
         *
         * @param prefix the prefix represented by this candidate
         * @param bitmap the bitmap representation of the candidate's occurrences
         * @param sn items usable for S-step extensions
         * @param in items usable for I-step extensions
         * @param hasToBeGreaterThanForIStep the minimum item id an I-step extension must exceed
         * @param candidateLength the length of the candidate's prefix
         * @param itemsetCount the number of itemsets in the candidate's prefix
         */
        CandidateTKS2(int[] prefix, BitmapSPAM bitmap, int[] sn, int[] in,
                      int hasToBeGreaterThanForIStep, int candidateLength, int itemsetCount) {
            this.prefix                    = prefix;
            this.bitmap                    = bitmap;
            this.sn                        = sn;
            this.in                        = in;
            this.hasToBeGreaterThanForIStep = hasToBeGreaterThanForIStep;
            this.candidateLength           = candidateLength;
            this.itemsetCount              = itemsetCount;
        }

        /**
         * Compares candidates by decreasing support, using several tie-breakers for determinism.
         *
         * @param o the candidate to compare to
         * @return a negative, zero, or positive value if this candidate has higher, equal, or lower priority
         */
        public int compareTo(CandidateTKS2 o) {
            if (o == this) return 0;
            int compare = o.bitmap.getSupport() - this.bitmap.getSupport();
            if (compare != 0) return compare;
            compare = this.hashCode() - o.hashCode();
            if (compare != 0) return compare;
            compare = this.itemsetCount - o.itemsetCount;
            if (compare != 0) return compare;
            return this.hasToBeGreaterThanForIStep - o.hasToBeGreaterThanForIStep;
        }
    }

    /**
     * Represents a pattern found by TKS, ordered by increasing support so that the least supported
     * pattern in the top-k set can be evicted efficiently.
     */
    public static final class PatternTKS2 implements Comparable<PatternTKS2> {
        /** The pattern's items, using -1 as itemset separator. */
        final int[]  prefix;
        /** The pattern's support. */
        final int    support;
        /** The bitmap of the pattern's occurrences, kept only when SIDs must be output. */
        BitmapSPAM  bitmap;

        /**
         * Creates a new pattern.
         *
         * @param prefix the pattern's items, using -1 as itemset separator
         * @param support the pattern's support
         */
        PatternTKS2(int[] prefix, int support) {
            this.prefix  = prefix;
            this.support = support;
        }

        /**
         * Compares patterns by increasing support.
         *
         * @param o the pattern to compare to
         * @return a negative, zero, or positive value if this pattern has lower, equal, or higher support
         */
        public int compareTo(PatternTKS2 o) {
            if (o == this) return 0;
            int compare = this.support - o.support;
            if (compare != 0) return compare;
            return this.hashCode() - o.hashCode();
        }
    }
}