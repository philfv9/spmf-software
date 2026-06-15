package ca.pfv.spmf.algorithms.frequentpatterns.hmine_fast;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * A highly optimized implementation of the HMine algorithm for mining frequent
 * itemsets from a transaction database. HMine is describe in this paper:
 *
 * Pei et al. (2007) H-Mine: Fast and space-preserving frequent pattern mining
 * in large databases. IIE Transactions, 39, 593-605.<br/>
 * <br/>
 *
 * This new version (SPMF 2.66+) is optimized with three main optimizations to improve
 * performance: (1) a pointer pool and row pool is used to reduce garbage collection, (2)
 * transaction merging (called suffix compression) and infrequent items are
 * ignored, (3) single-path optimization (generate all subsets when all items
 * have the same support).
 *
 * @see Row
 * @see SuffixCompressor
 * @author Philippe Fournier-Viger, 2026
 */
public class AlgoHMine_FAST {

	/** the time the algorithm started */
	long startTimestamp = 0;

	/** the time the algorithm terminated */
	long endTimestamp = 0;

	/** the number of frequent itemsets found */
	int patternCount = 0;

	/** the maximum memory usage of last execution */
	double maxMemoryUsage = 0;

	/** if true, debugging information will be shown in the console */
	boolean DEBUG = false;

	/** the minimum support threshold (absolute count) */
	int minSupport = 0;

	/** maximum number of items in a pattern (default: unlimited) */
	int maxItemsetSize = Integer.MAX_VALUE;

	/**
	 * The cell array holding all transactions. Each transaction is a sequence of
	 * renamed item IDs (sorted ascending) followed by a -1 separator. Written once
	 * during loading; never modified afterward.
	 */
	int[] cells;

	/**
	 * For each position i in cells[] where cells[i] != -1, transEnd[i] is the index
	 * of the last item in the same transaction. Enables O(1) lookup of where a
	 * transaction ends.
	 */
	private int[] transactionsEndingPoints;

	/** largest original item ID seen in the dataset */
	private int maxOldItemId;

	/** number of frequent items (number of renamed items) */
	private int maxNewItemId;

	/**
	 * Handles bidirectional conversion between original item IDs and renamed IDs.
	 * Items are renamed by ascending support.
	 */
	private ItemNameConverter nameConverter;

	/**
	 * Support counts of the sorted frequent items, indexed by renamed ID.
	 */
	private int[] frequentSupports;

	/** number of transactions in the database */
	private int transactionCount;

	/** total item occurrences across all transactions */
	private int totalItemOccurrences;

	/** length of the longest transaction in items */
	private int maxTransactionLength;

	// ================================================================
	// Row pointer pool (stored in three parallel int arrays)
	// ================================================================

	/** cell position that each pointer references */
	private int[] pointerPool;

	/** transaction weight for each pointer (>1 when merged) */
	private int[] pointerWeight;

	/** index of next pointer in the linked list (-1 = end) */
	private int[] nextPointerIndex;

	/** number of pointers currently allocated */
	private int pointerPoolSize;

	// ================================================================
	// Row pool
	// ================================================================

	/** Pool of pre-allocated Row objects */
	private Row[] rowPool;

	/** Next available position in the row pool */
	private int rowPoolPos;

	// ================================================================
	// Recursion workspace (one per recursion depth)
	// ================================================================

	/** buffer for building the current itemset */
	private int[] itemsetBuffer = null;

	/** projected rows for each recursion level */
	private Row[][] projectedRowsStack;

	/** frequent items found in the projection at each level */
	private int[][] newRowItemsStack;

	/** indices of items whose support count was modified (for cleanup) */
	private int[][] usedIndicesStack;

	/** isRelevant[item] = true if item is frequent at this level */
	private boolean[][] isRelevantStack;

	/** support counts accumulated during Phase 1 */
	private int[][] supportCountStack;

	/** current recursion depth */
	private int recursionDepth;

	/** maximum allowed recursion depth */
	private int maxRecursionDepth;

	// ================================================================
	// Other fields
	// ================================================================

	/** Helper class for suffix merging */
	private SuffixCompressor suffixCompressor;

	/** Reusable buffer for formatting output lines */
	private StringBuilder outputBuffer = null;

	/** Reusable char array for writing out itemsets */
	private char[] outputChars = null;

	/** Writer for the output file */
	BufferedWriter writer = null;

	/**
	 * Default constructor.
	 */
	public AlgoHMine_FAST() {
	}

	/**
	 * Run the HMine algorithm to find all frequent itemsets.
	 *
	 * @param input      path to the input transaction database file
	 * @param output     path to the output file for writing results
	 * @param minSupport the minimum support threshold as a fraction (e.g., 0.5
	 *                   means 50% of transactions)
	 * @throws IOException if an error occurs reading or writing files
	 */
	public void runAlgorithm(String input, String output, double minSupport) throws IOException {

		// initialize output and statistics fields
		outputBuffer = new StringBuilder(256);
		outputChars = new char[256];
		patternCount = 0;
		MemoryLogger.getInstance().reset();
		startTimestamp = System.currentTimeMillis();
		maxMemoryUsage = 0;
		writer = new BufferedWriter(new FileWriter(output), 1 << 16);

		try {
			// (1) first pass: count item supports and collect
			//     database sizing statistics
			int[] itemSupport = scanDatabase(input);

			// convert relative support to absolute count
			this.minSupport = (int) Math.ceil(minSupport * transactionCount);

			// (2) identify frequent items, sort by ascending
			//     support, and build old<->new name mappings
			int frequentItemCount = identifyFrequentItems(itemSupport);
			if (frequentItemCount == 0) {
				// no frequent items — nothing to mine
				endTimestamp = System.currentTimeMillis();
				maxMemoryUsage = MemoryLogger.getInstance().checkMemory();
				return;
			}

			// (3) allocate pools, workspace arrays, and cell array
			initializeDataStructures();

			// (4) second pass: load transactions into cells and
			// build the initial H-struct (header table + pointers)
			Row[] initialRows = buildHStruct(input);

			// print debug info if enabled
			if (DEBUG) {
				System.out.println("------ INITIAL HStruct -----");
				System.out.println("Transaction count: " + transactionCount);
				System.out.println("Frequent items: " + maxNewItemId);
				System.out.println("Max transaction length: " + maxTransactionLength);
				System.out.println("Max recursion depth: " + maxRecursionDepth);
				System.out.println("Min support: " + this.minSupport);
				for (int i = 0; i < maxNewItemId; i++) {
					Row row = initialRows[i];
					if (row != null) {
						System.out.println("  Item " + i + " (old:" + nameConverter.toOldName(i) + ") sup:" + row.support);
					}
				}
			}

			MemoryLogger.getInstance().checkMemory();

			// (5) build the list of all frequent item IDs 
			int[] initialRowItems = new int[maxNewItemId];
			for (int i = 0; i < maxNewItemId; i++) {
				initialRowItems[i] = i;
			}
			
			// Start the depth-first search
			hmine(itemsetBuffer, 0, initialRows, initialRowItems, maxNewItemId);

			MemoryLogger.getInstance().checkMemory();

		} finally {
			// close the writer 
			if (writer != null) {
				writer.close();
			}
		}

		// Record final statistics
		maxMemoryUsage = MemoryLogger.getInstance().checkMemory();
		endTimestamp = System.currentTimeMillis();

		// Free memory
		cells = null;
		transactionsEndingPoints = null;
		pointerPool = null;
		pointerWeight = null;
		nextPointerIndex = null;
		rowPool = null;
		projectedRowsStack = null;
		newRowItemsStack = null;
		usedIndicesStack = null;
		isRelevantStack = null;
		supportCountStack = null;
		frequentSupports = null;
		nameConverter = null;
		if (suffixCompressor != null) {
			suffixCompressor.dispose();
			suffixCompressor = null;
		}
		outputBuffer = null;
		outputChars = null;
	}
  
	/**
	 * First pass over the database file. Reads every transaction to count the
	 * support of each item and to collect sizing statistics (transaction count, max
	 * transaction length, total item occurrences). Also, find the max item ID.
	 *
	 * @param input path to the input file
	 * @return the support count for each item, indexed by original item ID
	 * @throws IOException if the file cannot be read
	 */
	private int[] scanDatabase(String input) throws IOException {
		int[] tempSupport = new int[10000];
		maxOldItemId = 0;
		transactionCount = 0;
		totalItemOccurrences = 0;
		maxTransactionLength = 0;

		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)), 1 << 16);
			String line;

			// for each line (transaction)
			while ((line = reader.readLine()) != null) {
				// skip comment and metadata lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				// parse space-separated item IDs from the line
				int lineLength = line.length();
				int currentNumber = 0;
				boolean hasNumber = false;
				int itemsInTransaction = 0;

				for (int i = 0; i <= lineLength; i++) {
					char character = (i < lineLength) ? line.charAt(i) : ' ';
					if (character >= '0' && character <= '9') {
						// accumulate digits
						currentNumber = currentNumber * 10 + (character - '0');
						hasNumber = true;
					} else if (hasNumber) {
						// end of a number — process the item

						// grow support array if needed
						if (currentNumber >= tempSupport.length) {
							tempSupport = Arrays.copyOf(tempSupport,
									Math.max(currentNumber + 1, tempSupport.length * 2));
						}

						// increment the item's support count
						tempSupport[currentNumber]++;

						// track the largest item ID
						if (currentNumber > maxOldItemId) {
							maxOldItemId = currentNumber;
						}

						// update occurrence and transaction-item counts
						totalItemOccurrences++;
						itemsInTransaction++;
						currentNumber = 0;
						hasNumber = false;
					}
				}

				// update transaction count and max length
				if (itemsInTransaction > 0) {
					transactionCount++;
					if (itemsInTransaction > maxTransactionLength) {
						maxTransactionLength = itemsInTransaction;
					}
				}
			}
		} finally {
			if (reader != null)
				reader.close();
		}

		return tempSupport;
	}

	/**
	 * Identify all frequent items, sort them
	 * by ascending support, and rename items according to this order using ItemNameConverter
	 *
	 * @param itemSupport support count for each original item ID
	 * @return the number of frequent items (0 if none are frequent)
	 */
	private int identifyFrequentItems(int[] itemSupport) {
		// count how many items meet the minimum support threshold
		int frequentItemCount = 0;
		for (int item = 0; item <= maxOldItemId; item++) {
			if (itemSupport[item] >= this.minSupport) {
				frequentItemCount++;
			}
		}

		if (frequentItemCount == 0) {
			return 0;
		}

		// collect frequent items and their support counts
		// into paired parallel arrays
		int[] frequentItems = new int[frequentItemCount];
		frequentSupports = new int[frequentItemCount];
		for (int item = 0, i = 0; item <= maxOldItemId; item++) {
			if (itemSupport[item] >= this.minSupport) {
				frequentItems[i] = item;
				frequentSupports[i] = itemSupport[item];
				i++;
			}
		}

		// sort both arrays by ascending support (ties by item ID)
		sortBySupport(frequentItems, frequentSupports);

		// assign new contiguous names 0..frequentItemCount-1
		// in the sorted order (lowest support = name 0)
		nameConverter = new ItemNameConverter(frequentItemCount, 0);
		maxNewItemId = frequentItemCount;

		for (int i = 0; i < frequentItemCount; i++) {
			nameConverter.assignNewName(frequentItems[i]);
		}

		return frequentItemCount;
	}

	/**
	 * Allocate all internal data structures sized according to the dataset
	 * statistics: pointer pool, row pool, suffix compressor, per-recursion-level
	 * workspace arrays, and the cell/transEnd arrays.
	 */
	private void initializeDataStructures() {

		// maximum recursion depth is bounded by the minimum of:
		// longest transaction length, number of frequent items,
		// and the user-specified maximum itemset size
		int effectiveMaxDepth = Math.min(maxTransactionLength, maxNewItemId);
		effectiveMaxDepth = Math.min(effectiveMaxDepth, maxItemsetSize);
		maxRecursionDepth = effectiveMaxDepth + 1;

		// prefix buffer: holds the current itemset being built
		itemsetBuffer = new int[maxRecursionDepth + 2];

		// pointer pool: three parallel arrays for linked-list
		// nodes (cell position, weight, next index)
		int initialPoolSize = Math.max(4096, totalItemOccurrences * 2);
		pointerPool = new int[initialPoolSize];
		pointerWeight = new int[initialPoolSize];
		nextPointerIndex = new int[initialPoolSize];

		// row pool: pre-allocated Row objects to avoid GC pressure
		int initialRowPoolSize = Math.max(1024, maxNewItemId * 4);
		rowPool = new Row[initialRowPoolSize];
		for (int i = 0; i < initialRowPoolSize; i++) {
			rowPool[i] = new Row();
		}

		// suffix compressor: handles transaction merging via
		// epoch-based hash table
		suffixCompressor = new SuffixCompressor(Math.max(1024, transactionCount));

		// per-recursion-level workspace arrays, each sized
		// to hold all possible renamed item IDs
		projectedRowsStack = new Row[maxRecursionDepth][];
		newRowItemsStack = new int[maxRecursionDepth][];
		usedIndicesStack = new int[maxRecursionDepth][];
		isRelevantStack = new boolean[maxRecursionDepth][];
		supportCountStack = new int[maxRecursionDepth][];
		for (int i = 0; i < maxRecursionDepth; i++) {
			projectedRowsStack[i] = new Row[maxNewItemId];
			newRowItemsStack[i] = new int[maxNewItemId];
			usedIndicesStack[i] = new int[maxNewItemId];
			isRelevantStack[i] = new boolean[maxNewItemId];
			supportCountStack[i] = new int[maxNewItemId];
		}

		// cell array for storing all transactions end-to-end,
		// plus the transaction-end lookup array
		int originalDataSize = totalItemOccurrences + transactionCount;
		cells = new int[originalDataSize];
		transactionsEndingPoints = new int[originalDataSize];
	}

	/**
	 * Second pass over the database file. Reads every transaction, keeps only
	 * frequent items (renaming them), sorts them, stores them into the immutable
	 * {@link #cells} array, and builds the initial H-struct by creating a
	 * {@link Row} per frequent item with pointers to every occurrence.
	 *
	 * @param input path to the input file
	 * @return the initial header table (one Row per frequent item, indexed by
	 *         renamed item ID)
	 * @throws IOException if the file cannot be read
	 */
	private Row[] buildHStruct(String input) throws IOException {

		// create one Row per frequent item in the header table
		Row[] initialRows = new Row[maxNewItemId];
		for (int i = 0; i < maxNewItemId; i++) {
			Row row = getPooledRow();
			row.item = i;
			row.support = frequentSupports[i];
			initialRows[i] = row;
		}

		BufferedReader reader = null;
		int currentCellIndex = 0;
		// temporary buffer to hold renamed items for one transaction
		int[] tempTransaction = new int[maxTransactionLength > 0 ? maxTransactionLength : 16];

		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)), 1 << 16);
			String line;

			// for each line (transaction)
			while ((line = reader.readLine()) != null) {
				// skip comment and metadata lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				// parse items, keeping only frequent ones (renamed)
				int lineLength = line.length();
				int currentNumber = 0;
				boolean hasNumber = false;
				int tempSize = 0;

				for (int i = 0; i <= lineLength; i++) {
					char character = (i < lineLength) ? line.charAt(i) : ' ';
					if (character >= '0' && character <= '9') {
						currentNumber = currentNumber * 10 + (character - '0');
						hasNumber = true;
					} else if (hasNumber) {
						// if item is frequent, rename it and add to temp buffer
						if (nameConverter.isOldItemExisting(currentNumber)) {
							if (tempSize >= tempTransaction.length) {
								tempTransaction = Arrays.copyOf(tempTransaction, tempTransaction.length * 2);
							}
							tempTransaction[tempSize++] = nameConverter.toNewName(currentNumber);
						}
						currentNumber = 0;
						hasNumber = false;
					}
				}

				// skip empty transactions (all items were infrequent)
				if (tempSize > 0) {
					int transactionBegin = currentCellIndex;

					// sort renamed items in ascending order
					Arrays.sort(tempTransaction, 0, tempSize);

					// copy transaction into the cell array
					System.arraycopy(tempTransaction, 0, cells, currentCellIndex, tempSize);
					currentCellIndex += tempSize;

					// fill the ending-point lookup for O(1) suffix-end access
					int transactionEnd = currentCellIndex - 1;
					Arrays.fill(transactionsEndingPoints, transactionBegin, transactionEnd + 1, transactionEnd);

					// grow arrays if needed and write -1 separator
					if (currentCellIndex >= cells.length) {
						int newLength = cells.length * 2;
						cells = Arrays.copyOf(cells, newLength);
						transactionsEndingPoints = Arrays.copyOf(transactionsEndingPoints, newLength);
					}
					cells[currentCellIndex++] = -1;

					// register a pointer from each item's Row
					// to its position in this transaction
					for (int j = transactionBegin; j <= transactionEnd; j++) {
						addPointer(initialRows[cells[j]], j, 1);
					}
				}
			}
		} finally {
			if (reader != null)
				reader.close();
		}

		// give the suffix compressor a reference to the
		// now-immutable cell array
		suffixCompressor.setCells(cells);

		return initialRows;
	}

	// ================================================================
	// Sorting (paired quicksort with insertion sort for small ranges)
	// ================================================================

	/**
	 * Sort items by ascending support, breaking ties by item ID. Both arrays are
	 * reordered in parallel so that the correspondence between items[i] and
	 * supports[i] is maintained.
	 *
	 * @param items    the item IDs to sort
	 * @param supports the corresponding support counts
	 */
	private void sortBySupport(int[] items, int[] supports) {
		quickSortPaired(items, supports, 0, items.length - 1);
	}

	/**
	 * Quicksort two parallel arrays by (support, itemID) ascending. Falls back to
	 * insertion sort for ranges smaller than 16 elements. Uses median-of-three
	 * pivot selection and tail-call elimination on the larger partition to
	 * guarantee O(log n) stack depth.
	 *
	 * @param items    the item IDs (reordered in place)
	 * @param supports the corresponding support counts (reordered in place)
	 * @param low      the lower bound of the range to sort (inclusive)
	 * @param high     the upper bound of the range to sort (inclusive)
	 */
	private void quickSortPaired(int[] items, int[] supports, int low, int high) {
		// iterate instead of recursing on the larger partition
		while (low < high) {
			// use insertion sort for small sub-arrays (< 16 elements)
			if (high - low < 16) {
				for (int i = low + 1; i <= high; i++) {
					int keyItem = items[i];
					int keySupport = supports[i];
					int j = i - 1;
					// shift larger entries to the right
					while (j >= low && (supports[j] > keySupport
							|| (supports[j] == keySupport && items[j] > keyItem))) {
						items[j + 1] = items[j];
						supports[j + 1] = supports[j];
						j--;
					}
					// insert the key at its correct position
					items[j + 1] = keyItem;
					supports[j + 1] = keySupport;
				}
				return;
			}

			// median-of-three: sort low/middle/high and use
			// middle value as pivot to avoid worst-case O(n^2)
			int middle = low + (high - low) / 2;
			if (comparePair(supports, items, low, middle) > 0)
				swapPaired(items, supports, low, middle);
			if (comparePair(supports, items, low, high) > 0)
				swapPaired(items, supports, low, high);
			if (comparePair(supports, items, middle, high) > 0)
				swapPaired(items, supports, middle, high);
			// place pivot at high position for partitioning
			swapPaired(items, supports, middle, high);

			// partition around the pivot
			int pivotIndex = partitionPaired(items, supports, low, high);

			// recurse on the smaller partition, loop on the
			// larger one (tail-call elimination for O(log n) stack)
			if (pivotIndex - low < high - pivotIndex) {
				quickSortPaired(items, supports, low, pivotIndex - 1);
				low = pivotIndex + 1;
			} else {
				quickSortPaired(items, supports, pivotIndex + 1, high);
				high = pivotIndex - 1;
			}
		}
	}

	/**
	 * Compare two entries in the parallel arrays by (support, itemID) ascending.
	 * Used as the comparison function for sorting.
	 *
	 * @param supports the support counts array
	 * @param items    the item IDs array
	 * @param a        index of the first entry to compare
	 * @param b        index of the second entry to compare
	 * @return a negative value if entry a should come before entry b, zero if they
	 *         are equal, or a positive value if entry a should come after entry b
	 */
	private int comparePair(int[] supports, int[] items, int a, int b) {
		// primary: ascending support; secondary: ascending item ID
		int difference = supports[a] - supports[b];
		return difference != 0 ? difference : items[a] - items[b];
	}

	/**
	 * Swap the entries at positions a and b in both parallel arrays.
	 *
	 * @param items    the item IDs array
	 * @param supports the support counts array
	 * @param a        index of the first entry to swap
	 * @param b        index of the second entry to swap
	 */
	private void swapPaired(int[] items, int[] supports, int a, int b) {
		int temp = items[a];
		items[a] = items[b];
		items[b] = temp;
		temp = supports[a];
		supports[a] = supports[b];
		supports[b] = temp;
	}

	/**
	 * Partition the parallel arrays around a pivot using the Lomuto scheme.
	 * The pivot is taken from items[high] / supports[high]. After partitioning,
	 * all entries in [low..pivotIndex-1] are less than the pivot and all entries
	 * in [pivotIndex+1..high] are greater or equal.
	 *
	 * @param items    the item IDs array
	 * @param supports the support counts array
	 * @param low      lower bound of the range to partition (inclusive)
	 * @param high     upper bound of the range to partition (inclusive); also the position of the pivot element
	 * @return the final position of the pivot element
	 */
	private int partitionPaired(int[] items, int[] supports, int low, int high) {
		// pivot is at the high position
		int pivotSupport = supports[high];
		int pivotItem = items[high];
		// boundary tracks the boundary of the "less than pivot" region
		int boundary = low - 1;
		// scan and swap elements smaller than pivot to the left
		for (int j = low; j < high; j++) {
			if (supports[j] < pivotSupport
					|| (supports[j] == pivotSupport && items[j] < pivotItem)) {
				boundary++;
				swapPaired(items, supports, boundary, j);
			}
		}
		// place pivot in its final sorted position
		swapPaired(items, supports, boundary + 1, high);
		return boundary + 1;
	}

	// ================================================================
	// Row pool management
	// ================================================================

	/**
	 * Get a Row from the pool, growing the pool if necessary. The returned Row is
	 * reset to a clean state ready for use.
	 *
	 * @return a reset Row instance from the pool
	 */
	private Row getPooledRow() {
		// return the next available row if pool has room
		if (rowPoolPos < rowPool.length) {
			Row row = rowPool[rowPoolPos++];
			row.reset();
			return row;
		}
		// double the pool and fill new slots with fresh Row objects
		int newSize = rowPool.length * 2;
		Row[] newPool = new Row[newSize];
		System.arraycopy(rowPool, 0, newPool, 0, rowPool.length);
		for (int i = rowPool.length; i < newSize; i++) {
			newPool[i] = new Row();
		}
		rowPool = newPool;
		// now take from the expanded pool
		Row row = rowPool[rowPoolPos++];
		row.reset();
		return row;
	}

	/**
	 * Add a pointer to a Row's linked list by prepending it. The pointer records
	 * which cell position this Row references and the transaction weight. The
	 * pointer pool arrays are grown automatically if full.
	 *
	 * @param row          the Row to prepend the pointer to
	 * @param cellPosition index into the cells array that this pointer
	 *                     references
	 * @param weight       transaction weight (if merged transactions)
	 */
	private void addPointer(Row row, int cellPosition, int weight) {
		// grow the pointer pool if full
		if (pointerPoolSize >= pointerPool.length) {
			int newSize = pointerPool.length * 2;
			pointerPool = Arrays.copyOf(pointerPool, newSize);
			pointerWeight = Arrays.copyOf(pointerWeight, newSize);
			nextPointerIndex = Arrays.copyOf(nextPointerIndex, newSize);
		}
		// allocate a new slot and prepend it to the row's list
		int newIndex = pointerPoolSize++;
		pointerPool[newIndex] = cellPosition;
		pointerWeight[newIndex] = weight;
		nextPointerIndex[newIndex] = row.headPointer;
		row.headPointer = newIndex;
	}

	// ================================================================
	// Core recursive mining
	// ================================================================

	/**
	 * Recursively mines all frequent itemsets that extend the given prefix.
	 *
	 * @param prefix       buffer holding the current prefix 
	 * @param prefixLength number of items currently in the prefix
	 * @param currentLevelRows header table (item -> Row) for this recursion level
	 * @param rowItems     array of item IDs present at this level
	 * @param rowCount     number of valid entries in rowItems
	 * @throws IOException if an error occurs writing output
	 */
	private void hmine(int[] prefix, int prefixLength, Row[] currentLevelRows, int[] rowItems, int rowCount)
			throws IOException {

		// stop if maximum depth reached
		if (recursionDepth >= maxRecursionDepth)
			return;

		// fetch workspace arrays for this recursion level
		Row[] projectedRows = projectedRowsStack[recursionDepth];
		int[] newRowItems = newRowItemsStack[recursionDepth];
		int[] usedIndices = usedIndicesStack[recursionDepth];
		boolean[] isRelevant = isRelevantStack[recursionDepth];
		int[] supportCount = supportCountStack[recursionDepth];

		// mark all items at this level as relevant so that
		// suffix compression and support counting skip
		// items pruned at higher levels
		for (int i = 0; i < rowCount; i++) {
			isRelevant[rowItems[i]] = true;
		}

		// process each item in the header table
		for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
			int currentItem = rowItems[rowIndex];
			Row row = currentLevelRows[currentItem];

			// save pool positions so we can roll back after
			// processing this item (cheap deallocation)
			int savedPointerPoolSize = pointerPoolSize;
			int savedRowPoolPos = rowPoolPos;
			int usedCount = 0;

			// === Suffix collection and merging ===
			// Walk the item's pointer chain. For each transaction,
			// collect the suffix after this item. The suffix
			// compressor merges identical suffixes by summing weights.
			suffixCompressor.reset();

			int pointer = row.headPointer;
			while (pointer != -1) {
				int suffixStart = pointerPool[pointer] + 1;
				// only process if the suffix is non-empty
				if (cells[suffixStart] != -1) {
					suffixCompressor.addOrMerge(suffixStart,
							transactionsEndingPoints[suffixStart],
							pointerWeight[pointer], isRelevant);
				}
				pointer = nextPointerIndex[pointer];
			}

			int mergedCount = suffixCompressor.count();

			// === Phase 1: support counting ===
			// Walk the merged suffixes and count support for each
			// relevant item. No pointer structures are built yet.
			for (int suffixIndex = 0; suffixIndex < mergedCount; suffixIndex++) {
				int suffixStart = suffixCompressor.start(suffixIndex);
				int suffixEnd = suffixCompressor.end(suffixIndex);
				int suffixWeight = suffixCompressor.weight(suffixIndex);

				for (int position = suffixStart; position <= suffixEnd; position++) {
					int item = cells[position];
					if (isRelevant[item]) {
						// track which items were touched for cleanup
						if (supportCount[item] == 0) {
							usedIndices[usedCount++] = item;
						}
						supportCount[item] += suffixWeight;
					}
				}
			}

			// collect items that are frequent in the projection
			int newRowCount = 0;
			for (int i = 0; i < usedCount; i++) {
				int item = usedIndices[i];
				if (supportCount[item] >= minSupport) {
					newRowItems[newRowCount++] = item;
				}
			}
			// sort frequent items by renamed ID (ascending)
			if (newRowCount > 1) {
				Arrays.sort(newRowItems, 0, newRowCount);
			}

			// output the current itemset: prefix + currentItem
			prefix[prefixLength] = currentItem;
			writeOut(prefix, prefixLength + 1, row.support);

			// recurse only if there are frequent extensions
			// and the max itemset size allows it
			if (newRowCount > 0 && prefixLength + 2 <= maxItemsetSize) {

				// === Single-path optimization ===
				// If there is exactly one merged suffix and all
				// frequent items share the same support, every
				// non-empty subset has that support. Enumerate
				// all 2^k-1 subsets directly without recursing.
				boolean useSinglePath = false;
				int pathSupport = 0;

				if (mergedCount == 1 && newRowCount <= 20) {
					pathSupport = supportCount[newRowItems[0]];
					useSinglePath = true;
					for (int i = 1; i < newRowCount; i++) {
						if (supportCount[newRowItems[i]] != pathSupport) {
							useSinglePath = false;
							break;
						}
					}
				}

				if (useSinglePath) {
					// directly enumerate all subsets of the path
					generateAllSubsets(prefix, prefixLength + 1,
							newRowItems, newRowCount, pathSupport);
				} else {
					// === Phase 2: build projected header table ===
					// Walk merged suffixes again, creating Row and
					// pointer structures only for items that passed
					// the support threshold in Phase 1
					for (int suffixIndex = 0; suffixIndex < mergedCount; suffixIndex++) {
						int suffixStart = suffixCompressor.start(suffixIndex);
						int suffixEnd = suffixCompressor.end(suffixIndex);
						int suffixWeight = suffixCompressor.weight(suffixIndex);

						for (int position = suffixStart; position <= suffixEnd; position++) {
							int item = cells[position];
							if (isRelevant[item] && supportCount[item] >= minSupport) {
								// create Row on first encounter
								Row projectedRow = projectedRows[item];
								if (projectedRow == null) {
									projectedRow = getPooledRow();
									projectedRow.item = item;
									projectedRow.support = supportCount[item];
									projectedRows[item] = projectedRow;
								}
								// register a pointer for this occurrence
								addPointer(projectedRow, position, suffixWeight);
							}
						}
					}

					// recurse on the projected database
					recursionDepth++;
					hmine(prefix, prefixLength + 1, projectedRows,
							newRowItems, newRowCount);
					recursionDepth--;
				}
			}

			// roll back pools to free all pointers and rows
			// allocated during this item's processing
			pointerPoolSize = savedPointerPoolSize;
			rowPoolPos = savedRowPoolPos;

			// clear per-item state for touched items
			for (int i = 0; i < usedCount; i++) {
				int item = usedIndices[i];
				supportCount[item] = 0;
				projectedRows[item] = null;
			}
		}

		// clear relevance marks for this recursion level
		for (int i = 0; i < rowCount; i++) {
			isRelevant[rowItems[i]] = false;
		}

		// check memory at the top level
		if (recursionDepth == 0) {
			MemoryLogger.getInstance().checkMemory();
		}
	}

	/**
	 * Generate all 2^k - 1 non-empty subsets of the items on a single path and
	 * write each as a frequent itemset with the given support. 
	 *
	 * @param prefix       the current prefix buffer (modified temporarily; restored
	 *                     by caller)
	 * @param prefixLength number of prefix items 
	 * @param pathItems    the renamed items forming the single path
	 * @param pathLength   number of items in pathItems to use
	 * @param support      the support count of all path subsets
	 * @throws IOException if an error occurs writing output
	 */
	private void generateAllSubsets(int[] prefix, int prefixLength, int[] pathItems, int pathLength, int support)
			throws IOException {

		// clamp effective length to respect maxItemsetSize,
		// the 20-bit bitmask limit, and the buffer capacity
		int effectiveLength = Math.min(pathLength, maxItemsetSize - prefixLength);
		if (effectiveLength <= 0)
			return;
		if (effectiveLength > 20)
			effectiveLength = 20;
		if (prefixLength + effectiveLength >= itemsetBuffer.length) {
			effectiveLength = itemsetBuffer.length - prefixLength - 1;
			if (effectiveLength <= 0)
				return;
		}

		// enumerate all 2^k - 1 non-empty subsets via bitmask
		int totalSubsets = (1 << effectiveLength) - 1;
		for (int mask = 1; mask <= totalSubsets; mask++) {
			// extract set bits to build the subset
			int subsetSize = 0;
			int remainingBits = mask;
			while (remainingBits != 0) {
				// get position of lowest set bit
				int bitPosition = Integer.numberOfTrailingZeros(remainingBits);
				prefix[prefixLength + subsetSize++] = pathItems[bitPosition];
				// clear the lowest set bit
				remainingBits &= remainingBits - 1;
			}
			// output this subset as a frequent itemset
			writeOut(prefix, prefixLength + subsetSize, support);
		}
	}

	// ================================================================
	// Output methods
	// ================================================================

	/**
	 * Write a frequent itemset given as a complete array of renamed item IDs to the
	 * output file. The items are converted back to their original names before
	 * writing. 
	 * @param itemset the itemset array (renamed IDs)
	 * @param length  number of items in the itemset to write
	 * @param support the support count of the itemset
	 * @throws IOException if an error occurs writing output
	 */
	private void writeOut(int[] itemset, int length, int support) throws IOException {
		patternCount++;

		// build the output line: "oldName1 oldName2 ... #SUP: count"
		outputBuffer.setLength(0);
		for (int i = 0; i < length; i++) {
			outputBuffer.append(nameConverter.toOldName(itemset[i]));
			outputBuffer.append(' ');
		}
		outputBuffer.append("#SUP: ");
		outputBuffer.append(support);

		// flush via reusable char array to avoid toString() allocation
		int outputLength = outputBuffer.length();
		if (outputLength > outputChars.length) {
			outputChars = new char[outputLength * 2];
		}
		outputBuffer.getChars(0, outputLength, outputChars, 0);
		writer.write(outputChars, 0, outputLength);
		writer.newLine();
	}

	/**
	 * Print statistics about the last algorithm execution to the console, including
	 * runtime, peak memory usage, and the number of frequent itemsets found.
	 */
	public void printStats() {
		System.out.println("============= HMine (Fast) ALGORITHM 2.66 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max Memory ~ " + maxMemoryUsage + " MB");
		System.out.println(" Frequent itemsets count : " + patternCount);
		System.out.println("===================================================");
	}

	/**
	 * Set the maximum pattern length. Only itemsets with at most this many items
	 * will be output. Must be called before {@link #runAlgorithm} if a limit is
	 * desired.
	 *
	 * @param length the maximum number of items in a pattern (must be &gt;= 1)
	 */
	public void setMaximumPatternLength(int length) {
		this.maxItemsetSize = length;
	}
}