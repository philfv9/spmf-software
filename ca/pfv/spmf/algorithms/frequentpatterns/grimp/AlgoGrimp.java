package ca.pfv.spmf.algorithms.frequentpatterns.grimp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/* Copyright (c) 2008-2025 M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger
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
 * This is an implementation of the GRIMP algorithm. This algorithm is described
 * in: <br/>
 * <br/>
 * 
 * GRIMP: A Genetic Algorithm for Compression-based Pattern Mining <br/>
 * <br/>
 * 
 * This is an optimized version that saves the result to a file or keep it into
 * memory if no output path is provided by the user to the runAlgorithm
 * method().
 *
 * @see Itemset
 * @author M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger
 */
public class AlgoGrimp {

	/** the start time of the last execution **/
	private long startTimestamp;

	/** the end time of the last emmxecution **/
	private long endTimeStamp;

	/** Number of patterns found (for statistics) **/
	private int numberOfPatternsFound;

	/** maximum memory usage (for statistics) */
	private double maxMemoryUsage;

	private int Pat1Count = 0;
	
	private int Pat2Count = 0;

	/** Random number generator **/
	private Random random = new Random();

	/**
	 * Constructor
	 */
	public AlgoGrimp() {
	}

	/**
	 * New runAlgorithm with debug flag
	 */
	public List<int[]> runAlgorithm(String databaseFilePath, String outputFilePath, int patternCount,
			int iterationCount, CrossoverVariant crossoverVariant, MutationVariant mutationVariant,
			int maxPatternLength, boolean debug) throws IOException {

		startTimestamp = System.currentTimeMillis();
		Pat1Count = 0;
		Pat2Count = 0;

		List<List<Integer>> database = readItemsetsFromFile(databaseFilePath);
		List<int[]> finalResult = runGRIMPAlgorithm(database, patternCount, iterationCount, crossoverVariant,
				mutationVariant, maxPatternLength, outputFilePath, debug);

		patternCount = finalResult.size();
		MemoryLogger.getInstance().checkMemory();
		maxMemoryUsage = MemoryLogger.getInstance().getMaxMemory();
		endTimeStamp = System.currentTimeMillis();
		return finalResult;
	}

	/**
	 * Print statistics about the latest execution of GRIMP.
	 */
	public void printStats() {
		System.out.println("=============  GRIMP - STATS =============");
		System.out.println(" Patterns found: " + numberOfPatternsFound);
		System.out.println(" Memory : " + String.format("%.2f", maxMemoryUsage) + " MB");
		System.out.println(" Time   : " + (endTimeStamp - startTimestamp) + " ms");
		System.out.println("============================================");
	}

	/**
	 * Run the GRIMP algorithm core logic
	 */
	private List<int[]> runGRIMPAlgorithm(List<List<Integer>> database, int patternCount, int iterationCount,
			CrossoverVariant crossoverVariant, MutationVariant mutationVariant, int maxPatternLength,
			String outputFilePath, boolean debug) {

		int minimumAcceptableSize = 2; // minimum acceptable resultant pattern size

		if (maxPatternLength < 3) {
			System.err.println("Error: Maximum pattern length (MTL) must be at least 3");
			return new ArrayList<>();
		}

		int numberOfMutationPoints = 2; // used in Multi Point Mutation only

		if (debug) {
			System.out.println("Crossover: " + crossoverVariant + ", Mutation: " + mutationVariant);
		}

		long startTime = System.currentTimeMillis();
		List<List<Integer>> subDatabase = new ArrayList<>(database);
		List<int[]> variantResult = new ArrayList<>();

		// Main GRIMP algorithm loop
		while (variantResult.size() < patternCount) {

			Map<Integer, Integer> itemFrequency = new HashMap<>();

			// Calculate the frequency of each unique item
			int totalWeight = 0;
			int longestItemSet = 0;
			for (List<Integer> transaction : subDatabase) {
				for (Integer item : transaction) {
					itemFrequency.put(item, itemFrequency.getOrDefault(item, 0) + 1);
					totalWeight++;
					if (transaction.size() > longestItemSet) {
						longestItemSet = transaction.size();
					}
				}

			}

			// Create the population
			List<Integer> population = new ArrayList<>(itemFrequency.size());
			for (Entry<Integer, Integer> entry : itemFrequency.entrySet()) {
				population.add(entry.getKey());
			}

			// ======= Compute weights and total weights once ============
			// Ensure that the total weight is greater than zero
			if (totalWeight == 0) {
				throw new IllegalArgumentException("Total weight must be greater than zero.");
			}

			// Compute population weights
			int[] populationWeights = new int[population.size()];
			int cumulativeWeight = 0;
			for (int i = 0; i < population.size(); i++) {
				int item = population.get(i);
				cumulativeWeight += itemFrequency.get(item);
				populationWeights[i] = cumulativeWeight;
			}
			// ==================================================================

			List<Integer> pattern1 = generateRandomItemset(longestItemSet, population, populationWeights, totalWeight);
			List<Integer> pattern2 = generateRandomItemset(longestItemSet, population, populationWeights, totalWeight);

			int[] fPat1 = new int[]{};
			int[]  fPat2 = new int[]{};

			int gP1C = 0;
			int gP2C = 0;

			for (int currentIteration = 1; currentIteration <= iterationCount; currentIteration++) {

				List<Integer> tPat1 = new ArrayList<>(pattern1);
				List<Integer> tPat2 = new ArrayList<>(pattern2);

				CrossoverResult crossoverResult = null;
				if (CrossoverVariant.MULTI.equals(crossoverVariant)) {
					crossoverResult = MPCrossoverOperator(tPat1, tPat2);
				} else if (CrossoverVariant.UNIFORM.equals(crossoverVariant)) {
					crossoverResult = uniformCrossoverAdaptiveSwap(tPat1, tPat2, 0.5);
				} else if (CrossoverVariant.SINGLE.equals(crossoverVariant)) {
					crossoverResult = SPcrossoverOperator(tPat1, tPat2);
				}
				tPat1 = crossoverResult.pattern1;
				tPat2 = crossoverResult.pattern2;

				if (MutationVariant.SINGLE.equals(mutationVariant)) {
					tPat1 = standardMutationOperator(tPat1, population);
					tPat2 = standardMutationOperator(tPat2, population);
				} else if (MutationVariant.MULTI.equals(mutationVariant)) {
					tPat1 = multiPointMutation(tPat1, numberOfMutationPoints, population, populationWeights,
							totalWeight);
					tPat2 = multiPointMutation(tPat2, numberOfMutationPoints, population, populationWeights,
							totalWeight);
				}
				
				int[] tPat1Array = sortAndRemoveDuplicateItems(tPat1);
				int[] tPat2Array = sortAndRemoveDuplicateItems(tPat2);

				List<List<Integer>> remainingDatabase = deleteItemset(subDatabase, tPat1Array, tPat2Array);
				if (Pat1Count > gP1C) {
					gP1C = Pat1Count;
					fPat1 = tPat1Array;
				}
				if (Pat2Count > gP2C) {
					gP2C = Pat2Count;
					fPat2 = tPat2Array;
				}

				int compressDatabaseSize = calculateSizeInBits(remainingDatabase);
				compressDatabaseSize = compressDatabaseSize + (tPat1.size() * Integer.SIZE)
						+ (tPat2.size() * Integer.SIZE);
			}

			subDatabase = deleteItemset(database, fPat1, fPat2);

			if (variantResult.size() < patternCount && fPat1.length >= minimumAcceptableSize
					&& fPat1.length <= maxPatternLength) {
				// Check if pattern already exists in variantResult
				if (!patternExistsInList(variantResult, fPat1)) {
					variantResult.add(fPat1);
				}
			}
			if (variantResult.size() < patternCount && fPat2.length >= minimumAcceptableSize
					&& fPat2.length <= maxPatternLength) {
				// Check if pattern already exists in variantResult
				if (!patternExistsInList(variantResult, fPat2)) {
					variantResult.add(fPat2);
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();

		int originalDatabaseSize = calculateSizeInBits(database);

		int compressSizeInBits2 = deleteAndCalculateSizeInBits2(database, variantResult);
		if (debug) {
			System.out.println("Compression ratio : " + (float) compressSizeInBits2 / originalDatabaseSize * 100);
			long endTime = System.currentTimeMillis();
			long elapsedTime = endTime - startTime;
			System.out.println("Time (s): " + (double) (elapsedTime) / 1000);
		}
		// Write results to file for this variant
		if (outputFilePath != null) {
			writeResultsToFile(outputFilePath, variantResult, database, crossoverVariant, mutationVariant, debug);
		}

		MemoryLogger.getInstance().checkMemory();

		numberOfPatternsFound = variantResult.size();
		return variantResult;
	}

	/**
	 * Sorts a list of integers in-place and removes duplicates efficiently.
	 * 
	 * @param list the list to process
	 */
	private int[] sortAndRemoveDuplicateItems(List<Integer> list) {
	    if (list == null || list.isEmpty()) {
	        return new int[0];
	    }

	    // Sort the list
	    Collections.sort(list);

	    // Count unique items
	    int uniqueCount = 1;
	    for (int i = 1; i < list.size(); i++) {
	        if (!list.get(i).equals(list.get(i - 1))) {
	            uniqueCount++;
	        }
	    }

	    // Fill the array with unique items
	    int[] result = new int[uniqueCount];
	    result[0] = list.get(0);
	    int index = 1;
	    for (int i = 1; i < list.size(); i++) {
	        if (!list.get(i).equals(list.get(i - 1))) {
	            result[index++] = list.get(i);
	        }
	    }

	    return result;
	}

	/**
	 * Deletes itemsets from a database and counts occurrences.
	 * 
	 * @param database the input sequence of transactions
	 * @param pattern1 the first pattern to delete
	 * @param pattern2 the second pattern to delete
	 * @return the modified database after pattern deletion
	 */
	private List<List<Integer>> deleteItemset(List<List<Integer>> database, int[] pattern1,
			int[] pattern2) {

		List<List<Integer>> modifiedDatabase = new ArrayList<>();
		int pat1Count = 0;
		int pat2Count = 0;

		for (List<Integer> transaction : database) {
			boolean containsPat1 = containsSorted(transaction, pattern1);
			boolean containsPat2 = containsSorted(transaction, pattern2);

			if (containsPat1 == false && containsPat2 == false) {
				modifiedDatabase.add(transaction);
			} else {
				List<Integer> modifiedTransaction = new ArrayList<>(transaction.size());
				int i = 0, j1 = 0, j2 = 0;

				while (i < transaction.size()) {
					int item = transaction.get(i);

					// Skip items from pattern1 if transaction contains the full pattern
					if (containsPat1 && j1 < pattern1.length && item == pattern1[j1]) {
						j1++;
						i++;
						continue;
					}

					// Skip items from pattern2 if transaction contains the full pattern
					if (containsPat2 && j2 < pattern2.length && item == pattern2[j2]) {
						j2++;
						i++;
						continue;
					}

					modifiedTransaction.add(item);
					i++;
				}

				// Add the modified transaction to the new sequence
				modifiedDatabase.add(modifiedTransaction);

				if (containsPat1) {
					pat1Count++;
				}
				if (containsPat2) {
					pat2Count++;
				}
			}
		}

		Pat1Count = pat1Count;
		Pat2Count = pat2Count;
		return modifiedDatabase;
	}

	/**
	 * Helper method: checks if sorted transaction contains sorted pattern using
	 * merge-like scan
	 * 
	 * @param transaction transaction sorted
	 * @param pattern     pattern sorted without duplicate items
	 * @return true if the transaction contains the pattern
	 */
	private boolean containsSorted(List<Integer> transaction, int[] pattern) {
		if (pattern.length == 0)
			return false;
		int i = 0, j = 0;
		while (i < transaction.size() && j < pattern.length) {
			if (transaction.get(i).equals(pattern[j])) {
				j++;
			} else if (transaction.get(i) > pattern[j]) {
				return false; // pattern element missing
			}
			i++;
		}
		return j == pattern.length;
	}

	/**
	 * Performs standard single-point mutation on a chromosome.
	 * 
	 * @param c        the chromosome to mutate
	 * @param allItems the set of all possible items for mutation
	 * @return the mutated chromosome
	 */
	private List<Integer> standardMutationOperator(List<Integer> c, List<Integer> population) {
		// Check if C or allItems is empty
		if (c.isEmpty() || population.isEmpty()) {
			return c;
		}
		// Generate a random index to select an item from C for mutation
		int randomIndex = random.nextInt(c.size());
		int itemToReplace = c.get(randomIndex);

		// Generate a new item randomly from allItems that is different from
		// itemToReplace
		int newItem;
		do {
			newItem = population.get(random.nextInt(population.size()));
		} while (newItem == itemToReplace);

		// Replace the selected item in C with the new item
		c.set(randomIndex, newItem);
		return c;
	}

	/**
	 * Performs single-point crossover between two parent solutions.
	 * 
	 * @param P1Sol the first parent solution
	 * @param P2Sol the second parent solution
	 * @return a list containing two offspring solutions
	 */
	private CrossoverResult SPcrossoverOperator(List<Integer> P1Sol, List<Integer> P2Sol) {
		// Get the maximum size of the smallest itemset among P1Sol and P2Sol
		int smallestSize = Math.min(P1Sol.size(), P2Sol.size());

		// Generate a random cutoff point within the range of 2 to the maximum size
		int cutoffPoint = random.nextInt(smallestSize - 1) + 2;

		// Perform crossover using the generated cutoff point
		return performSPCrossover(P1Sol, P2Sol, cutoffPoint);
	}

	/**
	 * Performs single-point crossover at a specific cutoff point.
	 * 
	 * @param P1Sol       the first parent solution
	 * @param P2Sol       the second parent solution
	 * @param cutoffPoint the crossover point
	 * @return a list containing two offspring solutions
	 */
	private CrossoverResult performSPCrossover(List<Integer> P1Sol, List<Integer> P2Sol, int cutoffPoint) {
		// Copy elements from P1Sol up to the cutoff point to child1
		List<Integer> child1 = new ArrayList<>(P1Sol.subList(0, cutoffPoint));
		// Copy elements from P2Sol after the cutoff point to child1
		child1.addAll(P2Sol.subList(cutoffPoint, P2Sol.size()));

		// Copy elements from P2Sol up to the cutoff point to child2
		List<Integer> child2 = new ArrayList<>(P2Sol.subList(0, cutoffPoint));
		// Copy elements from P1Sol after the cutoff point to child2
		child2.addAll(P1Sol.subList(cutoffPoint, P1Sol.size()));

		// Return the crossover result
		CrossoverResult crossoverResult = new CrossoverResult();
		crossoverResult.pattern1 = child1;
		crossoverResult.pattern2 = child2;
		return crossoverResult;
	}

	/**
	 * Calculates the total size in bits of a sequence.
	 * 
	 * @param sequence the sequence to calculate size for
	 * @return the total size in bits
	 */
	private static int calculateSizeInBits(List<List<Integer>> sequence) {
		int size = 0;
		for (List<Integer> transaction : sequence) {
			size += transaction.size() * Integer.SIZE; // Size of Integer in bits
		}
		return size;
	}

	/**
	 * Generates a random itemset based on item frequencies.
	 * 
	 * @param longestItemSet the maximum size of the itemset
	 * @param itemFrequency  the frequency map of items
	 * @return a randomly generated itemset
	 */
	public List<Integer> generateRandomItemset(int longestItemSet, List<Integer> population, int[] populationWeights,
			int totalWeight) {
		List<Integer> randomSet = new ArrayList<>();

		// System.out.println(longestItemSet);
		int randomItemsetSize = 2 + random.nextInt(Math.max(1, longestItemSet - 1)); // Random size between 2 and
																						// longestItemSet
		while (randomSet.size() < randomItemsetSize) {
			Integer randomNumber = getWeightedRandomItem(population, populationWeights, totalWeight);
			if (!randomSet.contains(randomNumber)) { // Ensure no repetition
				randomSet.add(randomNumber);
			}
		}
		return randomSet;
	}

	/**
	 * Selects a random item based on weighted probability.
	 * 
	 * @param itemFrequency the frequency map of items
	 * @return a randomly selected item based on frequency weights
	 * @throws IllegalArgumentException if total weight is zero
	 * @throws IllegalStateException    if no item is selected
	 */
	public Integer getWeightedRandomItem(List<Integer> population, int[] populationWeights, int totalWeight) {

		// Generate a random number between 0 and (totalWeight - 1)
		int randomNumber = random.nextInt(totalWeight);

		// Iterate over items to find the selected one based on weight
		for (int i = 0; i < populationWeights.length; i++) {
			if (randomNumber < populationWeights[i]) {
				return population.get(i);
			}
		}
		// Fallback: Should not occur unless no items are present
		throw new IllegalStateException("No item selected. Check the input data.");
	}

	/**
	 * Reads itemsets from a file and returns them as a sequence.
	 * 
	 * @param fileName the path to the input file
	 * @return a list of transactions (itemsets)
	 */
	public List<List<Integer>> readItemsetsFromFile(String fileName) {
		List<List<Integer>> database = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				List<Integer> transaction = new ArrayList<>();
				String[] items = line.split("\\s+"); // Split by whitespace
				for (String item : items) {
					transaction.add(Integer.valueOf(item));
				}

				// Sort the transaction
				Collections.sort(transaction); // ADDED BY PHILIPPE
				database.add(transaction);
			}
		} catch (IOException e) {
			System.err.println("Error reading file: " + e.getMessage());
		} catch (NumberFormatException e) {
			System.err.println("Error parsing integer: " + e.getMessage());
		}
		return database;
	}

	/**
	 * Class to store the result of a crossover
	 */
	private class CrossoverResult {
		List<Integer> pattern1;
		List<Integer> pattern2;
	}

	/**
	 * Performs multi-point crossover between two parent solutions.
	 * 
	 * @param P1Sol the first parent solution
	 * @param P2Sol the second parent solution
	 * @return a list containing two offspring solutions
	 */
	public CrossoverResult MPCrossoverOperator(List<Integer> P1Sol, List<Integer> P2Sol) {
		// Get the maximum size of the smallest itemset among P1Sol and P2Sol
		int smallestSize = Math.min(P1Sol.size(), P2Sol.size());

		if (smallestSize <= 3) {
			CrossoverResult crossoverResult = new CrossoverResult();
			crossoverResult.pattern1 = P1Sol;
			crossoverResult.pattern2 = P2Sol;
			return crossoverResult;
		} else {
			// Generate random crossover points within the range of 2 to the maximum size
			int[] crossoverPoints = generateCrossoverPoints2(smallestSize);

			// Perform crossover using the generated crossover points
			return performMPCrossover(P1Sol, P2Sol, crossoverPoints);
		}
	}

	private int[] generateCrossoverPoints2(int maxSize) {
		int[] crossoverPoints = new int[2];

		if (maxSize > 3) { // Ensure there's enough space for two distinct points
			int midPoint = maxSize / 2;
			// Generate the first crossover point between 2 and midPoint (inclusive)
			int firstPoint = 2 + random.nextInt(midPoint - 1);
			// Generate the second crossover point between midPoint (inclusive) and maxSize
			// - 1
			int secondPoint = midPoint + random.nextInt(maxSize - midPoint - 1);
			crossoverPoints[0] = firstPoint;
			crossoverPoints[1] = secondPoint;
			return crossoverPoints;
		} else {
			return null;
		}
	}

	private CrossoverResult performMPCrossover(List<Integer> P1Sol, List<Integer> P2Sol, int[] crossoverPoints)
			throws IllegalArgumentException {

		// Check for exactly two crossover points
		// System.out.println(crossoverPoints);
		if (crossoverPoints == null) {
			throw new IllegalArgumentException("Expecting two crossover points.");
		}

		// Initialize lists to store the crossover results
		List<Integer> child1 = new ArrayList<>(P1Sol.size());
		List<Integer> child2 = new ArrayList<>(P2Sol.size());

		// Variables to keep track of which parent is currently being used
		boolean useParent1 = true;
		int startIndex = 0;

		// Perform crossover using the generated crossover points
		for (int crossoverPoint : crossoverPoints) {
			List<Integer> currentParent = (useParent1) ? P1Sol : P2Sol;
			List<Integer> currentChild = (useParent1) ? child1 : child2;
			currentChild.addAll(currentParent.subList(startIndex, crossoverPoint));
			startIndex = crossoverPoint;
			useParent1 = !useParent1;
		}

		// Copy remaining elements from the last parent
		List<Integer> lastParent = (useParent1) ? P1Sol : P2Sol;
		List<Integer> lastChild = (useParent1) ? child1 : child2;
		lastChild.addAll(lastParent.subList(startIndex, lastParent.size()));

		// Add the crossover results to the list
		CrossoverResult result = new CrossoverResult();
		result.pattern1 = child1;
		result.pattern2 = child2;
		return result;
	}

	/**
	 * Calculates the size in bits after deleting patterns from a sequence.
	 * 
	 * @param database    the original sequence
	 * @param finalResult the patterns to delete
	 * @return the size in bits of the modified sequence
	 */
	public static int deleteAndCalculateSizeInBits(List<List<Integer>> database, List<List<Integer>> finalResult) {

		List<List<Integer>> modifiedDatabase = new ArrayList<>();

		// Process each transaction in database
		for (List<Integer> transaction : database) {
			List<Integer> modifiedTransaction = new ArrayList<>(transaction);

			// Iterate through transactions in finalResult
			for (List<Integer> pattern : finalResult) {
				// Check if all items in finalTransaction exist in modifiedTransaction
				if (modifiedTransaction.containsAll(pattern)) {
					// If all items match, remove the finalTransaction items from
					// modifiedTransaction
					modifiedTransaction.removeAll(pattern);
				}
			}
			// Add the modified transaction to the result
			modifiedDatabase.add(modifiedTransaction);
		}
		// Calculate the size in bits of the modified database
		int totalSizeInBits = 0;
		for (List<Integer> transaction : modifiedDatabase) {
			// Each integer is 32 bits
			totalSizeInBits += transaction.size() * Integer.SIZE;
		}
		return totalSizeInBits;
	}

	/**
	 * Calculates the size in bits with code table compression after deleting
	 * patterns.
	 * 
	 * @param database    the original sequence
	 * @param finalResult the patterns to delete
	 * @return the compressed size in bits including code table
	 */
	private int deleteAndCalculateSizeInBits2(List<List<Integer>> database, List<int[]> finalResult) {
		List<List<Integer>> modifiedDatabase = new ArrayList<>();
		Map<int[], Integer> patternCount = new HashMap<>();

		// Initialize pattern counts to zero
		for (int[] pattern : finalResult) {
			patternCount.put(pattern, 0);
		}
		// Process each transaction in database
		for (List<Integer> transaction : database) {
			List<Integer> modifiedTransaction = new ArrayList<>(transaction);
			
			for (int[] aPattern : finalResult) {
				if (containsSorted(modifiedTransaction, aPattern)) {
					// Increment the count for the pattern
					patternCount.put(aPattern, patternCount.get(aPattern) + 1);
					// Remove the finalTransaction items from modifiedTransaction
					removeAll(modifiedTransaction, aPattern);
				}
			}
			// Add the modified transaction to the result
			modifiedDatabase.add(modifiedTransaction);
		}
		// Calculate the size in bits of the modified sequence
		int totalSizeInBits = 0;
		for (List<Integer> transaction : modifiedDatabase) {
			// Each integer is 32 bits
			totalSizeInBits += transaction.size() * Integer.SIZE;
		}
		// Add the size of the transactions in finalResult multiplied by their counts
		for (Entry<int[], Integer> entry : patternCount.entrySet()) {
			int patternSize = 0;
			if (entry.getValue() != 0) {
				// patternSize = entry.getValue() * 3 + entry.getKey().size() * Integer.SIZE;
				int keySize = entry.getKey().length;
				// Calculate the bit size for entry.getValue() using logarithm base 2
				int valueBitSize = (int) Math.ceil(Math.log(keySize + 1) / Math.log(2));
				patternSize = valueBitSize + keySize * Integer.SIZE;
			}
			totalSizeInBits += patternSize;
		}
		return totalSizeInBits;
	}
	
	/**
	 * Removes all elements of the pattern from the transaction.
	 * Only works if the pattern is fully contained in the transaction.
	 *
	 * @param transaction the transaction to modify
	 * @param pattern the pattern to remove
	 */
	private static void removeAll(List<Integer> transaction, int[] pattern) {
	    int i = 0, j = 0;
	    while (i < transaction.size() && j < pattern.length) {
	        if (transaction.get(i) == pattern[j]) {
	            transaction.remove(i); // remove shifts the list, so do not increment i
	            j++;
	        } else {
	            i++;
	        }
	    }
	}

	/**
	 * Performs multi-point mutation on a chromosome.
	 * 
	 * @param C              the chromosome to mutate
	 * @param mutationPoints the number of mutation points
	 * @param itemFrequency  the frequency map of items for weighted selection
	 * @return the mutated chromosome
	 */
	private List<Integer> multiPointMutation(List<Integer> C, int mutationPoints, List<Integer> population,
			int[] populationWeights, int totalWeight) {
		if (mutationPoints <= 0 || C.isEmpty() || C.size() < mutationPoints) {
			return C;
		}
		Set<Integer> usedIndices = new HashSet<>();

		for (int i = 0; i < mutationPoints; i++) {
			int randomIndex;
			do {
				randomIndex = random.nextInt(C.size());
			} while (usedIndices.contains(randomIndex));
			usedIndices.add(randomIndex);
			int itemToReplace = C.get(randomIndex);
			// Generate a new item based on weighted random selection
			int newItem;
			do {
				newItem = getWeightedRandomItem(population, populationWeights, totalWeight);
			} while (newItem == itemToReplace);

			C.set(randomIndex, newItem);
		}
		return C;
	}

	/**
	 * Performs uniform crossover with adaptive swapping between two parents.
	 * 
	 * @param parent1       the first parent solution
	 * @param parent2       the second parent solution
	 * @param crossoverRate the probability of crossover for each position
	 * @return a list containing two offspring solutions
	 */
	private CrossoverResult uniformCrossoverAdaptiveSwap(List<Integer> parent1, List<Integer> parent2,
			double crossoverRate) {
		// Determine which is the shorter and which is the longer parent
		List<Integer> shorter = parent1.size() <= parent2.size() ? parent1 : parent2;
		List<Integer> longer = parent1.size() > parent2.size() ? parent1 : parent2;

		// Create a list of indices for the longer parent and shuffle them to ensure
		// unique random selection
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < longer.size(); i++) {
			indices.add(i);
		}
		Collections.shuffle(indices, random);
		// Perform swaps based on crossoverRate, only considering the size of the
		// shorter parent
		for (int i = 0; i < shorter.size(); i++) {
			if (random.nextDouble() < crossoverRate) {
				// Perform a swap between the shorter and a selected index from the longer
				int indexFromLonger = indices.get(i);
				Integer temp = shorter.get(i);
				shorter.set(i, longer.get(indexFromLonger));
				longer.set(indexFromLonger, temp);
			}
		}
		// Prepare the crossover result containing the updated parents
		CrossoverResult crossoverResult = new CrossoverResult();
		crossoverResult.pattern1 = parent1; // Clone parent1 to avoid external modifications
		crossoverResult.pattern2 = parent2; // Clone parent2 to avoid external modifications
		return crossoverResult;
	}

	/**
	 * Writes the discovered patterns to an output file. Modified: Now outputs each
	 * pattern as space-separated items followed by #SUP: and the count.
	 * 
	 * @param outputFile       the path to the output file
	 * @param patterns         the list of discovered patterns (List<List<Integer>>)
	 * @param originalSequence the original input (transactions)
	 * @param crossoverVariant
	 * @param mutationVariant
	 * @param append
	 * @param debug            true for printing extra info to console
	 */
	public void writeResultsToFile(String outputFile, List<int[]> patterns,
			List<List<Integer>> originalSequence, CrossoverVariant crossoverVariant, MutationVariant mutationVariant,
			boolean debug) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
			// Write only patterns in requested format
			for (int[] pattern : patterns) {
				int support = countPatternSupport(originalSequence, pattern);
				// Output as: 1 2 3 #SUP: 4
				for (int j = 0; j < pattern.length; j++) {
					writer.print(pattern[j]);
					if (j < pattern.length - 1)
						writer.print(" ");
				}
				writer.print(" #SUP: " + support);
				writer.println();
			}
			writer.flush();
		} catch (IOException e) {
			System.err.println("Error writing to output file: " + e.getMessage());
		}
		if (debug) {
			System.out.println("----------- DEBUG OUTPUT ------------");
			System.out.println("Patterns found:");
			int idx = 1;
			for (int[] pattern : patterns) {
				int support = countPatternSupport(originalSequence, pattern);
				System.out.print("Pattern " + idx + ": ");
				for (int i = 0; i < pattern.length; i++) {
					System.out.print(pattern[i]);
					if (i < pattern.length - 1)
						System.out.print(" ");
				}
				System.out.print(" #SUP: " + support);
				System.out.println();
				idx++;
			}
			System.out.println("Crossover operator: " + crossoverVariant);
			System.out.println("Mutation operator: " + mutationVariant);
			// you may add more debug details if available, e.g., compression/time stats
			System.out.println("-------------------------------------");
		}
	}

	/**
	 * Counts the support of a pattern in a list of transactions. A transaction
	 * supports a pattern if it contains all items of the pattern.
	 * 
	 * @param transactions the list of transactions (each transaction is a list of
	 *                     integers)
	 * @param pattern      the pattern to count (list of integers)
	 * @return the number of transactions that contain all items in the pattern
	 */
	private  int countPatternSupport(List<List<Integer>> transactions, int[] pattern) {
	    int count = 0;
	    for (List<Integer> transaction : transactions) {
	        if (containsSorted(transaction, pattern)) {
	            count++;
	        }
	    }
	    return count;
	}

	/**
	 * Checks if a pattern already exists in the list of patterns.
	 * 
	 * @param patternList the list of existing patterns
	 * @param pattern  the pattern to check for
	 * @return true if the pattern already exists, false otherwise
	 */
	private boolean patternExistsInList(List<int[]> patternList, int[] pattern) {
		for (int[] existingPattern : patternList) {
			if (arePatternsEqual(existingPattern, pattern)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if two sorted patterns are equal.
	 * 
	 * @param pattern1 the first pattern (sorted)
	 * @param pattern2 the second pattern (sorted)
	 * @return true if patterns are equal, false otherwise
	 */
	private boolean arePatternsEqual(int[] pattern1, int[] pattern2) {
		if (pattern1.length != pattern2.length) {
			return false;
		}
		for (int i = 0; i < pattern1.length; i++) {
			if (pattern1[i] != pattern2[i]) {
				return false;
			}
		}
		return true;
	}
}
