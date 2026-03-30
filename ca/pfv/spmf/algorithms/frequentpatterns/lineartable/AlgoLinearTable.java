package ca.pfv.spmf.algorithms.frequentpatterns.lineartable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;
/* This file is copyright (c) 2012-2025 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * Implementation of the LinearTable algorithm for mining frequent itemsets.
 * Uses linear table with grouping optimization. Items are converted to binary
 * representation, and support is computed via bitwise AND operations.
 * 
 * Paper: "Frequent itemset mining algorithm based on linear table" Journal of
 * Database Management, Volume 34, Issue 1.
 * 
 * Optimizations implemented in this code: - Long-based binary representation
 * for M ≤ 64 - Raw arrays instead of ArrayList for linear table - Early
 * termination in support counting - Pruning with A = A + (A & -A) formula (as
 * in the paper) - 100% primitive parallel arrays - NO object nodes
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoLinearTable {

	// ==================== Constants ====================
	/** Buffer size for file I/O */
	private static final int FILE_BUFFER_SIZE = 65536;

	/** Initial capacity for linear table */
	private static final int INITIAL_TABLE_CAPACITY = 4096;

	/** Initial capacity for item support array */
	private static final int INITIAL_ITEM_ARRAY_SIZE = 1024;

	/** Initial capacity for transaction database */
	private static final int INITIAL_DB_CAPACITY = 4096;

	/** StringBuilder capacity for output formatting */
	private static final int STRING_BUILDER_CAPACITY = 256;

	/** Enable debug output */
	private final boolean DEBUG_MODE = false;

	// ==================== Algorithm State ====================
	/** Start timestamp for performance measurement */
	private long startTimestamp;

	/** End timestamp for performance measurement */
	private long endTimestamp;

	/** Count of frequent itemsets found */
	private int frequentItemsetCount;

	/** Absolute minimum support threshold */
	private int minsupAbsolute;

	/** Total number of transactions in database */
	private int transactionCount;

	/** Collection of frequent itemsets (when saving to memory) */
	private Itemsets frequentItemsets;

	/** Count of pruning operations performed */
	private long pruningCount;

	/** Maximum transaction length in database */
	private int maxTransactionLength;

	/** Writer for output file */
	private BufferedWriter writer;

	/** Flag indicating whether to save results to memory */
	private boolean saveToMemory;

	// ==================== In-Memory Database ====================
	/** Stored transactions (raw item IDs, not yet filtered/sorted) */
	private int[][] transactionDatabase;

	/** Number of transactions stored */
	private int transactionDatabaseSize;

	// ==================== Item Mappings ====================
	/** Support of each item, indexed by original item ID */
	private int[] itemSupportArray;

	/** Maximum item ID in the dataset */
	private int maxItemId;

	/** Map: original item ID -> frequency rank (-1 if infrequent) */
	private int[] itemIdToRank;

	/** Map: frequency rank -> original item ID */
	private int[] rankToItemId;

	/** Number of frequent items (M in paper) */
	private int frequentItemCount;

	// ==================== Linear Table Structure (Parallel Arrays)
	// ====================
	/** Current size of linear table (number of nodes including root at index 0) */
	private int linearTableSize;

	/** Current capacity of linear table arrays */
	private int linearTableCapacity;

	/** Node item rank - CONSTRUCTION ONLY */
	private int[] nodeItemRank;

	/** Node parent index - CONSTRUCTION ONLY */
	private int[] nodeParentIndex;

	/** Node first child index - CONSTRUCTION ONLY */
	private int[] nodeChildIndex;

	/** Node next sibling index - CONSTRUCTION ONLY */
	private int[] nodeSiblingIndex;

	/** Root-level child lookup by rank (O(1) access) - CONSTRUCTION ONLY */
	private int[] rootChildByRank;

	/** Node previous occurrence index for d4 chain -CONSTRUCTION AND MINING */
	private int[] nodePrevIndex;

	/** Node frequency count - CONSTRUCTION AND MINING */
	private int[] nodeFrequency;

	/** Node binary representation for M ≤ 64 - CONSTRUCTION AND MINING */
	private long[] nodeBinaryLong;

	/** Node binary representation for M > 64 - - CONSTRUCTION AND MINING */
	private long[][] nodeBinaryArray;

	/** Header table: item rank -> last occurrence index */
	private int[] headerTable;

	/** Index of first root-level child */
	private int firstRootChildIndex;

	// ==================== Optimization Flags ====================
	/** True if M ≤ 64, allowing single long for binary representation */
	private boolean useLongRepresentation;

	// ==================== Reusable Buffers ====================
	/** Buffer for filtering/sorting transaction items (stored as ranks) */
	private int[] transactionBuffer;

	/** Buffer for building output itemsets */
	private int[] itemsetBuffer;

	/** Reusable StringBuilder for output */
	private final StringBuilder outputBuilder = new StringBuilder(STRING_BUILDER_CAPACITY);

	/**
	 * Default constructor
	 */
	public AlgoLinearTable() {
	}

	/**
	 * Run the LinearTable algorithm.
	 * 
	 * If an output path, frequent itemsets are saved to file and null is returned.
	 * If no output path is provided, frequent itemsets are returned.
	 * 
	 * @param input   path to input file (transaction database)
	 * @param output  path to output file, or null to store in memory
	 * @param minsupp minimum support threshold (0.0 to 1.0)
	 * @return Itemsets containing all frequent itemsets (if output is null)
	 * @throws IOException if file operations fail
	 */
	public Itemsets runAlgorithm(String input, String output, double minsupp) throws IOException {
		if (minsupp < 0 || minsupp > 1.0) {
			throw new IllegalArgumentException("minsupp must be in (0, 1]");
		}

		resetState();

		MemoryLogger.getInstance().reset();
		startTimestamp = System.currentTimeMillis();
		saveToMemory = (output == null);

		if (saveToMemory) {
			frequentItemsets = new Itemsets("FREQUENT ITEMSETS");
		} else {
			writer = new BufferedWriter(new FileWriter(output), FILE_BUFFER_SIZE);
		}

		try {
			// STEP 1: Read database, count supports, store in memory
			readDatabaseAndCountSupports(input);

			if (transactionCount == 0) {
				endTimestamp = System.currentTimeMillis();
				return frequentItemsets;
			}

			// Calculate absolute minimum support
			minsupAbsolute = (int) Math.ceil(minsupp * transactionCount);
			if (minsupAbsolute == 0) {
				minsupAbsolute = 1;
			}

			// Build item rankings
			buildItemRankings();

			// Determine representation mode
			useLongRepresentation = (frequentItemCount <= 64);

			// Initialize reusable buffers
			transactionBuffer = new int[maxTransactionLength];
			itemsetBuffer = new int[maxTransactionLength];

			// Output frequent 1-itemsets
			outputSingleItemsets();

			if (frequentItemCount <= 1) {
				endTimestamp = System.currentTimeMillis();
				return frequentItemsets;
			}

			MemoryLogger.getInstance().checkMemory();

			// STEP 2: Build Linear Table from in-memory database
			buildLinearTableFromMemory();

			// Free database memory - no longer needed
			transactionDatabase = null;

			// Debug output before freeing construction arrays
			if (DEBUG_MODE) {
				printHeaderTable();
				printLinearTable();
				printReconstructedDatabase();
			}

			// STEP 3: Free construction-only arrays (keep mining arrays)
			freeConstructionArrays();

			MemoryLogger.getInstance().checkMemory();

			// STEP 4: Mine Frequent Itemsets using shared arrays directly
			if (useLongRepresentation) {
				mineFrequentItemsetsLong();
			} else {
				mineFrequentItemsetsLongArray();
			}

			MemoryLogger.getInstance().checkMemory();
		} finally {
			if (writer != null) {
				writer.close();
			}
		}

		endTimestamp = System.currentTimeMillis();
		return frequentItemsets;
	}

	/**
	 * Single-pass: Read database, count item supports, and store transactions.
	 * 
	 * @param input path to input file
	 * @throws IOException if file reading fails
	 */
	private void readDatabaseAndCountSupports(String input) throws IOException {
		transactionCount = 0;
		transactionDatabaseSize = 0;
		maxItemId = 0;
		maxTransactionLength = 0;

		int currentArraySize = INITIAL_ITEM_ARRAY_SIZE;
		itemSupportArray = new int[currentArraySize];

		transactionDatabase = new int[INITIAL_DB_CAPACITY][];

		// Temporary buffer for parsing (will resize if needed)
		int[] tempItems = new int[64];

		try (BufferedReader reader = new BufferedReader(new FileReader(input), FILE_BUFFER_SIZE)) {
			String line;
			while ((line = reader.readLine()) != null) {
				// Skip comments and empty lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				int itemCount = 0;
				int len = line.length();
				int num = 0;
				boolean hasNum = false;

				// Parse line character by character
				for (int i = 0; i <= len; i++) {
					char c = (i < len) ? line.charAt(i) : ' ';
					if (c >= '0' && c <= '9') {
						num = num * 10 + (c - '0');
						hasNum = true;
					} else if (hasNum) {
						// Grow temp array if needed
						if (itemCount >= tempItems.length) {
							tempItems = Arrays.copyOf(tempItems, tempItems.length * 2);
						}
						tempItems[itemCount++] = num;

						// Grow support array if needed
						if (num >= currentArraySize) {
							int newSize = Math.max(currentArraySize * 2, num + 1);
							itemSupportArray = Arrays.copyOf(itemSupportArray, newSize);
							currentArraySize = newSize;
						}

						if (num > maxItemId) {
							maxItemId = num;
						}
						itemSupportArray[num]++;

						num = 0;
						hasNum = false;
					}
				}

				if (itemCount > 0) {
					// Grow transaction database if needed
					if (transactionDatabaseSize >= transactionDatabase.length) {
						transactionDatabase = Arrays.copyOf(transactionDatabase, transactionDatabase.length * 2);
					}

					// Store transaction (trimmed to actual size)
					transactionDatabase[transactionDatabaseSize++] = Arrays.copyOf(tempItems, itemCount);
					transactionCount++;

					if (itemCount > maxTransactionLength) {
						maxTransactionLength = itemCount;
					}
				}
			}
		}

		// Trim arrays to actual size
		if (maxItemId + 1 < currentArraySize) {
			itemSupportArray = Arrays.copyOf(itemSupportArray, maxItemId + 1);
		}
		if (transactionDatabaseSize < transactionDatabase.length) {
			transactionDatabase = Arrays.copyOf(transactionDatabase, transactionDatabaseSize);
		}
	}

	/**
	 * Build item rankings from support array. Items ranked by support (descending),
	 * ties broken by item ID (ascending).
	 */
	private void buildItemRankings() {
		// Count frequent items
		int count = 0;
		for (int i = 0; i <= maxItemId; i++) {
			if (itemSupportArray[i] >= minsupAbsolute) {
				count++;
			}
		}
		frequentItemCount = count;

		if (frequentItemCount == 0) {
			rankToItemId = new int[0];
			itemIdToRank = new int[maxItemId + 1];
			Arrays.fill(itemIdToRank, -1);
			return;
		}

		rankToItemId = new int[frequentItemCount];
		itemIdToRank = new int[maxItemId + 1];
		Arrays.fill(itemIdToRank, -1);

		// Collect frequent items: pairs[i][0] = itemId, pairs[i][1] = support
		int[][] pairs = new int[frequentItemCount][2];
		int idx = 0;
		for (int i = 0; i <= maxItemId; i++) {
			if (itemSupportArray[i] >= minsupAbsolute) {
				pairs[idx][0] = i;
				pairs[idx][1] = itemSupportArray[i];
				idx++;
			}
		}

		// Sort by support (descending), then item ID (ascending)
		Arrays.sort(pairs, (a, b) -> {
			int cmp = Integer.compare(b[1], a[1]);
			return (cmp != 0) ? cmp : Integer.compare(a[0], b[0]);
		});

		// Build mappings
		for (int rank = 0; rank < frequentItemCount; rank++) {
			int itemId = pairs[rank][0];
			rankToItemId[rank] = itemId;
			itemIdToRank[itemId] = rank;
		}
	}

	/**
	 * Initialize linear table parallel arrays with given capacity.
	 * 
	 * @param capacity initial capacity for arrays
	 */
	private void initializeLinearTableArrays(int capacity) {
		linearTableCapacity = capacity;

		// Construction-only arrays
		nodeItemRank = new int[capacity];
		nodeParentIndex = new int[capacity];
		nodeChildIndex = new int[capacity];
		nodeSiblingIndex = new int[capacity];

		// Shared arrays (used in both construction and mining)
		nodePrevIndex = new int[capacity];
		nodeFrequency = new int[capacity];

		if (useLongRepresentation) {
			nodeBinaryLong = new long[capacity];
			nodeBinaryArray = null;
		} else {
			nodeBinaryLong = null;
			nodeBinaryArray = new long[capacity][];
		}
	}

	/**
	 * Grow linear table parallel arrays to double capacity.
	 */
	private void growLinearTableArrays() {
		int newCapacity = linearTableCapacity * 2;

		// Construction-only arrays
		nodeItemRank = Arrays.copyOf(nodeItemRank, newCapacity);
		nodeParentIndex = Arrays.copyOf(nodeParentIndex, newCapacity);
		nodeChildIndex = Arrays.copyOf(nodeChildIndex, newCapacity);
		nodeSiblingIndex = Arrays.copyOf(nodeSiblingIndex, newCapacity);

		// Shared arrays
		nodePrevIndex = Arrays.copyOf(nodePrevIndex, newCapacity);
		nodeFrequency = Arrays.copyOf(nodeFrequency, newCapacity);

		if (useLongRepresentation) {
			nodeBinaryLong = Arrays.copyOf(nodeBinaryLong, newCapacity);
		} else {
			nodeBinaryArray = Arrays.copyOf(nodeBinaryArray, newCapacity);
		}

		linearTableCapacity = newCapacity;
	}

	/**
	 * Free construction-only arrays to reduce memory during mining phase. Keeps
	 * only the arrays needed for support calculation.
	 */
	private void freeConstructionArrays() {
		nodeItemRank = null;
		nodeParentIndex = null;
		nodeChildIndex = null;
		nodeSiblingIndex = null;
		rootChildByRank = null;
	}

	/**
	 * Build linear table from in-memory database using parallel arrays.
	 */
	private void buildLinearTableFromMemory() {
		// Initialize parallel arrays
		initializeLinearTableArrays(INITIAL_TABLE_CAPACITY);
		linearTableSize = 1; // Index 0 is root (null/empty)

		// Initialize root node (index 0) - all values default to 0
		nodeItemRank[0] = -1; // Invalid rank for root
		nodeFrequency[0] = 0;

		headerTable = new int[frequentItemCount];
		rootChildByRank = new int[frequentItemCount];
		firstRootChildIndex = 0;

		for (int t = 0; t < transactionDatabaseSize; t++) {
			int[] transaction = transactionDatabase[t];
			int filteredLength = filterAndSortTransaction(transaction);
			if (filteredLength > 0) {
				insertTransaction(transactionBuffer, filteredLength);
			}
		}
	}

	/**
	 * Filter transaction to keep only frequent items, then sort by rank.
	 * 
	 * @param transaction raw transaction (original item IDs)
	 * @return number of items in filtered result (stored in transactionBuffer)
	 */
	private int filterAndSortTransaction(int[] transaction) {
		int count = 0;

		for (int item : transaction) {
			if (item <= maxItemId) {
				int rank = itemIdToRank[item];
				if (rank >= 0) {
					transactionBuffer[count++] = rank;
				}
			}
		}

		if (count <= 1) {
			return count;
		}

		Arrays.sort(transactionBuffer, 0, count);

		return count;
	}

	/**
	 * Insert transaction into linear table using parallel arrays. New siblings are
	 * prepended for O(1) insertion.
	 * 
	 * @param ranks  the itemset as ranks
	 * @param length the itemset length
	 */
	private void insertTransaction(int[] ranks, int length) {
		int parentIndex = 0;
		long currentBinaryLong = 0L;
		long[] currentBinaryArray = useLongRepresentation ? null : new long[(frequentItemCount + 63) / 64];

		for (int i = 0; i < length; i++) {
			int rank = ranks[i];

			// Update binary representation
			if (useLongRepresentation) {
				currentBinaryLong |= (1L << rank);
			} else {
				int wordIndex = rank / 64;
				int bitIndex = rank % 64;
				currentBinaryArray[wordIndex] |= (1L << bitIndex);
			}

			// Look for existing child with this rank
			int existingIndex = findChildByRank(parentIndex, rank);

			if (existingIndex > 0) {
				// Node exists - increment frequency
				nodeFrequency[existingIndex]++;
				parentIndex = existingIndex;
			} else {
				// Create new node - ensure capacity
				if (linearTableSize >= linearTableCapacity) {
					growLinearTableArrays();
				}

				int newIndex = linearTableSize++;

				// Initialize new node in parallel arrays
				nodeItemRank[newIndex] = rank;
				nodeParentIndex[newIndex] = parentIndex;
				nodeChildIndex[newIndex] = 0;
				nodeSiblingIndex[newIndex] = 0;
				nodePrevIndex[newIndex] = 0;
				nodeFrequency[newIndex] = 1;

				if (useLongRepresentation) {
					nodeBinaryLong[newIndex] = currentBinaryLong;
				} else {
					nodeBinaryArray[newIndex] = currentBinaryArray.clone();
				}

				// Link into tree structure
				linkNewNode(parentIndex, rank, newIndex);

				// Update header table (d4 chain)
				int prevOccurrence = headerTable[rank];
				if (prevOccurrence > 0) {
					nodePrevIndex[newIndex] = prevOccurrence;
				}
				headerTable[rank] = newIndex;

				parentIndex = newIndex;
			}
		}
	}

	/**
	 * Find child node with given rank under parent using parallel arrays.
	 * 
	 * @param parentIndex index of parent node (0 = root)
	 * @param rank        item rank to find
	 * @return index of child node, or 0 if not found
	 */
	private int findChildByRank(int parentIndex, int rank) {
		if (parentIndex == 0) {
			// Root level: O(1) lookup
			return rootChildByRank[rank];
		}

		// Non-root: traverse sibling chain
		int childIdx = nodeChildIndex[parentIndex];

		while (childIdx > 0) {
			if (nodeItemRank[childIdx] == rank) {
				return childIdx;
			}
			childIdx = nodeSiblingIndex[childIdx];
		}

		return 0;
	}

	/**
	 * Link new node into tree structure using O(1) prepend. Uses parallel arrays
	 * instead of node objects.
	 * 
	 * @param parentIndex index of parent node (0 = root)
	 * @param rank        item rank of new node
	 * @param newIndex    index of new node
	 */
	private void linkNewNode(int parentIndex, int rank, int newIndex) {
		if (parentIndex == 0) {
			// Root level
			rootChildByRank[rank] = newIndex;
			nodeSiblingIndex[newIndex] = firstRootChildIndex;
			firstRootChildIndex = newIndex;
		} else {
			// Non-root: prepend to parent's child list
			nodeSiblingIndex[newIndex] = nodeChildIndex[parentIndex];
			nodeChildIndex[parentIndex] = newIndex;
		}
	}

	/**
	 * Mine frequent itemsets using long representation (M ≤ 64). Uses shared
	 * parallel arrays directly - no separate mining arrays needed.
	 */
	private void mineFrequentItemsetsLong() throws IOException {
		// Use shared arrays directly - no copying needed
		final int[] prev = nodePrevIndex;
		final int[] freq = nodeFrequency;
		final long[] binary = nodeBinaryLong;
		final int[] header = headerTable;
		final int minSup = minsupAbsolute;

		for (int groupRank = 1; groupRank < frequentItemCount; groupRank++) {
			long groupBit = 1L << groupRank;
			int maxPossibleSupport = itemSupportArray[rankToItemId[groupRank]];

			long maxPrefix = (1L << groupRank) - 1;
			long prefix = 1L;

			while (prefix <= maxPrefix) {
				long candidate = prefix | groupBit;
				int support = calculateSupportLong(candidate, groupRank, maxPossibleSupport, prev, freq, binary, header,
						minSup);

				if (support >= minSup) {
					outputItemsetFromBits(candidate, groupRank, support);
					prefix++;
				} else {
					// Pruning: A = A + (A & -A)
					long lowBit = prefix & (-prefix);
					prefix = prefix + lowBit;
					pruningCount++;
				}
			}
		}
	}

	/**
	 * Calculate support using parallel arrays (M ≤ 64). Cache-friendly: sequential
	 * access to primitive arrays.
	 * 
	 * @param candidate          the candidate itemset as bit representation
	 * @param rightmostRank      the rank of the rightmost (highest) item
	 * @param maxPossibleSupport maximum possible support for early termination
	 * @param prev               prevIndex array
	 * @param freq               frequency array
	 * @param binary             binary representation array
	 * @param header             header table
	 * @param minSup             minimum support threshold
	 * @return support count for the candidate
	 */
	private int calculateSupportLong(long candidate, int rightmostRank, int maxPossibleSupport, int[] prev, int[] freq,
			long[] binary, int[] header, int minSup) {
		int position = header[rightmostRank];
		int support = 0;
		int remaining = maxPossibleSupport;

		while (position > 0) {
			long nodeBinary = binary[position];
			int nodeFreq = freq[position];

			if ((nodeBinary & candidate) == candidate) {
				support += nodeFreq;
			}

			remaining -= nodeFreq;
			if (support + remaining < minSup) {
				return support;
			}

			position = prev[position];
		}

		return support;
	}

	/**
	 * Build and output itemset from bit representation.
	 * 
	 * @param bits    bit representation of itemset
	 * @param maxRank maximum rank to check
	 * @param support support count
	 */
	private void outputItemsetFromBits(long bits, int maxRank, int support) throws IOException {
		int count = 0;
		for (int rank = 0; rank <= maxRank; rank++) {
			if ((bits & (1L << rank)) != 0) {
				itemsetBuffer[count++] = rankToItemId[rank];
			}
		}
		saveItemsetFromBuffer(count, support);
	}

	/**
	 * Mine frequent itemsets using long array representation (M > 64). Uses shared
	 * parallel arrays directly - no separate mining arrays needed.
	 */
	private void mineFrequentItemsetsLongArray() throws IOException {
		int numWords = (frequentItemCount + 63) / 64;

		// Use shared arrays directly - no copying needed
		final int[] prev = nodePrevIndex;
		final int[] freq = nodeFrequency;
		final long[][] binaryArrays = nodeBinaryArray;
		final int[] header = headerTable;
		final int minSup = minsupAbsolute;

		/** Prefix: represents subset of items {0, 1, ..., groupRank-1} */
		long[] prefix = new long[numWords];

		/** Candidate = prefix | groupBit, used for support calculation */
		long[] candidate = new long[numWords];

		for (int groupRank = 1; groupRank < frequentItemCount; groupRank++) {
			int groupWordIndex = groupRank / 64;
			int groupBitIndex = groupRank % 64;
			long groupBit = 1L << groupBitIndex;
			int maxPossibleSupport = itemSupportArray[rankToItemId[groupRank]];

			// Reset prefix to 1
			Arrays.fill(prefix, 0);
			prefix[0] = 1L;

			// Precompute limit for this groupRank
			int limitWord = (groupRank - 1) / 64;
			int lastBitInLimitWord = (groupRank - 1) % 64;
			long limitMask = (lastBitInLimitWord == 63) ? -1L : ((1L << (lastBitInLimitWord + 1)) - 1);

			while (true) {
				// Build candidate = prefix | groupBit
				System.arraycopy(prefix, 0, candidate, 0, numWords);
				candidate[groupWordIndex] |= groupBit;

				int support = calculateSupportLongArray(candidate, groupRank, maxPossibleSupport, prev, freq,
						binaryArrays, header, minSup);

				if (support >= minSup) {
					outputItemsetFromBitsArray(candidate, groupRank, support);

					// Increment prefix by 1
					if (!incrementPrefix(prefix, limitWord, limitMask)) {
						break;
					}
				} else {
					// Prune: prefix = prefix + (prefix & -prefix)
					if (!prunePrefix(prefix, limitWord, limitMask)) {
						break;
					}
					pruningCount++;
				}
			}
		}
	}

	/**
	 * Increment prefix by 1 using simple multi-word arithmetic.
	 * 
	 * @param prefix    the prefix array to increment
	 * @param limitWord the highest word index that can have bits set
	 * @param limitMask mask for valid bits in limitWord
	 * @return false if increment would exceed the limit (prefix >= 2^groupRank)
	 */
	private boolean incrementPrefix(long[] prefix, int limitWord, long limitMask) {
		// Simple add-1 with carry propagation
		for (int w = 0; w <= limitWord; w++) {
			long oldVal = prefix[w];
			prefix[w] = oldVal + 1;

			if (prefix[w] != 0) {
				// No overflow in this word - check if we exceeded limit
				if (w == limitWord && (prefix[w] & ~limitMask) != 0) {
					return false;
				}
				return true;
			}
			// prefix[w] == 0 means overflow, continue to next word
		}
		// Complete overflow past limitWord - we've exhausted all prefixes
		return false;
	}

	/**
	 * Apply A = A + (A & -A) pruning using efficient multi-word arithmetic. This
	 * skips all subsets that share the same lowest set bit.
	 * 
	 * @param prefix    the prefix array to prune
	 * @param limitWord the highest word index that can have bits set
	 * @param limitMask mask for valid bits in limitWord
	 * @return false if result would exceed the limit
	 */
	private boolean prunePrefix(long[] prefix, int limitWord, long limitMask) {
		// Find the word containing the lowest set bit
		for (int w = 0; w <= limitWord; w++) {
			if (prefix[w] != 0) {
				// Found it - get the lowest bit
				long lowBit = prefix[w] & -prefix[w];

				// Add lowBit to prefix[w]
				long oldVal = prefix[w];
				prefix[w] = oldVal + lowBit;

				// Check for overflow (unsigned comparison)
				if (Long.compareUnsigned(prefix[w], oldVal) >= 0) {
					// No overflow in this word
					if (w == limitWord && (prefix[w] & ~limitMask) != 0) {
						return false;
					}
					return true;
				}

				// Overflow occurred - propagate carry
				prefix[w] = 0;
				for (int ww = 0; ww < w; ww++) {
					prefix[ww] = 0;
				}

				// Carry propagation
				for (int ww = w + 1; ww <= limitWord; ww++) {
					prefix[ww]++;
					if (prefix[ww] != 0) {
						if (ww == limitWord && (prefix[ww] & ~limitMask) != 0) {
							return false;
						}
						return true;
					}
				}
				return false;
			}
		}
		return false;
	}

	/**
	 * Calculate support using parallel arrays (M > 64). Optimized version that only
	 * checks words up to rightmostRank.
	 * 
	 * @param candidate          the candidate itemset as bit array
	 * @param rightmostRank      the rank of the rightmost (highest) item
	 * @param maxPossibleSupport maximum possible support for early termination
	 * @param prev               prevIndex array
	 * @param freq               frequency array
	 * @param binaryArrays       binary representation arrays
	 * @param header             header table
	 * @param minSup             minimum support threshold
	 * @return support count for the candidate
	 */
	private int calculateSupportLongArray(long[] candidate, int rightmostRank, int maxPossibleSupport, int[] prev,
			int[] freq, long[][] binaryArrays, int[] header, int minSup) {
		int position = header[rightmostRank];
		int support = 0;
		int remaining = maxPossibleSupport;

		// Only check words that could have bits set (0 to maxWord inclusive)
		final int maxWord = rightmostRank / 64;

		// Cache first candidate word for common single-word case
		final long cand0 = candidate[0];

		while (position > 0) {
			long[] nodeRep = binaryArrays[position];
			int nodeFreq = freq[position];

			// Check subset containment with early exit on first mismatch
			boolean contained = ((nodeRep[0] & cand0) == cand0);

			// Check remaining words if needed
			for (int w = 1; w <= maxWord && contained; w++) {
				long candWord = candidate[w];
				if ((nodeRep[w] & candWord) != candWord) {
					contained = false;
				}
			}

			if (contained) {
				support += nodeFreq;
			}

			remaining -= nodeFreq;
			if (support + remaining < minSup) {
				return support;
			}

			position = prev[position];
		}

		return support;
	}

	/**
	 * Output itemset from long array bit representation.
	 * 
	 * @param bits    bit array representation of itemset
	 * @param maxRank maximum rank to check
	 * @param support support count
	 */
	private void outputItemsetFromBitsArray(long[] bits, int maxRank, int support) throws IOException {
		int count = 0;
		for (int rank = 0; rank <= maxRank; rank++) {
			int wordIndex = rank / 64;
			int bitIndex = rank % 64;
			if ((bits[wordIndex] & (1L << bitIndex)) != 0) {
				itemsetBuffer[count++] = rankToItemId[rank];
			}
		}
		saveItemsetFromBuffer(count, support);
	}

	/**
	 * Save itemset from buffer to output.
	 * 
	 * @param length  number of items in itemsetBuffer
	 * @param support support count
	 */
	private void saveItemsetFromBuffer(int length, int support) throws IOException {
		frequentItemsetCount++;

		// Sort the itemset in ascending order
		Arrays.sort(itemsetBuffer, 0, length);

		if (saveToMemory) {
			Itemset itemsetObj = new Itemset(Arrays.copyOf(itemsetBuffer, length));
			itemsetObj.setAbsoluteSupport(support);
			frequentItemsets.addItemset(itemsetObj, length);
		} else {
			outputBuilder.setLength(0);
			for (int i = 0; i < length; i++) {
				if (i > 0)
					outputBuilder.append(' ');
				outputBuilder.append(itemsetBuffer[i]);
			}
			outputBuilder.append(" #SUP: ").append(support);
			writer.write(outputBuilder.toString());
			writer.newLine();
		}
	}

	/**
	 * Output frequent 1-itemsets.
	 */
	private void outputSingleItemsets() throws IOException {
		for (int rank = 0; rank < frequentItemCount; rank++) {
			int itemId = rankToItemId[rank];
			int support = itemSupportArray[itemId];

			frequentItemsetCount++;

			if (saveToMemory) {
				Itemset itemsetObj = new Itemset(new int[] { itemId });
				itemsetObj.setAbsoluteSupport(support);
				frequentItemsets.addItemset(itemsetObj, 1);
			} else {
				outputBuilder.setLength(0);
				outputBuilder.append(itemId);
				outputBuilder.append(" #SUP: ").append(support);
				writer.write(outputBuilder.toString());
				writer.newLine();
			}
		}
	}

	/**
	 * Reset all algorithm state.
	 */
	private void resetState() {
		startTimestamp = 0;
		endTimestamp = 0;
		frequentItemsetCount = 0;
		pruningCount = 0;
		minsupAbsolute = 0;
		transactionCount = 0;
		frequentItemsets = null;
		writer = null;
		saveToMemory = false;

		transactionDatabase = null;
		transactionDatabaseSize = 0;

		itemSupportArray = null;
		maxItemId = 0;
		itemIdToRank = null;
		rankToItemId = null;
		frequentItemCount = 0;

		// Reset linear table parallel arrays
		linearTableSize = 0;
		linearTableCapacity = 0;
		nodeItemRank = null;
		nodeParentIndex = null;
		nodeChildIndex = null;
		nodeSiblingIndex = null;
		nodePrevIndex = null;
		nodeFrequency = null;
		nodeBinaryLong = null;
		nodeBinaryArray = null;

		headerTable = null;
		firstRootChildIndex = 0;
		rootChildByRank = null;

		useLongRepresentation = false;
		transactionBuffer = null;
		itemsetBuffer = null;
		outputBuilder.setLength(0);
	}

	/**
	 * Print algorithm statistics.
	 */
	public void printStats() {
		String longRepresentation = (useLongRepresentation) ? "(LONG)" : "(LONG_ARRAY)";
		System.out.println(
				"============= LINEAR TABLE ALGORITHM 2.65 " + longRepresentation + " =============");
		if (DEBUG_MODE) {
			System.out.println(" DEBUG_MODE information:");
			System.out.println(" - Transactions count: " + transactionCount);
			System.out.println(" - Max. transaction length: " + maxTransactionLength);
			System.out.println(" - Linear table size: " + linearTableSize);
			System.out.println(" - Pruning operations: " + pruningCount);
			System.out.println(" - Using long representation: " + useLongRepresentation);
		}
		System.out.println(" Frequent itemsets count: " + frequentItemsetCount);
		System.out.println(" Maximum memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===========================================================");
	}

	// ======================== METHODS FOR DEBUGGING ====================

	/**
	 * Print linear table for debugging using parallel arrays. Note: Must be called
	 * before freeConstructionArrays().
	 */
	private void printLinearTable() {
		if (nodeItemRank == null) {
			System.out.println("Linear Table: (construction arrays freed - not available)");
			return;
		}
		System.out.println("Linear Table:");
		System.out.println("Index\t(rank[item], child, sibling, parent, prev, freq, binary)");
		for (int i = 0; i < linearTableSize; i++) {
			if (i == 0) {
				System.out.println(i + "\tΦ (root)");
			} else {
				int itemId = rankToItemId[nodeItemRank[i]];
				String binary = useLongRepresentation ? Long.toBinaryString(nodeBinaryLong[i])
						: Arrays.toString(nodeBinaryArray[i]);
				System.out.printf("%d\t(%d[%d], %d, %d, %d, %d, %d, %s)%n", i, nodeItemRank[i], itemId,
						nodeChildIndex[i], nodeSiblingIndex[i], nodeParentIndex[i], nodePrevIndex[i], nodeFrequency[i],
						binary);
			}
		}
	}

	/**
	 * Print header table for debugging.
	 */
	private void printHeaderTable() {
		System.out.println("Header Table:");
		System.out.println("Rank\tItem\tLast Index");
		for (int rank = 0; rank < frequentItemCount; rank++) {
			System.out.println(rank + "\t" + rankToItemId[rank] + "\t" + headerTable[rank]);
		}
	}

	/**
	 * Reconstruct and print the transaction database from the linear table. For
	 * each node, calculates how many transactions ended exactly at that node. Note:
	 * Must be called before freeConstructionArrays().
	 */
	private void printReconstructedDatabase() {
		if (nodeItemRank == null) {
			System.out.println("Reconstructed Database: (construction arrays freed - not available)");
			return;
		}
		System.out.println(
				"For debugging, we can reconstruct the transaction database (without infrequent items) from the linear table:");
		System.out.println("-----------------------------------");

		int transactionId = 0;

		for (int i = 1; i < linearTableSize; i++) {
			int childrenFreqSum = getChildrenFrequencySum(i);
			int endedHere = nodeFrequency[i] - childrenFreqSum;

			if (endedHere > 0) {
				int[] transaction = reconstructTransaction(i);

				for (int f = 0; f < endedHere; f++) {
					System.out.print("T" + transactionId + ": ");
					for (int j = 0; j < transaction.length; j++) {
						if (j > 0)
							System.out.print(" ");
						System.out.print(transaction[j]);
					}
					System.out.println();
					transactionId++;
				}
			}
		}

		System.out.println("-----------------------------------");
		System.out.println("Total transactions: " + transactionId);
	}

	/**
	 * Get the sum of frequencies of all direct children of a node. Note: Must be
	 * called before freeConstructionArrays().
	 * 
	 * @param nodeIndex index of the parent node
	 * @return sum of children's frequencies
	 */
	private int getChildrenFrequencySum(int nodeIndex) {
		int sum = 0;
		int childIdx = nodeChildIndex[nodeIndex];

		while (childIdx > 0) {
			sum += nodeFrequency[childIdx];
			childIdx = nodeSiblingIndex[childIdx];
		}

		return sum;
	}

	/**
	 * Reconstruct a transaction by walking from a node up to the root. Note: Must
	 * be called before freeConstructionArrays().
	 * 
	 * @param nodeIndex index of the node
	 * @return array of original item IDs in the transaction
	 */
	private int[] reconstructTransaction(int nodeIndex) {
		// First pass: count depth
		int depth = 0;
		int currentIdx = nodeIndex;
		while (currentIdx > 0) {
			depth++;
			currentIdx = nodeParentIndex[currentIdx];
		}

		// Second pass: collect items
		int[] items = new int[depth];
		currentIdx = nodeIndex;
		int pos = depth - 1;
		while (currentIdx > 0) {
			items[pos--] = rankToItemId[nodeItemRank[currentIdx]];
			currentIdx = nodeParentIndex[currentIdx];
		}

		return items;
	}
}