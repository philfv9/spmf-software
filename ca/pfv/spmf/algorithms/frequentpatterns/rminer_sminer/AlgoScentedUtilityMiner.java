package ca.pfv.spmf.algorithms.frequentpatterns.rminer_sminer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) Pushp Sra et al.
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * This class implements the Scented Utility miner algorithm for high utility
 * itemset mining. The algorithm uses a residue-based approach to identify high
 * utility itemsets. See the original paper for details.
 * 
 * @author Pushp Sra et al.
 */
public class AlgoScentedUtilityMiner {

    /** Timestamp at start of algorithm execution */
    public long startTimestamp = 0;

    /** Timestamp at end of algorithm execution */
    public long endTimestamp = 0;

    /** Maximum memory usage observed */
    public double maxMemory = 0;

    /** Total memory used */
    public double totalMemory = 0;

    /** Number of high utility itemsets discovered */
    public int huiCount = 0;

    /** Current length of itemset being processed */
    public int currentLength = 0;

    /** Writer to output high utility itemsets */
    BufferedWriter writer = null;

    /** Maps singleton itemsets to their ResidueMap */
    Map<Set<Integer>, ResidueMap> mapItemToResidueMap = null;

    /** Maps singleton itemsets to their transaction BitSets */
    Map<Set<Integer>, BitSet> bitVectorTID = null;

    /** Maps itemsets to their Transaction Weighted Utility (TWU) */
    Map<Set<Integer>, Integer> mapItemToTWU = null;

    /** Maps itemsets to their reinduction count */
    Map<Set<Integer>, Integer> mapItemToReinductionCount = null;

    /** Maps itemsets to precomputed joined ResidueMaps */
    Map<Set<Integer>, ResidueMap> mapItemToResidueMapCombinations = null;

    /** Set of discovered high utility itemsets */
    Set<Set<Integer>> HUISet = null;

    /** Iteration counter */
    int iterationCount = 0;

    /** Maximum number of iterations allowed */
    int iterationLimit;

    /** Counter for number of joins performed */
    int joinCount = 0;

    /** Maximum utility observed */
    long maxUtility = 0;

    /** Counter for number of successful pushes */
    int pushCount = 0;

    /** Minimum number of occurrences allowed */
    int minOccurrences = 0;

    /** Constructor */
	public AlgoScentedUtilityMiner() {
	}

	/**
     * Executes the Scented Utility Miner algorithm.
     * 
     * @param input input file path
     * @param output output file path
     * @param minUtility minimum utility threshold
     * @param maxReinductionCount maximum reinduction count allowed per itemset
     * @return database minimum utility observed
     * @throws IOException if error occurs during file processing
     */
	public long runAlgorithm(String input, String output, long minUtility, int maxReinductionCount)
			throws IOException {
		MemoryLogger.getInstance().reset();

        // Minimum utility threshold for database updates
        long dbMinUtility = Long.MAX_VALUE; 
        
		maxMemory = 0;
		totalMemory = 0;
		huiCount = 0;
		currentLength = 0;
		mapItemToResidueMap = new LinkedHashMap<>();
		bitVectorTID = new LinkedHashMap<>();
		mapItemToTWU = new HashMap<>();
		mapItemToReinductionCount = new HashMap<>();
		mapItemToResidueMapCombinations = new LinkedHashMap<>();
		HUISet = new HashSet<>();
		iterationCount = 0;
		joinCount = 0;
		maxUtility = 0;
		pushCount = 0;
		minOccurrences = 0;

		startTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();
		writer = new BufferedWriter(new FileWriter(output));
		BufferedReader myInput = null;
		int tid = 0;
		String thisLine;

		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				String[] split = thisLine.split(":");
				String[] items = split[0].split(" ");
				String[] utilityValues = split[2].split(" ");
				int transactionUtility = Integer.parseInt(split[1]);

				tid += 1;
				if (tid % 1 == 0) {
					if (mapItemToReinductionCount != null) {
						for (Map.Entry<Set<Integer>, Integer> mapElement : mapItemToReinductionCount.entrySet()) {
							Set<Integer> key = mapElement.getKey();
							int value = mapElement.getValue() - 1;
							mapItemToReinductionCount.put(key, value);
						}
					}
				}
				for (int i = 0; i < items.length; i++) {
					Integer item = Integer.parseInt(items[i]);
					Set<Integer> itemSet = Set.of(item);
					Integer utilityValue = Integer.parseInt(utilityValues[i]);

					if (!mapItemToResidueMap.containsKey(itemSet)) {
						ResidueMap residueMap = new ResidueMap();
						residueMap.putItem(itemSet);
						residueMap.addToMap(tid, utilityValue);
						BitSet bitSet = new BitSet();
						bitSet.set(tid);
						bitVectorTID.put(itemSet, bitSet);
						mapItemToTWU.put(itemSet, transactionUtility);
						mapItemToReinductionCount.put(itemSet, maxReinductionCount);
						mapItemToResidueMap.put(itemSet, residueMap);

						if (residueMap.totUtil >= minUtility) {
							saveItemset(residueMap);
						}
					} else {
						ResidueMap residueMap = mapItemToResidueMap.get(itemSet);
						BitSet bitSet = bitVectorTID.get(itemSet);
						bitSet.set(tid);
						residueMap.addToMap(tid, utilityValue);
						int twu = mapItemToTWU.get(itemSet) + transactionUtility;
						mapItemToTWU.put(itemSet, twu);
						mapItemToReinductionCount.put(itemSet, maxReinductionCount);

						if (residueMap.totUtil >= minUtility) {
							saveItemset(residueMap);
						}
						if (residueMap.totUtil > maxUtility) {
							maxUtility = residueMap.totUtil;
						}
						if (residueMap.totUtil < dbMinUtility) {
							dbMinUtility = residueMap.totUtil;
						}
					}
				}
			}

			List<Map.Entry<Set<Integer>, ResidueMap>> entries = new ArrayList<>(mapItemToResidueMap.entrySet());
			Collections.sort(entries, new Comparator<Map.Entry<Set<Integer>, ResidueMap>>() {
				public int compare(Map.Entry<Set<Integer>, ResidueMap> a, Map.Entry<Set<Integer>, ResidueMap> b) {
					return compareUtilities(a.getValue().totUtil, b.getValue().totUtil);
				}
			});
			Map<Set<Integer>, ResidueMap> sortedMap = new LinkedHashMap<>();
			for (Entry<Set<Integer>, ResidueMap> entry : entries) {
				if (mapItemToTWU.get(entry.getKey()) >= minUtility
						&& mapItemToReinductionCount.get(entry.getKey()) >= minOccurrences) {
					sortedMap.put(entry.getKey(), entry.getValue());
				}
			}

			int i = 0;
			Set<Set<Integer>> keys2 = sortedMap.keySet();
			for (Set<Integer> key : keys2) {
				ResidueMap residueMap = sortedMap.get(key);
				exploreAndJoinItemsets(residueMap, sortedMap, minUtility, i++, sortedMap.size(), maxReinductionCount);
			}

			writer.close();
			
			MemoryLogger.getInstance().checkMemory();
			endTimestamp = System.currentTimeMillis();

			totalMemory = MemoryLogger.getInstance().getMaxMemory();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		return dbMinUtility;
	}
	
    /**
     * Save the itemset to output if not already recorded
     * @param rMap the ResidueMap to save
     * @throws IOException if write fails
     */
	private void saveItemset(ResidueMap rMap) throws IOException {
		boolean wasNotAddedBefore = HUISet.add(rMap.itemSet);

		if (wasNotAddedBefore) {
			// Write items
			for (Integer item : rMap.itemSet) {
				writer.write(item + " ");
			}
			// Write utility
			writer.write("#UTIL: " + rMap.totUtil);
			writer.newLine();
			huiCount++;
		}
	}


	/**
	 * Compares two utility values.
	 * 
	 * @param util1 the first utility value
	 * @param util2 the second utility value
	 * @return a negative integer, zero, or a positive integer if util1 is less than, equal to, or greater than util2
	 */
	int compareUtilities(long util1, long util2) {
		return Long.compare(util1, util2);
	}

	/**
	 * Explores itemsets starting from the given input and joins them recursively with others to generate candidate itemsets.
	 * 
	 * @param inputMap the input ResidueMap to explore
	 * @param sortedMap the sorted map of itemsets and their ResidueMaps
	 * @param minUtility the minimum utility threshold
	 * @param currentIndex the index of the current itemset
	 * @param outputIndex the last index to consider in join operations
	 * @param maxReinductionCount the maximum reinduction count allowed
	 * @throws IOException if writing the output fails
	 */
	void exploreAndJoinItemsets(ResidueMap inputMap, Map<Set<Integer>, ResidueMap> sortedMap, long minUtility,
			int currentIndex, int outputIndex, int maxReinductionCount) throws IOException {
		if (currentIndex > outputIndex) {
			return;
		}

		Set<Set<Integer>> setKeys = sortedMap.keySet();
		List<Set<Integer>> listKeys = new ArrayList<>(setKeys);
		ListIterator<Set<Integer>> iterator = listKeys.listIterator(listKeys.size());

		while (iterator.hasPrevious() && outputIndex > currentIndex) {
			outputIndex--;
			Set<Integer> key = iterator.previous();

			if (sortedMap.get(key).totUtil >= (minUtility - inputMap.totUtil)) {
				ResidueMap newJoin = computeBitJoin(inputMap, sortedMap.get(key), maxReinductionCount);
				if (newJoin != null) {
					joinCount++;
					if (newJoin.totUtil >= minUtility) {
						saveItemset(newJoin);
					}
					recursiveJoinAndEvaluate(newJoin, sortedMap, minUtility, currentIndex, maxReinductionCount);
				}
			} else {
				break;
			}
		}
	}

	/**
	 * Recursively joins and evaluates itemsets from a given ResidueMap.
	 * 
	 * @param newJoin the joined itemset to evaluate further
	 * @param sortedMap the sorted map of itemsets and ResidueMaps
	 * @param minUtility the minimum utility threshold
	 * @param currentIndex the current index in traversal
	 * @param maxReinductionCount the maximum number of allowed reinductions
	 * @throws IOException if writing the output fails
	 */
	void recursiveJoinAndEvaluate(ResidueMap newJoin, Map<Set<Integer>, ResidueMap> sortedMap, long minUtility,
			int currentIndex, int maxReinductionCount) throws IOException {
		Set<Set<Integer>> setKeys = sortedMap.keySet();
		List<Set<Integer>> listKeys = new ArrayList<>(setKeys);
		ListIterator<Set<Integer>> iterator = listKeys.listIterator(currentIndex);

		while (iterator.hasPrevious()) {
			Set<Integer> key = iterator.previous();
			if (sortedMap.get(key).totUtil >= (minUtility - newJoin.totUtil)) {
				ResidueMap newJoin2 = computeBitJoin(newJoin, sortedMap.get(key), maxReinductionCount);
				if (newJoin2 != null) {
					joinCount++;
					if (newJoin2.totUtil >= minUtility) {
						saveItemset(newJoin2);
					}
					recursiveJoinAndEvaluate(newJoin2, sortedMap, minUtility, currentIndex--, maxReinductionCount);
				}
			} else {
				break;
			}
		}
	}

	/**
	 * Computes the bitwise join of two ResidueMaps, combining their transaction utilities where they overlap.
	 * 
	 * @param inputMap1 the first ResidueMap
	 * @param inputMap2 the second ResidueMap
	 * @param maxReinductionCount the maximum reinduction count
	 * @return the joined ResidueMap if join is valid, otherwise null
	 */
	ResidueMap computeBitJoin(ResidueMap inputMap1, ResidueMap inputMap2, int maxReinductionCount) {
		Set<Integer> newItemSet = new HashSet<>();
		newItemSet.addAll(inputMap1.itemSet);
		newItemSet.addAll(inputMap2.itemSet);

		if (newItemSet.size() < inputMap1.itemSet.size() + inputMap2.itemSet.size()) {
			return null;
		}
		if (mapItemToResidueMapCombinations.containsKey(newItemSet)) {
			return mapItemToResidueMapCombinations.get(newItemSet);
		}

		ResidueMap residueMap = new ResidueMap();
		residueMap.putItem(newItemSet);
		BitSet temp, temp2 = null;
		if (bitVectorTID.get(inputMap1.itemSet).cardinality() != 0
				&& bitVectorTID.get(inputMap2.itemSet).cardinality() != 0) {
			temp2 = bitVectorTID.get(inputMap1.itemSet);
			temp = (BitSet) temp2.clone();
			temp.and(bitVectorTID.get(inputMap2.itemSet));

			if (temp.cardinality() != 0) {
				Map<Integer, Integer> list1 = inputMap1.tidUtil;
				Map<Integer, Integer> list2 = inputMap2.tidUtil;

				for (int i = temp.nextSetBit(0); i >= 0; i = temp.nextSetBit(i + 1)) {
					residueMap.addToMap(i, (list1.get(i) + list2.get(i)));
				}
				if (!mapItemToResidueMapCombinations.containsKey(newItemSet)) {
					mapItemToResidueMapCombinations.put(newItemSet, residueMap);
					bitVectorTID.put(newItemSet, temp);
				}
				return residueMap;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

    /**
     * Print statistics of execution
     */
	public void printStats() {
		System.out.println("=============  Scented Utility Miner ALGORITHM 2.63 - STATS =============");
		System.out.println("High utility itemsets: " + huiCount);
		System.out.println("Join Count: " + joinCount);
		System.out.println("Execution Time: " + (endTimestamp - startTimestamp) + "ms");
		System.out.println("Memory ~ " + totalMemory + " MB");
		System.out.println("===================================================");
	}
}
