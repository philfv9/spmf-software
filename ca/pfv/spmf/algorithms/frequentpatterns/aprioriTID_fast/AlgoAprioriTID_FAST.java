package ca.pfv.spmf.algorithms.frequentpatterns.aprioriTID_fast;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
*

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
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Optimized implementation of the AprioriTID algorithm using a hybrid tidset
 * representation: dense (long[] bitset) for high-support itemsets and sparse
 * (sorted int[] list) for low-support itemsets. The representation is chosen
 * automatically based on a density threshold to ensure optimal intersection
 * performance across all support levels. <br/>
 * <br/>
 * AprioriTID was originally proposed in:<br/>
 * Agrawal R, Srikant R. "Fast Algorithms for Mining Association Rules", VLDB.
 * Sep 12-15 1994, Chile, 487-99.<br/>
 * 
 * @see ItemsetWithTID
 * @see TidsetManager
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoAprioriTID_FAST {

	/** the current level k */
	protected int k;

	/** the minimum support threshold (absolute) */
	protected int minSuppRelative;

	/** Maximum size of itemsets to discover */
	protected int maxItemsetSize = Integer.MAX_VALUE;

	/** start time of latest execution */
	protected long startTimestamp = 0;

	/** end time of latest execution */
	protected long endTimeStamp = 0;

	/** object to write the output file */
	protected BufferedWriter writer = null;

	/** the number of frequent itemsets found */
	protected int itemsetCount;

	/** the number of transactions */
	protected int tidcount = 0;

	/** if true, transaction identifiers of each pattern will be shown */
	protected boolean showTransactionIdentifiers = false;

	/** Manages tidset pooling, intersection, and conversion */
	protected final TidsetManager tidsetManager = new TidsetManager();

	/** Reusable StringBuilder for output */
	private final StringBuilder outputBuffer = new StringBuilder(512);

	/** Comparator for 1-itemsets (sorted by single item) */
	private static final Comparator<ItemsetWithTID> COMP_SINGLE_ITEM = new Comparator<ItemsetWithTID>() {
		public int compare(ItemsetWithTID o1, ItemsetWithTID o2) {
			return o1.items[0] - o2.items[0];
		}
	};

	/** Comparator for multi-item itemsets (lexicographic) */
	private static final Comparator<ItemsetWithTID> COMP_LEXICOGRAPHIC = new Comparator<ItemsetWithTID>() {
		public int compare(ItemsetWithTID a, ItemsetWithTID b) {
			int[] ia = a.items;
			int[] ib = b.items;
			int len = ia.length;
			for (int i = 0; i < len; i++) {
				if (ia[i] != ib[i]) {
					return ia[i] - ib[i];
				}
			}
			return 0;
		}
	};

	/**
	 * 
	 * Default constructor
	 */
	public AlgoAprioriTID_FAST() {

	}

	/**
	 * 
	 * Run the AprioriTID algorithm.
	 * 
	 * @param input  path to input file
	 * @param output path to output file
	 * @param minsup minimum support threshold as a fraction (e.g. 0.5)
	 *
	 * @throws NumberFormatException if the input contains non-integer tokens
	 * @throws IOException           if an error occurs reading/writing files
	 */
	public void runAlgorithm(String input, String output, double minsup) throws NumberFormatException, IOException {
		startTimestamp = System.currentTimeMillis();
		itemsetCount = 0;

		writer = new BufferedWriter(new FileWriter(output), 65536);

		// PHASE 1: Single-pass database scan
		List<ItemsetWithTID> level = scanDatabaseAndBuildLevel1(input, minsup);

		// Generate candidates with size k = 2, 3, ...
		k = 2;
		while (!level.isEmpty() && k <= maxItemsetSize) {
			MemoryLogger.getInstance().checkMemory();
			List<ItemsetWithTID> nextLevel = generateCandidateSizeK(level);
			tidsetManager.recycleTidsets(level);
			level = nextLevel;
			k++;
		}
		if (!level.isEmpty()) {
			tidsetManager.recycleTidsets(level);
		}

		writer.close();
		tidsetManager.clearPool();
		endTimeStamp = System.currentTimeMillis();
	}

	/**
	 * 
	 * Scan the database, identify frequent 1-itemsets, and build level 1. Also
	 * initializes tidcount, minSuppRelative, and the TidsetManager. Each 1-itemset
	 * is stored in either dense (bitset) or sparse (int list) representation based
	 * on its support relative to the density threshold.
	 * 
	 * @param input  path to input file
	 * @param minsup minimum support as a fraction
	 * @return sorted list of frequent 1-itemsets
	 * @throws IOException if an error occurs reading the file
	 */
	protected List<ItemsetWithTID> scanDatabaseAndBuildLevel1(String input, double minsup) throws IOException {
		tidcount = 0;
		// Create a map from items to list of transaction ids using bitsets
		Map<Integer, BitSet> mapItemTIDS = new HashMap<Integer, BitSet>(256);

		// Read the file line by line
		BufferedReader reader = new BufferedReader(new FileReader(input), 65536);
		String line;
		while ((line = reader.readLine()) != null) {

			// skip meta data line or comments
			if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
				continue;
			}

			// Fast integer parsing without String.split()
			int len = line.length();
			int number = 0;
			boolean hasNumber = false;
			for (int pos = 0; pos < len; pos++) {
				char c = line.charAt(pos);
				if (c >= '0' && c <= '9') {
					number = number * 10 + (c - '0');
					hasNumber = true;
				} else if (hasNumber) {
					BitSet tids = mapItemTIDS.get(number);
					if (tids == null) {
						tids = new BitSet();
						mapItemTIDS.put(number, tids);
					}
					tids.set(tidcount);
					number = 0;
					hasNumber = false;
				}
			}
			// If we have read a number
			if (hasNumber) {
				// Get the tidset corresponding to this item
				BitSet tids = mapItemTIDS.get(number);

				// If the tidset does not exist create it.
				if (tids == null) {
					tids = new BitSet();
					mapItemTIDS.put(number, tids);
				}
				// Set the bit corresponding to the TID to 1.
				tids.set(tidcount);
			}
			// Increase transaction count
			tidcount++;
		}
		reader.close();

		MemoryLogger.getInstance().checkMemory();

		// Initialize tidset manager
		tidsetManager.init(tidcount);

		// Compute absolute minimum support
		this.minSuppRelative = (int) Math.ceil(minsup * tidcount);

		// Build level 1
		k = 1;
		List<ItemsetWithTID> level = new ArrayList<ItemsetWithTID>(mapItemTIDS.size());

		// Check the frequency of single items
		Iterator<Entry<Integer, BitSet>> iterator = mapItemTIDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, BitSet> entry = iterator.next();
			int cardinality = entry.getValue().cardinality();

			// If frequent
			if (cardinality >= minSuppRelative && maxItemsetSize >= 1) {
				Integer item = entry.getKey();
				ItemsetWithTID ci;

				// Decide if should use bitset or list of integers for TIDs
				if (tidsetManager.shouldUseBitset(cardinality)) {
					long[] words = tidsetManager.bitsetToLongArray(entry.getValue());
					ci = new ItemsetWithTID(new int[] { item }, words, cardinality);
				} else {
					int[] list = tidsetManager.bitsetToSortedIntArray(entry.getValue(), cardinality);
					ci = new ItemsetWithTID(new int[] { item }, list);
				}
				// Add the itemset to size 1 itemset
				level.add(ci);

				// Save the itemset
				saveItemsetToFile(ci);
			}
			iterator.remove();
		}

		// Sort by item ID using static comparator
		Collections.sort(level, COMP_SINGLE_ITEM);

		return level;
	}

	/**
	 * 
	 * Generate frequent itemsets of size k from frequent itemsets of size k-1 using
	 * prefix-indexed candidate generation. The TidsetManager automatically
	 * dispatches to the optimal intersection method based on the tidset
	 * representations of each pair.
	 * 
	 * @param levelK_1 frequent itemsets of size k-1 (sorted text lexicographically)
	 * @return frequent itemsets of size k (sorted lexicographically)
	 * 
	 * @throws IOException if writing to output fails
	 */
	protected List<ItemsetWithTID> generateCandidateSizeK(List<ItemsetWithTID> levelK_1) throws IOException {
		int levelSize = levelK_1.size();
		List<ItemsetWithTID> candidates = new ArrayList<ItemsetWithTID>(levelSize);

		if (k == 2) {
			// Level 2: all pairs of frequent 1-itemsets
			for (int i = 0; i < levelSize; i++) {
				ItemsetWithTID ci1 = levelK_1.get(i);
				int item1 = ci1.items[0];

				for (int j = i + 1; j < levelSize; j++) {
					ItemsetWithTID ci2 = levelK_1.get(j);

					int cardinality = tidsetManager.intersect(ci1, ci2, minSuppRelative);

					if (cardinality >= minSuppRelative) {
						int[] newItems = new int[] { item1, ci2.items[0] };
						ItemsetWithTID candidate = tidsetManager.claimResultAsItemset(newItems, cardinality);
						candidates.add(candidate);
						saveItemsetToFile(candidate);
					}
				}
			}
		} else {
			// Level k >= 3: group by (k-2)-prefix, join within
			// groups
			int prefixLen = levelK_1.get(0).items.length - 1;

			int groupStart = 0;
			while (groupStart < levelSize) {
				int[] groupPrefix = levelK_1.get(groupStart).items;

				int groupEnd = groupStart + 1;
				while (groupEnd < levelSize) {
					int[] otherItems = levelK_1.get(groupEnd).items;
					boolean samePrefix = true;
					for (int p = 0; p < prefixLen; p++) {
						if (groupPrefix[p] != otherItems[p]) {
							samePrefix = false;
							break;
						}
					}
					if (!samePrefix) {
						break;
					}
					groupEnd++;
				}

				for (int i = groupStart; i < groupEnd; i++) {
					ItemsetWithTID ci1 = levelK_1.get(i);
					int[] itemset1 = ci1.items;

					for (int j = i + 1; j < groupEnd; j++) {
						ItemsetWithTID ci2 = levelK_1.get(j);
						int cardinality = tidsetManager.intersect(ci1, ci2, minSuppRelative);

						if (cardinality >= minSuppRelative) {
							int newLen = itemset1.length + 1;
							int[] newItems = new int[newLen];
							System.arraycopy(itemset1, 0, newItems, 0, itemset1.length);
							newItems[itemset1.length] = ci2.items[ci2.items.length - 1];

							ItemsetWithTID candidate = tidsetManager.claimResultAsItemset(newItems, cardinality);
							candidates.add(candidate);
							saveItemsetToFile(candidate);
						}
					}
				}

				groupStart = groupEnd;
			}
		}

		// Sort candidates lexicographically using static comparator
		if (!candidates.isEmpty()) {
			Collections.sort(candidates, COMP_LEXICOGRAPHIC);
		}

		return candidates;
	}

	/**
	 * Save an itemset to the output file. Handles both dense (bitset) and sparse
	 * (int list) tidset representations for TID output. Subclasses can override to
	 * add closure checks, etc.
	 *
	 * @param ci the itemset to save
	 * @throws IOException if writing fails
	 */
	/**
	 * Save an itemset to the output file.
	 *
	 * @param ci the itemset to save
	 * @throws IOException if writing fails
	 */
	protected void saveItemsetToFile(ItemsetWithTID ci) throws IOException {
		outputBuffer.setLength(0);

		// Write items
		int[] items = ci.items;
		for (int i = 0; i < items.length; i++) {
			if (i > 0)
				outputBuffer.append(' ');
			outputBuffer.append(items[i]);
		}

		// Write support
		outputBuffer.append(" #SUP: ").append(ci.support);

		// Optionally write transaction IDs
		if (showTransactionIdentifiers) {
			outputBuffer.append(" #TID:");
			if (ci.tidWords != null) {
				// Dense representation: extract TIDs from bitset
				long[] words = ci.tidWords;
				for (int w = 0; w < words.length; w++) {
					long word = words[w];
					while (word != 0) {
						int bit = Long.numberOfTrailingZeros(word);
						outputBuffer.append(' ').append((w << 6) + bit);
						word &= word - 1;
					}
				}
			} else {
				// Sparse representation
				int[] list = ci.tidList;
				for (int i = 0; i < list.length; i++) {
					outputBuffer.append(' ').append(list[i]);
				}
			}
		}

		// Write to file
		writer.write(outputBuffer.toString());
		writer.newLine();
		itemsetCount++;
	}

	/**
	 * Set whether transaction identifiers should be shown for each pattern found.
	 *
	 * @param show true to show TIDs, false otherwise
	 */
	public void setShowTransactionIdentifiers(boolean show) {
		this.showTransactionIdentifiers = show;
	}

	/**
	 * Print statistics about the algorithm execution.
	 */
	public void printStats() {
		System.out.println("=============  APRIORI TID FAST v. 2.66 - STATS =============");
		System.out.println(" Transactions count from database : " + tidcount);
		System.out.println(" Frequent itemsets count : " + itemsetCount);
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time ~ " + (endTimeStamp - startTimestamp) + " ms");
		System.out.println("===================================================");
	}

	/**
	 * Set the maximum pattern length.
	 *
	 * @param length the maximum length
	 */
	public void setMaximumPatternLength(int length) {
		this.maxItemsetSize = length;
	}
}