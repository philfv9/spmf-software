package ca.pfv.spmf.algorithms.frequentpatterns.tkn;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2021-2026 Mohamed Ashraf et al.
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
* 
* Do not remove copyright and author information.
*/

/**
 * This is the original implementation of the TKN algorithm for discovering the top-k high
 * utility itemsets in a transaction database where items may have positive or
 * negative unit profits. More information on the TKN algorithm can be found in
 * the paper: 
 * 
 *<br/><br/>
 * Ashraf, M., Abdelkader, T., Rady, S., Gharib, T. F.: TKN: An efficient
 * approach for discovering top-k high utility itemsets with positive or
 * negative profits. Information Sciences 587: 654-678 (2022). 
 * https://doi.org/10.1016/j.ins.2021.12.024
 *<br/><br/>
 *
 * @see Dataset
 * @see Transaction
 * @see ItemsetTKN
 * @author Mohamed Ashraf
 */
public class AlgoTKN {

	/** the top-k itemsets found until now (the head is the itemset with the lowest utility) */
	private PriorityQueue<ItemsetTKN> kItemsets;

	/** the number of itemsets to be found */
	private int k;

	/**
	 * the internal minimum utility threshold. It starts at 1 and it is raised by
	 * the threshold raising strategies and when itemsets are found.
	 */
	private long minUtil;

	/** the time when the last execution started */
	private long startTimestamp;

	/** the time when the last execution ended */
	private long endTimestamp;

	/** the number of itemsets returned by the last execution */
	private int itemsetCount;

	/** the minimum utility threshold after applying the PRIU strategy */
	private long minUtilAfterPRIU;

	/** the minimum utility threshold after applying the PLIU-E strategy */
	private long minUtilAfterPLIUE;

	/** the minimum utility threshold after applying the PLIU-LB strategy */
	private long minUtilAfterPLIULB;

	/** the number of itemsets from the search tree that were considered */
	private long candidateCount;

	/** the number of times that the EP strategy stopped building a projected database */
	private long epPrunedCount;

	/** the number of times that the EA strategy abandoned the exploration of a sub-tree */
	private long eaAbandonedCount;

	/** utility-bin array for calculating the positive sub-tree utility (PSU) of items */
	private long[] utilityBinArrayPSU;

	/**
	 * utility-bin array for calculating the positive local utility (PLU) of items.
	 * Before the search, it is used to store the Ptwu of each item (which is the PLU
	 * of the item with respect to the empty set).
	 */
	private long[] utilityBinArrayPLU;

	/** the utility of each item in the database (indexed by the old name of the item) */
	private long[] realItemUtility;

	/**
	 * The leaf itemset utility structure (LIUS). For an item y,
	 * leafItemsetUtility[y][d] is the utility of the itemset containing the
	 * positive items y-d-1, y-d, ..., y. It is calculated using the
	 * transactions where these items appear contiguously, which are all the
	 * transactions containing that itemset.
	 */
	private long[][] leafItemsetUtility;

	/**
	 * a priority queue of at most k values, used by the threshold raising
	 * strategies PRIU, PLIU-E and PLIU-LB. Each value is the utility (or a lower
	 * bound on the utility) of a different itemset. Thus, minUtil can be safely raised to
	 * the k-th largest value.
	 */
	private PriorityQueue<Long> raisingQueue;

	/** an array that maps an old item name to its new name */
	private int[] oldNameToNewNames;

	/** an array that maps a new item name to its old name */
	private int[] newNamesToOldNames;

	/** the number of promising items */
	private int newItemCount;

	/**
	 * the new name of the first negative item. Since negative items come after
	 * positive items in the total order imposed, an item is negative if and only if its new
	 * name is no less than this value.
	 */
	private int firstNegativeItem;

	/** a buffer to store the items (old names) of the current itemset */
	private int[] itemsetBuffer;

	/** A parameter for transaction merging */
	private static final int MAXIMUM_SIZE_MERGING = 1000;

	/** if true, transaction merging is performed */
	private boolean useTransactionMerging;

	/** if true, the PSU pruning strategy is used to select the primary items */
	private boolean usePSU;

	/** if true, the PRIU threshold raising strategy is used */
	private boolean usePRIU = true;

	/** if true, the PLIU-E threshold raising strategy is used */
	private boolean usePLIUE = true;

	/** if true, the PLIU-LB threshold raising strategy is used */
	private boolean usePLIULB = true;

	/** the maximum number of items that the PLIU-LB strategy removes from an itemset of the LIUS */
	private int maxRemovedItems = 4;

	/** if true, the EP (early pruning) strategy is used */
	private boolean useEP = true;

	/** if true, the EA (early abandoning) strategy is used */
	private boolean useEA = true;

	/**
	 * Constructor
	 */
	public AlgoTKN() {

	}

	/**
	 * Run the algorithm (with transaction merging and PSU pruning)
	 *
	 * @param inputPath  the input file path
	 * @param outputPath the output file path to save the result or null if the
	 *                   result should be kept in memory
	 * @param k          the number of itemsets to be found (at least 1)
	 * @return the top-k itemsets sorted by decreasing utility, or null if the user
	 *         chose to save them to a file
	 * @throws IOException if an error occurs while reading or writing a file
	 */
	public List<ItemsetTKN> runAlgorithm(String inputPath, String outputPath, int k) throws IOException {
		return runAlgorithm(inputPath, outputPath, k, true, Integer.MAX_VALUE, true);
	}

	/**
	 * Run the algorithm
	 *
	 * @param inputPath               the input file path
	 * @param outputPath              the output file path to save the result or
	 *                                null if the result should be kept in memory
	 * @param k                       the number of itemsets to be found (at least
	 *                                1)
	 * @param useTransactionMerging   if true, transaction merging is performed
	 * @param maximumTransactionCount the maximum number of transactions to be read
	 *                                from the input file (Integer.MAX_VALUE to read
	 *                                all of them)
	 * @param usePSU                  if true, the PSU pruning strategy is used
	 * @return the top-k itemsets sorted by decreasing utility, or null if the user
	 *         chose to save them to a file
	 * @throws IOException if an error occurs while reading or writing a file
	 */
	public List<ItemsetTKN> runAlgorithm(String inputPath, String outputPath, int k, boolean useTransactionMerging,
			int maximumTransactionCount, boolean usePSU) throws IOException {

		// check the parameter k
		if (k < 1) {
			throw new IllegalArgumentException("The parameter k must be at least 1.");
		}

		// reset the memory usage checking utility and record the start time
		MemoryLogger.getInstance().reset();
		startTimestamp = System.currentTimeMillis();

		// save the parameters
		this.k = k;
		this.useTransactionMerging = useTransactionMerging;
		this.usePSU = usePSU;

		// reset the variables of the previous execution.
		minUtil = 1;
		kItemsets = new PriorityQueue<ItemsetTKN>();
		raisingQueue = new PriorityQueue<Long>();
		candidateCount = 0;
		epPrunedCount = 0;
		eaAbandonedCount = 0;

		// read the input file
		Dataset dataset = new Dataset(inputPath, maximumTransactionCount);

		// if the user chose to save the result to a file, we create the output file now,
		// so that an invalid path is detected before mining
		if (outputPath != null) {
			new FileWriter(outputPath).close();
		}

		// Scan the database to calculate the Ptwu and the utility of each item,
		// and to find the negative items
		final boolean[] isNegativeItem = calculatePtwuAndItemUtilities(dataset);

		// Raise the threshold using the utilities of the single items (PRIU)
		if (usePRIU) {
			raiseThresholdPRIU();
		}
		minUtilAfterPRIU = minUtil;

		// Now, we keep only the promising items (those having a Ptwu >= minUtil)
		List<Integer> promisingItems = new ArrayList<Integer>();
		for (int item = 1; item <= dataset.getMaxItem(); item++) {
			if (utilityBinArrayPLU[item] >= minUtil) {
				promisingItems.add(item);
			}
		}

		// Sort the promising items according to the total order: positive
		// items by increasing Ptwu, followed by negative items by increasing Ptwu.
		// If two items have the same Ptwu, the item having the smallest name is first.
		Collections.sort(promisingItems, new Comparator<Integer>() {
			public int compare(Integer item1, Integer item2) {
				// positive items are before negative items
				if (isNegativeItem[item1] != isNegativeItem[item2]) {
					return isNegativeItem[item1] ? 1 : -1;
				}
				// then, we compare the Ptwu of the two items
				int comparison = Long.compare(utilityBinArrayPLU[item1], utilityBinArrayPLU[item2]);
				// if the Ptwu is equal, we compare the item names
				return (comparison != 0) ? comparison : item1 - item2;
			}
		});

		// Rename the promising items according to the total order, starting from the
		// name "1". This allows very fast comparison between items during the search.
		oldNameToNewNames = new int[dataset.getMaxItem() + 1];
		newItemCount = promisingItems.size();
		newNamesToOldNames = new int[newItemCount + 1];
		firstNegativeItem = newItemCount + 1;
		for (int j = 0; j < newItemCount; j++) {
			// get the item old name
			int item = promisingItems.get(j);
			int newName = j + 1;
			// give it the new name and remember its old name
			oldNameToNewNames[item] = newName;
			newNamesToOldNames[newName] = item;
			// remember the new name of the first negative item
			if (isNegativeItem[item] && firstNegativeItem > newItemCount) {
				firstNegativeItem = newName;
			}
		}

		// Remove unpromising items from each transaction, rename the other items and
		// sort them according to the total order. Empty transactions are not kept.
		List<Transaction> transactions = new ArrayList<Transaction>();
		for (Transaction transaction : dataset.getTransactions()) {
			transaction.removeUnpromisingItems(oldNameToNewNames);
			if (transaction.items.length > 0) {
				transactions.add(transaction);
			}
		}

		// If transaction merging is used, we sort the transactions according to the
		// lexicographical order when transactions are read backward, so that
		// identical projected transactions will be consecutive.
		if (useTransactionMerging) {
			Collections.sort(transactions, new Comparator<Transaction>() {
				public int compare(Transaction t1, Transaction t2) {
					// we will compare the two transaction items by items starting
					// from the last items.
					int pos1 = t1.items.length - 1;
					int pos2 = t2.items.length - 1;

					// if the first transaction is smaller than the second one
					if (t1.items.length < t2.items.length) {
						// while the current position in the first transaction is >0
						while (pos1 >= 0) {
							int subtraction = t2.items[pos2] - t1.items[pos1];
							if (subtraction != 0) {
								return subtraction;
							}
							pos1--;
							pos2--;
						}
						// if they were the same, then we compare based on length
						return -1;

						// else if the second transaction is smaller than the first one
					} else if (t1.items.length > t2.items.length) {
						// while the current position in the second transaction is >0
						while (pos2 >= 0) {
							int subtraction = t2.items[pos2] - t1.items[pos1];
							if (subtraction != 0) {
								return subtraction;
							}
							pos1--;
							pos2--;
						}
						// if they were the same, then we compare based on length
						return 1;

					} else {
						// else if both transactions have the same size
						while (pos2 >= 0) {
							int subtraction = t2.items[pos2] - t1.items[pos1];
							if (subtraction != 0) {
								return subtraction;
							}
							pos1--;
							pos2--;
						}
						// if they were the same, they are equal
						return 0;
					}
				}
			});
		}

		// Scan the database to calculate the PSU of each item with respect to the
		// empty set, and to build the LIUS if PLIU-E or PLIU-LB is used
		utilityBinArrayPSU = new long[newItemCount + 1];
		leafItemsetUtility = (usePLIUE || usePLIULB) ? new long[newItemCount + 1][] : null;
		calculatePSUAndLIUS(transactions);

		// Raise the threshold using the exact utilities stored in the LIUS (PLIU-E)
		if (usePLIUE) {
			raiseThresholdPLIUE();
		}
		minUtilAfterPLIUE = minUtil;

		// Raise the threshold using lower bounds calculated from the LIUS (PLIU-LB)
		if (usePLIULB) {
			raiseThresholdPLIULB();
		}
		minUtilAfterPLIULB = minUtil;

		// the structures used by the threshold raising strategies are not needed anymore
		leafItemsetUtility = null;
		raisingQueue = null;
		realItemUtility = null;

		// At the root of the search tree, all the promising items are secondary items.
		// The primary items are those having a PSU no less than minUtil.
		int[] secondaryItems = new int[newItemCount];
		for (int j = 0; j < newItemCount; j++) {
			secondaryItems[j] = j + 1;
		}
		int[] primaryItems = secondaryItems;
		if (usePSU) {
			primaryItems = new int[newItemCount];
			int primaryCount = 0;
			for (int item : secondaryItems) {
				if (utilityBinArrayPSU[item] >= minUtil) {
					primaryItems[primaryCount++] = item;
				}
			}
			primaryItems = Arrays.copyOf(primaryItems, primaryCount);
		}

		// Mine the database recursively using the global search procedure
		itemsetBuffer = new int[newItemCount];
		search(0, transactions, secondaryItems, primaryItems, 0);

		// Sort the top-k itemsets by decreasing utility
		List<ItemsetTKN> topKItemsets = new ArrayList<ItemsetTKN>(kItemsets);
		Collections.sort(topKItemsets, Collections.reverseOrder());
		itemsetCount = topKItemsets.size();

		// if the user chose to save the result to a file, write the file
		if (outputPath != null) {
			writeResultToFile(topKItemsets, outputPath);
		}

		// record the end time
		endTimestamp = System.currentTimeMillis();

		// check the maximum memory usage
		MemoryLogger.getInstance().checkMemory();

		// return the itemsets if the user chose to keep them in memory
		return (outputPath == null) ? topKItemsets : null;
	}

	/**
	 * Scan the initial database to calculate the Ptwu of each item (using the
	 * utility-bin array for PLU) and the utility of each item, and to find the
	 * negative items. An exception is thrown if an item has both positive and
	 * negative utilities.
	 *
	 * @param dataset the transaction database
	 * @return an array indicating for each item (old name) if it is a negative item
	 */
	private boolean[] calculatePtwuAndItemUtilities(Dataset dataset) {
		// Initialize the utility bins and the other arrays for all items
		int maxItem = dataset.getMaxItem();
		utilityBinArrayPLU = new long[maxItem + 1];
		realItemUtility = new long[maxItem + 1];
		boolean[] isNegativeItem = new boolean[maxItem + 1];
		boolean[] isPositiveItem = new boolean[maxItem + 1];

		// For each transaction
		for (Transaction transaction : dataset.getTransactions()) {
			// for each item
			for (int i = 0; i < transaction.items.length; i++) {
				int item = transaction.items[i];
				int utility = transaction.utilities[i];

				// we add the positive transaction utility to the Ptwu of the item
				utilityBinArrayPLU[item] += transaction.transactionUtility;
				// we add the utility of the item in this transaction to its utility
				realItemUtility[item] += utility;

				// we remember the sign of the item
				if (utility > 0) {
					isPositiveItem[item] = true;
				} else if (utility < 0) {
					isNegativeItem[item] = true;
				}
				// an item cannot have both positive and negative utilities (unstable profit)
				if (isPositiveItem[item] && isNegativeItem[item]) {
					throw new IllegalArgumentException("TKN requires that each item has either only positive or only "
							+ "negative utilities, but the item " + item + " has both.");
				}
			}
		}
		return isNegativeItem;
	}

	/**
	 * Raise the minimum utility threshold using the PRIU strategy. The utility of
	 * each positive item is the utility of a different itemset (containing only
	 * that item). Hence, minUtil can be safely raised to the k-th largest of these
	 * utilities.
	 */
	private void raiseThresholdPRIU() {
		// for each item
		for (int item = 1; item < realItemUtility.length; item++) {
			// add its utility to the queue
			// (the utility of a negative item is negative, so it is not added)
			addToRaisingQueue(realItemUtility[item]);
		}
		// raise the threshold
		raiseThresholdUsingQueue();
	}

	/**
	 * Raise the minimum utility threshold using the PLIU-E strategy. Each value
	 * stored in the LIUS is the exact utility of a different itemset.
	 */
	private void raiseThresholdPLIUE() {
		// for each item that can be the last item of an itemset of the LIUS
		for (int lastItem = 1; lastItem <= newItemCount; lastItem++) {
			long[] utilitiesOfItemsets = leafItemsetUtility[lastItem];
			if (utilitiesOfItemsets != null) {
				// add the utility of each itemset ending with this item to the queue
				for (long utility : utilitiesOfItemsets) {
					addToRaisingQueue(utility);
				}
			}
		}
		// raise the threshold
		raiseThresholdUsingQueue();
	}

	/**
	 * Raise the minimum utility threshold using the PLIU-LB strategy. If some of
	 * the items between the first and the last item of an itemset of the LIUS are
	 * removed, a different itemset is obtained, and the utility of the itemset of
	 * the LIUS minus the utilities of the removed items is a lower bound on its
	 * utility. At most maxRemovedItems items are removed.
	 */
	private void raiseThresholdPLIULB() {
		// for each item that can be the last item of an itemset of the LIUS
		for (int lastItem = 1; lastItem <= newItemCount; lastItem++) {
			long[] utilitiesOfItemsets = leafItemsetUtility[lastItem];
			if (utilitiesOfItemsets == null) {
				continue;
			}
			// for each itemset of at least three items ending with this item
			for (int d = 1; d < utilitiesOfItemsets.length; d++) {
				// Removing items can only decrease the lower bound. Thus, the itemset is
				// used only if its utility is large enough to raise the threshold.
				if (utilitiesOfItemsets[d] > getRaisingFloor()) {
					int firstItem = lastItem - d - 1;
					removeItemsFromLeafItemset(utilitiesOfItemsets[d], firstItem + 1, lastItem - 1, 0);
				}
			}
		}
		// raise the threshold
		raiseThresholdUsingQueue();
	}

	/**
	 * Calculate the lower bounds of the itemsets obtained by removing items from an
	 * itemset of the LIUS, and add them to the queue. This method removes one more
	 * item (the items are removed in increasing order) and calls itself to remove
	 * the next items recursively. Since the utility of a positive item is never negative,
	 * removing more items cannot increase the lower bound. Hence, a branch is
	 * stopped as soon as its lower bound is too small to raise the threshold.
	 *
	 * @param lowerBound   the lower bound of the current itemset
	 * @param firstItem    the first item that can be removed
	 * @param lastItem     the last item that can be removed
	 * @param removedCount the number of items removed until now
	 */
	private void removeItemsFromLeafItemset(long lowerBound, int firstItem, int lastItem, int removedCount) {
		// for each item that can be removed
		for (int item = firstItem; item <= lastItem; item++) {
			// calculate the lower bound of the itemset without this item
			long newLowerBound = lowerBound - realItemUtility[newNamesToOldNames[item]];
			// if this lower bound can raise the threshold
			if (newLowerBound > getRaisingFloor()) {
				// add it to the queue
				addToRaisingQueue(newLowerBound);
				// and try to remove more items
				if (removedCount + 1 < maxRemovedItems) {
					removeItemsFromLeafItemset(newLowerBound, item + 1, lastItem, removedCount + 1);
				}
			}
		}
	}

	/**
	 * Add a value to the queue of the threshold raising strategies, if this value
	 * can raise the threshold. The queue keeps at most the k largest values.
	 *
	 * @param value the utility (or a lower bound on the utility) of an itemset
	 */
	private void addToRaisingQueue(long value) {
		if (value > getRaisingFloor()) {
			raisingQueue.add(value);
			// keep only the k largest values
			if (raisingQueue.size() > k) {
				raisingQueue.poll();
			}
		}
	}

	/**
	 * Get the value that a new value must exceed to be useful for raising the
	 * threshold. It is the largest value among minUtil and the k-th largest value
	 * in the queue (if the queue contains k values).
	 *
	 * @return the value
	 */
	private long getRaisingFloor() {
		if (raisingQueue.size() == k && raisingQueue.peek() > minUtil) {
			return raisingQueue.peek();
		}
		return minUtil;
	}

	/**
	 * Raise minUtil to the k-th largest value in the queue of the threshold raising
	 * strategies, if the queue contains k values.
	 */
	private void raiseThresholdUsingQueue() {
		if (raisingQueue.size() == k && raisingQueue.peek() > minUtil) {
			minUtil = raisingQueue.peek();
		}
	}

	/**
	 * Scan the database (after removing the unpromising items) to calculate the
	 * PSU of each item with respect to the empty set using a utility-bin array. If
	 * the LIUS is used, it is built at the same time.
	 *
	 * @param transactions the transactions of the database
	 */
	private void calculatePSUAndLIUS(List<Transaction> transactions) {
		// For each transaction
		for (Transaction transaction : transactions) {
			// We will scan the transaction backward. The variable sumSU is the sum of
			// the utilities of the positive items from the current position to the
			// end of the transaction.
			long sumSU = 0;

			// For each item when reading the transaction backward
			for (int i = transaction.items.length - 1; i >= 0; i--) {
				// get the item
				int item = transaction.items[i];

				// if it is a positive item
				if (item < firstNegativeItem) {
					// we add its utility to the sub-tree utility
					sumSU += transaction.utilities[i];
					// and we update the LIUS with the itemsets ending with this item
					if (leafItemsetUtility != null) {
						updateLIUS(transaction, i);
					}
				}
				// we add the current sub-tree utility to the utility-bin of the item.
				// For a negative item, this value is 0 since only negative items follow it.
				utilityBinArrayPSU[item] += sumSU;
			}
		}
	}

	/**
	 * Update the LIUS using the contiguous sequences of positive items that end at
	 * a given position of a transaction. Since items are renamed according to the
	 * total order, a sequence of items is contiguous if the new names of its items
	 * are consecutive integers.
	 *
	 * @param transaction a transaction
	 * @param position    the position of a positive item in the transaction
	 */
	private void updateLIUS(Transaction transaction, int position) {
		int[] items = transaction.items;
		int[] utilities = transaction.utilities;

		// find the first position of the longest contiguous sequence ending at this
		// position
		int start = position;
		while (start > 0 && items[start - 1] == items[start] - 1) {
			start--;
		}

		// the number of sequences of at least two items ending at this position
		int sequenceCount = position - start;
		if (sequenceCount == 0) {
			return;
		}

		// get the utilities of the itemsets ending with this item in the LIUS, and
		// enlarge the array if a longer sequence was found
		int lastItem = items[position];
		long[] utilitiesOfItemsets = leafItemsetUtility[lastItem];
		if (utilitiesOfItemsets == null) {
			utilitiesOfItemsets = new long[sequenceCount];
			leafItemsetUtility[lastItem] = utilitiesOfItemsets;
		} else if (utilitiesOfItemsets.length < sequenceCount) {
			utilitiesOfItemsets = Arrays.copyOf(utilitiesOfItemsets, sequenceCount);
			leafItemsetUtility[lastItem] = utilitiesOfItemsets;
		}

		// add the utility of each sequence in this transaction to the LIUS
		long utility = utilities[position];
		for (int j = position - 1; j >= start; j--) {
			utility += utilities[j];
			utilitiesOfItemsets[position - 1 - j] += utility;
		}
	}

	/**
	 * Recursive method to find the top-k high utility itemsets (the global search
	 * procedure). Each primary item e of the current itemset P is used to explore
	 * the itemset P U {e} and its sub-tree in depth-first fashion.
	 *
	 * @param positivePrefixUtility the sum of the utilities of P in the transactions
	 *                              of its projected database, where each negative
	 *                              utility is replaced by 0
	 * @param transactionsOfP       the projected database of P
	 * @param secondaryItems        the secondary items of P (sorted by the total
	 *                              order)
	 * @param primaryItems          the primary items of P (sorted by the total
	 *                              order)
	 * @param prefixLength          the number of items in P
	 */
	private void search(long positivePrefixUtility, List<Transaction> transactionsOfP, int[] secondaryItems,
			int[] primaryItems, int prefixLength) {

		// update the number of candidates explored so far
		candidateCount += primaryItems.length;

		// ======== for each primary item e =============
		for (int j = 0; j < primaryItems.length; j++) {
			int e = primaryItems[j];

			// the position of e in the list of secondary items
			int positionOfE = Arrays.binarySearch(secondaryItems, e);

			// true if e is a negative item
			boolean eIsNegative = e >= firstNegativeItem;

			// ========== PERFORM INTERSECTION =====================
			// Calculate transactions containing P U {e}
			// At the same time project transactions to keep what appears after "e"
			List<Transaction> transactionsPe = new ArrayList<Transaction>();

			// variable to calculate the utility of P U {e}
			long utilityPe = 0;

			// EP strategy: if e is negative, all the items after e are also negative.
			// Thus, no itemset in the sub-tree of P U {e} can have a utility higher than
			// the positive prefix utility of P U {e}.
			long boundEP = positivePrefixUtility;
			boolean prunedByEP = false;

			// For merging transactions, we will keep track of the last transaction read
			// and the number of identical consecutive transactions
			Transaction previousTransaction = null;
			int consecutiveMergeCount = 0;

			// For each transaction
			for (Transaction transaction : transactionsOfP) {

				// we remember the position where e appears.
				// we will call this position an "offset"
				int positionE = -1;
				// Variables low and high for binary search
				int low = transaction.offset;
				int high = transaction.items.length - 1;

				// perform binary search to find e in the transaction
				while (high >= low) {
					int middle = (low + high) >>> 1; // divide by 2
					if (transaction.items[middle] < e) {
						low = middle + 1;
					} else if (transaction.items[middle] == e) {
						positionE = middle;
						break;
					} else {
						high = middle - 1;
					}
				}

				// if 'e' was found in the transaction
				if (positionE > -1) {

					// EP strategy
					if (eIsNegative) {
						boundEP -= Math.max(0, transaction.prefixUtility)
								-  Math.max(0, (long) transaction.prefixUtility + transaction.utilities[positionE]);
					}

					// optimization: if the 'e' is the last one in this transaction,
					// we don't keep the transaction
					if (transaction.getLastPosition() == positionE) {
						// but we still update the sum of the utility of P U {e}
						utilityPe += (long) transaction.utilities[positionE] + transaction.prefixUtility;
					} else {
						// otherwise
						if (useTransactionMerging && MAXIMUM_SIZE_MERGING >= (transaction.items.length - positionE)) {
							// we cut the transaction starting from position 'e'
							Transaction projectedTransaction = new Transaction(transaction, positionE);
							utilityPe += projectedTransaction.prefixUtility;

							// if it is the first transaction that we read
							if (previousTransaction == null) {
								// we keep the transaction in memory
								previousTransaction = projectedTransaction;
							} else if (isEqualTo(projectedTransaction, previousTransaction)) {
								// If it is not the first transaction of the database and
								// if the transaction is equal to the previously read transaction,
								// we will merge the transaction with the previous one

								// if the first consecutive merge
								if (consecutiveMergeCount == 0) {
									// copy items and their utilities from the previous transaction
									int itemsCount = previousTransaction.items.length - previousTransaction.offset;
									int[] items = new int[itemsCount];
									System.arraycopy(previousTransaction.items, previousTransaction.offset, items, 0,
											itemsCount);
									int[] utilities = new int[itemsCount];
									System.arraycopy(previousTransaction.utilities, previousTransaction.offset,
											utilities, 0, itemsCount);

									// make the sum of utilities from the previous transaction
									int positionPrevious = 0;
									int positionProjection = projectedTransaction.offset;
									while (positionPrevious < itemsCount) {
										utilities[positionPrevious] += projectedTransaction.utilities[positionProjection];
										positionPrevious++;
										positionProjection++;
									}

									// make the sum of prefix utilities
									int sumUtilities = previousTransaction.prefixUtility
											+ projectedTransaction.prefixUtility;

									// create the new transaction replacing the two merged transactions
									previousTransaction = new Transaction(items, utilities,
											previousTransaction.transactionUtility
													+ projectedTransaction.transactionUtility);
									previousTransaction.prefixUtility = sumUtilities;

								} else {
									// if not the first consecutive merge

									// add the utilities in the projected transaction to the previously
									// merged transaction
									int positionPrevious = 0;
									int positionProjected = projectedTransaction.offset;
									int itemsCount = previousTransaction.items.length;
									while (positionPrevious < itemsCount) {
										previousTransaction.utilities[positionPrevious] += projectedTransaction.utilities[positionProjected];
										positionPrevious++;
										positionProjected++;
									}

									// make also the sum of transaction utility and prefix utility
									previousTransaction.transactionUtility += projectedTransaction.transactionUtility;
									previousTransaction.prefixUtility += projectedTransaction.prefixUtility;
								}
								// increment the number of consecutive transactions merged
								consecutiveMergeCount++;
							} else {
								// if the transaction is not equal to the preceding transaction
								// we cannot merge it so we just add it to the database
								transactionsPe.add(previousTransaction);
								// the transaction becomes the previous transaction
								previousTransaction = projectedTransaction;
								// and we reset the number of consecutive transactions merged
								consecutiveMergeCount = 0;
							}
						} else {
							// Otherwise, if merging has been deactivated
							// then we just create the projected transaction
							Transaction projectedTransaction = new Transaction(transaction, positionE);
							// we add the utility of P U {e} in that transaction to the total utility of P U {e}
							utilityPe += projectedTransaction.prefixUtility;
							// we put the projected transaction in the projected database of P U {e}
							transactionsPe.add(projectedTransaction);
						}
					}
					// This is an optimization for binary search:
					// we remember the position of e so that for the next item, we will not search
					// before "e" in the transaction since items are visited in the total order
					transaction.offset = positionE;
				} else {
					// EP: this transaction does not contain P U {e}, so the utility of P in
					// this transaction (or 0 if it is negative) is removed from the bound
					if (eIsNegative) {
						boundEP -= Math.max(0, transaction.prefixUtility);
					}

					// This is an optimization for binary search:
					// we remember the position of e so that for the next item, we will not search
					// before "e" in the transaction since items are visited in the total order
					transaction.offset = low;
				}

				// EP: if the bound is lower than minUtil, we stop building the projected database
				if (eIsNegative && useEP && boundEP < minUtil) {
					prunedByEP = true;
					break;
				}
			}

			// if the EP strategy was applied, P U {e} and its sub-tree are not explored
			if (prunedByEP) {
				epPrunedCount++;
				continue;
			}

			// Add the last read transaction to the database if there is one
			if (previousTransaction != null) {
				transactionsPe.add(previousTransaction);
			}

			// Append item "e" to P to obtain P U {e}
			// but at the same time translate from new name of "e" to its old name
			itemsetBuffer[prefixLength] = newNamesToOldNames[e];

			// if the utility of P U {e} is no less than minUtil, we save it as one of the
			// top-k itemsets (this may raise minUtil, using the RUC strategy)
			if (utilityPe >= minUtil) {
				saveItemset(prefixLength, utilityPe);
			}

			// Calculate the positive prefix utility of P U {e}: the sum of its utilities
			// in the transactions of its projected database, where each negative
			// utility is replaced by 0
			long positivePrefixUtilityPe = 0;
			for (Transaction transaction : transactionsPe) {
				positivePrefixUtilityPe += Math.max(0, transaction.prefixUtility);
			}

			// EA strategy: if the next secondary item is negative, all the items that can
			// extend P U {e} are negative. Then, no itemset in the sub-tree of P U {e} can
			// have a utility higher than the positive prefix utility of P U {e}. If this
			// value is lower than minUtil, the sub-tree is not explored, and its upper
			// bounds are not calculated.
			if (useEA && positivePrefixUtilityPe < minUtil && positionOfE + 1 < secondaryItems.length
					&& secondaryItems[positionOfE + 1] >= firstNegativeItem) {
				eaAbandonedCount++;
				continue;
			}

			// ==== Next, we will calculate the PSU and PLU of
			// all items that could be appended to P U {e} ====
			computePSUandPLU(transactionsPe, positionOfE, secondaryItems);

			// We will create the new list of secondary items
			int maximumCount = secondaryItems.length - positionOfE - 1;
			int[] newSecondaryItems = new int[maximumCount];
			int secondaryCount = 0;
			// We will create the new list of primary items
			int[] newPrimaryItems = new int[maximumCount];
			int primaryCount = 0;

			// for each secondary item after e
			for (int i = positionOfE + 1; i < secondaryItems.length; i++) {
				int item = secondaryItems[i];

				// if the PSU is no less than minUtil
				if (utilityBinArrayPSU[item] >= minUtil) {
					// consider that item as a primary item and a secondary item
					newPrimaryItems[primaryCount++] = item;
					newSecondaryItems[secondaryCount++] = item;
				} else if (utilityBinArrayPLU[item] >= minUtil) {
					// otherwise, if the PLU is no less than minUtil,
					// consider this item to be a secondary item
					newSecondaryItems[secondaryCount++] = item;
				}
			}
			newSecondaryItems = Arrays.copyOf(newSecondaryItems, secondaryCount);

			// === recursive call to explore larger itemsets
			if (usePSU) {
				// if PSU pruning is used, we consider primary and secondary items
				newPrimaryItems = Arrays.copyOf(newPrimaryItems, primaryCount);
				search(positivePrefixUtilityPe, transactionsPe, newSecondaryItems, newPrimaryItems,
						prefixLength + 1);
			} else {
				// if PSU pruning is not used, the secondary items are also used
				// as primary items
				search(positivePrefixUtilityPe, transactionsPe, newSecondaryItems, newSecondaryItems,
						prefixLength + 1);
			}
		}

		// check the maximum memory usage for statistics purpose
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Check if two transactions are identical
	 *
	 * @param t1 the first transaction
	 * @param t2 the second transaction
	 * @return true if they are equal
	 */
	private boolean isEqualTo(Transaction t1, Transaction t2) {
		// we first compare the transaction lengths
		int length1 = t1.items.length - t1.offset;
		int length2 = t2.items.length - t2.offset;
		// if not same length, then transactions are not identical
		if (length1 != length2) {
			return false;
		}
		// if same length, we need to compare each element position by position,
		// to see if they are the same
		int position1 = t1.offset;
		int position2 = t2.offset;

		// for each position in the first transaction
		while (position1 < t1.items.length) {
			// if different from corresponding position in transaction 2
			// return false because they are not identical
			if (t1.items[position1] != t2.items[position2]) {
				return false;
			}
			// if the same, then move to next position
			position1++;
			position2++;
		}
		// if all items are identical, then return to true
		return true;
	}

	/**
	 * Use the utility-bin arrays to calculate the PSU and the PLU of all the
	 * secondary items that are after e according to the total order (the items
	 * that can extend P U {e}). In each transaction, the utility of P U {e} is used
	 * only if it is positive (otherwise, 0 is used), and only the utilities of
	 * positive items are added to the sub-tree utility.
	 *
	 * @param transactionsPe the projected database of P U {e}
	 * @param positionOfE    the position of e in the list of secondary items
	 * @param secondaryItems the list of secondary items of P
	 */
	private void computePSUandPLU(List<Transaction> transactionsPe, int positionOfE, int[] secondaryItems) {

		// For each secondary item after e according to the total order
		for (int i = positionOfE + 1; i < secondaryItems.length; i++) {
			int item = secondaryItems[i];
			// We reset the utility bins of that item for computing the PSU and PLU
			utilityBinArrayPSU[item] = 0;
			utilityBinArrayPLU[item] = 0;
		}

		// for each transaction
		for (Transaction transaction : transactionsPe) {

			// the utility of P U {e} in this transaction (0 if it is negative)
			long positivePrefixUtility = Math.max(0, transaction.prefixUtility);

			// We reset the sum of remaining utility to 0
			long sumRemainingUtility = 0;
			// we set high to the last secondary item for doing the binary search
			int high = secondaryItems.length - 1;

			// for each item in the transaction that is after e, when reading the
			// transaction backward
			for (int i = transaction.items.length - 1; i >= transaction.offset; i--) {
				// get the item
				int item = transaction.items[i];

				// We will check if this item is a secondary item using a binary search
				// over the secondary items that are after e.

				// This variable will be used as a flag to indicate that we found the item or
				// not using the binary search
				boolean contains = false;
				// we set "low" for the binary search to the first secondary item after e
				int low = positionOfE + 1;

				// do the binary search
				while (high >= low) {
					int middle = (low + high) >>> 1; // divide by 2
					int itemMiddle = secondaryItems[middle];
					if (itemMiddle == item) {
						// if we found the item, then we stop
						contains = true;
						break;
					} else if (itemMiddle < item) {
						low = middle + 1;
					} else {
						high = middle - 1;
					}
				}
				// if the item is a secondary item
				if (contains) {
					// if the item is positive, we add its utility to the sum of remaining utility
					if (item < firstNegativeItem) {
						sumRemainingUtility += transaction.utilities[i];
					}
					// We update the PSU of that item in its utility-bin
					utilityBinArrayPSU[item] += sumRemainingUtility + positivePrefixUtility;
					// We update the PLU of that item in its utility-bin
					utilityBinArrayPLU[item] += transaction.transactionUtility + positivePrefixUtility;
				}
			}
		}
	}

	/**
	 * Save an itemset as one of the top-k itemsets found until now. If k itemsets
	 * have been found, minUtil is raised to the utility of the k-th itemset (RUC
	 * strategy).
	 *
	 * @param prefixLength the position of the last item of the itemset in the
	 *                     itemset buffer
	 * @param utility      the utility of the itemset
	 */
	private void saveItemset(int prefixLength, long utility) {
		// copy the itemset from the buffer
		int[] items = new int[prefixLength + 1];
		System.arraycopy(itemsetBuffer, 0, items, 0, prefixLength + 1);

		// add the itemset to the top-k itemsets
		kItemsets.add(new ItemsetTKN(items, utility));
		// if there are more than k itemsets, we remove the itemset with the lowest utility
		if (kItemsets.size() > k) {
			kItemsets.poll();
		}
		// RUC: if k itemsets have been found, minUtil is raised to the utility of the
		// k-th itemset
		if (kItemsets.size() == k && kItemsets.peek().getUtility() > minUtil) {
			minUtil = kItemsets.peek().getUtility();
		}
	}

	/**
	 * Write the top-k itemsets to the output file. Each line contains the items of
	 * an itemset separated by spaces, followed by "#UTIL:" and its utility.
	 *
	 * @param itemsets   the itemsets
	 * @param outputPath the output file path
	 * @throws IOException if an error occurs while writing the file
	 */
	private void writeResultToFile(List<ItemsetTKN> itemsets, String outputPath) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
		for (ItemsetTKN itemset : itemsets) {
			writer.write(itemset.toString());
			writer.newLine();
		}
		writer.close();
	}

	/**
	 * Activate or deactivate the PRIU threshold raising strategy (activated by
	 * default).
	 *
	 * @param usePRIU true to use this strategy
	 */
	public void setUsePRIU(boolean usePRIU) {
		this.usePRIU = usePRIU;
	}

	/**
	 * Activate or deactivate the PLIU-E threshold raising strategy (activated by
	 * default).
	 *
	 * @param usePLIUE true to use this strategy
	 */
	public void setUsePLIUE(boolean usePLIUE) {
		this.usePLIUE = usePLIUE;
	}

	/**
	 * Activate or deactivate the PLIU-LB threshold raising strategy (activated by
	 * default).
	 *
	 * @param usePLIULB true to use this strategy
	 */
	public void setUsePLIULB(boolean usePLIULB) {
		this.usePLIULB = usePLIULB;
	}

	/**
	 * Set the maximum number of items that the PLIU-LB strategy removes from an
	 * itemset of the LIUS (4 by default).
	 *
	 * @param maxRemovedItems the maximum number of removed items (at least 1)
	 */
	public void setMaxRemovedItems(int maxRemovedItems) {
		if (maxRemovedItems < 1) {
			throw new IllegalArgumentException("The maximum number of removed items must be at least 1.");
		}
		this.maxRemovedItems = maxRemovedItems;
	}

	/**
	 * Activate or deactivate the EP (early pruning) strategy (activated by
	 * default).
	 *
	 * @param useEP true to use this strategy
	 */
	public void setUseEP(boolean useEP) {
		this.useEP = useEP;
	}

	/**
	 * Activate or deactivate the EA (early abandoning) strategy (activated by
	 * default).
	 *
	 * @param useEA true to use this strategy
	 */
	public void setUseEA(boolean useEA) {
		this.useEA = useEA;
	}

	/**
	 * Print statistics about the latest execution of the TKN algorithm.
	 */
	public void printStats() {
		System.out.println("=============  TKN ALGORITHM - STATS =============");
		System.out.println(" k : " + k);
		System.out.println(" High-utility itemsets count : " + itemsetCount);
		System.out.println(" Final minimum utility : " + minUtil);
		System.out.println(" Minimum utility after PRIU : " + minUtilAfterPRIU);
		System.out.println(" Minimum utility after PLIU-E : " + minUtilAfterPLIUE);
		System.out.println(" Minimum utility after PLIU-LB : " + minUtilAfterPLIULB);
		System.out.println(" Candidate count : " + candidateCount);
		System.out.println(" Projections stopped by EP : " + epPrunedCount);
		System.out.println(" Sub-trees abandoned by EA : " + eaAbandonedCount);
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println("===================================================");
	}
}
