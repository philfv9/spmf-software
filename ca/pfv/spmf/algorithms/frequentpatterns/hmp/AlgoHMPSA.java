package ca.pfv.spmf.algorithms.frequentpatterns.hmp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import ca.pfv.spmf.datastructures.collections.map.AMapIntToInt;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt;
import ca.pfv.spmf.tools.MemoryLogger;

/* Copyright (c) 2008-2025 Chen Enze, M. Zohaib Nawaz, Philippe Fournier-Viger, M. Saqib Nawaz, 
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
 * SAMP_Runner implements a Simulated Annealing-based Mining Pattern algorithm
 * for data compression optimization. The algorithm discovers frequent patterns
 * that best compress transactional databases.
 * 
 * @author Chen Enze, M. Zohaib Nawaz, Philippe Fournier-Viger, M. Saqib Nawaz
 */
public class AlgoHMPSA {

	// ========= Static Configuration ==============

	/** Minimum compression improvement ratio for pattern acceptance */
	final double IMPROVEMENT_THRESHOLD = 0.001;

	/**
	 * if this variable is set to true, some debugging information will be shown
	 */
	final boolean DEBUG_MODE = false;

	// ================= Optional parameters
	/** Initial temperature for simulated annealing */
	double initialTemperature = 100;

	/** Minimum temperature threshold for annealing termination */
	double minTemperature = 0.1;

	/** Cooling rate for temperature reduction */
	double coolingRate = 0.8;

	// ========== Global Data Structures =============
	/** Code table storing accepted compression patterns */
	ArrayList<int[]> codetable = new ArrayList<>();

	/** Sorted array of all unique items in the database */
	int[] allItems = null;

	/** Frequency map for individual items */
	MapIntToInt itemFrequency;

	/** Length of the longest transaction in the database */
	int longestItemSet = 0;

	/** Total weight for weighted random selection */
	int totalWeight = 0;

	/** Cumulative weights for item selection */
	List<Map.Entry<Integer, Integer>> cumulativeWeights = null;

	/** Frequency map for stored patterns */
	Map<PatternKey, Integer> itemsetCount = null;

	/** Triangular matrix for pairwise item co-occurrences */
	HMPSparseMatrix matrix = null;

	/** Reusable buffer for transaction processing */
	int[] BUFFER = new int[500];

	/** Random number generator */
	Random random = new Random(System.currentTimeMillis());

	/** Maximum allowed size for the code table */
	int maxCodeTableSize = 1583;

	// Generates a neighboring pattern by modifying or adding two patterns in the
	// current pattern set
	int[] BUFFERFLIP = new int[20];

	int[] SMALLBUFFER = new int[10];

	// ========= Statistics ==============

	/** The elapsed time for the most recent execution */
	private long elapsedTime;

	/** Maximum memory usage for the most recent execution */
	private double maxMemoryUsage;

	/** the number of iterations in the last execution */
	private int iterations = 0;

	/** the initial compression size during the last run */
	private double initialCompressionSize = 0;

	/** the final compression size */
	private double currentCompressionSize;

	/**
	 * Run the algorithm.
	 * 
	 * @param inputPath  input file path
	 * @param outputFile output file path
	 * 
	 * @return a list of itemsets found by the algorithm
	 */
	public ArrayList<int[]> runAlgorithm(String inputPath, String outputFile, int maxCodeTableSize) {
		this.maxCodeTableSize = maxCodeTableSize;

		maxMemoryUsage = 0;
		elapsedTime = 0;
		iterations = 0;
		initialCompressionSize = 0;
		codetable.clear();
		totalWeight = 0;
		longestItemSet = 0;
		totalWeight = 0;
		itemFrequency = new AMapIntToInt();
		cumulativeWeights = new ArrayList<Map.Entry<Integer, Integer>>();

		// Create a File object for the specified file
		File file = new File(inputPath);

		if (file.exists() == false) {
			throw new Error("Please give a valid file path");
		}

		if (DEBUG_MODE) {
			System.out.println("Processing file: " + file.getName());
		}
		// Read the database from the file
		List<int[]> database = readItemsetsFromFile(file.getPath());
		initializeDatabase(database);

		// Perform the main algorithm logic (this is the existing code logic)
		initialCompressionSize = calculateSizeInBits(database);
		currentCompressionSize = initialCompressionSize;
		double improveCompressionSize = initialCompressionSize;

		if (DEBUG_MODE) {
			System.out.println("Initial compression size: " + initialCompressionSize + "\n");
		}

		double temperature;

		// Record start time
		long startTime = System.currentTimeMillis();

		// Record memory usage
		MemoryLogger.getInstance().reset(); // Reset memory logger before each execution

		List<int[]> modifiedDatabase = copyDatabase(database);
		int attemptsWithoutImprovement = 0;

		// Main loop for the simulated annealing process
		while (codetable.size() < maxCodeTableSize / 3) {// Control the number of patterns from SA added
															// to the
			// codetable
			MemoryLogger.getInstance().checkMemory();

			iterations = 0;
			temperature = initialTemperature;

			// Generate a random pattern
			int[] pattern = generateRandomItemset(longestItemSet, itemFrequency, codetable);

			int temperatureTryCount = 0;
			while (temperature > minTemperature) {
				// Try to add a pattern to the codetable
				ArrayList<int[]> temp_ct = new ArrayList<>(codetable);
				// Generate neighbor pattern according to FLIP_NUM
				int[] newPattern = generateTwoFlipNeighborPattern(pattern);

				if (isPatternInDatabase(newPattern, modifiedDatabase)) {

					// Try to add new pattern to codetable
					temp_ct.add(newPattern);
					int newCompressionSize = deleteAndCalculateSizeInBits(modifiedDatabase, temp_ct);

					// Calculate the improvement when adding this pattern
					double compressionImprovement = (improveCompressionSize - newCompressionSize)
							/ improveCompressionSize;
					double acceptanceProbability = Math
							.exp((improveCompressionSize - newCompressionSize) / temperature);

					if (compressionImprovement > IMPROVEMENT_THRESHOLD) {
						// Accept the new pattern set because it improves
						improveCompressionSize = newCompressionSize;
						pattern = newPattern;
						temperatureTryCount = 0;
					} else if (acceptanceProbability > random.nextDouble()) {
						// Accept the new pattern with a certain probability
						improveCompressionSize = newCompressionSize;
						pattern = newPattern;
						temperatureTryCount = 0;
					}
				}

				// Decrease the temperature
				temperature *= coolingRate;
				iterations++;
				temperatureTryCount++;
				if (temperatureTryCount > 10) { // If the pattern can not find good neighbors, we should
												// move to the next pattern.
					break;
				}
			}

			if (isPatternInDatabase(pattern, modifiedDatabase)) {

				if (((currentCompressionSize - improveCompressionSize)
						/ initialCompressionSize) > IMPROVEMENT_THRESHOLD) {
					attemptsWithoutImprovement = 0;
					codetable.add(pattern);
					modifiedDatabase = deleteItemset(modifiedDatabase, pattern);
					itemFrequency.clear();
					cumulativeWeights.clear();
					for (int[] transaction : modifiedDatabase) {
						for (int item : transaction) {
							int frequency = itemFrequency.get(item);
							if (frequency == -1) {
								itemFrequency.put(item, 1);
							} else {
								itemFrequency.put(item, frequency + 1);
							}
						}
					}

					cumulativeWeights.clear();
					totalWeight = 0;
					MapIntToInt.EntryIterator iter = itemFrequency.iterator();
					while (iter.hasNext()) {
						MapIntToInt.MapEntryIntToInt entry = iter.next();
						totalWeight += entry.getValue();
						cumulativeWeights.add(new AbstractMap.SimpleEntry<>(entry.getKey(), totalWeight));
					}
					currentCompressionSize = improveCompressionSize;
				}

			}
			attemptsWithoutImprovement++;
			if (attemptsWithoutImprovement > 100 || attemptsWithoutImprovement > maxCodeTableSize / 3) { // or // is
				// less than 1/3
				// codetablesize
				break;
			}
		}

		matrix = new HMPSparseMatrix();
		fillTheMatrix(modifiedDatabase);

		// After the main loop ends, get the pattern of length 2 from the matrix and add
		// it to the codetable according to the number of occurrences
		List<Pair> length2Patterns = matrix.getAllPatternsWithOccurrences();

		// Sort by number of occurrences in descending order
		length2Patterns.sort((entry1, entry2) -> Integer.compare(entry2.count, entry1.count));

		// Initialize variables
		int totalPatterns = length2Patterns.size();

		// Loop until the compressed size change is less than the threshold or all
		// patterns are processed
		while (totalPatterns > 0) {

			// Get the next pattern to add (highest occurrence)
			Pair entry = length2Patterns.get(0);
			int[] pattern = entry.pattern;
//			int count = entry.count;

			// Add the pattern to the codetable and itemsetCount
			codetable.add(pattern);
//			itemsetCount.put(pattern, count);

			// Update the database and matrix after adding the pattern
			modifiedDatabase = deleteItemset(modifiedDatabase, pattern);
			fillTheMatrix(modifiedDatabase);

			// Recalculate the patterns and sort again after processing the pattern
			length2Patterns = matrix.getAllPatternsWithOccurrences();
			length2Patterns.sort((entry1, entry2) -> Integer.compare(entry2.count, entry1.count));
			totalPatterns = length2Patterns.size();

			// Determine whether the difference between the current compression size and the
			// previous compression size is less than the threshold
			if (codetable.size() > maxCodeTableSize - 1) {
				break; // If the difference is less than the threshold, stop the loop
			}

		}

		MemoryLogger.getInstance().checkMemory();

		// Final compression size calculation
		currentCompressionSize = deleteAndCalculateSizeInBits(database, codetable);

		writePatternsToFile(outputFile);

		// Store statistics
		maxMemoryUsage = MemoryLogger.getInstance().checkMemory();
		elapsedTime = System.currentTimeMillis() - startTime;

		return codetable;
	}

	/**
	 * Sets the initial temperature for the simulated annealing algorithm.
	 *
	 * @param initialTemperature the starting temperature value (should be > 0)
	 */
	public void setInitialTemperature(double initialTemperature) {
		this.initialTemperature = initialTemperature;
	}

	/**
	 * Sets the minimum temperature threshold for terminating the simulated
	 * annealing process.
	 *
	 * @param minTemperature the minimum allowable temperature (should be > 0 and <
	 *                       initialTemperature)
	 */
	public void setMinTemperature(double minTemperature) {
		this.minTemperature = minTemperature;
	}

	/**
	 * Sets the cooling rate used to reduce the temperature in each iteration of
	 * simulated annealing.
	 *
	 * @param coolingRate the cooling factor (usually between 0 and 1, e.g., 0.8)
	 */
	public void setCoolingRate(double coolingRate) {
		this.coolingRate = coolingRate;
	}

	/**
	 * Write the patterns found by the algorithm to a file
	 * 
	 * @param outputFile the output file path
	 */
	private void writePatternsToFile(String outputPath) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {

			// Write the final code table and results to the file
			for (Entry<PatternKey, Integer> entry : itemsetCount.entrySet()) {
				int[] pattern = entry.getKey().pattern;
				int support = entry.getValue();

				StringBuilder sb = new StringBuilder();

				// print items separated by spaces
				for (int k = 0; k < pattern.length; k++) {
					sb.append(pattern[k]);
					sb.append(' ');
				}
				sb.append("#SUP:" + support);
				writer.write(sb.toString());
				writer.newLine();
			}

			// Flush the writer after each run
			writer.flush();
		} catch (IOException e) {
			System.err.println("Error writing to the output file: " + e.getMessage());
		}
	}

	/**
	 * Calculates raw database size in bits without compression
	 * 
	 * @param database Transactional database
	 * @return Total size in bits (32 bits per integer)
	 */
	public int calculateSizeInBits(List<int[]> database) {
//		Map<int[], Integer> patternCount = new HashMap<>();

		// Calculate the size in bits of the modified database
		int totalSizeInBits = 0;

		for (int[] transaction : database) {
			// Each integer is 32 bits
			totalSizeInBits += transaction.length * Integer.SIZE;
		}

		return totalSizeInBits;
	}

	/**
	 * Calculates compressed size using current code table
	 * 
	 * @param database  Transactional database
	 * @param codeTable Current compression patterns
	 * @return Total size including both residual data and pattern codes
	 */
	public int deleteAndCalculateSizeInBits(List<int[]> database, List<int[]> codeTable) {

		itemsetCount = new HashMap<>();

		// Calculate the size in bits of the modified database
		int totalSizeInBits = 0;
		// Process each transaction in order
		for (int[] transaction : database) {
			// Dynamic expansion mechanism
			if (BUFFER.length < transaction.length) {
				BUFFER = new int[Math.max(transaction.length * 2, BUFFER.length)]; // 双倍扩容策略
			}

			// Copy transactions to buffer (overwrite only necessary parts)
			System.arraycopy(transaction, 0, BUFFER, 0, transaction.length);
			int newLength = transaction.length;

			// for each pattern
			for (int[] pattern : codeTable) {
				if (newLength >= pattern.length && containsAll(BUFFER, newLength, pattern)) {
					// Increment the count for the pattern
					PatternKey key = new PatternKey(pattern);
					itemsetCount.put(key, itemsetCount.getOrDefault(key, 0) + 1);
					// Remove the pattern from the transaction
					removePatternFromTransaction(BUFFER, newLength, pattern);
					newLength = newLength - pattern.length;
				}
			}
			totalSizeInBits += newLength * Integer.SIZE;
		}
		// Add the size of the transactions in finalResult multiplied by their counts
		for (Map.Entry<PatternKey, Integer> entry : itemsetCount.entrySet()) {
			int patternSize = 0;
			if (entry.getValue() != 0) {
				int keySize = entry.getKey().pattern.length;
				// Calculate the bit size for entry.getValue() using logarithm base 2
				int valueBitSize = (int) Math.ceil(Math.log(entry.getValue() + 1) / Math.log(2));
				patternSize = valueBitSize + keySize * Integer.SIZE;
			}

			totalSizeInBits += patternSize;
		}

		return totalSizeInBits;
	}

	/**
	 * Removes pattern elements from a transaction buffer
	 * 
	 * @param transaction       Transaction buffer (modified in-place)
	 * @param transactionLength Valid length of transaction data
	 * @param pattern           Pattern to remove
	 * @return New transaction array with pattern elements removed
	 */
	public int[] removePatternFromTransaction(int[] transaction, int transactionLength, int[] pattern) {
		Set<Integer> patternSet = new HashSet<>();
		for (int num : pattern) {
			patternSet.add(num);
		}
		int pos = 0;
		for (int j = 0; j < transactionLength; j++) {
			int item = transaction[j];
			if (!patternSet.contains(item)) {
				transaction[pos++] = item;
			}
		}
		return Arrays.copyOf(transaction, pos);
	}

	/**
	 * Make a copy of this itemset but exclude a set of items
	 *
	 * @param itemsetToNotKeep the set of items to be excluded
	 * @return the copy
	 */
	public int[] cloneItemSetMinusAnItemset(int[] itemset, int[] itemsetToNotKeep) {
		// create a new itemset
		int[] newItemset = new int[itemset.length - itemsetToNotKeep.length];
		int i = 0;
		// for each item of this itemset
		for (int j = 0; j < itemset.length; j++) {
			// copy the item except if it is not an item that should be excluded
			if (Arrays.binarySearch(itemsetToNotKeep, itemset[j]) < 0) {
				newItemset[i++] = itemset[j];
			}
		}
		return newItemset; // return the copy
	}

	/**
	 * Removes a specified pattern from each transaction in the given database.
	 *
	 * This method returns a modified version of the database where each transaction
	 * that contains the given pattern has that pattern removed. The rest of the
	 * items remain unchanged. The method tracks how many times the pattern is
	 * removed but does not return that count.
	 *
	 * @param database the original database of transactions as a list of lists of
	 *                 integers
	 * @param pattern  the pattern (list of integers) to remove from each
	 *                 transaction if present
	 * @return a new database with the specified pattern removed from all
	 *         transactions in which it appears
	 */
	public List<int[]> deleteItemset(List<int[]> database, int[] pattern) {
		List<int[]> modifiedDatabase = new ArrayList<>();

		for (int[] transaction : database) {

			if (containsOrEquals(transaction, pattern)) {
				transaction = cloneItemSetMinusAnItemset(transaction, pattern);
			}
			modifiedDatabase.add(transaction);
		}
		return modifiedDatabase;
	}

	/**
	 * Reads itemsets (transactions) from a specified file and returns them as a
	 * list of transactions.
	 *
	 * The file is expected to have one transaction per line, with items separated
	 * by whitespace. Each line is read and split into individual items (as
	 * integers), which are then gathered into an ArrayList<Integer> representing a
	 * single transaction. All transactions are collected and returned as a list of
	 * lists.
	 *
	 * @param fileName the name of the file containing the itemsets
	 * @return a list of transactions, where each transaction is represented as a
	 *         list of integers
	 */
	public List<int[]> readItemsetsFromFile(String fileName) {
		List<int[]> database = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] items = line.split("\\s+");

				int[] transaction = new int[items.length];
				for (int i = 0; i < items.length; i++) {
					transaction[i] = Integer.valueOf(items[i].trim());
				}

				// Sort the transaction
				Arrays.sort(transaction);
				database.add(transaction);
			}
		} catch (IOException | NumberFormatException e) {
			e.printStackTrace();
		}
		return database;
	}

	/**
	 * Generates random itemset weighted by item frequencies
	 * 
	 * @param longestItemSet    Length of longest transaction
	 * @param itemFrequency     Item frequency distribution
	 * @param currentPatternSet Existing patterns to avoid duplicates
	 * @return New unique pattern not present in currentPatternSet
	 */
	public int[] generateRandomItemset(int longestItemSet, MapIntToInt itemFrequency, List<int[]> currentPatternSet) {

		int randomItemsetSize = 3 + random.nextInt(3); // Random size between 2 and longestItemSet
		int length = 0;
		do {
			length = 0;
			while (length < randomItemsetSize) {
				int randomNumber = getWeightedRandomItem();
				if (!containsElementUnsorted(SMALLBUFFER, length, randomNumber)) {
					SMALLBUFFER[length] = randomNumber;
					length++;
				}
			}
			Arrays.sort(SMALLBUFFER, 0, length);
		} while (containsPattern(currentPatternSet, SMALLBUFFER, length)); // Check for duplicates in currentPatternSet

		return Arrays.copyOf(SMALLBUFFER, randomItemsetSize);
	}

	// Check if an element is inside an unsorted array
	private boolean containsElementUnsorted(int[] list, int listLength, int element) {
		for (int i = 0; i < listLength; i++) {
			if (list[i] == element) {
				return true;
			}
		}
		return false;
	}

	// Check if a pattern is in the pattern set
	private boolean containsPattern(List<int[]> listOfPatterns, int[] patternBuffer, int patternLength) {
		for (int[] patternInList : listOfPatterns) {
			if (includedIn(patternBuffer, patternLength, patternInList)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if a sorted itemset1 is contained in another itemset2
	 *
	 * @param itemset1 the first itemset
	 * @param itemset2 the second itemset
	 * @return true if yes, otherwise false
	 */
	public boolean includedIn(int[] itemset1, int itemset1Length, int[] itemset2) {
		int count = 0; // the current position of itemset1 that we want to find in itemset2

		// for each item in itemset2
		for (int i = 0; i < itemset2.length; i++) {
			// if we found the item
			if (itemset2[i] == itemset1[count]) {
				// we will look for the next item of itemset1
				count++;
				// if we have found all items already, return true
				if (count == itemset1Length) {
					return true;
				}
			}
		}
		// it is not included, so return false!
		return false;
	}

	/**
	 * Generates neighbor pattern by flipping two elements
	 * 
	 * @param currentPattern Base pattern for modification
	 * @return New pattern with two elements added/removed
	 */
	public int[] generateTwoFlipNeighborPattern(int[] currentPattern) {

		boolean ok = false;
		while (ok == false) {
			int length = currentPattern.length;

			// COPY THE PATTERN
			System.arraycopy(currentPattern, 0, BUFFERFLIP, 0, currentPattern.length);

			// Choose two distinct indices to flip based on the frequency of each item
			int flipItem1 = itemToIndex(getWeightedRandomItem());
			int flipItem2;
			do {
				flipItem2 = itemToIndex(getWeightedRandomItem());
			} while (flipItem1 == flipItem2); // Ensure the indices are different

			if (!containsElementUnsorted(BUFFERFLIP, length, flipItem1) && length < 10) {
				BUFFERFLIP[length] = flipItem1;
				length++;
			} else if (length > 2) {
				removeItemFromUnsortedList(BUFFERFLIP, length, flipItem1);
				length--;
			}

			if (length > 2) {
				Arrays.sort(BUFFERFLIP, 0, length);
				return Arrays.copyOf(BUFFERFLIP, length);
			}
		}

		return null;
	}

	// Make a copy of a list of items while removing some given item;
	private void removeItemFromUnsortedList(int[] currentPattern, int length, int itemToRemove) {
		int pos = 0;
		for (int i = 0; i < length; i++) {
			if (currentPattern[pos] != itemToRemove) {
				currentPattern[pos] = currentPattern[i];
				pos++;
			}
		}
	}

	/**
	 * Selects a random item based on weighted probability distribution.
	 *
	 * Utilizes cumulative frequency weights to achieve O(log n) selection time. The
	 * probability of selecting an item is proportional to its frequency weight.
	 *
	 * @return The selected item's identifier
	 * @throws IllegalStateException if total weight is uninitialized or invalid (<=
	 *                               0)
	 *
	 *                               Implementation Details: 1. Requires
	 *                               cumulativeWeights to be sorted in ascending
	 *                               order 2. Requires totalWeight = sum of all
	 *                               individual weights 3. Uses binary search to
	 *                               find the first cumulative weight greater than
	 *                               random value
	 *
	 *                               Example: For weights [A:30, B:50, C:20],
	 *                               cumulative weights [30, 80, 100] Random number
	 *                               45 would select B (first cumulative weight >45
	 *                               is 80)
	 */
	public int getWeightedRandomItem() {
		if (totalWeight == 0 || totalWeight < 0) {
			throw new IllegalStateException("Total weight must be initialized and greater than zero.");
		}

		// Generate a random number
		int randomNumber = random.nextInt(totalWeight);

		// Use binary search to find the item in the cumulative weight
		int left = 0;
		int right = cumulativeWeights.size() - 1;

		while (left < right) {
			int mid = (left + right) / 2;
			if (randomNumber < cumulativeWeights.get(mid).getValue()) {
				right = mid;
			} else {
				left = mid + 1;
			}
		}

		return cumulativeWeights.get(left).getKey();
	}

	/**
	 * Checks whether a given pattern of items exists in at least one transaction
	 * within the provided database.
	 *
	 * <p>
	 * This method converts the given pattern and each transaction into sets, then
	 * verifies if any of the transaction sets contains all items of the pattern
	 * set. If at least one transaction fully contains the pattern, the method
	 * returns true.
	 *
	 * @param pattern  the pattern to check for, represented as a list of integers.
	 * @param database the database of transactions, where each transaction is a
	 *                 list of integers.
	 * @return {@code true} if the pattern is found in at least one transaction in
	 *         the database; {@code false} otherwise.
	 */
	// Check if the pattern exists in the database
	public boolean isPatternInDatabase(int[] pattern, List<int[]> database) {
		for (int[] transaction : database) {
			if (containsOrEquals(transaction, pattern)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieves the index of the specified item from the global list of all items.
	 *
	 * <p>
	 * This method creates a list from the global collection of items and returns
	 * the zero-based index of the specified item. If the item is not present, the
	 * method returns -1.
	 *
	 * @param item the item whose index is to be determined.
	 * @return the index of the item in the global list, or -1 if the item does not
	 *         exist in the list.
	 */
	public int itemToIndex(int item) {
		// Note: Could be optimized with HashMap for O(1) lookup if
		// initialized once
		// rather than doing a sequential search
		for (int i = 0; i < allItems.length; i++) {
			if (allItems[i] == item)
				return i;
		}
		return -1;
	}

	/**
	 * Check if an itemset contains another itemset. It assumes that itemsets are
	 * sorted according to the lexical order.
	 *
	 * @param itemset1 the first itemset
	 * @param itemset2 the second itemset
	 * @return true if the first itemset contains the second itemset
	 */
	public boolean containsOrEquals(int itemset1[], int itemset2[]) {
		int i = 0, j = 0;

		// Double pointer traversal, assuming itemset1 and itemset2 are already sorted
		while (i < itemset1.length && j < itemset2.length) {
			if (itemset1[i] == itemset2[j]) {
				j++; // If a matching element is found, continue matching the next element of
						// itemset2
			}
			i++; // Regardless, i must move forward
		}

		// If j reaches the end of itemset2, then itemset1 contains all the elements of
		// itemset2.
		return j == itemset2.length;
	}

	/**
	 * Initializes the database by processing each transaction and updating item
	 * frequencies, pair supports, the longest itemset size, and cumulative weights.
	 *
	 * The method performs the following actions: 1. Clears previously stored global
	 * variables, including item frequencies, codetable, etc. 2. Iterates over each
	 * transaction in the database: - Updates the frequency of each item. - Tracks
	 * the longest itemset size. - Updates the support of all 2-item patterns in the
	 * matrix. 3. Calculates the cumulative weight of items and updates the
	 * `cumulativeWeights` list.
	 *
	 * @param database the list of transactions, where each transaction is
	 *                 represented as a list of integers.
	 */
	public void initializeDatabase(List<int[]> database) {

		// Iterate through the database and update item frequencies and pair supports
		for (int[] transaction : database) {

			// Update item frequency and the support of each item pair
			for (int i = 0; i < transaction.length; i++) {
				Integer item = transaction[i];

				// Update frequency for item1
				int frequency = itemFrequency.get(item);
				if (frequency == -1) {
					itemFrequency.put(item, 1);
				} else {
					itemFrequency.put(item, frequency + 1);
				}
			}

			// Track the longest itemset size
			if (transaction.length > longestItemSet) {
				longestItemSet = transaction.length;
			}
		}

		// Initialize list of all items here
		allItems = new int[itemFrequency.size()];
		int index = 0;
		// Now calculate the cumulative weight and populate the cumulativeWeights list
		MapIntToInt.EntryIterator iter = itemFrequency.iterator();
		while (iter.hasNext()) {
			MapIntToInt.MapEntryIntToInt entry = iter.next();
			allItems[index++] = entry.getKey();
			totalWeight += entry.getValue();
			cumulativeWeights.add(new AbstractMap.SimpleEntry<>(entry.getKey(), totalWeight));
		}
		Arrays.sort(allItems);
	}

	/**
	 * Counts the occurrences of a specific pattern in the provided database of
	 * transactions.
	 *
	 * This method checks each transaction to see if it contains all items in the
	 * given pattern. If so, the transaction is considered a match and is counted.
	 *
	 * @param pattern  the pattern to search for in the database (a list of
	 *                 integers).
	 * @param database the list of transactions to search through.
	 * @return the number of transactions that contain all items in the specified
	 *         pattern.
	 */

	public int countOccurrencesInDatabase(int[] pattern, List<int[]> database) {
		if (pattern.length == 0) {
			return 0;
		}

		int count = 0;
		for (int[] transaction : database) {
			if (containsOrEquals(transaction, pattern)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Checks if a list contains all elements of a pattern (unsorted version).
	 *
	 * Performs a linear search for each pattern element in the list. Suitable for
	 * unsorted datasets. For sorted datasets, consider using a more efficient
	 * algorithm like binary search or two-pointer technique.
	 *
	 * @param list       The target array to search in
	 * @param listLength Valid length of the list array (may contain trailing
	 *                   elements)
	 * @param pattern    The elements to search for
	 * @return true if all pattern elements exist in the list's valid range, false
	 *         otherwise
	 */

	public boolean containsAll(int[] list, int listLength, int[] pattern) {
		// Early exit for empty pattern
		if (pattern.length == 0)
			return true;

		int i = 0; // Pointer for list
		int j = 0; // Pointer for pattern

		// Traverse both arrays simultaneously
		while (i < listLength && j < pattern.length) {
			if (list[i] == pattern[j]) {
				// Match found, move both pointers
				i++;
				j++;
			} else if (list[i] < pattern[j]) {
				// List element smaller, move list pointer
				i++;
			} else {
				// List element larger, pattern element missing
				return false;
			}
		}

		// Verify all pattern elements were found
		return j == pattern.length;
	}

	/**
	 * Creates a deep copy of the provided database.
	 *
	 * This method duplicates the database such that modifications to the copied
	 * database do not affect the original one. Each transaction (which is a list of
	 * integers) is copied individually.
	 *
	 * @param database the original database of transactions.
	 * @return a new database that is a deep copy of the original.
	 */
	public List<int[]> copyDatabase(List<int[]> database) {
		List<int[]> copy = new ArrayList<>(database.size());

		for (int[] transaction : database) {
			copy.add(Arrays.copyOf(transaction, transaction.length));
		}

		return copy;
	}

	/**
	 * Updates the matrix of item pair supports by recalculating the supports based
	 * on the current database.
	 *
	 * This method clears the matrix and then iterates through the database,
	 * updating the support count for all 2-item patterns in each transaction.
	 *
	 * @param database the list of transactions to process.
	 */
	public void fillTheMatrix(List<int[]> database) {
		matrix.clear();
		for (int[] transaction : database) {
			// Generate and update all 2-item patterns in the matrix
			for (int i = 0; i < transaction.length; i++) {
				int item1 = transaction[i];

				// Iterate over the remaining items in the transaction to form 2-item patterns
				for (int j = i + 1; j < transaction.length; j++) {
					int item2 = transaction[j];

					// Increment the support count for this item pair in the matrix
					matrix.incrementCount(item1, item2);
				}
			}
		}
	}

	/**
	 * Print statistics about the latest execution of HMP-SA.
	 */
	public void printStats() {
		System.out.println("=============  HMP-SA - STATS =============");
		System.out.println(" Initial compression size: " + initialCompressionSize + " bits");
		System.out.println(" Final Compression size: " + currentCompressionSize + " bits");
		System.out.println(" Compression ratio: " + (currentCompressionSize / initialCompressionSize * 100));
		System.out.println(" Final codetable size: " + codetable.size());
		System.out.println();
		System.out.println(" Iteration count   : " + iterations);
		System.out.println();
		System.out.println(" Patterns found: " + codetable.size());
		System.out.println(" Memory : " + String.format("%.2f", maxMemoryUsage) + " MB");
		System.out.println(" Time   : " + elapsedTime + " ms");
		System.out.println("============================================");
	}
}
