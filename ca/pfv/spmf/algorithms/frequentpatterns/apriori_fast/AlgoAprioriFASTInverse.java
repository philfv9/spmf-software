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
 * This is an efficient implementation of the AprioriInverse algorithm with multiple
 * optimizations from AprioriSFAST. <br/>
 * 
 * AprioriInverse finds all "perfectly rare" (sporadic) itemsets - itemsets where
 * all subsets are rare (have support below maxsup threshold). <br/>
 * 
 * This implementation includes: <br/>
 * (1) merging identical transactions, (2) renaming items to speed up
 * comparisons, (3) using bitmap subset checking if there is no more than 64
 * items in the input dataset, (4) projecting transactions. <br/>
 * 
 * The original AprioriInverse algorithm is described in: <br/>
 * Yun Sing Koh, Nathan Rountree: Finding Sporadic Rules Using Apriori-Inverse.
 * PAKDD 2005: 97-106 <br/>
 * 
 * @see Itemset
 * @see ItemsetHashTree
 * @author Philippe Fournier-Viger
 */
public class AlgoAprioriFASTInverse {

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

	/** the relative minimum support used to find itemsets */
	private int minsupRelative;

	/** the relative maximum support used to find sporadic itemsets */
	private int maxsupRelative;

	/** an in-memory representation of the transaction database */
	private List<int[]> database = null;

	/** number of sporadic items (items with minsup <= support < maxsup) */
	private int sporadicItemCount;

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

	// === OPTIMIZATION 2: For renaming items to speed up comparison ==
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

	// === For saving results to memory ===
	/** Object to store the sporadic itemsets found (if saving to memory) */
	private Itemsets patterns = null;

	/** Database size (number of transactions) */
	private int databaseSize = 0;

	/**
	 * Default constructor
	 */
	public AlgoAprioriFASTInverse() {
	}

	/**
	 * Parse integers from a line to read a transaction
	 * 
	 * @param line   the input line
	 * @param result list for returning the items that have been read.
	 */
	private void parseLineToInts(String line, List<Integer> result) {
		result.clear();
		int length = line.length();
		int number = 0;
		boolean isInteger = false;

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
	// === BITMAP-BASED SUBSET CHECKING OPTIMIZATION ===

	/**
	 * Count support for all candidates using bitmap-based subset checking.
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
	// ===========================================================

	/**
	 * Run the AprioriInverse-FAST algorithm
	 * 
	 * @param minsup                 the minimum support threshold
	 * @param maxsup                 the maximum support threshold
	 * @param input                  path to the input file
	 * @param output                 path to save the result to an output file,
	 *                               or null to save results to memory
	 * @param hash_tree_branch_count the number of child nodes for each node in the
	 *                               hash tree
	 * @return the sporadic itemsets found (Itemsets object), or null if saving to file
	 * @throws IOException if an error while reading/writing files
	 */
	public Itemsets runAlgorithm(double minsup, double maxsup, String input, String output, int hash_tree_branch_count)
			throws IOException {
		// record start time
		startTimestamp = System.currentTimeMillis();

		// === NEW: Check if we save to file or memory ===
		if (output == null) {
			// If output is null, we will save to memory
			writer = null;
			patterns = new Itemsets("SPORADIC ITEMSETS");
		} else {
			// prepare object for writing the file
			writer = new BufferedWriter(new FileWriter(output));
			patterns = null;
		}

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
			
			// add transaction to raw transactions (will be recoded later)
			rawTransactions.add(transaction);
			
			// increase the transaction count
			transactionCount++;
		}
		reader.close();

		// Save database size
		databaseSize = transactionCount;

		// convert absolute minimum support to a relative minimum support
		// by multiplying minsup by the database size
		this.minsupRelative = (int) Math.ceil(minsup * transactionCount);
		this.maxsupRelative = (int) Math.ceil(maxsup * transactionCount);

		if (DEBUG_MODE) {
			System.out.println("database size = " + transactionCount + " minsuprel = " + minsupRelative 
					+ " maxsuprel = " + maxsupRelative);
		}
		
		// Apriori will start by generating itemsets of size 1
		k = 1;

		// === KEY DIFFERENCE FROM APRIORI ===
		// For AprioriInverse, we keep items with minsup <= support < maxsup (sporadic items)
		List<Map.Entry<Integer, Integer>> sporadicItemsList = new ArrayList<Map.Entry<Integer, Integer>>();
		for (Entry<Integer, Integer> entry : mapItemCount.entrySet()) {
			if (entry.getValue() >= minsupRelative && entry.getValue() < maxsupRelative) {
				sporadicItemsList.add(entry);
			}
		}
		// ==================================
		// Free memory
		mapItemCount = null;

		// Sort items by support ascending order
		Collections.sort(sporadicItemsList, new Comparator<Map.Entry<Integer, Integer>>() {
			public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
				return o1.getValue() - o2.getValue();
			}
		});

		// Save the number of sporadic items
		sporadicItemCount = sporadicItemsList.size();

		// If no sporadic items, stop here!
		if (sporadicItemCount == 0) {
			// Save end time and memory
			endTimestamp = System.currentTimeMillis();
			MemoryLogger.getInstance().checkMemory();
			// Close output file
			if (writer != null) {
				writer.close();
			}
			return patterns;
		}

		// Now based on the ascending order, we will rename the frequent items such
		// that the first item will be called 0, the second one will be called 1, and so
		// on.
		// This is done using a class called the NameConverter which remember the old
		// item names and the new item names.
		// Renaming items is useful because it simplifies the comparison of items by
		// Apriori.
		nameConverter = new ItemNameConverter(sporadicItemCount, 0);
		for (Map.Entry<Integer, Integer> entry : sporadicItemsList) {
			nameConverter.assignNewName(entry.getKey());
		}

		// Create the list of sporadic items using new names
		List<Integer> sporadic1 = new ArrayList<Integer>(sporadicItemCount);
		for (int i = 0; i < sporadicItemCount; i++) {
			sporadic1.add(i);
		}

		// Save sporadic items of size 1 to output (using original names)
		for (Map.Entry<Integer, Integer> entry : sporadicItemsList) {
			saveItemsetToFile(entry.getKey(), entry.getValue());
		}
		
		// Check maxPatternLength after saving 1-itemsets
		// If maxPatternLength is 1, we only want 1-itemsets, so stop here
		if (maxPatternLength <= 1) {
			endTimestamp = System.currentTimeMillis();
			MemoryLogger.getInstance().checkMemory();
			if (writer != null) {
				writer.close();
			}
			return patterns;
		}

		// Check if bitmap optimization should be used
		if (sporadicItemCount > MAX_ITEMS_FOR_BITMAP) {
			useBitmapOptimization = false;
		} else {
			useBitmapOptimization = true;
		}

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
			// Only add non-empty transactions
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
			List<int[]> recodedTransactions = new ArrayList<int[]>(rawTransactions.size());
			for (int[] rawTransaction : rawTransactions) {
				
				// Count the number of items
				int count = 0;
				for (int item : rawTransaction) {
					if (nameConverter.isOldItemExisting(item)) {
						count++;
					}
				}

				// If there is at least one item in the transaction
				if (count > 0) {
					// Create array of the correct size
					int[] items = new int[count];
					// Copy the items
					int index = 0;
					for (int item : rawTransaction) {
						if (nameConverter.isOldItemExisting(item)) {
							items[index++] = nameConverter.toNewName(item);
						}
					}
					// Sort the transactions by the new name order
					Arrays.sort(items);
					recodedTransactions.add(items);
				}
			}

			// Free memory
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
				int[] currentTrans = recodedTransactions.get(0);
				int currentWeight = 1;

				// Loop over next transactions to find identical transactions.
				// Since transactions are sorted, identical ones are consecutive.
				// We only need to compare with the immediate predecessor.
				for (int i = 1; i < recodedTransactions.size(); i++) {
					int[] nextTrans = recodedTransactions.get(i);

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

		// Initialize projection buffer ===
		projectionBuffer = new int[sporadicItemCount];
		totalCandidateCount += sporadic1.size();

		// Now, the algorithm recursively generates frequent itemsets of size K
		// by using frequent itemsets of size K-1 until no more
		// candidates can be generated.
		k = 2;

		// Save the number of itemsets of size 1
		int previousItemsetCount = itemsetCount;
		int previousActiveItemCount = sporadic1.size();

		// Create an hashtree for storing candidates for efficient support counting
		ItemsetHashTree candidatesK = null;
		do {
			// Check memory usage
			MemoryLogger.getInstance().checkMemory();

			// Generate candidates of size K
			if (k == 2) {
				// If it is the 2-itemsets
				candidatesK = generateCandidate2(sporadic1);
			} else {
				// Otherwise
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
			} else {
				// Use hash-tree based counting
				// This is done efficiently because the candidates are stored in a hash-tree.
				int dbSize = database.size();
				for (int t = 0; t < dbSize; t++) {
					int[] transaction = database.get(t);
					if (transaction.length >= k) {
						candidatesK.updateSupportCount(transaction, transactionWeights[t]);
					}
				}
			}

			// We next save to file all the candidates that have a support
			// higher than the minsup threshold and remove those who does not.

			// Create array to track which items appear in frequent k-itemsets
			boolean[] activeItems = new boolean[sporadicItemCount];
			int activeItemCount = 0;

			// === OPTIMIZATION ===
			// The algorithm will identify the "active" itemsets, that is those
			// that appears in the current frequent itemsets.
			// For each leaf of the hash tree:
			for (LeafNode node = candidatesK.lastInsertedNode; node != null; node = node.nextLeafNode) {
				// for each list of candidate itemsets stored in that node
				for (List<Itemset> listCandidate : node.candidates) {
					if (listCandidate != null) {
						// Use iterator for efficient removal
						Iterator<Itemset> iter = listCandidate.iterator();
						while (iter.hasNext()) {
							Itemset candidate = iter.next();
							// For AprioriInverse: only need to check minsup
							// (maxsup is implicitly satisfied since all subsets are sporadic)
							if (candidate.getAbsoluteSupport() >= minsupRelative) {
								saveItemsetToFileWithConversion(candidate);

								// === OPTIMIZATION: Mark items as active ===
								for (int item : candidate.itemset) {
									if (!activeItems[item]) {
										activeItems[item] = true;
										activeItemCount++;
									}
								}
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
		} while (previousItemsetCount != itemsetCount && k <= maxPatternLength);

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
			for (int item = 0; item < sporadicItemCount; item++) {
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
				// Count remaining items using bitCount in O(1)
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

			List<Long> uniqueBitmaps = new ArrayList<>(projectedCount);
			List<Integer> newWeights = new ArrayList<>(projectedCount);


			// Merge identical bitmaps using sorted indices - O(1) equality check
			long currentBitmap = projectedBitmaps.get(indices[0]);
			int currentWeight = projectedWeightsList.get(indices[0]);

			for (int i = 1; i < projectedCount; i++) {
				long nextBitmap = projectedBitmaps.get(indices[i]);
				int nextWeight = projectedWeightsList.get(indices[i]);

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
					if (k != itemset1.length - 1) {
						if (itemset2[k] > itemset1[k]) {
							continue loop1;
						}
						if (itemset1[k] > itemset2[k]) {
							continue loop2;
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

				// Create a new candidate by combining itemset1 and itemset2
				if (itemset2[itemset2.length - 1] < itemset1[itemset1.length - 1]) {
					System.arraycopy(itemset2, 0, newItemset, 0, itemset2.length);
					newItemset[itemset1.length] = itemset1[itemset1.length - 1];
				} else {
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
	private ItemsetHashTree generateCandidate2(List<Integer> sporadic1) {
		// we create an hash-tree to store the candidates
		ItemsetHashTree candidates = new ItemsetHashTree(2, hash_tree_branch_count);

		// For each pair of frequent items
		for (int i = 0; i < sporadic1.size(); i++) {
			Integer item1 = sporadic1.get(i);
			for (int j = i + 1; j < sporadic1.size(); j++) {
				Integer item2 = sporadic1.get(j);
				candidates.insertCandidateItemset(new Itemset(new int[] { item1, item2 }));
			}
		}

		// BUG FIX: Sort buckets for correct binary search when checking subsets
		// of size-3 candidates in the next level
		candidates.sortAllBuckets();
		
		// Return the hash tree
		return candidates;
	}

	/**
	 * Check if all subsets of size k-1 are frequent
	 */
	protected boolean allSubsetsOfSizeK_1AreFrequent(int[] itemset, ItemsetHashTree hashtreeCandidatesK_1) {
		for (int posRemoved = 0; posRemoved < itemset.length; posRemoved++) {
			if (hashtreeCandidatesK_1.isInTheTree(itemset, posRemoved) == false) {
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

		for (int i = 0; i < length; i++) {
			projectionBuffer[i] = nameConverter.toOldName(itemset.itemset[i]);
		}

		Arrays.sort(projectionBuffer, 0, length);

		if (writer != null) {
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
			int[] convertedItemset = new int[length];
			System.arraycopy(projectionBuffer, 0, convertedItemset, 0, length);
			Itemset newItemset = new Itemset(convertedItemset);
			newItemset.setAbsoluteSupport(itemset.getAbsoluteSupport());
			patterns.addItemset(newItemset, length);
		}
		itemsetCount++;
	}

	/**
	 * Save size-1 itemset
	 * @param item    the item contained in the itemset.
	 * @param support the support of the item.
	 */
	void saveItemsetToFile(Integer item, Integer support) throws IOException {
		if (writer != null) {
			writer.write(item + " #SUP: " + support);
			writer.newLine();
		} else {
			int[] itemsetArray = new int[] { item };
			Itemset newItemset = new Itemset(itemsetArray);
			newItemset.setAbsoluteSupport(support);
			patterns.addItemset(newItemset, 1);
		}
		itemsetCount++;
	}

	/**
	 * Print statistics about the algorithm execution
	 */
	public void printStats() {
		System.out.println("============= APRIORI-INVERSE-FAST 2.65 - STATS =============");
		System.out.println(" Sporadic itemsets count : " + itemsetCount);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		if (DEBUG_MODE) {
			System.out.println(" Candidates count : " + totalCandidateCount);
			System.out.println(" The algorithm stopped at size " + (k - 1));
			System.out.println(" Bitmap optimization: " + (useBitmapOptimization ? "enabled" : "disabled"));
		}
		System.out.println("===================================================");
	}

	/**
	 * Set the maximum pattern length
	 */
	public void setMaximumPatternLength(int length) {
		if(length < 1) {
			throw new InvalidParameterException("Maximum length must be at least 1");
		}
		maxPatternLength = length;
	}

	/**
	 * Get the database size
	 */
	public int getDatabaseSize() {
		return databaseSize;
	}
}