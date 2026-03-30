package ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.ItemsetHashTree.LeafNode;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an efficient implementation of the Apriori-Close algorithm with multiple
 * optimizations for mining closed frequent itemsets. Besides using a hash-tree for 
 * support counting (as in AlgoAprioriHT) this version applies four optimizations: <br/>
 * (1) merging identical transactions, (2) renaming items to speed up
 * comparisons, (3) using bitmap subset checking if there is no more than 64
 * items in the input dataset, (4) projecting transactions. <br/>
 * 
 * The Apriori-Close algorithm is based on the Close algorithm described in: <br/>
 * <br/>
 * Pasquier, N., Bastide, Y., Taouil, R., Lakhal, L., (1999). Efficient Mining
 * of Association Rules using Closed Itemset Lattices. Information Systems,
 * Elsevier Science, 24(1), pages 25-46. <br/>
 * <br/>
 * 
 * The original Apriori algorithm is described in : <br/>
 * <br/>
 * Agrawal R, Srikant R. "Fast Algorithms for Mining Association Rules", VLDB.
 * Sep 12-15 1994, Chile, 487-99, <br/>
 * <br/>
 * 
 * The Apriori-Close algorithm finds all the closed frequent itemsets and their support in a
 * transaction database. An itemset is closed if no proper superset has the same support. <br/>
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
public class AlgoAprioriFASTClose {

	/** the maximul level reached by Apriori */
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

	/** the relative minimum support used to find itemsets */
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
	// Reusable buffer for projecting transactions
	private int[] projectionBuffer = null;

	// === NEW: For saving results to memory ===
	/** Object to store the frequent itemsets found (if saving to memory) */
	private Itemsets patterns = null;

	/** Database size (number of transactions) */
	private int databaseSize = 0;
	
	// === FOR CLOSED ITEMSET MINING ===
	/** Support values for frequent 1-itemsets (indexed by new item name) */
	private int[] item1Supports = null;

	/**
	 * Default constructor
	 */
	public AlgoAprioriFASTClose() {
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
	 * Run the Apriori-FAST-Close algorithm
	 * 
	 * @param minsup                 the minimum support threshold
	 * @param input                  path to the input file
	 * @param output                 path to save the result to an output file,
	 *                               or null to save results to memory
	 * @param hash_tree_branch_count the number of child nodes for each node in the
	 *                               hash tree
	 * @return the frequent closed itemsets found (Itemsets object), or null if saving to file
	 * @throws IOException if an error while reading/writing files
	 */
	public Itemsets runAlgorithm(double minsup, String input, String output, int hash_tree_branch_count)
			throws IOException {
		// record start time
		startTimestamp = System.currentTimeMillis();

		// === NEW: Check if we save to file or memory ===
		if (output == null) {
			// If output is null, we will save to memory
			writer = null;
			patterns = new Itemsets("FREQUENT CLOSED ITEMSETS");
		} else {
			// prepare object for writing the file
			writer = new BufferedWriter(new FileWriter(output));
			patterns = null;
		}
		// === END NEW ===

		// reset statistics
		itemsetCount = 0;
		totalCandidateCount = 0;
		databaseSize = 0;
		MemoryLogger.getInstance().reset();
		int transactionCount = 0;

		// save the parameter
		this.hash_tree_branch_count = hash_tree_branch_count;

		// structure to count the support of each item
		// Key: item Value: support count
		Map<Integer, Integer> mapItemCount = new HashMap<Integer, Integer>();

		// the database in memory (intially empty)
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

			// === MODIFIED ==
			// Read the transaction without using String.split()
			parseLineToInts(line, parsedItems);
			// ==== END MODIFIED ===

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

			// === MODIFIED ==
			// add transaction to raw transactions (will be recoded later)
			rawTransactions.add(transaction);
			// ==== END MODIFIED ===

			// increase the transaction count
			transactionCount++;
		}
		// close the input file
		reader.close();

		// === NEW: Save the database size ===
		databaseSize = transactionCount;
		// === END NEW ===

		// convert absolute minimum support to a relative minimum support
		// by multiplying minsup by the database size
		this.minsupRelative = (int) Math.ceil(minsup * transactionCount);

		if (DEBUG_MODE) {
			System.out.println("database size = " + transactionCount + " minsuprel = " + minsupRelative);
		}

		// Apriori will start by generating itemsets of size 1
		k = 1;

		// === MODIFIED ==
		// Create list of frequent items sorted by support (ascending)
		List<Map.Entry<Integer, Integer>> frequentItemsList = new ArrayList<Map.Entry<Integer, Integer>>();
		for (Entry<Integer, Integer> entry : mapItemCount.entrySet()) {
			if (entry.getValue() >= minsupRelative) {
				frequentItemsList.add(entry);
			}
		}

		// Free memory
		mapItemCount = null;

		// Save the number of frequent items
		frequentItemCount = frequentItemsList.size();

		// if no frequent item, we stop there!
		// BUG FIX: Changed condition from maxPatternLength <= 1 to maxPatternLength < 1
		// to allow saving 1-itemsets when maxPatternLength == 1
		if (frequentItemCount == 0) {

			// save endtime
			endTimestamp = System.currentTimeMillis();

			// check the memory usage
			MemoryLogger.getInstance().checkMemory();

			// close the file if writing to file
			if (writer != null) {
				writer.close();
			}

			return patterns;
		}

		// Sort items by support ascending order (less frequent items first)
		Collections.sort(frequentItemsList, new Comparator<Map.Entry<Integer, Integer>>() {
			public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
				return o1.getValue() - o2.getValue();
			}
		});

		// Now based on the ascending order, we will rename the frequent items such
		// that the first item will be called 0, the second one will be called 1, and so
		// on.
		// This is done using a class called the NameConverter which remember the old
		// item names and the new item names.
		// Renaming items is useful because it simplifies the comparison of items by
		// Apriori.
		nameConverter = new ItemNameConverter(frequentItemCount, 0);
		
		// === MODIFIED FOR CLOSED ITEMSETS ===
		// Store supports for 1-itemsets (indexed by new name) for closedness checking
		item1Supports = new int[frequentItemCount];
		int newNameIndex = 0;
		for (Map.Entry<Integer, Integer> entry : frequentItemsList) {
			nameConverter.assignNewName(entry.getKey());
			item1Supports[newNameIndex] = entry.getValue();
			newNameIndex++;
		}
		// === END MODIFIED FOR CLOSED ITEMSETS ===

		// Create the list of all frequent items of size 1 using new names (thus sorted)
		List<Integer> frequent1 = new ArrayList<Integer>(frequentItemCount);
		for (int i = 0; i < frequentItemCount; i++) {
			frequent1.add(i);
		}

		// === MODIFIED FOR CLOSED ITEMSETS ===
		// Don't save 1-itemsets yet - need to check if they are closed first
		// === END MODIFIED FOR CLOSED ITEMSETS ===

		// BUG FIX: Check maxPatternLength after saving 1-itemsets
		// If maxPatternLength is 1, we only want 1-itemsets, so stop here
		if (maxPatternLength <= 1) {
			// === MODIFIED FOR CLOSED ITEMSETS ===
			// If maxPatternLength is 1, all frequent 1-itemsets are closed
			// (no supersets can exist)
			for (int i = 0; i < frequentItemCount; i++) {
				int originalItem = nameConverter.toOldName(i);
				saveItemsetToFile(originalItem, item1Supports[i]);
			}
			// === END MODIFIED FOR CLOSED ITEMSETS ===
			
			// save endtime
			endTimestamp = System.currentTimeMillis();

			// check the memory usage
			MemoryLogger.getInstance().checkMemory();

			// close the file if writing to file
			if (writer != null) {
				writer.close();
			}

			return patterns;
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

			// Free memory as we don't need the raw transactions anymore
			rawTransactions = null;

			// -- START OF TRANSACTION MERGING OPTIMIZATION
			// Now we will prepare the transaction merging optimization, which
			// consists of merging identical transactions.
			// To avoid the naive approach of comparing each transactions with each other
			// to find identical transactions, we will sort the transactions, and then
			// identical transactions will be consecutive.
			
			// BUG FIX: Use indexed sorting to keep weights aligned with bitmaps
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
		} else {
			// If the bitmap optimization is not used:
			// Recode transactions: keep only frequent items and use the new item names
			List<int[]> recodedTransactions = new ArrayList<int[]>(rawTransactions.size());
			for (int[] rawTransaction : rawTransactions) {

				// Count the number of frequent items
				int count = 0;
				for (int item : rawTransaction) {
					//
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

			// free memory as we dont need the raw transactions anymore
			rawTransactions = null;

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
			weightsList = null;
			recodedTransactions = null;
			// -- END OF TRANSACTION MERGING OPTIMIZATION
		}
		// ==== END MODIFIED ===

		// Initialize projection buffer ===
		projectionBuffer = new int[frequentItemCount];

		// increase the number of candidates
		totalCandidateCount += frequent1.size();

		// Now, the algorithm recursively generates frequent itemsets of size K
		// by using frequent itemsets of size K-1 until no more
		// candidates can be generated.
		k = 2;

		// Number of frequent items
		int previousActiveItemCount = frequent1.size();

		// Create an hashtree for storing candidates for efficient support counting
		ItemsetHashTree candidatesK = null;
		
		// === MODIFIED FOR CLOSED ITEMSETS ===
		// Store frequent itemsets from previous level for closedness checking
		List<Itemset> previousLevelFrequentItemsets = null;
		List<Itemset> currentLevelFrequentItemsets = null;
		// === END MODIFIED FOR CLOSED ITEMSETS ===
		
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

			// We next save to file all the candidates that have a support
			// higher than the minsup threshold and remove those who does not.

			// Create array to track which items appear in frequent k-itemsets
			boolean[] activeItems = new boolean[frequentItemCount];
			int activeItemCount = 0;

			// === MODIFIED FOR CLOSED ITEMSETS ===
			// Store current level frequent itemsets for closedness checking
			currentLevelFrequentItemsets = new ArrayList<Itemset>();
			// === END MODIFIED FOR CLOSED ITEMSETS ===

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
							// if enough support, save the itemset
							if (candidate.getAbsoluteSupport() >= minsupRelative) {
								// === MODIFIED FOR CLOSED ITEMSETS ===
								// Don't save yet - add to current level for closedness checking
								currentLevelFrequentItemsets.add(candidate);
								// === END MODIFIED FOR CLOSED ITEMSETS ===

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
			
			// === MODIFIED FOR CLOSED ITEMSETS ===
			// Check if itemsets from level k-1 are closed by comparing with level k
			if (k == 2) {
				// Special case: check if 1-itemsets are closed
				if (useBitmapOptimization) {
			        checkIfItems1AreClosedBitmap(frequent1, currentLevelFrequentItemsets);
			    } else {
			        checkIfItems1AreClosed(frequent1, currentLevelFrequentItemsets);
			    }
			} else if (previousLevelFrequentItemsets != null) {
				// General case: check if (k-1)-itemsets are closed
				
				// If using the bitmap representation (less than 64 items)
				if(useBitmapOptimization) {
					checkIfItemsetsK_1AreClosedBitmap(previousLevelFrequentItemsets, currentLevelFrequentItemsets);
				}else {
					// If more than 65 items..
					checkIfItemsetsK_1AreClosed(previousLevelFrequentItemsets, currentLevelFrequentItemsets);
				}
			}
			
			// Update previous level for next iteration
			previousLevelFrequentItemsets = currentLevelFrequentItemsets;
			// === END MODIFIED FOR CLOSED ITEMSETS ===

			// === OPTIMIZATION: Reduce database for next level ===
			// Try reducing the database if there are still frequent items, and
			// there are less than at the previous level
			if (k < maxPatternLength && activeItemCount > 0 && activeItemCount != previousActiveItemCount) {
				reduceDatabase(activeItems, k + 1);
			}
			
			if (DEBUG_MODE) {
				if (useBitmapOptimization) {
					System.out.println("  Level " + k + ": " + transactionBitmaps.length + " transactions");
				} else {
					System.out.println("  Level " + k + ": " + database.size() + " transactions");
				}
			}

			previousActiveItemCount = activeItemCount;
			// === END OPTIMIZATION ===
			// Continue recursively if some new itemsets were generated
			// during the current iteration
			k++;
		} while (currentLevelFrequentItemsets != null && !currentLevelFrequentItemsets.isEmpty() && k <= maxPatternLength);

		// === MODIFIED FOR CLOSED ITEMSETS ===
		// The itemsets at the last level are all closed (no supersets exist)
		if (currentLevelFrequentItemsets != null) {
			for (Itemset itemset : currentLevelFrequentItemsets) {
				saveItemsetToFileWithConversion(itemset);
			}
		}
		// === END MODIFIED FOR CLOSED ITEMSETS ===

		// save endtime
		endTimestamp = System.currentTimeMillis();

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// close the file if writing to file
		if (writer != null) {
			writer.close();
		}

		return patterns;
	}

	// === NEW METHODS FOR CLOSED ITEMSET CHECKING ===
	
	/**
	 * Checks if all the itemsets of size K-1 are closed by comparing them with
	 * itemsets of size K. An itemset is closed if no proper superset has the same support.
	 * 
	 * @param levelKm1 itemsets of size k-1
	 * @param levelK   itemsets of size k
	 * @throws IOException exception if error writing output file
	 */
	private void checkIfItemsetsK_1AreClosed(List<Itemset> levelKm1, List<Itemset> levelK) throws IOException {
	    // Group level K itemsets by support for O(1) lookup
	    Map<Integer, List<Itemset>> levelKBySupport = new HashMap<>();
	    for (Itemset itemset : levelK) {
	        levelKBySupport.computeIfAbsent(itemset.getAbsoluteSupport(), 
	            k -> new ArrayList<>()).add(itemset);
	    }
	    
	    // Check each k-1 itemset only against k-itemsets with same support
	    for (Itemset itemset : levelKm1) {
	        int support = itemset.getAbsoluteSupport();
	        List<Itemset> sameSupportK = levelKBySupport.get(support);
	        
	        boolean isClosed = true;
	        if (sameSupportK != null) {
	            for (Itemset itemsetK : sameSupportK) {
	                if (containsAll(itemsetK, itemset)) {
	                    isClosed = false;
	                    break;
	                }
	            }
	        }
	        
	        if (isClosed) {
	            saveItemsetToFileWithConversion(itemset);
	        }
	    }
	}
	
	/**
	 * Checks if all the itemsets of size K-1 are closed by comparing them with
	 * itemsets of size K. An itemset is closed if no proper superset has the same support.
	 * This version is using the bitmap representation when no more than 64 items.
	 * 
	 * @param levelKm1 itemsets of size k-1
	 * @param levelK   itemsets of size k
	 * @throws IOException exception if error writing output file
	 */
	private void checkIfItemsetsK_1AreClosedBitmap(List<Itemset> levelKm1, List<Itemset> levelK) throws IOException {
	    // Pre-compute bitmaps for level K, grouped by support
	    Map<Integer, List<Long>> levelKBitmapsBySupport = new HashMap<>();
	    for (Itemset itemset : levelK) {
	        long bitmap = 0L;
	        for (int item : itemset.itemset) {
	            bitmap |= (1L << item);
	        }
	        levelKBitmapsBySupport.computeIfAbsent(itemset.getAbsoluteSupport(), 
	            k -> new ArrayList<>()).add(bitmap);
	    }
	    
	    for (Itemset itemset : levelKm1) {
	        // Create bitmap for k-1 itemset
	        long itemsetBitmap = 0L;
	        for (int item : itemset.itemset) {
	            itemsetBitmap |= (1L << item);
	        }
	        
	        boolean isClosed = true;
	        List<Long> sameSupportBitmaps = levelKBitmapsBySupport.get(itemset.getAbsoluteSupport());
	        
	        if (sameSupportBitmaps != null) {
	            for (long kBitmap : sameSupportBitmaps) {
	                // O(1) containment check!
	                if ((kBitmap & itemsetBitmap) == itemsetBitmap) {
	                    isClosed = false;
	                    break;
	                }
	            }
	        }
	        
	        if (isClosed) {
	            saveItemsetToFileWithConversion(itemset);
	        }
	    }
	}

	
	/**
	 * Checks if all the itemsets of size 1 are closed by comparing them with
	 * itemsets of size 2.
	 * 
	 * @param frequent1 list of frequent items (using new names)
	 * @param level2    itemsets of size 2
	 * @throws IOException exception if error writing output file
	 */
	private void checkIfItems1AreClosed(List<Integer> frequent1, List<Itemset> level2) throws IOException {
	    // Group level 2 itemsets by support
	    Map<Integer, List<Itemset>> level2BySupport = new HashMap<>();
	    for (Itemset itemset : level2) {
	        level2BySupport.computeIfAbsent(itemset.getAbsoluteSupport(), 
	            s -> new ArrayList<>()).add(itemset);
	    }
	    
	    for (Integer item : frequent1) {
	        int support = item1Supports[item];
	        List<Itemset> sameSupportItemsets = level2BySupport.get(support);
	        
	        boolean isClosed = true;
	        if (sameSupportItemsets != null) {
	            for (Itemset itemset2 : sameSupportItemsets) {
	                if (contains(itemset2, item)) {
	                    isClosed = false;
	                    break;
	                }
	            }
	        }
	        
	        if (isClosed) {
	            int originalItem = nameConverter.toOldName(item);
	            saveItemsetToFile(originalItem, support);
	        }
	    }
	}
	
	/**
	 * Checks if all the itemsets of size 1 are closed by comparing them with
	 * itemsets of size 2, using bitmap operations for O(1) containment checks.
	 * An itemset is closed if no proper superset has the same support.
	 * 
	 * <p>This method is optimized for datasets with 64 or fewer frequent items,
	 * where each itemset can be represented as a single 64-bit bitmap. The
	 * containment check becomes a simple bitwise AND operation instead of
	 * array traversal.</p>
	 * 
	 * <p>The method groups level-2 itemsets by support first, so each 1-itemset
	 * only needs to be compared against 2-itemsets with the same support value.
	 * This significantly reduces the number of comparisons needed.</p>
	 * 
	 * @param frequent1 list of frequent items using internal renamed names (0 to frequentItemCount-1)
	 * @param level2    list of frequent itemsets of size 2
	 * @throws IOException if an error occurs while writing to the output file
	 * 
	 * @see #checkIfItems1AreClosed(List, List) for the non-bitmap version
	 * @see #checkIfItemsetsK_1AreClosedBitmap(List, List) for the general k-itemset version
	 */
	private void checkIfItems1AreClosedBitmap(List<Integer> frequent1, List<Itemset> level2) throws IOException {
	    // Pre-compute bitmaps for level 2 itemsets, grouped by support.
	    // Key: support value, Value: list of bitmaps for itemsets with that support
	    Map<Integer, List<Long>> level2BitmapsBySupport = new HashMap<>();
	    
	    for (Itemset itemset : level2) {
	        // Create bitmap representation of this 2-itemset
	        // Each bit position corresponds to an item (using renamed item IDs 0-63)
	        long bitmap = 0L;
	        for (int item : itemset.itemset) {
	            bitmap |= (1L << item);
	        }
	        
	        // Group by support for efficient lookup
	        level2BitmapsBySupport.computeIfAbsent(itemset.getAbsoluteSupport(), 
	            k -> new ArrayList<>()).add(bitmap);
	    }
	    
	    // Check each 1-itemset for closedness
	    for (Integer item : frequent1) {
	        // Create bitmap for this single item
	        // Only one bit is set, corresponding to the item's position
	        long itemBitmap = 1L << item;
	        
	        // Get the support of this 1-itemset
	        int support = item1Supports[item];
	        
	        // Assume the item is closed until proven otherwise
	        boolean isClosed = true;
	        
	        // Get all 2-itemsets with the same support
	        List<Long> sameSupportBitmaps = level2BitmapsBySupport.get(support);
	        
	        // If there are 2-itemsets with the same support, check containment
	        if (sameSupportBitmaps != null) {
	            for (long level2Bitmap : sameSupportBitmaps) {
	                // O(1) containment check using bitwise AND:
	                // If (level2Bitmap & itemBitmap) == itemBitmap, then the 2-itemset
	                // contains this item. Since 2-itemset is a proper superset with
	                // same support, the 1-itemset is not closed.
	                if ((level2Bitmap & itemBitmap) == itemBitmap) {
	                    isClosed = false;
	                    break;
	                }
	            }
	        }
	        
	        // If no superset with same support was found, the itemset is closed
	        if (isClosed) {
	            // Convert back to original item name before saving
	            int originalItem = nameConverter.toOldName(item);
	            saveItemsetToFile(originalItem, support);
	        }
	    }
	}
	
	/**
	 * Check if an itemset contains all items from another itemset
	 * 
	 * @param itemset1 the first itemset (potential superset)
	 * @param itemset2 the second itemset (potential subset)
	 * @return true if itemset1 contains all items of itemset2
	 */
	/**
	 * Check if an itemset contains all items from another itemset.
	 * Both itemsets are assumed to be sorted in ascending order.
	 * 
	 * @param itemset1 the first itemset (potential superset)
	 * @param itemset2 the second itemset (potential subset)
	 * @return true if itemset1 contains all items of itemset2
	 */
	private boolean containsAll(Itemset itemset1, Itemset itemset2) {
		int[] items1 = itemset1.itemset;
		int[] items2 = itemset2.itemset;
		
		// If itemset1 is smaller, it can't contain all of itemset2
		if (items1.length < items2.length) {
			return false;
		}
		
		int i = 0; // pointer for itemset1
		int j = 0; // pointer for itemset2
		
		while (i < items1.length && j < items2.length) {
			if (items1[i] < items2[j]) {
				// Move pointer in itemset1
				i++;
			} else if (items1[i] == items2[j]) {
				// Found a match, move both pointers
				i++;
				j++;
			} else {
				// items1[i] > items2[j], meaning items2[j] is not in items1
				return false;
			}
		}
		
		// If we've gone through all of itemset2, then itemset1 contains all of itemset2
		return j == items2.length;
	}
	
	/**
	 * Check if an itemset contains a specific item
	 * 
	 * @param itemset the itemset
	 * @param item    the item to search for
	 * @return true if the itemset contains the item
	 */
	private boolean contains(Itemset itemset, int item) {
		return Arrays.binarySearch(itemset.itemset, item) >= 0;
	}
	
	// === END NEW METHODS FOR CLOSED ITEMSET CHECKING ===

	// === OPTIMIZATION: DATABASE REDUCTION METHOD ===
	/**
	 * Reduce the database by removing items that don't appear in any frequent
	 * k-itemset, removing transactions shorter than nextK, and merging identical
	 * transactions.
	 * 
	 * This optimization provides significant speedup because: 1. Database shrinks
	 * progressively at each level 2. Transactions become shorter 3. More
	 * transactions become identical and can be merged 4. Support counting becomes
	 * much faster
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

			// BUG FIX: Use indexed sorting to keep weights aligned with bitmaps
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
		// BUG FIX: Sort buckets for correct binary search in next level
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

		// BUG FIX: Sort buckets for correct binary search when checking subsets
		// of size-3 candidates in the next level
		candidates.sortAllBuckets();

		return candidates; // return the hash-tree
	}

	/**
	 * This method checks if all the subsets of an items are frequent (i.e. if all
	 * the subsets are in the hash-tree of the previous level)
	 * 
	 * @param itemset                the itemset
	 * @param hashtreeCandidatesK_1 the hash-tree of the previous level
	 * @return
	 */
	protected boolean allSubsetsOfSizeK_1AreFrequent(int[] itemset, ItemsetHashTree hashtreeCandidatesK_1) {
		// generate all subsets by always each item from the candidate, one by one
		for (int posRemoved = 0; posRemoved < itemset.length; posRemoved++) {

			if (hashtreeCandidatesK_1.isInTheTree(itemset, posRemoved) == false) { // if we did not find it, that means
																					// that candidate is not a frequent
																					// itemset because
				// at least one of its subsets does not appear in level k-1.
				return false;
			}
		}
		return true;
	}

	/**
	 * Method to save a frequent itemset to file or memory, converting item names back to
	 * original
	 * 
	 * @param itemset the itemset (using internal renamed items)
	 * @throws IOException
	 */
	void saveItemsetToFileWithConversion(Itemset itemset) throws IOException {
		int length = itemset.itemset.length;

		// Reuse projectionBuffer for conversion (already allocated to
		// frequentItemCount)
		for (int i = 0; i < length; i++) {
			projectionBuffer[i] = nameConverter.toOldName(itemset.itemset[i]);
		}

		// Sort only the used portion
		Arrays.sort(projectionBuffer, 0, length);

		// === MODIFIED: Save to file or memory ===
		if (writer != null) {
			// Save to file
			outputBuffer.setLength(0);
			for (int i = 0; i < length; i++) {
				outputBuffer.append(projectionBuffer[i]);
				outputBuffer.append(' ');
			}
			outputBuffer.append("#SUP: ");
			outputBuffer.append(itemset.getAbsoluteSupport());

			writer.write(outputBuffer.toString());
			writer.newLine();
		} else {
			// Save to memory
			int[] convertedItemset = new int[length];
			System.arraycopy(projectionBuffer, 0, convertedItemset, 0, length);
			Itemset newItemset = new Itemset(convertedItemset);
			newItemset.setAbsoluteSupport(itemset.getAbsoluteSupport());
			patterns.addItemset(newItemset, length);
		}
		// === END MODIFIED ===
		itemsetCount++;
	}

	/**
	 * Method to save a frequent itemset of size 1 to file or memory.
	 * @param item the item contained in the itemset.
	 * @param support the support of the item.
	 * @throws IOException if an error happens while writing to file.
	 */
	void saveItemsetToFile(Integer item, Integer support) throws IOException {
		// === MODIFIED: Save to file or memory ===
		if (writer != null) {
			// Save to file
			writer.write(item + " #SUP: " + support);
			writer.newLine();
		} else {
			// Save to memory
			int[] itemsetArray = new int[] { item };
			Itemset newItemset = new Itemset(itemsetArray);
			newItemset.setAbsoluteSupport(support);
			patterns.addItemset(newItemset, 1);
		}
		// === END MODIFIED ===
		itemsetCount++;
	}

	/**
	 * Method to print statistics about the execution of the algorithm.
	 */
	public void printStats() {
		System.out.println("============= APRIORI-FAST-CLOSE 2.65 - STATS =============");
		System.out.println(" Frequent closed itemsets count : " + itemsetCount);
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
		System.out.println("===================================================");
	}

	/**
	 * Set the maximum pattern length
	 * @param length the maximum length
	 */
	public void setMaximumPatternLength(int length) {
		if(length < 1) {
			throw new InvalidParameterException("Maximum length must be at least 1");
		}
		maxPatternLength = length;
	}

	/**
	 * Get the database size (number of transactions)
	 * @return the database size
	 */
	public int getDatabaseSize() {
		return databaseSize;
	}
}