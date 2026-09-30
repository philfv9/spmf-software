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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
 * Optimized implementation of the VGEN algorithm. Uses a bitmap-based vertical
 * representation of the database, contiguous internal item-id remapping, and
 * pooled candidate-item buffers to reduce memory allocation during the
 * depth-first search. Bitmaps of accepted candidates are always freshly
 * allocated (never pooled/reused), because VGEN must keep the bitmap of every
 * saved generator pattern valid for the whole run, unlike plain SPAM which can
 * discard a bitmap right after writing it.
 *
 * <p>
 * Note on the "immediate backward extension" check used in the DFS
 * ({@link #dfsPruning}): if a candidate pattern has exactly the same support as
 * the immediate (one-item-shorter) prefix it was extended from, that candidate
 * is <b>provably not a generator</b> (its own immediate prefix is a proper
 * subsequence witnessing the same support), so attempting to save it via
 * {@link #savePatternMultipleItems(BitmapSPAM, int)} would be wasted work and
 * is skipped. However, this cheap, local test must <b>never</b> be used to stop
 * the depth-first search from exploring the candidate's descendants: an
 * extension of a non-generator candidate can still be a genuine generator (its
 * support can drop below that of every one of its own proper subsequences
 * further down the search tree, even though it stayed flat for one step). Only
 * the rigorous, occurrence-position-based check performed inside
 * {@link #savePatternMultipleItems(BitmapSPAM, int)} (via
 * {@link #isThereBackwardExtension(BitmapSPAM, BitmapSPAM)}, itself gated by
 * {@link #useBackwardPruning}) is allowed to justify skipping a whole subtree
 * of the search.
 * </p>
 *
 * <p>
 * Note on the interaction between {@code maxGap} and the {@code sidsum}
 * indexing used in {@link #savePatternMultipleItems(BitmapSPAM, int)}:
 * generators found during the search are grouped by the sum of the sequence ids
 * ("sidsum") in which they occur, purely as a performance optimization to avoid
 * scanning every previously found pattern when checking sub-pattern /
 * super-pattern domination. This optimization is sound only when subsequence
 * containment is monotonic with respect to the supporting-sequence set, i.e.
 * when "a sequence matches pattern P" always implies "that sequence also
 * matches every subsequence of P". This property always holds when no
 * {@code maxGap} constraint is used. However, when {@code maxGap} restricts the
 * distance between <em>consecutive</em> elements of a pattern, it is perfectly
 * possible for a longer pattern to be satisfied by a sequence (thanks to a
 * mediating item keeping every consecutive gap small) while a shorter
 * subsequence obtained by deleting that mediating item is <em>not</em>
 * satisfied by the same sequence (because its two surviving elements would then
 * be direct, and too far apart). In that case, two patterns can have the exact
 * same support <em>count</em> while having different supporting sequence sets
 * (hence different {@code sidsum} values) and still need to be compared for
 * generator domination. Therefore, whenever {@code maxGap} is active (matching
 * the same threshold used to select {@link #strictlyContainsWithMaxgap}), the
 * {@code sidsum} bucket lookup is bypassed in favor of scanning every bucket at
 * the relevant pattern length.
 * </p>
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoVGEN {

	/** Time at which the algorithm execution started (milliseconds). */
	private long startTime;
	/** Time at which the algorithm execution ended (milliseconds). */
	private long endTime;
	/** Number of frequent generator patterns found. */
	private int patternCount;

	/** Minimum support threshold, as an absolute sequence count. */
	private int minsup = 0;
	/** Minimum length (number of items) a pattern must have to be output. */
	private int minimumPatternLength = 0;
	/** Maximum length (number of items) a pattern may have. */
	private int maximumPatternLength = 1000;
	/**
	 * Maximum allowed gap (in itemsets) between consecutive elements of a pattern.
	 */
	private int maxGap = Integer.MAX_VALUE;
	/** Whether to include sequence identifiers (SIDs) in the output. */
	private boolean outputSequenceIdentifiers;

	/** Writer used to write discovered patterns to the output file. */
	private BufferedWriter writer = null;
	/**
	 * In-memory buffer accumulating output text before it is flushed to disk (~512
	 * KB).
	 */
	private final StringBuilder outputBuffer = new StringBuilder(1 << 19);
	/** Output buffer size threshold that triggers a flush to disk (~256 KB). */
	private static final int OUTPUT_BUFFER_FLUSH_THRESHOLD = 1 << 18;

	/** Buffer size used when reading the input file (64 KB). */
	private static final int INPUT_BUFFER_SIZE = 1 << 16;

	/**
	 * Threshold below which the {@code sidsum} bucketing shortcut used in
	 * {@link #savePatternMultipleItems(BitmapSPAM, int)} becomes unsound when
	 * {@link #maxGap} is active (see class-level Javadoc). The same threshold
	 * decides whether {@link #strictlyContainsWithMaxgap} or
	 * {@link #strictlyContainsWithoutMaxgap} is used for containment checks.
	 */
	private static final int MAXGAP_EXACT_CHECK_THRESHOLD = 100;

	/** Vertical database, indexed by contiguous internal item ids. */
	private BitmapSPAM[] vdbArray = null;

	/** Highest original item id seen in the input file. */
	private int maxItemId = 0;

	/**
	 * Maps contiguous internal item ids back to their original ids from the input
	 * file.
	 */
	private int[] newIdToOriginal = null;

	/** Starting bit index of each sequence in the bitmap representation. */
	private int[] sequencesSize = null;
	/** Ending bit index of each sequence in the bitmap representation. */
	private int[] sequenceEndBit = null;

	/** Resolves a bit position to its owning sequence id (SID). */
	private BitmapSPAM.SidResolver sidResolver = null;

	/**
	 * Shared scratch buffer holding the sequence ids occupied by the current prefix
	 * bitmap.
	 */
	private int[] sidScratchBuffer = null;

	/**
	 * Shared scratch buffer holding, for each entry in sidScratchBuffer, the first
	 * set bit of the prefix bitmap in that sequence.
	 */
	private int[] firstBitScratchBuffer = null;

	/**
	 * Scratch buffer used to compute sid sums / backward-extension checks without
	 * interfering with the DFS scratch buffers.
	 */
	private int[] sidSumSidBuffer = null;
	/** Scratch buffer paired with sidSumSidBuffer for first-bit storage. */
	private int[] sidSumFirstBitBuffer = null;

	/**
	 * True if every itemset in the input file contains at most one item, allowing
	 * I-steps to be skipped entirely.
	 */
	private boolean onlySingletonItemsets = true;

	/** Counter used to throttle how often memory usage is checked. */
	private int memCheckCounter = 0;
	/** Number of DFS nodes visited between memory usage checks. */
	private static final int MEM_CHECK_INTERVAL = 1000;

	/**
	 * Reusable buffer used to store integers parsed from a line of the input file.
	 */
	private int[] tokenBuffer = new int[64];

	/**
	 * Reusable buffer used to build the textual representation of a pattern before
	 * output.
	 */
	private final StringBuilder patternBuilder = new StringBuilder(64);

	/**
	 * Flat buffer holding the sequence currently being built by the DFS (items with
	 * -1 as itemset separators).
	 */
	private int[] patternBuffer = new int[64];
	/** Number of valid tokens currently stored in patternBuffer. */
	private int patternLength = 0;

	/**
	 * Pool of reusable buffers for S-step candidates, indexed by DFS depth. Only
	 * the item-id arrays are actually reused.
	 */
	private final List<DfsBuffer> sBufferPool = new ArrayList<>();
	/**
	 * Pool of reusable buffers for I-step candidates, indexed by DFS depth. Only
	 * the item-id arrays are actually reused.
	 */
	private final List<DfsBuffer> iBufferPool = new ArrayList<>();
	/** Number of frequent items (size of the dense internal item-id space). */
	private int frequentItemCount = 0;

	/**
	 * Co-occurrence map for items appearing in later itemsets (used for S-step CMAP
	 * pruning).
	 */
	private Map<Integer, Map<Integer, Integer>> coocMapAfter = null;
	/**
	 * Co-occurrence map for items appearing in the same itemset (used for I-step
	 * CMAP pruning).
	 */
	private Map<Integer, Map<Integer, Integer>> coocMapEquals = null;

	/**
	 * Reusable scratch set used by {@link #updateCoocMaps(List)} to dedupe
	 * same-itemset co-occurring pairs once per sequence, avoiding a fresh
	 * allocation for every sequence processed.
	 */
	private final HashSet<Long> coocSeenEqualsBuffer = new HashSet<>();
	/**
	 * Reusable scratch set used by {@link #updateCoocMaps(List)} to dedupe
	 * later-itemset co-occurring pairs once per sequence, avoiding a fresh
	 * allocation for every sequence processed.
	 */
	private final HashSet<Long> coocSeenAfterBuffer = new HashSet<>();

	/** Whether CMAP pruning is activated. */
	private boolean useCMAPPruning = true;
	/** Whether immediate backward checking is activated. */
	private boolean useImmediateBackwardChecking = true;
	/** Whether backward pruning is activated. */
	private boolean useBackwardPruning = false;

	/**
	 * Number of sequences in the database (used as the support of the empty set).
	 */
	private int transactionCount = 0;

	/** True if the user has set a max gap constraint. */
	private boolean maxGapActivated = false;

	/** Generator patterns stored by item-count level, then grouped by sid sum. */
	private List<Map<Integer, List<GeneratorPattern>>> generatorPatterns = null;

	/**
	 * Reusable per-depth scratch storage for candidate item ids during the DFS (the
	 * item-id arrays only; bitmaps are never pooled).
	 */
	private static class DfsBuffer {
		/** Candidate item ids held in this buffer. */
		int[] items;
		/**
		 * Bitmaps corresponding to each candidate item; always freshly allocated, never
		 * reused across DFS visits.
		 */
		BitmapSPAM[] bitmaps;

		/**
		 * Creates a buffer able to hold the given number of candidates.
		 *
		 * @param capacity the maximum number of candidates this buffer can hold
		 */
		DfsBuffer(int capacity) {
			items = new int[capacity];
			bitmaps = new BitmapSPAM[capacity];
		}

		/**
		 * Grows this buffer in place to at least the given capacity.
		 *
		 * @param capacity the new minimum capacity
		 */
		void growTo(int capacity) {
			items = new int[capacity];
			bitmaps = new BitmapSPAM[capacity];
		}
	}

	/** Stores a generator pattern found by VGEN. */
	public static class GeneratorPattern {
		/** Flat buffer of internal item ids with -1 as itemset separators. */
		int[] buffer;
		/** Number of valid tokens in buffer. */
		int length;
		/** Number of items in the pattern. */
		int itemCount;
		/** Sum of even original item ids in this pattern. */
		int sumOfEvenItems;
		/** Sum of odd original item ids in this pattern. */
		int sumOfOddItems;
		/**
		 * Bitmap holding the pattern's support information (a dedicated, never-reused
		 * instance).
		 */
		BitmapSPAM bitmap;
		/** Absolute support of the pattern. */
		int support;
		/** Sum of sequence ids containing this pattern. */
		int sidsum;
		/**
		 * Pre-parsed itemset representation of {@link #buffer} (each element is one
		 * itemset), computed once when the pattern is stored and reused by every
		 * later sub-pattern/super-pattern containment check, instead of re-parsing
		 * the flat buffer on every comparison.
		 */
		List<int[]> itemsets;

		/**
		 * Get the support
		 * 
		 * @return the support
		 */
		public int getSupport() {
			return support;
		}

		/**
		 * Returns a string representation of the pattern and its associated statistics.
		 * 
		 * @return a string representation
		 */
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			// append items and separators from the buffer
			for (int i = 0; i < length; i++) {
				sb.append(buffer[i]).append(" ");
			}

			// append pattern statistics
			sb.append("#SUP: ").append(support);

			return sb.toString();
		}
	}

	/** Creates a new instance of the VGEN2 algorithm. */
	public AlgoVGEN() {
	}

	/**
	 * Runs the VGEN algorithm on an input sequence database and writes the frequent
	 * sequential generators to a file.
	 *
	 * @param input          the path of the input file
	 * @param outputFilePath the path of the output file
	 * @param minsupRel      the minimum support threshold, as a relative value
	 *                       between 0 and 1
	 * @return the list of generator patterns grouped by level and sid sum
	 * @throws IOException if an error occurs while reading the input or writing the
	 *                     output
	 */
	public List<Map<Integer, List<GeneratorPattern>>> runAlgorithm(String input, String outputFilePath,
			double minsupRel) throws IOException {
		writer = new BufferedWriter(new FileWriter(outputFilePath), 1 << 16);
		writer.write("@FILETYPE=\"Frequent sequential generators\"");
		writer.append(System.lineSeparator());
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.append(System.lineSeparator());
		
		patternCount = 0;
		memCheckCounter = 0;
		outputBuffer.setLength(0);
		MemoryLogger.getInstance().reset();

		startTime = System.currentTimeMillis();
		try {
			vgen(input, minsupRel);

			// IMPORTANT: the generator patterns collected during mining store items
			// using the dense/contiguous internal item ids used internally for
			// performance reasons (see newIdToOriginal). Before the patterns are
			// written to the output file or handed back to the caller, we must
			// translate those internal ids back to the original item ids from the
			// input file. This must be done exactly once.
			translatePatternsToOriginalIds();

			writeResultTofile();
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
		return generatorPatterns;
	}

	/**
	 * Translates the item ids stored in every generator pattern from the internal
	 * dense representation back to their original ids from the input file. This
	 * must be called exactly once, after mining has completed and before the
	 * patterns are written to the output file or exposed to the caller; calling it
	 * more than once would corrupt the item ids.
	 */
	private void translatePatternsToOriginalIds() {
		if (generatorPatterns == null)
			return;
		for (Map<Integer, List<GeneratorPattern>> level : generatorPatterns) {
			for (List<GeneratorPattern> patterns : level.values()) {
				for (GeneratorPattern pattern : patterns) {
					for (int i = 0; i < pattern.length; i++) {
						if (pattern.buffer[i] != -1) {
							pattern.buffer[i] = newIdToOriginal[pattern.buffer[i]];
						}
					}
				}
			}
		}
	}

	/** Clears all pooled DFS buffers so they can be garbage collected. */
	private void clearBufferPools() {
		sBufferPool.clear();
		iBufferPool.clear();
	}

	/**
	 * Checks whether a line of the input file should be ignored (empty line or
	 * metadata/comment line).
	 *
	 * @param line the line to check
	 * @return true if the line should be skipped
	 */
	private static boolean isSkippableLine(String line) {
		return line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@';
	}

	/**
	 * Splits a line of the input file into integer tokens, growing the buffer if
	 * needed.
	 *
	 * @param line   the line to tokenize
	 * @param buffer the buffer to store the parsed integers into (may be replaced
	 *               if too small)
	 * @return the number of tokens parsed
	 */
	private int tokenizeLine(String line, int[] buffer) {
		int length = line.length();
		int count = 0;
		int i = 0;
		while (i < length) {
			while (i < length && line.charAt(i) == ' ')
				i++; // skip spaces
			if (i >= length)
				break;

			boolean neg = false;
			if (line.charAt(i) == '-') {
				neg = true;
				i++;
			} // handle negative sign

			int val = 0;
			while (i < length && line.charAt(i) != ' ') {
				val = val * 10 + (line.charAt(i) - '0'); // accumulate digits
				i++;
			}
			if (count == buffer.length) {
				buffer = Arrays.copyOf(buffer, buffer.length * 2); // grow buffer
			}
			buffer[count++] = neg ? -val : val;
		}
		tokenBuffer = buffer;
		return count;
	}

	/**
	 * Ensures the pattern buffer can hold at least the given number of tokens,
	 * growing it if necessary.
	 *
	 * @param minCapacity the minimum number of tokens the buffer must be able to
	 *                    hold
	 */
	private void ensurePatternCapacity(int minCapacity) {
		if (minCapacity > patternBuffer.length) {
			patternBuffer = Arrays.copyOf(patternBuffer, Math.max(minCapacity, patternBuffer.length * 2));
		}
	}

	/**
	 * Runs the main phases of VGEN: counting item supports, building the vertical
	 * database and co-occurrence maps, saving single-item generators, and
	 * performing the depth-first search for longer generator patterns.
	 *
	 * <p>
	 * The input file is read from disk exactly once. Its tokenized content is
	 * cached in memory using plain {@code int[]} arrays ({@code database} holds
	 * every token of every kept line back-to-back, while {@code lineStart} /
	 * {@code lineLength} record where each line's tokens are located inside
	 * {@code database}). Both logical passes needed to build the vertical database
	 * (support counting, then bitmap/co-occurrence construction) run against this
	 * in-memory cache; the cache is discarded as soon as it is no longer needed,
	 * before the depth-first search begins.
	 * </p>
	 *
	 * @param input     the path of the input file
	 * @param minsupRel the relative minimum support threshold
	 * @throws IOException if an error occurs while reading the input file
	 */
	private void vgen(String input, double minsupRel) throws IOException {

		// init generator storage by item-count level (levels 0 and 1 always exist)
		generatorPatterns = new ArrayList<>(20);
		generatorPatterns.add(new HashMap<>());
		generatorPatterns.add(new HashMap<>());

		// ---------------------------------------------------------------------
		// SINGLE DISK SCAN: read the whole input file once and cache its
		// tokenized content in memory using plain int[] arrays, so that the two
		// logical passes below (support counting, then vertical-database/CMAP
		// construction) never touch the disk again.
		// ---------------------------------------------------------------------
		int[] database = new int[1 << 16];
		int databaseLength = 0;
		int[] lineStart = new int[256];
		int[] lineLength = new int[256];
		int lineCount = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))),
				INPUT_BUFFER_SIZE)) {

			String line;
			while ((line = reader.readLine()) != null) {
				if (isSkippableLine(line)) {
					continue;
				}

				int tokenCount = tokenizeLine(line, tokenBuffer);
				// tokenizeLine() may have replaced the shared tokenBuffer field with a
				// larger array; always use the up-to-date reference from the field.
				int[] tokens = tokenBuffer;

				if (databaseLength + tokenCount > database.length) {
					int newCap = Math.max(database.length * 2, databaseLength + tokenCount);
					database = Arrays.copyOf(database, newCap);
				}
				System.arraycopy(tokens, 0, database, databaseLength, tokenCount);

				if (lineCount == lineStart.length) {
					lineStart = Arrays.copyOf(lineStart, lineStart.length * 2);
					lineLength = Arrays.copyOf(lineLength, lineLength.length * 2);
				}
				lineStart[lineCount] = databaseLength;
				lineLength[lineCount] = tokenCount;
				lineCount++;

				databaseLength += tokenCount;
			}
		}

		ArrayListInt sequencesSizeList = new ArrayListInt();

		int initialCapacity = 1024;
		int[] itemSupport = new int[initialCapacity];
		int[] itemLastSid = new int[initialCapacity];
		Arrays.fill(itemLastSid, -1);
		maxItemId = 0;

		int lastBitIndex;
		boolean sawMultiItemItemset = false;

		// first logical pass (in memory): count item supports and detect
		// singleton-itemset mode
		{
			int bitIndex = 0;
			int sid = 0;

			for (int L = 0; L < lineCount; L++) {
				int start = lineStart[L];
				int count = lineLength[L];

				sequencesSizeList.add(bitIndex);

				int itemsInCurrentItemset = 0;

				for (int t = 0; t < count; t++) {
					int token = database[start + t];
					if (token == -1) {
						bitIndex++;
						itemsInCurrentItemset = 0;
					} else if (token == -2) {
						sid++;
					} else {
						if (!sawMultiItemItemset) {
							itemsInCurrentItemset++;
							if (itemsInCurrentItemset > 1) {
								sawMultiItemItemset = true;
							}
						}

						if (token >= itemSupport.length) {
							int oldLen = itemSupport.length;
							int newCap = Math.max(token + 1, oldLen * 2);
							itemSupport = Arrays.copyOf(itemSupport, newCap);
							itemLastSid = Arrays.copyOf(itemLastSid, newCap);
							Arrays.fill(itemLastSid, oldLen, newCap, -1);
						}
						if (token > maxItemId)
							maxItemId = token;

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
		transactionCount = numSequences;

		minsup = (int) Math.ceil((minsupRel * numSequences) - 0.00001);
		if (minsup == 0)
			minsup = 1;

		sequenceEndBit = BitmapSPAM.buildSequenceEndBit(sequencesSize, lastBitIndex, numSequences);

		sidResolver = new BitmapSPAM.SidResolver(sequencesSize, sequenceEndBit);
		sidScratchBuffer = new int[Math.max(numSequences, 1)];
		firstBitScratchBuffer = new int[Math.max(numSequences, 1)];
		sidSumSidBuffer = new int[Math.max(numSequences, 1)];
		sidSumFirstBitBuffer = new int[Math.max(numSequences, 1)];

		// build contiguous item-id remap, keeping only frequent items
		ArrayListInt frequentOriginalIdsList = new ArrayListInt();
		for (int origId = 0; origId <= maxItemId; origId++) {
			int cnt = itemSupport[origId];
			if (cnt >= minsup) {
				int newId = frequentOriginalIdsList.size();
				frequentOriginalIdsList.add(origId);
				itemSupport[origId] = newId;
			} else {
				itemSupport[origId] = -1;
			}
		}
		int[] remap = itemSupport;
		itemSupport = null;
		itemLastSid = null;

		frequentItemCount = frequentOriginalIdsList.size();
		newIdToOriginal = new int[frequentItemCount];
		for (int i = 0; i < frequentItemCount; i++) {
			newIdToOriginal[i] = frequentOriginalIdsList.get(i);
		}
		frequentOriginalIdsList = null;

		// second logical pass (in memory): build the vertical database and the
		// co-occurrence maps directly from the cached database, without touching
		// the disk again
		vdbArray = new BitmapSPAM[frequentItemCount];
		coocMapEquals = new HashMap<>(frequentItemCount);
		coocMapAfter = new HashMap<>(frequentItemCount);

		int sid2 = 0;
		List<int[]> itemsetsInSeq = new ArrayList<>();
		int[] currentItemset = new int[8];

		for (int L = 0; L < lineCount; L++) {
			int start = lineStart[L];
			int count = lineLength[L];
			int tid2 = 0;

			itemsetsInSeq.clear();
			int currentItemsetSize = 0;

			for (int t = 0; t < count; t++) {
				int token = database[start + t];
				if (token == -1) {
					tid2++;
					if (currentItemsetSize > 0) {
						itemsetsInSeq.add(Arrays.copyOf(currentItemset, currentItemsetSize));
						currentItemsetSize = 0;
					}
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

						if (currentItemsetSize == currentItemset.length) {
							currentItemset = Arrays.copyOf(currentItemset, currentItemset.length * 2);
						}
						currentItemset[currentItemsetSize++] = newId;
					}
				}
			}
			if (currentItemsetSize > 0) {
				itemsetsInSeq.add(Arrays.copyOf(currentItemset, currentItemsetSize));
			}

			// update the co-occurrence maps using the frequent-item itemsets of this
			// sequence
			updateCoocMaps(itemsetsInSeq);
		}

		// the raw tokenized database is no longer needed now that the vertical
		// bitmap database and the co-occurrence maps have been built; release it
		// so its memory can be reclaimed before the (potentially long) DFS phase
		database = null;
		lineStart = null;
		lineLength = null;
		remap = null;

		// save frequent single-item generators (skip items whose support equals the
		// whole database)
		int[] frequentItemsArr = new int[frequentItemCount];
		for (int i = 0; i < frequentItemCount; i++) {
			frequentItemsArr[i] = i;
		}

		for (int newId = 0; newId < frequentItemCount; newId++) {
			BitmapSPAM bm = vdbArray[newId];
			if (transactionCount != bm.getSupport()) {
				saveSingleItemGenerator(newId, bm);
			}
		}

		if (maximumPatternLength == 1) {
			addEmptySetGenerator();
			return;
		}

		// depth-first search for longer generators, starting from every frequent
		// single item. The DFS buffer pools are guaranteed to already be empty at
		// this point (either freshly constructed, or emptied by clearBufferPools()
		// at the end of a previous run() call), so no explicit reset is needed here.
		for (int idx = 0; idx < frequentItemCount; idx++) {
			BitmapSPAM bm = vdbArray[idx];

			patternLength = 0;
			ensurePatternCapacity(1);
			patternBuffer[patternLength++] = idx;

			dfsPruning(bm, frequentItemsArr, frequentItemCount, frequentItemsArr, frequentItemCount, idx, 2, 0, idx);
		}

		// the empty set is always a generator, so it is always added to the result set
		addEmptySetGenerator();
	}

	/**
	 * Updates the co-occurrence maps from the itemsets of a single sequence,
	 * counting each (item, item) pair at most once per sequence so that CMAP counts
	 * reflect sequence support.
	 *
	 * <p>
	 * Uses the reusable {@link #coocSeenEqualsBuffer} / {@link #coocSeenAfterBuffer}
	 * scratch sets (cleared at the start of this method) instead of allocating two
	 * new {@code HashSet} instances on every call, since this method runs once per
	 * sequence of the input file and is never called concurrently or re-entrantly.
	 * </p>
	 *
	 * @param itemsetsInSeq list of itemsets (each an int array of internal item
	 *                      ids) in this sequence
	 */
	private void updateCoocMaps(List<int[]> itemsetsInSeq) {
		HashSet<Long> seenEquals = coocSeenEqualsBuffer;
		HashSet<Long> seenAfter = coocSeenAfterBuffer;
		seenEquals.clear();
		seenAfter.clear();
		int n = itemsetsInSeq.size();

		for (int p = 0; p < n; p++) {
			int[] isP = itemsetsInSeq.get(p);

			// pairs co-occurring within the same itemset
			for (int a = 0; a < isP.length; a++) {
				for (int b = a + 1; b < isP.length; b++) {
					int ia = isP[a], ib = isP[b];
					long key = ((long) ia << 32) | (ib & 0xffffffffL);
					if (seenEquals.add(key)) {
						coocMapEquals.computeIfAbsent(ia, k -> new HashMap<>()).merge(ib, 1, Integer::sum);
					}
				}
			}

			// pairs where the second item is in a later itemset
			for (int q = p + 1; q < n; q++) {
				int[] isQ = itemsetsInSeq.get(q);
				for (int ia : isP) {
					for (int ib : isQ) {
						long key = ((long) ia << 32) | (ib & 0xffffffffL);
						if (seenAfter.add(key)) {
							coocMapAfter.computeIfAbsent(ia, k -> new HashMap<>()).merge(ib, 1, Integer::sum);
						}
					}
				}
			}
		}
	}

	/**
	 * Saves a single-item generator pattern.
	 *
	 * @param item   the internal id of the frequent item
	 * @param bitmap the (permanent, never reused) bitmap holding the pattern's
	 *               support information
	 */
	private void saveSingleItemGenerator(int item, BitmapSPAM bitmap) {
		int sidsum = computeSidSum(bitmap);
		int support = bitmap.getSupport();
		int origItem = newIdToOriginal[item];
		int sumEven = (origItem % 2 == 0) ? origItem : 0;
		int sumOdd = (origItem % 2 != 0) ? origItem : 0;

		// group the pattern under its sid sum at level 1
		List<GeneratorPattern> listPatterns = generatorPatterns.get(1).get(sidsum);
		if (listPatterns == null) {
			listPatterns = new ArrayList<>();
			generatorPatterns.get(1).put(sidsum, listPatterns);
		}

		patternCount++;
		GeneratorPattern gp = new GeneratorPattern();
		gp.buffer = new int[] { item };
		gp.length = 1;
		gp.itemCount = 1;
		gp.sumOfEvenItems = sumEven;
		gp.sumOfOddItems = sumOdd;
		gp.bitmap = bitmap;
		gp.support = support;
		gp.sidsum = sidsum;
		// pre-parse once so future domination checks never need to re-parse it
		gp.itemsets = flatToItemsets(gp.buffer, gp.length);
		listPatterns.add(gp);
	}

	/** Adds the empty set as a generator (it is always a generator). */
	private void addEmptySetGenerator() {
		GeneratorPattern gp = new GeneratorPattern();
		gp.buffer = new int[0];
		gp.length = 0;
		gp.itemCount = 0;
		gp.sumOfEvenItems = 0;
		gp.sumOfOddItems = 0;
		gp.bitmap = null;
		gp.support = transactionCount;
		gp.sidsum = 0;
		gp.itemsets = new ArrayList<>();

		List<GeneratorPattern> listLevel0 = new ArrayList<>();
		listLevel0.add(gp);
		generatorPatterns.get(0).put(0, listLevel0);
		patternCount++;
	}

	/**
	 * Retrieves the pooled DFS buffer for the given recursion depth, growing it on
	 * demand if the requested capacity exceeds what is currently allocated. Only
	 * the item-id array is meant to be reused across DFS visits; the bitmap slots
	 * are always overwritten with fresh instances.
	 *
	 * @param pool     the buffer pool to use (S-step or I-step pool)
	 * @param depth    the current DFS recursion depth
	 * @param capacity the minimum capacity required for this node
	 * @return a buffer with at least the requested capacity
	 */
	private DfsBuffer getBuffer(List<DfsBuffer> pool, int depth, int capacity) {
		if (capacity < 1)
			capacity = 1;
		while (pool.size() <= depth) {
			pool.add(new DfsBuffer(capacity));
		}
		DfsBuffer buf = pool.get(depth);
		if (buf.items.length < capacity) {
			buf.growTo(capacity);
		}
		return buf;
	}

	/**
	 * Finds the index of the first entry in arr[0..count) that is strictly greater
	 * than value, using binary search since the array is always kept sorted in
	 * ascending order.
	 *
	 * @param arr   the sorted array to search
	 * @param count the number of valid entries in arr
	 * @param value the value to compare against
	 * @return the index of the first entry strictly greater than value, or count if
	 *         none is found
	 */
	private static int upperBound(int[] arr, int count, int value) {
		int lo = 0, hi = count;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (arr[mid] <= value)
				lo = mid + 1;
			else
				hi = mid;
		}
		return lo;
	}

	/**
	 * Recursively extends the pattern currently held in patternBuffer with S-steps
	 * and I-steps, saving each frequent generator found and pruning infrequent or
	 * non-generator candidates.
	 *
	 * <p>
	 * <b>Important correctness note:</b> the cheap "immediate backward extension"
	 * test (comparing the support of the extended candidate against the support of
	 * its immediate, one-item-shorter prefix) is only used below to decide whether
	 * it is worth calling {@link #savePatternMultipleItems(BitmapSPAM, int)} for a
	 * given candidate (when the supports are equal, the candidate is provably not a
	 * generator, so the call would be wasted work). It is deliberately <b>not</b>
	 * used to skip recursing into the candidate's descendants: an extension of a
	 * candidate that failed this cheap test can still turn out to be a genuine
	 * generator further down the search tree. Only {@code hasBackwardExtension},
	 * returned by {@link #savePatternMultipleItems(BitmapSPAM, int)} and backed by
	 * the rigorous, occurrence-position-based check in
	 * {@link #isThereBackwardExtension(BitmapSPAM, BitmapSPAM)}, is allowed to
	 * stop the recursion for a whole subtree.
	 * </p>
	 *
	 * @param prefixBitmap               the bitmap representation of the current
	 *                                   pattern
	 * @param sn                         candidate items usable for S-steps
	 * @param snCount                    number of valid entries in sn
	 * @param in                         candidate items usable for I-steps
	 * @param inCount                    number of valid entries in in
	 * @param hasToBeGreaterThanForIStep the minimum item id (exclusive) allowed for
	 *                                   I-step extension
	 * @param m                          the item-count length of the pattern that
	 *                                   would result from the extension
	 * @param depth                      the current DFS recursion depth, used to
	 *                                   select the pooled item-id buffers
	 * @param lastAppendedItem           the internal id of the last appended item,
	 *                                   used for CMAP pruning
	 * @throws IOException if an error occurs while saving a pattern
	 */
	private void dfsPruning(BitmapSPAM prefixBitmap, int[] sn, int snCount, int[] in, int inCount,
			int hasToBeGreaterThanForIStep, int m, int depth, int lastAppendedItem) throws IOException {
		// CMAP pruning maps for this node are keyed on the last appended item only
		Map<Integer, Integer> mapSupportItemsAfter = useCMAPPruning ? coocMapAfter.get(lastAppendedItem) : null;

		DfsBuffer sBuf = getBuffer(sBufferPool, depth, snCount);
		int sCount = 0;

		int occCount = (maxGap == Integer.MAX_VALUE && snCount > 0)
				? prefixBitmap.collectOccupiedSidsAndFirstBit(sidScratchBuffer, firstBitScratchBuffer, sidResolver)
				: 0;

		for (int idx = 0; idx < snCount; idx++) {
			int item = sn[idx];

			// CMAP pruning: skip items that never follow the last appended item often
			// enough
			if (useCMAPPruning) {
				if (mapSupportItemsAfter == null)
					continue;
				Integer support = mapSupportItemsAfter.get(item);
				if (support == null || support < minsup)
					continue;
			}

			BitmapSPAM itemBitmap = vdbArray[item];

			// always allocate a fresh bitmap: accepted candidates may be persisted as
			// generators
			BitmapSPAM out = new BitmapSPAM();
			out.resetForReuse(itemBitmap.wordCount);

			prefixBitmap.createNewBitmapSStep(out, itemBitmap, maxGap, sidResolver, sequenceEndBit, sidScratchBuffer,
					firstBitScratchBuffer, occCount);

			if (out.getSupportWithoutGapTotal() >= minsup) {
				sBuf.items[sCount] = item;
				sBuf.bitmaps[sCount] = out;
				sCount++;
			}
		}

		for (int k = 0; k < sCount; k++) {
			int item = sBuf.items[k];
			BitmapSPAM newBitmap = sBuf.bitmaps[k];

			if (newBitmap.getSupport() >= minsup) {
				int savedLen = patternLength;
				ensurePatternCapacity(patternLength + 2);
				if (patternLength > 0) {
					patternBuffer[patternLength++] = -1;
				}
				patternBuffer[patternLength++] = item;

				// Cheap local test: equal support to the immediate prefix proves this
				// exact candidate is not a generator, so saving it can be skipped.
				// This must NOT gate recursion (see method-level Javadoc).
				boolean worthAttemptingSave = !useImmediateBackwardChecking
						|| prefixBitmap.getSupport() != newBitmap.getSupport();

				boolean hasBackwardExtension = false;
				if (worthAttemptingSave) {
					hasBackwardExtension = savePatternMultipleItems(newBitmap, m);
				}

				// recursion always proceeds (up to the max length and unless the
				// rigorous backward-extension check proved the whole subtree is
				// redundant), regardless of the cheap local test above
				if (!hasBackwardExtension && m < maximumPatternLength) {
					dfsPruning(newBitmap, sBuf.items, sCount, sBuf.items, sCount, item, m + 1, depth + 1, item);
				}
				patternLength = savedLen;
			}
		}

		// I-steps: extend the pattern by adding an item to the last itemset
		if (!onlySingletonItemsets) {
			Map<Integer, Integer> mapSupportItemsEquals = useCMAPPruning ? coocMapEquals.get(lastAppendedItem) : null;
			DfsBuffer iBuf = getBuffer(iBufferPool, depth, inCount);
			int iCount = 0;

			int startIdx = upperBound(in, inCount, hasToBeGreaterThanForIStep);

			for (int idx = startIdx; idx < inCount; idx++) {
				int item = in[idx];

				if (useCMAPPruning) {
					if (mapSupportItemsEquals == null)
						continue;
					Integer support = mapSupportItemsEquals.get(item);
					if (support == null || support < minsup)
						continue;
				}

				BitmapSPAM itemBitmap = vdbArray[item];

				// always allocate a fresh bitmap: accepted candidates may be persisted as
				// generators
				BitmapSPAM out = new BitmapSPAM();
				out.resetForReuse(Math.min(prefixBitmap.wordCount, itemBitmap.wordCount));

				prefixBitmap.createNewBitmapIStep(out, itemBitmap, sidResolver);

				if (out.getSupport() >= minsup) {
					iBuf.items[iCount] = item;
					iBuf.bitmaps[iCount] = out;
					iCount++;
				}
			}

			for (int k = 0; k < iCount; k++) {
				int item = iBuf.items[k];
				BitmapSPAM newBitmap = iBuf.bitmaps[k];

				int savedLen = patternLength;
				ensurePatternCapacity(patternLength + 1);
				patternBuffer[patternLength++] = item;

				// same rationale as for S-steps above: this cheap test may only skip
				// the save attempt for THIS candidate, never the recursion.
				boolean worthAttemptingSave = !useImmediateBackwardChecking
						|| prefixBitmap.getSupport() != newBitmap.getSupport();

				boolean hasBackwardExtension = false;
				if (worthAttemptingSave) {
					hasBackwardExtension = savePatternMultipleItems(newBitmap, m);
				}

				if (!hasBackwardExtension && m < maximumPatternLength) {
					dfsPruning(newBitmap, sBuf.items, sCount, iBuf.items, iCount, item, m + 1, depth + 1, item);
				}
				patternLength = savedLen;
			}
		}

		// periodically check memory usage without doing it on every single node
		if (++memCheckCounter >= MEM_CHECK_INTERVAL) {
			memCheckCounter = 0;
			MemoryLogger.getInstance().checkMemory();
		}
	}

	/**
	 * Saves a multi-item generator pattern after checking sub-pattern and
	 * super-pattern containment against previously stored generators, and removes
	 * any previously stored larger pattern that is no longer a generator.
	 *
	 * <p>
	 * <b>Important correctness note on {@code sidsum} bucketing:</b> both the
	 * sub-pattern and super-pattern comparison loops below normally restrict their
	 * search to the bucket of previously stored patterns whose {@code sidsum}
	 * equals that of the pattern currently being checked. This is a valid
	 * optimization only when subsequence containment is monotonic with respect to
	 * the supporting-sequence set, which is always true when no {@code maxGap}
	 * constraint is active. When {@code maxGap} restricts the distance between
	 * consecutive elements of a pattern, this monotonicity can break down (see the
	 * class-level Javadoc for a detailed example), so two patterns can have equal
	 * support counts while having different {@code sidsum} values and still be in a
	 * genuine domination relationship. In that case the {@code sidsum} bucket
	 * lookup is bypassed and every bucket at the relevant pattern length is scanned
	 * instead.
	 * </p>
	 *
	 * <p>
	 * The current candidate pattern (held in {@link #patternBuffer}) is parsed into
	 * its itemset representation exactly once per call (see {@code currentItemsets}
	 * below) and reused for every comparison performed in this call; if the
	 * candidate ends up being stored as a new generator, that same parsed
	 * representation is cached on the resulting {@link GeneratorPattern} so it does
	 * not need to be re-parsed by any future comparison either.
	 * </p>
	 *
	 * @param bitmap the (dedicated, never reused) bitmap holding the pattern's
	 *               support information
	 * @param length the item count of the pattern being saved
	 * @return true if a backward extension was found (extensions of this pattern
	 *         should be pruned)
	 * @throws IOException if an error occurs while saving the pattern
	 */
	private boolean savePatternMultipleItems(BitmapSPAM bitmap, int length) throws IOException {
		int sidsum = computeSidSum(bitmap);
		int support = bitmap.getSupport();

		// not a generator if its support equals the support of the empty set
		if (support == transactionCount) {
			return false;
		}

		// compute sum of even/odd original items in the current pattern
		int sumEven = 0, sumOdd = 0, itemCount = 0;
		for (int i = 0; i < patternLength; i++) {
			int t = patternBuffer[i];
			if (t != -1) {
				int orig = newIdToOriginal[t];
				if (orig % 2 == 0)
					sumEven += orig;
				else
					sumOdd += orig;
				itemCount++;
			}
		}

		// parse the current candidate into itemsets once: it may be compared
		// against many previously stored generators below, and, if it turns out
		// to be a genuine generator, against many more in the future
		List<int[]> currentItemsets = flatToItemsets(patternBuffer, patternLength);

		// see method-level Javadoc: the sidsum bucketing optimization is unsound
		// when maxGap restricts consecutive-element distances, because two
		// patterns can then have equal support counts with different supporting
		// sequence sets (and hence different sidsum) while still being in a
		// genuine domination relationship
		boolean sidSumBucketingIsSound = !(maxGapActivated && maxGap < MAXGAP_EXACT_CHECK_THRESHOLD);

		// WE COMPARE PATTERN WITH SMALLER PATTERNS FOR SUB-PATTERN CHECKING
		boolean mayBeAGenerator = true;
		for (int i = 1; i < length && i < generatorPatterns.size(); i++) {
			Map<Integer, List<GeneratorPattern>> levelMap = generatorPatterns.get(i);

			if (sidSumBucketingIsSound) {
				List<GeneratorPattern> level = levelMap.get(sidsum);
				if (level == null)
					continue;
				for (GeneratorPattern pPrime : level) {
					if (sumEven >= pPrime.sumOfEvenItems && sumOdd >= pPrime.sumOfOddItems && support == pPrime.support
							&& strictlyContainsPattern(currentItemsets, pPrime.itemsets)) {
						if (useBackwardPruning) {
							if (isThereBackwardExtension(bitmap, pPrime.bitmap)) {
								return true;
							} else {
								mayBeAGenerator = false;
							}
						} else {
							return false;
						}
					}
				}
			} else {
				// maxGap active: the sidsum shortcut is unsound, scan every bucket
				for (List<GeneratorPattern> level : levelMap.values()) {
					for (GeneratorPattern pPrime : level) {
						if (sumEven >= pPrime.sumOfEvenItems && sumOdd >= pPrime.sumOfOddItems
								&& support == pPrime.support
								&& strictlyContainsPattern(currentItemsets, pPrime.itemsets)) {
							if (useBackwardPruning) {
								if (isThereBackwardExtension(bitmap, pPrime.bitmap)) {
									return true;
								} else {
									mayBeAGenerator = false;
								}
							} else {
								return false;
							}
						}
					}
				}
			}
		}

		if (!mayBeAGenerator)
			return false;

		// WE COMPARE WITH LARGER PATTERNS FOR SUPER-PATTERN CHECKING
		for (int i = generatorPatterns.size() - 1; i > length; i--) {
			Map<Integer, List<GeneratorPattern>> levelMap = generatorPatterns.get(i);

			if (sidSumBucketingIsSound) {
				List<GeneratorPattern> level = levelMap.get(sidsum);
				if (level == null)
					continue;
				Iterator<GeneratorPattern> iter = level.iterator();
				while (iter.hasNext()) {
					GeneratorPattern pPrime = iter.next();
					if (sumEven <= pPrime.sumOfEvenItems && sumOdd <= pPrime.sumOfOddItems && support == pPrime.support
							&& strictlyContainsPattern(pPrime.itemsets, currentItemsets)) {
						patternCount--;
						iter.remove();
					}
				}
			} else {
				// maxGap active: the sidsum shortcut is unsound, scan every bucket
				for (List<GeneratorPattern> level : levelMap.values()) {
					Iterator<GeneratorPattern> iter = level.iterator();
					while (iter.hasNext()) {
						GeneratorPattern pPrime = iter.next();
						if (sumEven <= pPrime.sumOfEvenItems && sumOdd <= pPrime.sumOfOddItems
								&& support == pPrime.support
								&& strictlyContainsPattern(pPrime.itemsets, currentItemsets)) {
							patternCount--;
							iter.remove();
						}
					}
				}
			}
		}

		// store the new generator
		while (generatorPatterns.size() - 1 < length) {
			generatorPatterns.add(new HashMap<>());
		}

		List<GeneratorPattern> listPatterns = generatorPatterns.get(length).get(sidsum);
		if (listPatterns == null) {
			listPatterns = new ArrayList<>();
			generatorPatterns.get(length).put(sidsum, listPatterns);
		}

		patternCount++;
		GeneratorPattern gp = new GeneratorPattern();
		gp.buffer = Arrays.copyOf(patternBuffer, patternLength);
		gp.length = patternLength;
		gp.itemCount = itemCount;
		gp.sumOfEvenItems = sumEven;
		gp.sumOfOddItems = sumOdd;
		gp.bitmap = bitmap;
		gp.support = support;
		gp.sidsum = sidsum;
		// reuse the already-parsed itemsets instead of re-parsing gp.buffer
		gp.itemsets = currentItemsets;
		listPatterns.add(gp);

		return false;
	}

	/**
	 * Computes the sum of sequence ids containing the pattern described by the
	 * given bitmap.
	 *
	 * @param bitmap the bitmap to compute the sid sum for
	 * @return the sum of sequence ids in which the pattern occurs
	 */
	private int computeSidSum(BitmapSPAM bitmap) {
		// collect the sequences containing the pattern
		int occCount = bitmap.collectOccupiedSidsAndFirstBit(sidSumSidBuffer, sidSumFirstBitBuffer, sidResolver);
		int sum = 0;
		for (int i = 0; i < occCount; i++) {
			sum += sidSumSidBuffer[i]; // accumulate sequence ids
		}
		return sum;
	}

	/**
	 * Checks whether there is a backward extension by comparing, per sequence, the
	 * first matching position of a pattern and one of its sub-patterns, given they
	 * occur in exactly the same sequences.
	 *
	 * @param bitmap1 bitmap of the larger pattern
	 * @param bitmap2 bitmap of the smaller pattern
	 * @return true if a backward extension exists
	 */
	private boolean isThereBackwardExtension(BitmapSPAM bitmap1, BitmapSPAM bitmap2) {
		// collect the sequences and first matching position of both patterns
		int occ1 = bitmap1.collectOccupiedSidsAndFirstBit(sidSumSidBuffer, sidSumFirstBitBuffer, sidResolver);
		int occ2 = bitmap2.collectOccupiedSidsAndFirstBit(sidScratchBuffer, firstBitScratchBuffer, sidResolver);
		if (occ1 != occ2)
			return false; // must occur in exactly the same sequences
		for (int i = 0; i < occ1; i++) {
			if (sidSumSidBuffer[i] != sidScratchBuffer[i])
				return false;
			if (sidSumFirstBitBuffer[i] > firstBitScratchBuffer[i])
				return false; // larger pattern must not start later
		}
		return true;
	}

	/**
	 * Checks if the itemset pattern1 strictly contains the itemset pattern2.
	 * Chooses the maxgap-aware or maxgap-free containment check depending on
	 * whether {@link #maxGap} is active below {@link #MAXGAP_EXACT_CHECK_THRESHOLD}.
	 *
	 * @param pattern1 the containing pattern, as a list of itemsets
	 * @param pattern2 the contained pattern, as a list of itemsets
	 * @return true if pattern1 strictly contains pattern2
	 */
	private boolean strictlyContainsPattern(List<int[]> pattern1, List<int[]> pattern2) {
		if (maxGapActivated && maxGap < MAXGAP_EXACT_CHECK_THRESHOLD) {
			return strictlyContainsWithMaxgap(pattern1, pattern2);
		} else {
			return strictlyContainsWithoutMaxgap(pattern1, pattern2);
		}
	}

	/**
	 * Converts a flat token buffer (items with -1 separators) into a list of
	 * itemsets, without any autoboxing.
	 *
	 * @param buffer the flat buffer
	 * @param length the number of valid tokens in the buffer
	 * @return a list of itemsets, each as an int array
	 */
	private static List<int[]> flatToItemsets(int[] buffer, int length) {
		List<int[]> itemsets = new ArrayList<>();
		int start = 0;
		for (int i = 0; i < length; i++) {
			if (buffer[i] == -1) {
				// itemset separator: close the current itemset (if non-empty)
				if (i > start) {
					itemsets.add(Arrays.copyOfRange(buffer, start, i));
				}
				start = i + 1;
			}
		}
		if (start < length) {
			itemsets.add(Arrays.copyOfRange(buffer, start, length)); // add the last itemset
		}
		return itemsets;
	}

	/**
	 * Checks whether every item in b is present in a.
	 *
	 * @param a the containing array
	 * @param b the contained array
	 * @return true if a contains all items of b
	 */
	private static boolean intArrayContainsAll(int[] a, int[] b) {
		for (int x : b) {
			boolean found = false;
			for (int y : a) {
				if (y == x) {
					found = true;
					break;
				} // item found in a
			}
			if (!found)
				return false;
		}
		return true;
	}

	/**
	 * Checks if pattern1 strictly contains pattern2 without considering maxgap.
	 *
	 * @param pattern1 the first pattern as a list of itemsets
	 * @param pattern2 the second pattern as a list of itemsets
	 * @return true if pattern1 strictly contains pattern2
	 */
	private boolean strictlyContainsWithoutMaxgap(List<int[]> pattern1, List<int[]> pattern2) {
		int i = 0, j = 0;
		while (true) {
			if (intArrayContainsAll(pattern1.get(j), pattern2.get(i))) {
				i++; // matched one more itemset of pattern2
				if (i == pattern2.size())
					return true;
			}
			j++;
			if (j >= pattern1.size())
				return false;
			if ((pattern1.size() - j) < pattern2.size() - i)
				return false; // not enough itemsets left to match
		}
	}

	/**
	 * Checks if pattern1 strictly contains pattern2 considering the maxgap
	 * constraint.
	 *
	 * @param pattern1 the first pattern as a list of itemsets
	 * @param pattern2 the second pattern as a list of itemsets
	 * @return true if pattern1 strictly contains pattern2
	 */
	private boolean strictlyContainsWithMaxgap(List<int[]> pattern1, List<int[]> pattern2) {
		// try every possible starting position in pattern1
		for (int pos1 = 0; pos1 < pattern1.size(); pos1++) {
			if (strictlyContainsWithMaxGapHelper(pattern1, pattern2, pos1, 0, -1)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Helper method to check containment with maxgap from given positions in
	 * pattern1 and pattern2.
	 *
	 * @param pattern1                       the first pattern
	 * @param pattern2                       the second pattern
	 * @param pos1                           the current position in pattern1
	 * @param pos2                           the current position in pattern2
	 * @param lastMatchingPositionOfPattern1 the last matched position in pattern1,
	 *                                       or -1 if none
	 * @return true if a match is found
	 */
	private boolean strictlyContainsWithMaxGapHelper(List<int[]> pattern1, List<int[]> pattern2, int pos1, int pos2,
			int lastMatchingPositionOfPattern1) {
		int maxPos1 = pattern1.size() - 1;
		if (lastMatchingPositionOfPattern1 >= 0) {
			if (maxPos1 > (lastMatchingPositionOfPattern1 + maxGap)) {
				maxPos1 = lastMatchingPositionOfPattern1 + maxGap; // limit the search window by maxgap
			}
		}
		for (int i = pos1; i <= maxPos1; i++) {
			if (intArrayContainsAll(pattern1.get(i), pattern2.get(pos2))) {
				int nextPos2 = pos2 + 1;
				if (nextPos2 == pattern2.size())
					return true;
				if ((pattern1.size() - i) < pattern2.size() - nextPos2)
					return false;
				if (strictlyContainsWithMaxGapHelper(pattern1, pattern2, i + 1, nextPos2, i)) {
					return true; // recurse to match the remaining itemsets
				}
			}
		}
		return false;
	}

	/**
	 * Flushes the in-memory output buffer to the writer once it exceeds the flush
	 * threshold.
	 *
	 * @throws IOException if an error occurs while writing to the output
	 */
	private void maybeFlushOutputBuffer() throws IOException {
		if (outputBuffer.length() >= OUTPUT_BUFFER_FLUSH_THRESHOLD) {
			writer.write(outputBuffer.toString());
			outputBuffer.setLength(0);
		}
	}

	/**
	 * Writes all stored generator patterns to the output file via the buffered
	 * writer.
	 *
	 * <p>
	 * Note: this method assumes that {@link #translatePatternsToOriginalIds()} has
	 * already been called, so that {@code pattern.buffer} contains the original
	 * item ids from the input file (not the internal dense ids).
	 * </p>
	 *
	 * <p>
	 * The {@link #minimumPatternLength} constraint is enforced here, at output
	 * time, by comparing it against {@code pattern.itemCount}: every discovered
	 * generator is kept internally regardless of its length (shorter generators may
	 * still be needed while checking whether longer patterns discovered later are
	 * themselves generators), but only patterns meeting the minimum length are
	 * actually written out. As a side effect, {@link #patternCount} is recomputed
	 * here to reflect the number of patterns actually written, so that
	 * {@link #printStatistics()} reports accurate figures.
	 * </p>
	 *
	 * @throws IOException if an error occurs while writing to the output
	 */
	private void writeResultTofile() throws IOException {
		patternCount = 0;
		for (Map<Integer, List<GeneratorPattern>> level : generatorPatterns) {
			for (List<GeneratorPattern> patterns : level.values()) {
				for (GeneratorPattern pattern : patterns) {
					// enforce the minimum pattern length constraint at output time
					if (pattern.itemCount < minimumPatternLength) {
						continue;
					}

					patternBuilder.setLength(0);
					if (pattern.length == 0) {
						// the empty set has no items to print
						patternBuilder.append("#SUP: ").append(pattern.support);
					} else {
						// items are already expressed using their original ids, see
						// translatePatternsToOriginalIds()
						for (int i = 0; i < pattern.length; i++) {
							int t = pattern.buffer[i];
							if (t == -1) {
								patternBuilder.append("-1 ");
							} else {
								patternBuilder.append(t).append(' ');
							}
						}
						patternBuilder.append("-1 #SUP: ").append(pattern.support);
					}
					if (outputSequenceIdentifiers && pattern.bitmap != null) {
						patternBuilder.append(" #SID: ").append(pattern.bitmap.getSIDs(sidResolver));
					}
					outputBuffer.append(patternBuilder).append('\n');
					patternCount++;
					maybeFlushOutputBuffer();
				}
			}
		}
	}

	/**
	 * Prints statistics about the last execution of the algorithm to standard
	 * output.
	 */
	public void printStatistics() {
		System.out.println("=============  VGEN2 v2.70 - STATISTICS =============\n" + " Total time ~ "
				+ (endTime - startTime) + " ms\n" + " Frequent sequences count : " + patternCount + "\n"
				+ " Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory() + "\n" + " minsup " + minsup + "\n"
				+ " maxItemId (original ID space) " + maxItemId + " | frequentItemCount (dense vdbArray size) "
				+ frequentItemCount + " | numSequences " + sequencesSize.length + "\n"
				+ " onlySingletonItemsets (I-step skipped entirely) : " + onlySingletonItemsets + "\n"
				+ "===================================================");
	}

	/** @return the maximum allowed pattern length */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/** @param maximumPatternLength the maximum allowed pattern length */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/** @param minimumPatternLength the minimum required pattern length */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * @param maxGap the maximum allowed gap between consecutive elements of a
	 *               pattern
	 */
	public void setMaxGap(int maxGap) {
		this.maxGap = maxGap;
		this.maxGapActivated = true;
	}

	/** @param show whether to include sequence identifiers (SIDs) in the output */
	public void showSequenceIdentifiersInOutput(boolean show) {
		this.outputSequenceIdentifiers = show;
	}
}