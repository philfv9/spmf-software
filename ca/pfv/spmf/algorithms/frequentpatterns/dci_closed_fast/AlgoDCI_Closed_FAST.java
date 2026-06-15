package ca.pfv.spmf.algorithms.frequentpatterns.dci_closed_fast;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is the optimized implementation of the "DCI_Closed" algorithm. The
 * DCI_Closed algorithm finds all closed itemsets in a transaction database.
 * <br/>
 * <br/>
 *
 * DCI_Closed was initially proposed in this article: <br/>
 * <br/>
 *
 * Lucchese, C., Orlando, S. & Perego, Raffaele (2004), DCI_Closed: a fast and
 * memory efficient algorithm to mine frequent closed itemsets, Proc. 2nd IEEE
 * ICDM Workshop on Frequent Itemset Mining Implementations at ICDM 2004. <br/>
 * <br/>
 *
 * Note: My implementation assumes that there is no item named "0". <br/>
 * <br/>
 *
 * My implementation includes several optimizations:<br/>
 * - the use of a bit matrix (as described in the TKDE paper)<br/>
 * - projecting the database, although not at all levels<br/>
 * - and various other optimizations to speed up the algorithm
 *
 * @see BitMatrix
 * @see IntList
 * @see LongArrayBitSet
 * @author Philippe Fournier-Viger
 */
public class AlgoDCI_Closed_FAST {

	/** number of closed itemsets found */
	int closedCount = 0;

	/** the number of transactions in the transaction database */
	int tidsCount = 0;

	/** the number of frequent items after renaming (new maxItemId) */
	int renameCount = 0;

	/** relative minimum support set by the user */
	private int minSuppRelative;

	/** object to write the output file */
	BufferedWriter writer = null;

	/** if true, transaction identifiers of each pattern will be shown */
	boolean showTransactionIdentifiers = false;

	/** converter for translating between renamed items and original names */
	private ItemNameConverter nameConverter;

	/** reusable StringBuilder for writeOut */
	private StringBuilder writeBuffer = new StringBuilder();

	/** reusable char[] for writing output without String allocation */
	private char[] writeCharBuffer = new char[4096];

	/** size of the I/O read buffer */
	private static final int IO_BUFFER_SIZE = 65536;

	/** reusable buffer for tid mapping in projectMatrix */
	private int[] tidMappingBuffer = null;

	/** pre-computed lookup from renamed item id to original name */
	private int[] oldNameLookup = null;

	/** pre-computed number of words for the original transaction count */
	private int originalWords = 0;

	/** total time of last execution */
	private double timeOfLastRun = 0;

	/** peak memory of last execution */
	private double memoryOfLastRun = 0;

	/** minimum support (in tids) to consider projection worthwhile */
	private static final int MIN_SUPPORT_FOR_PROJECTION = 64;

	/** minimum postset size to consider projection worthwhile */
	private static final int MIN_POSTSET_FOR_PROJECTION = 3;

	/** reusable buffer for original item ids during output */
	private int[] originalIdBuffer = null;

	/**
	 * Default constructor
	 */
	public AlgoDCI_Closed_FAST() {
	}

	/**
	 * Run the algorithm with a minimum support threshold expressed as a percentage.
	 * The percentage is converted to an absolute count after the single database
	 * scan, so no extra scan is performed. The scan is done first with no threshold
	 * to count transactions, then the real threshold is derived and a second
	 * internal build of the matrix is performed with the correct threshold so that
	 * infrequent items are properly excluded.
	 *
	 * @param input  the path of an input file (transaction database).
	 * @param output the path of the output file for writing the result
	 * @param minsup a minimum support threshold as a percentage (e.g. 0.5 = 50%)
	 * @throws IOException exception if error while writing/reading files
	 */
	public void runAlgorithm(String input, String output, double minsup) throws IOException {
		// record start time
		long startTimestamp = System.currentTimeMillis();

		// reset all counters and stats
		closedCount = 0;
		tidsCount = 0;
		renameCount = 0;
		timeOfLastRun = 0;
		memoryOfLastRun = 0;

		// reset tool for logging memory usage
		MemoryLogger.getInstance().reset();

		System.out.println("Running the DCI-Closed algorithm");

		// first pass: read transactions into memory with no support filter so that
		// tidsCount is known; the raw transactions are returned for reuse so the
		// file is read exactly once
		List<int[]> transactions = new ArrayList<int[]>();
		int maxItemId = readTransactions(input, transactions);

		// tidsCount is now set; convert percentage to absolute count using ceiling
		// so that e.g. 0.5 on 3 transactions gives ceil(1.5) = 2
		tidsCount = transactions.size();
		this.minSuppRelative = (int) Math.ceil(minsup * tidsCount);

		// build the bit matrix from the already-loaded transactions using the
		// real threshold; no second file scan is needed
		final BitMatrix matrix = buildMatrix(transactions, maxItemId);

		// run the rest of the algorithm with the computed absolute threshold
		runAlgorithmAfterScan(matrix, startTimestamp, output);
	}

	/**
	 * Run the algorithm with a minimum support threshold expressed as an absolute
	 * count.
	 *
	 * @param input  the path of an input file (transaction database).
	 * @param output the path of the output file for writing the result
	 * @param minsup a minimum support threshold as an absolute transaction count
	 * @throws IOException exception if error while writing/reading files
	 */
	public void runAlgorithm(String input, String output, int minsup) throws IOException {
		// record start time
		long startTimestamp = System.currentTimeMillis();

		// reset all counters and stats
		closedCount = 0;
		tidsCount = 0;
		renameCount = 0;
		timeOfLastRun = 0;
		memoryOfLastRun = 0;

		// reset tool for logging memory usage
		MemoryLogger.getInstance().reset();

		System.out.println("Running the DCI-Closed algorithm");

		// save the minimum support
		this.minSuppRelative = minsup;

		// (0) SINGLE SCAN: read database, rename items by ascending support,
		// create and populate the bit matrix.
		final BitMatrix matrix = scanAndBuildMatrix(input);

		// run the rest of the algorithm with the known absolute threshold
		runAlgorithmAfterScan(matrix, startTimestamp, output);
	}

	/**
	 * Execute all steps of the algorithm that follow the initial database scan.
	 * Both runAlgorithm overloads converge here once minSuppRelative and the bit
	 * matrix are ready, avoiding any duplication of logic.
	 *
	 * @param matrix         the populated bit matrix built by scanAndBuildMatrix
	 * @param startTimestamp the System.currentTimeMillis() value recorded at the
	 *                       very beginning of runAlgorithm, used to compute the
	 *                       total execution time
	 * @param output         the path of the output file
	 * @throws IOException exception if error while writing/reading files
	 */
	private void runAlgorithmAfterScan(BitMatrix matrix, long startTimestamp, String output) throws IOException {

		// pre-allocate the tid mapping buffer for projection (max possible size)
		tidMappingBuffer = new int[tidsCount];

		// pre-compute word count for the original database (used by projection
		// heuristic)
		originalWords = LongArrayBitSet.wordsNeeded(tidsCount);

		// pre-compute old name lookup for fast output
		oldNameLookup = new int[renameCount + 1];
		for (int k = 1; k <= renameCount; k++) {
			oldNameLookup[k] = nameConverter.toOldName(k);
		}

		MemoryLogger.getInstance().checkMemory();

		// (1) INITIAL VARIABLES FOR THE FIRST CALL TO THE "DCI_CLOSED" PROCEDURE
		IntList closedset = new IntList();
		IntList preset = new IntList();

		// items with support == tidsCount appear in every transaction; pre-add to
		// closedset and exclude from postset so they propagate into every output itemset
		IntList postset = new IntList(renameCount);
		for (int i = 1; i <= renameCount; i++) {
			if (matrix.getSupportOfItem(i) == tidsCount) {
				closedset.add(i);
			} else {
				postset.add(i);
			}
		}

		MemoryLogger.getInstance().checkMemory();

		// (2) Open the output file before any writeOut calls so that writer is
		// never null when writeOut is invoked (e.g. for the universal-items closure).
		// Use try-with-resources to guarantee the output file is always closed,
		// even if an exception is thrown during mining.
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
			writer = bw;

			// output the closed itemset of universal items if any exist.
			// This must happen inside the try block so that writer is non-null.
			if (closedset.size() > 0) {
				LongArrayBitSet allTIDs = new LongArrayBitSet(LongArrayBitSet.wordsNeeded(tidsCount));
				allTIDs.setRange(0, tidsCount);
				writeOut(closedset, tidsCount, allTIDs);
			}

			// (3) CALL THE "DCI_CLOSED" RECURSIVE PROCEDURE
			dci_closed(closedset, null, postset, preset, matrix, originalWords);
		} finally {
			writer = null;
		}

		MemoryLogger.getInstance().checkMemory();

		// release buffers
		tidMappingBuffer = null;
		oldNameLookup = null;

		timeOfLastRun = (System.currentTimeMillis() - startTimestamp);
		memoryOfLastRun = MemoryLogger.getInstance().getMaxMemory();

		printStats();
	}

	/**
	 * Print stats to the console
	 */
	private void printStats() {
		System.out.println("========== DCI_CLOSED FAST 2.66 - STATS ============");
		System.out.println(" Number of transactions: " + tidsCount);
		System.out.println(" Number of frequent closed itemsets: " + closedCount);
		System.out.println(" Total time ~: " + timeOfLastRun + " ms");
		System.out.println(" Maximum memory usage : " + memoryOfLastRun + " mb");
		System.out.println("==========================================");
	}

	/**
	 * Set that the transaction identifiers should be shown (true) or not (false)
	 * for each pattern found, when writing the result to an output file.
	 *
	 * @param showTransactionIdentifiers true or false
	 */
	public void setShowTransactionIdentifiers(boolean showTransactionIdentifiers) {
		this.showTransactionIdentifiers = showTransactionIdentifiers;
	}

	/**
	 * Read the input file and store all transactions in the provided list. Returns
	 * the largest item id seen. This method does not filter by support so that the
	 * caller can determine tidsCount before computing a percentage-based threshold.
	 *
	 * @param input        the input file path
	 * @param transactions the list to populate with the parsed transactions
	 * @return the largest item id seen in the file
	 * @throws IOException exception if error while reading the file
	 */
	private int readTransactions(String input, List<int[]> transactions) throws IOException {
		int maxItemId = 0;
		IntList itemBuffer = new IntList(64);

		try (BufferedReader reader = new BufferedReader(new FileReader(input), IO_BUFFER_SIZE)) {
			String line;
			while ((line = reader.readLine()) != null) {
				int len = line.length();
				if (len == 0) {
					continue;
				}
				char first = line.charAt(0);
				if (first == '#' || first == '%' || first == '@') {
					continue;
				}

				// parse items manually to avoid String.split and Integer.parseInt overhead
				itemBuffer.clear();
				int currentVal = 0;
				boolean hasVal = false;
				for (int pos = 0; pos < len; pos++) {
					char c = line.charAt(pos);
					if (c == ' ') {
						if (hasVal) {
							itemBuffer.add(currentVal);
							if (currentVal > maxItemId) {
								maxItemId = currentVal;
							}
							currentVal = 0;
							hasVal = false;
						}
					} else {
						currentVal = currentVal * 10 + (c - '0');
						hasVal = true;
					}
				}
				// add the last item on the line if present
				if (hasVal) {
					itemBuffer.add(currentVal);
					if (currentVal > maxItemId) {
						maxItemId = currentVal;
					}
				}

				// copy to a compact int array and store
				int[] items = new int[itemBuffer.size()];
				System.arraycopy(itemBuffer.data(), 0, items, 0, itemBuffer.size());
				transactions.add(items);
			}
		}

		return maxItemId;
	}

	/**
	 * Build the bit matrix from an already-loaded list of transactions. Computes
	 * item supports, filters infrequent items using minSuppRelative, renames
	 * frequent items by ascending support order and populates the vertical bit
	 * matrix. Called by the percentage overload of runAlgorithm after the real
	 * threshold has been computed.
	 *
	 * @param transactions the list of transactions loaded by readTransactions
	 * @param maxItemId    the largest item id seen in the database
	 * @return the populated BitMatrix using renamed item ids
	 */
	private BitMatrix buildMatrix(List<int[]> transactions, int maxItemId) {

		// compute support of each original item
		int[] supportOriginal = new int[maxItemId + 1];
		for (int tid = 0; tid < tidsCount; tid++) {
			int[] items = transactions.get(tid);
			for (int k = 0; k < items.length; k++) {
				supportOriginal[items[k]]++;
			}
		}

		// collect frequent items using the real threshold
		IntList frequentItems = new IntList();
		for (int item = 1; item <= maxItemId; item++) {
			if (supportOriginal[item] >= minSuppRelative) {
				frequentItems.add(item);
			}
		}

		// sort frequent items by ascending support, breaking ties by original name
		final int[] supRef = supportOriginal;
		frequentItems.sort(new IntComparator() {
			public int compare(int a, int b) {
				int diff = supRef[a] - supRef[b];
				if (diff != 0) {
					return diff;
				}
				return (a < b) ? -1 : 1;
			}
		});

		// assign new names 1, 2, 3, ... in ascending support order
		renameCount = frequentItems.size();
		nameConverter = new ItemNameConverter(renameCount);
		int[] freqData = frequentItems.data();
		for (int k = 0; k < renameCount; k++) {
			nameConverter.assignNewName(freqData[k]);
		}

		// build the bit matrix using renamed items
		BitMatrix matrix = new BitMatrix(renameCount, tidsCount);
		for (int tid = 0; tid < tidsCount; tid++) {
			int[] items = transactions.get(tid);
			for (int k = 0; k < items.length; k++) {
				if (nameConverter.isOldItemExisting(items[k])) {
					int newName = nameConverter.toNewName(items[k]);
					matrix.addTidForItem(newName, tid);
				}
			}
		}

		return matrix;
	}

	/**
	 * Read the input file in a single pass to determine the database size and the
	 * largest item. Then rename frequent items by ascending support order and build
	 * the vertical bit matrix using renamed items. Uses manual int parsing to avoid
	 * String.split and Integer.parseInt overhead.
	 *
	 * @param input the input file path
	 * @return the populated BitMatrix using renamed item ids
	 * @throws IOException exception if error while reading the file
	 */
	private BitMatrix scanAndBuildMatrix(String input) throws IOException {
		// read all transactions into memory in a single pass;
		// try-with-resources guarantees the reader is closed even on exception
		List<int[]> transactions = new ArrayList<int[]>();

		int maxItemId = readTransactions(input, transactions);

		// set the transaction count
		tidsCount = transactions.size();

		// build and return the matrix using the already-known threshold
		return buildMatrix(transactions, maxItemId);
	}

	/**
	 * The method "DCI_CLOSED" as described in the paper. After item renaming, i
	 * is less than j in renamed space directly corresponds to the total order (ascending
	 * support, then lexicographical). Supports adaptive projection at any recursion
	 * level.
	 * <p>
	 * When closedsetTIDs is null this is the top-level call and newgenTIDs is taken
	 * directly from the matrix (no reusable buffer needed). Otherwise newgenTIDs is
	 * computed by intersecting closedsetTIDs with each item's tidset.
	 * <p>
	 * When projection is used a snapshot of preset is passed to the projected
	 * recursion so that items added to preset after the projection point (which are
	 * absent from the projected matrix) cannot corrupt isDuplicate checks.
	 *
	 * @param closedset        the closed set (see paper)
	 * @param closedsetTIDs    the tids of the closed set, or null for the top-level
	 *                         call
	 * @param postset          the postset (see paper)
	 * @param preset           the preset (see paper)
	 * @param matrix           the current (possibly projected) matrix
	 * @param currentWordCount the word count of the current matrix's tid space
	 * @throws IOException if error writing the output file
	 */
	private void dci_closed(IntList closedset, LongArrayBitSet closedsetTIDs, IntList postset, IntList preset,
			BitMatrix matrix, int currentWordCount) throws IOException {

		// whether this is the top-level call (closedsetTIDs == null)
		final boolean firstTime = (closedsetTIDs == null);

		// reusable buffer for intersection; only needed when not the top-level call
		LongArrayBitSet reusableNewgenTIDs = firstTime ? null : new LongArrayBitSet(closedsetTIDs.numWords());

		// cache postset internals for direct access inside the loop
		int[] postsetData = postset.data();
		int postsetSize = postset.size();

		// L2: for all i in postset
		for (int idx = 0; idx < postsetSize; idx++) {
			int i = postsetData[idx];

			// L4: calculate the tidset of newgen = closedset U {i}
			LongArrayBitSet newgenTIDs;
			int newgenSupport;
			if (firstTime) {
				// top-level: newgenTIDs is the matrix bitset directly (no copy)
				newgenTIDs = matrix.getBitSetOf(i);
				newgenSupport = newgenTIDs.cardinality();
			} else {
				// intersect closedsetTIDs and item i's tidset in one fused pass
				newgenSupport = reusableNewgenTIDs.copyFromAndAnd(closedsetTIDs, matrix.getBitSetOf(i));
				newgenTIDs = reusableNewgenTIDs;
			}

			// if newgen is frequent
			if (newgenSupport >= minSuppRelative) {

				// L5: if newgen is not a duplicate
				if (!isDuplicate(newgenTIDs, preset, matrix)) {

					// L6: closedsetNew = closedset U {i} U closure items
					IntList closedsetNew = new IntList(closedset.size() + postsetSize - idx);
					closedsetNew.addAll(closedset);
					closedsetNew.add(i);

					// L7: postsetNew = items in suffix of postset not subsumed by newgenTIDs
					// maximum size is the number of remaining suffix items after i
					IntList postsetNew = new IntList(postsetSize - idx - 1);
					// L8: for each j in postset with j > i (suffix only, postset is ordered)
					for (int jdx = idx + 1; jdx < postsetSize; jdx++) {
						int j = postsetData[jdx];
						if (newgenTIDs.isSubsetOf(matrix.getBitSetOf(j))) {
							// newgenTIDs ⊆ tidset(j) so j is in the closure;
							// intersection would just yield newgenTIDs again
							closedsetNew.add(j);
						} else {
							postsetNew.add(j);
						}
					}

					// L15: write out the new closed itemset
					writeOut(closedsetNew, newgenSupport, newgenTIDs);

					// L16: recursive call
					// save preset size so we can backtrack after recursion
					int savedPresetSize = preset.size();

					// decide whether to project the matrix
					int projectedWords = LongArrayBitSet.wordsNeeded(newgenSupport);
					boolean shouldProject = (projectedWords <= currentWordCount / 2)
							&& (newgenSupport >= MIN_SUPPORT_FOR_PROJECTION)
							&& (postsetNew.size() >= MIN_POSTSET_FOR_PROJECTION);

					if (shouldProject) {
						// snapshot the preset at this point so that items added to preset
						// by later iterations of this loop (which are absent from the
						// projected matrix) cannot corrupt isDuplicate in the recursion
						IntList presetSnapshot = snapshotPreset(preset);
						BitMatrix projectedMatrix = projectMatrix(matrix, newgenTIDs, newgenSupport, postsetNew,
								presetSnapshot);
						// in projected space all surviving tids are 0..newgenSupport-1
						LongArrayBitSet projectedAll = new LongArrayBitSet(projectedWords);
						projectedAll.setRange(0, newgenSupport);
						dci_closed(closedsetNew, projectedAll, postsetNew, presetSnapshot, projectedMatrix,
								projectedWords);
					} else {
						// no projection: determine whether newgenTIDs needs cloning
						// - firstTime: newgenTIDs is a direct reference to the matrix bitset,
						// which is stable, so no clone is needed
						// - otherwise: newgenTIDs is backed by reusableNewgenTIDs which will
						// be overwritten on the next loop iteration, so we must clone
						LongArrayBitSet tidsetForRecursion = firstTime ? newgenTIDs : newgenTIDs.clone();
						dci_closed(closedsetNew, tidsetForRecursion, postsetNew, preset, matrix, currentWordCount);
					}

					// backtrack preset to state before the recursive call
					preset.truncate(savedPresetSize);
				}

				// L17: add i to preset so later items detect this branch as explored;
				// must happen for every frequent i, duplicate or not
				preset.add(i);
			}
		}
	}

	/**
	 * Create a snapshot copy of the preset for use inside a projected recursion.
	 * Items added to the shared preset after the projection point are not in the
	 * projected matrix, so the projected recursion must work with a fixed copy.
	 *
	 * @param preset the current preset
	 * @return a new IntList containing the same elements
	 */
	private IntList snapshotPreset(IntList preset) {
		IntList snapshot = new IntList(preset.size());
		snapshot.addAll(preset);
		return snapshot;
	}

	/**
	 * Write a frequent closed itemset to the output file. Converts renamed items
	 * back to their original names using the pre-computed lookup array. Items are
	 * sorted by their original names before output.
	 *
	 * @param closedset a closed itemset (using renamed item ids)
	 * @param support   the support of this itemset
	 * @param tids      the transaction ids of this itemset
	 * @throws IOException if error writing the output file
	 */
	private void writeOut(IntList closedset, int support, LongArrayBitSet tids) throws IOException {
		closedCount++;

		int[] data = closedset.data();
		int size = closedset.size();

		// reuse a static buffer for original ids to avoid repeated allocation
		// (allocate once if needed, reuse thereafter)
		if (originalIdBuffer == null || originalIdBuffer.length < size) {
			originalIdBuffer = new int[Math.max(size, 16)];
		}

		// convert renamed ids to original ids
		for (int k = 0; k < size; k++) {
			originalIdBuffer[k] = oldNameLookup[data[k]];
		}
		// sort original ids in ascending order
		Arrays.sort(originalIdBuffer, 0, size);

		// write to writeBuffer
		writeBuffer.setLength(0);
		for (int k = 0; k < size; k++) {
			if (k > 0) {
				writeBuffer.append(' ');
			}
			writeBuffer.append(originalIdBuffer[k]);
		}

		writeBuffer.append(" #SUP: ");
		writeBuffer.append(support);

		if (showTransactionIdentifiers) {
			writeBuffer.append(" #TID:");
			for (int tid = tids.nextSetBit(0); tid != -1; tid = tids.nextSetBit(tid + 1)) {
				writeBuffer.append(' ');
				writeBuffer.append(tid);
			}
		}

		// write using char[] to avoid a toString() String allocation
		int len = writeBuffer.length();
		if (len > writeCharBuffer.length) {
			writeCharBuffer = new char[len * 2];
		}
		writeBuffer.getChars(0, len, writeCharBuffer, 0);
		writer.write(writeCharBuffer, 0, len);
		writer.newLine();
	}

	/**
	 * The method "is_dup" as described in the paper. Iterates preset in reverse
	 * because most recently added items have higher support and are more likely to
	 * be supersets, enabling earlier exit.
	 *
	 * @param newgenTIDs the tidset of newgen
	 * @param preset     the preset itemset
	 * @param matrix     the current transaction database as a bit matrix
	 * @return true if newgen is a duplicate, false otherwise
	 */
	private boolean isDuplicate(LongArrayBitSet newgenTIDs, IntList preset, BitMatrix matrix) {
		int[] presetData = preset.data();
		int presetSize = preset.size();
		// iterate in reverse for earlier exit (see paper L25-L26)
		for (int k = presetSize - 1; k >= 0; k--) {
			if (newgenTIDs.isSubsetOf(matrix.getBitSetOf(presetData[k]))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Project the bit matrix with a given bitset, keeping only the tids that are
	 * set in the bitset. Only items in postset or preset are projected since those
	 * are the only items needed in subsequent recursive calls. 
	 *
	 * @param matrix        the original bit matrix
	 * @param bitset        a bitset indicating which tids to keep
	 * @param projectedsize the number of transactions in the projected matrix
	 * @param postset       the postset of items to project
	 * @param preset        the preset of items to project (snapshot, not shared)
	 * @return a new bit matrix containing only the projected tids
	 */
	private BitMatrix projectMatrix(BitMatrix matrix, LongArrayBitSet bitset, int projectedsize, IntList postset,
			IntList preset) {
		// allocate a projected matrix; bitsets for individual items are created
		// lazily inside BitMatrix so only slots actually written pay allocation cost
		BitMatrix newMatrix = new BitMatrix(renameCount, projectedsize);

		// build tid mapping: shared buffer maps new tid index -> old tid index
		int newTid = 0;
		for (int bit = bitset.nextSetBit(0); bit >= 0; bit = bitset.nextSetBit(bit + 1)) {
			tidMappingBuffer[newTid++] = bit;
		}

		// project postset items using word-level gather
		int postsetSize = postset.size();
		int[] postsetData = postset.data();
		for (int r = 0; r < postsetSize; r++) {
			projectItem(postsetData[r], matrix, newMatrix, projectedsize);
		}

		// project preset items using word-level gather (only if non-empty)
		int presetSize = preset.size();
		if (presetSize > 0) {
			int[] presetData = preset.data();
			for (int r = 0; r < presetSize; r++) {
				projectItem(presetData[r], matrix, newMatrix, projectedsize);
			}
		}

		return newMatrix;
	}

	/**
	 * Project a single item's tidset from the source matrix into the destination
	 * matrix using word-level bit gathering. For each group of up to 64 destination
	 * tids the corresponding source bits are assembled into one long word and
	 * written in a single call, avoiding one get() and one set() call per tid.
	 * Destination words that are all-zero are skipped entirely.
	 *
	 * @param item          the item to project (renamed id)
	 * @param src           the source bit matrix
	 * @param dst           the destination (projected) bit matrix
	 * @param projectedsize the number of tids in the projected matrix
	 */
	private void projectItem(int item, BitMatrix src, BitMatrix dst, int projectedsize) {
		LongArrayBitSet srcBits = src.getBitSetOf(item);

		int destWordIdx = 0;
		int t = 0;

		// process 64 destination tids at a time to build one destination word per pass
		while (t + 64 <= projectedsize) {
			long word = 0L;
			for (int b = 0; b < 64; b++) {
				if (srcBits.get(tidMappingBuffer[t + b])) {
					word |= (1L << b);
				}
			}
			if (word != 0L) {
				// write the full word directly into the destination bitset
				dst.setWordForItem(item, destWordIdx, word);
			}
			destWordIdx++;
			t += 64;
		}

		// handle the remaining tids (fewer than 64) in the last partial word
		if (t < projectedsize) {
			long word = 0L;
			for (int b = 0; t + b < projectedsize; b++) {
				if (srcBits.get(tidMappingBuffer[t + b])) {
					word |= (1L << b);
				}
			}
			if (word != 0L) {
				dst.setWordForItem(item, destWordIdx, word);
			}
		}
	}
}