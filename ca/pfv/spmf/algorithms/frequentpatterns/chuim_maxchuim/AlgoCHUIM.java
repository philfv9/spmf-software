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
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Do not remove copyright and license information.
 */

package ca.pfv.spmf.algorithms.frequentpatterns.chuim_maxchuim;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * An implementation of C-HUIM from Duong et al., Knowledge-Based Systems 257
 * (2022), 109921, DOI: 10.1016/j.knosys.2022.109921. Algorithms 1-4 use TWU
 * reduction, TPUT/MPUN-lists, the proper-forward weak bound SPWUB, raised
 * minimum support, TWU-hashed PSNonCHUB, LPSNonCHUB and support-preserving
 * closure unions without enumerating all HUIs.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoCHUIM {
	
	/** Flag to indicate if debug information should be presented. */
	private static final boolean DEBUG = false;
	
	/** The peak memory usage of the latest run */
	private double peakMemory;
	
	/** The start time of the latest run. */
	public long startTimestamp;
	
	/** The end time of the latest run. */
	public long endTimestamp;
	
	/** The latest elapsed time in nanoseconds. */
	public long runtimeNanos;
	
	/** The number of closed high-utility itemsets written. */
	public long closedCount;
	
	/** The number of original database transactions. */
	public int transactionCount;
	
	/** The number of nonempty transactions retained in RD. */
	public int reducedTransactionCount;
	
	/** The inclusive raised minimum support from Remark 1.a. */
	public long minSupportCount;
	
	/** The number of initialization database scans. */
	public int databaseScanCount;
	
	/** The number of TPUT nodes including the root. */
	public int treeNodeCount;
	
	/** The number of prefix branches considered. */
	public long candidateCount;
	
	/** The number of MPUN joins attempted. */
	public long joinCount;
	
	/** The number of singleton, conditional and closure MPUN records built. */
	public long mpunElementCount;
	
	/** The number of support-pruned branches or extensions. */
	public long supportPrunedCount;
	
	/** The number of proper branches eliminated by SPWUB. */
	public long weakPrunedCount;
	
	/** The number of whole branches eliminated by PSNonCHUB. */
	public long backwardPrunedCount;
	
	/** The number of later siblings marked by LPSNonCHUB. */
	public long localMarkCount;
	
	/** The number of marked sibling branches skipped. */
	public long localPrunedCount;
	
	/** The number of nonempty support-preserving unions constructed. */
	public long closureJumpCount;
	
	/** Whether to use the proper-extension weak bound. */
	private boolean weakPruning = true;
	
	/** Whether to prune whole branches with a known backward extension. */
	private boolean backwardPruning = true;
	
	/** Whether to use local non-closed branch pruning. */
	private boolean localPruning = true;
	
	/** Whether to raise minimum support using the maximum original TU. */
	private boolean supportOptimization = true;
	
	/** Whether to jump directly to a support-preserving union. */
	private boolean closureJumping = true;
	
	/** Whether to filter closed-registry checks by exact TWU. */
	private boolean twuHashing = true;

	/**
	 * Enable or disable proper-extension weak-bound pruning.
	 *
	 * @param enabled whether to use this strategy
	 */
	public void setWeakPruningEnabled(boolean enabled) {
		weakPruning = enabled;
	}

	/**
	 * Enable or disable whole-branch backward pruning.
	 *
	 * @param enabled whether to use this strategy
	 */
	public void setBackwardPruningEnabled(boolean enabled) {
		backwardPruning = enabled;
	}

	/**
	 * Enable or disable local non-closed branch pruning.
	 *
	 * @param enabled whether to use this strategy
	 */
	public void setLocalPruningEnabled(boolean enabled) {
		localPruning = enabled;
	}

	/**
	 * Enable or disable the raised minimum-support optimization.
	 *
	 * @param enabled whether to use this strategy
	 */
	public void setSupportOptimizationEnabled(boolean enabled) {
		supportOptimization = enabled;
	}

	/**
	 * Enable or disable support-preserving closure unions.
	 *
	 * @param enabled whether to use this strategy
	 */
	public void setClosureJumpingEnabled(boolean enabled) {
		closureJumping = enabled;
	}

	/**
	 * Enable or disable exact TWU filtering of backward checks.
	 *
	 * @param enabled whether to use this strategy
	 */
	public void setTWUHashingEnabled(boolean enabled) {
		twuHashing = enabled;
	}

	/**
	 * Mine closed HUIs directly using the 2022 MPUN hybrid search.
	 *
	 * @param input      the original SPMF utility database
	 * @param output     the closed output file
	 * @param minUtility the inclusive minimum utility
	 * @throws IOException if input, validation or output fails
	 */
	public void runAlgorithm(String input, String output, long minUtility) throws IOException {
		MemoryLogger.getInstance().reset();
		peakMemory = 0;
		startTimestamp = System.currentTimeMillis();
		endTimestamp = startTimestamp;
		runtimeNanos = 0;
		closedCount = candidateCount = joinCount = mpunElementCount = 0;
		supportPrunedCount = weakPrunedCount = backwardPrunedCount = localMarkCount = localPrunedCount = closureJumpCount = 0;
		transactionCount = reducedTransactionCount = databaseScanCount = treeNodeCount = 0;
		minSupportCount = 1;
		long started = System.nanoTime();
		try {
			if (input == null || output == null || minUtility < 0) {
				throw new IllegalArgumentException(
						"Input, output paths and a nonnegative minimum utility are required");
			}
			File inputFile = new File(input);
			File closedFile = new File(output);
			CHUIMUtility.checkDifferentFiles(inputFile, closedFile);
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(closedFile))) {
				writeMetadata(writer, "Closed");
				mine(inputFile, writer, minUtility);
			}
		} finally {
			MemoryLogger.getInstance().checkMemory();
			peakMemory = MemoryLogger.getInstance().getMaxMemory();
			runtimeNanos = System.nanoTime() - started;
			endTimestamp = System.currentTimeMillis();
		}
	}

	/**
	 * Write standard SPMF output metadata before any result records.
	 *
	 * @param writer the output writer
	 * @param kind   the concise representation name
	 * @throws IOException if metadata cannot be written
	 */
	private void writeMetadata(BufferedWriter writer, String kind) throws IOException {
		writer.write("@FILETYPE=\"" + kind + " high-utility itemsets\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();
	}

	/**
	 * Build RD and TPUT in two database scans and start this miner's own search.
	 *
	 * @param input   the original transaction file
	 * @param writer  the closed output writer
	 * @param minimum the inclusive minimum utility
	 * @throws IOException if initialization or output fails
	 */
	private void mine(File input, BufferedWriter writer, long minimum) throws IOException {
		databaseScanCount++;
		CHUIMUtility.Metadata metadata = CHUIMUtility.metadata(input.toPath());
		transactionCount = metadata.transactions;
		if (supportOptimization && minimum > 0) {
			minSupportCount = metadata.maximumTU == 0 ? (long) transactionCount + 1
					: 1 + (minimum - 1) / metadata.maximumTU;
		}
		int[] order = CHUIMUtility.order(metadata, minimum);
		databaseScanCount++;
		List<CHUIMUtility.Row> rows = CHUIMUtility.reduce(input.toPath(), order, metadata);
		reducedTransactionCount = rows.size();
		TPUT tree = new TPUT(rows, order.length);
		treeNodeCount = tree.nodes.size();
		List<MPUNList> roots = tree.singletons();
		for (MPUNList root : roots) {
			mpunElementCount += root.elements.size();
		}
		MemoryLogger.getInstance().checkMemory();
		Search search = new Search(order, writer, minimum);
		for (MPUNList root : roots) {
			search.findClosed(root, roots);
		}
	}

	/** This execution's independent hybrid search and gradual closed registry. */
	private final class Search {
		/** The original item IDs in increasing TWU order. */
		final int[] order;
		/** The closed output writer. */
		final BufferedWriter writer;
		/** The inclusive exact utility threshold. */
		final long minimum;
		/** The proven closed results for optional unhashed checking. */
		final List<MPUNList.Pattern> closed = new ArrayList<>();
		/** The proven closed results indexed by exact original TWU. */
		final Map<Long, List<MPUNList.Pattern>> closedByTWU = new HashMap<>();

		/**
		 * Initialize one run's independent search state.
		 *
		 * @param order   the fixed TWU-ordered original identifiers
		 * @param writer  the closed output writer
		 * @param minimum the inclusive utility threshold
		 */
		Search(int[] order, BufferedWriter writer, long minimum) {
			this.order = order;
			this.writer = writer;
			this.minimum = minimum;
		}

		/**
		 * Explore one branch using Algorithms 1-3 without exiting its sibling loop.
		 *
		 * @param itemset  the current prefix itemset
		 * @param siblings its complete retained sibling headers
		 * @throws IOException if a closed result cannot be written
		 */
		void findClosed(MPUNList itemset, List<MPUNList> siblings) throws IOException {
			candidateCount++;
			MemoryLogger.getInstance().checkMemory();
			if (itemset.support < minSupportCount) {
				supportPrunedCount++;
				return;
			}
			if (localPruning && itemset.localPruned) {
				localPrunedCount++;
				return;
			}
			if (backwardPruning && hasBackward(itemset)) {
				backwardPrunedCount++;
				return;
			}
			// SPWUB bounds proper extensions, not the utility of A itself.
			if (weakPruning && itemset.weakUpperBound < minimum) {
				weakPrunedCount++;
				addClosed(itemset);
				return;
			}
			List<MPUNList> children = new ArrayList<>();
			int sameSupport = 0;
			for (MPUNList sibling : siblings) {
				if (sibling.lastItem() <= itemset.lastItem()) {
					continue;
				}
				joinCount++;
				MPUNList child = itemset.join(sibling);
				mpunElementCount += child.elements.size();
				if (child.support < minSupportCount) {
					supportPrunedCount++;
					continue;
				}
				children.add(child);
				// Algorithm 1 constructs pairs; Algorithm 2 marks later conditional siblings.
				if (localPruning && itemset.items.length > 1 && child.support == sibling.support
						&& !sibling.localPruned) {
					sibling.localPruned = true;
					localMarkCount++;
				}
				if (child.support == itemset.support) {
					sameSupport++;
				}
			}
			if (closureJumping && sameSupport == children.size()) {
				MPUNList closure = itemset.jumpingClosure(children);
				if (!children.isEmpty()) {
					closureJumpCount++;
					mpunElementCount += closure.elements.size();
				}
				addClosed(closure);
				return;
			}
			if (sameSupport == 0) {
				addClosed(itemset);
			}
			// Marking a sibling never removes it as a join operand for earlier siblings.
			for (MPUNList child : children) {
				findClosed(child, children);
			}
		}

		/**
		 * Verify a previously discovered strict equal-support closed superset.
		 *
		 * @param list the current itemset
		 * @return true if its backward extension has already been found
		 */
		boolean hasBackward(MPUNList list) {
			List<MPUNList.Pattern> bucket = twuHashing ? closedByTWU.get(list.twu) : closed;
			if (bucket != null) {
				for (MPUNList.Pattern pattern : bucket) {
					if (pattern.items.length > list.items.length && pattern.support == list.support
							&& MPUNList.isSubset(list.items, pattern.items)) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Apply Algorithm 4 with the formal final backward-closedness condition.
		 *
		 * @param list the closed candidate with no qualifying one-forward
		 * @throws IOException if output fails
		 */
		@SuppressWarnings("unused")
		void addClosed(MPUNList list) throws IOException {
			if (list.utility < minimum || hasBackward(list)) {
				return;
			}
			MPUNList.Pattern pattern = new MPUNList.Pattern(list);
			closed.add(pattern);
			closedByTWU.computeIfAbsent(list.twu, key -> new ArrayList<>()).add(pattern);
			writePattern(writer, pattern);
			closedCount++;
		}

		/**
		 * Write a proven pattern with numeric-sorted original identifiers.
		 *
		 * @param destination the closed or maximal writer
		 * @param pattern     the proven qualifying pattern
		 * @throws IOException if output fails
		 */
		void writePattern(BufferedWriter destination, MPUNList.Pattern pattern) throws IOException {
			int[] items = new int[pattern.items.length];
			for (int i = 0; i < items.length; i++) {
				items[i] = order[pattern.items[i]];
			}
			Arrays.sort(items);
			for (int i = 0; i < items.length; i++) {
				if (i != 0) {
					destination.write(' ');
				}
				destination.write(Integer.toString(items[i]));
			}
			destination.write(" #SUP: " + pattern.support + " #UTIL: " + pattern.utility);
			destination.newLine();
		}
	}

	/**
	 * Get the latest cached SPMF MemoryLogger maximum without querying the
	 * singleton.
	 *
	 * @return the observed peak memory in MB
	 */
	public double getPeakMemory() {
		return peakMemory;
	}

	/** Print statistics about the latest execution to System.out. */
	public void printStats() {
		System.out.println("=============  C-HUIM v.2.68 - SPMF - STATS =============");
		System.out.println(" Closed high-utility itemset count: " + closedCount);
		if (DEBUG) {
			System.out.println(" Original transactions: " + transactionCount);
			System.out.println(" Revised transactions: " + reducedTransactionCount);
			System.out.println(" Raised minimum support: " + minSupportCount);
			System.out.println(" Database scans: " + databaseScanCount);
			System.out.println(" TPUT nodes: " + treeNodeCount);
			System.out.println(" Candidate branches: " + candidateCount);
			System.out.println(" MPUN joins: " + joinCount);
			System.out.println(" MPUN records: " + mpunElementCount);
			System.out.println(" Support prunes: " + supportPrunedCount);
			System.out.println(" SPWUB prunes: " + weakPrunedCount);
			System.out.println(" PSNonCHUB prunes: " + backwardPrunedCount);
			System.out.println(" LPSNonCHUB marks: " + localMarkCount);
			System.out.println(" LPSNonCHUB prunes: " + localPrunedCount);
			System.out.println(" Closure jumps: " + closureJumpCount);
		}
		System.out.println(" Maximum memory usage: " + peakMemory + " MB");
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===============================================");
	}
}
