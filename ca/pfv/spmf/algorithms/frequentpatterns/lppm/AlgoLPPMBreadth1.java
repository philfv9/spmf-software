package ca.pfv.spmf.algorithms.frequentpatterns.lppm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
 * This version do not use SPM strategy, hence it will compare prefix to
 * generate larger patterns. LPPM-breadth is presented in this paper: <br/>
 * <br/>
 * 
 * Fournier-Viger, P., Yang, P., Kiran, U., Ventura, S., Luna, J.M.(2019):
 * Mining Local Periodic Patterns in a Discrete Sequence.
 * 
 * @author Peng yang
 */

public class AlgoLPPMBreadth1 {

	/** the maximum periodicity threshold */
	private int maxPer;

	/** the minimum duration threshold */
	private int minDur;

	/** the maximal spillover of periods threshold */
	private int maxSoPer;

	/** object to write the output file */
	BufferedWriter writer = null;

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
	public AlgoLPPMBreadth1() {
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

		// (2) generate periodic pattern of 1-pattern

		List<int[]> level = new ArrayList<>();
		Iterator<Map.Entry<Integer, BitSet>> it = mapItemTS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, BitSet> entry = it.next();
			if (!generatePattern(entry)) {
				it.remove();
			} else {
				level.add(new int[] { entry.getKey() });
			}
		}

		// sort itemsets of size 1 according to lexicographical order.
//        Collections.sort(LPP1);
		Collections.sort(level, new Comparator<int[]>() {
			public int compare(int[] arg0, int[] arg1) {
				return arg0[0] - arg1[0];
			}
		});

		while (!level.isEmpty()) {

			// we check the memory usage
			MemoryLogger.getInstance().checkMemory();
			// We build the level k+1 with all the candidates that have
			// a support higher than the minsup threshold.
			level = generateCandidateSizeK(level, mapItemTS);

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
	 * Method to generate itemsets of size k from periodic frequent itemsets of size
	 * K-1.
	 * 
	 * @param levelK1   LPPs of size k-1
	 * @param mapItemTS map of items to timestamps
	 * @return list of itemsets of size k
	 */
	protected List<int[]> generateCandidateSizeK(List<int[]> levelK1, Map<Integer, BitSet> mapItemTS)
			throws IOException {
		// create a variable to store candidates
		List<int[]> candidates = new ArrayList<int[]>();

		// For each itemset I1 and I2 of level k-1
		loop1: for (int i = 0; i < levelK1.size(); i++) {
			int[] itemset1 = levelK1.get(i);
			loop2: for (int j = i + 1; j < levelK1.size(); j++) {
				int[] itemset2 = levelK1.get(j);

				// we compare items of itemset1 and itemset2.
				// If they have all the same k-1 items and the last item of
				// itemset1 is smaller than
				// the last item of itemset2, we will combine them to generate a
				// candidate
				for (int k = 0; k < itemset1.length; k++) {
					// if they are the last items
					if (k == itemset1.length - 1) {
						// the one from itemset1 should be smaller (lexical
						// order)
						// and different from the one of itemset2
						if (itemset1[k] >= itemset2[k]) {
							continue loop1;
						}
					}
					// if they are not the last items, and
					else if (itemset1[k] < itemset2[k]) {
						continue loop2; // we continue searching
					} else if (itemset1[k] > itemset2[k]) {
						continue loop1; // we stop searching: because of lexical
						// order
					}
				}

				// NOW COMBINE ITEMSET 1 AND ITEMSET 2
				// and operation for all of items in itemset1
				BitSet bitSetIJ = (BitSet) mapItemTS.get(itemset1[0]).clone();
				for (int k = 1; k < itemset1.length; k++) {
					bitSetIJ.and(mapItemTS.get(itemset1[k]));
				}

				// and operation for the last item of itemset2
				bitSetIJ.and(mapItemTS.get(itemset2[itemset2.length - 1]));

				ArrayList<int[]> timeIntervals = bitset2intervals(bitSetIJ);
				if (timeIntervals.size() > 0) {
					int[] newItems = new int[itemset1.length + 1];
					System.arraycopy(itemset1, 0, newItems, 0, itemset1.length);
					newItems[itemset2.length] = itemset2[itemset2.length - 1];
					save(newItems, timeIntervals);
					candidates.add(newItems);
				}

			}
			MemoryLogger.getInstance().checkMemory();
		}
		return candidates;
	}

	/**
	 * Method to get periodic time-intervals from timestamps stored in a bitset.
	 * 
	 * @param bitSet the bitset of timestamps
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

			// find right endpoint -- pre_ts
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
	 * @return true if it is an LPP, otherwise, false
	 * @throws IOException if error while reading or writing to file
	 */
	private boolean generatePattern(Map.Entry<Integer, BitSet> entry) throws IOException {
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
						bitSet.clear(left, preTS + 1);
					}
					left = -1;
				}

			} else {
				// curent time do not statisfy ,hence we clear it
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
			save(new int[] { entry.getKey() }, timeIntervals);

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
	 * @return the vertical database as a map where key = item and value = bitset of timestamps
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
	 * @throws IOException if error while writing to the output file
	 */
	private void save(int[] items, ArrayList<int[]> timeIntervals) throws IOException {
		itemsetCount++;
		if (writer == null) {
			patterns.addItemset(new Itemset(items, timeIntervals), items.length);
		} else {
			// if the result should be saved to a file
			// write it to the output file
			StringBuilder buffer = new StringBuilder();
			for (int item : items) {
				buffer.append(item);
				buffer.append(" ");
			}
			// as well as its support
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
		System.out.println("=============  LPPM_breadth(no SPM) - STATS =============");
		long temps = endTime - startTimestamp;

		System.out.println(" Total time ~ " + temps + " ms");
		System.out.println(" Itemsets count : " + itemsetCount);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println("===================================================");
	}

}
