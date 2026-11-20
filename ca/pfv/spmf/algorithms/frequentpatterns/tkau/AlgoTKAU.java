package ca.pfv.spmf.algorithms.frequentpatterns.tkau;

/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the TKAU algorithm for mining Top-K High
 * Average-Utility Itemsets (HAUIs), as described in: Wu, R., &amp; He, Z.
 * (2018). Top-k high average-utility itemsets mining with effective pruning
 * strategies. Applied Intelligence, 48(10), 3592-3608.
 * 
 * This is not the original implementation but a re-implementation based on the
 * paper.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoTKAU {

	/** The maximum memory usage in MB */
	private double maxMemory = 0;

	/** The start timestamp of the last execution */
	private long startTimestamp = 0;

	/** The end timestamp of the last execution */
	private long endTimestamp = 0;

	/** The number of top-k HAUIs found */
	private int hauiCount = 0;

	/** The internal minimum average-utility threshold */
	private double minAveUtil = 0;

	/** The target number of top-k itemsets */
	private int k = 0;

	/** The priority queue storing the current top-k candidate HAUIs */
	private PriorityQueue<Candidate> topK;

	/** Map from item ID to its AUUB value */
	private Map<Integer, Long> mapItemToAUUB;

	/** Map from item ID to its real item utility (RIU) */
	private Map<Integer, Long> mapItemToRIU;

	/** Map from item ID to its rank in ascending AUUB order */
	private Map<Integer, Integer> mapItemToRank;

	/**
	 * Represents an element in an AUO-List.
	 */
	private static class AUOElement {

		/** Transaction identifier */
		final int tid;

		/** Itemset utility in this transaction */
		final long iu;

		/** Maximum utility among remaining items in this transaction */
		final long mu;

		/** Number of remaining items in this transaction */
		final int rn;

		/** Tighter maximum average-utility tmau(X, T) */
		double tmau;

		/**
		 * Constructs an AUO-List element.
		 *
		 * @param tid the transaction ID
		 * @param iu  the itemset utility
		 * @param mu  the maximum remaining utility
		 * @param rn  the remaining item count
		 */
		AUOElement(int tid, long iu, long mu, int rn) {
			this.tid = tid;
			this.iu = iu;
			this.mu = mu;
			this.rn = rn;
			this.tmau = 0.0;
		}
	}

	/**
	 * Represents an AUO-List for an itemset.
	 */
	private static class AUOList {

		/** The itemset represented by this list */
		final int[] itemset;

		/** The rank of the last item in ascending AUUB order */
		final int lastItemRank;

		/** The list of elements */
		final List<AUOElement> elements;

		/** The average utility of this itemset */
		double au;

		/** The tighter maximum average-utility (tmau) upper bound */
		double tmau;

		/** The remaining utility upper bound (ru) for 1-itemsets */
		long ru;

		/**
		 * Constructs an AUO-List.
		 *
		 * @param itemset      the itemset array
		 * @param lastItemRank the rank of the last item
		 * @param elements     the list of AUOElements
		 */
		AUOList(int[] itemset, int lastItemRank, List<AUOElement> elements) {
			this.itemset = itemset;
			this.lastItemRank = lastItemRank;
			this.elements = elements;
			long sumIu = 0;
			for (AUOElement e : elements) {
				sumIu += e.iu;
			}
			this.au = itemset.length > 0 ? (double) sumIu / itemset.length : 0.0;
		}
	}

	/**
	 * Represents a candidate top-k HAUI.
	 */
	private static class Candidate implements Comparable<Candidate> {

		/** The itemset */
		final int[] itemset;

		/** The average utility of the itemset */
		final double au;

		/**
		 * Constructs a candidate.
		 *
		 * @param itemset the itemset
		 * @param au      the average utility
		 */
		Candidate(int[] itemset, double au) {
			this.itemset = itemset;
			this.au = au;
		}

		/**
		 * Compares candidates by ascending average utility.
		 *
		 * @param other the other candidate
		 * @return comparison result
		 */
		@Override
		public int compareTo(Candidate other) {
			return Double.compare(this.au, other.au);
		}
	}

	/**
	 * Represents an item and its utility in a transaction.
	 */
	private static class Pair {

		/** The item ID */
		final int item;

		/** The item utility */
		final long utility;

		/**
		 * Constructs a pair.
		 *
		 * @param item    the item ID
		 * @param utility the item utility
		 */
		Pair(int item, long utility) {
			this.item = item;
			this.utility = utility;
		}
	}

	/**
	 * Default constructor.
	 */
	public AlgoTKAU() {
	}

	/**
	 * Runs the TKAU algorithm to mine top-k high average-utility itemsets.
	 *
	 * @param input  the input file path
	 * @param output the output file path
	 * @param k      the number of top-k itemsets to mine
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithm(String input, String output, int k) throws IOException {
		this.k = k;
		this.maxMemory = 0;
		this.hauiCount = 0;
		this.minAveUtil = 0;
		this.startTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();

		this.topK = new PriorityQueue<Candidate>();
		this.mapItemToAUUB = new HashMap<Integer, Long>();
		this.mapItemToRIU = new HashMap<Integer, Long>();
		this.mapItemToRank = new HashMap<Integer, Integer>();

		if (k <= 0) {
			writeOutput(output);
			this.endTimestamp = System.currentTimeMillis();
			MemoryLogger.getInstance().checkMemory();
			this.maxMemory = MemoryLogger.getInstance().getMaxMemory();
			return;
		}

		// First database scan: compute AUUB and RIU for each item
		firstScan(input);

		// Raise minAveUtil using RIU strategy
		raiseThresholdRIU();

		// Sort promising items in ascending AUUB order
		List<Integer> promisingItems = new ArrayList<Integer>();
		for (Map.Entry<Integer, Long> entry : mapItemToAUUB.entrySet()) {
			if (entry.getValue() >= minAveUtil) {
				promisingItems.add(entry.getKey());
			}
		}
		Collections.sort(promisingItems, new Comparator<Integer>() {
			@Override
			public int compare(Integer a, Integer b) {
				int cmp = Long.compare(mapItemToAUUB.get(a), mapItemToAUUB.get(b));
				return cmp != 0 ? cmp : Integer.compare(a, b);
			}
		});

		for (int i = 0; i < promisingItems.size(); i++) {
			mapItemToRank.put(promisingItems.get(i), i);
		}

		// Second database scan: build initial 1-itemset AUO-Lists and CADM
		Map<Integer, List<AUOElement>> mapItemToElements = new HashMap<Integer, List<AUOElement>>();
		Map<Integer, Long> mapItemToRU = new HashMap<Integer, Long>();
		for (Integer item : promisingItems) {
			mapItemToElements.put(item, new ArrayList<AUOElement>());
			mapItemToRU.put(item, 0L);
		}
		List<Double> cadmValues = new ArrayList<Double>();
		secondScan(input, mapItemToElements, mapItemToRU, cadmValues);

		// Raise minAveUtil using CAD strategy
		raiseThresholdCAD(cadmValues);

		// Build AUO-Lists for promising 1-itemsets
		List<AUOList> allItems = new ArrayList<AUOList>();
		for (Integer item : promisingItems) {
			if (mapItemToAUUB.get(item) >= minAveUtil) {
				AUOList list = new AUOList(new int[] { item }, mapItemToRank.get(item), mapItemToElements.get(item));
				list.ru = mapItemToRU.get(item);
				allItems.add(list);
			}
		}

		// EPBF: sort initial 1-itemsets by descending average utility
		List<AUOList> initialExtensions = new ArrayList<AUOList>(allItems);
		Collections.sort(initialExtensions, new Comparator<AUOList>() {
			@Override
			public int compare(AUOList a, AUOList b) {
				return Double.compare(b.au, a.au);
			}
		});

		// Recursive mining
		mining(initialExtensions, allItems);

		// Write output
		writeOutput(output);

		MemoryLogger.getInstance().checkMemory();
		this.maxMemory = MemoryLogger.getInstance().getMaxMemory();
		this.endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Performs the first database scan to compute AUUB and RIU for all items.
	 *
	 * @param input the input file path
	 * @throws IOException if an I/O error occurs
	 */
	private void firstScan(String input) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
				continue;
			}
			String[] split = line.split(":");
			String[] items = split[0].split(" ");
			String[] utilities = split[2].split(" ");

			long tmu = Long.MIN_VALUE;
			for (int i = 0; i < utilities.length; i++) {
				long u = Long.parseLong(utilities[i]);
				if (u > tmu) {
					tmu = u;
				}
			}

			for (int i = 0; i < items.length; i++) {
				int item = Integer.parseInt(items[i]);
				long u = Long.parseLong(utilities[i]);
				mapItemToAUUB.put(item, mapItemToAUUB.getOrDefault(item, 0L) + tmu);
				mapItemToRIU.put(item, mapItemToRIU.getOrDefault(item, 0L) + u);
			}
		}
		reader.close();
	}

	/**
	 * Raises minAveUtil using the RIU strategy.
	 */
	private void raiseThresholdRIU() {
		if (mapItemToRIU.size() < k) {
			return;
		}
		List<Long> riuValues = new ArrayList<Long>(mapItemToRIU.values());
		Collections.sort(riuValues, Collections.reverseOrder());
		double kth = riuValues.get(k - 1);
		if (kth > minAveUtil) {
			minAveUtil = kth;
		}
	}

	/**
	 * Performs the second database scan to build 1-itemset AUO-Lists and CADM.
	 *
	 * @param input             the input file path
	 * @param mapItemToElements map from item to its AUO-List elements
	 * @param mapItemToRU       map from item to its remaining utility bound
	 * @param cadmValues        list to collect 2-itemset average utilities
	 * @throws IOException if an I/O error occurs
	 */
	private void secondScan(String input, Map<Integer, List<AUOElement>> mapItemToElements,
			Map<Integer, Long> mapItemToRU, List<Double> cadmValues) throws IOException {
		Map<Long, Double> cadmMap = new HashMap<Long, Double>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
		String line;
		int tid = 0;
		while ((line = reader.readLine()) != null) {
			if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
				continue;
			}
			tid++;
			String[] split = line.split(":");
			String[] items = split[0].split(" ");
			String[] utilities = split[2].split(" ");

			List<Pair> revisedTx = new ArrayList<Pair>();
			for (int i = 0; i < items.length; i++) {
				int item = Integer.parseInt(items[i]);
				if (mapItemToRank.containsKey(item)) {
					long u = Long.parseLong(utilities[i]);
					revisedTx.add(new Pair(item, u));
				}
			}

			Collections.sort(revisedTx, new Comparator<Pair>() {
				@Override
				public int compare(Pair a, Pair b) {
					return Integer.compare(mapItemToRank.get(a.item), mapItemToRank.get(b.item));
				}
			});

			int len = revisedTx.size();
			long maxSuffix = 0;
			for (int i = len - 1; i >= 0; i--) {
				Pair p = revisedTx.get(i);
				int rn = len - 1 - i;
				AUOElement elem = new AUOElement(tid, p.utility, maxSuffix, rn);
				mapItemToElements.get(p.item).add(elem);
				mapItemToRU.put(p.item, mapItemToRU.get(p.item) + Math.max(p.utility, maxSuffix));
				if (p.utility > maxSuffix) {
					maxSuffix = p.utility;
				}
			}

			for (int i = 0; i < len; i++) {
				Pair pi = revisedTx.get(i);
				for (int j = i + 1; j < len; j++) {
					Pair pj = revisedTx.get(j);
					long key = (((long) pi.item) << 32) | (pj.item & 0xFFFFFFFFL);
					double pairAu = (pi.utility + pj.utility) / 2.0;
					cadmMap.put(key, cadmMap.getOrDefault(key, 0.0) + pairAu);
				}
			}
		}
		reader.close();
		cadmValues.addAll(cadmMap.values());
	}

	/**
	 * Raises minAveUtil using the CAD strategy.
	 *
	 * @param cadmValues the list of 2-itemset average utilities
	 */
	private void raiseThresholdCAD(List<Double> cadmValues) {
		List<Double> aus = new ArrayList<Double>(cadmValues.size() + mapItemToRIU.size());
		for (Long riu : mapItemToRIU.values()) {
			aus.add((double) riu);
		}
		aus.addAll(cadmValues);
		if (aus.size() < k) {
			return;
		}
		Collections.sort(aus, Collections.reverseOrder());
		double kth = aus.get(k - 1);
		if (kth > minAveUtil) {
			minAveUtil = kth;
		}
	}

	/**
	 * Recursively mines top-k HAUIs.
	 *
	 * @param extensions the current candidate extensions sorted by descending au
	 * @param allItems   all promising 1-itemsets in ascending AUUB order
	 */
	private void mining(List<AUOList> extensions, List<AUOList> allItems) {
		MemoryLogger.getInstance().checkMemory();
		for (AUOList x : extensions) {
			if (x.au >= minAveUtil) {
				topK.add(new Candidate(x.itemset, x.au));
				if (topK.size() > k) {
					topK.poll();
				}
				if (topK.size() == k) {
					minAveUtil = topK.peek().au;
				}
			}

			calculateTmau(x);
			if (x.tmau >= minAveUtil) {
				List<AUOList> extOfX = new ArrayList<AUOList>();
				for (AUOList y : allItems) {
					if (y.lastItemRank <= x.lastItemRank) {
						continue;
					}
					double eau = Math.max(x.au, (double) y.ru);
					if (eau < minAveUtil) {
						continue;
					}
					AUOList xy = constructAUOList(x, y);
					if (xy != null && !xy.elements.isEmpty()) {
						extOfX.add(xy);
					}
				}
				if (!extOfX.isEmpty()) {
					Collections.sort(extOfX, new Comparator<AUOList>() {
						@Override
						public int compare(AUOList a, AUOList b) {
							return Double.compare(b.au, a.au);
						}
					});
					mining(extOfX, allItems);
				}
			}
		}
	}

	/**
	 * Calculates the tighter maximum average-utility (tmau) for an AUO-List.
	 *
	 * @param x the AUO-List
	 */
	private void calculateTmau(AUOList x) {
		double totalTmau = 0.0;
		int len = x.itemset.length;
		for (AUOElement e : x.elements) {
			double tmauT;
			if (e.mu > (double) e.iu / len) {
				tmauT = (double) (e.iu + e.mu * e.rn) / (len + e.rn);
			} else if (e.rn > 0) {
				tmauT = (double) (e.iu + e.mu) / (len + 1);
			} else {
				tmauT = 0.0;
			}
			e.tmau = tmauT;
			totalTmau += tmauT;
		}
		x.tmau = totalTmau;
	}

	/**
	 * Constructs the AUO-List for Xy with Early Abandoning (EA).
	 *
	 * @param x the prefix AUO-List
	 * @param y the 1-itemset AUO-List to append
	 * @return the constructed AUO-List, or null if early abandoned
	 */
	private AUOList constructAUOList(AUOList x, AUOList y) {
		List<AUOElement> elements = new ArrayList<AUOElement>();
		double ltmau = x.tmau;
		int i = 0;
		int j = 0;
		int sizeX = x.elements.size();
		int sizeY = y.elements.size();

		while (i < sizeX && j < sizeY) {
			AUOElement ex = x.elements.get(i);
			AUOElement ey = y.elements.get(j);
			if (ex.tid == ey.tid) {
				elements.add(new AUOElement(ex.tid, ex.iu + ey.iu, ey.mu, ey.rn));
				i++;
				j++;
			} else if (ex.tid < ey.tid) {
				ltmau -= ex.tmau;
				if (ltmau < minAveUtil) {
					return null;
				}
				i++;
			} else {
				j++;
			}
		}

		while (i < sizeX) {
			ltmau -= x.elements.get(i).tmau;
			if (ltmau < minAveUtil) {
				return null;
			}
			i++;
		}

		if (elements.isEmpty()) {
			return null;
		}

		int[] newItemset = new int[x.itemset.length + 1];
		System.arraycopy(x.itemset, 0, newItemset, 0, x.itemset.length);
		newItemset[x.itemset.length] = y.itemset[0];
		return new AUOList(newItemset, y.lastItemRank, elements);
	}

	/**
	 * Writes the top-k HAUIs to the output file in SPMF HAUI-Miner format.
	 *
	 * @param output the output file path
	 * @throws IOException if an I/O error occurs
	 */
	private void writeOutput(String output) throws IOException {
		List<Candidate> results = new ArrayList<Candidate>(topK);
		Collections.sort(results, Collections.reverseOrder());

		BufferedWriter writer = new BufferedWriter(new FileWriter(output));
		writer.write("@FILETYPE=\"High average-utility itemsets\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

		for (Candidate c : results) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < c.itemset.length; i++) {
				if (i > 0) {
					sb.append(' ');
				}
				sb.append(c.itemset[i]);
			}
			sb.append(" #AUTIL: ");
			sb.append(c.au);
			writer.write(sb.toString());
			writer.newLine();
			hauiCount++;
		}
		writer.close();
	}

	/**
	 * Prints statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  TKAU ALGORITHM v. 2.69 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" High average-utility itemsets count : " + hauiCount);
		System.out.println("===================================================");
	}
}
