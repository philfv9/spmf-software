package ca.pfv.spmf.algorithms.frequentpatterns.slim;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.tools.MemoryLogger;

/* Copyright (c) 2008-2013 Philippe Fournier-Viger
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
 * This is an implementation of the SLIM algorithm. This algorithm is described
 * in: <br/>
 * <br/>
 * 
 * Smets et al. (2012) SLIM: Directly Mining Descriptive Patterns * <br/>
 * <br/>
 * 
 * This is an optimized version that saves the result to a file or keep it into
 * memory if no output path is provided by the user to the runAlgorithm
 * method().<br/>
 * <br/>
 * 
 * The article is not very detailed, so I had to take some design decisions in
 * the code but I tried to follow the idea of SLIM as closely as possible.
 *
 * @see Itemset
 * @author Philippe Fournier-Viger, 2024
 */
public class AlgoSLIM {

	/** Object to write result to the output file **/
	private BufferedWriter writer = null;

	/** Double formatting code */
	private static final String DOUBLE_FORMAT = "%.3f";

	/** DEBUG MODE */
	private boolean DEBUG_MODE = false;

	/** an empty list */
	final List<Integer> EMPTY_LIST = new ArrayList<Integer>();

//	/** Post-acceptance pruning optimization */
//	private boolean USE_POST_ACCEPTANCE_PRUNING = false;

	/** If true, display the iteration number in the console */
	private boolean DISPLAY_ITERATIONS_IN_CONSOLE = false;

	/** maximum memory usage (for statistics) */
	private double maxMemoryUsage;

	/** the start time of the last execution (for statistics) **/
	private long startTimestamp;

	/** the end time of the last execution (for statistics) **/
	private long endTimeStamp;

	/** Final encoded size (for statistics) */
	private double finalTotalSize;

	/** Initial size (for statistics) **/
	private double initialSize;

	/** Number of patterns found (for statistics) **/
	private int patternCount;

	/** number of generated candidates (for statistics) */
	private long generatedCandidatesCount;

	/** number of testedCandidates (for statistics) */
	private long testedCandidatesCount;

	/**
	 * A temporary array used for storing a copy of a transaction when calculating
	 * the cover, and also used when performing merges
	 */
	private static int[] tempArray = new int[3000];

	/** Constructor */
	public AlgoSLIM() {

	}

	/**
	 * Class candidate
	 */
	class Candidate implements Comparable<Candidate> {
		/** The itemset */
		Itemset itemset;

		/** The estimated total size for encoding with this itemset */
		double totalsize;

		/** Constructor */
		Candidate(Itemset itemset, double totalsize) {
			this.itemset = itemset;
			this.totalsize = totalsize;
		}

		@Override
		public String toString() {
			return "(" + Arrays.toString(itemset.items) + " size: " + totalsize + ")";
		}

		/**
		 * Compare candidates using the Gain Order.
		 */
		public int compareTo(Candidate candidate2) {
			int value = Double.compare(candidate2.totalsize, this.totalsize);
			if (value != 0) {
				return value;
			}
			int comparison = candidate2.itemset.length() - this.itemset.length();
			if (comparison != 0) {
				return comparison;
			}
			// Use the compareTo method of the Itemset class
			return this.itemset.compareTo(candidate2.itemset);
		}

		// Override the equals method
		@Override
		public boolean equals(Object obj) {
			Candidate candidate2 = (Candidate) obj;
			int value = (int) (candidate2.totalsize - this.totalsize);
			if (value != 0) {
				return false;
			}
			int comparison = candidate2.itemset.length() - this.itemset.length();
			if (comparison != 0) {
				return false;
			}
//			// Cast the parameter to a Candidate object
//			Candidate other = (Candidate) obj;
			// Compare the fields using equals and ==
			return this.itemset.equals(candidate2.itemset);
		}

		// Override the hashCode method
		@Override
		public int hashCode() {
			return Arrays.hashCode(itemset.items);
		}
	}

	/**
	 * Standard Cover Order comparator As defined in the paper, itemsets are first
	 * sorted by decreasing size, then decreasing support and then increasing
	 * lexicographical order.
	 */
	private Comparator<Itemset> comparatorStandardCoverOrder = new Comparator<Itemset>() {
		@Override
		public int compare(Itemset a, Itemset b) {
			int lengthDifference = b.items.length - a.items.length;
			if (lengthDifference != 0) {
				return lengthDifference;
			}
			int supportDifference = b.transactionList.size() - a.transactionList.size();
			if (supportDifference != 0) {
				return supportDifference;
			}
			for (int i = 0; i < a.items.length; i++) {
				if (a.items[i] < b.items[i]) {
					return -1;
				}
				if (a.items[i] > b.items[i]) {
					return 1;
				}
			}
			return 0;
		}
	};

	/**
	 * A method to run the SLIM algorithm and return the final code table
	 * 
	 * @param databaseFilePath the input file path
	 * @param outputFilePath   the output file path
	 * @return a list of itemsets
	 * @throws IOException if error while reading or writing the files
	 */
	public List<Itemset> runAlgorithm(String databaseFilePath, String outputFilePath) throws IOException {
		// record the start time
		startTimestamp = System.currentTimeMillis();

		generatedCandidatesCount = 0;

		// Reset the tool to record the maximum memory usage
		MemoryLogger.getInstance().reset();
		MemoryLogger.getInstance().checkMemory();

		// Read the database file
		TransactionDatabase database = readDatabase(databaseFilePath);

		// Create standard code table as initial database
		List<Itemset> codeTable = initializeCodeTable(database);

		// Sort the code table by decreasing support
		Collections.sort(codeTable, comparatorStandardCoverOrder);

		if (DEBUG_MODE) {
			System.out.println("== Step 1: Read the database (" + database.size() + " transactions) ==");
			database.printDatabase();
			System.out.println();
		}

		if (DEBUG_MODE) {
			System.out.println("== Step 2: Initialize the code table with single items and sort it==");
			printCodeTable(codeTable);

			System.out.println();
			System.out.println("  The standard code table size is: ");
		}

		// Compute the initial total size using the standard code table
		Size currentCodeTableSize = computeSize(codeTable);
		initialSize = currentCodeTableSize.totalSize();

		// Generate the candidates and sort them by increasing gain
		List<Candidate> candidates = generateCandidates(database, codeTable, currentCodeTableSize);
		Collections.sort(candidates);

		if (DEBUG_MODE) {
			System.out.println();
			System.out.println("== GENERATE CANDIDATES ==");
			System.out.println();
			System.out.println("  Candidate list sorted by increasing gain: " + candidates);
		}

		// Loop until the code table does not change
		while (candidates.size() > 0 && testedCandidatesCount < MAX_NUMBER_OF_ITERATIONS) {

			// Pop the candidate (highest gain)
			Itemset candidate = candidates.removeLast().itemset;

			testedCandidatesCount++;
			if (DISPLAY_ITERATIONS_IN_CONSOLE) {
				System.out.println(testedCandidatesCount);
			}

			if (DEBUG_MODE) {
				System.out.println();
				System.out.println("== NEW ITERATION ==");
				System.out.println(
						" Select candidate: " + Arrays.toString(candidate.items) + " for insertion in the table");
				System.out.println(" The current code table is: ");
				printCodeTable(codeTable);
				System.out.println("  Total size = " + asString(currentCodeTableSize.totalSize()) + " bits.");
			}

			// Add the candidate to the code table
			// after "any present code table element with the same length"

			// IMPORTANT: Set the support of the candidate to ZERO.
			// (otherwise he wont be inserted at the right place)
			List<Integer> listBackup = candidate.transactionList;
			candidate.transactionList = EMPTY_LIST;

			// First find the position in the code table where the candidate should be
			// inserted:
			int insertionPosition = 0;
			for (int i = codeTable.size() - 1; i >= 0; i--) {
				int value = comparatorStandardCoverOrder.compare(codeTable.get(i), candidate);

				if (value < 0) {
					insertionPosition = i + 1;
					break;
				}
			}

			// Insert the candidate at that position
			codeTable.add(insertionPosition, candidate);

			if (DEBUG_MODE) {
//					System.out.println(" The insertion position of the candidate is: " + insertionPosition);
				System.out.println(" The code table after inserting the candidate is:  ");
				printCodeTable(codeTable);
			}

			candidate.transactionList = listBackup;

			// Only used if post-acceptance pruning is activated
//			Map<Itemset, Integer> mapPreviousItemsetToCount = null;
//			if (USE_POST_ACCEPTANCE_PRUNING) {
//				// If post-acceptance pruning is activated, we make a copy of the code table
//				mapPreviousItemsetToCount = makeBackupOfSupportCounts(codeTable);
//			}

			// Update the support of each itemset in the code table
			updateSupportInTheCodeTable(database, codeTable);

			// Sort the code table by the desired order
			Collections.sort(codeTable, comparatorStandardCoverOrder);

			// Compute the new encoded size of the database using the code table
			Size newSizes = computeSize(codeTable);
			double newTotalSize = newSizes.totalSize();

			if (DEBUG_MODE) {
				System.out.println(" The code table after updating the support:  ");
				printCodeTable(codeTable);
				System.out.println(" The code table after sorting:  ");
				printCodeTable(codeTable);
				System.out.println();
				System.out.println("   = Size calculation =");
				System.out.println("   The total size is: " + asString(newTotalSize) + " but before, it was: "
						+ asString(currentCodeTableSize.totalSize()) + ".");
			}

			// If the new size is smaller than the previous size, ACCEPT the candidate
			if (newTotalSize < currentCodeTableSize.totalSize()) {
				currentCodeTableSize = newSizes;

				if (DEBUG_MODE) {
					System.out.println("   Hence, the candidate " + Arrays.toString(candidate.items) + " is KEPT!");
				}

//				if (USE_POST_ACCEPTANCE_PRUNING) {
//					if (DEBUG_MODE) {
//						System.out.println();
//						System.out.println("= Try to do post-acceptance pruning = ");
//						System.out.println();
//					}
//
//					List<Itemset> pruneSet = calculatePruneSet(codeTable, mapPreviousItemsetToCount);
//					pruneSet.sort(
//							(itemset1, itemset2) -> itemset1.transactionList.size() - itemset2.transactionList.size());
//
//					for (int z = 0; z < pruneSet.size(); z++) {
//						Itemset pruneCand = pruneSet.get(z);
//
//						List<Itemset> prunedCodeTable = makeCopyWithout(codeTable, pruneCand);
//
//						// Update the support of each itemset in the code table
//						updateSupportInTheCodeTable(database, prunedCodeTable);
//
//						Size prunedSizes = computeSize(prunedCodeTable);
////
//						if (prunedSizes.totalSize() < newTotalSize) {
//							if (DEBUG_MODE) {
//								System.out.println("   %%%%%% pruned:" + pruneCand);
//							}
//							
//							// Keep the new table
//							codeTable = prunedCodeTable;
//							currentCodeTableSize = prunedSizes;
//							newTotalSize = prunedSizes.totalSize();
//						} else {
//							codeTable = prunedCodeTable;
//							codeTable.add(pruneCand);
//
//							Collections.sort(codeTable, comparatorStandardCoverOrder);
//
//							// Update the support of each itemset in the code table
//							updateSupportInTheCodeTable(database, codeTable);
//
//							// Sort the code table by the desired order
//							currentCodeTableSize = newSizes;
//						}
//					}
//				}

				// Generate the candidates and sort them in increasing gain
				candidates = generateCandidates(database, codeTable, currentCodeTableSize);
				Collections.sort(candidates);

				if (DEBUG_MODE) {
					System.out.println();
					System.out.println("== GENERATE CANDIDATES ==");
					System.out.println();
					System.out.println("  Candidate list sorted by increasing gain: " + candidates);
					System.out.println();
				}

			}
			// Otherwise, REJECT the candidate and remove it from the code table
			else {
				// Find the position of the candidate
				if (DEBUG_MODE) {
					System.out
							.println("   Hence, the candidate " + Arrays.toString(candidate.items) + " is DISCARDED!");
				}
				codeTable.remove(candidate);
			}

		}

		// Remove all itemsets with support = 0 from the code table because
		// we don't need them
		codeTable.removeIf(itemset -> itemset.transactionList.size() == 0);

		patternCount = codeTable.size();
		finalTotalSize = currentCodeTableSize.totalSize();

		// Save the patterns to the output file if the user has provided a path
		// for writing the results.
		if (outputFilePath != null) {
			writer = new BufferedWriter(new FileWriter(outputFilePath));

			// Write each itemset that has a non zero support!
			for (Itemset itemset : codeTable) {
				writeOut(itemset);
			}
			writer.close();
		}

		if (DEBUG_MODE) {
			// Print the itemsets and the size of the code table
			System.out.println();
			System.out.println("== The final result is: == ");
			for (Itemset itemset : codeTable) {
				System.out
						.println(" " + Arrays.toString(itemset.items) + " support: " + itemset.transactionList.size());
			}
			System.out.println();
		}

		// save the end time.
		MemoryLogger.getInstance().checkMemory();
		maxMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
		endTimeStamp = System.currentTimeMillis();
		patternCount = codeTable.size();

		// Return the final code table
		return codeTable;

	}

	/**
	 * Generate candidates for a given code table
	 * 
	 * @param database             the database
	 * @param codeTable            the code table
	 * @param currentCodeTableSize the current code table size (needed to evaluate
	 *                             the gain)
	 * @return a list of candidates that is unordered
	 */
	private List<Candidate> generateCandidates(TransactionDatabase database, List<Itemset> codeTable,
			Size currentCodeTableSize) {

		List<Candidate> candidates = new ArrayList<Candidate>();

		// Loop over the code table and try to merge each pair of itemsets
		for (int i = 0; i < codeTable.size(); i++) {
			for (int j = i + 1; j < codeTable.size(); j++) {

				// Get the current pair of itemsets
				Itemset x = codeTable.get(i);
				Itemset y = codeTable.get(j);

				// Merge them to create a new itemset
				Itemset xy = merge(x, y);

				if (xy != null) {

					if (notInTheCodeTable(xy, codeTable)) {
						if (DEBUG_MODE) {
							System.out.println();
							System.out.println(
									"  Itemset " + Arrays.toString(x.items) + " and " + Arrays.toString(y.items)
											+ " are merged to obtain candidate: " + Arrays.toString(xy.items));
							System.out.println("  Its transactions are: " + x.transactionList + " AND "
									+ y.transactionList + " = " + xy.transactionList);
						}

						// Estimate how big the total size will be if we would select this itemset as
						// candidate
						// using the formulas from the paper.
						int xprime = x.transactionList.size() - xy.transactionList.size();
						int yprime = y.transactionList.size() - xy.transactionList.size();
						double xyprime = xy.transactionList.size();
						double sprime = currentCodeTableSize.usageCount - xy.transactionList.size();

						if (DEBUG_MODE) {
							System.out.println();
							System.out.println("  The approximated size if we use candidate "
									+ Arrays.toString(xy.items) + " is calculated as: ");
							System.out.println("   xy' = " + xyprime);
							System.out.println("   x' = x - xy' = " + x.transactionList.size() + " - "
									+ xy.transactionList.size() + " = " + xprime);
							System.out.println("   y' = y - xy' = " + y.transactionList.size() + " - "
									+ xy.transactionList.size() + " = " + yprime);
							System.out.println("   s' = s - xy' = " + currentCodeTableSize.usageCount + " - "
									+ xy.transactionList.size() + " = " + sprime);
						}

						// Compute the estimated size
						Size estimatedSize = computeSizeGivenCandidate(database, codeTable, sprime, x, xprime, y,
								yprime, xy, xyprime);

						if (estimatedSize.totalSize() < currentCodeTableSize.totalSize()) {
							candidates.add(new Candidate(xy, estimatedSize.totalSize()));
						}
					}
				}

			}
		}

		generatedCandidatesCount += candidates.size();
		return candidates;
	}

	/**
	 * Verify if an itemset is in the code table already.
	 * 
	 * @param itemset the itemset
	 * @param table   the code table
	 * @return true if the itemset is not in the table. otherwise, false.
	 */
	private boolean notInTheCodeTable(Itemset itemset, List<Itemset> table) {
		for (int i = 0; i < table.size(); i++) {
			if (Arrays.equals(table.get(i).items, itemset.items)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Calculate the prune set for post-acceptance pruning
	 * 
	 * @param codeTable                 the current code table
	 * @param mapPreviousItemsetToCount the previous code table
	 * @return a list of itemset that are candidates to be pruned
	 */
	private List<Itemset> calculatePruneSet(List<Itemset> codeTable, Map<Itemset, Integer> mapPreviousItemsetToCount) {
		List<Itemset> pruneSet = new ArrayList<Itemset>();
		for (Itemset itemset : codeTable) {
			if (itemset.items.length > 1 && itemset.transactionList.size() < mapPreviousItemsetToCount.get(itemset)) {
				pruneSet.add(itemset);
			}
		}
		return pruneSet;
	}

	/**
	 * Method to update the prune set.
	 * 
	 * @param prunedCodeTable a pruned code table
	 * @param codeTable       the previous code table (before pruning)
	 * @param pruneSet        the prune set
	 */
	private void updatePruneSet(List<Itemset> codeTable, Map<Itemset, Integer> mapPreviousItemsetToCount,
			List<Itemset> pruneSet) {
		for (Itemset itemset : codeTable) {
			if (itemset.items.length > 1
					&& itemset.transactionList.size() < mapPreviousItemsetToCount.getOrDefault(itemset, 0)) {
				if (!contains(pruneSet, itemset)) {
					pruneSet.add(itemset);
				}
			}
		}
	}

	/**
	 * Check if an itemset is already in the prune set
	 * 
	 * @param pruneSet the prune set
	 * @param itemset  the itemset
	 * @return true if it is inside, otherwise, false
	 */
	private boolean contains(List<Itemset> pruneSet, Itemset itemset) {
		for (Itemset element : pruneSet) {
			if (Arrays.equals(element.items, itemset.items)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Compute the encoded database size and the code table size, for a given code
	 * table.
	 * 
	 * @param codeTable the code table
	 * @return the calculated sizes (encoded database size and code table size)
	 */
	private Size computeSize(List<Itemset> codeTable) {
		// Initialize an object to store size information
		Size size = new Size();

		// Calculate the total usage count (we need this to calculate logs)
		size.usageCount = 0;
		for (Itemset itemset : codeTable) {
			size.usageCount += itemset.transactionList.size();
		}

		// For each itemset in the code table, calculate its size
		for (Itemset itemset : codeTable) {
			int itemsetSupport = itemset.transactionList.size();
			calculateContributionOfItemsetToSize(size.usageCount, size, itemset, itemsetSupport);
		}

		if (DEBUG_MODE) {
			System.out.println("   Total size = encoded db size + code table size " + " = ("
					+ asString(size.encodedDatabaseSize) + " + " + asString(size.codeTableSize) + ") = "
					+ asString(size.encodedDatabaseSize + size.codeTableSize));
			System.out.println("                              usage count for calculation : " + size.usageCount);
		}
		// Return the size
		return size;
	}

	/**
	 * Compute the encoded database size and the code table size, for a given code
	 * table.
	 * 
	 * @param database  the database
	 * @param codeTable the code table
	 * @return the calculated sizes (encoded database size and code table size)
	 */
	private Size computeSizeGivenCandidate(TransactionDatabase database, List<Itemset> codeTable,
			double totalUsageCount, Itemset itemsetX, int xprime, Itemset itemsetY, int yprime, Itemset itemsetXY,
			double xyprime) {
		Size size = new Size();
		size.usageCount = totalUsageCount;

		// For each itemset in the code table, calculate its size
		for (Itemset itemset : codeTable) {
			int itemsetSupport;
			if (itemset == itemsetX) {
				itemsetSupport = xprime;
			} else if (itemset == itemsetY) {
				itemsetSupport = yprime;
			} else {
				itemsetSupport = itemset.transactionList.size();
			}

			calculateContributionOfItemsetToSize(totalUsageCount, size, itemset, itemsetSupport);
		}

		calculateContributionOfItemsetToSize(totalUsageCount, size, itemsetXY, xyprime);

		if (DEBUG_MODE) {
			System.out.println("   Total size = encoded db size + code table size ");
			System.out.println("               = (" + asString(size.encodedDatabaseSize) + " + "
					+ asString(size.codeTableSize) + ") = " + asString(size.encodedDatabaseSize + size.codeTableSize));
			System.out.println("                              usage count for calculation : " + totalUsageCount);
		}

		// Return the size
		return size;
	}

//	/**
//	 * Compute the encoded database size and the code table size, without a given itemset
//	 * 
//	 * @param database  the database
//	 * @param codeTable the code table
//	 * @return the calculated sizes (encoded database size and code table size)
//	 */
//	private Size computeSizeWithout(TransactionDatabase database, List<Itemset> codeTable, Itemset itemsetToRemove) {
//		// Initialize an object to store size information
//		Size size = new Size();
//
//		// Calculate the total usage count (we need this to calculate logs)
//		size.usageCount = 0;
//		for (Itemset itemset : codeTable) {
//			if(itemset.equals(itemsetToRemove) == false) {
//				size.usageCount += itemset.transactionList.size();
//			}
//		}
//
//		// For each itemset in the code table, calculate its size
//		for (Itemset itemset : codeTable) {
//			int itemsetSupport = itemset.transactionList.size();
//			calculateContributionOfItemsetToSize(size.usageCount, size, itemset, itemsetSupport);
//		}
//
//		if (DEBUG_MODE) {
//			System.out.println("   Total size = encoded db size + code table size " + " = ("
//					+ asString(size.encodedDatabaseSize) + " + " + asString(size.codeTableSize) + ") = "
//					+ asString(size.encodedDatabaseSize + size.codeTableSize));
//			System.out.println("                              usage count for calculation : " + size.usageCount);
//		}
//		// Return the size
//		return size;
//	}

	private void calculateContributionOfItemsetToSize(double totalUsageCount, Size size, Itemset itemset,
			double itemsetSupport) {
		if (itemsetSupport > 0) {
			// Calculate the code length
			double codeLength = -Math.log((double) itemsetSupport / totalUsageCount) / Math.log(2d);

			// Increase the encoded database size
			size.encodedDatabaseSize += codeLength * itemsetSupport;

			// Increase the code table size
			// (important we add +1 for the usage count)
			size.codeTableSize += codeLength + itemset.items.length + 1;

			if (DEBUG_MODE) {

				System.out.println(
						"   encoded size with    " + Arrays.toString(itemset.items) + " is: \t" + asString(codeLength)
								+ " * " + itemsetSupport + " = " + asString((codeLength * itemsetSupport)));
				System.out.println("   code table size with " + Arrays.toString(itemset.items) + " is: \t"
						+ asString(codeLength) + " + " + itemset.items.length + " + 1 = "
						+ asString((codeLength + itemset.items.length) + 1));
			}
		}
	}

	/**
	 * Convert a double to a formatted string
	 * 
	 * @param value the double value
	 * @return the strings
	 */
	private String asString(double value) {
		return String.format(DOUBLE_FORMAT, value);
	}

	/**
	 * Read the database
	 * 
	 * @param databaseFilePath the path of the database file
	 * @throws IOException
	 */
	private TransactionDatabase readDatabase(String databaseFilePath) throws IOException {
		TransactionDatabase database = new TransactionDatabase();
		database.loadFile(databaseFilePath);
		return database;
	}

	/** Maximum number of iterations */
	private long MAX_NUMBER_OF_ITERATIONS = Long.MAX_VALUE;

	/**
	 * Calculate the support of all itemsets in a code table
	 * 
	 * @param database  the database
	 * @param codeTable the code table
	 */
	private void updateSupportInTheCodeTable(TransactionDatabase database, List<Itemset> codeTable) {
		// Reset the support of each itemset to zero in the code table
		for (Itemset itemset : codeTable) {
			itemset.transactionList.clear();
		}

		// For each transaction in the database
//		for (Transaction transaction : database.getTransactions()) {
		for (int i = 0; i < database.getTransactions().size(); i++) {
			Transaction transaction = database.getTransactions().get(i);
			// Make a copy of the transaction in the tempory array to avoid modifying the
			// original transaction
			System.arraycopy(transaction.items, 0, tempArray, 0, transaction.items.length);

			// For each itemset in the code table
			for (Itemset itemset : codeTable) {

				// If the transaction is larger or equal to the itemset's length
				if (transaction.items.length >= itemset.items.length) {

					// If the itemset appears in the transaction
					if (ArraysAlgos.includedIn(itemset.items, tempArray, transaction.items.length)) {

						// Increase the support of the itemset by 1
						itemset.transactionList.add(i);

						// Remove all items of that itemset from the transaction to avoid
						// using them again!
						for (int item : itemset.items) {
							for (int j = 0; j < transaction.items.length; j++) {
								if (tempArray[j] == item) {
									// Remove the item by replacing it by -1.
									tempArray[j] = -1;
									continue;
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * A method to initialize the code table as the standard code table with
	 * singleton itemsets.
	 * 
	 * @return
	 */
	private List<Itemset> initializeCodeTable(TransactionDatabase database) {
		List<Itemset> codeTable = new ArrayList<Itemset>();

		// A map to store the list of transactions of each item
		Map<Integer, List<Integer>> mapItemToTransactions = new HashMap<Integer, List<Integer>>();

		// Loop over the transactions and count the support of each item
		for (int i = 0; i < database.getTransactions().size(); i++) {
			Transaction transaction = database.getTransactions().get(i);
			for (int item : transaction.items) {
				List<Integer> transactionList = mapItemToTransactions.get(item);
				if (transactionList == null) {
					transactionList = new ArrayList<Integer>();
					mapItemToTransactions.put(item, transactionList);
				}
				transactionList.add(i);
			}
		}

		// Loop over the items and create singleton itemsets with their support
		for (Entry<Integer, List<Integer>> entry : mapItemToTransactions.entrySet()) {
			int item = entry.getKey();
			List<Integer> transactionList = entry.getValue();
			Itemset itemset = new Itemset(new int[] { item }, transactionList);
			codeTable.add(itemset);
		}
		return codeTable;
	}

	/**
	 * Print a code table in the console
	 * 
	 * @param codeTable the code table
	 */
	private void printCodeTable(List<Itemset> codeTable) {
		for (Itemset itemset : codeTable) {
			System.out.println("   Pattern: " + itemset.toString() + " Support: " + itemset.transactionList.size()
					+ " Transactions: " + itemset.transactionList);
		}
	}

	/**
	 * A method to merge two itemsets and create a new itemset with one more item
	 * 
	 * @param x the first itemset
	 * @param y the second itemset
	 * @return the resulting itemset or null if the itemset is longer than expected.
	 */
	private static Itemset merge(Itemset x, Itemset y) {
//		if(x.transactionList.size() == 0 || y.transactionList.size() == 0) {
//			return null;
//		}

		// SPECIAL CASE 1: If only single items, we can merge the two itemsets quickly.
		if (x.length() == 1 && y.length() == 1) {
			// Calculate the support of the new itemset as the minimum of x and y
			List<Integer> intersectionXY = intersectTwoSortedLists(x.transactionList, y.transactionList);

			// Create and return the new itemset
			return new Itemset(new int[] { x.items[0], y.items[0] }, intersectionXY);
		}

		// GENERAL CASE: We try to merge the two itemsets in a temporary array to
		// see if there are more than two differences.
		int insertionPosition = 0;
		int posX = 0;
		int posY = 0;

		// Loop over each positions
		while (posX < x.length() && posY < y.length()) {
			int itemX = x.items[posX];
			int itemY = y.items[posY];

			// If the items are the same, add it to the temporary array
			if (itemX == itemY) {
				tempArray[insertionPosition++] = itemX;
				posX++;
				posY++;
			}
			// If not the same, add both items, in lexicographical order
			else if (itemX < itemY) {
				tempArray[insertionPosition++] = itemX;
				posX++;
			} else {
				tempArray[insertionPosition++] = itemY;
				posY++;
			}
		}

		while (posX < x.length()) {
			int itemX = x.items[posX++];
			tempArray[insertionPosition++] = itemX;
		}

		while (posY < y.length()) {
			int itemY = y.items[posY++];
			tempArray[insertionPosition++] = itemY;
		}

		// IMPORTANT: If there are more than one difference or
		// if the number of difference is zero, we dont keep that itemset!
		int maxSize = x.length() < y.length() ? y.length() : x.length();
		int diffCount = (insertionPosition - maxSize);
		if (diffCount != 1) {
			return null;
		}

		// Create a new array of items with the exact size of the result
		int[] items = new int[insertionPosition];

		// Copy the elements from the temporary array to the new array using
		// System.arraycopy
		System.arraycopy(tempArray, 0, items, 0, insertionPosition);

		// Calculate the support of the new itemset as the minimum of x and y
		List<Integer> intersectionXY = intersectTwoSortedLists(x.transactionList, y.transactionList);

		// Create and return the new itemset
		return new Itemset(items, intersectionXY);
	}
//	
//	public static void main(String[] args) {
//		Itemset a = new Itemset(new int[] {1,2,3,5}, new ArrayList<>());
//		Itemset b = new Itemset(new int[] {2,3,4,5,6}, new ArrayList<>());
//		System.out.println(merge(a,b));
//		System.out.println(merge(b,a));
//	}

	/**
	 * A method that performs the intersection of two sorted lists of integers and
	 * return a new sorted list
	 * 
	 * @param list1 a list
	 * @param list2 another list
	 * @return the intersection
	 */
	private static List<Integer> intersectTwoSortedLists(List<Integer> list1, List<Integer> list2) {
		// Create a new list to store the result
		List<Integer> newList = new ArrayList<Integer>();

		// Create two indices to traverse the lists
		int index1 = 0;
		int index2 = 0;

		// Loop until one of the indices reaches the end of the list
		while (index1 < list1.size() && index2 < list2.size()) {
			// Get the current elements of the lists
			int item1 = list1.get(index1);
			int item2 = list2.get(index2);

			// If the first element is smaller, increment the first index
			if (item1 < item2) {
				index1++;
			} // If the second element is smaller, increment the second index
			else if (item2 < item1) {
				index2++;
			} // If they are the same, add it to the new list and increment both indices
			else {
				newList.add(item1);
				index1++;
				index2++;
			}
		}

		// Return the new list
		return newList;
	}

	/**
	 * Method to write an itemset to the output file.
	 * 
	 * @param itemset the itemset
	 */
	private void writeOut(Itemset itemset) throws IOException {
		// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < itemset.items.length; i++) {
			buffer.append(itemset.items[i]);
			buffer.append(' ');
		}
		// append the support value
		buffer.append("#SUP: ");
		buffer.append(itemset.transactionList.size());
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Class for storing information about the encoded database size and code table
	 * size.
	 */
	private class Size {
		double encodedDatabaseSize = 0;
		double codeTableSize = 0;
		double usageCount = 0;

		public double totalSize() {
			return encodedDatabaseSize + codeTableSize;
		}
	}

	// A method to print some statistics about the code table and the database
	public void printStats() {
		System.out.println("=============  SLIM 2.60 - STATS =============");
		System.out.println(" Itemsets found: " + patternCount);
		System.out.println(" Memory : " + String.format("%.2f", maxMemoryUsage) + " MB");
		System.out.println(" Time   : " + (endTimeStamp - startTimestamp) + " ms");
//		if (USE_POST_ACCEPTANCE_PRUNING) {
//			System.out.println(" Post-acceptance pruning: ENABLED");
//		} else {
//			System.out.println(" Post-acceptance pruning: DISABLED");
//		}
		System.out.println(" Generated candidates count: " + generatedCandidatesCount);
		System.out.println(" Tested candidates count: " + testedCandidatesCount);
		System.out.println(" Total size (using the standard code table): " + asString(initialSize));
		System.out.println(" Total size (using the selected itemsets)  : " + asString(finalTotalSize) + " ("
				+ asString((finalTotalSize / initialSize * 100)) + " %)");
		System.out.println("============================================");
	}

	/**
	 * Set a maximum number of iterations before the algorithm stops
	 * 
	 * @param i number of iteration
	 */
	public void setMaxIteration(int i) {
		this.MAX_NUMBER_OF_ITERATIONS = i;
	}
	

	/**
	 * Set whether to display or not the iteration numbers in the console
	 * @param display display if true. Otherwise, false.
	 */
	public void setDisplayIterationInConsole(boolean display) {
		DISPLAY_ITERATIONS_IN_CONSOLE = display;
	}


//	/**
//	 * Activate or not the acceptance pruning
//	 * 
//	 * @param enable if true, acceptance pruning is activated. otherwise not.
//	 */
//	public void setPruningEnabled(boolean enable) {
//		this.USE_POST_ACCEPTANCE_PRUNING = enable;
//	}

//	/**
//	 * Make a copy of the support count information from the count table
//	 * 
//	 * @param codeTable the code table
//	 * @return the copy
//	 */
//	private Map<Itemset, Integer> makeBackupOfSupportCounts(List<Itemset> codeTable) {
//		Map<Itemset, Integer> mapItemsetToCount = new HashMap<>();
//		for (Itemset itemset : codeTable) {
//			mapItemsetToCount.put(itemset, itemset.transactionList.size());
//		}
//		return mapItemsetToCount;
//	}

//	/**
//	 * Make a copy of a code table while removing an itemset
//	 * 
//	 * @param codeTable the code table
//	 * @return the copy
//	 */
//	private List<Itemset> makeCopyWithout(List<Itemset> codeTable, Itemset pruneCand) {
//		List<Itemset> copy = new ArrayList<Itemset>(codeTable.size());
//		for (Itemset itemset : codeTable) {
//			if (Arrays.equals(pruneCand.items, itemset.items) == false) {
//				copy.add(new Itemset(itemset.items, itemset.transactionList));
//			}
//		}
//		return copy;
//	}

}