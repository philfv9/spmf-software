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

package ca.pfv.spmf.algorithms.frequentpatterns.huo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the MaxCloFHUOIM algorithm re-implemented based on the paper. 
 * Reference:
 * Duong et al., Applied Intelligence 54, 4012-4042 (2024),
 * DOI: 10.1007/s10489-024-05296-2
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoMaxCloFHUOIM {
	
	/** Flag to indicate if debug information should be presented. */
	private static final boolean DEBUG = false;
	
	/** Peak memory of the last execution, as detected by SPMF MemoryLogger. */
	private double peakMemory;
	
	/** The start time of the last execution. */
	public long startTimestamp;
	
	/** The end time of the last execution. */
	public long endTimestamp;
	
	/** The elapsed time of the last execution in nanoseconds. */
	public long runtimeNanos;
	
	/** The number of closed frequent high utility-occupancy itemsets found, including maximal-only runs. */
	public long closedCount;
	
	/** The number of maximal frequent high utility-occupancy itemsets written. */
	public long maximalCount;
	
	/** The number of prefix branches considered by the hybrid search. */
	public long candidateCount;
	
	/** The number of PUON-list sibling intersections attempted. */
	public long joinCount;
	
	/** The number of singleton and conditional PUON records constructed. */
	public long puonElementCount;
	
	/** The number of branches rejected by the inclusive support test. */
	public long infrequentPrunedCount;
	
	/** The number of joins avoided by the CMAP tightening strategy. */
	public long pairPrunedCount;
	
	/** The number of proper branches pruned by the weak occupancy bound. */
	public long strictDepthPrunedCount;
	
	/** The number of whole branches pruned during backward checking. */
	public long backwardPrunedCount;
	
	/** The number of whole branches skipped after local pruning. */
	public long localPrunedCount;
	
	/** The number of later siblings marked by the local pruning strategy. */
	public long localMarkCount;
	
	/** The number of nonempty jumping closures constructed. */
	public long closureJumpCount;
	
	/** The number of output decisions requiring exact rational arithmetic. */
	public long exactCheckCount;
	
	/** The number of original input transactions. */
	public int transactionCount;
	
	/** The inclusive absolute minimum support. */
	public int minSupportCount;
	
	/** The number of database scans started by the last execution. */
	public int databaseScanCount;
	
	/** The number of item-prefix nodes, including the root and excluding artificial leaves. */
	public int treeNodeCount;
	
	/** The number of complete revised utility/TU groups, used only as a diagnostic. */
	public int terminalGroupCount;
	
	/** Whether to use weak-bound pruning of proper branches. */
	private boolean strictDepthPruning = true;
	
	/** Whether to use whole-branch pruning during backward checking. */
	private boolean backwardPruning = true;
	
	/** Whether to use local non-closed branch pruning. */
	private boolean localPruning = true;
	
	/** Whether to use CMAP support tightening before PUON joins. */
	private boolean pairPruning = true;
	
	/** Whether to use jumping directly to an all-support-preserving closure. */
	private boolean closureJumping = true;
	
	/** Whether to merge identical utility prefixes with the same original TU within item nodes. */
	private boolean transactionMerging = true;

	/**
	 * Enable or disable weak-bound pruning of proper branches.
	 *
	 * @param enabled whether the strategy should be used
	 */
	public void setStrictDepthPruningEnabled(boolean enabled) {
		strictDepthPruning = enabled;
	}

	/**
	 * Enable or disable whole-branch pruning during backward checking.
	 *
	 * @param enabled whether the strategy should be used
	 */
	public void setBackwardPruningEnabled(boolean enabled) {
		backwardPruning = enabled;
	}

	/**
	 * Enable or disable local non-closed branch pruning.
	 *
	 * @param enabled whether the strategy should be used
	 */
	public void setLocalPruningEnabled(boolean enabled) {
		localPruning = enabled;
	}

	/**
	 * Enable or disable CMAP support tightening before PUON joins.
	 *
	 * @param enabled whether the strategy should be used
	 */
	public void setPairPruningEnabled(boolean enabled) {
		pairPruning = enabled;
	}

	/**
	 * Enable or disable jumping directly to an all-support-preserving closure.
	 *
	 * @param enabled whether the strategy should be used
	 */
	public void setClosureJumpingEnabled(boolean enabled) {
		closureJumping = enabled;
	}

	/**
	 * Enable or disable exact utility-prefix cohort merging within item nodes.
	 *
	 * @param enabled whether the strategy should be used
	 */
	public void setTransactionMergingEnabled(boolean enabled) {
		transactionMerging = enabled;
	}


	/**
	 * Mine both representations internally while optionally suppressing the closed output file.
	 *
	 * @param input               the SPMF utility transaction file
	 * @param outputClosed        the closed output path, ignored and allowed to be null if closed itemsets should not be saved
	 * @param maximalOutput       the maximal output file, always distinct from input
	 * @param minSupportRatio     the inclusive support ratio in (0, 1]
	 * @param minUtilityOccupancy the inclusive mean-occupancy threshold in (0, 1]
	 * @throws IOException if input, validation or output fails
	 */
	public void runAlgorithm(String input, String outputClosed, String maximalOutput, double minSupportRatio,
			double minUtilityOccupancy) throws IOException {
		MemoryLogger.getInstance().reset();
		// whether to skip opening and writing the closed output path
		boolean maximalOnly = outputClosed == null;
		peakMemory = 0;
		startTimestamp = System.currentTimeMillis();
		endTimestamp = startTimestamp;
		runtimeNanos = 0;
		closedCount = maximalCount = candidateCount = joinCount = puonElementCount = 0;
		infrequentPrunedCount = pairPrunedCount = strictDepthPrunedCount = backwardPrunedCount = 0;
		localPrunedCount = localMarkCount = closureJumpCount = exactCheckCount = 0;
		transactionCount = minSupportCount = databaseScanCount = treeNodeCount = terminalGroupCount = 0;
		long start = System.nanoTime();
		try {
			if (!Double.isFinite(minSupportRatio) || minSupportRatio <= 0 || minSupportRatio > 1
					|| !Double.isFinite(minUtilityOccupancy) || minUtilityOccupancy <= 0 || minUtilityOccupancy > 1) {
				throw new IllegalArgumentException("Support and utility-occupancy ratios must be finite and in (0, 1]");
			}
			if (input == null || maximalOutput == null || (!maximalOnly && outputClosed == null)) {
				throw new IllegalArgumentException("Input and output paths are required");
			}
			File inputFile = new File(input);
			File outputFile = maximalOnly ? null : new File(outputClosed);
			File maximalFile = new File(maximalOutput);
			checkDifferentFiles(inputFile, maximalFile);
			if (!maximalOnly) {
				checkDifferentFiles(inputFile, outputFile);
				checkDifferentFiles(outputFile, maximalFile);
			}
			try (BufferedWriter writer = maximalOnly ? null : new BufferedWriter(new FileWriter(outputFile));
					BufferedWriter maximalWriter = new BufferedWriter(new FileWriter(maximalFile))) {
				if (writer != null) {
					writeMetadata(writer, "Closed");
				}
				writeMetadata(maximalWriter, "Maximal");
				mine(inputFile, writer, maximalWriter, minSupportRatio, minUtilityOccupancy);
			}
		} finally {
			MemoryLogger.getInstance().checkMemory();
			peakMemory = MemoryLogger.getInstance().getMaxMemory();
			runtimeNanos = System.nanoTime() - start;
			endTimestamp = System.currentTimeMillis();
		}
	}

	/**
	 * Reject aliased files before any writer can truncate an input or another output.
	 *
	 * @param first  the first input or output file
	 * @param second the other input or output file
	 * @throws IOException if file identity cannot be checked
	 */
	private void checkDifferentFiles(File first, File second) throws IOException {
		if (first.getCanonicalFile().equals(second.getCanonicalFile())
				|| (first.exists() && second.exists() && Files.isSameFile(first.toPath(), second.toPath()))) {
			throw new IllegalArgumentException("Input and output files must be distinct");
		}
	}

	/**
	 * Write SPMF metadata for one concise representation.
	 *
	 * @param writer         the output writer
	 * @param representation the kind of concise itemsets
	 * @throws IOException if a metadata line cannot be written
	 */
	private void writeMetadata(BufferedWriter writer, String representation) throws IOException {
		writer.write("@FILETYPE=\"" + representation + " high utility-occupancy itemsets\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();
	}

	/**
	 * Perform the two initialization scans and start the support-ordered PUON search.
	 *
	 * @param input         the original transaction input file
	 * @param writer        the closed-itemset writer, or null for maximal-only output
	 * @param maximalWriter the maximal-itemset output writer
	 * @param alpha         the relative minimum support
	 * @param beta          the minimum mean utility occupancy
	 * @throws IOException if initialization or output fails
	 */
	private void mine(File input, BufferedWriter writer, BufferedWriter maximalWriter, double alpha, double beta)
			throws IOException {
		databaseScanCount++;
		HUOUtility.Metadata metadata = HUOUtility.metadata(input.toPath(), false);
		transactionCount = metadata.transactionCount;
		minSupportCount = HUOUtility.minimumSupport(alpha, transactionCount);
		int[] order = HUOUtility.order(metadata, minSupportCount, false);
		Map<Integer, Integer> ranks = HUOUtility.ranks(order);
		TPUTTree tree = new TPUTTree();
		HUOUtility.RevisedTransaction revised = new HUOUtility.RevisedTransaction();
		MemoryLogger.getInstance().checkMemory();
		databaseScanCount++;
		@SuppressWarnings("unused")
		int count = HUOUtility.scan(input.toPath(), (transaction, tid) -> {
			revised.revise(transaction, ranks);
			tree.add(transaction, revised, transactionMerging);
		});
		if (count != transactionCount) {
			throw new IOException("The input changed between database scans");
		}
		List<PUONList> roots = new ArrayList<>();
		for (int rank = 0; rank < order.length; rank++) {
			roots.add(new PUONList(new int[] { rank }));
		}
		Map<Long, Integer> cooccurrence = new HashMap<>();
		tree.initializeLists(roots, cooccurrence);
		treeNodeCount = tree.nodeCount;
		terminalGroupCount = tree.terminalGroupCount;
		for (PUONList root : roots) {
			puonElementCount += root.elements.size();
		}
		MemoryLogger.getInstance().checkMemory();
		Search search = new Search(order, cooccurrence, writer, beta);
		for (PUONList root : roots) {
			search.findClosed(root, roots);
		}
		for (PUONList.Pattern pattern : search.maximal) {
			search.writePattern(maximalWriter, pattern);
		}
		maximalCount = search.maximal.size();
	}

	/** The current execution's hybrid search and incrementally constructed closed registry. */
	private final class Search {
		/** The original item identifiers in ascending support order. */
		final int[] order;
		/** The pairwise supports used for the CMAP tightening strategy. */
		final Map<Long, Integer> cooccurrence;
		/** The proven closed patterns indexed by exact TWU. */
		final Map<BigInteger, List<PUONList.Pattern>> closedByTWU = new HashMap<>();
		/** The closed output writer, or null when only maximal itemsets are written. */
		final BufferedWriter writer;
		/** The inclusive actual occupancy threshold. */
		final double beta;
		/** The conservative occupancy threshold used for pruning only. */
		final double betaLower;
		/** The exact decimal threshold used at a numerical output boundary. */
		final BigDecimal exactBeta;
		/** The current maximal antichain maintained by Figure 4 during mining. */
		final List<PUONList.Pattern> maximal = new ArrayList<>();

		/**
		 * Create one independent execution's hybrid search state.
		 *
		 * @param order        the support-ordered original item identifiers
		 * @param cooccurrence the pairwise supports
		 * @param writer       the closed output writer, or null for maximal-only output
		 * @param beta         the minimum mean utility occupancy
		 */
		Search(int[] order, Map<Long, Integer> cooccurrence, BufferedWriter writer, double beta) {
			this.order = order;
			this.cooccurrence = cooccurrence;
			this.writer = writer;
			this.beta = beta;
			betaLower = Math.nextDown(beta);
			exactBeta = BigDecimal.valueOf(beta);
		}

		/**
		 * Apply Figure 3's branch pruning, complete sibling construction and closure tests.
		 *
		 * @param itemset  the current prefix itemset
		 * @param siblings the retained work headers sharing its immediate prefix
		 * @throws IOException if a proven closed itemset cannot be written
		 */
		void findClosed(PUONList itemset, List<PUONList> siblings) throws IOException {
			candidateCount++;
			MemoryLogger.getInstance().checkMemory();
			if (itemset.support < minSupportCount) {
				infrequentPrunedCount++;
				return;
			}
			if (localPruning && itemset.isPrunedByLPS) {
				localPrunedCount++;
				return;
			}
			if (backwardPruning && hasBackwardExtension(itemset)) {
				backwardPrunedCount++;
				return;
			}
			itemset.computeStatistics(minSupportCount);
			// SDPS bounds only proper extensions: the current node may still qualify.
			if (strictDepthPruning && itemset.fWubocc < betaLower) {
				strictDepthPrunedCount++;
				if (isHigh(itemset)) {
					addClosed(itemset);
				}
				return;
			}
			List<PUONList> children = new ArrayList<>();
			int oneForwardCount = 0;
			for (PUONList sibling : siblings) {
				if (sibling.lastItem() <= itemset.lastItem()) {
					continue;
				}
				Integer pairSupport = cooccurrence.get(TPUTTree.pairKey(itemset.lastItem(), sibling.lastItem()));
				if (pairPruning && (pairSupport == null || pairSupport < minSupportCount)) {
					pairPrunedCount++;
					continue;
				}
				joinCount++;
				PUONList child = itemset.join(sibling);
				puonElementCount += child.elements.size();
				if (child.support < minSupportCount) {
					infrequentPrunedCount++;
					continue;
				}
				children.add(child);
				// Strategy 6 is stated for siblings with a nonempty common prefix.
				if (localPruning && itemset.items.length > 1 && child.support == sibling.support
						&& !sibling.isPrunedByLPS) {
					sibling.isPrunedByLPS = true;
					localMarkCount++;
				}
				if (child.support == itemset.support) {
					oneForwardCount++;
				}
			}
			if (closureJumping && oneForwardCount == children.size()) {
				PUONList closure = itemset.jumpingClosure(children);
				if (!children.isEmpty()) {
					closureJumpCount++;
					puonElementCount += closure.elements.size();
				}
				closure.computeStatistics(minSupportCount);
				if (isHigh(closure)) {
					addClosed(closure);
				}
				return;
			}
			if (oneForwardCount == 0 && isHigh(itemset)) {
				addClosed(itemset);
			}
			// Child headers must remain available as join operands even after LPS marks them.
			for (PUONList child : children) {
				findClosed(child, children);
			}
		}

		/**
		 * Check the actual mean with exact arithmetic when close to the threshold.
		 *
		 * @param list the evaluated PUON-list
		 * @return true if the actual mean meets the inclusive occupancy threshold
		 */
		boolean isHigh(PUONList list) {
			if (Math.abs(list.fOcc - beta) <= 1e-12) {
				exactCheckCount++;
				return list.exactOccupancyAtLeast(exactBeta);
			}
			return list.fOcc >= beta;
		}

		/**
		 * Apply Figure 5's backward check within the exact TWU hash bucket.
		 *
		 * @param list the current itemset and support
		 * @return true if an already discovered closed proper superset has equal support
		 */
		boolean hasBackwardExtension(PUONList list) {
			List<PUONList.Pattern> bucket = closedByTWU.get(list.twu);
			if (bucket != null) {
				for (PUONList.Pattern pattern : bucket) {
					if (pattern.support == list.support && pattern.items.length > list.items.length
							&& PUONList.isSubset(list.items, pattern.items)) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Add a proven closed itemset without collecting all qualifying FHUOIs first.
		 *
		 * @param list the high-occupancy itemset having no one-forward extension
		 * @throws IOException if its output record cannot be written
		 */
		@SuppressWarnings("unused")
		void addClosed(PUONList list) throws IOException {
			if (hasBackwardExtension(list)) {
				return;
			}
			PUONList.Pattern pattern = new PUONList.Pattern(list);
			closedByTWU.computeIfAbsent(list.twu, key -> new ArrayList<>()).add(pattern);
			if (writer != null) {
				writePattern(writer, pattern);
			}
			closedCount++;
			updateMaximal(pattern);
		}

		/**
		 * Maintain Figure 4's maximal antichain immediately after discovering a closed itemset.
		 *
		 * @param pattern the newly discovered closed FHUOI
		 */
		void updateMaximal(PUONList.Pattern pattern) {
			for (PUONList.Pattern current : maximal) {
				if (PUONList.isSubset(pattern.items, current.items)) {
					return;
				}
			}
			Iterator<PUONList.Pattern> iterator = maximal.iterator();
			while (iterator.hasNext()) {
				if (PUONList.isSubset(iterator.next().items, pattern.items)) {
					iterator.remove();
				}
			}
			maximal.add(pattern);
		}

		/**
		 * Write one itemset using numeric item order and the standard occupancy fields.
		 *
		 * @param destination the closed or maximal output writer
		 * @param pattern     the proven qualifying pattern
		 * @throws IOException if its output record cannot be written
		 */
		void writePattern(BufferedWriter destination, PUONList.Pattern pattern) throws IOException {
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
			destination.write(" #SUP: " + pattern.support + " #UO: " + pattern.occupancy);
			destination.newLine();
		}
	}

	/**
	 * Get the peak memory detected by MemoryLogger during the latest execution.
	 *
	 * @return the cached last-run peak memory in MB
	 */
	public double getPeakMemory() {
		return peakMemory;
	}


	/** Print statistics about the latest execution to System.out. */
	public void printStats() {
		System.out.println("=============  MaxCloFHUOIM v.2.68 - STATS =============");
		System.out.println(" Closed high utility-occupancy itemset count: " + closedCount);
		System.out.println(" Maximal high utility-occupancy itemset count: " + maximalCount);
		if (DEBUG) {
			System.out.println(" Transactions: " + transactionCount);
			System.out.println(" Minimum support: " + minSupportCount);
			System.out.println(" Candidate branches: " + candidateCount);
			System.out.println(" PUON-list joins: " + joinCount);
			System.out.println(" PUON records: " + puonElementCount);
			System.out.println(" Support prunes: " + infrequentPrunedCount);
			System.out.println(" CMAP prunes: " + pairPrunedCount);
			System.out.println(" Strict-depth prunes: " + strictDepthPrunedCount);
			System.out.println(" Backward branch prunes: " + backwardPrunedCount);
			System.out.println(" Local branch prunes: " + localPrunedCount);
			System.out.println(" Local marks: " + localMarkCount);
			System.out.println(" Jumping closures: " + closureJumpCount);
			System.out.println(" Exact boundary checks: " + exactCheckCount);
			System.out.println(" Input database scans: " + databaseScanCount);
			System.out.println(" TPUT nodes: " + treeNodeCount);
			System.out.println(" Terminal transaction groups: " + terminalGroupCount);
		}
		System.out.println(" Maximum memory usage: " + peakMemory + " MB");
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===============================================");
	}
}
