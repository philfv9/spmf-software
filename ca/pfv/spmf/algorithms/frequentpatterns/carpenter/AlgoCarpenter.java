package ca.pfv.spmf.algorithms.frequentpatterns.carpenter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;

/* Copyright (c) 2025 Philippe Fournier-Viger
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
* 
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * Implementation of the Carpenter algorithm for mining closed/maximal frequent
 * itemsets. <br>
 * <br>
 * 
 * Carpenter uses row enumeration with depth-first search, making it efficient
 * for datasets with many items but few transactions (e.g., biological
 * datasets). <br>
 * <br>
 * 
 * Reference: F. Pan, G. Cong, A.K.H. Tung, J. Yang, and M. Zaki. "Carpenter:
 * Finding Closed Patterns in Long Biological Datasets." Proc. 9th ACM SIGKDD
 * Int. Conf. (KDD 2003), pp. 637-642. <br>
 * <br>
 * 
 * This implementation was made by taking inspiration from Christian Borgelt's C++ version 
 * with several simplifications, modifications and variations in design choices. 
 * 
 * @author Philippe Fournier-Viger, 2025
 * @see Itemsets
 * @see TIDList
 */
public class AlgoCarpenter {

	/** Enable debug mode for verbose console output */
	private static final boolean DEBUG_MODE = false;

	// ===================== Statistics =============
	/** Start time of last execution in milliseconds */
	private long startTimestamp;

	/** End time of last execution in milliseconds */
	private long endTimestamp;

	/** Peak memory usage in megabytes */
	private double peakMemory;

	/** Minimum support threshold as transaction count */
	private int minsupRelative;

	// ====================== Parameters ================
	/** Minimum itemset size constraint */
	private int minSizeConstraint;

	/** Maximum itemset size constraint */
	private int maxSizeConstraint;

	/** If true, mine maximal patterns; otherwise mine closed patterns */
	private boolean mineMaximalPatterns;

	// ====================== Output =========
	/** The discovered patterns */
	private Itemsets patternsFound;

	// ====================== Internal variables =========
	/** Map of each item to its support count */
	private Map<Integer, Integer> mapItemToSupport;

	/** Converter for renaming items according to support-based total order */
	private ItemNameConverter nameConverter;

	/** The transaction database (list of item arrays) */
	private List<int[]> transactionDatabase;

	/** Weights (occurrence counts) for each unique transaction */
	private List<Integer> transactionWeights;

	/** Transaction weights as array for efficient access */
	private int[] transactionWeightsArray;

	/** Original transaction count before merging duplicates */
	private int uniqueTransactionCount;

	/** Transaction count after merging identical transactions */
	private int transactionCount;

	/** Number of frequent items */
	private int itemCount;

	/** Candidate closed itemsets before final filtering */
	private Map<BitSet, Integer> candidateSet;

	/**
	 * Run the Carpenter algorithm to find closed or maximal frequent itemsets.
	 * 
	 * @param inputFile               path to the input transaction database file
	 * @param outputFile              path for output file (null to skip file
	 *                                output)
	 * @param minsup                  minimum support threshold (0.0 to 1.0)
	 * @param minSizeConstraint       minimum itemset size (use 0 for no constraint)
	 * @param maxSizeConstraint       maximum itemset size (use Integer.MAX_VALUE
	 *                                for no constraint)
	 * @param keepOnlyMaximalPatterns if true, return only maximal patterns
	 * @throws IOException if error reading input or writing output
	 */
	public void runAlgorithm(String inputFile, String outputFile, double minsup, int minSizeConstraint,
			int maxSizeConstraint, boolean keepOnlyMaximalPatterns) throws IOException {

		// Initialize statistics tracking
		MemoryLogger.getInstance().reset();
		startTimestamp = System.currentTimeMillis();

		// Initialize result storage
		this.patternsFound = new Itemsets("Itemsets");
		this.mineMaximalPatterns = keepOnlyMaximalPatterns;
		this.minSizeConstraint = minSizeConstraint;
		this.maxSizeConstraint = maxSizeConstraint;

		// Read and preprocess database
		readTransactionDatabase(inputFile);

		// Calculate relative minimum support
		this.minsupRelative = (int) Math.ceil(minsup * uniqueTransactionCount);
		if (minsupRelative < 1) {
			minsupRelative = 1;
		}

		if (DEBUG_MODE) {
			System.out.println("Relative minimum support: " + minsupRelative);
		}

		// Rename items by support order and remove infrequent items
		renameItemsFromDatabase();
		mapItemToSupport = null;

		// Mine closed patterns
		mineClosedPatterns();

		// Apply constraints and extract final patterns
		extractFinalPatterns();

		// Write results to file if specified
		if (outputFile != null) {
			writeItemsetsToFile(outputFile);
		}

		// Record final statistics
		MemoryLogger.getInstance().checkMemory();
		peakMemory = MemoryLogger.getInstance().getMaxMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Extract final patterns from candidates, applying size constraints and maximal
	 * filtering.
	 */
	private void extractFinalPatterns() {
		for (Entry<BitSet, Integer> entry : candidateSet.entrySet()) {
			BitSet itemSet = entry.getKey();
			int support = entry.getValue();

			int size = itemSet.cardinality();
			if (size < minSizeConstraint || size > maxSizeConstraint) {
				continue;
			}

			if (mineMaximalPatterns && hasFrequentSuperset(itemSet, size)) {
				continue;
			}

			// Convert to original item names and save
			int[] items = convertBitSetToSortedItems(itemSet, size);
			patternsFound.addItemset(new Itemset(items, support), items.length);
		}
	}

	/**
	 * Check if the given itemset has a frequent superset in the candidate set.
	 * 
	 * @param itemSet the itemset to check
	 * @param size    cardinality of the itemset
	 * @return true if a frequent superset exists
	 */
	private boolean hasFrequentSuperset(BitSet itemSet, int size) {
		// Precompute candidate sets by size
		for (Entry<BitSet, Integer> other : candidateSet.entrySet()) {
			BitSet otherSet = other.getKey();
			int otherSize = otherSet.cardinality();
			
			if(otherSize > maxSizeConstraint) {
				continue;
			}
			int otherSupport = other.getValue();
			

			if (otherSupport < minsupRelative || otherSize <= size) {
				continue;
			}

			// Check if itemSet is subset of otherSet without cloning
			BitSet temp = (BitSet) itemSet.clone();
			temp.andNot(otherSet);
			if (temp.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Convert a BitSet to a sorted array of original item names.
	 * 
	 * @param bitSet the BitSet representation
	 * @param size   cardinality of the BitSet
	 * @return sorted array of original item names
	 */
	private int[] convertBitSetToSortedItems(BitSet bitSet, int size) {
		int[] items = new int[size];
		int idx = 0;
		for (int i = bitSet.nextSetBit(0); i >= 0; i = bitSet.nextSetBit(i + 1)) {
			items[idx++] = nameConverter.toOldName(i);
		}
		Arrays.sort(items);
		return items;
	}

	/**
	 * Mine closed patterns using Carpenter's row-enumeration depth-first search.
	 * Uses TID list intersections for efficient support computation.
	 */
	private void mineClosedPatterns() {
		initializeCandidateSet();

		if (DEBUG_MODE) {
			System.out.println("\n=== STARTING CARPENTER ===");
			System.out.println("Transaction count: " + transactionCount);
			System.out.println("Unique transaction count: " + uniqueTransactionCount);
			System.out.println("Item count: " + itemCount);
			System.out.println("Minsup relative: " + minsupRelative);
		}

		// Build TID lists for all items as int[] arrays
		int[] itemSupports = new int[itemCount];
		int[][] itemTidLists = new int[itemCount][];
		int[] itemTidCounts = new int[itemCount]; // Track counts for each item

		// First, count occurrences to pre-allocate arrays
		for (int tid = 0; tid < transactionCount; tid++) {
			int[] transaction = transactionDatabase.get(tid);
			int weight = transactionWeightsArray[tid];
			for (int item : transaction) {
				itemSupports[item] += weight;
				itemTidCounts[item]++; // increment number of transactions containing item
			}
		}

		// Allocate TID arrays
		for (int i = 0; i < itemCount; i++) {
			itemTidLists[i] = new int[itemTidCounts[i]];
			itemTidCounts[i] = 0; // reset to use as index
		}

		// Fill TID arrays
		for (int tid = 0; tid < transactionCount; tid++) {
			int[] transaction = transactionDatabase.get(tid);
			for (int item : transaction) {
				itemTidLists[item][itemTidCounts[item]++] = tid;
			}
		}

		// Filter to frequent items and create TIDList objects
		List<TIDList> frequentTidLists = new ArrayList<>();
		for (int i = 0; i < itemCount; i++) {
			if (itemSupports[i] >= minsupRelative) {
				frequentTidLists.add(new TIDList(i, itemSupports[i], itemTidLists[i]));
			}
		}

		TIDList[] tidlists = frequentTidLists.toArray(new TIDList[0]);

		// Initialize DFS with all transactions
		int[] allTids = new int[transactionCount];
		for (int i = 0; i < transactionCount; i++) {
			allTids[i] = i;
		}

		// Pre-allocate reusable BitSets for DFS to reduce object creation
		int maxDepth = itemCount + 1;
		BitSet[] prefixStack = new BitSet[maxDepth];
		BitSet[] closureStack = new BitSet[maxDepth];
		for (int i = 0; i < maxDepth; i++) {
			prefixStack[i] = new BitSet(itemCount);
			closureStack[i] = new BitSet(itemCount);
		}

		depthFirstSearch(tidlists, prefixStack, closureStack, 0, 0, allTids, uniqueTransactionCount);

		if (DEBUG_MODE) {
			System.out.println("\n=== MINING COMPLETE ===");
			System.out.println("Candidate set size: " + candidateSet.size());
		}
	}

	/**
	 * Perform depth-first search for row enumeration in Carpenter. Uses
	 * pre-allocated BitSet stacks to minimize object creation.
	 * 
	 * @param tidlists      array of frequent items with their TID lists
	 * @param prefixStack   pre-allocated BitSets for prefix at each depth
	 * @param closureStack  pre-allocated BitSets for closure at each depth
	 * @param depth         current recursion depth
	 * @param startIdx      starting index in tidlists for child enumeration
	 * @param prefixTids    transactions containing current prefix
	 * @param prefixSupport support of current prefix
	 */
	private void depthFirstSearch(TIDList[] tidlists, BitSet[] prefixStack, BitSet[] closureStack, int depth,
			int startIdx, int[] prefixTids, int prefixSupport) {

		// Get pre-allocated BitSets for this depth
		BitSet prefix = prefixStack[depth];
		BitSet closure = closureStack[depth];

		// Initialize closure as copy of prefix
		closure.clear();
		closure.or(prefix);

		// For each item in the dataset
		for (TIDList tidlist : tidlists) {
		    // Skip items already in the prefix
		    if (prefix.get(tidlist.item)) continue;

		    // Check if tidlist of this item contains all prefix transactions
		    // If so, the item is in the closure
		    if (isSubset(prefixTids, tidlist.tids)) {
		        closure.set(tidlist.item);
		    }
		}

		// Check for items added by closure (not in original prefix)
		// Find first bit set in closure but not in prefix
		int firstPrefixItem = prefix.nextSetBit(0);
		int firstClosureExtension = -1;

		for (int i = closure.nextSetBit(0); i >= 0; i = closure.nextSetBit(i + 1)) {
		    if (!prefix.get(i)) {
		        firstClosureExtension = i;
		        break;
		    }
		}

		if (firstPrefixItem >= 0 && firstClosureExtension != -1 && firstClosureExtension < firstPrefixItem) {
		    return; // canonical prune
		}


		// Save closed itemset if new (must clone here since candidateSet stores it)
		if (closure.cardinality() > 0 && !candidateSet.containsKey(closure)) {
			candidateSet.put((BitSet) closure.clone(), prefixSupport);
			if (DEBUG_MODE) {
				System.out.println("*** SAVED closed itemset with support=" + prefixSupport + " ***");
			}
		}

		// Enumerate children by adding items not in closure
		for (int i = startIdx; i < tidlists.length; i++) {
			TIDList item = tidlists[i];

			if (closure.get(item.item)) {
				continue;
			}

			int[] newTids = intersectTidLists(prefixTids, item.tids);
			int newSupport = calculateSupport(newTids);

			if (newSupport < minsupRelative) {
				continue;
			}

			// Set up prefix for next depth level
			BitSet nextPrefix = prefixStack[depth + 1];
			nextPrefix.clear();
			nextPrefix.or(prefix);
			nextPrefix.set(item.item);

			depthFirstSearch(tidlists, prefixStack, closureStack, depth + 1, i + 1, newTids, newSupport);
		}
	}
	


	/**
	 * Check if prefixTids is a subset of itemTids.
	 * Both arrays are sorted.
	 */
	private boolean isSubset(int[] prefixTids, int[] itemTids) {
	    int i = 0, j = 0;
	    while (i < prefixTids.length && j < itemTids.length) {
	        if (prefixTids[i] == itemTids[j]) {
	            i++;
	            j++;
	        } else if (prefixTids[i] < itemTids[j]) {
	            return false; // missing prefixTID
	        } else {
	            j++;
	        }
	    }
	    return i == prefixTids.length;
	}

	/**
	 * Compute intersection of two sorted TID arrays.
	 * 
	 * @param tids1 first sorted TID array
	 * @param tids2 second sorted TID array
	 * @return new array containing TIDs present in both inputs
	 */
	private int[] intersectTidLists(int[] tids1, int[] tids2) {
		int[] temp = new int[Math.min(tids1.length, tids2.length)];
		int i = 0, j = 0, k = 0;

		while (i < tids1.length && j < tids2.length) {
			if (tids1[i] == tids2[j]) {
				temp[k++] = tids1[i];
				i++;
				j++;
			} else if (tids1[i] < tids2[j]) {
				i++;
			} else {
				j++;
			}
		}
		return Arrays.copyOf(temp, k);
	}

	/**
	 * Calculate weighted support by summing transaction weights for given TIDs.
	 * 
	 * @param tids array of transaction IDs
	 * @return total support (sum of weights)
	 */
	private int calculateSupport(int[] tids) {
		int support = 0;
		for (int tid : tids) {
			support += transactionWeightsArray[tid];
		}
		return support;
	}

	/**
	 * Initialize the candidate set, optionally including the empty set.
	 */
	private void initializeCandidateSet() {
		candidateSet = new HashMap<>();

		// Add empty set if it meets minimum support
		if (uniqueTransactionCount >= minsupRelative) {
			candidateSet.put(new BitSet(0), uniqueTransactionCount);
		}
	}

	/**
	 * Convert a List of Integers to a primitive int array.
	 * 
	 * @param list the list to convert
	 * @return equivalent int array
	 */
	private int[] toIntArray(List<Integer> list) {
		int[] array = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			array[i] = list.get(i);
		}
		return array;
	}

	/**
	 * Convert an itemset array to a BitSet representation.
	 * 
	 * @param itemset array of item IDs
	 * @return BitSet with bits set for each item
	 */
	private BitSet convertItemsetToBitset(int[] itemset) {
		BitSet bitset = new BitSet();
		for (int item : itemset) {
			bitset.set(item);
		}
		return bitset;
	}

	/**
	 * Read the transaction database from file, merging identical transactions.
	 * 
	 * @param inputPath path to the input file
	 * @throws IOException if error reading file
	 */
	private void readTransactionDatabase(String inputPath) throws IOException {
		uniqueTransactionCount = 0;
		mapItemToSupport = new HashMap<>();
		transactionDatabase = new ArrayList<>();
		transactionWeights = new ArrayList<>();

		// Map for detecting duplicate transactions
		Map<BitSet, Integer> transactionToIndex = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(inputPath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// Skip comments and metadata
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] tokens = line.split(" ");
				List<Integer> items = new ArrayList<>(tokens.length);

				for (String token : tokens) {
					Integer item = Integer.parseInt(token);
					items.add(item);
					mapItemToSupport.merge(item, 1, Integer::sum);
				}

				if (!items.isEmpty()) {
					int[] transaction = toIntArray(items);
					BitSet transactionKey = convertItemsetToBitset(transaction);
					Integer existingIndex = transactionToIndex.get(transactionKey);

					if (existingIndex != null) {
						// Increment weight of existing identical transaction
						transactionWeights.set(existingIndex, transactionWeights.get(existingIndex) + 1);
					} else {
						// Add new unique transaction
						transactionDatabase.add(transaction);
						transactionWeights.add(1);
						transactionToIndex.put(transactionKey, transactionDatabase.size() - 1);
					}
					uniqueTransactionCount++;
				}
			}
		}
		printDatabaseAndWeightsForDebugging();
		transactionCount = transactionDatabase.size();
	}

	/**
	 * Rename items by support order and remove infrequent items from transactions.
	 * Items are renamed so that lower support items have lower IDs.
	 */
	private void renameItemsFromDatabase() {
		// Collect frequent items
		List<Integer> frequentItems = new ArrayList<>(mapItemToSupport.size());
		for (Entry<Integer, Integer> entry : mapItemToSupport.entrySet()) {
			if (entry.getValue() >= minsupRelative) {
				frequentItems.add(entry.getKey());
			}
		}

		itemCount = frequentItems.size();

		if (DEBUG_MODE) {
			System.out.println("All frequent items: " + frequentItems);
		}

		// Sort by increasing support
		frequentItems.sort(Comparator.comparingInt(mapItemToSupport::get));

		if (DEBUG_MODE) {
			System.out.println("Sorted items: " + frequentItems);
		}

		// Create name converter with new ordering
		nameConverter = new ItemNameConverter(frequentItems.size(), 0);
		for (Integer item : frequentItems) {
			nameConverter.assignNewName(item);
		}

		// Rebuild database with renamed items
		List<int[]> modifiedDatabase = new ArrayList<>();
		List<Integer> modifiedWeights = new ArrayList<>();

		for (int i = 0; i < transactionDatabase.size(); i++) {
			int[] transaction = transactionDatabase.get(i);
			List<Integer> modifiedTransaction = new ArrayList<>();

			for (int item : transaction) {
				if (nameConverter.isOldItemExisting(item)) {
					modifiedTransaction.add(nameConverter.toNewName(item));
				}
			}

			// Keep transaction if it meets minimum size constraint
			if (modifiedTransaction.size() >= minSizeConstraint) {
				Collections.sort(modifiedTransaction);
				modifiedDatabase.add(toIntArray(modifiedTransaction));
				modifiedWeights.add(transactionWeights.get(i));
			}
		}

		transactionDatabase = modifiedDatabase;
		transactionWeights = modifiedWeights;
		transactionCount = transactionDatabase.size();

		printDatabaseAndWeightsForDebugging();

		// Convert to array and free list
		transactionWeightsArray = toIntArray(transactionWeights);
		transactionWeights = null;
		mapItemToSupport = null;
	}

	/**
	 * Print database contents for debugging (only when DEBUG_MODE is enabled).
	 */
	private void printDatabaseAndWeightsForDebugging() {
		if (DEBUG_MODE) {
			System.out.println("TRANSACTIONS");
			for (int i = 0; i < transactionDatabase.size(); i++) {
				System.out.println("  Transaction " + i + ": " + Arrays.toString(transactionDatabase.get(i))
						+ " weight: " + transactionWeights.get(i));

			}
		}
	}

	/**
	 * Write discovered itemsets to output file.
	 * 
	 * @param outputPath path to output file
	 * @throws IOException if error writing file
	 */
	private void writeItemsetsToFile(String outputPath) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
			for (List<Itemset> level : patternsFound.getLevels()) {
				for (Itemset itemset : level) {
					StringBuilder buffer = new StringBuilder();

					for (int i = 0; i < itemset.size(); i++) {
						buffer.append(itemset.get(i));
						buffer.append(' ');
					}

					buffer.append("#SUP: ");
					buffer.append(itemset.getAbsoluteSupport());

					writer.write(buffer.toString());
					writer.newLine();
				}
			}
		}
	}

	/**
	 * Get the discovered patterns.
	 * 
	 * @return the itemsets found by the algorithm
	 */
	public Itemsets getPatterns() {
		return patternsFound;
	}

	/**
	 * Print execution statistics to the console.
	 */
	public void printStats() {
		String algorithmName = mineMaximalPatterns ? "CARPENTER-MAX v2.64" : "CARPENTER v2.64";
		String patternType = mineMaximalPatterns ? "Maximal" : "Closed";

		System.out.println("=============  " + algorithmName + " - STATS =============");
		System.out.println(" " + patternType + " itemsets found: " + patternsFound.getItemsetsCount());
		System.out.println(" Time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory: " + String.format("%.2f", peakMemory) + " MB");
		System.out.println("==============================================");
	}
}