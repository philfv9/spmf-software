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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the HUOPM algorithm. Reference: Gan et al., IEEE
 * Transactions on Cybernetics 50, 1195-1208, DOI: 10.1109/TCYB.2019.2896267,
 * Algorithms 1-4.
 *
 * Each completed UO-list has a separate FU record caching support and the mean
 * actual uo/ruo values specified by Definition 12 and Figures 2-3. Algorithm 1
 * joins sibling lists and subtracts the common prefix once. Its
 * remaining-support counter is local rather than mutating a source FU record,
 * and Strategy 4 preserves support equal to the minimum threshold. Raw utility
 * companions keep output decisions and rounded bounds safe.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoHUOPM {
	/** Flag to indicate if debug information should be presented. */
	private static final boolean DEBUG = false;
	/** Peak memory of the last execution, as detected by SPMF MemoryLogger. */
	private double peakMemory;
	/** The start time of the last execution. */
	public long startTimestamp;
	/** The end time of the last execution. */
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

	/** The number of attempted UO-list joins. */
	public long joinCount;
	/** The number of joins abandoned by remaining-support pruning. */
	public long earlyAbandonedCount;
	/** The number of completed lists rejected by post-join support pruning. */
	public long supportPrunedListCount;
	/** The number of subtrees rejected by the occupancy bound. */
	public long boundPrunedCount;
	/** The number of joins subtracting a nonempty common prefix. */
	public long nonEmptyPrefixJoinCount;
	/** The number of input scans started by the latest run. */
	public int databaseScanCount;
	/** The number of completed FU-table records built in the latest run. */
	public long fuTableCount;
	/**
	 * The number of backing-array growths for the run's reusable sorted-bound
	 * buffer.
	 */
	public int boundBufferGrowthCount;
	/** Whether to apply the top-support occupancy bound. */
	private boolean upperBoundPruning = true;
	/** Whether to stop a join when its remaining support cannot qualify. */
	private boolean remainingSupportPruning = true;


	/**
	 * A UO-list triple with exact arithmetic companions for itemset and remaining
	 * utility.
	 */
	static final class Element {
		/** The original transaction identifier. */
		final int tid;
		/** The exact itemset utility used for arithmetic and boundary decisions. */
		final long utility;
		/** The exact remaining utility in the fixed global order. */
		final long remaining;
		/** The actual utility occupancy of the itemset in this transaction. */
		final double uo;
		/** The actual remaining utility occupancy in this transaction. */
		final double ruo;

		/**
		 * Create a tuple without rounding its stored occupancy fields upward.
		 * 
		 * @param tid       the original transaction identifier
		 * @param utility   the exact itemset utility
		 * @param remaining the exact remaining utility
		 * @param total     the original transaction utility
		 */
		Element(int tid, long utility, long remaining, long total) {
			this.tid = tid;
			this.utility = utility;
			this.remaining = remaining;
			uo = utility / (double) total;
			ruo = remaining / (double) total;
		}
	}

	/** A Definition 12 FU-table record with cached support and mean occupancies. */
	static final class FUEntry {
		/** The number of supporting transactions accumulated in this record. */
		int support;
		/** The completed mean utility occupancy. */
		double uo;
		/** The completed mean remaining utility occupancy. */
		double ruo;
		/** The compensated actual utility-occupancy numerator of the mean. */
		final HUOUtility.Sum sumUO = new HUOUtility.Sum();
		/** The compensated actual remaining-occupancy numerator of the mean. */
		final HUOUtility.Sum sumRUO = new HUOUtility.Sum();

		/**
		 * Accumulate a tuple's support and actual occupancy information.
		 * 
		 * @param element the newly appended UO-list tuple
		 */
		void add(Element element) {
			support++;
			sumUO.add(element.uo);
			sumRUO.add(element.ruo);
		}

		/** Cache the mean occupancies after all tuples have been accumulated. */
		void finish() {
			uo = support == 0 ? 0 : sumUO.value() / support;
			ruo = support == 0 ? 0 : sumRUO.value() / support;
		}
	}

	/**
	 * A UO-list whose FU record is addressed by its last rank and current prefix.
	 */
	static final class UOList {
		/** The global rank of the final item. */
		final int rank;
		/** The UO-list triples in ascending original transaction order. */
		final List<Element> elements;
		/** The separate FU-table summary used by HUOP-Search. */
		final FUEntry fu = new FUEntry();

		/**
		 * Create a UO-list and an initially empty FU-table record.
		 * 
		 * @param rank     the global rank of the final item
		 * @param capacity the initial tuple capacity
		 */
		UOList(int rank, int capacity) {
			this.rank = rank;
			elements = new ArrayList<>(capacity);
		}

		/**
		 * Append a tuple and update its associated FU record in constant time.
		 * 
		 * @param element the new UO-list tuple
		 */
		void add(Element element) {
			elements.add(element);
			fu.add(element);
		}
	}

	/**
	 * Enable or disable top-support occupancy-bound pruning.
	 * 
	 * @param enabled whether to apply Strategy 2
	 */
	public void setUpperBoundPruningEnabled(boolean enabled) {
		upperBoundPruning = enabled;
	}

	/**
	 * Enable or disable local remaining-support early abandoning.
	 * 
	 * @param enabled whether to apply Strategy 3
	 */
	public void setRemainingSupportPruningEnabled(boolean enabled) {
		remainingSupportPruning = enabled;
	}

	/**
	 * Mine frequent high utility-occupancy itemsets using UO-lists and FU records.
	 * 
	 * @param input               the SPMF utility transaction file
	 * @param output              the distinct output file
	 * @param minSupportRatio     the inclusive support ratio in (0, 1]
	 * @param minUtilityOccupancy the inclusive mean-occupancy threshold in (0, 1]
	 * @throws IOException if input, validation or output fails
	 */
	public void runAlgorithm(String input, String output, double minSupportRatio, double minUtilityOccupancy)
			throws IOException {
		joinCount = earlyAbandonedCount = supportPrunedListCount = boundPrunedCount = nonEmptyPrefixJoinCount = fuTableCount = 0;
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
	 * Perform Algorithm 2's two scans and support-ordered singleton initialization.
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
		List<UOList> roots = new ArrayList<>(order.length);
		for (int rank = 0; rank < order.length; rank++)
			roots.add(new UOList(rank, metadata.support.get(order[rank])));
		HUOUtility.RevisedTransaction revised = new HUOUtility.RevisedTransaction();
		databaseScanCount++;
		int count = HUOUtility.scan(run.input, (transaction, tid) -> {
			run.setTransactionUtility(tid, transaction.total);
			revised.revise(transaction, ranks);
			long remaining = 0;
			for (int i = revised.size - 1; i >= 0; i--) {
				long utility = revised.utility(transaction, i);
				roots.get(revised.rank(i)).add(new Element(tid, utility, remaining, transaction.total));
				remaining = Math.addExact(remaining, utility);
			}
		});
		run.checkSecondScan(count);
		for (UOList root : roots) {
			root.fu.finish();
			fuTableCount++;
		}
		Search search = new Search(run, order);
		search.search(null, roots, 0);
		boundBufferGrowthCount = search.bounds.growthCount;
	}

	/** The current run's implicit FU-tree search state. */
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
		 * Create a support-ordered FU-tree search state.
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
		 * Perform Algorithm 3's FU-table output tests and sibling extensions.
		 * 
		 * @param parent     the common-prefix UO-list, or null at the root
		 * @param extensions the current frequent sibling work list consumed in order
		 * @param length     the current common-prefix length
		 * @throws IOException if an output record cannot be written
		 */
		void search(UOList parent, List<UOList> extensions, int length) throws IOException {
			MemoryLogger.getInstance().checkMemory();
			for (int i = 0; i < extensions.size(); i++) {
				// Earlier siblings cannot be used by later joins; keep only this local
				// reference.
				UOList x = extensions.set(i, null);
				prefix[length] = order[x.rank];
				run.considerAverage(prefix, length + 1, x.fu.support, x.fu.uo, () -> exactSum(x));
				if (x.fu.support < minSupportCount)
					continue;
				double bound = upperBound(x);
				if (upperBoundPruning && bound < run.betaLower) {
					boundPrunedCount++;
					continue;
				}
				List<UOList> children = null;
				for (int j = i + 1; j < extensions.size(); j++) {
					UOList child = construct(parent, x, extensions.get(j), length + 1);
					// Strategy 4 rejects support strictly below, not equal to, k.
					if (child != null && child.fu.support >= minSupportCount) {
						if (children == null)
							children = new ArrayList<>();
						children.add(child);
					} else if (child != null)
						supportPrunedListCount++;
				}
				if (children != null)
					search(x, children, length + 1);
			}
		}

		/**
		 * Perform Algorithm 1's sibling join with a nonmutating remaining-support
		 * counter.
		 * 
		 * @param parent the common-prefix UO-list, or null at the root
		 * @param x      the left sibling list
		 * @param y      the right sibling list
		 * @param length the number of valid left-prefix items
		 * @return the completed child list, or null when Strategy 3 abandons it
		 */
		UOList construct(UOList parent, UOList x, UOList y, int length) {
			joinCount++;
			if (parent != null)
				nonEmptyPrefixJoinCount++;
			int parentSupport = parent == null ? transactionCount : parent.fu.support;
			int capacity = Math.max(1, (int) ((long) x.fu.support * y.fu.support / parentSupport));
			UOList result = new UOList(y.rank, capacity);
			int remainingSupport = x.fu.support;
			int a = 0;
			int b = 0;
			int p = 0;
			while (a < x.elements.size()) {
				Element left = x.elements.get(a);
				while (b < y.elements.size() && y.elements.get(b).tid < left.tid)
					b++;
				if (b == y.elements.size()) {
					// All trailing left tuples are now known misses without rescanning them.
					remainingSupport -= x.elements.size() - a;
					a = x.elements.size();
				} else if (left.tid == y.elements.get(b).tid) {
					Element right = y.elements.get(b++);
					long common = 0;
					if (parent != null) {
						while (parent.elements.get(p).tid < left.tid)
							p++;
						if (parent.elements.get(p).tid != left.tid)
							throw new IllegalStateException("Missing common prefix tuple");
						common = parent.elements.get(p).utility;
					}
					long utility = Math.addExact(Math.subtractExact(left.utility, common), right.utility);
					result.add(new Element(left.tid, utility, right.remaining, run.transactionUtilities[left.tid]));
					a++;
				} else {
					remainingSupport--;
					a++;
				}
				if (remainingSupportPruning && remainingSupport < minSupportCount) {
					earlyAbandonedCount++;
					return null;
				}
			}
			result.fu.finish();
			fuTableCount++;
			return result;
		}

		/**
		 * Perform Algorithm 4's sorted top-support bound with outward rounding.
		 * 
		 * @param list the frequent UO-list
		 * @return a conservative average of the largest minimum-support ratios
		 */
		double upperBound(UOList list) {
			int size = list.elements.size();
			double[] values = bounds.prepare(size);
			for (int i = 0; i < size; i++) {
				Element element = list.elements.get(i);
				values[i] = HUOUtility.upperRatio(Math.addExact(element.utility, element.remaining),
						run.transactionUtilities[element.tid]);
			}
			return HUOUtility.topBound(values, size, minSupportCount);
		}

		/**
		 * Sum exact occupancy fractions for a near-threshold output decision.
		 * 
		 * @param list the selected UO-list
		 * @return the exact sum of supporting-transaction occupancy fractions
		 */
		HUOUtility.Fraction exactSum(UOList list) {
			HUOUtility.Fraction sum = new HUOUtility.Fraction();
			for (Element element : list.elements)
				sum.add(element.utility, run.transactionUtilities[element.tid]);
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
		System.out.println("=============  HUOPM  v. 2.68 - STATS =============");
		System.out.println(" High utility-occupancy itemset count: " + huopCount);
		if (DEBUG) {
			System.out.println(" Transactions: " + transactionCount);
			System.out.println(" Minimum support: " + minSupportCount);
			System.out.println(" Candidate itemsets: " + candidateCount);
			System.out.println(" Input database scans: " + databaseScanCount);
			System.out.println(" Completed FU-table records: " + fuTableCount);
			System.out.println(" Sorted-bound buffer growths: " + boundBufferGrowthCount);
			System.out.println(" UO-list joins: " + joinCount);
			System.out.println(" Remaining-support abandoned joins: " + earlyAbandonedCount);
			System.out.println(" Post-join support prunes: " + supportPrunedListCount);
			System.out.println(" Occupancy-bound prunes: " + boundPrunedCount);
			System.out.println(" Nonempty-prefix joins: " + nonEmptyPrefixJoinCount);
			System.out.println(" Exact boundary checks: " + exactCheckCount);
		}
		System.out.println(" Maximum memory usage: " + peakMemory + " MB");
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===============================================");
	}
}
