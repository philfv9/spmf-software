package ca.pfv.spmf.algorithms.frequentpatterns.efim;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import ca.pfv.spmf.datastructures.collections.comparators.ComparatorObject;
import ca.pfv.spmf.datastructures.collections.list.ArrayListInt;
import ca.pfv.spmf.datastructures.collections.list.ArrayListObject;
import ca.pfv.spmf.datastructures.collections.list.ListInt;
import ca.pfv.spmf.datastructures.collections.list.ListObject;
import ca.pfv.spmf.tools.MemoryLogger;
/* This file is copyright (c) 2012-2015 Souleymane Zida & Philippe Fournier-Viger
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
 * This is an implementation of the iMEFIM algorithm for mining high-utility
 * itemsets from a transaction database. iMEFIM is an improved version of MEFIM
 * that uses P-set data structure to reduce database scans.
 * 
 * Based on: "An improved algorithm based on the P-set structure" from
 * L.T.T. Nguyen, P. Nguyen, T.D.D. Nguyen et al. / Knowledge-Based Systems 175 (2019) 130–144
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoIMEFIM {

	/** the set of high-utility itemsets */
	private Itemsets highUtilityItemsets;

	/** object to write the output file */
	BufferedWriter writer = null;

	/** the number of high-utility itemsets found (for statistics) */
	private int patternCount;

	/** the start time and end time of the last algorithm execution */
	long startTimestamp;
	long endTimestamp;

	/** the minutil threshold */
	int minUtil;

	/** if this variable is set to true, some debugging information will be shown */
	final boolean DEBUG = false;

	/** utility bin array for sub-tree utility */
	private int[] utilityBinArraySU;
	/** utility bin array for local utility */
	private int[] utilityBinArrayLU;

	/** a temporary buffer */
	private int[] temp = new int[500];

	/** The total time spent for performing intersections */
	long timeIntersections;
	/** The total time spent for performing database reduction */
	long timeDatabaseReduction;
	/** The total time spent for identifying promising items */
	long timeIdentifyPromisingItems;
	/** The total time spent for sorting */
	long timeSort;
	/** The total time spent for binary search */
	long timeBinarySearch;
	/** The total time spent for P-set operations */
	long timePSetOperations;

	/** an array that map an old item name to its new name */
	int[] oldNameToNewNames;
	/** an array that map a new item name to its old name */
	int[] newNamesToOldNames;
	/** the number of new items */
	int newItemCount;

	/** if true, transaction merging will be performed by the algorithm */
	boolean activateTransactionMerging;

	/** A parameter for transaction merging */
	final int MAXIMUM_SIZE_MERGING = 1000;

	/** number of times a transaction was read */
	long transactionReadingCount;
	/** number of merges */
	long mergeCount;

	/** number of itemsets from the search tree that were considered */
	private long candidateCount;

	/** If true, sub-tree utility pruning will be performed */
	private boolean activateSubtreeUtilityPruning;

	/** 
	 * P-set structure: stores transaction IDs for each item
	 * pSetArray[item] contains the list of transaction indices containing that item
	 */
	private ArrayListInt[] pSetArray;

	/**
	 * Constructor
	 */
	public AlgoIMEFIM() {
	}

	/**
	 * Run the algorithm
	 * 
	 * @param minUtil                       the minimum utility threshold
	 * @param inputPath                     the input file path
	 * @param outputPath                    the output file path to save the result
	 * @param activateTransactionMerging    whether to activate transaction merging
	 * @param maximumTransactionCount       maximum number of transactions to read
	 * @param activateSubtreeUtilityPruning whether to activate subtree utility pruning
	 * @return the itemsets or null if the user chose to save to file
	 * @throws IOException if exception while reading/writing to file
	 */
	public Itemsets runAlgorithm(int minUtil, String inputPath, String outputPath, boolean activateTransactionMerging,
			int maximumTransactionCount, boolean activateSubtreeUtilityPruning) throws IOException {

		// reset variables for statistics
		mergeCount = 0;
		transactionReadingCount = 0;
		timeIntersections = 0;
		timeDatabaseReduction = 0;
		timePSetOperations = 0;

		// save parameters about activating or not the optimizations
		this.activateTransactionMerging = activateTransactionMerging;
		this.activateSubtreeUtilityPruning = activateSubtreeUtilityPruning;

		// record the start time
		startTimestamp = System.currentTimeMillis();

		// read the input file
		Dataset dataset = new Dataset(inputPath, maximumTransactionCount);

		// save minUtil value selected by the user
		this.minUtil = minUtil;

		// if the user chose to save to file
		if (outputPath != null) {
			writer = new BufferedWriter(new FileWriter(outputPath));
		} else {
			writer = null;
			this.highUtilityItemsets = new Itemsets("Itemsets");
		}

		// reset the number of itemset found
		patternCount = 0;

		// reset the memory usage checking utility
		MemoryLogger.getInstance().reset();

		if (DEBUG) {
			System.out.println("===== Initial database === ");
			System.out.println(dataset.toString());
		}

		// Scan the database using utility-bin array to calculate the TWU (local utility)
		// of each item
		useUtilityBinArrayToCalculateLocalUtilityFirstTime(dataset);

		if (DEBUG) {
			System.out.println("===== TWU OF SINGLE ITEMS === ");
			for (int i = 1; i < utilityBinArrayLU.length; i++) {
				System.out.println("item : " + i + " twu: " + utilityBinArrayLU[i]);
			}
			System.out.println();
		}

		// Keep only the promising items (those having a twu >= minutil)
		ListInt itemsToKeep = new ArrayListInt();
		for (int j = 1; j < utilityBinArrayLU.length; j++) {
			if (utilityBinArrayLU[j] >= minUtil) {
				itemsToKeep.add(j);
			}
		}

		// Sort promising items according to the increasing order of TWU
		insertionSort(itemsToKeep, utilityBinArrayLU);

		// Rename promising items according to the increasing order of TWU
		oldNameToNewNames = new int[dataset.getMaxItem() + 1];
		newNamesToOldNames = new int[dataset.getMaxItem() + 1];
		int currentName = 1;
		for (int j = 0; j < itemsToKeep.size(); j++) {
			int item = itemsToKeep.get(j);
			oldNameToNewNames[item] = currentName;
			newNamesToOldNames[currentName] = item;
			itemsToKeep.set(j, currentName);
			currentName++;
		}

		// remember the number of promising items
		newItemCount = itemsToKeep.size();
		// initialize the utility-bin array for counting the subtree utility
		utilityBinArraySU = new int[newItemCount + 1];

		// Initialize P-set array for each item
		pSetArray = new ArrayListInt[newItemCount + 1];
		for (int i = 0; i <= newItemCount; i++) {
			pSetArray[i] = new ArrayListInt();
		}

		if (DEBUG) {
			System.out.println(itemsToKeep);
			System.out.println(Arrays.toString(oldNameToNewNames));
			System.out.println(Arrays.toString(newNamesToOldNames));
		}

		// Remove unpromising items from transactions and rename items
		for (int i = 0; i < dataset.getTransactions().size(); i++) {
			Transaction transaction = dataset.getTransactions().get(i);
			transaction.removeUnpromisingItems(oldNameToNewNames);
		}

		// Sort transactions
		long timeStartSorting = System.currentTimeMillis();
		if (activateTransactionMerging) {
			dataset.getTransactions().sort(new ComparatorObject<Transaction>() {
				@Override
				public int compare(Transaction t1, Transaction t2) {
					int pos1 = t1.items.length - 1;
					int pos2 = t2.items.length - 1;

					if (t1.items.length < t2.items.length) {
						while (pos1 >= 0) {
							int subtraction = t2.items[pos2] - t1.items[pos1];
							if (subtraction != 0) {
								return subtraction;
							}
							pos1--;
							pos2--;
						}
						return -1;
					} else if (t1.items.length > t2.items.length) {
						while (pos2 >= 0) {
							int subtraction = t2.items[pos2] - t1.items[pos1];
							if (subtraction != 0) {
								return subtraction;
							}
							pos1--;
							pos2--;
						}
						return 1;
					} else {
						while (pos2 >= 0) {
							int subtraction = t2.items[pos2] - t1.items[pos1];
							if (subtraction != 0) {
								return subtraction;
							}
							pos1--;
							pos2--;
						}
						return 0;
					}
				}
			});

			// Remove empty transactions
			int emptyTransactionCount = 0;
			for (int i = 0; i < dataset.getTransactions().size(); i++) {
				Transaction transaction = dataset.getTransactions().get(i);
				if (transaction.items.length == 0) {
					emptyTransactionCount++;
				}
			}
			dataset.transactions = dataset.transactions.immutableSubList(emptyTransactionCount,
					dataset.transactions.size());
		}

		timeSort = System.currentTimeMillis() - timeStartSorting;

		if (DEBUG) {
			System.out.println("===== Database without unpromising items and sorted by TWU increasing order === ");
			System.out.println(dataset.toString());
		}

		// Calculate sub-tree utility and build initial P-set for each item
		useUtilityBinArrayToCalculateSubtreeUtilityAndBuildPSet(dataset);

		// Calculate the set of items that pass the sub-tree utility pruning condition
		ArrayListInt itemsToExplore = new ArrayListInt();
		if (activateSubtreeUtilityPruning) {
			for (int b = 0; b < itemsToKeep.size(); b++) {
				int item = itemsToKeep.get(b);
				if (utilityBinArraySU[item] >= minUtil) {
					itemsToExplore.add(item);
				}
			}
		}

		if (DEBUG) {
			System.out.println("===== List of promising items === ");
			System.out.println(itemsToKeep);
		}

		// Recursive call to the algorithm with P-set
		if (activateSubtreeUtilityPruning) {
			backtrackingIMEFIM(dataset.getTransactions(), itemsToKeep, itemsToExplore, 0, pSetArray);
		} else {
			backtrackingIMEFIM(dataset.getTransactions(), itemsToKeep, itemsToKeep, 0, pSetArray);
		}

		// record the end time
		endTimestamp = System.currentTimeMillis();

		// close the output file
		if (writer != null) {
			writer.close();
		}

		// check the maximum memory usage
		MemoryLogger.getInstance().checkMemory();

		return highUtilityItemsets;
	}

	/**
	 * Implementation of Insertion sort for sorting a list of items by increasing
	 * order of TWU.
	 */
	public static void insertionSort(ListInt items, int[] utilityBinArrayTWU) {
		for (int j = 1; j < items.size(); j++) {
			int itemJ = items.get(j);
			int i = j - 1;
			int itemI = items.get(i);

			int comparison = utilityBinArrayTWU[itemI] - utilityBinArrayTWU[itemJ];
			if (comparison == 0) {
				comparison = itemI - itemJ;
			}

			while (comparison > 0) {
				items.set(i + 1, itemI);
				i--;
				if (i < 0) {
					break;
				}
				itemI = items.get(i);
				comparison = utilityBinArrayTWU[itemI] - utilityBinArrayTWU[itemJ];
				if (comparison == 0) {
					comparison = itemI - itemJ;
				}
			}
			items.set(i + 1, itemJ);
		}
	}

	/**
	 * Recursive method to find all high-utility itemsets using P-set optimization
	 * 
	 * @param transactionsOfP the list of transactions containing the current prefix P
	 * @param itemsToKeep     the list of secondary items in the p-projected database
	 * @param itemsToExplore  the list of primary items in the p-projected database
	 * @param prefixLength    the current prefixLength
	 * @param pexSetArray     the P-set (Pex-set) array for items
	 * @throws IOException if error writing to output file
	 */
	private void backtrackingIMEFIM(ListObject<Transaction> transactionsOfP, ListInt itemsToKeep, 
			ListInt itemsToExplore, int prefixLength, ArrayListInt[] pexSetArray) throws IOException {

		// update the number of candidates explored so far
		candidateCount += itemsToExplore.size();

		// For each primary item e
		for (int j = 0; j < itemsToExplore.size(); j++) {
			int e = itemsToExplore.get(j);
			

			// Get the P-set (Pex-set) for item e - this is the key optimization of iMEFIM
			ArrayListInt pexSetE = pexSetArray[e];
			
		      // OPTIMIZATION: Skip if P-set is null or empty
	        if (pexSetE == null || pexSetE.size() == 0) {
	            continue;
	        }

			// Calculate transactions containing P U {e} using P-set
			// Only scan transactions in pexSetE instead of all transactions
			ArrayListObject<Transaction> transactionsPe = new ArrayListObject<Transaction>();

			// variable to calculate the utility of P U {e}
			int utilityPe = 0;

			// For merging transactions
			Transaction previousTransaction = null;
			int consecutiveMergeCount = 0;

			long timeFirstIntersection = System.currentTimeMillis();

			// KEY DIFFERENCE: Only iterate through transactions in P-set (Pex-set)
			// instead of all transactions in transactionsOfP
			for (int pIdx = 0; pIdx < pexSetE.size(); pIdx++) {
				int transactionIndex = pexSetE.get(pIdx);
				
				// Bounds check
				if (transactionIndex < 0 || transactionIndex >= transactionsOfP.size()) {
					continue;
				}
				
				Transaction transaction = transactionsOfP.get(transactionIndex);
				transactionReadingCount++;

				long timeBinaryLocal = System.currentTimeMillis();

				// Binary search to find e in the transaction
				int positionE = -1;
				int low = transaction.offset;
				int high = transaction.items.length - 1;

				while (high >= low) {
					int middle = (low + high) >>> 1;
					if (transaction.items[middle] < e) {
						low = middle + 1;
					} else if (transaction.items[middle] == e) {
						positionE = middle;
						break;
					} else {
						high = middle - 1;
					}
				}
				timeBinarySearch += System.currentTimeMillis() - timeBinaryLocal;

				// if 'e' was found in the transaction
				if (positionE > -1) {
					if (transaction.getLastPosition() == positionE) {
						utilityPe += transaction.utilities[positionE] + transaction.prefixUtility;
					} else {
						if (activateTransactionMerging
								&& MAXIMUM_SIZE_MERGING >= (transaction.items.length - positionE)) {
							Transaction projectedTransaction = new Transaction(transaction, positionE);
							utilityPe += projectedTransaction.prefixUtility;

							if (previousTransaction == null) {
								previousTransaction = projectedTransaction;
							} else if (isEqualTo(projectedTransaction, previousTransaction)) {
								mergeCount++;

								if (consecutiveMergeCount == 0) {
									int itemsCount = previousTransaction.items.length - previousTransaction.offset;
									int[] items = new int[itemsCount];
									System.arraycopy(previousTransaction.items, previousTransaction.offset, items, 0,
											itemsCount);
									int[] utilities = new int[itemsCount];
									System.arraycopy(previousTransaction.utilities, previousTransaction.offset,
											utilities, 0, itemsCount);

									int positionPrevious = 0;
									int positionProjection = projectedTransaction.offset;
									while (positionPrevious < itemsCount) {
										utilities[positionPrevious] += projectedTransaction.utilities[positionProjection];
										positionPrevious++;
										positionProjection++;
									}

									int sumUtilities = previousTransaction.prefixUtility += projectedTransaction.prefixUtility;

									previousTransaction = new Transaction(items, utilities,
											previousTransaction.transactionUtility
													+ projectedTransaction.transactionUtility);
									previousTransaction.prefixUtility = sumUtilities;
								} else {
									int positionPrevious = 0;
									int positionProjected = projectedTransaction.offset;
									int itemsCount = previousTransaction.items.length;
									while (positionPrevious < itemsCount) {
										previousTransaction.utilities[positionPrevious] += projectedTransaction.utilities[positionProjected];
										positionPrevious++;
										positionProjected++;
									}

									previousTransaction.transactionUtility += projectedTransaction.transactionUtility;
									previousTransaction.prefixUtility += projectedTransaction.prefixUtility;
								}
								consecutiveMergeCount++;
							} else {
								transactionsPe.add(previousTransaction);
								previousTransaction = projectedTransaction;
								consecutiveMergeCount = 0;
							}
						} else {
							Transaction projectedTransaction = new Transaction(transaction, positionE);
							utilityPe += projectedTransaction.prefixUtility;
							transactionsPe.add(projectedTransaction);
						}
					}
					transaction.offset = positionE;
				} else {
					transaction.offset = low;
				}
			}

			timeIntersections += (System.currentTimeMillis() - timeFirstIntersection);

			// Add the last read transaction to the database if there is one
			if (previousTransaction != null) {
				transactionsPe.add(previousTransaction);
			}

			// Append item "e" to P to obtain P U {e}
			temp[prefixLength] = newNamesToOldNames[e];

			// if the utility of PU{e} is enough to be a high utility itemset
			if (utilityPe >= minUtil) {
				output(prefixLength, utilityPe);
			}

			// Calculate upper bounds and build new P-set (Pex-set) for items that could extend PU{e}
			ArrayListInt[] newPexSetArray = useUtilityBinArraysToCalculateUpperBoundsAndBuildPSet(
					transactionsPe, j, itemsToKeep);

			long initialTime = System.currentTimeMillis();

			// Create the new list of secondary items
			ArrayListInt newItemsToKeep = new ArrayListInt();
			// Create the new list of primary items
			ArrayListInt newItemsToExplore = new ArrayListInt();

			// for each item
			for (int k = j + 1; k < itemsToKeep.size(); k++) {
				int itemk = itemsToKeep.get(k);

				if (utilityBinArraySU[itemk] >= minUtil) {
					if (activateSubtreeUtilityPruning) {
						newItemsToExplore.add(itemk);
					}
					newItemsToKeep.add(itemk);
				} else if (utilityBinArrayLU[itemk] >= minUtil) {
					newItemsToKeep.add(itemk);
				}
			}

			timeIdentifyPromisingItems += (System.currentTimeMillis() - initialTime);

			// Recursive call with new P-set
			if (activateSubtreeUtilityPruning) {
				backtrackingIMEFIM(transactionsPe, newItemsToKeep, newItemsToExplore, prefixLength + 1, newPexSetArray);
			} else {
				backtrackingIMEFIM(transactionsPe, newItemsToKeep, newItemsToKeep, prefixLength + 1, newPexSetArray);
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Check if two transactions are identical
	 */
	private boolean isEqualTo(Transaction t1, Transaction t2) {
		int length1 = t1.items.length - t1.offset;
		int length2 = t2.items.length - t2.offset;
		if (length1 != length2) {
			return false;
		}
		int position1 = t1.offset;
		int position2 = t2.offset;

		while (position1 < t1.items.length) {
			if (t1.items[position1] != t2.items[position2]) {
				return false;
			}
			position1++;
			position2++;
		}
		return true;
	}

	/**
	 * Scan the initial database to calculate the local utility of each item
	 */
	public void useUtilityBinArrayToCalculateLocalUtilityFirstTime(Dataset dataset) {
		utilityBinArrayLU = new int[dataset.getMaxItem() + 1];

		for (int i = 0; i < dataset.getTransactions().size(); i++) {
			Transaction transaction = dataset.getTransactions().get(i);
			for (int item : transaction.getItems()) {
				utilityBinArrayLU[item] += transaction.transactionUtility;
			}
		}
	}

	/**
	 * Scan the initial database to calculate the sub-tree utility and build P-set
	 * for each item. This is a key modification for iMEFIM.
	 */
	public void useUtilityBinArrayToCalculateSubtreeUtilityAndBuildPSet(Dataset dataset) {
		long startTime = System.currentTimeMillis();
		
		int sumSU;
		for (int transIdx = 0; transIdx < dataset.getTransactions().size(); transIdx++) {
			Transaction transaction = dataset.getTransactions().get(transIdx);
			sumSU = 0;

			// For each item when reading the transaction backward
			for (int i = transaction.getItems().length - 1; i >= 0; i--) {
				int item = transaction.getItems()[i];
				sumSU += transaction.getUtilities()[i];
				utilityBinArraySU[item] += sumSU;

				// Build P-set: add this transaction index to the P-set of item
				if (item > 0 && item < pSetArray.length) {
					pSetArray[item].add(transIdx);
				}
			}
		}
		
		timePSetOperations += (System.currentTimeMillis() - startTime);
	}

	/**
	 * Calculate upper bounds and build new P-set (Pex-set) for projected database.
	 * OPTIMIZED VERSION
	 */
	private ArrayListInt[] useUtilityBinArraysToCalculateUpperBoundsAndBuildPSet(
	        ListObject<Transaction> transactionsPe, int j, ListInt itemsToKeep) {

	    long initialTime = System.currentTimeMillis();

	    // OPTIMIZATION 1: Only allocate P-sets for items we'll actually explore
	    ArrayListInt[] newPexSetArray = new ArrayListInt[newItemCount + 1];
	    // Lazy initialization - only create lists when needed (see below)

	    // Reset utility bins for promising items > e
	    for (int i = j + 1; i < itemsToKeep.size(); i++) {
	        int item = itemsToKeep.get(i);
	        utilityBinArraySU[item] = 0;
	        utilityBinArrayLU[item] = 0;
	    }

	    int sumRemainingUtility;
	    // for each transaction in the projected database
	    for (int transIdx = 0; transIdx < transactionsPe.size(); transIdx++) {
	        Transaction transaction = transactionsPe.get(transIdx);
	        // Removed duplicate: transactionReadingCount++;

	        sumRemainingUtility = 0;

	        // for each item in the transaction
	        for (int i = transaction.getItems().length - 1; i >= transaction.offset; i--) {
	            int item = transaction.getItems()[i];

	            // FIX: Reset high for each binary search!
	            int high = itemsToKeep.size() - 1;
	            int low = j + 1;  // Only search items after j
	            boolean contains = false;

	            while (high >= low) {
	                int middle = (low + high) >>> 1;
	                int itemMiddle = itemsToKeep.get(middle);
	                if (itemMiddle == item) {
	                    contains = true;
	                    break;
	                } else if (itemMiddle < item) {
	                    low = middle + 1;
	                } else {
	                    high = middle - 1;
	                }
	            }

	            // if the item is promising
	            if (contains) {
	                sumRemainingUtility += transaction.getUtilities()[i];
	                utilityBinArraySU[item] += sumRemainingUtility + transaction.prefixUtility;
	                utilityBinArrayLU[item] += transaction.transactionUtility + transaction.prefixUtility;

	                // OPTIMIZATION 2: Lazy initialization of P-set lists
	                if (item > 0 && item < newPexSetArray.length) {
	                    if (newPexSetArray[item] == null) {
	                        newPexSetArray[item] = new ArrayListInt();
	                    }
	                    newPexSetArray[item].add(transIdx);
	                }
	            }
	        }
	    }

	    // OPTIMIZATION 3: Initialize empty lists only for items we'll explore
	    for (int i = j + 1; i < itemsToKeep.size(); i++) {
	        int item = itemsToKeep.get(i);
	        if (newPexSetArray[item] == null) {
	            newPexSetArray[item] = new ArrayListInt();
	        }
	    }

	    timeDatabaseReduction += (System.currentTimeMillis() - initialTime);
	    timePSetOperations += (System.currentTimeMillis() - initialTime);

	    return newPexSetArray;
	}

	/**
	 * Save a high-utility itemset to file or memory
	 */
	private void output(int tempPosition, int utility) throws IOException {
		patternCount++;

		if (writer == null) {
			int[] copy = new int[tempPosition + 1];
			System.arraycopy(temp, 0, copy, 0, tempPosition + 1);
			highUtilityItemsets.addItemset(new Itemset(copy, utility), copy.length);
		} else {
			StringBuffer buffer = new StringBuffer();
			for (int i = 0; i <= tempPosition; i++) {
				buffer.append(temp[i]);
				if (i != tempPosition) {
					buffer.append(' ');
				}
			}
			buffer.append(" #UTIL: ");
			buffer.append(utility);

			writer.write(buffer.toString());
			writer.newLine();
		}
	}

	/**
	 * Print statistics about the latest execution of the iMEFIM algorithm.
	 */
	public void printStats() {
		System.out.println("========== iMEFIM 2.64b - STATS ============");
		System.out.println(" minUtil = " + minUtil);
		System.out.println(" High utility itemsets count: " + patternCount);
		System.out.println(" Total time ~: " + (endTimestamp - startTimestamp) + " ms");
		if (DEBUG) {
			System.out.println(" Transaction merge count ~: " + mergeCount);
			System.out.println(" Transaction read count ~: " + transactionReadingCount);
			System.out.println(" Time intersections ~: " + timeIntersections + " ms");
			System.out.println(" Time database reduction ~: " + timeDatabaseReduction + " ms");
			System.out.println(" Time promising items ~: " + timeIdentifyPromisingItems + " ms");
			System.out.println(" Time binary search ~: " + timeBinarySearch + " ms");
			System.out.println(" Time sort ~: " + timeSort + " ms");
			System.out.println(" Time P-set operations ~: " + timePSetOperations + " ms");
		}
		System.out.println(" Max memory:" + MemoryLogger.getInstance().getMaxMemory());
		System.out.println(" Candidate count : " + candidateCount);
		System.out.println("=====================================");
	}
}