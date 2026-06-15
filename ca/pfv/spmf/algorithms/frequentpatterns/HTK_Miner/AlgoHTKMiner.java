package ca.pfv.spmf.algorithms.frequentpatterns.HTK_Miner;

/*
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
 * Do not remove the copyright and license information from this file.
 *
 * @author Konstantinos Malliaridis and Stefanos Ougiaroglou (Copyright 2026)
 * @Email terminal_gr@yahoo.com, konsmall@ihu.gr 
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.regex.Pattern;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * The AlgoHTKMiner algorithm. HTK-Miner (High-Performance Top-K Frequent
 * Itemset Miner) is an algorithm designed for the efficient discovery of the
 * most frequent itemsets without requiring a user-defined minimum support
 * threshold. This Java implementation is optimized for the SPMF library,
 * translating high-level C++ performance strategies into efficient
 * JVM-compliant code. 
 * 
 * Key Technical Features: 
 * 
 * - Integrated Top-K Management: In
 * this implementation, the dual-structure approach of the original C++ version
 * (TopKFI and Q-Heap) is consolidated into a specialized PriorityQueue logic.
 * This ensures O(log K) complexity for result maintenance while dynamically
 * raising the internal minimum support threshold (minSup) to prune the search
 * space as early as possible. 
 * 
 * - SIME (Smart Iteration Mechanism): The algorithm
 * employs the SIME mechanism to significantly reduce the number of candidate
 * intersections. By utilizing a rank-based item ordering and prefix-matching
 * logic, HTK-Miner skips millions of unnecessary operations during the
 * generation of higher-level itemsets. 
 * s
 * - Vertical Bitmap Representation: To
 * achieve hardware-level efficiency in Java, transactions are stored in a
 * vertical bitmap format using primitive long[] arrays. This allows the
 * algorithm to perform itemset intersections via bitwise AND operations and
 * calculate support counts using the highly optimized Long.bitCount() method
 * (leveraging the hardware POPCNT instruction). 
 * 
 * - Memory Efficiency: The
 * implementation prioritizes Java primitive arrays and pre-compiled regex
 * patterns to minimize garbage collection (GC) overhead and maximize throughput
 * on large-scale datasets. - Original Support for K+N Ties HAS NOT BEEN
 * IMPLEMENTED in this version due to internal characteristics of PriorityQueue
 * in Java.
 * 
 * The implementation will return exactly K itemsets, even if there are ties at
 * the K-th position due to PriorityQueue structure limitations. - Parallel
 * Processing for tidset to bitmap conversion HAS NOT BEEN IMPLEMENTED in this
 * version. The current implementation focuses on a single-threaded approach to
 * maintain simplicity and ensure correctness. Future enhancements may explore
 * parallelization strategies for this step if performance bottlenecks are
 * identified.
 * 
 * 
 * * The implementation of the "HTK-Miner algorithm", the algorithm presented
 * in:
 * 
 * @article{HTK-Miner, title = {Efficient techniques for retrieving top-K
 *                     frequent itemsets}, journal = {Expert Systems with
 *                     Applications}, volume = {311}, pages = {131250}, year =
 *                     {2026}, issn = {0957-4174}, doi =
 *                     {https://doi.org/10.1016/j.eswa.2026.131250}, url =
 *                     {https://www.sciencedirect.com/science/article/pii/S0957417426001582},
 *                     author = {Konstantinos Malliaridis and Stefanos
 *                     Ougiaroglou}, keywords = {HTK-Miner, HTK-negFIN, Q-Heap,
 *                     Top- frequent itemsets} }
 * 
 * @author Konstantinos Malliaridis and Stefanos Ougiaroglou (Copyright 2026)
 */
public class AlgoHTKMiner {

	private int k; // The 'K' in Top-K
	private int minSup = 0;
	private int numberOfTransactions = 0;
	private long startTimestamp;
	private long endTimestamp;
	private double maxMemoryUsage;

	// Mapping: Item Name (String) -> Internal ID (Integer)
	private Map<String, Integer> itemToId = new HashMap<>();
	private Map<Integer, String> idToItem = new HashMap<>();

	// Top-K results
	private PriorityQueue<TopKItemset> topKHeap;

	// delimiter handling
	private Pattern delimiterPattern;

	public AlgoHTKMiner() {
	}

	/**
	 * Runs the HTK-Miner algorithm.
	 * 
	 * @param inputPath  The path to the input transaction database.
	 * @param outputPath The path to the output file.
	 * @param k          The number of top-frequent itemsets to find.
	 * @param delimiter  The delimiter used to separate items in the input file.
	 * @throws IOException
	 */
	public void runAlgorithm(String inputPath, String outputPath, int k, String delimiter) throws IOException {
		this.k = k;
		this.maxMemoryUsage = 0;
		this.startTimestamp = System.currentTimeMillis();
		this.topKHeap = new PriorityQueue<>(k, new TopKComparator());
		this.topKHeap = new PriorityQueue<>(k, new TopKComparator());
		MemoryLogger.getInstance().reset();

		// delimiter precompile for faster splitting
		this.delimiterPattern = Pattern.compile(Pattern.quote(delimiter));

		// 1. First Pass: Read file and count supports to rank items
		List<Candidate> currentLevelData = readAndRank(inputPath);

		// 2. Main Mining Loop (Level-wise generation)
		int level = 1;

		Candidate candJ, candI, nextCand;

		while (!currentLevelData.isEmpty()) {
			List<Candidate> nextLevelData = new ArrayList<>();
			int n = currentLevelData.size();

			// SMART ITERATION MECHANISM (SIME)
			for (int j = 1; j < n; ++j) {
				candJ = currentLevelData.get(j);
				if (candJ.support < minSup)
					break;

				for (int i = 0; i < j; ++i) {
					candI = currentLevelData.get(i);
					if (candI.support < minSup)
						break;

					// Prefix Matching Check
					if (hasSamePrefix(candI.itemset, candJ.itemset, level - 1)) {
						CustomBitSet newBits = CustomBitSet.intersect(candI.bits, candJ.bits);
						int newSup = newBits.count();

						if (newSup >= minSup) {
							int[] newKey = Arrays.copyOf(candI.itemset, level + 1);
							newKey[level] = candJ.itemset[level - 1];

							nextCand = new Candidate(newKey, newBits, newSup);
							nextLevelData.add(nextCand);

							// Update Top-K Global Results
							updateTopK(newKey, newSup);
						}
					}
				}
			}

			// Prune and sort for next level
			currentLevelData = pruneAndSort(nextLevelData);
			level++;

			MemoryLogger.getInstance().checkMemory();
		}

		// Update the final minSup retrieved from final Top-K results
		this.minSup = topKHeap.peek().support;

		this.maxMemoryUsage = MemoryLogger.getInstance().getMaxMemory();

		this.endTimestamp = System.currentTimeMillis();
		saveResults(outputPath);
	}

	private List<Candidate> readAndRank(String path) throws IOException {
		Map<Integer, List<Integer>> vR = new HashMap<>();
		int transIndex = 0;
		int itemIndex = 0;

		try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String[] items = delimiterPattern.split(line);
				// String[] items = line.split("\\s+");
				for (String itemStr : items) {
					Integer id = itemToId.get(itemStr);
					if (id == null) {
						id = itemIndex++;
						itemToId.put(itemStr, id);
						idToItem.put(id, itemStr);
					}

					List<Integer> tids = vR.computeIfAbsent(id, k -> new ArrayList<>());
					if (tids.isEmpty() || tids.get(tids.size() - 1) != transIndex) {
						tids.add(transIndex);
					}
				}
				transIndex++;
			}
		}
		this.numberOfTransactions = transIndex;

		// Sort items by support descending
		List<ItemStat> stats = new ArrayList<>();
		for (Map.Entry<Integer, List<Integer>> entry : vR.entrySet()) {
			stats.add(new ItemStat(entry.getValue().size(), entry.getKey()));
		}
		Collections.sort(stats, (a, b) -> b.support - a.support);

		// Determine initial cutoff for K items
		int cutoff = Math.min(stats.size(), k);

		List<Candidate> l1 = new ArrayList<>(cutoff);
		for (int i = 0; i < cutoff; i++) {
			ItemStat stat = stats.get(i);

			CustomBitSet bs = new CustomBitSet(numberOfTransactions);
			for (int tid : vR.get(stat.rawID)) {
				bs.set(tid);
			}

			int[] itemset = new int[] { stat.rawID };
			l1.add(new Candidate(itemset, bs, stat.support));
			updateTopK(itemset, stat.support);
		}

		Collections.sort(l1, (a, b) -> b.support - a.support);
		return l1;

	}

	private void updateTopK(int[] itemset, int support) {
		int topKHeapSize = topKHeap.size();
		if (topKHeapSize < k) {
			topKHeap.add(new TopKItemset(itemset, support));
			if (++topKHeapSize == k) {
				this.minSup = topKHeap.peek().support;
			}
		} else if (support > this.minSup) {
			topKHeap.add(new TopKItemset(itemset, support));
			this.minSup = topKHeap.poll().support;
		}
	}

	private List<Candidate> pruneAndSort(List<Candidate> nextLevel) {
		List<Candidate> pruned = new ArrayList<>();
		for (Candidate c : nextLevel) {
			if (c.support >= minSup) {
				pruned.add(c);
			}
		}
		Collections.sort(pruned, (a, b) -> {
			if (a.support != b.support)
				return b.support - a.support;
			return compareItemsets(a.itemset, b.itemset);
		});
		return pruned;
	}

	private boolean hasSamePrefix(int[] a, int[] b, int prefixLen) {
		for (int i = 0; i < prefixLen; i++) {
			if (a[i] != b[i])
				return false;
		}
		return true;
	}

	private int compareItemsets(int[] a, int[] b) {
		for (int i = 0; i < Math.min(a.length, b.length); i++) {
			if (a[i] != b[i])
				return a[i] - b[i];
		}
		return a.length - b.length;
	}

	public void saveResults(String path) throws IOException {
		List<TopKItemset> results = new ArrayList<>(topKHeap);
		Collections.sort(results, (a, b) -> b.support - a.support);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
			for (TopKItemset res : results) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < res.items.length; i++) {
					sb.append(idToItem.get(res.items[i]));
					if (i < res.items.length - 1)
						sb.append(" ");
				}
				sb.append(" #SUP: ").append(res.support);
				writer.write(sb.toString());
				writer.newLine();
			}
		}
	}

	public void printStats() {
		System.out.println("=============  HTK_MINER - 2.66 - STATS =============");
		System.out.println("Total time: " + (endTimestamp - startTimestamp) / 1000.0 + " s");
		String memory = String.format("%.3f", maxMemoryUsage);
		System.out.println("Maximum memory usage " + memory + " MB");
		System.out.println("Top-K patterns count: " + topKHeap.size());
		System.out.println("Absolute minSup: " + minSup);
		String stats = String.format("Relative minSup: %.5f", (minSup / (double) numberOfTransactions));
		System.out.println(stats);
		System.out.println("Number of transactions: " + numberOfTransactions);
		System.out.println("===================================================");
	}

	// --- Helper Classes ---

	private static class CustomBitSet {
		private final long[] data;

		public CustomBitSet(int numBits) {
			this.data = new long[(numBits + 63) / 64];
		}

		public void set(int index) {
			data[index >> 6] |= (1L << (index & 63));
		}

		public int count() {
			int count = 0;
			for (long word : data) {
				count += Long.bitCount(word);
			}
			return count;
		}

		public static CustomBitSet intersect(CustomBitSet a, CustomBitSet b) {
			CustomBitSet res = new CustomBitSet(a.data.length * 64);
			for (int i = 0; i < a.data.length; i++) {
				res.data[i] = a.data[i] & b.data[i];
			}
			return res;
		}
	}

	private static class Candidate {
		int[] itemset;
		CustomBitSet bits;
		int support;

		Candidate(int[] itemset, CustomBitSet bits, int support) {
			this.itemset = itemset;
			this.bits = bits;
			this.support = support;
		}
	}

	private static class TopKItemset {
		int[] items;
		int support;

		TopKItemset(int[] items, int support) {
			this.items = items;
			this.support = support;
		}
	}

	private static class TopKComparator implements Comparator<TopKItemset> {
		@Override
		public int compare(TopKItemset o1, TopKItemset o2) {
			return Integer.compare(o1.support, o2.support);
		}
	}

	private static class ItemStat {
		int support;
		int rawID;

		ItemStat(int support, int rawID) {
			this.support = support;
			this.rawID = rawID;
		}
	}
}