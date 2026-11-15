package ca.pfv.spmf.algorithms.frequentpatterns.vmhaui;

/* This is an implementation of the VMHAUI algorithm.
*
* Copyright (c) 2026 VMHAUI
*
* This file is part of the SPMF DATA MINING SOFTWARE * (http://www.philippe-fournier-viger.com/spmf).
*
*
* SPMF is free software: you can redistribute it and/or modify it under the * terms of the GNU General Public License as published by the Free Software * Foundation, either version 3 of the License, or (at your option) any later * version. *
*
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR * A PARTICULAR PURPOSE. See the GNU General Public License for more details. *
*
* You should have received a copy of the GNU General Public License along with * SPMF. If not, see <http://www.gnu.org/licenses/>.
*
* @author Philippe Fournier-Viger
* 
* Do not remove copyright and license information.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the VMHAUI (Vertical Mining of High Average-Utility
 * Itemsets) algorithm as described in:
 * T. Truong, H. Duong, B. Le, P. Fournier-Viger, U. Yun (2019).
 * Efficient high average-utility itemset mining using novel vertical weak upper-bounds.
 * Knowledge-Based Systems.
 * 
 * This is not the original implementation but a re-implementation based on the paper.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoVMHAUI {

	/** the maximum memory usage */
	double maxMemory = 0;

	/** the time the algorithm started */
	long startTimestamp = 0;

	/** the time the algorithm terminated */
	long endTimestamp = 0;

	/** the number of HAUIs generated */
	int huiCount = 0;

	/** writer to write the output file */
	BufferedWriter writer = null;

	/** map from an item of the reduced dataset to its rank in the ascending order of support */
	Map<Integer, Integer> mapItemToRank;

	/** map from a rank to the corresponding item of the reduced dataset */
	int[] rankToItem;

	/** the header table entries pointing to the first created TPUT node of each rank */
	TPUTNode[] headerFirst;

	/** the last created TPUT node of each rank, used to maintain the next_link fields */
	TPUTNode[] headerLast;

	/** temporary column utilities indexed by item rank, used to compute the V vector and vmsub */
	long[] columnSums;

	/** the ranks of the columns currently used in columnSums */
	int[] touchedColumns;

	/** the number of columns currently used in columnSums */
	int touchedCount = 0;

	/**
	 * Default constructor.
	 */
	public AlgoVMHAUI() {
	}

	/**
	 * Run the VMHAUI algorithm.
	 * @param input the input file path
	 * @param output the output file path
	 * @param minAUtility the minimum average-utility threshold
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public void runAlgorithm(String input, String output, int minAUtility) throws IOException {
		maxMemory = 0;
		huiCount = 0;
		startTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();

		writer = new BufferedWriter(new FileWriter(output));
		writer.write("@FILETYPE=\"High average-utility itemsets\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

		List<List<Pair>> database = new ArrayList<List<Pair>>();

		BufferedReader myInput = null;
		String thisLine;
		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#'
						|| thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}
				String[] split = thisLine.split(":");
				String[] items = split[0].split(" ");
				String[] utilityValues = split[2].split(" ");

				List<Pair> transaction = new ArrayList<Pair>();
				for (int i = 0; i < items.length; i++) {
					Pair pair = new Pair();
					pair.item = Integer.parseInt(items[i]);
					pair.utility = Integer.parseInt(utilityValues[i]);
					if (pair.utility > 0) {
						transaction.add(pair);
					}
				}
				if (!transaction.isEmpty()) {
					database.add(transaction);
				}
			}
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		Map<Integer, Long> mapItemToUtility = new HashMap<Integer, Long>();
		Map<Integer, Long> mapItemToVmsub1 = computeVmsub1(database, mapItemToUtility);

		for (Map.Entry<Integer, Long> entry : mapItemToUtility.entrySet()) {
			if (entry.getValue() >= minAUtility) {
				writeOut(new int[] { entry.getKey() }, (double) entry.getValue());
			}
		}

		Set<Integer> promisingItems = new HashSet<Integer>();
		for (Map.Entry<Integer, Long> entry : mapItemToVmsub1.entrySet()) {
			if (entry.getValue() >= minAUtility) {
				promisingItems.add(entry.getKey());
			}
		}

		List<List<Pair>> reducedDataset = buildReducedDataset(database, promisingItems);
		buildTPUT(reducedDataset);

		columnSums = new long[rankToItem.length];
		touchedColumns = new int[rankToItem.length];
		touchedCount = 0;

		MemoryLogger.getInstance().checkMemory();

		mine(minAUtility);

		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
		writer.close();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Compute the utility of each item and its vmsub1 weak upper-bound from the integrated matrix.
	 * Only transactions containing at least one other item are used for vmsub1, as TS1 of the paper.
	 * @param database the transactions of the integrated matrix
	 * @param mapItemToUtility the map filled with the total utility of each item
	 * @return the map from each item to its vmsub1 value
	 */
	private Map<Integer, Long> computeVmsub1(List<List<Pair>> database, Map<Integer, Long> mapItemToUtility) {
		Map<Integer, Map<Integer, Long>> mapColumns = new HashMap<Integer, Map<Integer, Long>>();
		for (List<Pair> transaction : database) {
			for (Pair pair : transaction) {
				Long total = mapItemToUtility.get(pair.item);
				mapItemToUtility.put(pair.item, (total == null) ? (long) pair.utility : total + pair.utility);
			}
			if (transaction.size() < 2) {
				continue;
			}
			for (Pair owner : transaction) {
				Map<Integer, Long> columns = mapColumns.get(owner.item);
				if (columns == null) {
					columns = new HashMap<Integer, Long>();
					mapColumns.put(owner.item, columns);
				}
				for (Pair column : transaction) {
					Long value = columns.get(column.item);
					columns.put(column.item, (value == null) ? (long) column.utility : value + column.utility);
				}
			}
		}
		Map<Integer, Long> mapItemToVmsub1 = new HashMap<Integer, Long>();
		for (Integer item : mapItemToUtility.keySet()) {
			long best = 0;
			Map<Integer, Long> columns = mapColumns.get(item);
			if (columns != null) {
				for (Long value : columns.values()) {
					if (value > best) {
						best = value;
					}
				}
			}
			mapItemToVmsub1.put(item, best);
		}
		return mapItemToVmsub1;
	}

	/**
	 * Build the reduced dataset RD by removing unpromising items (Pruning strategy 1.a) and empty transactions,
	 * and sort the items of each transaction by ascending support.
	 * @param database the transactions of the integrated matrix
	 * @param promisingItems the items whose vmsub1 value is no less than the threshold
	 * @return the reduced dataset, with transactions renumbered by their position in the list
	 */
	private List<List<Pair>> buildReducedDataset(List<List<Pair>> database, Set<Integer> promisingItems) {
		List<List<Pair>> reducedDataset = new ArrayList<List<Pair>>();
		final Map<Integer, Integer> mapItemToSupport = new HashMap<Integer, Integer>();
		for (List<Pair> transaction : database) {
			List<Pair> revised = new ArrayList<Pair>();
			for (Pair pair : transaction) {
				if (promisingItems.contains(pair.item)) {
					revised.add(pair);
				}
			}
			if (revised.isEmpty()) {
				continue;
			}
			for (Pair pair : revised) {
				Integer support = mapItemToSupport.get(pair.item);
				mapItemToSupport.put(pair.item, (support == null) ? 1 : support + 1);
			}
			reducedDataset.add(revised);
		}

		List<Integer> sortedItems = new ArrayList<Integer>(mapItemToSupport.keySet());
		Collections.sort(sortedItems, new Comparator<Integer>() {
			public int compare(Integer o1, Integer o2) {
				int cmp = Integer.compare(mapItemToSupport.get(o1), mapItemToSupport.get(o2));
				return (cmp != 0) ? cmp : Integer.compare(o1, o2);
			}
		});

		mapItemToRank = new HashMap<Integer, Integer>();
		rankToItem = new int[sortedItems.size()];
		for (int rank = 0; rank < sortedItems.size(); rank++) {
			mapItemToRank.put(sortedItems.get(rank), rank);
			rankToItem[rank] = sortedItems.get(rank);
		}

		for (List<Pair> transaction : reducedDataset) {
			Collections.sort(transaction, new Comparator<Pair>() {
				public int compare(Pair o1, Pair o2) {
					return Integer.compare(mapItemToRank.get(o1.item), mapItemToRank.get(o2.item));
				}
			});
		}
		return reducedDataset;
	}

	/**
	 * Build the transactional prefix utility tree (TPUT) by scanning the reduced dataset once and
	 * inserting the items of each transaction in right-to-left order (descending support).
	 * @param reducedDataset the reduced dataset sorted by ascending support
	 */
	private void buildTPUT(List<List<Pair>> reducedDataset) {
		int numberOfItems = rankToItem.length;
		TPUTNode root = new TPUTNode(-1, -1, 0, null);
		headerFirst = new TPUTNode[numberOfItems];
		headerLast = new TPUTNode[numberOfItems];
		int nidCounter = 0;

		for (int tid = 0; tid < reducedDataset.size(); tid++) {
			List<Pair> transaction = reducedDataset.get(tid);
			TPUTNode current = root;
			for (int i = transaction.size() - 1; i >= 0; i--) {
				Pair pair = transaction.get(i);
				int rank = mapItemToRank.get(pair.item);
				TPUTNode child = current.getChild(rank);
				if (child == null) {
					nidCounter++;
					child = new TPUTNode(pair.item, rank, nidCounter, current);
					current.addChild(child);
					if (headerFirst[rank] == null) {
						headerFirst[rank] = child;
					} else {
						headerLast[rank].next = child;
					}
					headerLast[rank] = child;
				}
				child.addTuple(tid, pair.utility);
				current = child;
			}
		}
	}

	/**
	 * Mine the search space with the entries of the header table, starting from the bottom-most entry.
	 * For each entry, the NVUV-lists of the 2-itemsets are built from the ancestors of its nodes and the HAUID procedure is called.
	 * @param minAUtility the minimum average-utility threshold
	 * @throws IOException if an error occurs while writing output
	 */
	private void mine(int minAUtility) throws IOException {
		int numberOfItems = rankToItem.length;
		for (int j = 0; j < numberOfItems; j++) {
			NVUVList[] lists = new NVUVList[numberOfItems];
			for (TPUTNode dn = headerFirst[j]; dn != null; dn = dn.next) {
				UtilityVector vsuv = null;
				TPUTNode an = dn.parent;
				while (an.parent != null) {
					if (vsuv == null) {
						vsuv = dn.getVector();
					}
					if (lists[an.rank] == null) {
						lists[an.rank] = new NVUVList(new int[] { j, an.rank });
					}
					lists[an.rank].addElement(new NVUVElement(dn.nid, an.depth - 1, vsuv));
					an = an.parent;
				}
			}
			List<NVUVList> vListSet = new ArrayList<NVUVList>();
			for (int k = j + 1; k < numberOfItems; k++) {
				if (lists[k] != null) {
					vListSet.add(lists[k]);
				}
			}
			hauid(vListSet, minAUtility);
			for (TPUTNode dn = headerFirst[j]; dn != null; dn = dn.next) {
				dn.releaseVector();
			}
		}
	}

	/**
	 * The HAUID procedure, which discovers the HAUIs of length two or more from a set of NVUV-lists
	 * sharing a common prefix, using Pruning strategies 1.b and 2.
	 * @param vListSet the set of NVUV-lists of the itemsets having the same prefix
	 * @param minAUtility the minimum average-utility threshold
	 * @throws IOException if an error occurs while writing output
	 */
	private void hauid(List<NVUVList> vListSet, int minAUtility) throws IOException {
		if (vListSet.isEmpty()) {
			return;
		}
		List<NVUVList> candidates = new ArrayList<NVUVList>();
		for (NVUVList vList : vListSet) {
			int length = vList.itemRanks.length;
			long utility = computeUtility(vList);
			if (utility >= (long) minAUtility * length) {
				writeOut(toItems(vList.itemRanks), utility / (double) length);
			}
			if (computeVmsub(vList) >= minAUtility) {
				candidates.add(vList);
			}
		}

		for (int j = 0; j < candidates.size(); j++) {
			NVUVList vListJ = candidates.get(j);
			if (!canExtend(vListJ, minAUtility)) {
				continue;
			}
			List<NVUVList> newVListSet = new ArrayList<NVUVList>();
			for (int k = j + 1; k < candidates.size(); k++) {
				NVUVList vListJK = construct(vListJ, candidates.get(k));
				if (vListJK != null) {
					newVListSet.add(vListJK);
				}
			}
			hauid(newVListSet, minAUtility);
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Compute the utility u(A) of an itemset as the sum of the column utilities of its items over all elements (Lemma 2).
	 * @param vList the NVUV-list of the itemset
	 * @return the utility of the itemset
	 */
	private long computeUtility(NVUVList vList) {
		int[] itemRanks = vList.itemRanks;
		long utility = 0;
		for (NVUVElement element : vList.elements) {
			int[] ranks = element.vsuv.ranks;
			long[] utils = element.vsuv.utils;
			int position = 0;
			for (int t = 0; t < itemRanks.length; t++) {
				while (ranks[position] != itemRanks[t]) {
					position++;
				}
				utility += utils[position];
			}
		}
		return utility;
	}

	/**
	 * Compute the vmsub weak upper-bound of an itemset.
	 * It is the maximum column utility over the transactions that contain an item, not in the itemset, whose index is no less than the first item.
	 * @param vList the NVUV-list of the itemset
	 * @return the vmsub value of the itemset
	 */
	private long computeVmsub(NVUVList vList) {
		int length = vList.itemRanks.length;
		touchedCount = 0;
		for (NVUVElement element : vList.elements) {
			int[] ranks = element.vsuv.ranks;
			long[] utils = element.vsuv.utils;
			if (ranks.length > length) {
				for (int q = 0; q < ranks.length; q++) {
					addToColumn(ranks[q], utils[q]);
				}
			}
		}
		long best = 0;
		for (int i = 0; i < touchedCount; i++) {
			if (columnSums[touchedColumns[i]] > best) {
				best = columnSums[touchedColumns[i]];
			}
		}
		clearColumns();
		return best;
	}

	/**
	 * Scan the NVUV-list of an itemset to obtain mn(A) and the V vector according to Lemma 2,
	 * and apply Pruning strategy 2 with the vmaub and vtopmaub weak upper-bounds.
	 * @param vList the NVUV-list of the itemset A
	 * @param minAUtility the minimum average-utility threshold
	 * @return true if forward extensions of A must be considered, false if they can be pruned
	 */
	private boolean canExtend(NVUVList vList, int minAUtility) {
		int[] itemRanks = vList.itemRanks;
		int length = itemRanks.length;
		long[] itemColumns = new long[length];
		int maxMn = 0;
		touchedCount = 0;

		for (NVUVElement element : vList.elements) {
			if (element.mn == 0) {
				continue;
			}
			int[] ranks = element.vsuv.ranks;
			long[] utils = element.vsuv.utils;
			int position = 0;
			for (int t = 0; t < length; t++) {
				while (ranks[position] != itemRanks[t]) {
					position++;
				}
				itemColumns[t] += utils[position];
			}
			for (int q = ranks.length - element.mn; q < ranks.length; q++) {
				addToColumn(ranks[q], utils[q]);
			}
			if (element.mn > maxMn) {
				maxMn = element.mn;
			}
		}

		if (maxMn == 0) {
			clearColumns();
			return false;
		}

		long utilityInSubset = 0;
		for (int t = 0; t < length; t++) {
			utilityInSubset += itemColumns[t];
		}
		long vmru = 0;
		long[] values = new long[length + touchedCount];
		for (int t = 0; t < length; t++) {
			values[t] = itemColumns[t];
		}
		for (int i = 0; i < touchedCount; i++) {
			long value = columnSums[touchedColumns[i]];
			values[length + i] = value;
			if (value > vmru) {
				vmru = value;
			}
		}
		clearColumns();

		long numerator;
		long denominator;
		if (vmru * length > utilityInSubset) {
			numerator = utilityInSubset + (long) maxMn * vmru;
			denominator = length + maxMn;
		} else {
			numerator = utilityInSubset + vmru;
			denominator = length + 1;
		}
		if (numerator < (long) minAUtility * denominator) {
			return false;
		}

		Arrays.sort(values);
		long topSum = 0;
		int taken = 0;
		for (int i = values.length - 1; i >= 0 && taken < length + 1; i--) {
			topSum += values[i];
			taken++;
		}
		if (topSum < (long) minAUtility * (length + 1)) {
			return false;
		}
		return true;
	}

	/**
	 * Construct the NVUV-list of a k-itemset from the NVUV-lists of two (k-1)-itemsets sharing a (k-2)-prefix.
	 * Elements are matched by nid, and the new element reuses the mn field and the shared vsuv of the second list.
	 * @param vList1 the NVUV-list of the first (k-1)-itemset
	 * @param vList2 the NVUV-list of the second (k-1)-itemset
	 * @return the NVUV-list of the k-itemset, or null if it is empty
	 */
	private NVUVList construct(NVUVList vList1, NVUVList vList2) {
		int length = vList1.itemRanks.length;
		int[] newRanks = Arrays.copyOf(vList1.itemRanks, length + 1);
		newRanks[length] = vList2.getLastRank();
		NVUVList result = new NVUVList(newRanks);

		int i = 0;
		int j = 0;
		int size1 = vList1.elements.size();
		int size2 = vList2.elements.size();
		while (i < size1 && j < size2) {
			NVUVElement element1 = vList1.elements.get(i);
			NVUVElement element2 = vList2.elements.get(j);
			if (element1.nid == element2.nid) {
				result.addElement(new NVUVElement(element2.nid, element2.mn, element2.vsuv));
				i++;
				j++;
			} else if (element1.nid < element2.nid) {
				i++;
			} else {
				j++;
			}
		}
		if (result.elements.isEmpty()) {
			return null;
		}
		return result;
	}

	/**
	 * Add a value to a temporary column utility and remember that the column is used.
	 * @param rank the rank of the column
	 * @param value the strictly positive value to add
	 */
	private void addToColumn(int rank, long value) {
		if (columnSums[rank] == 0) {
			touchedColumns[touchedCount++] = rank;
		}
		columnSums[rank] += value;
	}

	/**
	 * Reset the temporary column utilities that were used.
	 */
	private void clearColumns() {
		for (int i = 0; i < touchedCount; i++) {
			columnSums[touchedColumns[i]] = 0;
		}
		touchedCount = 0;
	}

	/**
	 * Convert item ranks to the corresponding item identifiers.
	 * @param ranks the ranks of the items
	 * @return the item identifiers
	 */
	private int[] toItems(int[] ranks) {
		int[] items = new int[ranks.length];
		for (int i = 0; i < ranks.length; i++) {
			items[i] = rankToItem[ranks[i]];
		}
		return items;
	}

	/**
	 * Write a high average-utility itemset to the output file.
	 * @param items the item identifiers of the itemset
	 * @param autility the average utility of the itemset
	 * @throws IOException if an error occurs while writing
	 */
	private void writeOut(int[] items, double autility) throws IOException {
		huiCount++;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < items.length; i++) {
			if (i > 0) {
				buffer.append(' ');
			}
			buffer.append(items[i]);
		}
		buffer.append(" #AUTIL: ");
		buffer.append(autility);
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  VMHAUI ALGORITHM v.2.68 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High-utility itemsets count : " + huiCount);
		System.out.println("===================================================");
	}
}