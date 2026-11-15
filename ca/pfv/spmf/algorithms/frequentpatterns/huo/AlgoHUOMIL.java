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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Supplier;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the HUOMIL algorithm. Reference: Kim et al.,
 * IEEE Access 11, 43140-43158 (2023), DOI:
 * 10.1109/ACCESS.2023.3271864, Algorithms 1-3.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoHUOMIL {
	/** Flag to indicate if debug information should be presented. */
	private static final boolean DEBUG = false;
	/** Peak memory of the last execution, as detected by SPMF MemoryLogger. */
	private double peakMemory;
	/** The start time of the last execution. */
	public long startTimestamp;
	/** The end time of the last execution */
	public long endTimestamp;
	/** The elapsed time of the last execution. */
	public long runtimeNanos;
	/** The number of high utility-occupancy itemsets written. */
	public long huopCount;
	/** The number of nonempty itemsets evaluated, including rejected candidates. */
	public long candidateCount;
	/** The number of original input transactions. */
	public int transactionCount;
	/** The inclusive absolute support threshold. */
	public int minSupportCount;
	/** The number of output decisions requiring exact rational arithmetic. */
	public long exactCheckCount;

	/** The number of conditional indexed lists created. */
	public long conditionalListCount;
	/** The number of conditional entries created. */
	public long indexedEntryCount;
	/** The number of direct next-index links traversed during projection. */
	public long linkTraversalCount;
	/** The number of branches stopped by the loose aggregate bound. */
	public long looseBoundPrunedCount;
	/** The number of branches stopped by the tighter top-support bound. */
	public long tightBoundPrunedCount;
	/** The number of conditional entries subtracting a nonempty common prefix. */
	public long prefixSubtractionCount;
	/** The number of input scans started by the latest run. */
	public int databaseScanCount;
	/** The number of top-support vectors evaluated after the loose test. */
	public long tightBoundEvaluationCount;
	/**
	 * The number of backing-array growths for the run's reusable tight-bound
	 * buffer.
	 */
	public int boundBufferGrowthCount;
	/** Whether to apply occupancy-bound subtree pruning. */
	private boolean upperBoundPruning = true;
	/** Whether to test the loose bound before constructing a tight-bound vector. */
	private boolean looseBoundPruning = true;
	

	/** A primitive base list shared by all indexed lists of one prefix. */
	static final class BaseList {
		/** The number of populated base entries. */
		int size;
		/** The original transaction identifier at each base index. */
		final int[] tid;
		/** The exact utility of the common prefix at each base index. */
		final long[] utility;
		/** The common-prefix occupancy at each base index. */
		final double[] uo;

		/**
		 * Allocate a base list for at most the given number of transactions.
		 * 
		 * @param capacity the maximum number of base entries
		 */
		BaseList(int capacity) {
			tid = new int[capacity];
			utility = new long[capacity];
			uo = new double[capacity];
		}

		/**
		 * Append a transaction's common-prefix information.
		 * 
		 * @param transaction the original transaction identifier
		 * @param value       the exact common-prefix utility
		 * @param occupancy   the common-prefix occupancy
		 * @return the new base entry's direct index
		 */
		int add(int transaction, long value, double occupancy) {
			int index = size++;
			tid[index] = transaction;
			utility[index] = value;
			uo[index] = occupancy;
			return index;
		}
	}

	/** A GUO-IL or CUO-IL with packed quadruples and independent aggregate sums. */
	static final class IndexedList {
		/** The global rank of the list's final item. */
		final int rank;
		/** The number of populated quadruples. */
		int size;
		/** The global rank of the next linked list, or minus one. */
		int[] nextItem;
		/** The direct entry index in the next linked list, or minus one. */
		int[] nextIndex;
		/** The actual itemset occupancy of each quadruple. */
		double[] uo;
		/** The actual remaining occupancy of each quadruple. */
		double[] ruo;
		/** The exact itemset-utility arithmetic companion of each quadruple. */
		long[] utility;
		/** The exact remaining-utility arithmetic companion of each quadruple. */
		long[] remaining;
		/** The direct common-prefix base index of each quadruple. */
		int[] baseIndex;
		/** The compensated sumUO aggregate from the paper. */
		final HUOUtility.Sum sumUO = new HUOUtility.Sum();
		/** The compensated sumRUO aggregate from the paper. */
		final HUOUtility.Sum sumRUO = new HUOUtility.Sum();
		/** The outward-rounded sum used only for conservative loose pruning. */
		double sumUpper;

		/**
		 * Allocate an indexed list with the given initial column capacity.
		 * 
		 * @param rank     the global rank of the final item
		 * @param capacity the initial number of quadruples to accommodate
		 */
		IndexedList(int rank, int capacity) {
			this.rank = rank;
			nextItem = new int[capacity];
			nextIndex = new int[capacity];
			uo = new double[capacity];
			ruo = new double[capacity];
			utility = new long[capacity];
			remaining = new long[capacity];
			baseIndex = new int[capacity];
		}

		/**
		 * Append a quadruple and update actual and conservative aggregates separately.
		 * 
		 * @param value  the exact itemset utility
		 * @param suffix the exact remaining utility in the final global order
		 * @param total  the original transaction utility
		 * @param base   the direct common-prefix base index
		 * @param next   the next linked list's global rank, or minus one
		 * @param index  the next linked entry's index, or minus one
		 * @return the appended quadruple's direct index
		 */
		int add(long value, long suffix, long total, int base, int next, int index) {
			if (size == utility.length)
				grow();
			int position = size++;
			utility[position] = value;
			remaining[position] = suffix;
			uo[position] = value / (double) total;
			ruo[position] = suffix / (double) total;
			baseIndex[position] = base;
			nextItem[position] = next;
			nextIndex[position] = index;
			sumUO.add(uo[position]);
			sumRUO.add(ruo[position]);
			sumUpper = HUOUtility.upperAdd(sumUpper, HUOUtility.upperRatio(Math.addExact(value, suffix), total));
			return position;
		}

		/** Grow all parallel columns without changing any existing entry index. */
		private void grow() {
			int capacity = HUOUtility.grownCapacity(utility.length, size + 1);
			nextItem = Arrays.copyOf(nextItem, capacity);
			nextIndex = Arrays.copyOf(nextIndex, capacity);
			uo = Arrays.copyOf(uo, capacity);
			ruo = Arrays.copyOf(ruo, capacity);
			utility = Arrays.copyOf(utility, capacity);
			remaining = Arrays.copyOf(remaining, capacity);
			baseIndex = Arrays.copyOf(baseIndex, capacity);
		}
	}

	/**
	 * Enable or disable utility-occupancy subtree pruning.
	 * 
	 * @param enabled whether to apply occupancy-bound pruning
	 */
	public void setUpperBoundPruningEnabled(boolean enabled) {
		upperBoundPruning = enabled;
	}

	/**
	 * Enable or disable the cheap aggregate test before the tight bound.
	 * 
	 * @param enabled whether to apply the loose-bound test first
	 */
	public void setLooseBoundPruningEnabled(boolean enabled) {
		looseBoundPruning = enabled;
	}

	/**
	 * Mine frequent high utility-occupancy itemsets using indexed-list projection.
	 * 
	 * @param input               the SPMF utility transaction file
	 * @param output              the distinct output file
	 * @param minSupportRatio     the inclusive support ratio in (0, 1]
	 * @param minUtilityOccupancy the inclusive mean-occupancy threshold in (0, 1]
	 * @throws IOException if input, validation or output fails
	 */
	public void runAlgorithm(String input, String output, double minSupportRatio, double minUtilityOccupancy)
			throws IOException {
		conditionalListCount = indexedEntryCount = linkTraversalCount = 0;
		looseBoundPrunedCount = tightBoundPrunedCount = prefixSubtractionCount = tightBoundEvaluationCount = 0;
		databaseScanCount = boundBufferGrowthCount = 0;
		MemoryLogger.getInstance().reset();
		peakMemory = 0;
		startTimestamp = System.currentTimeMillis();
		endTimestamp = startTimestamp;
		runtimeNanos = 0;
		huopCount = candidateCount = exactCheckCount = 0;
		transactionCount = minSupportCount = 0;
		long start = System.nanoTime();
		try {
			if (!Double.isFinite(minSupportRatio) || minSupportRatio <= 0 || minSupportRatio > 1
					|| !Double.isFinite(minUtilityOccupancy) || minUtilityOccupancy <= 0 || minUtilityOccupancy > 1) {
				throw new IllegalArgumentException("Support and utility-occupancy ratios must be finite and in (0, 1]");
			}
			if (input == null || output == null)
				throw new IllegalArgumentException("Input and output paths are required");
			File inputFile = new File(input);
			File outputFile = new File(output);
			if (inputFile.getCanonicalFile().equals(outputFile.getCanonicalFile())) {
				throw new IllegalArgumentException("Input and output must be different files");
			}
			Path source = inputFile.toPath();
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
				writer.write("@FILETYPE=\"High utility-occupancy itemsets\"");
				writer.newLine();
				writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
				writer.newLine();
				MemoryLogger.getInstance().checkMemory();
				mine(new Run(source, writer, minSupportRatio, minUtilityOccupancy));
			}
		} finally {
			MemoryLogger.getInstance().checkMemory();
			peakMemory = MemoryLogger.getInstance().getMaxMemory();
			runtimeNanos = System.nanoTime() - start;
			endTimestamp = System.currentTimeMillis();
		}
	}

	/**
	 * Perform Algorithms 1 and 2 to build support-ordered global indexed lists.
	 * 
	 * @param run the current input, output and threshold context
	 * @throws IOException if a database scan fails
	 */
	private void mine(Run run) throws IOException {
		databaseScanCount++;
		HUOUtility.Metadata metadata = HUOUtility.metadata(run.input, false);
		MemoryLogger.getInstance().checkMemory();
		run.initialize(metadata.transactionCount);
		int[] order = HUOUtility.order(metadata, minSupportCount, false);
		Map<Integer, Integer> ranks = HUOUtility.ranks(order);
		IndexedList[] global = new IndexedList[order.length];
		for (int rank = 0; rank < order.length; rank++) {
			global[rank] = new IndexedList(rank, metadata.support.get(order[rank]));
		}
		BaseList bases = new BaseList(transactionCount);
		HUOUtility.RevisedTransaction revised = new HUOUtility.RevisedTransaction();
		databaseScanCount++;
		int count = HUOUtility.scan(run.input, (transaction, tid) -> {
			run.setTransactionUtility(tid, transaction.total);
			int base = bases.add(tid, 0, 0);
			revised.revise(transaction, ranks);
			long remaining = 0;
			int nextItem = -1;
			int nextIndex = -1;
			for (int i = revised.size - 1; i >= 0; i--) {
				int rank = revised.rank(i);
				long utility = revised.utility(transaction, i);
				IndexedList list = global[rank];
				nextIndex = list.add(utility, remaining, transaction.total, base, nextItem, nextIndex);
				nextItem = rank;
				remaining = Math.addExact(remaining, utility);
			}
		});
		run.checkSecondScan(count);
		Search search = new Search(run, order);
		search.mine(global, bases, 0);
		boundBufferGrowthCount = search.bounds.growthCount;
	}

	/** The depth-first search state for the current run. */
	private final class Search {
		/** The current input, output and threshold context. */
		final Run run;
		/** The fixed support-ascending item order. */
		final int[] order;
		/** The reusable depth-first prefix buffer. */
		final int[] prefix;
		/** The one reusable full-vector bound buffer for the current DFS run. */
		final HUOUtility.BoundBuffer bounds = new HUOUtility.BoundBuffer();

		/**
		 * Create the indexed depth-first search state.
		 * 
		 * @param run   the current run context
		 * @param order the fixed support-ascending item order
		 */
		Search(Run run, int[] order) {
			this.run = run;
			this.order = order;
			prefix = new int[order.length];
		}

		/**
		 * Apply Algorithm 3's output, loose-bound, tight-bound and projection steps.
		 * 
		 * @param level  the current rank-addressable indexed-list headers
		 * @param bases  the common-prefix base list for this level
		 * @param length the current common-prefix length
		 * @throws IOException if an output record cannot be written
		 */
		void mine(IndexedList[] level, BaseList bases, int length) throws IOException {
			MemoryLogger.getInstance().checkMemory();
			for (IndexedList list : level) {
				if (list == null)
					continue;
				prefix[length] = order[list.rank];
				run.consider(prefix, length + 1, list.size, list.sumUO.value(), () -> exactSum(list, bases));
				if (list.size < minSupportCount)
					continue;
				double loose = HUOUtility.upperDivide(list.sumUpper, minSupportCount);
				boolean loosePruned = upperBoundPruning && looseBoundPruning && loose < run.betaLower;
				double tight = loosePruned ? Double.NaN : upperBound(list, bases);
				if (loosePruned) {
					looseBoundPrunedCount++;
					continue;
				}
				if (upperBoundPruning && tight < run.betaLower) {
					tightBoundPrunedCount++;
					continue;
				}
				if (list.rank + 1 < order.length)
					project(level, bases, list, length);
			}
		}

		/**
		 * Project a selected prefix using direct next-item and next-index links only.
		 * 
		 * @param level    the source indexed-list headers
		 * @param bases    the source common-prefix base list
		 * @param selected the selected prefix list
		 * @param length   the common-prefix length before selection
		 * @throws IOException if recursive output fails
		 */
		void project(IndexedList[] level, BaseList bases, IndexedList selected, int length) throws IOException {
			IndexedList[] conditional = new IndexedList[order.length];
			BaseList nextBases = new BaseList(selected.size);
			boolean any = false;
			for (int entry = 0; entry < selected.size; entry++) {
				int oldBase = selected.baseIndex[entry];
				int tid = bases.tid[oldBase];
				int baseIndex = nextBases.add(tid, selected.utility[entry], selected.uo[entry]);
				int nextItem = selected.nextItem[entry];
				int nextIndex = selected.nextIndex[entry];
				IndexedList previous = null;
				int previousIndex = -1;
				while (nextItem != -1) {
					IndexedList connected = level[nextItem];
					linkTraversalCount++;
					IndexedList target = conditional[nextItem];
					if (target == null) {
						int capacity = Math.max(1, (int) ((long) selected.size * connected.size / bases.size));
						target = new IndexedList(nextItem, capacity);
						conditional[nextItem] = target;
						conditionalListCount++;
						any = true;
					}
					long utility = Math.addExact(Math.subtractExact(selected.utility[entry], bases.utility[oldBase]),
							connected.utility[nextIndex]);
					if (length != 0)
						prefixSubtractionCount++;
					int index = target.add(utility, connected.remaining[nextIndex], run.transactionUtilities[tid],
							baseIndex, -1, -1);
					indexedEntryCount++;
					if (previous != null) {
						previous.nextItem[previousIndex] = nextItem;
						previous.nextIndex[previousIndex] = index;
					}
					previous = target;
					previousIndex = index;
					int following = connected.nextItem[nextIndex];
					nextIndex = connected.nextIndex[nextIndex];
					nextItem = following;
				}
			}
			// Low-support lists remain link targets until this level is discarded.
			if (any)
				mine(conditional, nextBases, length + 1);
		}

		/**
		 * Calculate Definition 9's top-support bound after the loose test succeeds.
		 * 
		 * @param list  the frequent indexed list
		 * @param bases the transaction-reference base list
		 * @return an outward-rounded top-support occupancy average
		 */
		double upperBound(IndexedList list, BaseList bases) {
			tightBoundEvaluationCount++;
			double[] values = bounds.prepare(list.size);
			for (int i = 0; i < list.size; i++) {
				long total = run.transactionUtilities[bases.tid[list.baseIndex[i]]];
				values[i] = HUOUtility.upperRatio(Math.addExact(list.utility[i], list.remaining[i]), total);
			}
			return HUOUtility.topBound(values, list.size, minSupportCount);
		}

		/**
		 * Sum exact occupancy fractions for a near-threshold output decision.
		 * 
		 * @param list  the selected indexed list
		 * @param bases the transaction-reference base list
		 * @return the exact sum of supporting-transaction occupancy fractions
		 */
		HUOUtility.Fraction exactSum(IndexedList list, BaseList bases) {
			HUOUtility.Fraction sum = new HUOUtility.Fraction();
			for (int i = 0; i < list.size; i++)
				sum.add(list.utility[i], run.transactionUtilities[bases.tid[list.baseIndex[i]]]);
			return sum;
		}
	}

	/**
	 * Input, output and thresholds owned by one execution of this independent
	 * miner.
	 */
	private final class Run {
		/** The original input path for this run. */
		final Path input;
		/** The actual minimum mean-occupancy threshold. */
		final double beta;
		/** The downward-rounded threshold used only for safe pruning. */
		final double betaLower;
		/** The relative minimum-support threshold. */
		private final double alpha;
		/** The canonical decimal threshold used for exact boundary checks. */
		private final BigDecimal exactBeta;
		/** The output writer owned by this run. */
		private final BufferedWriter writer;
		/** The reusable numeric-sorted output item buffer. */
		private int[] outputItems = new int[0];
		/**
		 * The reusable output-line builder, cleared before every qualifying itemset.
		 */
		private final StringBuilder outputLine = new StringBuilder();
		/** The reusable character buffer passed to the output writer. */
		private char[] outputCharacters = new char[0];
		/** The original declared transaction utilities indexed by TID. */
		long[] transactionUtilities;

		/**
		 * Create a per-run input, output and threshold context.
		 * 
		 * @param input  the original input path
		 * @param writer the owned output writer
		 * @param alpha  the inclusive relative support threshold
		 * @param beta   the inclusive mean-occupancy threshold
		 */
		Run(Path input, BufferedWriter writer, double alpha, double beta) {
			this.input = input;
			this.writer = writer;
			this.alpha = alpha;
			this.beta = beta;
			betaLower = Math.nextDown(beta);
			exactBeta = BigDecimal.valueOf(beta);
		}

		/**
		 * Initialize the absolute support threshold and original-utility storage.
		 * 
		 * @param count the number of original transactions
		 */
		void initialize(int count) {
			transactionCount = count;
			minSupportCount = HUOUtility.minimumSupport(alpha, count);
			transactionUtilities = new long[count];
		}

		/**
		 * Reject a changed transaction count between the two initialization scans.
		 * 
		 * @param count the number of transactions observed in the second scan
		 * @throws IOException if the input transaction count changed
		 */
		void checkSecondScan(int count) throws IOException {
			if (count != transactionCount)
				throw new IOException("The input changed between database scans");
		}

		/**
		 * Record an original transaction denominator during initialization.
		 * 
		 * @param tid     the original transaction identifier
		 * @param utility the positive declared transaction utility
		 * @throws IOException if the second scan exceeds the original transaction count
		 */
		void setTransactionUtility(int tid, long utility) throws IOException {
			if (tid >= transactionUtilities.length)
				throw new IOException("The input changed between database scans");
			transactionUtilities[tid] = utility;
		}

		/**
		 * Test a summed-occupancy candidate with exact arithmetic at the output
		 * boundary.
		 * 
		 * @param prefix       the shared prefix buffer
		 * @param length       the number of valid prefix items
		 * @param support      the exact support count
		 * @param sumOccupancy the compensated actual occupancy sum
		 * @param exactSum     the lazy exact sum used near the threshold
		 * @throws IOException if an output record cannot be written
		 */
		void consider(int[] prefix, int length, int support, double sumOccupancy,
				Supplier<HUOUtility.Fraction> exactSum) throws IOException {
			considerAverage(prefix, length, support, support == 0 ? 0 : sumOccupancy / support, exactSum);
		}

		/**
		 * Write a qualifying itemset using a cached mean without re-aggregating its
		 * list.
		 * 
		 * @param prefix   the current prefix buffer
		 * @param length   the number of valid prefix items
		 * @param support  the exact support count
		 * @param average  the cached mean utility occupancy
		 * @param exactSum the lazy exact sum for a near-threshold decision
		 * @throws IOException if an output record cannot be written
		 */
		void considerAverage(int[] prefix, int length, int support, double average,
				Supplier<HUOUtility.Fraction> exactSum) throws IOException {
			candidateCount++;
			if (support < minSupportCount)
				return;
			boolean high;
			if (Math.abs(average - beta) <= 1e-12) {
				exactCheckCount++;
				high = exactSum.get().averageAtLeast(support, exactBeta);
			} else {
				high = average >= beta;
			}
			if (!high)
				return;
			if (outputItems.length < length)
				outputItems = new int[HUOUtility.grownCapacity(outputItems.length, length)];
			System.arraycopy(prefix, 0, outputItems, 0, length);
			Arrays.sort(outputItems, 0, length);
			outputLine.setLength(0);
			for (int i = 0; i < length; i++) {
				if (i != 0)
					outputLine.append(' ');
				outputLine.append(outputItems[i]);
			}
			outputLine.append(" #SUP: ").append(support).append(" #UO: ").append(Math.max(0, Math.min(1, average)));
			int characters = outputLine.length();
			if (outputCharacters.length < characters) {
				outputCharacters = new char[HUOUtility.grownCapacity(outputCharacters.length, characters)];
			}
			outputLine.getChars(0, characters, outputCharacters, 0);
			writer.write(outputCharacters, 0, characters);
			writer.newLine();
			huopCount++;
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
		System.out.println("=============  HUOMIL v. 2.68 - STATS =============");
		System.out.println(" High utility-occupancy itemset count: " + huopCount);
		if (DEBUG) {
			System.out.println(" Transactions: " + transactionCount);
			System.out.println(" Minimum support: " + minSupportCount);
			System.out.println(" Candidate itemsets: " + candidateCount);
			System.out.println(" Input database scans: " + databaseScanCount);
			System.out.println(" Conditional indexed lists: " + conditionalListCount);
			System.out.println(" Conditional entries: " + indexedEntryCount);
			System.out.println(" Direct link traversals: " + linkTraversalCount);
			System.out.println(" Loose-bound prunes: " + looseBoundPrunedCount);
			System.out.println(" Tight-bound prunes: " + tightBoundPrunedCount);
			System.out.println(" Tight-bound vectors evaluated: " + tightBoundEvaluationCount);
			System.out.println(" Tight-bound buffer growths: " + boundBufferGrowthCount);
			System.out.println(" Nonempty-prefix subtractions: " + prefixSubtractionCount);
			System.out.println(" Exact boundary checks: " + exactCheckCount);
		}
		System.out.println(" Maximum memory usage: " + peakMemory + " MB");
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===============================================");
	}
}
