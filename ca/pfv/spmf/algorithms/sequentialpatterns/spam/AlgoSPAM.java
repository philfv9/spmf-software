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
import java.util.List;

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
 * Optimized implementation of the SPAM (Sequential PAttern Mining) algorithm.
 * <p>
 * Uses a bitmap-based vertical representation of the database, contiguous
 * internal item-id remapping, and pooled buffers to reduce memory allocation
 * during the depth-first search for frequent sequential patterns. The sequence
 * currently being built by the DFS is kept in a single flat int-array buffer
 * rather than a stack of itemset objects.
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoSPAM {

	/** Time at which the algorithm execution started (milliseconds). */
	private long startTime;
	/** Time at which the algorithm execution ended (milliseconds). */
	private long endTime;
	/** Number of frequent patterns found. */
	private int patternCount;

	/** Minimum support threshold, as an absolute sequence count. */
	private int minsup = 0;
	/** Minimum length (number of itemsets) a pattern must have to be output. */
	private int minimumPatternLength = 0;
	/** Maximum length (number of itemsets) a pattern may have. */
	private int maximumPatternLength = 1000;
	/**
	 * Maximum allowed gap (in itemsets) between consecutive elements of a pattern.
	 */
	private int maxGap = Integer.MAX_VALUE;
	/** Whether to include sequence identifiers (SIDs) in the output. */
	private boolean outputSequenceIdentifiers;

	/**
	 * Optional set of items (in the original item id space, as they appear in the
	 * input file) that must all appear in a pattern for it to be output; null if no
	 * such constraint has been set.
	 */
	private int[] mustItems = null;

	/**
	 * Bitmask indexed by internal (dense) item id marking which items are required
	 * by the "must appear" constraint. Null if no "must items" constraint has been
	 * set.
	 */
	private boolean[] mustItemMask = null;

	/**
	 * Number of distinct required items expected to be found in a pattern (i.e. the
	 * resolved size of {@link #mustItems}); only meaningful when
	 * {@link #mustItemMask} is non-null.
	 */
	private int mustItemsRequiredCount = 0;

	/**
	 * True if every item in {@link #mustItems} is frequent and could therefore
	 * appear in some pattern; false if at least one required item is infrequent,
	 * meaning no pattern can ever satisfy the constraint.
	 */
	private boolean mustItemsPossible = true;

	/**
	 * Running count, during the depth-first search, of how many <em>distinct</em>
	 * required "must items" are currently present in the pattern being built.
	 */
	private int mustItemsMatchedCount = 0;

	/**
	 * Running per-(dense internal) item-id occurrence count of how many times each
	 * required "must item" currently appears in the pattern being built
	 * ({@link #patternBuffer}).
	 */
	private int[] mustItemsOccurrenceCount = null;

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
	 * bitmap. Only allocated when there is no gap constraint (see
	 * {@link #maxGap}), since it is only used by the no-gap fast path in
	 * {@link #dfsPruning}.
	 */
	private int[] sidScratchBuffer = null;

	/**
	 * Shared scratch buffer holding, for each entry in sidScratchBuffer, the first
	 * set bit of the prefix bitmap in that sequence. Only allocated when there is
	 * no gap constraint (see {@link #maxGap}).
	 */
	private int[] firstBitScratchBuffer = null;

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

	/** Pool of reusable buffers for S-step candidates, indexed by DFS depth. */
	private final List<DfsBuffer> sBufferPool = new ArrayList<>();
	/** Pool of reusable buffers for I-step candidates, indexed by DFS depth. */
	private final List<DfsBuffer> iBufferPool = new ArrayList<>();
	/** Number of frequent items (size of the dense internal item-id space). */
	private int frequentItemCount = 0;

	/**
	 * Reusable per-depth scratch storage for candidate items and their bitmaps
	 * during the DFS.
	 */
	private static class DfsBuffer {
		/** Candidate item ids held in this buffer. */
		int[] items;
		/**
		 * Bitmaps corresponding to each candidate item, reused across DFS visits to
		 * this depth.
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
		 * Grows this buffer to at least the given capacity, preserving already pooled
		 * bitmap objects.
		 *
		 * @param capacity the new minimum capacity
		 */
		void growTo(int capacity) {
			items = new int[capacity]; // contents are fully rewritten before being read
			bitmaps = Arrays.copyOf(bitmaps, capacity); // preserve already-pooled bitmap objects
		}
	}

	/** Creates a new instance of the SPAM2 algorithm. */
	public AlgoSPAM() {
	}

	/**
	 * Runs the SPAM algorithm on an input sequence database and writes the frequent
	 * sequential patterns to a file.
	 *
	 * @param input          the path of the input file
	 * @param outputFilePath the path of the output file
	 * @param minsupRel      the minimum support threshold, as a relative value
	 *                       between 0 and 1
	 * @throws IOException if an error occurs while reading the input or writing the
	 *                     output
	 */
	public void runAlgorithm(String input, String outputFilePath, double minsupRel) throws IOException {
		// prepare output writer and reset counters/statistics
		writer = new BufferedWriter(new FileWriter(outputFilePath), 1 << 16);
		writer.write("@FILETYPE=\"Frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();
		
		patternCount = 0;
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

			// release large per-run structures now that patterns have been written out
			vdbArray = null;
			sequenceEndBit = null;
			sidResolver = null;
			newIdToOriginal = null;
			sidScratchBuffer = null;
			firstBitScratchBuffer = null;
		}
		endTime = System.currentTimeMillis();
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
			// skip spaces between tokens
			while (i < length && line.charAt(i) == ' ')
				i++;
			if (i >= length)
				break;

			boolean neg = false;
			if (line.charAt(i) == '-') {
				neg = true;
				i++;
			}

			// parse the digits of the current token
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
	 * Runs the main phases of SPAM: reading the input file once (while counting
	 * item supports and caching the tokenized lines), building the vertical bitmap
	 * database from the cache, and performing the depth-first search for frequent
	 * sequential patterns.
	 *
	 * @param input     the path of the input file
	 * @param minsupRel the relative minimum support threshold
	 * @throws IOException if an error occurs while reading the input file
	 */
	private void spam(String input, double minsupRel) throws IOException {

		// Step 0: single pass over the file. Computes item frequencies, detects
		// singleton-itemset mode, and caches every tokenized line into a flat in-memory
		// buffer so that the vertical bitmap database can be built afterwards without
		// reading or re-parsing the file a second time.
		ArrayListInt sequencesSizeList = new ArrayListInt();
		ArrayListInt tokenLineStartList = new ArrayListInt();

		int[] cachedTokens = new int[1 << 16];
		int cachedTokenCount = 0;

		int initialCapacity = 1024;
		int[] itemSupport = new int[initialCapacity];
		int[] itemLastSid = new int[initialCapacity];
		Arrays.fill(itemLastSid, -1);
		maxItemId = 0;

		int lastBitIndex = 0;
		boolean sawMultiItemItemset = false;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))),
				INPUT_BUFFER_SIZE)) {

			String line;
			int bitIndex = 0;
			int sid = 0;

			while ((line = reader.readLine()) != null) {
				if (isSkippableLine(line)) {
					continue;
				}

				sequencesSizeList.add(bitIndex);
				tokenLineStartList.add(cachedTokenCount);

				int tokenCount = tokenizeLine(line, tokenBuffer);

				// cache the parsed tokens of this line so the vertical database can be built
				// later
				// directly from memory, without reading/tokenizing the file a second time
				if (cachedTokenCount + tokenCount > cachedTokens.length) {
					int newCap = Math.max(cachedTokens.length * 2, cachedTokenCount + tokenCount);
					cachedTokens = Arrays.copyOf(cachedTokens, newCap);
				}
				System.arraycopy(tokenBuffer, 0, cachedTokens, cachedTokenCount, tokenCount);
				cachedTokenCount += tokenCount;

				int itemsInCurrentItemset = 0; // reset per line (each line = one full sequence)

				for (int t = 0; t < tokenCount; t++) {
					int token = tokenBuffer[t];
					if (token == -1) {
						bitIndex++;
						itemsInCurrentItemset = 0; // reset at itemset boundary
					} else if (token == -2) {
						sid++;
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

		// sentinel entry marking the end of the last cached line
		tokenLineStartList.add(cachedTokenCount);

		onlySingletonItemsets = !sawMultiItemItemset;

		// copy sequence start positions into a plain array for fast access
		sequencesSize = new int[sequencesSizeList.size()];
		for (int idx = 0; idx < sequencesSize.length; idx++) {
			sequencesSize[idx] = sequencesSizeList.get(idx);
		}
		sequencesSizeList = null;

		// copy cached-line offsets into a plain array for fast access
		int[] tokenLineStart = new int[tokenLineStartList.size()];
		for (int idx = 0; idx < tokenLineStart.length; idx++) {
			tokenLineStart[idx] = tokenLineStartList.get(idx);
		}
		tokenLineStartList = null;

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
		if (minsup == 0)
			minsup = 1;

		// precompute sequenceEndBit[] and build the SID resolver
		sequenceEndBit = BitmapSPAM.buildSequenceEndBit(sequencesSize, lastBitIndex, numSequences);

		sidResolver = new BitmapSPAM.SidResolver(sequencesSize, sequenceEndBit);

		// these scratch buffers are only read/written by the no-gap-constraint fast
		// path in dfsPruning (see the occCount computation there); when a real gap
		// constraint is configured, that path is never taken, so avoid allocating
		// them at all in that case
		if (maxGap == Integer.MAX_VALUE) {
			sidScratchBuffer = new int[Math.max(numSequences, 1)];
			firstBitScratchBuffer = new int[Math.max(numSequences, 1)];
		} else {
			sidScratchBuffer = null;
			firstBitScratchBuffer = null;
		}

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
		itemLastSid = null;

		frequentItemCount = frequentOriginalIdsList.size();
		newIdToOriginal = new int[frequentItemCount];
		for (int i = 0; i < frequentItemCount; i++) {
			newIdToOriginal[i] = frequentOriginalIdsList.get(i);
		}
		frequentOriginalIdsList = null;

		// resolve the "must items" constraint (given in the original item id space)
		// into a bitmask
		// over internal dense item ids, so that membership can be tested with a plain
		// array lookup
		// during the DFS instead of a hash-set lookup with boxing
		mustItemMask = null;
		mustItemsPossible = true;
		mustItemsRequiredCount = 0;
		mustItemsOccurrenceCount = null;
		if (mustItems != null && mustItems.length != 0) {
			mustItemMask = new boolean[frequentItemCount];
			for (int orig : mustItems) {
				// NOTE: the bound must be checked against maxItemId
				int newId = (orig >= 0 && orig <= maxItemId) ? remap[orig] : -1;
				if (newId < 0) {
					// a required item is infrequent (or unknown): the constraint can never be
					// satisfied
					mustItemsPossible = false;
				} else if (!mustItemMask[newId]) {
					// count each distinct required item only once, so duplicate entries in
					// mustItems do not inflate the required count
					mustItemMask[newId] = true;
					mustItemsRequiredCount++;
				}
			}

			if (!mustItemsPossible) {
				// at least one required item is infrequent (or unknown): no pattern can ever
				// satisfy the constraint, so building the vertical database and running the
				// depth-first search would all be wasted work
				return;
			}

			mustItemsOccurrenceCount = new int[frequentItemCount];
		}

		// Step 1: build the vertical database directly from the cached tokens (the file
		// itself has
		// already been read exactly once, above; no second disk read or re-tokenization
		// happens here)
		vdbArray = new BitmapSPAM[frequentItemCount];

		int sid2 = 0;
		int numCachedLines = tokenLineStart.length - 1;

		for (int line = 0; line < numCachedLines; line++) {
			int start = tokenLineStart[line];
			int end = tokenLineStart[line + 1];
			int tid2 = 0;

			for (int p = start; p < end; p++) {
				int token = cachedTokens[p];
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

		// release the cached token database and the remap table now that the vertical
		// bitmap representation has been built, so they don't compete for memory with the DFS
		// below
		cachedTokens = null;
		tokenLineStart = null;
		remap = null;

		// Step 2: save frequent 1-patterns
		if (minimumPatternLength <= 1 && maximumPatternLength >= 1) {
			for (int newId = 0; newId < frequentItemCount; newId++) {
				if (mustItemMask == null || (mustItemsPossible && mustItemsRequiredCount == 1 && mustItemMask[newId])) {
					savePattern(newId, vdbArray[newId]);
				}
			}
		}

		if (maximumPatternLength == 1)
			return;

		int[] frequentItemsArr = new int[frequentItemCount];
		for (int i = 0; i < frequentItemCount; i++) {
			frequentItemsArr[i] = i;
		}

		sBufferPool.clear();
		iBufferPool.clear();

		// Step 3: depth-first search for longer patterns
		for (int idx = 0; idx < frequentItemCount; idx++) {
			BitmapSPAM bm = vdbArray[idx];

			// start a new pattern buffer containing a single itemset with this item
			patternLength = 0;
			ensurePatternCapacity(1);
			patternBuffer[patternLength++] = idx;

			// initialize the running "must items" match count and per-item occurrence count
			// for this new pattern prefix
			boolean idxRequired = mustItemMask != null && mustItemMask[idx];
			if (idxRequired) {
				mustItemsOccurrenceCount[idx] = 1;
				mustItemsMatchedCount = 1;
			} else {
				mustItemsMatchedCount = 0;
			}

			dfsPruning(bm, frequentItemsArr, frequentItemCount, frequentItemsArr, frequentItemCount, idx, 2, 0);

			if (idxRequired) {
				mustItemsOccurrenceCount[idx] = 0;
			}
		}
	}

	/**
	 * Retrieves the pooled DFS buffer for the given recursion depth, growing it on
	 * demand if the requested capacity exceeds what is currently allocated.
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
			// grow in place so already-pooled bitmap objects are preserved
			buf.growTo(capacity);
		}
		return buf;
	}

	/**
	 * Finds the index of the first entry in {@code arr[0..count)} that is strictly
	 * greater than {@code value}, using binary search since the array is always
	 * kept sorted in ascending order.
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
	 * Recursively extends the current pattern with S-steps (new itemset) and
	 * I-steps (item added to the last itemset). Saves each frequent pattern found
	 * and prunes candidates below minsup.
	 *
	 * @param prefixBitmap               the bitmap representation of the current
	 *                                   pattern
	 * @param sn                         candidate items usable for S-steps
	 * @param snCount                    number of valid entries in sn
	 * @param in                         candidate items usable for I-steps
	 * @param inCount                    number of valid entries in in
	 * @param hasToBeGreaterThanForIStep the minimum item id (exclusive) allowed for
	 *                                   I-step extension
	 * @param m                          the length of the pattern that would result
	 *                                   from the extension
	 * @param depth                      the current DFS recursion depth, used to
	 *                                   select the pooled buffers
	 * @throws IOException if an error occurs while writing a pattern to the output
	 */
	private void dfsPruning(BitmapSPAM prefixBitmap, int[] sn, int snCount, int[] in, int inCount,
			int hasToBeGreaterThanForIStep, int m, int depth) throws IOException {
		// S-steps: extend the pattern with a new itemset
		DfsBuffer sBuf = getBuffer(sBufferPool, depth, snCount);
		int sCount = 0;

		// precompute occupied SIDs/first-bit once per node when there is no gap
		// constraint
		int occCount = (maxGap == Integer.MAX_VALUE && snCount > 0)
				? prefixBitmap.collectOccupiedSidsAndFirstBit(sidScratchBuffer, firstBitScratchBuffer, sidResolver)
				: 0;

		// build S-step candidate bitmaps and keep only those meeting minsup
		for (int idx = 0; idx < snCount; idx++) {
			int item = sn[idx];
			BitmapSPAM itemBitmap = vdbArray[item];

			BitmapSPAM out = sBuf.bitmaps[sCount];
			if (out == null) {
				out = new BitmapSPAM();
				sBuf.bitmaps[sCount] = out;
			}
			out.resetForReuse(itemBitmap.wordCount);

			prefixBitmap.createNewBitmapSStep(out, itemBitmap, maxGap, sidResolver, sequenceEndBit, sidScratchBuffer,
					firstBitScratchBuffer, occCount);

			// keep the candidate only if it meets minimum support
			if (out.getSupportWithoutGapTotal() >= minsup) {
				sBuf.items[sCount] = item;
				sCount++;
			}
		}

		// recurse on each frequent S-step candidate
		for (int k = 0; k < sCount; k++) {
			int item = sBuf.items[k];
			BitmapSPAM newBitmap = sBuf.bitmaps[k];

			if (newBitmap.getSupport() >= minsup) {
				// append a new itemset (separator + item) to the pattern buffer
				int savedLen = patternLength;
				ensurePatternCapacity(patternLength + 2);
				if (patternLength > 0) {
					patternBuffer[patternLength++] = -1;
				}
				patternBuffer[patternLength++] = item;

				// update the running "must items" match count for this push; only the 0->1
				// transition of this item's occurrence count affects the distinct-item count,
				// so
				// reusing the same required item later in the pattern does not inflate it (this
				// fixes a bug where a repeated required item could wrongly be counted once per
				// occurrence instead of once per distinct item)
				boolean isRequired = mustItemMask != null && mustItemMask[item];
				if (isRequired && ++mustItemsOccurrenceCount[item] == 1) {
					mustItemsMatchedCount++;
				}

				if (m >= minimumPatternLength && mustItemsSatisfied()) {
					savePattern(newBitmap);
				}
				if (maximumPatternLength > m) {
					dfsPruning(newBitmap, sBuf.items, sCount, sBuf.items, sCount, item, m + 1, depth + 1);
				}

				// pop: undo the occurrence-count update, then restore the buffer, for the next
				// sibling
				if (isRequired && --mustItemsOccurrenceCount[item] == 0) {
					mustItemsMatchedCount--;
				}
				patternLength = savedLen;
			}
		}

		// I-steps: extend the pattern by adding an item to the last itemset
		if (!onlySingletonItemsets) {
			DfsBuffer iBuf = getBuffer(iBufferPool, depth, inCount);
			int iCount = 0;

			// only consider items greater than the last one added, to avoid duplicate
			// itemsets
			int startIdx = upperBound(in, inCount, hasToBeGreaterThanForIStep);

			// build I-step candidate bitmaps and keep only those meeting minsup
			for (int idx = startIdx; idx < inCount; idx++) {
				int item = in[idx];
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

			// recurse on each frequent I-step candidate
			for (int k = 0; k < iCount; k++) {
				int item = iBuf.items[k];
				BitmapSPAM newBitmap = iBuf.bitmaps[k];

				// append the item directly to the last itemset in the pattern buffer
				int savedLen = patternLength;
				ensurePatternCapacity(patternLength + 1);
				patternBuffer[patternLength++] = item;

				// update the running "must items" match count for this push; only the 0->1
				// transition of this item's occurrence count affects the distinct-item count,
				// so
				// reusing the same required item later in the pattern does not inflate it
				boolean isRequired = mustItemMask != null && mustItemMask[item];
				if (isRequired && ++mustItemsOccurrenceCount[item] == 1) {
					mustItemsMatchedCount++;
				}

				if (m >= minimumPatternLength && mustItemsSatisfied()) {
					savePattern(newBitmap);
				}
				if (maximumPatternLength > m) {
					dfsPruning(newBitmap, sBuf.items, sCount, iBuf.items, iCount, item, m + 1, depth + 1);
				}

				// pop: undo the occurrence-count update, then restore the buffer, for the next
				// sibling
				if (isRequired && --mustItemsOccurrenceCount[item] == 0) {
					mustItemsMatchedCount--;
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
	 * Checks whether the pattern currently being built by the DFS satisfies the
	 * "must items" constraint, if one was set. This relies on
	 * {@link #mustItemsMatchedCount}, which is maintained incrementally as items
	 * are pushed onto / popped off {@link #patternBuffer}, so the check costs O(1)
	 * rather than requiring a rescan of the whole pattern buffer at every DFS node.
	 *
	 * @return true if the pattern may be output, false if it must be filtered out
	 */
	private boolean mustItemsSatisfied() {
		return mustItemMask == null || (mustItemsPossible && mustItemsMatchedCount == mustItemsRequiredCount);
	}

	/**
	 * Flushes the in-memory output buffer to the writer once it exceeds the flush
	 * threshold.
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
	 * @param item   the internal id of the frequent item
	 * @param bitmap the bitmap holding the pattern's support information
	 * @throws IOException if an error occurs while writing to the output
	 */
	private void savePattern(int item, BitmapSPAM bitmap) throws IOException {
		patternCount++;
		patternBuilder.setLength(0);
		// build the pattern text: item, terminator, support, and optional SIDs
		patternBuilder.append(newIdToOriginal[item]).append(" -1 #SUP: ").append(bitmap.getSupport());

		if (outputSequenceIdentifiers) {
			patternBuilder.append(" #SID: ").append(bitmap.getSIDs(sidResolver));
		}

		outputBuffer.append(patternBuilder).append('\n');
		maybeFlushOutputBuffer();
	}

	/**
	 * Saves the pattern currently held in {@code patternBuffer} (a multi-itemset
	 * pattern) to the output buffer.
	 *
	 * @param bitmap the bitmap holding the pattern's support information
	 * @throws IOException if an error occurs while writing to the output
	 */
	private void savePattern(BitmapSPAM bitmap) throws IOException {
		patternCount++;
		patternBuilder.setLength(0);

		// walk the flat buffer, translating items and turning -1 separators into
		// itemset closers
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
			patternBuilder.append(" #SID: ").append(bitmap.getSIDs(sidResolver));
		}

		outputBuffer.append(patternBuilder).append('\n');
		maybeFlushOutputBuffer();
	}

	/**
	 * Prints statistics about the last execution of the algorithm to standard
	 * output.
	 */
	public void printStatistics() {
		System.out.println("=============  SPAM2 v2.67 - STATISTICS =============\n" + " Total time ~ "
				+ (endTime - startTime) + " ms\n" + " Frequent sequences count : " + patternCount + "\n"
				+ " Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory() + "\n" + " minsup " + minsup + "\n"
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
	}

	/** @param show whether to include sequence identifiers (SIDs) in the output */
	public void showSequenceIdentifiersInOutput(boolean show) {
		this.outputSequenceIdentifiers = show;
	}

	/**
	 * Sets a set of items that must all appear in a sequential pattern for that
	 * pattern to be output. Items are given using their original ids, as they
	 * appear in the input file. If null or empty, no such constraint is applied
	 * (the default behavior). The given array is defensively copied.
	 *
	 * @param mustAppearItems the set of items that must all appear in output
	 *                        patterns
	 */
	public void setMustItems(int[] mustAppearItems) {
		if (mustAppearItems == null || mustAppearItems.length == 0) {
			this.mustItems = null;
		} else {
			this.mustItems = Arrays.copyOf(mustAppearItems, mustAppearItems.length);
		}
	}
}