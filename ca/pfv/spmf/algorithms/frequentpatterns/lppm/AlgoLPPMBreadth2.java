package ca.pfv.spmf.algorithms.frequentpatterns.lppm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/*
 * Copyright (c) 2019 Peng Yang, Philippe Fournier-Viger et al.

 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This is an implementation of the LPPM-breadth algorithm based on AprioriTID
 * It is fast,because it uses bit vector for representing TID SETS (transaction
 * id sets) The LPPM-breadth is to find all periodic time-intervals of patterns.
 * This version uses the SPM (share prefix) strategy LPPM-breadth is presented
 * in this paper: <br/>
 * <br/>
 * 
 * Fournier-Viger, P., Yang, P., Kiran, U., Ventura, S., Luna, J.M.(2019):
 * Mining Local Periodic Patterns in a Discrete Sequence.
 * 
 * @author Peng yang
 */

public class AlgoLPPMBreadth2 {
	/** the maximum periodicity threshold */
	private int maxPer;

	/** the minimum duration threshold */
	private int minDur;

	/** the maximal spillover of periods threshold */
	private int maxSoPer;

	/** object to write the output file */
	BufferedWriter writer = null;

	/** number of candidates */
	private long intersectionCount = 0;

	/**
	 * The patterns that are found // (if the user want to keep them into memory)
	 */
	protected Itemsets patterns = null;

	/** the largest timestamps of the database */
	private int largestTs;

	/**
	 * if selfIncrement == true --> considering that all transactions in this //
	 * database are occurring at a fixed time interval, // we have assigned
	 * timestamps for each transaction as increments of 1. // // if selfIncrement ==
	 * flase --> the file has the timestamps
	 */
	private boolean selfIncrement;

	/** number of LPPs found */
	private int itemsetCount;

	/** start time of the latest execution */
	private long startTimestamp;

	/** end time of the latest execution */
	private long endTime;

	/**
	 * Constructor
	 */
	public AlgoLPPMBreadth2() {
		// empty
	}

	/**
	 * Method to run the LPPM-breadth algorithm.
	 * 
	 * @param input         the path to an input file containing a transaction
	 *                      database.
	 * @param output        the output file path for saving the result (if null, the
	 *                      result will be returned by the method instead of being
	 *                      saved).
	 * @param maxPer        the maximum periodicity threshold
	 * @param minDur        the minimum duration threshold
	 * @param maxSoPer      the maximum spillover of period threshold
	 * @param selfIncrement whether the database contains real timestamps
	 * @return the result if no output file path is provided.
	 * @throws IOException exception if error reading or writing files
	 */
	public Itemsets runAlgorithm(String input, String output, int maxPer, int minDur, int maxSoPer,
			boolean selfIncrement) throws IOException {

		// Reset the tool to assess the maximum memory usage (for statistics)
		MemoryLogger.getInstance().reset();

		this.maxPer = maxPer;
		this.minDur = minDur;
		this.maxSoPer = maxSoPer;
		this.selfIncrement = selfIncrement;

		intersectionCount = 0;

		// if the user want to keep the result into memory
		if (output == null) {
			writer = null;
			patterns = new Itemsets("Local Periodic Pattern");
		} else { // if the user want to save the result to a file
			patterns = null;
			writer = new BufferedWriter(new FileWriter(output));
		}

		// record itemCount
		itemsetCount = 0;

		// record the start time
		startTimestamp = System.currentTimeMillis();

		// (1) PREPROCESSING: scan the database to build the convertTimeStamps
		Map<Integer, BitSet> mapItemTS = convertTimeStamps(input);

		// (2) generate periodic frequent pattern of 1-pattern
		ArrayList<Integer> lpp1 = new ArrayList<>();

		Iterator<Map.Entry<Integer, BitSet>> it = mapItemTS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, BitSet> entry = it.next();
			if (!generatePattern(entry, 1)) {
				it.remove();
			} else {
				lpp1.add(entry.getKey());
			}
		}

		// sort itemsets of size 1 according to lexicographical order.
		Collections.sort(lpp1);

		// generaete candidates of size 2 by using SPM strategy:
		// key : prefix, value: the list of last item of candidates with same prefix
		LinkedHashMap<int[], ArrayList<Integer>> combinationMap = generateCandidate2(lpp1, mapItemTS);

		while (true) {

			combinationMap = generateCandidateK(combinationMap, mapItemTS);

			if (combinationMap.size() <= 0)
				break;

		}

		// close the output file if the result was saved to a file
		if (writer != null) {
			writer.close();
		}

		// we check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// record the end time for statistics
		endTime = System.currentTimeMillis();
		return patterns;

	}

	/**
	 * generate candidates of size k
	 * 
	 * @param combinationMap the map store LPPs of size k-1
	 * @param mapItemTS      the items' timestamps (binary vector)
	 * @return a linked hashmap of candidates where key = itemset, value = list of
	 *         items that are combined
	 * @throws IOException if error while reading or writing to a file
	 */
	@SuppressWarnings("serial")
	private LinkedHashMap<int[], ArrayList<Integer>> generateCandidateK(
			LinkedHashMap<int[], ArrayList<Integer>> combinationMap, Map<Integer, BitSet> mapItemTS)
			throws IOException {
		LinkedHashMap<int[], ArrayList<Integer>> newCombinationMap = new LinkedHashMap<>();

		for (Map.Entry<int[], ArrayList<Integer>> entry : combinationMap.entrySet()) {
			int len = entry.getValue().size();
			if (len <= 1) {
				continue;
			}

			if (len == 2) {
				int itemI = entry.getValue().get(0);
				int itemJ = entry.getValue().get(1);

				BitSet bitSetIJ = (BitSet) mapItemTS.get(itemI).clone();
				bitSetIJ.and(mapItemTS.get(itemJ));
				intersectionCount++;

				int[] prefix = new int[entry.getKey().length + 2];
				for (int m = 0; m < entry.getKey().length; m++) {
					prefix[m] = entry.getKey()[m];
					bitSetIJ.and(mapItemTS.get(prefix[m]));
					intersectionCount++;
				}

				ArrayList<int[]> timeIntervals = bitset2intervals(bitSetIJ);
				if (timeIntervals.size() > 0) {

					prefix[entry.getKey().length] = itemI;
					prefix[entry.getKey().length + 1] = itemJ;
					save(prefix, timeIntervals, prefix.length);
				}
				continue;
			}

			int[] prefix = new int[entry.getKey().length + 2];
			BitSet bitSetPrefix = (BitSet) mapItemTS.get(entry.getKey()[0]).clone();
			prefix[0] = entry.getKey()[0];
			for (int m = 1; m < entry.getKey().length; m++) {
				prefix[m] = entry.getKey()[m];
				bitSetPrefix.and(mapItemTS.get(entry.getKey()[m]));
				intersectionCount++;
			}
			for (int i = 0; i < len - 1; i++) {

				int itemI = entry.getValue().get(i);
				BitSet bitSetI = (BitSet) mapItemTS.get(itemI).clone();
				bitSetI.and(bitSetPrefix);
				intersectionCount++;
				prefix[entry.getKey().length] = itemI;

				int[] head = new int[entry.getKey().length + 1];
				System.arraycopy(prefix, 0, head, 0, entry.getKey().length + 1);

				for (int j = i + 1; j < len; j++) {
					int itemJ = entry.getValue().get(j);
					BitSet bitSetIJ = (BitSet) mapItemTS.get(itemJ).clone();
					bitSetIJ.and(bitSetI);
					intersectionCount++;

					ArrayList<int[]> timeIntervals = bitset2intervals(bitSetIJ);
					if (timeIntervals.size() > 0) {

						prefix[entry.getKey().length + 1] = itemJ;

						save(prefix.clone(), timeIntervals, prefix.length);
//                        patterns.addItemset(new Itemset(prefix.clone(),timeIntervals),prefix.length);

						if (newCombinationMap.containsKey(head)) {
							newCombinationMap.get(head).add(itemJ);
						} else {
							newCombinationMap.put(head, new ArrayList<Integer>() {
								{
									add(itemJ);
								}
							});
						}
					}
				}
			}
		}

		// check memory usage
		MemoryLogger.getInstance().checkMemory();
		return newCombinationMap;
	}

	/**
	 * Generate candidates of size 2
	 * 
	 * @param lpp1      LPPs of size 1
	 * @param mapItemTS their vertical binary tiemstamps
	 * @return a linked hash map of an itemset as key and items that can be combined
	 *         as value
	 * @throws IOException if error while reading or writing to file
	 */
	@SuppressWarnings("serial")
	private LinkedHashMap<int[], ArrayList<Integer>> generateCandidate2(ArrayList<Integer> lpp1,
			Map<Integer, BitSet> mapItemTS) throws IOException {
		LinkedHashMap<int[], ArrayList<Integer>> newConbinationMap = new LinkedHashMap<>();
		for (int i = 0; i < lpp1.size() - 1; i++) {
			int itemI = lpp1.get(i);
			BitSet bitSetI = mapItemTS.get(itemI);
			int[] head = new int[] { itemI };

			for (int j = i + 1; j < lpp1.size(); j++) {
				int itemJ = lpp1.get(j);

				BitSet bitSetIJ = (BitSet) mapItemTS.get(itemJ).clone();
				bitSetIJ.and(bitSetI);
				intersectionCount++;

				ArrayList<int[]> timeIntervals = bitset2intervals(bitSetIJ);
				if (timeIntervals.size() > 0) {

					int[] itemName = new int[] { itemI, itemJ };
					save(itemName, timeIntervals, 2);
//                    patterns.addItemset(new Itemset(itemName,timeIntervals),2);

					if (!newConbinationMap.containsKey(head)) {
						newConbinationMap.put(head, new ArrayList<Integer>() {
							{
								add(itemJ);
							}
						});
					} else {
						newConbinationMap.get(head).add(itemJ);
					}
				}
			}
		}
		// check memory usage
		MemoryLogger.getInstance().checkMemory();
		return newConbinationMap;
	}

	/**
	 * Convert an itemset's timestamps to time-intervals
	 * 
	 * @param bitSet the bit verctor (timestamps)
	 * @return the list of time intervals
	 */
	private ArrayList<int[]> bitset2intervals(BitSet bitSet) {
		ArrayList<int[]> timeIntervals = new ArrayList<>();
		int left = -1;
		int soPer = maxSoPer;
		int preTS = bitSet.nextSetBit(1);
		int ts = bitSet.nextSetBit(preTS + 1);
		while (ts > 0) {

			// find left endpoint -- left
			if (ts - preTS <= maxPer && left == -1) {
				left = preTS;
				soPer = maxSoPer;
			}

			// find right endpoint -- preTS
			if (left != -1) {
				// find right endpoint
				soPer = Math.max(0, soPer + ts - preTS - maxPer);
				if (soPer > maxSoPer) {
					// we should check its duration
					if (preTS - left >= minDur) {
						timeIntervals.add(new int[] { left, preTS });
					}
					left = -1;
				}
			}
			preTS = ts;
			ts = bitSet.nextSetBit(preTS + 1);
		}

		// add final time point
		if (left != -1) {
			soPer = Math.max(0, soPer + largestTs - preTS - maxPer);
			if (soPer > maxSoPer) {
				if (preTS - left >= minDur) {
					timeIntervals.add(new int[] { left, preTS });
				}
			} else {
				if (largestTs - left >= minDur) {
					timeIntervals.add(new int[] { left, largestTs });
				}
			}
		}
		return timeIntervals;
	}

	/**
	 * Method to check whether an item can be an LPP of size 1
	 * 
	 * @param entry a map entry where key = item and value = bitset of timestamps
	 * @param k     the size of the itemset
	 * @return true if it is an LPP, otherwise, false
	 * @throws IOException if error while reading or writing to file
	 */
	private boolean generatePattern(Map.Entry<Integer, BitSet> entry, int k) throws IOException {
		ArrayList<int[]> timeIntervals = new ArrayList<>();
		int left = -1;
		BitSet bitSet = entry.getValue();
		int soPer = maxSoPer;

		int preTS = bitSet.nextSetBit(1);
		int ts = bitSet.nextSetBit(preTS + 1);
		while (ts > 0) {
			// find left endpoint
			if (ts - preTS <= maxPer && left == -1) {
				left = preTS;
				soPer = maxSoPer;
			}

			if (left != -1) {
				// find right endpoint
				soPer = Math.max(0, soPer + ts - preTS - maxPer);
				if (soPer > maxSoPer) {
					// we should check its duration
					if (preTS - left >= minDur) {
						timeIntervals.add(new int[] { left, preTS });
					} else {
						// if the duration donot satisfy the minDur threshold, then we will clear the
						// timestamps in this time-interval
						bitSet.clear(left, preTS + 1);
					}
					left = -1;
				}

			} else {
				// curent time do not satisfy ,hence we clear it
				bitSet.clear(preTS);
			}

			preTS = ts;
			ts = bitSet.nextSetBit(preTS + 1);
		}

		// add final time point
		if (left != -1) {
			soPer = Math.max(0, soPer + largestTs - preTS - maxPer);
			if (soPer > maxSoPer) {
				if (preTS - left >= minDur) {
					timeIntervals.add(new int[] { left, preTS });
				} else {
					bitSet.clear(left, preTS + 1);
				}
			} else {
				if (largestTs - left >= minDur) {
					timeIntervals.add(new int[] { left, largestTs });
				} else {
					bitSet.clear(left, largestTs + 1);
				}
			}
		}

		if (timeIntervals.size() > 0) {
			save(new int[] { entry.getKey() }, timeIntervals, k);
//            patterns.addItemset(new Itemset(entry.getKey(),timeIntervals),k);
			// refresh the data
			entry.setValue(bitSet);
			return true;
		}
		return false;
	}

	/**
	 * Convert transaction database to vertical database
	 * 
	 * @param input the path to a transaction database file
	 * @return the vertical database as a map where key = item and value = bitset of
	 *         timestamps
	 * @throws IOException if error while reading the file
	 */
	private Map<Integer, BitSet> convertTimeStamps(String input) throws IOException {
		// read the file
		BufferedReader reader = new BufferedReader(new FileReader(input));
		String line;
		Map<Integer, BitSet> mapItemTS = new HashMap<Integer, BitSet>();

		if (selfIncrement) {
			int ts = 1;
			while (((line = reader.readLine()) != null)) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				String[] lineSplited = line.split(" ");

				for (String itemString : lineSplited) {
					Integer itemName = Integer.parseInt(itemString);

					if (!mapItemTS.containsKey(itemName)) {
						mapItemTS.put(itemName, new BitSet());
					}
					mapItemTS.get(itemName).set(ts);
				}
				ts++;
			}
			largestTs = ts - 1;
		} else {
			int ts = 0;
			while (((line = reader.readLine()) != null)) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] lineSplited = line.split("\\|");
				String[] lineItems = lineSplited[0].split(" ");
				ts = Integer.parseInt(lineSplited[1]);
				for (String itemString : lineItems) {
					Integer itemName = Integer.parseInt(itemString);

					if (!mapItemTS.containsKey(itemName)) {
						mapItemTS.put(itemName, new BitSet());
					}
					mapItemTS.get(itemName).set(ts);
				}
			}
			largestTs = ts;
		}

		reader.close();
		return mapItemTS;

	}

	/**
	 * Save an LPP to the memory or an output file
	 * 
	 * @param items         the tiems of this LPP
	 * @param timeIntervals the periodic time-intervals
	 * @param k             the number of items
	 * @throws IOException if error while writing to the output file
	 */
	private void save(int[] items, ArrayList<int[]> timeIntervals, int k) throws IOException {
		itemsetCount++;
		if (writer == null) {
			patterns.addItemset(new Itemset(items, timeIntervals), k);
		} else {
			// if the result should be saved to a file
			// write it to the output file
			StringBuilder buffer = new StringBuilder();
			for (int item : items) {
				buffer.append(item);
				buffer.append(" ");
			}
			// Then, write the time-intervals
			buffer.append("#TIME-INTERVALS: ");
			for (int[] timeInterval : timeIntervals) {
				buffer.append("[");
				buffer.append(timeInterval[0]);
				buffer.append(",");
				buffer.append(timeInterval[1]);
				buffer.append("]   ");
			}
			writer.write(buffer.toString());
			writer.newLine();
		}
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  LPPM_breadth(SPM) - STATS =============");
		long temps = endTime - startTimestamp;

		System.out.println(" Total time ~ " + temps + " ms");
		System.out.println(" Itemsets count : " + itemsetCount);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Intersection count : " + intersectionCount);
		System.out.println("===================================================");
	}

}
