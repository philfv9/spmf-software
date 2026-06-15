package ca.pfv.spmf.algorithms.frequentpatterns.NFWI;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see http://www.gnu.org/licenses/.
* Do not remove copyright or license information from this file.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class shared by NFWI and NFWCI algorithms.
 *
 * Encapsulates all pipeline stages that are identical in both algorithms:
 * weight-table loading, database scanning, WN-tree construction, DFS code
 * assignment, etc.
 *
 * @author Philippe Fournier-Viger
 */
abstract class AlgoNFWIBase {

	/** the time at which the algorithm started */
	public long startTimestamp = 0;

	/** the time at which the algorithm ended */
	public long endTimestamp = 0;

	/** the number of candidate itemsets generated */
	public int candidateCount = 0;

	/** the minimum weighted support threshold (ratio in [0.0, 1.0]) */
	protected double minWeightedSupport;

	/** map from item to its weight, as defined in the weight table */
	protected Map<Integer, Double> weightTable;

	/** Sum of tw(tk) over all transactions. */
	protected double totalTransactionWeight;

	/** writer to write the output file */
	protected BufferedWriter writer;

	/** reusable buffer for building output lines without per-call allocation */
	protected final StringBuilder outputBuffer = new StringBuilder(256);

	/**
	 * Global incremental counter for assigning pre-order and post-order tree node
	 * codes
	 */
	protected int codeCounter = 1;

	/**
	 * Represents a single node entry in a WN-list, capturing its structural
	 * boundaries and accumulated transaction weight.
	 */
	protected static class WNNode {

		/** The pre-order traversal index of the node */
		int preCode;

		/** The post-order traversal index of the node */
		int postCode;

		/** The accumulated transaction weight passing through this node */
		double weight;

		/**
		 * Constructor to instantiate a WN-list node.
		 * 
		 * @param preCode  the pre-order code
		 * @param postCode the post-order code
		 * @param weight   the accumulated node weight
		 */
		WNNode(int preCode, int postCode, double weight) {
			this.preCode = preCode;
			this.postCode = postCode;
			this.weight = weight;
		}
	}

	/**
	 * Internal WN-tree node used to build the physical prefix tree structure and
	 * generate DFS traversal codes.
	 */
	protected static class WNTreeNode {

		/** The item ID registered at this node */
		int itemId;

		/**
		 * The item's pre-calculated global rank index, cached here to bypass Map
		 * lookups during DFS
		 */
		int rank;

		/** The accumulated weight of transactions passing through this node */
		double weight = 0.0;

		/** Map of child nodes branching from this node */
		Map<Integer, WNTreeNode> children = new HashMap<>();

		/** Assigned pre-order code */
		int preCode;

		/** Assigned post-order code */
		int postCode;

		/**
		 * Constructor for a tree node.
		 * 
		 * @param itemId the integer item ID
		 */
		WNTreeNode(int itemId) {
			this.itemId = itemId;
		}
	}

	/**
	 * Executes the shared pipeline stages common to both algorithms:
	 * <ol>
	 * <li>Load the weight table.</li>
	 * <li>Scan the database once to build transactions and accumulate tw sums.</li>
	 * <li>Collect frequent 1-itemsets and establish global descending order.</li>
	 * <li>Construct the WN-Tree.</li>
	 * <li>Assign DFS pre/post codes and extract base WN-lists.</li>
	 * </ol>
	 * Returns a PipelineResultcarrying everything the subclass mining procedure
	 * needs. Returns NULL if the total transaction weight is zero (e.g. empty
	 * database).
	 *
	 * @param inputDatabase           path to the transaction database file
	 * @param inputWeights            path to the weight table file
	 * @param deduplicateTransactions whether to deduplicate items within each
	 *                                transaction
	 * @return populated {@link PipelineResult}, or {@code null} if nothing to mine
	 * @throws IOException if an error occurs while reading files
	 */
	protected PipelineResult runPipeline(String inputDatabase, String inputWeights, boolean deduplicateTransactions)
			throws IOException {

		// Step 1: Load weight table (must precede database loading)
		weightTable = loadWeightTable(inputWeights);

		// Step 2: Single scan over the database to build transactions and
		// accumulate raw tw sums for each item.

		// Use a primitive double array instead of List<Double> for txWeights
		List<int[]> transactions = new ArrayList<>();
		double[] txWeightsArr = new double[64]; // grows as needed
		int txCount = 0;
		Map<Integer, Double> itemWsums = new HashMap<>();
		this.totalTransactionWeight = 0.0;

		// Read the file line by line
		try (BufferedReader reader = new BufferedReader(new FileReader(inputDatabase), 65536)) {
			String line;
			while ((line = reader.readLine()) != null) {

				// If metadata line, skip it
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				// Parse the line into a transaction
				int[] tx = parseLineToIntArray(line);
				if (tx.length == 0)
					continue;

				// If needed to remove duplicate items in transactions
				if (deduplicateTransactions) {
					tx = deduplicateIntArray(tx);
				}

				// Compute the transaction weight
				double tw = computeTransactionWeight(tx);
				transactions.add(tx);

				// Store tw directly into primitive array; grow with doubling when needed
				if (txCount == txWeightsArr.length) {
					txWeightsArr = Arrays.copyOf(txWeightsArr, txCount * 2);
				}
				txWeightsArr[txCount++] = tw;

				this.totalTransactionWeight += tw;

				// For each item, add the transaction weigth to its total transaction weigth for
				// all transactions until now
				for (int item : tx) {
					Double existing = itemWsums.get(item);
					itemWsums.put(item, existing == null ? tw : existing + tw);
				}
			}
		}

		// If total weight is zero (e.g. database is empty)
		if (this.totalTransactionWeight == 0.0) {
			return null;
		}

		// Step 3: Collect 1-itemsets whose ws >= minws and establish
		// descending global order by weighted support
		List<Integer> globalOrder = new ArrayList<>();
		Map<Integer, Double> frequent1ItemsetsWS = new HashMap<>();

		for (Map.Entry<Integer, Double> entry : itemWsums.entrySet()) {
			double ws = entry.getValue() / this.totalTransactionWeight;
			if (ws >= this.minWeightedSupport) {
				frequent1ItemsetsWS.put(entry.getKey(), ws);
				globalOrder.add(entry.getKey());
			}
		}

		// Sort items strictly descending by weighted support to optimise
		// the WN-tree compression ratio
		globalOrder.sort((a, b) -> {
			int comp = Double.compare(frequent1ItemsetsWS.get(b), frequent1ItemsetsWS.get(a));
			if (comp != 0)
				return comp;
			return Integer.compare(a, b);
		});

		// Store the rank
		Map<Integer, Integer> itemRank = new HashMap<>();
		for (int k = 0; k < globalOrder.size(); k++) {
			itemRank.put(globalOrder.get(k), k);
		}

		// Step 4: Construct the WN-Tree by compressing the sorted transactions.
		//
		// Use a reusable primitive int[] scratch buffer for building the sorted row.
		// A parallel ranksBuf mirrors the item buffer so we can sort both together
		// with a primitive insertion sort, completely bypassing Integer[] boxing.
		WNTreeNode root = new WNTreeNode(-1);
		int[] sortedBuf = new int[64]; // grows as needed
		int[] ranksBuf = new int[64]; // parallel rank buffer; avoids Map lookups during sort

		for (int t = 0; t < txCount; t++) {
			int[] tx = transactions.get(t);
			double tw = txWeightsArr[t]; // direct primitive array read; no unboxing

			// Collect frequent items and their ranks into the scratch buffers
			int sortedLen = 0;
			for (int item : tx) {
				Integer rankObj = itemRank.get(item);
				if (rankObj != null) {
					if (sortedLen == sortedBuf.length) {
						sortedBuf = Arrays.copyOf(sortedBuf, sortedLen * 2);
						ranksBuf = Arrays.copyOf(ranksBuf, sortedLen * 2);
					}
					sortedBuf[sortedLen] = item;
					ranksBuf[sortedLen] = rankObj;
					sortedLen++;
				}
			}

			// Primitive parallel insertion sort on (sortedBuf, ranksBuf) keyed by rank.
			for (int i = 1; i < sortedLen; i++) {
				int keyItem = sortedBuf[i];
				int keyRank = ranksBuf[i];
				int j = i - 1;
				while (j >= 0 && ranksBuf[j] > keyRank) {
					sortedBuf[j + 1] = sortedBuf[j];
					ranksBuf[j + 1] = ranksBuf[j];
					j--;
				}
				sortedBuf[j + 1] = keyItem;
				ranksBuf[j + 1] = keyRank;
			}

			WNTreeNode currentNode = root;
			for (int k = 0; k < sortedLen; k++) {
				int item = sortedBuf[k];
				WNTreeNode child = currentNode.children.get(item);
				if (child == null) {
					child = new WNTreeNode(item);
					child.rank = ranksBuf[k]; // Cache the rank directly in the node to bypass map lookups in DFS
					currentNode.children.put(item, child);
				}
				child.weight += tw;
				currentNode = child;
			}
		}

		// Step 5: Assign DFS Pre/Post codes and extract base WN-Lists.
		//
		// Pre-build a direct-lookup array indexed by item rank so that
		// assignCodesAndExtractLists can locate each item's list with an O(1)
		// array index instead of a HashMap.get(itemId) call per DFS node.
		@SuppressWarnings("unchecked")
		List<WNNode>[] wnListArray = new List[globalOrder.size()];
		for (int i = 0; i < globalOrder.size(); i++) {
			wnListArray[i] = new ArrayList<>();
		}

		assignCodesAndExtractLists(root, wnListArray);

		// Sort each WN-list by preCode ascending (guaranteed by DFS traversal order,
		// but sort explicitly for correctness).
		for (List<WNNode> list : wnListArray) {
			list.sort(Comparator.comparingInt(n -> n.preCode));
		}

		return new PipelineResult(globalOrder, frequent1ItemsetsWS, wnListArray);
	}

	/**
	 * Carries the outputs of the shared pipeline that each subclass needs to build
	 * its level-1 equivalence class and begin mining.
	 */
	protected static class PipelineResult {
		/** Items in descending weighted-support order (index 0 = highest ws) */
		final List<Integer> globalOrder;
		/** Weighted support of each frequent 1-itemset */
		final Map<Integer, Double> frequent1ItemsetsWS;
		/** WN-lists indexed by item rank */
		final List<WNNode>[] wnListArray;

		PipelineResult(List<Integer> globalOrder, Map<Integer, Double> frequent1ItemsetsWS,
				List<WNNode>[] wnListArray) {
			this.globalOrder = globalOrder;
			this.frequent1ItemsetsWS = frequent1ItemsetsWS;
			this.wnListArray = wnListArray;
		}
	}

	/**
	 * Recursively traverses the WN-Tree using Depth-First Search to assign
	 * pre-order and post-order codes to each node, simultaneously extracting the
	 * base WN-lists for every item. Each node carries its rank cached at insertion time.
	 *
	 * @param node        the current WNTreeNode being processed
	 * @param wnListArray array of WN-lists indexed by item rank
	 */
	protected void assignCodesAndExtractLists(WNTreeNode node, List<WNNode>[] wnListArray) {
		for (WNTreeNode child : node.children.values()) {
			child.preCode = this.codeCounter++;
			assignCodesAndExtractLists(child, wnListArray);
			child.postCode = this.codeCounter++;

			// Direct array index lookup using rank cached in the node at tree-build time.
			wnListArray[child.rank].add(new WNNode(child.preCode, child.postCode, child.weight));
		}
	}

	/**
	 * Computes tw(tk) = (sum of weights of items in tk) / |tk|. Called once per
	 * transaction during database loading.
	 * 
	 * @param transaction the int[] of items in transaction tk
	 * @return the transaction weight tw(tk)
	 */
	protected double computeTransactionWeight(int[] transaction) {
		double sum = 0.0;
		for (int item : transaction) {
			Double w = weightTable.get(item);
			if (w != null) {
				sum += w;
			}
		}
		return sum / transaction.length;
	}

	/**
	 * Loads the weight table from a file. 
	 * Uses manual character-level integer parsing to avoid
	 * regex overhead from split().
	 * 
	 * @param filePath the path to the weight table file
	 * @return a map from item integer to its weight
	 * @throws IOException if an error occurs while reading the file
	 */
	protected Map<Integer, Double> loadWeightTable(String filePath) throws IOException {
		Map<Integer, Double> table = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath), 65536)) {
			String line;
			// For each line
			while ((line = reader.readLine()) != null) {
				// Ignore metadata lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				int len = line.length();
				int pos = 0;

				// skip leading whitespace
				while (pos < len && line.charAt(pos) == ' ')
					pos++;

				// parse item integer manually
				int item = 0;
				while (pos < len && line.charAt(pos) >= '0' && line.charAt(pos) <= '9') {
					item = item * 10 + (line.charAt(pos) - '0');
					pos++;
				}

				// skip whitespace between tokens
				while (pos < len && line.charAt(pos) == ' ')
					pos++;

				// parse weight with Double.parseDouble on the remaining substring
				double weight = Double.parseDouble(line.substring(pos));
				table.put(item, weight);
			}
		}
		return table;
	}

	/**
	 * Parses a string representation of a database transaction line into an integer
	 * array. Uses manual character iteration to skip regex overhead. 
	 * 
	 * @param line the text line representing the transaction
	 * @return an integer array containing the items
	 */
	protected static int[] parseLineToIntArray(String line) {
		int len = line.length();
		// Heuristic initial capacity: each item occupies at least 2 chars ("X ")
		int cap = Math.max(8, len / 2);
		int[] buf = new int[cap];
		int count = 0;
		int number = 0;
		boolean hasNumber = false;

		for (int pos = 0; pos < len; pos++) {
			char c = line.charAt(pos);
			if (c >= '0' && c <= '9') {
				number = number * 10 + (c - '0');
				hasNumber = true;
			} else if (hasNumber) {
				if (count == buf.length) {
					buf = Arrays.copyOf(buf, count * 2);
				}
				buf[count++] = number;
				number = 0;
				hasNumber = false;
			}
		}
		if (hasNumber) {
			if (count == buf.length) {
				buf = Arrays.copyOf(buf, count + 1);
			}
			buf[count++] = number;
		}

		// Return exact-sized slice; copy only when the buffer is oversized
		return count == buf.length ? buf : Arrays.copyOf(buf, count);
	}

	/**
	 * Deduplicate a primitive int[] without boxing or streams. Uses
	 * sort-and-compaction to avoid HashMap allocations and boxing overhead
	 * entirely.
	 * 
	 * @param tx the raw transaction array (may contain duplicates)
	 * @return a new array with duplicates removed, in sorted order
	 */
	protected static int[] deduplicateIntArray(int[] tx) {
		// Fast path: single-element arrays cannot have duplicates
		if (tx.length <= 1)
			return tx;

		Arrays.sort(tx);
		int len = 1;
		for (int i = 1; i < tx.length; i++) {
			if (tx[i] != tx[i - 1]) {
				tx[len++] = tx[i];
			}
		}
		return len == tx.length ? tx : Arrays.copyOf(tx, len);
	}

	/**
	 * Writes a frequent weighted itemset to the output file. 
	 *
	 * @param itemset         the itemset to write
	 * @param weightedSupport the weighted support of the itemset
	 * @throws IOException if an error occurs while writing
	 */
	protected void writeOut(int[] itemset, double weightedSupport) throws IOException {
		outputBuffer.setLength(0);

		// Avoid clone+sort for trivial single-element case
		int[] sorted;
		if (itemset.length == 1) {
			sorted = itemset; // no copy needed
		} else {
			sorted = itemset.clone();
			// Insertion sort — optimal for very short arrays (typical itemset length < 10)
			for (int k = 1; k < sorted.length; k++) {
				int key = sorted[k];
				int m = k - 1;
				while (m >= 0 && sorted[m] > key) {
					sorted[m + 1] = sorted[m];
					m--;
				}
				sorted[m + 1] = key;
			}
		}

		for (int k = 0; k < sorted.length; k++) {
			if (k > 0)
				outputBuffer.append(' ');
			outputBuffer.append(sorted[k]);
		}
		outputBuffer.append(" #WSUP: ");
		appendFormatted(outputBuffer, weightedSupport);
		
		// Convert to String once via toString()
		writer.write(outputBuffer.toString());
		writer.newLine();
	}

	/**
	 * Appends a double value formatted to exactly 4 decimal places into sb without
	 * invoking String.format (which is 10-30x slower).
	 * 
	 * @param sb    the StringBuilder to append into
	 * @param value the value to format
	 */
	protected static void appendFormatted(StringBuilder sb, double value) {
		long rounded = Math.round(value * 10000L);
		sb.append(rounded / 10000L).append('.');
		long frac = Math.abs(rounded % 10000L);
		if (frac < 1000)
			sb.append('0');
		if (frac < 100)
			sb.append('0');
		if (frac < 10)
			sb.append('0');
		sb.append(frac);
	}

	/**
	 * Prints statistics about the latest execution to System.out. Subclasses
	 * override this to print algorithm-specific labels and counters.
	 */
	public abstract void printStats();
}