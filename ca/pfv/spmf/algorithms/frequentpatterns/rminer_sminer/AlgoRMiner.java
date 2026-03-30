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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
/* This file is copyright (c) 2025 Push Sra
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
 * This class implements the R-Miner algorithm for high utility itemset mining.
 * The algorithm uses a residue-based approach to identify high utility
 * itemsets.
 * 
 * @author Pushp Sra et al.
 */
public class AlgoRMiner {

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
    
    /** Maps itemsets to precomputed joined ResidueMaps */
    Map<Set<Integer>, ResidueMap> mapItemToResidueMapCombinations = null;
    
    /** Set of discovered high utility itemsets */
    Set<Set<Integer>> highUtilityItemsets = null;
    
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

	/** Constructor */
	public AlgoRMiner() {

	}

	/**
	 * This method runs the R-Miner algorithm to find high utility itemsets.
	 * 
	 * @param input      the path to the input file
	 * @param output     the path to the output file
	 * @param minUtility the minimum utility threshold
	 * @throws IOException if an error occurs while reading/writing files
	 */
	public void runAlgorithm(String input, String output, int minUtility) throws IOException {
		maxMemory = 0;
		totalMemory = 0;
		currentLength = 0;
		startTimestamp = System.currentTimeMillis();
		writer = new BufferedWriter(new FileWriter(output));
		BufferedReader myInput = null;
		int tid = 0;
		huiCount = 0;
		iterationCount = 0;
		joinCount = 0;
		maxUtility = 0;
		pushCount = 0;
		mapItemToResidueMap = new LinkedHashMap<>();
		bitVectorTID = new LinkedHashMap<>();
		mapItemToResidueMapCombinations = new LinkedHashMap<>();
		highUtilityItemsets = new HashSet<>();

		String thisLine;
		MemoryLogger.getInstance().reset();

		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));

			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue; // Skip empty lines or comments
				}

				String split[] = thisLine.split(":");
				String items[] = split[0].split(" ");
				String utilityValues[] = split[2].split(" ");

				tid += 1; // Increment the transaction ID counter
				for (int i = 0; i < items.length; i++) {
					Integer t = Integer.parseInt(items[i]);
					Set<Integer> item = Set.of(t);

					Integer ul_val = Integer.parseInt(utilityValues[i]);
					if (!mapItemToResidueMap.containsKey(item)) { // If itemset is not already in the map
						ResidueMap rMap = new ResidueMap(); // Create a new ResidueMap
						rMap.putItem(item);
						rMap.addToMap(tid, ul_val); // Add transaction ID and utility value to ResidueMap
						BitSet bs1 = new BitSet();
						bs1.set(tid);
						bitVectorTID.put(item, bs1); // Store BitSet in the map
						mapItemToResidueMap.put(item, rMap); // Store ResidueMap in the map

						// Check if the current itemset is a high utility itemset
						if (rMap.totUtil >= minUtility) {
							saveItemset(rMap);
						}
					} else {
						// If itemset already exists in the map
						ResidueMap rMap = mapItemToResidueMap.get(item);
						BitSet bs1 = bitVectorTID.get(item);
						bs1.set(tid); // Update the BitSet with the current transaction ID
						rMap.addToMap(tid, ul_val); // Add the utility value to the existing ResidueMap

						if (rMap.totUtil >= minUtility) {
							saveItemset(rMap);
						}
						if (rMap.totUtil >= maxUtility) {
							maxUtility = rMap.totUtil; // Update maximum utility if necessary
						}
					}
				}
			}

			// Sort the residue maps by total utility
			List<Map.Entry<Set<Integer>, ResidueMap>> entries = new ArrayList<>(mapItemToResidueMap.entrySet());
			Collections.sort(entries, Comparator.comparingLong(a -> a.getValue().totUtil));

			Map<Set<Integer>, ResidueMap> sortedMap = new LinkedHashMap<>();
			for (Entry<Set<Integer>, ResidueMap> entry : entries) {
				sortedMap.put(entry.getKey(), entry.getValue());
			}

			int i = 0;
			Set<Set<Integer>> keys = sortedMap.keySet();
			for (Set<Integer> key : keys) {
				ResidueMap rMap = sortedMap.get(key);
//				sSystem.out.println(rMap.itemSet + ":" + rMap.totUtil);
//				System.out.println(rMap.tidUtil);

				recursivePush(rMap, sortedMap, minUtility, i++, sortedMap.size());
			}

			writer.close();

			MemoryLogger.getInstance().checkMemory();
			totalMemory = MemoryLogger.getInstance().getMaxMemory();
			endTimestamp = System.currentTimeMillis();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
	}

	/**
	 * Save itemset
	 * 
	 * @param rMap residue map
	 * @throws IOException if exception occurs
	 */
	private void saveItemset(ResidueMap rMap) throws IOException {
		boolean wasNotAddedBefore = highUtilityItemsets.add(rMap.itemSet);

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

	/** Method to compare utilities */
	int compareUtilities(long util1, long util2) {
		return (int) (util1 - util2);
	}

	/** Method to check memory usage */
	double checkMemory() {
		return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
	}

	/**
	 * Recursive method to perform the join operations and search for high utility
	 * itemsets.
	 * 
	 * @throws IOException
	 */
	void recursivePush(ResidueMap inputMap, Map<Set<Integer>, ResidueMap> mapItemTorap, int minUtility,
			int currentIndex, int extendIndexCount) throws IOException {
		if (currentIndex > extendIndexCount) {
			return;
		}

		Set<Set<Integer>> setKeys = mapItemTorap.keySet();
		List<Set<Integer>> listKeys = new ArrayList<>(setKeys);
		ListIterator<Set<Integer>> extendIterator = listKeys.listIterator(listKeys.size());

		while (extendIterator.hasPrevious()) {
			Set<Integer> kps = extendIterator.previous();

			if (mapItemTorap.get(kps).totUtil >= (minUtility - inputMap.totUtil)) {
				extendIndexCount--;

				ResidueMap newJoin = computeBitJoin(inputMap, mapItemTorap.get(kps));
				if (newJoin != null) {
					joinCount++;

					if (newJoin.totUtil >= minUtility) {
						saveItemset(newJoin);
					}

					ListIterator<Set<Integer>> itrj = listKeys.listIterator(currentIndex);
					int itrjCounter = currentIndex;

					while (itrj.hasPrevious() && currentIndex <= extendIndexCount) {
						itrjCounter--;
						Set<Integer> kps2 = itrj.previous();

						if (mapItemTorap.get(kps2).totUtil >= (minUtility - newJoin.totUtil)) {
							ResidueMap newJoin2 = computeBitJoin(newJoin, mapItemTorap.get(kps2));

							if (newJoin2 != null) {
								joinCount++;
								if (newJoin2.totUtil >= minUtility) {
									saveItemset(newJoin2);
								}
								recursivePush(newJoin2, mapItemTorap, minUtility, itrjCounter, extendIndexCount);
							}
						} else {
							break;
						}
					}
				}
			} else {
				break;
			}
		}
	}

	/** Method to compute the join of two residue maps */
	ResidueMap computeBitJoin(ResidueMap inputMap1, ResidueMap inputMap2) {
		Set<Integer> newItemset = new HashSet<>();
		newItemset.addAll(inputMap1.itemSet);
		newItemset.addAll(inputMap2.itemSet);

		if (newItemset.size() < inputMap1.itemSet.size() + inputMap2.itemSet.size()) {
			return null;
		}

		if (mapItemToResidueMapCombinations.containsKey(newItemset)) {
			return mapItemToResidueMapCombinations.get(newItemset);
		}

		ResidueMap rMap = new ResidueMap();
		rMap.putItem(newItemset);
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
					rMap.addToMap(i, (list1.get(i) + list2.get(i)));
				}

				if (!mapItemToResidueMapCombinations.containsKey(newItemset)) {
					mapItemToResidueMapCombinations.put(newItemset, rMap);
					bitVectorTID.put(newItemset, temp);
				}
				return rMap;
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
		System.out.println("=============  RMiner ALGORITHM 2.63 - STATS =============");
		System.out.println("High utility itemsets: " + huiCount);
		System.out.println("Join Count: " + joinCount);
		System.out.println("Execution Time: " + (endTimestamp - startTimestamp) + "ms");
		System.out.println("Memory ~ " + totalMemory + " MB");
		System.out.println("===================================================");
	}
}