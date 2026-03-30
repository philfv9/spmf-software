package ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger

This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.ItemsetHashTree.LeafNode;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is a Top-K implementation of the APRIORI-FAST algorithm with multiple
 * optimizations. Instead of using a minimum support threshold, this algorithm
 * finds the top-k most frequent itemsets.
 * 
 * Besides using a hash-tree for support counting (as in AlgoAprioriHT)
 * this version applies four optimizations: <br/>
 * (1) merging identical transactions, (2) renaming items to speed up
 * comparisons, (3) using bitmap subset checking if there is no more than 64
 * items in the input dataset, (4) projecting transactions. <br/>
 * 
 * The original Apriori algorithm is described in : <br/>
 * 
 * <br/>
 * Agrawal R, Srikant R. "Fast Algorithms for Mining Association Rules", VLDB.
 * Sep 12-15 1994, Chile, 487-99, <br/>
 * 
 * <br/>
 * The Apriori algorithm finds all the frequents itemsets and their support in a
 * transaction database. This top-k version finds the k most frequent itemsets
 * instead of using a minimum support threshold. <br/>
 * 
 * <br/>
 * Similarly to AprioriHT, the performance depends on the BRANCH COUNT value for
 * the hash tree. In my test, I have used a value of 30 because it seems to
 * provide the best results. But other values could also be used.
 * 
 * @see Itemset
 * @see AbstractOrderedItemsetsAdapter
 * @see ItemsetHashTree
 * @author Philippe Fournier-Viger
 */
public class AlgoAprioriFAST_TopK {

	// ======================================
	/** the number of patterns to find "n" */
	protected int n;

	/** priority queue to store the top-n patterns (min-heap ordered by support) */
	protected PriorityQueue<Itemset> nItemsets;
	// ======================================

	/** the maximum level reached by Apriori */
	protected int k;

	/** total number of candidates generated */
	protected int totalCandidateCount = 0;

	/** start time */
	protected long startTimestamp;

	/** end time */
	protected long endTimestamp;

	/** number of itemsets found */
	private int itemsetCount;

	/** the number of branches in the hash tree */
	private int hash_tree_branch_count;

	/** the relative minimum support used to find itemsets (dynamically updated for top-k) */
	private int minsupRelative;

	/** an in-memory representation of the transaction database */
	private List<int[]> database = null;

	/** number of frequent items */
	private int frequentItemCount;

	/** DEBUG MODE if this variable is set to true **/
	boolean DEBUG_MODE = false;

	/** Maximum pattern length */
	private int maxPatternLength = 1000;

	// === For reading and writing to file
	/** Reusable StringBuilder for outputing itemsets */
	private final StringBuilder outputBuffer = new StringBuilder(256);
	/** Buffer to write to file */
	BufferedWriter writer = null;

	// === OPTIMIZATION 1: For the transaction merging optimization ==
	/** weights (counts) for each transaction after merging identical ones */
	private int[] transactionWeights = null;

	// === OPTIMIZATION 2: For renaming frequent items to speed up comparison ==
	/** converter for renaming items according to support-based total order */
	private ItemNameConverter nameConverter;

	// === OPTIMIZATION 3: For the bitmap subset checking optimization ==
	/** Max. number of items required for the bitmap subset checking optimization */
	private static final int MAX_ITEMS_FOR_BITMAP = 64;
	/** Flag indicating if bitmap subset checking optimization will be used */
	private boolean useBitmapOptimization = false;

	/** Bitmap representation of transactions (for items 0-63) */
	private long[] transactionBitmaps = null;

	// === OPTIMIZATION 4: For the database reduction optimization ==
	/** Reusable buffer for projecting transactions */
	private int[] projectionBuffer = null;

	/** The number of transactions in the database */
	private int databaseSize;

	/** Object to store itemsets in memory when output is null (SPMF style) */
	private Itemsets itemsetsInMemory = null;

	/**
	 * Default constructor
	 */
	public AlgoAprioriFAST_TopK() {
	}

	/**
	 * Parse integers from a line to read a transaction
	 * 
	 * @param line   the input line
	 * @param result list for returning the items that have been read.
	 */
	private void parseLineToInts(String line, List<Integer> result) {
		// Clear the result list
		result.clear();
		int length = line.length();
		int number = 0;
		boolean isInteger = false;

		// Read the line, character by character
		for (int i = 0; i < length; i++) {
			char c = line.charAt(i);
			if (c >= '0' && c <= '9') {
				number = number * 10 + (c - '0');
				isInteger = true;
			} else if (c == ' ' || c == '\t') {
				if (isInteger) {
					result.add(number);
					number = 0;
					isInteger = false;
				}
			}
		}
		if (isInteger) {
			result.add(number);
		}
	}

	// ===========================================================
	// === START OF BITMAP-BASED SUBSET CHECKING OPTIMIZATION ===

	/**
	 * Count support for all candidates using bitmap-based subset checking. This is
	 * O(1) per candidate-transaction pair instead of O(k) for array comparison.
	 * 
	 * @param candidatesK the hash tree containing candidates
	 * @param k           the size of itemsets
	 */
	private void countSupportUsingBitmaps(ItemsetHashTree candidatesK, int k) {
		// For each leaf node in the hash-tree
		for (LeafNode node = candidatesK.lastInsertedNode; node != null; node = node.nextLeafNode) {
			// For each list of candidates in the node
			for (List<Itemset> listCandidate : node.candidates) {
				if (listCandidate == null) {
					continue;
				}

				// For each candidate
				for (Itemset candidate : listCandidate) {

					// Create a bitmap representing this candidate
					// by setting the bit representing each of its item to 1.
					long candidateBitmap = 0L;
					for (int item : candidate.itemset) {
						candidateBitmap |= (1L << item);
					}

					// Count support by scanning all transactions
					int support = 0;
					for (int t = 0; t < transactionBitmaps.length; t++) {
						// Skip transactions shorter than the itemset length (k)
						// Use bitCount to get transaction length from bitmap - O(1)
						if (Long.bitCount(transactionBitmaps[t]) < k) {
							continue;
						}

						// O(1) subset check using bitwise AND
						// If (transaction & candidate) == candidate, then candidate is subset of
						// transaction
						if ((transactionBitmaps[t] & candidateBitmap) == candidateBitmap) {
							support += transactionWeights[t];
						}
					}

					candidate.support = support;
				}
			}
		}
	}
	// === END OF BITMAP-BASED SUBSET CHECKING OPTIMIZATION ===
	// ===========================================================

	/**
	 * Run the Apriori-FAST Top-K algorithm
	 * 
	 * @param kValue                 the number of top patterns to find (i.e. top-k)
	 * @param input                  path to the input file
	 * @param output                 path to save the result to an output file, or null to save to memory
	 * @param hash_tree_branch_count the number of child nodes for each node in the
	 *                               hash tree
	 * @return the priority queue containing the top-k itemsets
	 * @throws IOException if an error while reading/writing files
	 */
	public PriorityQueue<Itemset> runAlgorithm(int kValue, String input, String output, int hash_tree_branch_count)
			throws IOException {
		// record start time
		startTimestamp = System.currentTimeMillis();

		// ======================================
		// Initialize for top-k mining
		this.n = kValue;

		// Initialize the priority queue to store the top K patterns
		// This is a min-heap ordered by support, so the pattern with lowest support
		// is always at the top and can be efficiently removed when we find better patterns
		nItemsets = new PriorityQueue<>(Comparator.comparingInt(Itemset::getAbsoluteSupport));

		// Set the internal minsup value to 1 (will be raised dynamically)
		minsupRelative = 1;
		// ======================================

		// If output is not null, prepare object for writing the file
		// Otherwise, initialize the in-memory itemsets storage (SPMF style)
		if (output != null) {
			writer = new BufferedWriter(new FileWriter(output));
			itemsetsInMemory = null;
		} else {
			writer = null;
			itemsetsInMemory = new Itemsets("TOP-K FREQUENT ITEMSETS");
		}

		// reset statistics
		itemsetCount = 0;
		totalCandidateCount = 0;
		MemoryLogger.getInstance().reset();
		databaseSize = 0;

		// save the parameter
		this.hash_tree_branch_count = hash_tree_branch_count;

		// structure to count the support of each item
		// Key: item Value: support count
		Map<Integer, Integer> mapItemCount = new HashMap<Integer, Integer>();

		// the database in memory (initially empty)
		database = new ArrayList<int[]>(10000);

		// Temporary storage for reading raw transactions before recoding
		List<int[]> rawTransactions = new ArrayList<int[]>(10000);

		// Reusable list for parsing each transaction
		List<Integer> parsedItems = new ArrayList<Integer>(100);

		// Scan the database to load it into memory and count the support of each single
		// item at the same time
		BufferedReader reader = new BufferedReader(new FileReader(input));
		String line;
		// for each line (transaction) of the input file until the end of file
		while (((line = reader.readLine()) != null)) {
			// if the line is a comment, is empty or is a
			// kind of metadata, skip it
			if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
				continue;
			}

			// Read the transaction without using String.split()
			parseLineToInts(line, parsedItems);

			// Create an array to store the items
			int transaction[] = new int[parsedItems.size()];

			// For each item in the current transaction
			for (int i = 0; i < parsedItems.size(); i++) {
				// get the item (already an integer)
				Integer item = parsedItems.get(i);
				// add the item to the transaction
				transaction[i] = item;

				// increase the support count of the item
				Integer count = mapItemCount.get(item);
				if (count == null) {
					mapItemCount.put(item, 1);
				} else {
					mapItemCount.put(item, ++count);
				}
			}

			// add transaction to raw transactions (will be recoded later)
			rawTransactions.add(transaction);

			// increase the transaction count
			databaseSize++;
		}
		// close the input file
		reader.close();

		if (DEBUG_MODE) {
			System.out.println("database size = " + databaseSize + " looking for top-" + n + " patterns");
		}

		// Apriori will start by generating itemsets of size 1
		k = 1;

		// ======================================
		// === TOP-K OPTIMIZATION: Raise initial minsup based on item supports ===
		// If we have at least n items, we can raise minsup to the n-th highest
		// item support, since we know we will find at least n patterns
		int itemCount = mapItemCount.size();
		if (itemCount >= n) {
			// Collect all item supports
			int[] itemSupports = new int[itemCount];
			int index = 0;
			for (Entry<Integer, Integer> entry : mapItemCount.entrySet()) {
				itemSupports[index++] = entry.getValue();
			}
			// Sort supports in ascending order
			Arrays.sort(itemSupports);
			// The n-th highest support becomes our initial minsup
			minsupRelative = itemSupports[itemCount - n];
			if (DEBUG_MODE) {
				System.out.println("Initial minsup raised to: " + minsupRelative);
			}
		}
		// === END TOP-K OPTIMIZATION ===
		// ======================================

		// Create list of frequent items sorted by support (ascending)
		List<Map.Entry<Integer, Integer>> frequentItemsList = new ArrayList<Map.Entry<Integer, Integer>>();
		for (Entry<Integer, Integer> entry : mapItemCount.entrySet()) {
			if (entry.getValue() >= minsupRelative) {
				frequentItemsList.add(entry);
			}
		}

		// Free memory
		mapItemCount = null;

		// Sort items by support ascending order (less frequent items first)
		Collections.sort(frequentItemsList, new Comparator<Map.Entry<Integer, Integer>>() {
			public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
				return o1.getValue() - o2.getValue();
			}
		});

		// Save the number of frequent items
		frequentItemCount = frequentItemsList.size();

		// if no frequent item, we stop there!
		if (frequentItemCount == 0) {
			// save endtime
			endTimestamp = System.currentTimeMillis();

			// check the memory usage
			MemoryLogger.getInstance().checkMemory();

			// close the file if writer was created
			if (writer != null) {
				writer.close();
			}

			return nItemsets;
		}

		// Now based on the ascending order, we will rename the frequent items such
		// that the first item will be called 0, the second one will be called 1, and so
		// on.
		// This is done using a class called the NameConverter which remember the old
		// item names and the new item names.
		// Renaming items is useful because it simplifies the comparison of items by
		// Apriori.
		nameConverter = new ItemNameConverter(frequentItemCount, 0);
		for (Map.Entry<Integer, Integer> entry : frequentItemsList) {
			nameConverter.assignNewName(entry.getKey());
		}

		// Create the list of all frequent items of size 1 using new names (thus sorted)
		List<Integer> frequent1 = new ArrayList<Integer>(frequentItemCount);
		for (int i = 0; i < frequentItemCount; i++) {
			frequent1.add(i);
		}

		// ======================================
		// Save frequent items of size 1 to the top-k queue (using original names)
		for (Map.Entry<Integer, Integer> entry : frequentItemsList) {
			Itemset itemset1 = new Itemset(new int[] { entry.getKey() }, entry.getValue());
			saveItemsetToQueue(itemset1, entry.getValue());
		}
		// ======================================

		// Check maxPatternLength after saving 1-itemsets
		// If maxPatternLength is 1, we only want 1-itemsets, so stop here
		if (maxPatternLength <= 1) {
			// save endtime
			endTimestamp = System.currentTimeMillis();

			// check the memory usage
			MemoryLogger.getInstance().checkMemory();

			// Write the top-k patterns to file or memory
			writeQueueToOutput();

			// close the file if writer was created
			if (writer != null) {
				writer.close();
			}

			return nItemsets;
		}

		// === OPTIMIZATION: Check if bitmap optimization should be used ===
		if (frequentItemCount > MAX_ITEMS_FOR_BITMAP) {
			useBitmapOptimization = false;
			if (DEBUG_MODE) {
				System.out.println(" Bitmap optimization disabled: " + frequentItemCount + " items ");
			}
		} else {
			useBitmapOptimization = true;
			if (DEBUG_MODE) {
				System.out.println(" Bitmap optimization enabled: " + frequentItemCount + " items");
			}
		}
		// === END OPTIMIZATION ===

		// === OPTIMIZATION: Recode and merge transactions differently based on bitmap
		// If the bitmap representations is used, the database will be represented as
		// bitmaps.
		if (useBitmapOptimization) {
			processBitmapTransactions(rawTransactions);
		} else {
			// If the bitmap optimization is not used:
			// Recode transactions: keep only frequent items and use the new item names
			processArrayTransactions(rawTransactions);
		}

		// free memory as we dont need the raw transactions anymore
		rawTransactions = null;

		// Initialize projection buffer
		projectionBuffer = new int[frequentItemCount];

		// increase the number of candidates
		totalCandidateCount += frequent1.size();

		// Now, the algorithm recursively generates frequent itemsets of size K
		// by using frequent itemsets of size K-1 until no more
		// candidates can be generated.
		k = 2;

		// While the level is not empty
		int previousItemsetCount = itemsetCount;

		// Number of frequent items
		int previousActiveItemCount = frequent1.size();

		// Create an hashtree for storing candidates for efficient support counting
		ItemsetHashTree candidatesK = null;
		do {
			// check the memory usage
			MemoryLogger.getInstance().checkMemory();

			// Generate candidates of size K
			if (k == 2) {
				// if K=2, use an optimized version of candidate generation
				candidatesK = generateCandidate2(frequent1);
			} else {
				// Otherwise use the regular candidate generation procedure
				candidatesK = generateCandidateSizeK(candidatesK, k);
			}

			// if no candidates were generated, we stop candidate generation
			if (candidatesK.candidateCount == 0) {
				break;
			}

			// we keep the total number of candidates generated until now
			// for statistics purposes
			totalCandidateCount += candidatesK.candidateCount;

			// We scan the database one time to calculate the support
			// of each candidates and keep those with higher support.
			// === OPTIMIZATION: IF BITMAP-BASED SUBSET CHECKING OPTIMIZATION IS ACTIVATED
			// THEN USE BITMAP FOR SUPPORT COUNTING ===
			if (useBitmapOptimization) {
				countSupportUsingBitmaps(candidatesK, k);
				// === END OF OPTIMIZATION
			} else {
				// Use hash-tree based counting
				// This is done efficiently because the candidates are stored in a hash-tree.
				int dbSize = database.size();
				for (int t = 0; t < dbSize; t++) {
					int[] transaction = database.get(t);
					// NEW OPTIMIZATION 2013: Skip transactions shorter than k!
					if (transaction.length >= k) {
						candidatesK.updateSupportCount(transaction, transactionWeights[t]);
					}
				}
			}

			// We next save to the queue all the candidates that have a support
			// higher than the minsup threshold and remove those who does not.

			// Create array to track which items appear in frequent k-itemsets
			boolean[] activeItems = new boolean[frequentItemCount];
			int activeItemCount = 0;

			// === OPTIMIZATION ===
			// The algorithm will identify the "active" itemsets, that is those
			// that appears in the current frequent itemsets.
			// For each leaf of the hash tree:
			for (LeafNode node = candidatesK.lastInsertedNode; node != null; node = node.nextLeafNode) {
				// for each list of candidate itemsets stored in that node
				for (List<Itemset> listCandidate : node.candidates) {
					// if the list is not null
					if (listCandidate != null) {
						// Use iterator for efficient removal
						Iterator<Itemset> iter = listCandidate.iterator();
						while (iter.hasNext()) {
							Itemset candidate = iter.next();
							// if enough support, save the itemset to the top-k queue
							if (candidate.getAbsoluteSupport() >= minsupRelative) {
								// ======================================
								// Save to top-k queue with original item names
								saveItemsetToQueueWithConversion(candidate);
								// ======================================

								// === OPTIMIZATION: Mark items as active ===
								for (int item : candidate.itemset) {
									if (!activeItems[item]) {
										activeItems[item] = true;
										activeItemCount++;
									}
								}
								// === OPTIMIZATION ===
							} else {
								// otherwise remove it
								iter.remove();
							}
						}
					}
				}
			}
			// === END OPTIMIZATION ===

			// === OPTIMIZATION: Reduce database for next level ===
			// Try reducing the database if there are still frequent items, and
			// there are less than at the previous level
			if (k < maxPatternLength && activeItemCount > 0 && activeItemCount != previousActiveItemCount) {
				reduceDatabase(activeItems, k + 1);
			}

			if (DEBUG_MODE) {
				if (useBitmapOptimization) {
					System.out.println("  Level " + k + ": " + transactionBitmaps.length + " transactions, minsup=" + minsupRelative);
				} else {
					System.out.println("  Level " + k + ": " + database.size() + " transactions, minsup=" + minsupRelative);
				}
			}

			previousActiveItemCount = activeItemCount;
			// === END OPTIMIZATION ===

			// Continue recursively if some new itemsets were generated
			// during the current iteration
			k++;
		} while (previousItemsetCount != itemsetCount && k <= maxPatternLength);

		// ======================================
		// Write the top-k patterns to file or memory
		writeQueueToOutput();
		// ======================================

		// save endtime
		endTimestamp = System.currentTimeMillis();

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// close the file if writer was created
		if (writer != null) {
			writer.close();
		}

		return nItemsets;
	}

	/**
	 * Process transactions using bitmap representation.
	 * This method recodes transactions as bitmaps and merges identical ones.
	 * 
	 * @param rawTransactions the raw transactions read from file
	 */
	private void processBitmapTransactions(List<int[]> rawTransactions) {
		// Create list to store bitmap representations
		List<Long> transactionBitmapsList = new ArrayList<>(rawTransactions.size());

		for (int[] rawTransaction : rawTransactions) {
			// Build bitmap from frequent items
			long bitmap = 0L;
			for (int item : rawTransaction) {
				if (nameConverter.isOldItemExisting(item)) {
					int newItem = nameConverter.toNewName(item);
					bitmap |= (1L << newItem);
				}
			}

			// Only add non-empty transactions
			if (bitmap != 0L) {
				transactionBitmapsList.add(bitmap);
			}
		}

		// -- START OF TRANSACTION MERGING OPTIMIZATION
		// Now we will prepare the transaction merging optimization, which
		// consists of merging identical transactions.
		// To avoid the naive approach of comparing each transactions with each other
		// to find identical transactions, we will sort the transactions, and then
		// identical transactions will be consecutive.

		// Use indexed sorting to keep weights aligned with bitmaps
		// Create indices array for indirect sorting
		final int bitmapCount = transactionBitmapsList.size();
		Integer[] indices = new Integer[bitmapCount];
		for (int i = 0; i < bitmapCount; i++) {
			indices[i] = i;
		}

		// Sort indices based on bitmap values
		final List<Long> bitmapsRef = transactionBitmapsList;
		Arrays.sort(indices, new Comparator<Integer>() {
			public int compare(Integer i1, Integer i2) {
				return Long.compare(bitmapsRef.get(i1), bitmapsRef.get(i2));
			}
		});

		// Merge identical transactions using bitmap comparison (O(1))
		List<Long> uniqueBitmaps = new ArrayList<>(bitmapCount);
		List<Integer> weightsList = new ArrayList<>(bitmapCount);

		if (bitmapCount > 0) {
			long currentBitmap = transactionBitmapsList.get(indices[0]);
			int currentWeight = 1;

			for (int i = 1; i < bitmapCount; i++) {
				long nextBitmap = transactionBitmapsList.get(indices[i]);

				// O(1) bitmap comparison
				if (currentBitmap == nextBitmap) {
					// Identical transaction found, so just increment weight
					currentWeight++;
				} else {
					// Different transaction - save current and move to next
					uniqueBitmaps.add(currentBitmap);
					weightsList.add(currentWeight);
					currentBitmap = nextBitmap;
					currentWeight = 1;
				}
			}

			// Save the last transaction and weight
			uniqueBitmaps.add(currentBitmap);
			weightsList.add(currentWeight);
		}
		// -- END OF TRANSACTION MERGING OPTIMIZATION

		// Store bitmaps and weights in arrays for more efficiency
		transactionWeights = new int[uniqueBitmaps.size()];
		transactionBitmaps = new long[uniqueBitmaps.size()];

		for (int i = 0; i < uniqueBitmaps.size(); i++) {
			transactionBitmaps[i] = uniqueBitmaps.get(i);
			transactionWeights[i] = weightsList.get(i);
		}

		// We dont need to use the traditional database representation.
		database.clear();
	}

	/**
	 * Process transactions using array representation.
	 * This method recodes transactions as arrays and merges identical ones.
	 * 
	 * @param rawTransactions the raw transactions read from file
	 */
	private void processArrayTransactions(List<int[]> rawTransactions) {
		// Recode transactions: keep only frequent items and use the new item names
		List<int[]> recodedTransactions = new ArrayList<int[]>(rawTransactions.size());
		for (int[] rawTransaction : rawTransactions) {

			// Count the number of frequent items
			int count = 0;
			for (int item : rawTransaction) {
				if (nameConverter.isOldItemExisting(item)) {
					count++;
				}
			}

			// If there is at least one frequent item in the transaction
			if (count > 0) {
				// Create a new array of the correct size
				int[] items = new int[count];
				int index = 0;
				// Copy the frequent items to the transaction using the new names
				for (int item : rawTransaction) {
					if (nameConverter.isOldItemExisting(item)) {
						items[index++] = nameConverter.toNewName(item);
					}
				}
				// Sort the transaction by the new name order (ascending support)
				Arrays.sort(items);
				recodedTransactions.add(items);
			}
		}

		// -- START OF TRANSACTION MERGING OPTIMIZATION
		// Now we will prepare the transaction merging optimization, which
		// consists of merging identical transactions.
		// To avoid the naive approach of comparing each transactions with each other
		// to find identical transactions, we will sort the transactions, and then
		// identical transactions will be consecutive.
		Collections.sort(recodedTransactions, new Comparator<int[]>() {
			public int compare(int[] a, int[] b) {
				int minLen = Math.min(a.length, b.length);
				for (int i = 0; i < minLen; i++) {
					if (a[i] != b[i]) {
						return a[i] - b[i];
					}
				}
				return a.length - b.length;
			}
		});

		// Merge identical transactions
		// An array of weight is used to store each transaction's weight.
		// The weight is a number indicating the number of transactions that
		// are identical to a given transaction.
		List<Integer> weightsList = new ArrayList<Integer>(recodedTransactions.size());

		// If the database is not empty after removing infrequent items
		if (!recodedTransactions.isEmpty()) {
			// Get the first transaction and assume the weight is 1.
			int[] currentTrans = recodedTransactions.get(0);
			int currentWeight = 1;

			// Loop over next transactions to find identical transactions.
			// Since transactions are sorted, identical ones are consecutive.
			// We only need to compare with the immediate predecessor.
			for (int i = 1; i < recodedTransactions.size(); i++) {
				int[] nextTrans = recodedTransactions.get(i);

				// Quick length check first (O(1)) - if lengths differ, transactions can't be
				// equal
				// Since transactions are sorted by lexicographic order with length as
				// tiebreaker,
				// equal transactions must have same length
				boolean isEqual = (currentTrans.length == nextTrans.length);

				// Only do element-wise comparison if lengths match
				if (isEqual) {
					for (int j = 0; j < currentTrans.length; j++) {
						if (currentTrans[j] != nextTrans[j]) {
							isEqual = false;
							break;
						}
					}
				}

				if (isEqual) {
					// Identical transaction found - just increment weight
					currentWeight++;
				} else {
					// Different transaction - save current and move to next
					database.add(currentTrans);
					weightsList.add(currentWeight);
					currentTrans = nextTrans;
					currentWeight = 1;
				}
			}

			// Save the transaction and weight
			database.add(currentTrans);
			weightsList.add(currentWeight);
		}

		// Convert the list of weights to a primitive array
		transactionWeights = new int[weightsList.size()];
		for (int i = 0; i < weightsList.size(); i++) {
			transactionWeights[i] = weightsList.get(i);
		}
		// -- END OF TRANSACTION MERGING OPTIMIZATION
	}

	// ======================================
	/**
	 * Method to save an itemset to the queue of top-n patterns.
	 * The queue is a min-heap, so the pattern with lowest support is at the top.
	 * When the queue exceeds n patterns, we remove the lowest support patterns
	 * and raise the minsup threshold accordingly.
	 * 
	 * @param itemset an itemset (with original item names)
	 * @param support the support of the itemset
	 */
	private void saveItemsetToQueue(Itemset itemset, int support) {
		// Add the itemset to the priority queue
		nItemsets.add(itemset);

		// If we have more than n patterns
		if (nItemsets.size() > n) {
			// If this new pattern has higher support than current minsup
			if (support > this.minsupRelative) {
				// Remove patterns with lowest support until we have exactly n
				Itemset lower;
				do {
					lower = nItemsets.peek();
					if (lower == null) {
						break;
					}
					nItemsets.remove(lower);
				} while (nItemsets.size() > n);

				// Raise the minsup threshold to the support of the k-th best pattern
				this.minsupRelative = nItemsets.peek().getAbsoluteSupport();
			}
		}
	}

	/**
	 * Save an itemset to the queue, converting item names back to original.
	 * This method is used for itemsets found during candidate generation,
	 * which use renamed items internally.
	 * 
	 * @param itemset the itemset (using internal renamed items)
	 */
	private void saveItemsetToQueueWithConversion(Itemset itemset) {
		int length = itemset.itemset.length;

		// Convert back to original names
		int[] originalItems = new int[length];
		for (int i = 0; i < length; i++) {
			originalItems[i] = nameConverter.toOldName(itemset.itemset[i]);
		}

		// Sort by original item order
		Arrays.sort(originalItems);

		// Create itemset with original names and save to queue
		Itemset convertedItemset = new Itemset(originalItems, itemset.getAbsoluteSupport());
		saveItemsetToQueue(convertedItemset, itemset.getAbsoluteSupport());
		itemsetCount++;
	}

	/**
	 * Write all itemsets from the queue to the output file or save to memory.
	 * If writer is not null, writes to file. Otherwise, saves to itemsetsInMemory (SPMF style).
	 * 
	 * @throws IOException if an error occurs while writing to file
	 */
	private void writeQueueToOutput() throws IOException {
		if (writer != null) {
			// Write to file
			Iterator<Itemset> iter = nItemsets.iterator();
			while (iter.hasNext()) {
				Itemset itemset = iter.next();
				outputBuffer.setLength(0);
				for (int i = 0; i < itemset.itemset.length; i++) {
					if (i > 0) {
						outputBuffer.append(' ');
					}
					outputBuffer.append(itemset.itemset[i]);
				}
				outputBuffer.append(" #SUP: ");
				outputBuffer.append(itemset.getAbsoluteSupport());
				writer.write(outputBuffer.toString());
				writer.newLine();
			}
		} else {
			// Save to memory (SPMF style)
			Iterator<Itemset> iter = nItemsets.iterator();
			while (iter.hasNext()) {
				Itemset itemset = iter.next();
				int itemsetSize = itemset.itemset.length;
				// Add the itemset to the in-memory storage, organized by level (itemset size)
				itemsetsInMemory.addItemset(itemset, itemsetSize);
			}
		}
	}
	// ======================================

	/**
	 * Get the itemsets stored in memory (SPMF style).
	 * This method returns the itemsets when the output file was null.
	 * 
	 * @return the Itemsets object containing all top-k frequent itemsets, or null if output was not null
	 */
	public Itemsets getItemsets() {
		return itemsetsInMemory;
	}

	// === OPTIMIZATION: DATABASE REDUCTION METHOD ===
	/**
	 * Reduce the database by removing items that don't appear in any frequent
	 * k-itemset, removing transactions shorter than nextK, and merging identical
	 * transactions.
	 * 
	 * This optimization provides significant speedup because: 
	 * 1. Database shrinks progressively at each level 
	 * 2. Transactions become shorter 
	 * 3. More transactions become identical and can be merged 
	 * 4. Support counting becomes much faster
	 * 
	 * @param activeItems boolean array indicating items from frequent itemsets
	 * @param nextK       the size of itemsets for the next level
	 */
	private void reduceDatabase(boolean[] activeItems, int nextK) {
		// If using bitmaps, work ONLY with bitmaps
		if (useBitmapOptimization) {
			// Create active items bitmap mask
			long activeItemsMask = 0L;
			for (int item = 0; item < frequentItemCount; item++) {
				if (activeItems[item]) {
					activeItemsMask |= (1L << item);
				}
			}

			// Project transactions using bitmap operations
			List<Long> projectedBitmaps = new ArrayList<>(transactionBitmaps.length);
			List<Integer> projectedWeightsList = new ArrayList<>(transactionBitmaps.length);

			for (int t = 0; t < transactionBitmaps.length; t++) {
				long originalBitmap = transactionBitmaps[t];
				int weight = transactionWeights[t];

				// Project: keep only active items using bitwise AND
				long projectedBitmap = originalBitmap & activeItemsMask;

				// Count remaining items using bitCount - O(1)
				int count = Long.bitCount(projectedBitmap);

				// Only keep if enough items for next level
				if (count >= nextK) {
					projectedBitmaps.add(projectedBitmap);
					projectedWeightsList.add(weight);
				}
			}

			// If no valid transactions remain, clear everything
			if (projectedBitmaps.isEmpty()) {
				transactionWeights = new int[0];
				transactionBitmaps = new long[0];
				return;
			}

			// Use indexed sorting to keep weights aligned with bitmaps
			// Create indices array for indirect sorting
			final int projectedCount = projectedBitmaps.size();
			Integer[] indices = new Integer[projectedCount];
			for (int i = 0; i < projectedCount; i++) {
				indices[i] = i;
			}

			// Sort indices based on bitmap values
			final List<Long> bitmapsRef = projectedBitmaps;
			Arrays.sort(indices, new Comparator<Integer>() {
				public int compare(Integer i1, Integer i2) {
					return Long.compare(bitmapsRef.get(i1), bitmapsRef.get(i2));
				}
			});

			// Merge identical bitmaps using sorted indices - O(1) equality check
			List<Long> uniqueBitmaps = new ArrayList<>(projectedCount);
			List<Integer> newWeights = new ArrayList<>(projectedCount);

			long currentBitmap = projectedBitmaps.get(indices[0]);
			int currentWeight = projectedWeightsList.get(indices[0]);

			for (int i = 1; i < projectedCount; i++) {
				long nextBitmap = projectedBitmaps.get(indices[i]);
				int nextWeight = projectedWeightsList.get(indices[i]);

				// O(1) bitmap comparison
				if (currentBitmap == nextBitmap) {
					currentWeight += nextWeight;
				} else {
					uniqueBitmaps.add(currentBitmap);
					newWeights.add(currentWeight);
					currentBitmap = nextBitmap;
					currentWeight = nextWeight;
				}
			}
			// Add last transaction
			uniqueBitmaps.add(currentBitmap);
			newWeights.add(currentWeight);

			// Store ONLY bitmaps and weights
			transactionWeights = new int[uniqueBitmaps.size()];
			transactionBitmaps = new long[uniqueBitmaps.size()];

			for (int i = 0; i < uniqueBitmaps.size(); i++) {
				transactionBitmaps[i] = uniqueBitmaps.get(i);
				transactionWeights[i] = newWeights.get(i);
			}

		} else {
			// If not using bitmaps
			// Step 1: Project transactions - keep only active items
			// Use ArrayList to collect valid projected transactions
			List<int[]> projectedTransactions = new ArrayList<int[]>(database.size());
			int[] projectedWeightsTemp = new int[database.size()];
			int projectedCount = 0;
			int totalWeightedCount = 0;

			// For each transaction
			for (int t = 0; t < database.size(); t++) {
				int[] transaction = database.get(t);
				int weight = transactionWeights[t];

				// Project: count and copy only active items
				int count = 0;
				for (int item : transaction) {
					if (activeItems[item]) {
						projectionBuffer[count++] = item;
					}
				}

				// Only keep if enough items for next level
				if (count >= nextK) {
					int[] projected = new int[count];
					System.arraycopy(projectionBuffer, 0, projected, 0, count);
					// Items maintain sorted order since we process in order and keep relative
					// positions
					projectedTransactions.add(projected);
					projectedWeightsTemp[projectedCount++] = weight;
					totalWeightedCount += weight;
				}
			}

			// If no valid transactions remain, clear database
			if (projectedTransactions.isEmpty() || totalWeightedCount < minsupRelative) {
				database.clear();
				transactionWeights = new int[0];
				return;
			}

			// Step 2: Sort transactions lexicographically for merging
			// Create indices array for indirect sorting (avoids moving actual transaction
			// arrays)
			final int size = projectedTransactions.size();
			Integer[] indices = new Integer[size];
			for (int i = 0; i < size; i++) {
				indices[i] = i;
			}

			final List<int[]> finalProjected = projectedTransactions;
			Arrays.sort(indices, new Comparator<Integer>() {
				public int compare(Integer i1, Integer i2) {
					int[] a = finalProjected.get(i1);
					int[] b = finalProjected.get(i2);
					int minLen = Math.min(a.length, b.length);
					for (int i = 0; i < minLen; i++) {
						if (a[i] != b[i]) {
							return a[i] - b[i];
						}
					}
					return a.length - b.length;
				}
			});

			// Step 3: Merge identical transactions
			database.clear();
			List<Integer> newWeights = new ArrayList<Integer>(size);

			int[] currentTransaction = projectedTransactions.get(indices[0]);
			int currentWeight = projectedWeightsTemp[indices[0]];

			for (int i = 1; i < size; i++) {
				int[] nextTransaction = projectedTransactions.get(indices[i]);
				int nextWeight = projectedWeightsTemp[indices[i]];

				if (Arrays.equals(currentTransaction, nextTransaction)) {
					currentWeight += nextWeight;
				} else {
					database.add(currentTransaction);
					newWeights.add(currentWeight);
					currentTransaction = nextTransaction;
					currentWeight = nextWeight;
				}
			}
			database.add(currentTransaction);
			newWeights.add(currentWeight);

			// Convert to primitive array
			transactionWeights = new int[newWeights.size()];
			for (int i = 0; i < newWeights.size(); i++) {
				transactionWeights[i] = newWeights.get(i);
			}
		}
	}
	// === END DATABASE REDUCTION OPTIMIZATION ===

	/**
	 * Method to generate candidates of size k, where k > 2
	 * 
	 * @param candidatesK_1 the candidates of size k-1
	 * @param k             k
	 * @return the candidates of size k, stored in an hash-tree
	 */
	private ItemsetHashTree generateCandidateSizeK(ItemsetHashTree candidatesK_1, int k) {
		// create the hash-tree to store the candidates of size K
		ItemsetHashTree newCandidates = new ItemsetHashTree(k, hash_tree_branch_count);

		// The generation will be done by comparing the leaves of the hash-tree
		// containing the itemsets of size k-1.
		// To generate an itemsets, we need to use two itemsets from the same leaf node.

		// For each leaf node
		for (LeafNode node = candidatesK_1.lastInsertedNode; node != null; node = node.nextLeafNode) {
			List<Itemset> subgroups[] = node.candidates;
			// For each sets of itemsets in this node
			for (int i = 0; i < subgroups.length; i++) {
				if (subgroups[i] == null) {
					continue;
				}
				// For each sets of itemsets in this node
				for (int j = i; j < subgroups.length; j++) {
					if (subgroups[j] == null) {
						continue;
					}
					// try to use these list of itemsets to generate candidates.
					generate(subgroups[i], subgroups[j], candidatesK_1, newCandidates);
				}
			}
		}
		// Sort buckets for correct binary search in next level
		newCandidates.sortAllBuckets();

		return newCandidates;
	}

	/**
	 * Method to generate candidates of size k from two list of itemsets of size k-1
	 * 
	 * @param list1         the first list
	 * @param list2         the second list (may be equal to the first list)
	 * @param candidatesK   the hash-tree containing the candidates of size k-1
	 * @param newCandidates the hash-tree to store the candidates of size k
	 */
	private void generate(List<Itemset> list1, List<Itemset> list2, ItemsetHashTree candidatesK_1,
			ItemsetHashTree newCandidates) {
		// For each itemset I1 and I2 of lists
		loop1: for (int i = 0; i < list1.size(); i++) {
			int[] itemset1 = list1.get(i).itemset;

			// if the two lists are the same, we will start from i+1 in the second list
			// to avoid comparing pairs of itemsets twice.
			int j = (list1 == list2) ? i + 1 : 0;
			// For each itemset in list 2
			loop2: for (; j < list2.size(); j++) {
				int[] itemset2 = list2.get(j).itemset;

				// we compare items of itemset1 and itemset2.
				// If they have all the same k-1 items and the last item of
				// itemset1 is smaller than
				// the last item of itemset2, we will combine them to generate a
				// candidate
				for (int k = 0; k < itemset1.length; k++) {
					// if k is not the last item
					if (k != itemset1.length - 1) {
						if (itemset2[k] > itemset1[k]) {
							continue loop1; // we continue searching
						}
						if (itemset1[k] > itemset2[k]) {
							continue loop2; // we continue searching
						}
					}

				}
				// If we are here, it is because the two itemsets share
				// the same k-1 first item. Therefore, we can generate
				// a new candidate.
				// There is two cases depending if the last item of itemset1 is smaller
				// or greater than the last item of itemset2. We do this just to make
				// sure that we add items in the new candidate according to the lexicographical
				// order
				int newItemset[] = new int[itemset1.length + 1];
				if (itemset2[itemset2.length - 1] < itemset1[itemset1.length - 1]) {
					// Create a new candidate by combining itemset1 and itemset2
					System.arraycopy(itemset2, 0, newItemset, 0, itemset2.length);
					newItemset[itemset1.length] = itemset1[itemset1.length - 1];
				} else {
					// Create a new candidate by combining itemset1 and itemset2
					System.arraycopy(itemset1, 0, newItemset, 0, itemset1.length);
					newItemset[itemset1.length] = itemset2[itemset2.length - 1];
				}

				// The candidate is tested to see if its subsets of size k-1 are
				// included in level k-1 (they are frequent).
				if (allSubsetsOfSizeK_1AreFrequent(newItemset, candidatesK_1)) {
					// If yes, we add the candidate to the hash-tree
					newCandidates.insertCandidateItemset(new Itemset(newItemset));
				}
			}
		}
	}

	/**
	 * Method for generating the candidate itemsets of size 2.
	 * 
	 * @param frequent1 The frequent itemsets of size 1
	 * @return the candidate itemsets of size 2 stored in a hash-tree.
	 */
	private ItemsetHashTree generateCandidate2(List<Integer> frequent1) {
		// we create an hash-tree to store the candidates
		ItemsetHashTree candidates = new ItemsetHashTree(2, hash_tree_branch_count);

		// For each pair of frequent items
		for (int i = 0; i < frequent1.size(); i++) {
			Integer item1 = frequent1.get(i);
			for (int j = i + 1; j < frequent1.size(); j++) {
				Integer item2 = frequent1.get(j);
				// Create a new candidate by combining the two items and insert
				// it in the hash-tree
				candidates.insertCandidateItemset(new Itemset(new int[] { item1, item2 }));
			}
		}

		// Sort buckets for correct binary search when checking subsets
		// of size-3 candidates in the next level
		candidates.sortAllBuckets();

		return candidates; // return the hash-tree
	}

	/**
	 * This method checks if all the subsets of an items are frequent (i.e. if all
	 * the subsets are in the hash-tree of the previous level)
	 * 
	 * @param itemset               the itemset
	 * @param hashtreeCandidatesK_1 the hash-tree of the previous level
	 * @return true if all subsets are frequent, false otherwise
	 */
	protected boolean allSubsetsOfSizeK_1AreFrequent(int[] itemset, ItemsetHashTree hashtreeCandidatesK_1) {
		// generate all subsets by always each item from the candidate, one by one
		for (int posRemoved = 0; posRemoved < itemset.length; posRemoved++) {

			if (hashtreeCandidatesK_1.isInTheTree(itemset, posRemoved) == false) { 
				// if we did not find it, that means that candidate is not a frequent
				// itemset because at least one of its subsets does not appear in level k-1.
				return false;
			}
		}
		return true;
	}

	/**
	 * Return the number of transactions in the last database read by the algorithm.
	 * 
	 * @return the number of transactions.
	 */
	public int getDatabaseSize() {
		return databaseSize;
	}

	/**
	 * Method to print statistics about the execution of the algorithm.
	 */
	public void printStats() {
		System.out.println("============= APRIORI-FAST TOP-K 2.65 - STATS =============");
		System.out.println(" Top-k patterns requested: " + n);
		System.out.println(" Top-k patterns found: " + nItemsets.size());
		System.out.println(" Final internal minsup: " + minsupRelative);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		if (DEBUG_MODE) {
			if (useBitmapOptimization) {
				System.out.println(" Unique transactions after merging: " + transactionBitmaps.length);
			} else {
				System.out.println(" Unique transactions after merging: " + database.size());
			}
			System.out.println(" Candidates count : " + totalCandidateCount);
			System.out.println(" The algorithm stopped at size " + (k - 1));
			System.out.println(" Bitmap optimization: " + (useBitmapOptimization ? "enabled" : "disabled"));
		}
		System.out.println("======================================================");
	}

	/**
	 * Set the maximum pattern length
	 * 
	 * @param length the maximum length
	 */
	public void setMaximumPatternLength(int length) {
		maxPatternLength = length;
	}
}