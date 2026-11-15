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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the OCEAN algorithm by Shen et al., "OCEAN: Fast
 * Discovery of High Utility Occupancy Itemsets", PAKDD 2016, pp. 354-365, DOI:
 * 10.1007/978-3-319-31753-3_29.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoOCEAN {
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

	/**
	 * The number of attempted prefix/global-singleton merges, including empty ones.
	 */
	public long joinCount;
	/** The number of subtrees pruned by the occupancy bound. */
	public long boundPrunedCount;
	/** The number of merges extending a prefix of at least two items. */
	public long multiItemPrefixJoinCount;
	/** The number of input scans started by the most recent run. */
	public int databaseScanCount;
	/** The largest number of values held by an Algorithm 1 selection heap. */
	public int maxTopKSize;
	/**
	 * The deprecated common-prefix join counter, always zero for disjoint OCEAN
	 * merges.
	 */
	@Deprecated
	public long nonEmptyPrefixJoinCount;
	/** Whether to use the paper's occupancy-bound subtree pruning. */
	private boolean upperBoundPruning = true;



	/** A raw utility-list tuple; singleton remaining utility is finalized once. */
	static final class Element {
		/** The original transaction identifier. */
		final int tid;
		/** The exact itemset utility in this transaction. */
		final long utility;
		/** The remaining utility finalized once in the global TWU order. */
		long remaining;

		/**
		 * Create a raw utility-list tuple.
		 * 
		 * @param tid       the original transaction identifier
		 * @param utility   the exact itemset utility
		 * @param remaining the remaining utility in the fixed global order
		 */
		Element(int tid, long utility, long remaining) {
			this.tid = tid;
			this.utility = utility;
			this.remaining = remaining;
		}
	}

	/** A raw utility list, with the global rank of its last item. */
	static final class UtilityList {
		/** The global rank of the itemset's final item. */
		final int rank;
		/** The raw utility tuples in ascending original transaction order. */
		final List<Element> elements;

		/**
		 * Create an empty raw utility list.
		 * 
		 * @param rank the global rank of the final item
		 */
		UtilityList(int rank) {
			this(rank, new ArrayList<>());
		}

		/**
		 * Wrap an existing singleton tuple column in a ranked utility list.
		 * 
		 * @param rank     the global rank of the singleton
		 * @param elements the singleton tuples collected by the input scan
		 */
		UtilityList(int rank, List<Element> elements) {
			this.rank = rank;
			this.elements = elements;
		}
	}

	/**
	 * Temporary single-scan data; no horizontal transaction database is retained.
	 */
	private static final class InitialLists {
		/** The raw singleton tuples collected during the one input scan. */
		final Map<Integer, List<Element>> columns = new HashMap<>();
		/** The exact transaction-weighted utility of each observed item. */
		final Map<Integer, BigInteger> twu = new HashMap<>();
		/** The growable buffer of original transaction utilities. */
		long[] totals = new long[16];

		/**
		 * Collect raw singleton tuples and exact TWUs during the one input scan.
		 * 
		 * @param transaction the original parsed transaction
		 * @param tid         the original transaction identifier
		 */
		@SuppressWarnings("unused")
		void add(HUOUtility.Transaction transaction, int tid) {
			if (tid == totals.length) {
				int capacity = (int) Math.min(Integer.MAX_VALUE, Math.max(tid + 1L, 2L * totals.length));
				totals = Arrays.copyOf(totals, capacity);
			}
			totals[tid] = transaction.total;
			BigInteger total = BigInteger.valueOf(transaction.total);
			for (HUOUtility.Item item : transaction.items) {
				columns.computeIfAbsent(item.item, key -> new ArrayList<>()).add(new Element(tid, item.utility, 0));
				twu.merge(item.item, total, BigInteger::add);
			}
		}
	}

	/**
	 * Enable or disable Algorithm 1's occupancy-bound subtree pruning.
	 * 
	 * @param enabled whether to apply the top-K bound
	 */
	public void setUpperBoundPruningEnabled(boolean enabled) {
		upperBoundPruning = enabled;
	}

	/**
	 * Mine frequent high utility-occupancy itemsets using global singleton
	 * extensions.
	 * 
	 * @param input               the SPMF utility transaction file
	 * @param output              the distinct output file
	 * @param minSupportRatio     the inclusive support ratio in (0, 1]
	 * @param minUtilityOccupancy the inclusive mean-occupancy threshold in (0, 1]
	 * @throws IOException if input, validation or output fails
	 */
	public void runAlgorithm(String input, String output, double minSupportRatio, double minUtilityOccupancy)
			throws IOException {
		joinCount = boundPrunedCount = multiItemPrefixJoinCount = nonEmptyPrefixJoinCount = 0;
		databaseScanCount = maxTopKSize = 0;
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
	 * Perform Algorithm 2 and finalize remaining utilities in the final TWU order.
	 * 
	 * @param run the current input, output and threshold context
	 * @throws IOException if input scanning or recursive output fails
	 */
	private void mine(Run run) throws IOException {
		InitialLists initial = new InitialLists();
		databaseScanCount++;
		int count = HUOUtility.scan(run.input, initial::add);
		MemoryLogger.getInstance().checkMemory();
		run.initialize(count);
		System.arraycopy(initial.totals, 0, run.transactionUtilities, 0, count);

		List<Integer> items = new ArrayList<>(initial.columns.keySet());
		items.sort(Comparator.comparing((Integer item) -> initial.twu.get(item)).thenComparingInt(Integer::intValue));
		int[] order = items.stream().mapToInt(Integer::intValue).toArray();
		UtilityList[] singletons = new UtilityList[order.length];
		for (int rank = 0; rank < order.length; rank++) {
			singletons[rank] = new UtilityList(rank, initial.columns.get(order[rank]));
		}

		// The final global order is also the order used by every remaining sum.
		// This is an in-memory pass over stored singleton tuples, not a DB rescan.
		long[] remaining = new long[count];
		for (int rank = order.length - 1; rank >= 0; rank--) {
			for (Element element : singletons[rank].elements) {
				element.remaining = remaining[element.tid];
				remaining[element.tid] = Math.addExact(remaining[element.tid], element.utility);
			}
		}
		new Search(run, order, singletons).prune(Arrays.asList(singletons), 0);
	}

	/** The current run's depth-first global-singleton search state. */
	private final class Search {
		/** The current input, output and threshold context. */
		final Run run;
		/** The fixed ascending-TWU item order. */
		final int[] order;
		/** The original global singleton lists used by every extension. */
		final UtilityList[] singletons;
		/** The reusable depth-first prefix buffer. */
		final int[] prefix;
		/**
		 * The lazily allocated primitive top-K heap reused by all bound evaluations.
		 */
		double[] topK;

		/**
		 * Create the current run's global-singleton search state.
		 * 
		 * @param run        the current run context
		 * @param order      the fixed ascending-TWU item order
		 * @param singletons the original globally ranked singleton lists
		 */
		Search(Run run, int[] order, UtilityList[] singletons) {
			this.run = run;
			this.order = order;
			this.singletons = singletons;
			prefix = new int[order.length];
		}

		/**
		 * Perform Algorithm 3's output, support, bound and global-extension steps.
		 * 
		 * @param extensions the prefix work list consumed only for nonglobal levels
		 * @param length     the current common-prefix length
		 * @throws IOException if an output record cannot be written
		 */
		void prune(List<UtilityList> extensions, int length) throws IOException {
			MemoryLogger.getInstance().checkMemory();
			for (int index = 0; index < extensions.size(); index++) {
				// Global singletons remain immutable merge operands throughout the DFS.
				UtilityList x = length == 0 ? extensions.get(index) : extensions.set(index, null);
				prefix[length] = order[x.rank];
				if (x.elements.size() < minSupportCount) {
					candidateCount++;
					continue;
				}
				HUOUtility.Sum sum = new HUOUtility.Sum();
				for (Element element : x.elements)
					sum.add(element.utility / (double) run.transactionUtilities[element.tid]);
				run.consider(prefix, length + 1, x.elements.size(), sum.value(), () -> exactSum(x));
				double bound = upperBound(x);
				if (upperBoundPruning && bound < run.betaLower) {
					boundPrunedCount++;
					continue;
				}
				List<UtilityList> children = null;
				for (int rank = x.rank + 1; rank < singletons.length; rank++) {
					UtilityList singleton = singletons[rank];
					UtilityList child = merge(x, singleton, length + 1);
					if (!child.elements.isEmpty()) {
						if (children == null)
							children = new ArrayList<>();
						children.add(child);
					}
				}
				if (children != null)
					prune(children, length + 1);
			}
		}

		/**
		 * Merge disjoint prefix and global-singleton lists using Section 4.2's full TID
		 * intersection.
		 * 
		 * @param x         the current raw prefix list
		 * @param singleton the original right singleton list
		 * @param length    the selected prefix length
		 * @return the new raw utility list, including an empty intersection
		 */
		UtilityList merge(UtilityList x, UtilityList singleton, int length) {
			joinCount++;
			if (length > 1)
				multiItemPrefixJoinCount++;
			UtilityList result = new UtilityList(singleton.rank);
			int a = 0;
			int b = 0;
			while (a < x.elements.size() && b < singleton.elements.size()) {
				Element left = x.elements.get(a);
				Element right = singleton.elements.get(b);
				if (left.tid < right.tid) {
					a++;
				} else if (left.tid > right.tid) {
					b++;
				} else {
					long utility = Math.addExact(left.utility, right.utility);
					result.elements.add(new Element(left.tid, utility, right.remaining));
					a++;
					b++;
				}
			}
			return result;
		}

		/**
		 * Select and average Algorithm 1's largest minimum-support transaction bounds.
		 * 
		 * @param list the frequent raw utility list
		 * @return an outward-rounded top-K occupancy average
		 */
		double upperBound(UtilityList list) {
			if (topK == null)
				topK = new double[minSupportCount];
			int size = 0;
			for (Element element : list.elements) {
				double value = HUOUtility.upperRatio(Math.addExact(element.utility, element.remaining),
						run.transactionUtilities[element.tid]);
				if (size < minSupportCount) {
					int index = size++;
					while (index > 0) {
						int parent = (index - 1) >>> 1;
						if (topK[parent] <= value)
							break;
						topK[index] = topK[parent];
						index = parent;
					}
					topK[index] = value;
				} else if (value > topK[0])
					replaceMinimum(value);
			}
			maxTopKSize = Math.max(maxTopKSize, size);
			double sum = 0;
			for (int i = 0; i < size; i++)
				sum = HUOUtility.upperAdd(sum, topK[i]);
			return HUOUtility.upperDivide(sum, minSupportCount);
		}

		/**
		 * Replace the heap minimum without boxing values or allocating another heap.
		 * 
		 * @param value the new value larger than the current heap minimum
		 */
		void replaceMinimum(double value) {
			int index = 0;
			while (index < minSupportCount / 2) {
				int child = 2 * index + 1;
				if (child + 1 < minSupportCount && topK[child + 1] < topK[child])
					child++;
				if (topK[child] >= value)
					break;
				topK[index] = topK[child];
				index = child;
			}
			topK[index] = value;
		}

		/**
		 * Sum exact occupancy fractions for a near-threshold output decision.
		 * 
		 * @param list the selected raw utility list
		 * @return the exact sum of supporting-transaction occupancy fractions
		 */
		HUOUtility.Fraction exactSum(UtilityList list) {
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
		System.out.println("=============  OCEAN  v. 2.68 - STATS =============");
		System.out.println(" High utility-occupancy itemset count: " + huopCount);
		if (DEBUG) {
			System.out.println(" Transactions: " + transactionCount);
			System.out.println(" Minimum support: " + minSupportCount);
			System.out.println(" Candidate itemsets: " + candidateCount);
			System.out.println(" Input database scans: " + databaseScanCount);
			System.out.println(" Global singleton extension merges: " + joinCount);
			System.out.println(" Merges extending multi-item prefixes: " + multiItemPrefixJoinCount);
			System.out.println(" Occupancy-bound prunes: " + boundPrunedCount);
			System.out.println(" Largest top-K heap: " + maxTopKSize);
			System.out.println(" Exact boundary checks: " + exactCheckCount);
		}
		System.out.println(" Maximum memory usage: " + peakMemory + " MB");
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("===============================================");
	}
}
