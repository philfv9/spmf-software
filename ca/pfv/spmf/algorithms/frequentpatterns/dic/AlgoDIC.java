package ca.pfv.spmf.algorithms.frequentpatterns.dic;

/*
 * Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Optimized implementation of the DIC (Dynamic Itemset Counting) algorithm for
 * mining frequent itemsets from a transaction database.
 * 
 * <p>
 * DIC improves upon Apriori by starting to count candidate itemsets before
 * completing a full database pass. The database is divided into buckets, and
 * new candidates can be added at bucket boundaries once their subsets are
 * confirmed frequent.
 * </p>
 * 
 * <h3>Key DIC Properties:</h3>
 * <ul>
 * <li>Candidates promoted IMMEDIATELY when subsets become frequent
 * (mid-pass)</li>
 * <li>Smaller buckets → earlier promotion → more dynamic counting</li>
 * <li>Active itemsets counted continuously across multiple passes</li>
 * <li>Only joins itemsets from the same "active frontier"</li>
 * </ul>
 * 
 * <h3>Algorithm States:</h3>
 * 
 * <pre>
 *   DASHED_BOX    → Suspect, waiting to start counting
 *   SOLID_BOX     → Currently being counted
 *   DASHED_CIRCLE → Confirmed infrequent
 *   SOLID_CIRCLE  → Confirmed frequent
 * </pre>
 * 
 * <h3>Reference:</h3>
 * <p>
 * Brin, S., Motwani, R., Ullman, J. D., &amp; Tsur, S. (1997). Dynamic itemset
 * counting and implication rules for market basket data. In ACM SIGMOD Record
 * (Vol. 26, No. 2, pp. 255-264).
 * </p>
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoDIC {

	/** The default bucket size M */
	private static final int DEFAULT_BUCKET_SIZE = 200;

	// ==================== Statistics ====================
	/** Timestamp when algorithm execution started (milliseconds). */
	private long startTimestamp;

	/** Timestamp when algorithm execution ended (milliseconds). */
	private long endTimestamp;

	/** Total number of frequent itemsets discovered. */
	private int itemsetCount;

	/** Total number of transactions in the database. */
	private int transactionCount;

	/** Number of complete database passes performed. */
	private int passCount;

	/** Total number of candidate itemsets generated. */
	private long candidatesGenerated;

	/** Maximum memory usage during execution (megabytes). */
	private double maxMemory;

	// ==================== Parameters ====================
	/** Minimum support threshold as absolute count. */
	private int minSupportAbsolute;

	/** Number of transactions per bucket (M parameter). */
	private int bucketSize;

	/** Total number of buckets in the database. */
	private int numBuckets;

	// ==================== Data Structures ====================
	/** In-memory transaction database as sorted integer arrays. */
	private List<int[]> database;
	/**
	 * Sorted array of all unique it ems in the database.
	 */
	private int[] allItems;

	/**
	 * Represents the state of an itemset in the DIC lattice.
	 */
	private enum State {
		/** Suspect - waiting to start counting. */
		DASHED_BOX,
		/** Currently being counted. */
		SOLID_BOX,
		/** Confirmed infrequent. */
		DASHED_CIRCLE,
		/** Confirmed frequent. */
		SOLID_CIRCLE
	}

	/**
	 * Holds metadata for an itemset during DIC processing.
	 */
	private static class ItemsetData {
		/** Current state in the DIC lattice. */
		State state;
		/** Support count accumulated so far. */
		int count;
		/** Bucket index where counting started (for tracking completion). */
		int startBucket;
		/** Number of buckets processed for this itemset since activation. */
		int bucketsProcessed;

		/**
		 * Creates new itemset metadata.
		 * 
		 * @param state       initial state
		 * @param startBucket bucket index where counting begins
		 */
		ItemsetData(State state, int startBucket) {
			this.state = state;
			this.count = 0;
			this.startBucket = startBucket;
			this.bucketsProcessed = 0;
		}
	}

	/** Master map of all itemsets to their metadata. */
	private Map<IntArrayWrapper, ItemsetData> itemsets;

	/** Active itemsets currently being counted (SOLID_BOX state only). */
	private Set<IntArrayWrapper> activeItemsets;

	/** Pending itemsets waiting to be promoted (DASHED_BOX state only). */
	private Set<IntArrayWrapper> pendingItemsets;

	/** Frequent itemsets organized by size for candidate generation. */
	private Map<Integer, Set<IntArrayWrapper>> frequentBySize;

	/** Reusable buffer for subset checking to reduce allocations. */
	private int[] subsetBuffer;

	/**
	 * Default constructor.
	 */
	public AlgoDIC() {
	}

	/**
	 * Runs the DIC algorithm to find all frequent itemsets with a default bucket
	 * size of 200.
	 * 
	 * <p>
	 * The algorithm processes the database in buckets of size {@code bucketSize},
	 * dynamically adding new candidates as their subsets become confirmed frequent.
	 * </p>
	 * 
	 * @param input      path to the input transaction database file
	 * @param output     path to write the frequent itemsets
	 * @param minSupport minimum support threshold in [0, 1]
	 * @throws IOException              if file I/O fails
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public void runAlgorithm(String input, String output, double minSupport) throws IOException {

		runAlgorithm(input, output, minSupport, DEFAULT_BUCKET_SIZE);
	}

	/**
	 * Runs the DIC algorithm to find all frequent itemsets.
	 * 
	 * <p>
	 * The algorithm processes the database in buckets of size {@code bucketSize},
	 * dynamically adding new candidates as their subsets become confirmed frequent.
	 * </p>
	 * 
	 * @param input      path to the input transaction database file
	 * @param output     path to write the frequent itemsets
	 * @param minSupport minimum support threshold in [0, 1]
	 * @param bucketSize number of transactions per bucket (M parameter)
	 * @throws IOException              if file I/O fails
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public void runAlgorithm(String input, String output, double minSupport, int bucketSize) throws IOException {

		// Initialize statistics
		startTimestamp = System.currentTimeMillis();
		itemsetCount = 0;
		passCount = 0;
		candidatesGenerated = 0;
		maxMemory = 0;
		MemoryLogger.getInstance().reset();

		this.bucketSize = bucketSize;

		// Initialize data structures
		itemsets = new HashMap<>();
		activeItemsets = new HashSet<>();
		pendingItemsets = new HashSet<>();
		frequentBySize = new HashMap<>();
		database = new ArrayList<>();

		readDatabase(input);

		if (transactionCount == 0) {
			writeOutput(output);
			endTimestamp = System.currentTimeMillis();
			return;
		}

		// Adjust bucket size if larger than database
		if (this.bucketSize > transactionCount) {
			this.bucketSize = transactionCount;
		}

		numBuckets = (transactionCount + this.bucketSize - 1) / this.bucketSize;

		minSupportAbsolute = (int) Math.ceil(minSupport * transactionCount);
		if (minSupportAbsolute < 1) {
			minSupportAbsolute = 1;
		}

		subsetBuffer = new int[allItems.length > 0 ? allItems.length : 1];

		runDIC();

		writeOutput(output);

		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Reads the transaction database from a file.
	 * 
	 * <p>
	 * Each line represents a transaction with space-separated item IDs. Lines
	 * starting with #, %, or @ are treated as comments.
	 * </p>
	 * 
	 * @param input path to the input file
	 * @throws IOException if file reading fails
	 */
	private void readDatabase(String input) throws IOException {
		Set<Integer> itemSet = new TreeSet<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] parts = line.trim().split("\\s+");
				List<Integer> items = new ArrayList<>();
				for (String part : parts) {
					if (part.isEmpty())
						continue;
					try {
						int item = Integer.parseInt(part);
						items.add(item);
						itemSet.add(item);
					} catch (NumberFormatException e) {
						// Skip invalid tokens
					}
				}

				if (!items.isEmpty()) {
					int[] transaction = new int[items.size()];
					for (int i = 0; i < items.size(); i++) {
						transaction[i] = items.get(i);
					}
					Arrays.sort(transaction);
					database.add(transaction);
				}
			}
		}

		transactionCount = database.size();
		allItems = new int[itemSet.size()];
		int idx = 0;
		for (int item : itemSet) {
			allItems[idx++] = item;
		}
	}

	/**
	 * Executes the main DIC algorithm loop.
	 * 
	 * <p>
	 * <strong>TRUE DIC BEHAVIOR:</strong>
	 * </p>
	 * <ul>
	 * <li>Promotes candidates IMMEDIATELY when subsets become frequent</li>
	 * <li>Generates candidates from "active frontier" only</li>
	 * <li>Counts passes based on actual full database scans</li>
	 * <li>Smaller buckets enable earlier promotion and more dynamic counting</li>
	 * </ul>
	 */
	private void runDIC() {
		if (allItems.length == 0) {
			return;
		}

		// Initialize all 1-itemsets as DASHED_BOX to start at bucket 0
		for (int item : allItems) {
			int[] itemset = new int[] { item };
			IntArrayWrapper key = new IntArrayWrapper(itemset);
			itemsets.put(key, new ItemsetData(State.DASHED_BOX, 0));
			pendingItemsets.add(key);
			candidatesGenerated++;
		}

		// Track absolute bucket position for pass counting
		int absoluteBucketCount = 0;
		int currentBucket = 0;

		// Continue while there's work remaining
		while (!activeItemsets.isEmpty() || !pendingItemsets.isEmpty()) {

			// STEP 1: Promote pending itemsets whose start bucket has been reached
			promoteReadyItemsets(currentBucket);

			// STEP 2: Process the current bucket
			processBucket(currentBucket);

			// STEP 3: Check for newly frequent itemsets
			List<IntArrayWrapper> newlyFrequent = checkForFrequentItemsets();

			// STEP 4: Generate candidates IMMEDIATELY from active frontier
			if (!newlyFrequent.isEmpty()) {
				// Start at NEXT bucket to avoid missing current bucket
				int nextBucket = (currentBucket + 1) % numBuckets;
				generateCandidates(newlyFrequent, nextBucket);
			}

			// STEP 5: Move to next bucket
			absoluteBucketCount++;
			currentBucket = (currentBucket + 1) % numBuckets;

			// Count passes every full database scan
			if (absoluteBucketCount > 0 && absoluteBucketCount % numBuckets == 0) {
				passCount++;
			}

			MemoryLogger.getInstance().checkMemory();
		}
	}

	/**
	 * Processes a single bucket, counting support for all active itemsets.
	 * 
	 * @param bucketIndex the index of the bucket to process
	 */
	private void processBucket(int bucketIndex) {
		int startTid = bucketIndex * bucketSize;
		int endTid = Math.min(startTid + bucketSize, transactionCount);

		if (activeItemsets.isEmpty()) {
			return;
		}

		// Count support for each transaction in bucket
		for (int tid = startTid; tid < endTid; tid++) {
			int[] transaction = database.get(tid);

			for (IntArrayWrapper key : activeItemsets) {
				ItemsetData data = itemsets.get(key);
				if (transactionContains(transaction, key.items)) {
					data.count++;
				}
			}
		}

		// Increment bucket counter for all active itemsets
		for (IntArrayWrapper key : activeItemsets) {
			itemsets.get(key).bucketsProcessed++;
		}
	}

	/**
	 * Promotes pending itemsets to active state when their start bucket is reached.
	 * 
	 * <p>
	 * Itemsets start counting immediately at their designated start bucket, which
	 * may be mid-pass if their subsets were confirmed mid-pass.
	 * </p>
	 * 
	 * @param currentBucket the bucket about to be processed
	 */
	private void promoteReadyItemsets(int currentBucket) {
		Iterator<IntArrayWrapper> iter = pendingItemsets.iterator();
		while (iter.hasNext()) {
			IntArrayWrapper key = iter.next();
			ItemsetData data = itemsets.get(key);

			// Promote if this is the designated start bucket
			if (data.startBucket == currentBucket) {
				data.state = State.SOLID_BOX;
				data.bucketsProcessed = 0; // Reset for this counting phase
				activeItemsets.add(key);
				iter.remove();
			}
		}
	}

	/**
	 * Checks active itemsets for frequency and removes completed ones.
	 * 
	 * <p>
	 * <strong>KEY DIC FEATURE:</strong> Runs EVERY bucket, not just at pass
	 * boundaries. Itemsets are marked frequent as soon as they've been counted in
	 * all buckets, enabling immediate candidate generation.
	 * </p>
	 * 
	 * @return list of itemsets that just became frequent (the "active frontier")
	 */
	private List<IntArrayWrapper> checkForFrequentItemsets() {
		List<IntArrayWrapper> newlyFrequent = new ArrayList<>();

		Iterator<IntArrayWrapper> iter = activeItemsets.iterator();
		while (iter.hasNext()) {
			IntArrayWrapper key = iter.next();
			ItemsetData data = itemsets.get(key);

			// Check if itemset has completed a full pass (all buckets processed)
			if (data.bucketsProcessed >= numBuckets) {
				if (data.count >= minSupportAbsolute) {
					// Mark as frequent - this is the "active frontier"
					data.state = State.SOLID_CIRCLE;
					newlyFrequent.add(key);
					addToFrequentBySize(key);
				} else {
					// Mark as infrequent
					data.state = State.DASHED_CIRCLE;
				}
				iter.remove(); // Remove from active set
			}
		}

		return newlyFrequent;
	}

	/**
	 * Generates candidate itemsets of size k+1 from newly frequent k-itemsets.
	 * 
	 * <p>
	 * <strong>FIX #2:</strong> Only joins itemsets from the same "active frontier"
	 * (those that became frequent together), not all previously frequent itemsets.
	 * This matches the original DIC paper's approach.
	 * </p>
	 * 
	 * @param newlyFrequent itemsets that just became confirmed frequent (active
	 *                      frontier)
	 * @param startBucket   bucket index where new candidates should start counting
	 */
	private void generateCandidates(List<IntArrayWrapper> newlyFrequent, int startBucket) {
		// Group newly frequent itemsets by size
		Map<Integer, List<int[]>> newBySize = new HashMap<>();
		for (IntArrayWrapper wrapper : newlyFrequent) {
			int size = wrapper.items.length;
			newBySize.computeIfAbsent(size, k -> new ArrayList<>()).add(wrapper.items);
		}

		for (Map.Entry<Integer, List<int[]>> entry : newBySize.entrySet()) {
			List<int[]> newFreqK = entry.getValue();

			// FIX: Only join within the active frontier, not with all previous frequent
			if (newFreqK.size() < 2) {
				// Need at least 2 itemsets of the same size to join
				continue;
			}

			List<int[]> sortedNew = new ArrayList<>(newFreqK);
			sortedNew.sort(this::compareArrays);

			// Join pairs from the newly frequent set only (active frontier)
			for (int i = 0; i < sortedNew.size(); i++) {
				for (int j = i + 1; j < sortedNew.size(); j++) {
					int[] itemset1 = sortedNew.get(i);
					int[] itemset2 = sortedNew.get(j);

					if (!sharePrefix(itemset1, itemset2))
						continue;

					int[] candidate = joinItemsets(itemset1, itemset2);
					if (candidate == null)
						continue;

					IntArrayWrapper candidateKey = new IntArrayWrapper(candidate);

					if (itemsets.containsKey(candidateKey))
						continue;
					if (!allSubsetsFrequent(candidate))
						continue;

					ItemsetData newData = new ItemsetData(State.DASHED_BOX, startBucket);
					itemsets.put(candidateKey, newData);
					pendingItemsets.add(candidateKey);
					candidatesGenerated++;
				}
			}
		}
	}

	/**
	 * Compares two integer arrays lexicographically.
	 * 
	 * @param a first array
	 * @param b second array
	 * @return negative if a &lt; b, zero if equal, positive if a &gt; b
	 */
	private int compareArrays(int[] a, int[] b) {
		int len = Math.min(a.length, b.length);
		for (int i = 0; i < len; i++) {
			if (a[i] != b[i]) {
				return Integer.compare(a[i], b[i]);
			}
		}
		return Integer.compare(a.length, b.length);
	}

	/**
	 * Checks if two itemsets share the same (k-1)-prefix.
	 * 
	 * @param a first itemset
	 * @param b second itemset
	 * @return {@code true} if they share a prefix and can be joined
	 */
	private boolean sharePrefix(int[] a, int[] b) {
		if (a.length != b.length)
			return false;
		for (int i = 0; i < a.length - 1; i++) {
			if (a[i] != b[i])
				return false;
		}
		return true;
	}

	/**
	 * Joins two k-itemsets to create a (k+1)-candidate.
	 * 
	 * @param a first itemset (must have smaller last element)
	 * @param b second itemset
	 * @return the joined candidate, or {@code null} if join is invalid
	 */
	private int[] joinItemsets(int[] a, int[] b) {
		int k = a.length;
		int lastA = a[k - 1];
		int lastB = b[k - 1];

		if (lastA >= lastB)
			return null;

		int[] result = new int[k + 1];
		System.arraycopy(a, 0, result, 0, k);
		result[k] = lastB;
		return result;
	}

	/**
	 * Verifies that all (k-1)-subsets of a k-itemset are frequent.
	 * 
	 * <p>
	 * <strong>CONSERVATIVE APPROACH:</strong> Only allows candidate generation when
	 * all subsets are confirmed frequent (SOLID_CIRCLE state). This is correct per
	 * the DIC paper and prevents spurious candidates.
	 * </p>
	 * 
	 * @param itemset the candidate itemset to check
	 * @return {@code true} if all subsets are confirmed frequent
	 */
	private boolean allSubsetsFrequent(int[] itemset) {
		if (itemset.length <= 1)
			return true;

		int subsetSize = itemset.length - 1;

		for (int skip = 0; skip < itemset.length; skip++) {
			int idx = 0;
			for (int i = 0; i < itemset.length; i++) {
				if (i != skip) {
					subsetBuffer[idx++] = itemset[i];
				}
			}

			int[] subset = Arrays.copyOf(subsetBuffer, subsetSize);
			IntArrayWrapper subsetKey = new IntArrayWrapper(subset);

			ItemsetData subsetData = itemsets.get(subsetKey);

			// Subset must exist and be confirmed frequent
			if (subsetData == null || subsetData.state != State.SOLID_CIRCLE) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Adds an itemset to the frequent-by-size index.
	 * 
	 * <p>
	 * Uses LinkedHashSet for deterministic iteration order and reproducibility.
	 * </p>
	 * 
	 * @param key the itemset wrapper to add
	 */
	private void addToFrequentBySize(IntArrayWrapper key) {
		int size = key.items.length;
		frequentBySize.computeIfAbsent(size, k -> new LinkedHashSet<>()).add(key);
	}

	/**
	 * Checks if a transaction contains all items in an itemset.
	 * 
	 * <p>
	 * Uses linear merge since both arrays are sorted.
	 * </p>
	 * 
	 * @param transaction the transaction (sorted)
	 * @param itemset     the itemset to check (sorted)
	 * @return {@code true} if transaction contains all items in itemset
	 */
	private boolean transactionContains(int[] transaction, int[] itemset) {
		if (itemset.length > transaction.length)
			return false;

		int ti = 0, ii = 0;
		while (ii < itemset.length && ti < transaction.length) {
			if (transaction[ti] == itemset[ii]) {
				ti++;
				ii++;
			} else if (transaction[ti] < itemset[ii]) {
				ti++;
			} else {
				return false;
			}
		}
		return ii == itemset.length;
	}

	/**
	 * Writes all frequent itemsets to the output file.
	 * 
	 * <p>
	 * Output format: {@code item1 item2 ... itemN #SUP: count}
	 * </p>
	 * 
	 * @param output path to the output file
	 * @throws IOException if file writing fails
	 */
	private void writeOutput(String output) throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
			for (Map.Entry<IntArrayWrapper, ItemsetData> entry : itemsets.entrySet()) {
				if (entry.getValue().state == State.SOLID_CIRCLE) {
					itemsetCount++;

					StringBuilder sb = new StringBuilder();
					int[] items = entry.getKey().items;
					for (int i = 0; i < items.length; i++) {
						if (i > 0)
							sb.append(' ');
						sb.append(items[i]);
					}
					sb.append(" #SUP: ").append(entry.getValue().count);

					writer.write(sb.toString());
					writer.newLine();
				}
			}
		}
	}

	/**
	 * Prints algorithm execution statistics to standard output.
	 */
	public void printStats() {
		System.out.println("====== DIC ALGORITHM - STATS ======");
		System.out.println(" Transactions: " + transactionCount);
		System.out.println(" Unique items: " + (allItems != null ? allItems.length : 0));
		System.out.println(" Bucket size (M): " + bucketSize);
		System.out.println(" Number of buckets: " + numBuckets);
		System.out.println(" Candidates generated: " + candidatesGenerated);
		System.out.println(" Database passes: " + passCount);
		System.out.println();
		System.out.println(" Frequent itemsets: " + itemsetCount);
		System.out.println(" Time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory: " + String.format("%.2f", maxMemory) + " MB");
		System.out.println("===================================");
	}

	/**
	 * Returns the number of frequent itemsets found.
	 * 
	 * @return frequent itemset count
	 */
	public int getItemsetCount() {
		return itemsetCount;
	}

	/**
	 * Returns the number of complete database passes.
	 * 
	 * @return pass count
	 */
	public int getPassCount() {
		return passCount;
	}

	/**
	 * Returns the total number of candidates generated.
	 * 
	 * @return candidate count
	 */
	public long getCandidatesGenerated() {
		return candidatesGenerated;
	}

	/**
	 * Returns the maximum memory usage in megabytes.
	 * 
	 * @return max memory in MB
	 */
	public double getMaxMemory() {
		return maxMemory;
	}

	/**
	 * Returns the total execution time in milliseconds.
	 * 
	 * @return execution time in ms
	 */
	public long getExecutionTime() {
		return endTimestamp - startTimestamp;
	}

	/**
	 * Wrapper for int arrays enabling use as HashMap keys.
	 * 
	 * <p>
	 * Caches hash code for efficient repeated lookups.
	 * </p>
	 */
	private static class IntArrayWrapper {
		/** The wrapped itemset array. */
		final int[] items;
		/** Cached hash code. */
		private final int hash;

		/**
		 * Wraps an integer array.
		 * 
		 * @param items the array to wrap (not copied)
		 */
		IntArrayWrapper(int[] items) {
			this.items = items;
			this.hash = Arrays.hashCode(items);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof IntArrayWrapper))
				return false;
			return Arrays.equals(items, ((IntArrayWrapper) o).items);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public String toString() {
			return Arrays.toString(items);
		}
	}
}