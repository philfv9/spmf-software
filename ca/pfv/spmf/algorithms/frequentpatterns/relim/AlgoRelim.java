package ca.pfv.spmf.algorithms.frequentpatterns.relim;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the RELIM algorithm for mining frequent
 * itemsets. RELIM is proposed by : <br/>
 * <br/>
 * Borgelt, C. (2005) Keeping Things Simple: Finding Frequent Item Sets by
 * Recursive Elimination Workshop Open Source Data Mining Software (OSDM'05,
 * Chicago, IL), 66-70. ACM Press, New York, NY, USA 2005<br/>
 * <br/>
 * From SPMF 2.65, the code includes additional optimizations: (1) Item renaming
 * based on support (ascending order), (2) Transaction merging to combine
 * identical transactions with weights, and more...
 * 
 * @see DatabaseStructureRelim
 * @see TransactionRelim
 * @author Philippe Fournier-Viger
 */
public class AlgoRelim {
	/** Start time */
	private long startTimestamp;

	/** End time */
	private long endTimestamp;

	/** Minimum support as a relative value (integer) */
	private int relativeMinsupp;

	/** Number of frequent items */
	private int frequentItemCount;

	/** Object used to write the result to a file */
	private BufferedWriter writer = null;

	/** Number of frequent itemsets found (for statistics) */
	private int frequentCount;

	/** Converter for renaming items according to support-based total order */
	private ItemNameConverter nameConverter;

	/** Reusable buffer for parsing input lines (avoids repeated allocations) */
	private int[] parseBuffer = new int[500];

	/** Reusable buffer for sorting items when writing itemsets */
	private int[] itemsetBuffer;

	/** Reusable StringBuilder for output formatting */
	private StringBuilder outputBuffer;

	/**
	 * Default constructor
	 */
	public AlgoRelim() {
	}

	/**
	 * Run the algorithm
	 * 
	 * @param minsupp minimum support threshold
	 * @param input   the file path of the input file
	 * @param output  the file path of the desired output file
	 * @throws IOException exception if error reading/writing files
	 */
	public void runAlgorithm(double minsupp, String input, String output) throws IOException {
		// record start time
		startTimestamp = System.currentTimeMillis();

		// prepare output file
		writer = new BufferedWriter(new FileWriter(output));

		// reset the number of itemsets found to 0
		frequentCount = 0;
		// reset the utility for checking the memory usage
		MemoryLogger.getInstance().reset();

		// (1) First pass: Scan the database and count the support of each item
		Map<Integer, Integer> itemCounts = new HashMap<>();
		List<int[]> rawTransactions = new ArrayList<>();

		BufferedReader reader = new BufferedReader(new FileReader(input));
		String line;
		// for each line (transaction) until the end of file
		while ((line = reader.readLine()) != null) {
			// if the line is a comment, is empty or is a kind of metadata
			if (isCommentLine(line)) {
				continue;
			}

			// parse the transaction into items using efficient parsing
			int[] transaction = parseLine(line);
			if (transaction.length > 0) {
				rawTransactions.add(transaction);
				// count support of each item
				for (int item : transaction) {
					itemCounts.merge(item, 1, Integer::sum);
				}
			}
		}
		reader.close();

		int transactionCount = rawTransactions.size();

		// transform the minimum support from relative to absolute value
		this.relativeMinsupp = (int) Math.ceil(minsupp * transactionCount);

		// (2) Find frequent items and sort by ascending support
		List<Map.Entry<Integer, Integer>> frequentItems = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : itemCounts.entrySet()) {
			if (entry.getValue() >= relativeMinsupp) {
				frequentItems.add(entry);
			}
		}

		// Sort by ascending support, then by item value for tie-breaking
		frequentItems.sort((a, b) -> {
			int cmp = Integer.compare(a.getValue(), b.getValue());
			if (cmp == 0) {
				return Integer.compare(a.getKey(), b.getKey());
			}
			return cmp;
		});

		frequentItemCount = frequentItems.size();

		// If no frequent items, close and return
		if (frequentItemCount == 0) {
			writer.close();
			endTimestamp = System.currentTimeMillis();
			return;
		}

		// (3) Create item name converter for renaming items to 0, 1, 2, ...
		// Items are renamed in ascending order of support
		nameConverter = new ItemNameConverter(frequentItemCount, 0);
		for (Map.Entry<Integer, Integer> entry : frequentItems) {
			nameConverter.assignNewName(entry.getKey());
		}

		// Initialize buffers for writing itemsets
		itemsetBuffer = new int[frequentItemCount];
		outputBuffer = new StringBuilder(256);

		// (4) Recode transactions: keep only frequent items, rename them, and sort
		List<TransactionRelim> recodedTransactions = recodeTransactions(rawTransactions);
		rawTransactions = null; // allow GC

		MemoryLogger.getInstance().checkMemory();

		// (6) Create initial database structure
		// supports[i] = weighted support of item i as first item
		int[] supports = new int[frequentItemCount];

		// Create database structure with one transaction list per item
		DatabaseStructureRelim initialDatabase = new DatabaseStructureRelim(supports, frequentItemCount);

		// Insert transactions into initial database structure
		for (int index = 0, size = recodedTransactions.size(); index < size; index++) {
			TransactionRelim transaction = recodedTransactions.get(index);
			int length = transaction.length();

			if (length == 0) {
				continue;
			}

			// Get the first item (which is the renamed item, so it's also the index)
			int firstItem = transaction.get(0);
			int weight = transaction.getWeight();

			// Increase the weighted support of the first item
			supports[firstItem] += weight;

			// Add the transaction (excluding the first item) to the list for firstItem
			if (length > 1) {
				initialDatabase.getOrCreateList(firstItem).add(new TransactionRelim(transaction.getItems(), 1, weight));
			}
		}

		// release the memory
		recodedTransactions = null;

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// (7) START RECURSION
		// Create reusable prefix buffer (allocate once)
		int[] prefixBuffer = new int[frequentItemCount];

		// call the recursive procedure to discover itemsets
		recursion(initialDatabase, prefixBuffer, 0);

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// close the output file
		writer.close();

		// record end time
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Checks whether a line is empty or a comment line. Comment lines start with
	 * '#', '%', or '@'.
	 * 
	 * @param line the input line
	 * @return true if the line should be ignored
	 */
	private boolean isCommentLine(String line) {
		if (line.isEmpty()) {
			return true;
		}
		char first = line.charAt(0);
		return first == '#' || first == '%' || first == '@';
	}

	/**
	 * Parses a transaction line into an array of integers. Items are separated by
	 * whitespace; non-numeric tokens are ignored. This is more efficient than using
	 * String.split().
	 * 
	 * @param line the input line
	 * @return the parsed items as an integer array
	 */
	private int[] parseLine(String line) {
		int len = line.length();
		int count = 0;
		int i = 0;

		while (i < len) {
			// Skip whitespace
			while (i < len && line.charAt(i) <= ' ') {
				i++;
			}
			if (i >= len) {
				break;
			}

			// Handle negative numbers
			boolean negative = false;
			if (line.charAt(i) == '-') {
				negative = true;
				i++;
			}

			// Parse digits
			int value = 0;
			int digitStart = i;
			while (i < len) {
				char c = line.charAt(i);
				if (c >= '0' && c <= '9') {
					value = value * 10 + (c - '0');
					i++;
				} else {
					break;
				}
			}

			// If we parsed at least one digit, store the number
			if (i > digitStart) {
				if (count >= parseBuffer.length) {
					int[] newBuffer = new int[parseBuffer.length * 2];
					System.arraycopy(parseBuffer, 0, newBuffer, 0, parseBuffer.length);
					parseBuffer = newBuffer;
				}
				parseBuffer[count++] = negative ? -value : value;
			}

			// Skip any remaining non-whitespace characters
			while (i < len && line.charAt(i) > ' ') {
				i++;
			}
		}

		int[] result = new int[count];
		System.arraycopy(parseBuffer, 0, result, 0, count);
		return result;
	}

	/**
	 * Recode transactions: filter out infrequent items, rename items, sort items
	 * within each transaction, and merge identical transactions using HashMap. This
	 * approach is O(n) average case instead of O(n log n) for sort-based merging.
	 * 
	 * @param rawTransactions the list of raw transactions
	 * @return list of recoded and merged transactions
	 */
	private List<TransactionRelim> recodeTransactions(List<int[]> rawTransactions) {
		// Use HashMap for O(n) merging of transactions in databases
		Map<TransactionRelim, TransactionRelim> mergeMap = new HashMap<>(rawTransactions.size());

		for (int idx = 0, size = rawTransactions.size(); idx < size; idx++) {
			int[] raw = rawTransactions.get(idx);

			// Count how many frequent items are in this transaction
			int count = 0;
			for (int i = 0, len = raw.length; i < len; i++) {
				if (nameConverter.isOldItemExisting(raw[i])) {
					count++;
				}
			}

			if (count > 0) {
				// Create array with renamed items
				int[] items = new int[count];
				int index = 0;
				for (int i = 0, length = raw.length; i < length; i++) {
					int item = raw[i];
					if (nameConverter.isOldItemExisting(item)) {
						items[index++] = nameConverter.toNewName(item);
					}
				}

				// Sort by ascending order (which is ascending support due to renaming)
				Arrays.sort(items);

				// Create transaction and try to merge with existing
				TransactionRelim newTransaction = new TransactionRelim(items, 0, 1);

				// Use putIfAbsent for single hash computation
				TransactionRelim existing = mergeMap.putIfAbsent(newTransaction, newTransaction);
				if (existing != null) {
					// Identical transaction exists, then add weight to existing
					existing.addWeight(1);
				}
			}
		}

		// Return as list
		return new ArrayList<>(mergeMap.values());
	}

	/**
	 * Recursive method for discovering frequent itemsets starting with a given
	 * prefix.
	 * 
	 * @param database  the database structure
	 * @param prefix    reusable prefix buffer
	 * @param prefixLen current prefix length
	 * @throws IOException exception if error writing to the output file
	 */
	private void recursion(DatabaseStructureRelim database, int[] prefix, int prefixLen) throws IOException {

		// Allocate mergeMaps for merging identical transactions
		@SuppressWarnings("unchecked")
		Map<TransactionRelim, TransactionRelim>[] mergeMaps = new Map[frequentItemCount];

		// Loop for each items
		for (int i = 0; i < frequentItemCount; i++) {
			int support = database.supports[i];

			if (support == 0) {
				continue;
			}

			boolean isFrequent = support >= relativeMinsupp;
			if (isFrequent) {
				writeOut(i, prefix, prefixLen, support);
			}

			List<TransactionRelim> transactionsForI = database.getList(i);

			if (transactionsForI == null || transactionsForI.isEmpty()) {
				continue;
			}

			int transactionCount = transactionsForI.size();
			prefix[prefixLen] = i;

			int[] newSupports = isFrequent ? new int[frequentItemCount] : null;

			DatabaseStructureRelim databasePrefix = isFrequent
					? new DatabaseStructureRelim(newSupports, frequentItemCount)
					: null;

			boolean hasNewSupport = false;

			for (int t = 0; t < transactionCount; t++) {
				TransactionRelim transaction = transactionsForI.get(t);

				int[] items = transaction.getItems();
				int offset = transaction.getOffset();
				int len = items.length - offset;
				int weight = transaction.getWeight();

				if (len == 0) {
					continue;
				}

				int firstItem = items[offset];

				database.supports[firstItem] += weight;

				// Only track for child database if item is frequent (will recurse)
				if (isFrequent) {
					newSupports[firstItem] += weight;
					hasNewSupport = true;
				}

				if (len >= 2) {
					// Create sub-transaction starting after firstItem
					TransactionRelim subTransaction = new TransactionRelim(items, offset + 1, weight);

					if (mergeMaps[firstItem] == null) {
						mergeMaps[firstItem] = new HashMap<>();
					}

					// Use putIfAbsent for single hash computation
					TransactionRelim existing = mergeMaps[firstItem].putIfAbsent(subTransaction, subTransaction);
					if (existing != null) {
						// Merge weights into existing transaction
						existing.addWeight(weight);
					} else {
						// New unique transaction - add to parent database
						database.getOrCreateList(firstItem).add(subTransaction);
					}
				}
			}

			transactionsForI.clear();

			// Convert merge maps to lists in databasePrefix
			// Only recurse if item is frequent (anti-monotonicity pruning)
			if (isFrequent && hasNewSupport) {
				// Only check items > i since transactions contain only items > i
				for (int item = i + 1; item < frequentItemCount; item++) {
					Map<TransactionRelim, TransactionRelim> mergeMap = mergeMaps[item];
					if (mergeMap != null && !mergeMap.isEmpty()) {
						List<TransactionRelim> list = databasePrefix.getOrCreateListWithCapacity(item, mergeMap.size());
						list.addAll(mergeMap.values());
					}
				}

				recursion(databasePrefix, prefix, prefixLen + 1);
			}

			// Clear mergeMaps entries for next iteration
			for (int j = i + 1; j < frequentItemCount; j++) {
				mergeMaps[j] = null;
			}
		}
	}

	/**
	 * Write a frequent itemset to the output buffer. Uses buffered writing to
	 * reduce I/O overhead.
	 * 
	 * @param item         an item (renamed) that should be appended to the prefix
	 * @param prefix       the prefix itemset (using renamed items)
	 * @param prefixLength the prefix length
	 * @param support      the support of the itemset with the item
	 * @throws IOException exception if error while writing to the output file.
	 */
	private void writeOut(int item, int[] prefix, int prefixLength, int support) throws IOException {
		frequentCount++;

		// Copy all items to buffer with original names
		int length = prefixLength + 1;
		itemsetBuffer[0] = nameConverter.toOldName(item);
		for (int i = 0; i < prefixLength; i++) {
			itemsetBuffer[i + 1] = nameConverter.toOldName(prefix[i]);
		}

		// Sort items to output in ascending order
		Arrays.sort(itemsetBuffer, 0, length);

		// Build output string in reusable buffer to reduce writer calls
		outputBuffer.setLength(0);
		outputBuffer.append(itemsetBuffer[0]);
		for (int i = 1; i < length; i++) {
			outputBuffer.append(' ').append(itemsetBuffer[i]);
		}
		outputBuffer.append(" #SUP: ").append(support);
		writer.write(outputBuffer.toString());
		writer.newLine();
	}

	/**
	 * Print statistics about the latest execution of the algorithm to System.out
	 */
	public void printStatistics() {
		System.out.println("========== RELIM 2.65 - STATS ============");
		System.out.println(" Number of frequent itemsets: " + frequentCount);
		System.out.println(" Total time ~: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory: " + MemoryLogger.getInstance().getMaxMemory());
		System.out.println("=====================================");
	}
}