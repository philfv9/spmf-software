package ca.pfv.spmf.algorithms.frequentpatterns.krimp;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the KRIMP algorithm. This algorithm is described
 * in: <br/>
 * <br/>
 * 
 * Vreeken et al. (2011) KRIMP: mining itemsets that compress * <br/>
 * <br/>
 * 
 * This is an optimized version that saves the result to a file or keep it into
 * memory if no output path is provided by the user to the runAlgorithm
 * method().
 *
 * @see Itemset
 * @author Philippe Fournier-Viger, 2024
 */
public class AlgoKrimp {

	/** Double formatting code */
	private static final String DOUBLE_FORMAT = "%.3f";

	/** the start time of the last execution **/
	private long startTimestamp;

	/** the end time of the last execution **/
	private long endTimeStamp;

	/** DEBUG MODE */
	private boolean DEBUG_MODE = false;

	/** Number of patterns found (for statistics) **/
	private int patternCount;
	
	/** maximum memory usage (for statistics) */
	private double maxMemoryUsage;

	/** Final encoded size (for statistics) */
	private double finalTotalSize;

	/** Initial size **/
	private double initialSize;

	/** Object to write result to the output file **/
	private BufferedWriter writer = null;

//	/** Post-acceptance pruning */
//	private boolean USE_POST_ACCEPTANCE_PRUNING = true;

	/**
	 * Class for storing information about the encoded database size and code table
	 * size.
	 */
	private class Size {
		double encodedDatabaseSize = 0;
		double codeTableSize = 0;

		public double totalSize() {
			return encodedDatabaseSize + codeTableSize;
		}
	}

	/**
	 * Constructor
	 */
	public AlgoKrimp() {

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
			int supportDifference = b.support - a.support;
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
	 * Standard Candidate Order comparator As defined in the paper, itemsets are
	 * first sorted by decreasing support, then decreasing size and then increasing
	 * lexicographical order.
	 */
	private Comparator<Itemset> comparatorStandardCandidateOrder = new Comparator<Itemset>() {
		@Override
		public int compare(Itemset a, Itemset b) {
			int supportDifference = b.support - a.support;
			if (supportDifference != 0) {
				return supportDifference;
			}
			int lengthDifference = b.items.length - a.items.length;
			if (lengthDifference != 0) {
				return lengthDifference;
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
	 * Read a file containing patterns (itemsets)
	 * 
	 * @param fileName the path to the file
	 * @return a list of patterns
	 * @throws IOException if error when reading the file
	 */
	private List<Itemset> readPatternSizeTwoOrMore(String fileName) throws IOException {
		List<Itemset> patterns = new ArrayList<Itemset>();

		// Create a buffered reader to read the file line by line
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		String line;

		// For each line
		while ((line = br.readLine()) != null) {
			// Split the line by spaces
			String[] tokens = line.split(" ");

			int itemCount = tokens.length - 2;

			// We want to keep patterns with at least 2 items.
			if (itemCount >= 2) {

				int items[] = new int[itemCount];
				for (int i = 0; i < itemCount; i++) {
					items[i] = Integer.parseInt(tokens[i]);
				}

				// Because the items may not be sorted in an itemset, we sort them by
				// lexicographical order
				Arrays.sort(items);

				// The last token is the support value after #SUP:
				int support = Integer.parseInt(tokens[tokens.length - 1]);

				// Add the itemset
				patterns.add(new Itemset(items, support));

			}
		}
		br.close();
		return patterns;
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

	/**
	 * A method to find the compressing patterns in a database given a set of
	 * patterns.
	 * 
	 * @param databaseFilePath path to a database file
	 * @param patternsFilePath path to a patterns file
	 * @param outputFilePath   a path to save the compressing patterns found (can be
	 *                         set to null if you dont want to save the result to a
	 *                         file)
	 * @return the list compressing patterns
	 * @throws IOException if error while reading or writing to a file
	 */
	public List<Itemset> runAlgorithm(String databaseFilePath, String patternsFilePath, String outputFilePath)
			throws IOException {
		// record the start time
		startTimestamp = System.currentTimeMillis();

		testedCandidatesCount = 0;

		// Reset the tool to record the maximum memory usage
		MemoryLogger.getInstance().reset();
		MemoryLogger.getInstance().checkMemory();

		// Read the patterns file
		List<Itemset> candidates = readPatternSizeTwoOrMore(patternsFilePath);
		readDatabase(databaseFilePath);

		// Read the database file
		TransactionDatabase database = readDatabase(databaseFilePath);

		if (DEBUG_MODE) {
			System.out.println("== Step 1: Read the file containing patterns to use for compression ("
					+ candidates.size() + " patterns) ==");
			printCodeTable(candidates);

			System.out.println();
			System.out.println("== Step 2: Read the database (" + database.size() + " transactions) ==");
			database.printDatabase();
		}

		// Create standard code table
		List<Itemset> codeTable = initializeCodeTable(database);

		// Sort the code table by decreasing support
		Collections.sort(codeTable, comparatorStandardCoverOrder);

		if (DEBUG_MODE) {
			System.out.println(" The standard code table after sorting:  ");
			printCodeTable(codeTable);
			System.out.println();
			System.out.println("   = Size calculation =");
		}

		// Compute the initial encoded size of the database using the standard code
		// table
		Size sizes = computeSize(database, codeTable);
		double totalSize = sizes.totalSize();
		initialSize = totalSize;

		// Sort the candidates by the candidate order
		Collections.sort(candidates, comparatorStandardCandidateOrder);

		if (DEBUG_MODE) {
			System.out.println();
			System.out.println("== Step 3: Sort the candidates by the candidate order: ==");
			printCodeTable(candidates);

			System.out.println();
			System.out.println("== Step 4: Try to add each candidate to the code table ==");
		}

		// Only used if post-acceptance pruning is activated
		List<Itemset> previousCodeTable = null;

		// For each candidate itemset (according to the order), we will try to add it to
		// the code table
		for (int x = 0; x < candidates.size(); x++) {
			Itemset candidate = candidates.get(x);
			if (DEBUG_MODE) {
				System.out.println();
				System.out.println(
						" = Iteration " + (x + 1) + ", the candidate is: " + Arrays.toString(candidate.items) + " =");
				System.out.println(" The current code table is: ");
				printCodeTable(codeTable);
				System.out.println("  total size = " + asString(totalSize) + " bits.");
			}
			
			testedCandidatesCount++;

			// Add the candidate to the code table
			// after "any present code table element with the same length"

			// IMPORTANT: Set the support of the candidate to ZERO.
			// (otherwise he wont be inserted at the right place)
			candidate.support = 0;

			// First find the position in the code table where the candidate should be
			// inserted:
			int insertionPosition = 0;
			for (int i = codeTable.size() - 1; i >= 0; i--) {
				int value = comparatorStandardCoverOrder.compare(codeTable.get(i), candidate);
//				System.out.println("  candidate:" + Arrays.toString(candidate.items) + "  compare with "
//						+ Arrays.toString(codeTable.get(i).items) + " " + value);

				if (value < 0) {
					insertionPosition = i + 1;
					break;
				}

			}

			// Insert the candidate at that position
			codeTable.add(insertionPosition, candidate);

			if (DEBUG_MODE) {
//				System.out.println(" The insertion position of the candidate is: " + insertionPosition);
				System.out.println(" The code table after inserting the candidate is:  ");
				printCodeTable(codeTable);
			}

//			if (USE_POST_ACCEPTANCE_PRUNING) {
//				// If post-acceptance pruning is activated, we make a copy of the code table
//				previousCodeTable = makeCopy(codeTable);
//			}

			// Update the support of each itemset in the code table
			updateSupportInTheCodeTable(database, codeTable);

			if (DEBUG_MODE) {
				System.out.println(" The code table after updating the support:  ");
				printCodeTable(codeTable);
			}

			// Sort the code table by the desired order
			Collections.sort(codeTable, comparatorStandardCoverOrder);

			if (DEBUG_MODE) {
				System.out.println(" The code table after sorting:  ");
				printCodeTable(codeTable);
				System.out.println();
				System.out.println("   = Size calculation =");
			}

			// Compute the new encoded size of the database using the code table
			Size newSizes = computeSize(database, codeTable);
			double newTotalSize = newSizes.totalSize();

			if (DEBUG_MODE) {
				System.out.println("   The total size is: " + asString(newTotalSize) + " but before, it was: "
						+ asString(totalSize) + ".");
			}

			// If the new size is smaller than the previous size, accept the candidate
			if (newTotalSize < totalSize) {
				totalSize = newTotalSize;

				if (DEBUG_MODE) {
					System.out.println("   Hence, the candidate " + Arrays.toString(candidate.items) + " is KEPT!");
				}

//				if (USE_POST_ACCEPTANCE_PRUNING) {
//					if (DEBUG_MODE) {
//						System.out.println();
//						System.out.println("= Try to do post-acceptance pruning = ");
//						System.out.println();
//					}
//					double codeTableSize = newTotalSize;
//
//					List<Itemset> pruneSet = calculatePruneSet(codeTable, previousCodeTable);
//					pruneSet.sort((itemset1, itemset2) -> itemset1.support - itemset2.support);
//
//					for (int z = 0; z < pruneSet.size(); z++) {
//						Itemset pruneCand = pruneSet.get(z);
//						List<Itemset> prunedCodeTable = makeCopyWithout(codeTable, pruneCand);
//						Size prunedSizes = computeSize(database, prunedCodeTable);
//						double prunedTotalSize = prunedSizes.totalSize();
//
//						if (prunedTotalSize < codeTableSize) {
//							// Update the prune set
//							updatePruneSet(prunedCodeTable, codeTable, pruneSet);
//
//							// Keep the new table
//							codeTable = prunedCodeTable;
//							codeTableSize = prunedTotalSize;
//							totalSize = prunedTotalSize;
//						}
//
//					}
//
//				}

			}
			// Otherwise, remove the candidate from the code table
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
		codeTable.removeIf(itemset -> itemset.support == 0);

		patternCount = codeTable.size();
		finalTotalSize = totalSize;

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
				System.out.println(" " + Arrays.toString(itemset.items) + " support: " + itemset.support);
			}
			System.out.println();
		}

		// save the end time.
		MemoryLogger.getInstance().checkMemory();
		maxMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
		endTimeStamp = System.currentTimeMillis();

		// Return the final code table
		return codeTable;
	}

	/**
	 * Create the initial standard code table
	 * 
	 * @param database the database
	 * @return the initial code table
	 */
	private List<Itemset> initializeCodeTable(TransactionDatabase database) {
		// map to store the tidset corresponding to each item
		final Map<Integer, Integer> mapItemToSupport = new HashMap<Integer, Integer>();

		// Loop over the transactions and count the support of each item
		for (int i1 = 0; i1 < database.size(); i1++) {
			for (Integer item : database.getTransactions().get(i1)) {
				mapItemToSupport.put(item, mapItemToSupport.getOrDefault(item, 0) + 1);
			}
		}

		// Create the standard code table with singleton itemsets with their support
		// values
		List<Itemset> codeTable = new ArrayList<>();
		for (Entry<Integer, Integer> entry : mapItemToSupport.entrySet()) {
			int item = entry.getKey();
			int support = entry.getValue();
			Itemset itemset = new Itemset(new int[] { item }, support);
			codeTable.add(itemset);
		}
		return codeTable;
	}

//	/**
//	 * Method to update the prune set.
//	 * 
//	 * @param prunedCodeTable a pruned code table
//	 * @param codeTable       the previous code table (before pruning)
//	 * @param pruneSet        the prune set
//	 */
//	private void updatePruneSet(List<Itemset> codeTable, List<Itemset> previousCodeTable, List<Itemset> pruneSet) {
//		for (int i = 0; i < codeTable.size(); i++) {
//			if (codeTable.get(i).items.length > 1 && codeTable.get(i).support < previousCodeTable.get(i).support) {
//				Itemset itemset = codeTable.get(i);
//				if (contains(pruneSet, itemset)) {
//					pruneSet.add(itemset);
//				}
//			}
//		}
//	}

//	/**
//	 * Calculate the prune set for post-acceptance pruning
//	 * 
//	 * @param codeTable         the current code table
//	 * @param previousCodeTable the previous code table
//	 * @return a list of itemset that are candidates to be pruned
//	 */
//	private List<Itemset> calculatePruneSet(List<Itemset> codeTable, List<Itemset> previousCodeTable) {
//		List<Itemset> pruneSet = new ArrayList<Itemset>();
//		for (int i = 0; i < codeTable.size(); i++) {
//			if (codeTable.get(i).items.length > 1 && codeTable.get(i).support < previousCodeTable.get(i).support) {
//				pruneSet.add(codeTable.get(i));
////				System.out.println(" ADD TO PRUNE SET : " + codeTable.get(i)+ "   " + previousCodeTable.get(i));
//			}
//		}
//
//		return pruneSet;
//	}

//	/**
//	 * Check if an itemset is already in the prune set
//	 * 
//	 * @param pruneSet the prune set
//	 * @param itemset  the itemset
//	 * @return true if it is inside, otherwise, false
//	 */
//	private boolean contains(List<Itemset> pruneSet, Itemset itemset) {
//		for (Itemset element : pruneSet) {
//			if (Arrays.equals(element.items, itemset.items)) {
//				return true;
//			}
//		}
//		return false;
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
//				copy.add(new Itemset(itemset.items, itemset.support));
//			}
//		}
//		return copy;
//	}

//	/**
//	 * Make a copy of a code table
//	 * 
//	 * @param codeTable the code table
//	 * @return the copy
//	 */
//	public List<Itemset> makeCopy(List<Itemset> codeTable) {
//		List<Itemset> copy = new ArrayList<Itemset>(codeTable.size());
//		for (Itemset itemset : codeTable) {
//			copy.add(new Itemset(itemset.items, itemset.support));
//		}
//		return copy;
//	}



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
		buffer.append(itemset.support);
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Print a code table in the console
	 * 
	 * @param codeTable the code table
	 */
	private void printCodeTable(List<Itemset> codeTable) {
		for (Itemset itemset : codeTable) {
			System.out.println("  " + itemset.toString() + " support: " + itemset.support);
		}
	}

	/**
	 * A temporary array used for storing a copy of a transaction when calculating
	 * the cover
	 */
	private int[] tempArray = new int[2058];

	/** number of testedCandidates */
	private long testedCandidatesCount;

	/**
	 * Calculate the support of all itemsets in a code table
	 * 
	 * @param database  the database
	 * @param codeTable the code table
	 */
	private void updateSupportInTheCodeTable(TransactionDatabase database, List<Itemset> codeTable) {
		// Reset the support of each itemset to zero in the code table
		for (Itemset itemset : codeTable) {
			itemset.support = 0;
		}

		// For each transaction in the database
		for (int[] transaction : database.getTransactions()) {
			// Make a copy of the transaction in the tempory array to avoid modifying the
			// original transaction
			System.arraycopy(transaction, 0, tempArray, 0, transaction.length);

			// For each itemset in the code table
			for (Itemset itemset : codeTable) {

				// If the transaction is larger than the itemset
				if (transaction.length >= itemset.items.length) {

					// If the itemset appears in the transaction
					if (ArraysAlgos.includedIn(itemset.items, tempArray, transaction.length)) {

						// Increase the support of the itemset by 1
						itemset.support++;

						// Remove all items of that itemset from the transaction to avoid
						// using them again!
						for (int item : itemset.items) {
							for (int j = 0; j < transaction.length; j++) {
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
	 * Compute the encoded database size and the code table size, for a given code
	 * table.
	 * 
	 * @param database  the database
	 * @param codeTable the code table
	 * @return the calculated sizes (encoded database size and code table size)
	 */
	private Size computeSize(TransactionDatabase database, List<Itemset> codeTable) {
		Size size = new Size();

		// Calculate the total usage count
		double totalUsageCount = 0d;
		for (Itemset itemset : codeTable) {
			totalUsageCount += itemset.support;
		}

		// For each itemset in the code table, calculate its size
		for (Itemset itemset : codeTable) {
			if (itemset.support > 0) {
				// Calculate the code length
				double codeLength = -Math.log((double) itemset.support / totalUsageCount) / Math.log(2d);

				// Increase the encoded database size
				size.encodedDatabaseSize += codeLength * itemset.support;

				// Increase the code table size
				// (important we add +1 for the usage count)
				size.codeTableSize += codeLength + itemset.items.length + 1.0d;

				if (DEBUG_MODE) {

					System.out.println("   encoded size with    " + Arrays.toString(itemset.items) + " is: \t"
							+ asString(codeLength) + " * " + itemset.support + " = "
							+ asString((codeLength * itemset.support)));
					System.out.println("   code table size with " + Arrays.toString(itemset.items) + " is: \t"
							+ asString(codeLength) + " + " + itemset.items.length + " + 1 = "
							+ asString((codeLength + itemset.items.length) + 1));
				}
			}
		}

		if (DEBUG_MODE) {
			System.out.println("   Total size = encoded db size + code table size ");
			System.out.println("               = (" + asString(size.encodedDatabaseSize) + " + "
					+ asString(size.codeTableSize) + ") = " + asString(size.encodedDatabaseSize + size.codeTableSize));

		}
		// Return the size
		return size;
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
	 * Print statistics about the latest execution of Krimp.
	 */
	public void printStats() {
		System.out.println("=============  Krimp 2.60 - STATS =============");
		System.out.println(" Itemsets found: " + patternCount);
		System.out.println(" Memory : " + String.format("%.2f", maxMemoryUsage) + " MB");
		System.out.println(" Time   : " + (endTimeStamp - startTimestamp) + " ms");
//		if (USE_POST_ACCEPTANCE_PRUNING) {
//			System.out.println(" Post-acceptance pruning: ENABLED");
//		} else {
//			System.out.println(" Post-acceptance pruning: DISABLED");
//		}
		System.out.println(" Tested candidates count: " + testedCandidatesCount);
		System.out.println(" Total size (using the standard code table): " + asString(initialSize));
		System.out.println(" Total size (using the selected itemsets)  : " + asString(finalTotalSize) + " ("
				+ asString((finalTotalSize / initialSize * 100)) + " %)");
		System.out.println("============================================");

	}

}
