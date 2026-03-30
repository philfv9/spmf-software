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
 * This is an implementation of the LPPM-depth algorithm based on Elcat It is
 * fast,because it uses bit vector for representing TID SETS (transaction id
 * sets) The LPPM-depth is to find all periodic time-intervals of patterns. not
 * using OTS strategy LPPM-depth is presented in this paper: <br/>
 * <br/>
 * 
 * Fournier-Viger, P., Yang, P., Kiran, U., Ventura, S., Luna, J.M.(2019):
 * Mining Local Periodic Patterns in a Discrete Sequence.
 * 
 * @author Peng yang
 */

public class AlgoLPPMDepth2 {

	/** the maximum periodicity threshold */
	private int maxPer;

	/** the minimum duration threshold */
	private int minDur;

	/** the maximal spillover of periods threshold */
	private int maxSoPer;

	/** number of LPPs found */
	private int itemsetCount;

	/** intersection count */
	private long intersectionCount = 0;

	/**
	 * The patterns that are found // (if the user want to keep them into memory)
	 */
	protected Itemsets patterns = null;

	/** object to write the output file */
	BufferedWriter writer = null;

	/**
	 * buffer for storing the current itemset that is mined when performing mining
	 * the idea is to always reuse the same buffer to reduce memory usage.
	 */
	final int BUFFERS_SIZE = 2000;
	/** size of the buffer */
	private int[] itemsetBuffer = null;

	/** the largest timestamps of the database */
	private int largestTs;

	/**
	 * if selfIncrement == true --> considering that all transactions in this //
	 * database are occurring at a fixed time interval, // we have assigned
	 * timestamps for each transaction as increments of 1. // // if selfIncrement ==
	 * flase --> the file has the timestamps
	 */
	private boolean selfIncrement;

	/** start time of the latest execution */
	private long startTimestamp;

	/** end time of the latest execution */
	private long endTime;

	/**
	 * Constructor
	 */
	public AlgoLPPMDepth2() {
		// empty
	}

	/**
	 * Method to run the LPPM-depth algorithm.
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

		// initialize the buffer for storing the current itemset
		itemsetBuffer = new int[BUFFERS_SIZE];

		// if the user want to keep the result into memory
		if (output == null) {
			writer = null;
			patterns = new Itemsets("Local Periodic Pattern");
		} else { // if the user want to save the result to a file
			patterns = null;
			writer = new BufferedWriter(new FileWriter(output));
		}

		// record the start time
		startTimestamp = System.currentTimeMillis();

		// (1) PREPROCESSING: scan the database to build the convertTimeStamps
		Map<Integer, BitSet> mapItemTS = convertTimeStamps(input);

		// (2) generate periodic frequent pattern of 1-pattern

		ArrayList<Integer> lpp1 = new ArrayList<>();

		Iterator<Map.Entry<Integer, BitSet>> it = mapItemTS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, BitSet> entry = it.next();
			if (!generatePattern(entry)) {
				it.remove();
			} else {
				lpp1.add(entry.getKey());
			}
		}
//
//        // sort LPPM1 by totalDuration of each item
//        Collections.sort(LPP1,new Comparator<Integer>(){
//            public int compare(Integer arg0, Integer arg1) {
////                // compare totalDuration
////                int compare =  mapItemTS.get(arg1).totalDuration - mapItemTS.get(arg0).totalDuration;
////                // if totalDuration are same,we check the lexical ordering!
////                if(compare == 0) {
////                    return arg0 - arg1;
////                }
////                // otherwise, just use the totalDuration
////                return compare;
//                return arg0-arg1;
//            }
//        });

		// sort itemsets of size 1 according to lexicographical order.
		Collections.sort(lpp1);

		// For each frequent item I according to the total order
		for (int i = 0; i < lpp1.size() - 1; i++) {
			Integer itemI = lpp1.get(i);
			// We obtain the tidset and support of that item
			BitSet tsSetI = mapItemTS.get(itemI);

			// We create empty equivalence class for storing all 2-itemsets starting with
			// the item "i".
			// This equivalence class is represented by two structures.
			// The first structure stores the suffix of all 2-itemsets starting with the
			// prefix "i".
			// For example, if itemI = "1" and the equivalence class contains 12, 13, 14,
			// then
			// the structure "equivalenceClassIitems" will only contain 2, 3 and 4 instead
			// of
			// 12, 13 and 14. The reason for this implementation choice is that it is more
			// memory efficient.
			List<Integer> equivalenceClassIitems = new ArrayList<Integer>();
			// The second structure stores the tidset of each 2-itemset in the equivalence
			// class
			// of the prefix "i"
			List<BitSet> equivalenceClassItssets = new ArrayList<BitSet>();

			// This is done by a recursive call. Note that we pass
			// item I to that method as the prefix of that equivalence class.
			itemsetBuffer[0] = itemI;

			// For each item itemJ that is larger than i according to the total order of
			// increasing support.
			for (int j = i + 1; j < lpp1.size(); j++) {
				int itemJ = lpp1.get(j);

				// Obtain the tidset of item J and its support.
				BitSet tsSetJ = mapItemTS.get(itemJ);

				// Calculate the tidset of itemset "IJ" by performing the intersection of
				// the tidsets of I and the tidset of J.
				BitSet tsSetIJ = (BitSet) tsSetI.clone();
				tsSetIJ.and(tsSetJ);
				intersectionCount++;

				// After that, we add the itemJ to the equivalence class of 2-itemsets
				// starting with the prefix "i". Note that although we only add "j" to the
				// equivalence class, the item "j"
				// actually represents the itemset "ij" since we keep the prefix "i" for the
				// whole equilvalence class.
				ArrayList<int[]> timeIntervals = bitset2intervals(tsSetIJ);
				if (timeIntervals.size() > 0) {
					equivalenceClassIitems.add(itemJ);
					// We also keep the tidset of "ij".
					equivalenceClassItssets.add(tsSetIJ);
					save(itemsetBuffer, 1, itemJ, timeIntervals);

				}
			}
			// Process all itemsets from the equivalence class of 2-itemsets starting with
			// prefix I
			// to find larger itemsets if that class has more than 0 itemsets.
			if (equivalenceClassIitems.size() > 0) {

				processEquivalenceClass(itemsetBuffer, 1, equivalenceClassIitems, equivalenceClassItssets);
			}
		}

		// close the output file if the result was saved to a file
		if (writer != null) {
			writer.close();
		}

		// we check the memory usage
		MemoryLogger.getInstance().checkMemory();
		// record the end time for statistics
		endTime = System.currentTimeMillis();

		// Return all frequent itemsets found!
		return patterns;

	}

	/**
	 * Process an equivalence class containing several patterns
	 * 
	 * @param prefix                  the prefix of patterns in that class
	 * @param prefixLength            the prefix length (number of items)
	 * @param equivalenceClassItems   the item appended to that prefix for each
	 *                                pattern.
	 * @param equivalenceClassItssets a list of bitsets indicating timestamps for
	 *                                each item (pattern)
	 * @throws IOException if error while writing or reading a file
	 */
	private void processEquivalenceClass(int[] prefix, int prefixLength, List<Integer> equivalenceClassItems,
			List<BitSet> equivalenceClassItssets) throws IOException {

		// If there is only on itemset in equivalence class
		if (equivalenceClassItems.size() <= 1) {
			return;
		}

		// If there are only two itemsets in the equivalence class
		if (equivalenceClassItems.size() == 2) {
			// We get the suffix of the first itemset (an item that we will call I)
			int itemI = equivalenceClassItems.get(0);
			BitSet tsSetI = equivalenceClassItssets.get(0);

			int itemJ = equivalenceClassItems.get(1);
			BitSet tsSetJ = equivalenceClassItssets.get(1);

			BitSet tsSetIJ = (BitSet) tsSetI.clone();
			tsSetIJ.and(tsSetJ);
			intersectionCount++;

			ArrayList<int[]> timeIntervals = bitset2intervals(tsSetIJ);
			if (timeIntervals.size() > 0) {
				int newPrefixLength = prefixLength + 1;
				prefix[prefixLength] = itemI;
				save(prefix, newPrefixLength, itemJ, timeIntervals);
			}
			return;
		}

		// THE FOLLOWING OPTIMIZATION IS COMMENTED SINCE IT DOES NOT IMPROVE PERFORMANCE
		// Sort the equivalence class by support
//		insertionSort(equivalenceClassItems, equivalenceClassTidsets);

		// The next loop combines each pairs of itemsets of the equivalence class
		// to form larger itemsets

		// For each itemset "prefix" + "i"
		for (int i = 0; i < equivalenceClassItems.size() - 1; i++) {
			int itemI = equivalenceClassItems.get(i);
			// get the tidset and support of that itemset
			BitSet tsSetI = equivalenceClassItssets.get(i);

			// create the empty equivalence class for storing all itemsets of the
			// equivalence class starting with prefix + i
			List<Integer> equivalenceClassISuffixItems = new ArrayList<Integer>();
			List<BitSet> equivalenceClassISuffixtssets = new ArrayList<BitSet>();

			int newPrefixLength = prefixLength + 1;
			prefix[prefixLength] = itemI;

			// For each itemset "prefix" + j"
			for (int j = i + 1; j < equivalenceClassItems.size(); j++) {
				int itemJ = equivalenceClassItems.get(j);

				// Get the tidset and support of the itemset prefix + "j"
				BitSet tsSetJ = equivalenceClassItssets.get(j);

				// We will now calculate the tidset of the itemset {prefix, i,j}
				// This is done by intersecting the tidset of the itemset prefix+i
				// with the itemset prefix+j
				BitSet tsSetIJ = (BitSet) tsSetI.clone();
				tsSetIJ.and(tsSetJ);
				intersectionCount++;

				ArrayList<int[]> timeIntervals = bitset2intervals(tsSetIJ);

				// If the itemset prefix+i+j is frequent, then we add it to the
				// equivalence class of itemsets having the prefix "prefix"+i
				// Note actually, we just keep "j" for optimization because all itemsets
				// in the equivalence class of prefix+i will start with prefix+i so it would
				// just
				// waste memory to keep prefix + i for all itemsets.
				if (timeIntervals.size() > 0) {
					equivalenceClassISuffixItems.add(itemJ);
					// We also keep the corresponding tidset and support
					equivalenceClassISuffixtssets.add(tsSetIJ);

					save(prefix, newPrefixLength, itemJ, timeIntervals);
				}
			}

			// If there is more than an itemset in the equivalence class
			// then we recursively process that equivalence class to find larger itemsets
			if (equivalenceClassISuffixItems.size() > 0) {

				// Recursive call
				processEquivalenceClass(prefix, newPrefixLength, equivalenceClassISuffixItems,
						equivalenceClassISuffixtssets);
			}

		}
		// we check the memory usage
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Save a pattern
	 * 
	 * @param prefix        the prefix of the pattern
	 * @param prefixLen     the prefix length
	 * @param itemJ         the item appended to the prefix
	 * @param timeIntervals the time intervals of that pattern
	 * @throws IOException if error while writing to the output file
	 */
	private void save(int[] prefix, int prefixLen, int itemJ, ArrayList<int[]> timeIntervals) throws IOException {
		itemsetCount++;
		if (writer == null) {
			int[] itemName = new int[prefixLen + 1];
			System.arraycopy(prefix, 0, itemName, 0, prefixLen);
			itemName[prefixLen] = itemJ;
			patterns.addItemset(new Itemset(itemName, timeIntervals), prefixLen + 1);
		} else {
			// if the result should be saved to a file
			// write it to the output file
			StringBuilder buffer = new StringBuilder();
			for (int i = 0; i < prefixLen; i++) {
				int item = prefix[i];
				buffer.append(item);
				buffer.append(" ");
			}
			buffer.append(itemJ);
			// as well as its support
			// Then, write the time-intervals
			buffer.append(" #TIME-INTERVALS: ");
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
	 * Save a pattern containing a single item
	 * 
	 * @param itemName      the item name
	 * @param timeIntervals the time intervals of that item
	 * @throws IOException if error while writing to the output file
	 */
	private void saveSingleItem(int itemName, ArrayList<int[]> timeIntervals) throws IOException {
		itemsetCount++;
		if (writer == null) {
			patterns.addItemset(new Itemset(itemName, timeIntervals), 1);
		} else {
			// if the result should be saved to a file
			// write it to the output file
			StringBuilder buffer = new StringBuilder();

			buffer.append(itemName);
			buffer.append(" ");
			// as well as its support
			// Then, write the time-intervals
			buffer.append(" #TIME-INTERVALS: ");
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
	 * @return true if it is an LPP, otherwise, false
	 * @throws IOException if error while reading or writing to file
	 */
	private boolean generatePattern(Map.Entry<Integer, BitSet> entry) throws IOException {
		ArrayList<int[]> timeIntervals = new ArrayList<>();
		int left = -1;
		int soPer = maxSoPer;
		BitSet bitSet = entry.getValue();

		int preTS = bitSet.nextSetBit(1);
		int ts = bitSet.nextSetBit(preTS + 1);
		while (ts > 0) {

			// find left endpoint -- left
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
						bitSet.clear(left, preTS);
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
					bitSet.clear(left, preTS);
				}
			} else {
				if (largestTs - left >= minDur) {
					timeIntervals.add(new int[] { left, largestTs });
				} else {
					bitSet.clear(left, largestTs);
				}
			}
		}

		if (timeIntervals.size() > 0) {
			saveSingleItem(entry.getKey(), timeIntervals);
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
	public Map<Integer, BitSet> convertTimeStamps(String input) throws IOException {
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
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  LPPM_depth(no OTS) - STATS =============");
		long temps = endTime - startTimestamp;

		System.out.println(" Total time ~ " + temps + " ms");
		System.out.println(" Itemsets count : " + itemsetCount);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Intersection count : " + intersectionCount);
		System.out.println("===================================================");
	}

}
