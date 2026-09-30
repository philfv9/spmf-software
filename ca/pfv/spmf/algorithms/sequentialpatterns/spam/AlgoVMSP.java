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
import java.util.TreeSet;

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
 * Optimized implementation of the VMSP algorithm (mining maximal sequential
 * patterns) based on the SPAM2 bitmap infrastructure.
 *  @author Philippe Fournier-Viger
 */
public class AlgoVMSP {

	/** Start time of the last execution (in milliseconds). */
	private long startTime;
	/** End time of the last execution (in milliseconds). */
	private long endTime;
	/** Number of maximal patterns found during the last execution. */
	private int patternCount;

	/** Minimum support threshold, expressed as an absolute item count. */
	private int minsup = 0;
	/** Maximum number of itemsets allowed in a pattern. */
	private int maximumPatternLength = 1000;
	/** Maximum allowed gap between consecutive itemsets of a pattern. */
	private int maxGap = Integer.MAX_VALUE;
	/**
	 * Whether the sequence identifiers of each pattern should be written to the
	 * output.
	 */
	private boolean outputSequenceIdentifiers;

	/** Whether a maximum gap constraint has been set by the user. */
	private boolean maxGapActivated = false;

	/** Writer used to write the discovered patterns to the output file. */
	private BufferedWriter writer = null;
	/** In-memory buffer accumulating output text before it is flushed to disk. */
	private final StringBuilder outputBuffer = new StringBuilder(1 << 19);
	/**
	 * Buffer size, in characters, that triggers a flush of {@link #outputBuffer} to
	 * disk.
	 */
	private static final int OUTPUT_BUFFER_FLUSH_THRESHOLD = 1 << 18;
	/** Size of the buffer used when reading the input file. */
	private static final int INPUT_BUFFER_SIZE = 1 << 16;

	/** Vertical database, indexed by contiguous internal item ids. */
	private BitmapSPAM[] vdbArray = null;
	/** Highest original item id seen in the input file. */
	private int maxItemId = 0;
	/** Maps internal item ids back to original ids. */
	private int[] newIdToOriginal = null;

	/** Number of bits (itemset transitions) contained in each input sequence. */
	private int[] sequencesSize = null;
	/** Index of the last bit of each sequence, in the global bit numbering. */
	private int[] sequenceEndBit = null;
	/** Resolves a global bit index to the sequence id it belongs to. */
	private BitmapSPAM.SidResolver sidResolver = null;
	/** Scratch buffer holding the sequence ids occupied by a bitmap. */
	private int[] sidScratchBuffer = null;
	/** Scratch buffer holding the first occupied bit of each occupied sequence. */
	private int[] firstBitScratchBuffer = null;
	
	/**
	 * Whether every frequent itemset in the database contains a single item, so
	 * I-steps can be skipped.
	 */
	private boolean onlySingletonItemsets = true;

	/**
	 * Set during the first database pass if any raw itemset (before frequent-item
	 * pruning) contains more than one item; if false, no frequent itemset can
	 * contain more than one item either, and pass 2 can skip building
	 * {@link #coocMapEquals} entirely.
	 */
	private boolean possibleMultiItemItemsets = false;

	/**
	 * Counter used to trigger periodic memory usage checks during the DFS search.
	 */
	private int memCheckCounter = 0;
	/** Number of DFS calls between two memory usage checks. */
	private static final int MEM_CHECK_INTERVAL = 1000;

	/**
	 * Flattened tokens of every sequence of the input file (itemset separators are
	 * encoded as {@code -1}, sequence separators as {@code -2}, exactly like in the
	 * raw file). The whole database is read from disk exactly once and kept here so
	 * that both preparatory passes of the algorithm (1: compute item support; 2:
	 * build the vertical bitmaps and co-occurrence maps) run against memory instead
	 * of re-reading and re-tokenizing the file. Released (set to {@code null}) as
	 * soon as pass 2 is done, before the DFS search starts.
	 */
	private int[] databaseTokens;
	/**
	 * Start offset (inclusive) into {@link #databaseTokens} of each sequence read
	 * from the input file. Contains one extra trailing sentinel entry equal to the
	 * total number of tokens, so that sequence {@code i} spans
	 * {@code [lineStart[i], lineStart[i + 1])}.
	 */
	private int[] lineStart;
	/** Number of sequences (non-skipped lines) stored in {@link #databaseTokens}. */
	private int lineCount;

	/**
	 * Reusable buffer holding the pattern currently being explored by the DFS
	 * search.
	 */
	private int[] patternBuffer = new int[64];
	/** Number of valid tokens currently stored in {@link #patternBuffer}. */
	private int patternLength = 0;

	/** Pool of reusable buffers for S-step candidates, indexed by DFS depth. */
	private final List<DfsBuffer> sBufferPool = new ArrayList<>();
	/** Pool of reusable buffers for I-step candidates, indexed by DFS depth. */
	private final List<DfsBuffer> iBufferPool = new ArrayList<>();
	/** Number of frequent items (size of the dense internal item id space). */
	private int frequentItemCount = 0;

	/**
	 * For each item, the frequent items appearing in a later itemset of the same
	 * sequence, with counts.
	 */
	private Map<Integer, Map<Integer, Integer>> coocMapAfter = null;
	/**
	 * For each item, the frequent items appearing in the same itemset, with counts.
	 */
	private Map<Integer, Map<Integer, Integer>> coocMapEquals = null;

	/**
	 * Reused across sequences in {@link #updateCoocMaps} to avoid per-sequence
	 * allocation.
	 */
	private final HashSet<Long> seenEqualsScratch = new HashSet<>();
	/**
	 * Reused across sequences in {@link #updateCoocMaps} to avoid per-sequence
	 * allocation.
	 */
	private final HashSet<Long> seenAfterScratch = new HashSet<>();

	/**
	 * Maximal patterns found so far, grouped by pattern length (list index =
	 * pattern length).
	 */
	private List<TreeSet<PatternVMSP2>> maxPatterns = null;

	/**
	 * Reusable per-depth buffer holding candidate items and their bitmaps during
	 * the DFS search.
	 */
	private static class DfsBuffer {
		/** Candidate items at this DFS depth. */
		int[] items;
		/** Bitmaps corresponding to {@link #items}. */
		BitmapSPAM[] bitmaps;

		/**
		 * Creates a buffer with the given initial capacity.
		 *
		 * @param capacity the initial capacity
		 */
		DfsBuffer(int capacity) {
			items = new int[capacity];
			bitmaps = new BitmapSPAM[capacity];
		}

		/**
		 * Grows the buffer to the given capacity.
		 *
		 * @param capacity the new capacity
		 */
		void growTo(int capacity) {
			items = new int[capacity];
			bitmaps = new BitmapSPAM[capacity];
		}
	}

	/**
	 * Candidate maximal pattern, ordered by descending sum of internal item ids (so
	 * that longer / heavier patterns sort first, enabling early-exit pruning).
	 */
	public static class PatternVMSP2 implements Comparable<PatternVMSP2> {
		/**
		 * Flat representation of the pattern (items with {@code -1} as itemset
		 * separators).
		 */
		int[] buffer;
		/** Number of valid tokens in {@link #buffer}. */
		int length;
		/** Sum of the even-numbered items in the pattern. */
		int sumOfEvenItems;
		/** Sum of the odd-numbered items in the pattern. */
		int sumOfOddItems;
		/**
		 * Cached sum of {@link #sumOfEvenItems} and {@link #sumOfOddItems}, computed
		 * once at creation time.
		 */
		int totalSum;
		/** Support of the pattern. */
		int support;
		/** Bitmap of the pattern, kept only if sequence identifiers must be output. */
		BitmapSPAM bitmap;
		/**
		 * Itemset structure of this pattern, computed once and reused for subsumption
		 * checks.
		 */
		int[][] itemsets;

		/**
		 * Compares two patterns by descending total item-id sum, then lexicographically
		 * by content and length.
		 *
		 * @param o the other pattern
		 * @return a negative, zero, or positive value as required by {@link Comparable}
		 */
		@Override
		public int compareTo(PatternVMSP2 o) {
			if (o == this)
				return 0;
			int compare = o.totalSum - this.totalSum;
			if (compare != 0)
				return compare;

			int minLen = Math.min(this.length, o.length);
			for (int i = 0; i < minLen; i++) {
				int cmp = Integer.compare(this.buffer[i], o.buffer[i]);
				if (cmp != 0)
					return cmp;
			}
			return Integer.compare(this.length, o.length);
		}

		/**
		 * Checks whether this pattern contains the given item.
		 *
		 * @param item the item to look for
		 * @return true if the pattern contains the item, false otherwise
		 */
		boolean containsItem(int item) {
			for (int i = 0; i < length; i++) {
				if (buffer[i] == item)
					return true;
			}
			return false;
		}

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

	/**
	 * Constructs a new instance of the VMSP2 algorithm.
	 */
	public AlgoVMSP() {
	}

	/**
	 * Runs the VMSP2 algorithm on an input file and writes the maximal sequential
	 * patterns found to an output file.
	 *
	 * @param input          the input file path
	 * @param outputFilePath the output file path
	 * @param minsupRel      the minimum support threshold, as a relative value
	 *                       between 0 and 1
	 * @return the maximal patterns found, grouped by pattern length
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public List<TreeSet<PatternVMSP2>> runAlgorithm(String input, String outputFilePath, double minsupRel)
			throws IOException {
		writer = new BufferedWriter(new FileWriter(outputFilePath), 1 << 16);
		writer.write("@FILETYPE=\"Frequent maximal sequential patterns\"");
		writer.append(System.lineSeparator());
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.append(System.lineSeparator());
		
		patternCount = 0;
		memCheckCounter = 0;
		outputBuffer.setLength(0);
		MemoryLogger.getInstance().reset();

		startTime = System.currentTimeMillis();
		try {
			vmsp(input, minsupRel); // mine the maximal patterns
			convertPatternsToOriginalIDs(); // convert internal IDs back to original IDs
			writeResultTofile(); // write them to the output file
		} finally {
			if (outputBuffer.length() > 0) {
				writer.write(outputBuffer.toString());
				outputBuffer.setLength(0);
			}
			writer.close();
			clearBufferPools();
			// release the in-memory database in case an exception interrupted mining
			// before it was already released at the end of the normal pass-2 flow
			databaseTokens = null;
			lineStart = null;
		}
		endTime = System.currentTimeMillis();
		return maxPatterns;
	}

	/**
	 * Converts internal item IDs in all stored patterns back to original item IDs.
	 * This ensures that patterns returned to the user contain the original item
	 * names rather than the renamed (optimized) internal IDs.
	 */
	private void convertPatternsToOriginalIDs() {
		if (maxPatterns == null || newIdToOriginal == null)
			return;

		for (TreeSet<PatternVMSP2> level : maxPatterns) {
			if (level == null)
				continue;
			for (PatternVMSP2 pattern : level) {
				// Convert each item in the pattern buffer from internal ID to original ID
				for (int i = 0; i < pattern.length; i++) {
					int item = pattern.buffer[i];
					// Only convert actual items, not separators (-1)
					if (item != -1) {
						pattern.buffer[i] = newIdToOriginal[item];
					}
				}

				// Also update the itemsets array if it exists
				if (pattern.itemsets != null) {
					for (int[] itemset : pattern.itemsets) {
						for (int j = 0; j < itemset.length; j++) {
							itemset[j] = newIdToOriginal[itemset[j]];
						}
					}
				}

				// Recalculate sums based on original IDs
				pattern.sumOfEvenItems = 0;
				pattern.sumOfOddItems = 0;
				for (int i = 0; i < pattern.length; i++) {
					int item = pattern.buffer[i];
					if (item != -1) {
						if (item % 2 == 0) {
							pattern.sumOfEvenItems += item;
						} else {
							pattern.sumOfOddItems += item;
						}
					}
				}
				pattern.totalSum = pattern.sumOfEvenItems + pattern.sumOfOddItems;
			}
		}
	}

	/**
	 * Clears the DFS buffer pools to release references to bitmaps once mining is
	 * complete.
	 */
	private void clearBufferPools() {
		sBufferPool.clear();
		iBufferPool.clear();
	}

	/**
	 * Checks whether a line from the input file should be ignored (empty line or a
	 * comment/metadata line).
	 *
	 * @param line the line to check
	 * @return true if the line should be skipped, false otherwise
	 */
	private static boolean isSkippableLine(String line) {
		return line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@';
	}

	/**
	 * Reads the whole input file exactly once, tokenizes every non-skippable line
	 * and appends the resulting tokens (unchanged, including the {@code -1} /
	 * {@code -2} separators) to {@link #databaseTokens}, recording the start offset
	 * of each line in {@link #lineStart}. This allows the two preparatory passes of
	 * {@link #vmsp(String, double)} to operate on memory instead of scanning the
	 * file from disk twice.
	 *
	 * @param input the input file path
	 * @throws IOException if an error occurs while reading the input file
	 */
	private void readDatabaseIntoMemory(String input) throws IOException {
		databaseTokens = new int[1 << 16];
		lineStart = new int[1024];
		lineCount = 0;
		int tokenTotal = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))),
				INPUT_BUFFER_SIZE)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (isSkippableLine(line))
					continue;

				if (lineCount == lineStart.length - 1) { // keep room for the trailing sentinel
					lineStart = Arrays.copyOf(lineStart, lineStart.length * 2);
				}
				lineStart[lineCount++] = tokenTotal;

				tokenTotal = tokenizeLineInto(line, tokenTotal);
			}
		}

		// trailing sentinel = total token count, so that line i spans
		// [lineStart[i], lineStart[i + 1])
		if (lineCount == lineStart.length) {
			lineStart = Arrays.copyOf(lineStart, lineStart.length + 1);
		}
		lineStart[lineCount] = tokenTotal;
	}

	/**
	 * Tokenizes a line directly into {@link #databaseTokens}, starting at the given
	 * offset, growing the array as needed.
	 *
	 * @param line   the line to tokenize
	 * @param offset the offset in {@link #databaseTokens} at which to start writing
	 *               tokens
	 * @return the new total token count (offset just past the last token written)
	 */
	private int tokenizeLineInto(String line, int offset) {
		int[] buffer = databaseTokens;
		int length = line.length();
		int count = offset;
		int i = 0;
		while (i < length) {
			while (i < length && line.charAt(i) == ' ')
				i++;
			if (i >= length)
				break;

			boolean neg = false;
			if (line.charAt(i) == '-') {
				neg = true;
				i++;
			}

			int val = 0;
			while (i < length && line.charAt(i) != ' ') {
				val = val * 10 + (line.charAt(i) - '0');
				i++;
			}
			if (count == buffer.length) {
				buffer = Arrays.copyOf(buffer, buffer.length * 2);
			}
			buffer[count++] = neg ? -val : val;
		}
		databaseTokens = buffer;
		return count;
	}

	/**
	 * Grows {@link #patternBuffer} if needed so that it can hold at least the given
	 * capacity.
	 *
	 * @param minCapacity the minimum required capacity
	 */
	private void ensurePatternCapacity(int minCapacity) {
		if (minCapacity > patternBuffer.length) {
			patternBuffer = Arrays.copyOf(patternBuffer, Math.max(minCapacity, patternBuffer.length * 2));
		}
	}

	/**
	 * Runs the VMSP2 mining process: reads the input file once into memory, builds
	 * the vertical database and co-occurrence maps from that in-memory
	 * representation, then performs the DFS search for maximal sequential
	 * patterns.
	 *
	 * @param input     the path of the input file
	 * @param minsupRel the minimum support threshold, as a relative value between 0
	 *                  and 1
	 * @throws IOException if an error occurs while reading the input file
	 */
	private void vmsp(String input, double minsupRel) throws IOException {

		maxPatterns = new ArrayList<>(20);
		maxPatterns.add(null);
		maxPatterns.add(new TreeSet<>());

		// ---------------------------------------------------------------------
		// Read the file from disk exactly once and keep it in memory (as int[])
		// so that the two passes below do not need to re-read / re-tokenize it.
		// ---------------------------------------------------------------------
		readDatabaseIntoMemory(input);

		ArrayListInt sequencesSizeList = new ArrayListInt();

		int initialCapacity = 1024;
		int[] itemSupport = new int[initialCapacity];
		int[] itemLastSid = new int[initialCapacity];
		Arrays.fill(itemLastSid, -1);
		maxItemId = 0;
		possibleMultiItemItemsets = false;

		int lastBitIndex = 0;

		// ---- pass 1 (in memory): compute per-item support and detect
		// multi-item itemsets ----
		{
			int bitIndex = 0;
			int sid = 0;

			for (int lineIdx = 0; lineIdx < lineCount; lineIdx++) {
				int start = lineStart[lineIdx];
				int end = lineStart[lineIdx + 1];

				sequencesSizeList.add(bitIndex);

				int itemsInCurrentItemset = 0;

				for (int t = start; t < end; t++) {
					int token = databaseTokens[t];
					if (token == -1) { // end of itemset
						bitIndex++;
						itemsInCurrentItemset = 0;
					} else if (token == -2) { // end of sequence
						sid++;
					} else {
						itemsInCurrentItemset++;
						if (itemsInCurrentItemset > 1) {
							possibleMultiItemItemsets = true;
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

						if (itemLastSid[token] != sid) { // count support once per sequence
							itemSupport[token]++;
							itemLastSid[token] = sid;
						}
					}
				}
			}
			lastBitIndex = bitIndex - 1;
		}

		sequencesSize = new int[sequencesSizeList.size()];
		for (int idx = 0; idx < sequencesSize.length; idx++) {
			sequencesSize[idx] = sequencesSizeList.get(idx);
		}
		sequencesSizeList = null;

		int numSequences = sequencesSize.length;

		minsup = (int) Math.ceil((minsupRel * numSequences) - 0.000001);
		if (minsup == 0)
			minsup = 1;

		sequenceEndBit = BitmapSPAM.buildSequenceEndBit(sequencesSize, lastBitIndex, numSequences);
		sidResolver = new BitmapSPAM.SidResolver(sequencesSize, sequenceEndBit);
		sidScratchBuffer = new int[Math.max(numSequences, 1)];
		firstBitScratchBuffer = new int[Math.max(numSequences, 1)];

		// build frequent-item remapping (original id -> dense internal id, or -1 if
		// infrequent)
		ArrayListInt frequentOriginalIdsList = new ArrayListInt();
		for (int origId = 0; origId <= maxItemId; origId++) {
			int cnt = itemSupport[origId];
			if (cnt >= minsup) {
				frequentOriginalIdsList.add(origId);
				itemSupport[origId] = frequentOriginalIdsList.size() - 1;
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

		vdbArray = new BitmapSPAM[frequentItemCount];
		coocMapEquals = new HashMap<>(frequentItemCount);
		coocMapAfter = new HashMap<>(frequentItemCount);

		boolean sawMultiItemFrequentItemset = false;

		int sid2 = 0;
		List<int[]> itemsetsInSeq = new ArrayList<>();
		int[] currentItemset = new int[8];

		// ---- pass 2 (in memory): build the vertical bitmaps and the
		// co-occurrence maps ----
		for (int lineIdx = 0; lineIdx < lineCount; lineIdx++) {
			int start = lineStart[lineIdx];
			int end = lineStart[lineIdx + 1];

			int tid2 = 0;

			itemsetsInSeq.clear();
			int currentItemsetSize = 0;

			for (int t = start; t < end; t++) {
				int token = databaseTokens[t];
				if (token == -1) { // end of itemset
					tid2++;
					if (currentItemsetSize > 0) {
						if (currentItemsetSize > 1)
							sawMultiItemFrequentItemset = true;
						itemsetsInSeq.add(Arrays.copyOf(currentItemset, currentItemsetSize));
						currentItemsetSize = 0;
					}
				} else if (token == -2) { // end of sequence
					sid2++;
					tid2 = 0;
				} else {
					int newId = (token < remap.length) ? remap[token] : -1;
					if (newId >= 0) { // keep only frequent items
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
				if (currentItemsetSize > 1)
					sawMultiItemFrequentItemset = true;
				itemsetsInSeq.add(Arrays.copyOf(currentItemset, currentItemsetSize));
			}

			updateCoocMaps(itemsetsInSeq);
		}

		// The raw tokenized database is no longer needed once the vertical bitmaps
		// and co-occurrence maps have been built: release it immediately so its
		// memory can be reclaimed before the (potentially memory-hungry) DFS search
		// starts.
		databaseTokens = null;
		lineStart = null;

		onlySingletonItemsets = !sawMultiItemFrequentItemset;

		remap = null;

		int[] frequentItemsArr = new int[frequentItemCount];
		for (int i = 0; i < frequentItemCount; i++)
			frequentItemsArr[i] = i;

		// DFS root: try each frequent item as a length-1 seed pattern
		for (int idx = 0; idx < frequentItemCount; idx++) {
			BitmapSPAM bm = vdbArray[idx];
			boolean itemIsEven = (idx % 2 == 0);

			patternLength = 0;
			ensurePatternCapacity(1);
			patternBuffer[patternLength++] = idx;

			boolean hasExtension = false;
			if (maximumPatternLength > 1) {
				hasExtension = dfsPruning(bm, frequentItemsArr, frequentItemCount, frequentItemsArr, frequentItemCount,
						idx, 2, 0, idx);
			}

			if (!hasExtension) {
				savePatternSingleItem(idx, bm, itemIsEven);
			}
		}
	}

	/**
	 * Updates the co-occurrence maps ({@link #coocMapEquals} and
	 * {@link #coocMapAfter}) using the itemsets of one sequence.
	 *
	 * @param itemsetsInSeq the itemsets making up the current sequence
	 */
	private void updateCoocMaps(List<int[]> itemsetsInSeq) {
		int n = itemsetsInSeq.size();

		// skip if pass 1 found no itemset with more than one item, since pruning can
		// only remove items
		if (possibleMultiItemItemsets) {
			seenEqualsScratch.clear();
			// record items co-occurring within the same itemset
			for (int p = 0; p < n; p++) {
				int[] isP = itemsetsInSeq.get(p);
				for (int a = 0; a < isP.length; a++) {
					for (int b = a + 1; b < isP.length; b++) {
						int ia = isP[a], ib = isP[b];
						long key = ((long) ia << 32) | (ib & 0xFFFFFFFFL);
						if (seenEqualsScratch.add(key)) {
							coocMapEquals.computeIfAbsent(ia, k -> new HashMap<>()).merge(ib, 1, Integer::sum);
						}
					}
				}
			}
		}

		// record items occurring in a later itemset of the same sequence
		seenAfterScratch.clear();
		for (int p = 0; p < n; p++) {
			int[] isP = itemsetsInSeq.get(p);
			for (int q = p + 1; q < n; q++) {
				int[] isQ = itemsetsInSeq.get(q);
				for (int ia : isP) {
					for (int ib : isQ) {
						long key = ((long) ia << 32) | (ib & 0xFFFFFFFFL);
						if (seenAfterScratch.add(key)) {
							coocMapAfter.computeIfAbsent(ia, k -> new HashMap<>()).merge(ib, 1, Integer::sum);
						}
					}
				}
			}
		}
	}

	/**
	 * Gets the reusable DFS buffer for a given depth, growing it if necessary.
	 *
	 * @param pool     the buffer pool to use (S-step or I-step)
	 * @param depth    the current DFS depth
	 * @param capacity the minimum required capacity
	 * @return the buffer to use at this depth
	 */
	private DfsBuffer getBuffer(List<DfsBuffer> pool, int depth, int capacity) {
		if (capacity < 1)
			capacity = 1;
		while (pool.size() <= depth)
			pool.add(new DfsBuffer(capacity));
		DfsBuffer buf = pool.get(depth);
		if (buf.items.length < capacity)
			buf.growTo(capacity);
		return buf;
	}

	/**
	 * Finds the index of the first element strictly greater than {@code value} in a
	 * sorted array.
	 *
	 * @param arr   the sorted array
	 * @param count the number of valid elements in the array
	 * @param value the value to search for
	 * @return the index of the first element greater than {@code value}
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
	 * Recursively explores S-step and I-step extensions of the current prefix
	 * pattern, pruning candidates with the co-occurrence maps, and saves patterns
	 * that have no frequent extension.
	 *
	 * @param prefixBitmap               the bitmap of the current prefix pattern
	 * @param sn                         the candidate items for S-step extensions
	 * @param snCount                    the number of valid items in {@code sn}
	 * @param in                         the candidate items for I-step extensions
	 * @param inCount                    the number of valid items in {@code in}
	 * @param hasToBeGreaterThanForIStep the minimum item id allowed for I-step
	 *                                   extensions
	 * @param m                          the total number of items that a child
	 *                                   pattern obtained by extending the current
	 *                                   prefix with one more item would contain
	 *                                   (incremented by exactly one for BOTH
	 *                                   S-step and I-step extensions, since either
	 *                                   kind of step adds exactly one item to the
	 *                                   pattern). This value is used both to
	 *                                   enforce {@link #maximumPatternLength} and
	 *                                   to index {@link #maxPatterns} by pattern
	 *                                   size: the subsumption-search loops in
	 *                                   {@link #savePatternSingleItem} and
	 *                                   {@link #savePatternMultipleItems} rely on
	 *                                   the invariant that a pattern which
	 *                                   structurally contains another one always
	 *                                   has a strictly greater total item count
	 *                                   (even though it may have the same
	 *                                   itemset count, e.g. an I-step adds an item
	 *                                   to an existing itemset).
	 * @param depth                      the current DFS depth, used to select the
	 *                                   reusable buffers
	 * @param lastAppendedItem           the last item appended to the pattern
	 * @return true if at least one frequent extension was found, false otherwise
	 * @throws IOException if an error occurs while writing output
	 */
	private boolean dfsPruning(BitmapSPAM prefixBitmap, int[] sn, int snCount, int[] in, int inCount,
			int hasToBeGreaterThanForIStep, int m, int depth, int lastAppendedItem) throws IOException {

		boolean atLeastOneFrequentExtension = false;

		Map<Integer, Integer> mapSupportItemsAfter = coocMapAfter.get(lastAppendedItem);

		DfsBuffer sBuf = getBuffer(sBufferPool, depth, snCount);
		int sCount = 0;

		// S-step candidates: skip entirely if lastAppendedItem has no "after"
		// co-occurrence,
		// since every candidate would be rejected anyway
		if (mapSupportItemsAfter != null && snCount > 0) {
			int occCount = (maxGap == Integer.MAX_VALUE)
					? prefixBitmap.collectOccupiedSidsAndFirstBit(sidScratchBuffer, firstBitScratchBuffer, sidResolver)
					: 0;

			BitmapSPAM scratchS = null; // reused bitmap; allocated only when a candidate is kept
			for (int idx = 0; idx < snCount; idx++) {
				int item = sn[idx];

				Integer support = mapSupportItemsAfter.get(item);
				if (support == null || support < minsup)
					continue; // CMAP pruning

				BitmapSPAM itemBitmap = vdbArray[item];
				if (scratchS == null) {
					scratchS = new BitmapSPAM();
				}
				scratchS.resetForReuse(itemBitmap.wordCount);

				prefixBitmap.createNewBitmapSStep(scratchS, itemBitmap, maxGap, sidResolver, sequenceEndBit,
						sidScratchBuffer, firstBitScratchBuffer, occCount);

				if (scratchS.getSupportWithoutGapTotal() >= minsup) {
					sBuf.items[sCount] = item;
					sBuf.bitmaps[sCount] = scratchS;
					sCount++;
					scratchS = null; // consumed; next candidate gets a fresh bitmap
				}
			}
		}

		// recurse on each frequent S-step extension
		for (int k = 0; k < sCount; k++) {
			int item = sBuf.items[k];
			BitmapSPAM newBitmap = sBuf.bitmaps[k];

			if (newBitmap.getSupport() >= minsup) {
				int savedLen = patternLength;
				ensurePatternCapacity(patternLength + 2);
				if (patternLength > 0)
					patternBuffer[patternLength++] = -1; // itemset separator
				patternBuffer[patternLength++] = item;

				boolean hasFrequentExtension;
				if (maximumPatternLength > m) {
					hasFrequentExtension = dfsPruning(newBitmap, sBuf.items, sCount, sBuf.items, sCount, item, m + 1,
							depth + 1, item);
				} else {
					hasFrequentExtension = false;
				}

				atLeastOneFrequentExtension = true;
				if (!hasFrequentExtension) {
					savePatternMultipleItems(newBitmap, m); // no extension found: save as maximal
				}
				patternLength = savedLen;
			}
		}

		// I-step candidates (skipped entirely for singleton-only databases)
		if (!onlySingletonItemsets) {
			Map<Integer, Integer> mapSupportItemsEquals = coocMapEquals.get(lastAppendedItem);

			// skip if lastAppendedItem has no "equals" co-occurrence with any item
			if (mapSupportItemsEquals != null) {
				DfsBuffer iBuf = getBuffer(iBufferPool, depth, inCount);
				int iCount = 0;

				int startIdx = upperBound(in, inCount, hasToBeGreaterThanForIStep); // avoid duplicate itemsets

				BitmapSPAM scratchI = null; // reused bitmap; allocated only when a candidate is kept
				for (int idx = startIdx; idx < inCount; idx++) {
					int item = in[idx];

					Integer support = mapSupportItemsEquals.get(item);
					if (support == null || support < minsup)
						continue; // CMAP pruning

					BitmapSPAM itemBitmap = vdbArray[item];
					if (scratchI == null) {
						scratchI = new BitmapSPAM();
					}
					scratchI.resetForReuse(Math.min(prefixBitmap.wordCount, itemBitmap.wordCount));

					prefixBitmap.createNewBitmapIStep(scratchI, itemBitmap, sidResolver);

					if (scratchI.getSupport() >= minsup) {
						iBuf.items[iCount] = item;
						iBuf.bitmaps[iCount] = scratchI;
						iCount++;
						scratchI = null; // consumed; next candidate gets a fresh bitmap
					}
				}

				// recurse on each frequent I-step extension (an I-step adds exactly one item,
				// just like an S-step, so "m" is advanced in exactly the same way)
				for (int k = 0; k < iCount; k++) {
					int item = iBuf.items[k];
					BitmapSPAM newBitmap = iBuf.bitmaps[k];

					int savedLen = patternLength;
					ensurePatternCapacity(patternLength + 1);
					patternBuffer[patternLength++] = item;

					boolean hasFrequentExtension;
					if (maximumPatternLength > m) {
						hasFrequentExtension = dfsPruning(newBitmap, sBuf.items, sCount, iBuf.items, iCount, item,
								m + 1, depth + 1, item);
					} else {
						hasFrequentExtension = false;
					}

					atLeastOneFrequentExtension = true;
					if (!hasFrequentExtension) {
						savePatternMultipleItems(newBitmap, m); // no extension found: save as maximal
					}
					patternLength = savedLen;
				}
			}
		}

		// periodic memory usage check
		if (++memCheckCounter >= MEM_CHECK_INTERVAL) {
			memCheckCounter = 0;
			MemoryLogger.getInstance().checkMemory();
		}

		return atLeastOneFrequentExtension;
	}

	/**
	 * Saves a single-item pattern as maximal, unless it is already subsumed by a
	 * longer stored pattern.
	 *
	 * @param item       the item forming the pattern
	 * @param bitmap     the bitmap of the pattern
	 * @param itemIsEven whether the item id is even
	 * @return true if the pattern is subsumed by an existing pattern, false if it
	 *         was saved
	 */
	private boolean savePatternSingleItem(int item, BitmapSPAM bitmap, boolean itemIsEven) {
		int support = bitmap.getSupport();

		// check whether a longer stored pattern already subsumes this item
		for (int i = maxPatterns.size() - 1; i > 1; i--) {
			for (PatternVMSP2 pPrime : maxPatterns.get(i)) {
				if (pPrime.totalSum < item)
					break;
				boolean supportCheck = maxGapActivated || support >= pPrime.support;
				int relevantSum = itemIsEven ? pPrime.sumOfEvenItems : pPrime.sumOfOddItems;
				if (relevantSum >= item && supportCheck && pPrime.containsItem(item)) {
					return true;
				}
			}
		}

		// not subsumed: store as a new maximal pattern
		patternCount++;
		PatternVMSP2 pattern = new PatternVMSP2();
		pattern.buffer = new int[] { item };
		pattern.length = 1;
		pattern.sumOfEvenItems = itemIsEven ? item : 0;
		pattern.sumOfOddItems = itemIsEven ? 0 : item;
		pattern.totalSum = item;
		pattern.support = support;
		pattern.itemsets = new int[][] { { item } };
		if (outputSequenceIdentifiers)
			pattern.bitmap = bitmap;
		maxPatterns.get(1).add(pattern);
		return false;
	}

	/**
	 * Saves the pattern currently stored in {@link #patternBuffer} as maximal,
	 * unless it is subsumed by an already-stored longer pattern; also removes any
	 * shorter stored patterns that this new pattern subsumes.
	 *
	 * @param bitmap the bitmap of the pattern
	 * @param length the total number of items in the pattern (see the
	 *               documentation of parameter {@code m} in
	 *               {@link #dfsPruning}) 
	 * @return true if the pattern is subsumed by an existing pattern, false if it
	 *         was saved
	 */
	private boolean savePatternMultipleItems(BitmapSPAM bitmap, int length) {
		int sumEven = 0, sumOdd = 0;
		for (int i = 0; i < patternLength; i++) {
			int t = patternBuffer[i];
			if (t != -1) {
				if (t % 2 == 0)
					sumEven += t;
				else
					sumOdd += t;
			}
		}
		int support = bitmap.getSupport();
		int totalSum = sumEven + sumOdd;

		// parse the itemset structure once, reused for all subsumption checks below
		int[][] newItemsets = flatToItemsetsArray(patternBuffer, patternLength);

		// check whether an already-stored longer pattern subsumes this one
		for (int i = maxPatterns.size() - 1; i > length; i--) {
			for (PatternVMSP2 pPrime : maxPatterns.get(i)) {
				if (pPrime.totalSum < totalSum)
					break;
				boolean supportCheck = maxGapActivated || support >= pPrime.support;
				if (sumEven <= pPrime.sumOfEvenItems && sumOdd <= pPrime.sumOfOddItems && supportCheck
						&& containsWithoutGap(pPrime.itemsets, newItemsets)) {
					return true;
				}
			}
		}

		// remove any already-stored shorter patterns subsumed by this new one
		for (int i = 1; i < length && i < maxPatterns.size(); i++) {
			Iterator<PatternVMSP2> iter = maxPatterns.get(i).descendingIterator();
			while (iter.hasNext()) {
				PatternVMSP2 pPrime = iter.next();
				if (pPrime.totalSum > totalSum)
					break;
				boolean supportCheck = maxGapActivated || support <= pPrime.support;
				if (sumEven >= pPrime.sumOfEvenItems && sumOdd >= pPrime.sumOfOddItems && supportCheck
						&& containsWithoutGap(newItemsets, pPrime.itemsets)) {
					patternCount--;
					iter.remove();
				}
			}
		}

		while (maxPatterns.size() - 1 < length) {
			maxPatterns.add(new TreeSet<>());
		}

		// not subsumed: store as a new maximal pattern
		patternCount++;
		PatternVMSP2 pattern = new PatternVMSP2();
		pattern.buffer = Arrays.copyOf(patternBuffer, patternLength);
		pattern.length = patternLength;
		pattern.sumOfEvenItems = sumEven;
		pattern.sumOfOddItems = sumOdd;
		pattern.totalSum = totalSum;
		pattern.support = support;
		pattern.itemsets = newItemsets;
		if (outputSequenceIdentifiers)
			pattern.bitmap = bitmap;
		maxPatterns.get(length).add(pattern);
		return false;
	}

	/**
	 * Converts a flat token array (items with {@code -1} separators) into an array
	 * of itemsets.
	 *
	 * @param buffer the flat token array
	 * @param length the number of valid tokens in {@code buffer}
	 * @return the itemsets extracted from the token array
	 */
	private static int[][] flatToItemsetsArray(int[] buffer, int length) {
		int itemsetCount = 0;
		int cur = 0;
		for (int i = 0; i < length; i++) {
			if (buffer[i] == -1) {
				itemsetCount++;
				cur = 0;
			} else {
				cur++;
			}
		}
		boolean hasTrailing = cur > 0;
		if (hasTrailing)
			itemsetCount++;

		int[][] itemsets = new int[itemsetCount][];
		int idx = 0;
		int start = 0;
		int size = 0;
		for (int i = 0; i < length; i++) {
			if (buffer[i] == -1) {
				itemsets[idx] = new int[size];
				System.arraycopy(buffer, start, itemsets[idx], 0, size);
				idx++;
				start = i + 1;
				size = 0;
			} else {
				size++;
			}
		}
		if (hasTrailing) {
			itemsets[idx] = new int[size];
			System.arraycopy(buffer, start, itemsets[idx], 0, size);
		}
		return itemsets;
	}

	/**
	 * Checks whether array {@code a} contains all the elements of array {@code b}.
	 *
	 * @param a the array to search in
	 * @param b the array of elements to look for
	 * @return true if all elements of {@code b} are found in {@code a}, false
	 *         otherwise
	 */
	private static boolean intArrayContainsAll(int[] a, int[] b) {
		// fast path for the common case of a single-item itemset
		if (b.length == 1) {
			int x = b[0];
			for (int y : a) {
				if (y == x)
					return true;
			}
			return false;
		}
		for (int x : b) {
			boolean found = false;
			for (int y : a) {
				if (y == x) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	/**
	 * Gap-unaware subsequence test: checks whether {@code pattern2} is a
	 * subsequence of {@code pattern1}. Used for pattern-vs-pattern structural
	 * subsumption in {@link #savePatternMultipleItems}.
	 *
	 * @param pattern1 the candidate super-pattern
	 * @param pattern2 the candidate sub-pattern
	 * @return true if {@code pattern2} is a subsequence of {@code pattern1}, false
	 *         otherwise
	 */
	private boolean containsWithoutGap(int[][] pattern1, int[][] pattern2) {
		int i = 0, j = 0;
		while (true) {
			if (intArrayContainsAll(pattern1[j], pattern2[i])) {
				i++;
				if (i == pattern2.length)
					return true;
			}
			j++;
			if (j >= pattern1.length)
				return false;
			if ((pattern1.length - j) < pattern2.length - i)
				return false;
		}
	}

	/**
	 * Flushes the output buffer to the writer if it has reached the flush
	 * threshold.
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
	 * Writes all the maximal patterns found to the output file. Note: Patterns have
	 * already been converted to original IDs by convertPatternsToOriginalIDs().
	 *
	 * @throws IOException if an error occurs while writing to the output file
	 */
	private void writeResultTofile() throws IOException {
		StringBuilder patternBuilder = new StringBuilder(64);
		for (TreeSet<PatternVMSP2> level : maxPatterns) {
			if (level == null)
				continue;
			for (PatternVMSP2 pattern : level) {
				patternBuilder.setLength(0);
				// Patterns now contain original IDs, so we write them directly
				for (int i = 0; i < pattern.length; i++) {
					int t = pattern.buffer[i];
					if (t == -1) {
						patternBuilder.append("-1 ");
					} else {
						patternBuilder.append(t).append(' ');
					}
				}
				patternBuilder.append("-1 #SUP: ").append(pattern.support);
				if (outputSequenceIdentifiers && pattern.bitmap != null) {
					patternBuilder.append(" #SID: ").append(pattern.bitmap.getSIDs(sidResolver));
				}
				outputBuffer.append(patternBuilder).append('\n');
				maybeFlushOutputBuffer();
			}
		}
	}

	/**
	 * Prints statistics about the last execution of the algorithm to the console.
	 */
	public void printStatistics() {
		System.out.println("=============  VMSP2 v2.68 - STATISTICS =============\n" + " Total time ~ "
				+ (endTime - startTime) + " ms\n" + " Maximal sequences count : " + patternCount + "\n"
				+ " Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory() + "\n" + " minsup " + minsup + "\n"
				+ " maxItemId (original ID space) " + maxItemId + " | frequentItemCount (dense vdbArray size) "
				+ frequentItemCount + " | numSequences " + sequencesSize.length + "\n"
				+ " onlySingletonItemsets (I-step skipped entirely) : " + onlySingletonItemsets + "\n"
				+ "===================================================");
	}

	/**
	 * Gets the maximum pattern length allowed during mining.
	 *
	 * @return the maximum pattern length
	 */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/**
	 * Sets the maximum pattern length allowed during mining.
	 *
	 * @param maximumPatternLength the maximum number of itemsets in a pattern
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Sets the maximum gap allowed between consecutive itemsets of a pattern.
	 *
	 * @param maxGap the maximum gap
	 */
	public void setMaxGap(int maxGap) {
		this.maxGap = maxGap;
		this.maxGapActivated = true;
	}

	/**
	 * Sets whether the sequence identifiers containing each pattern should be shown
	 * in the output.
	 *
	 * @param show true to show sequence identifiers, false otherwise
	 */
	public void showSequenceIdentifiersInOutput(boolean show) {
		this.outputSequenceIdentifiers = show;
	}
}