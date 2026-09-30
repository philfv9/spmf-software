package ca.pfv.spmf.algorithms.sequentialpatterns.disc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
 * Copyright (c) Chen Hui
 *
 * Do not remove copyright and license information.
 */
/**
 * Implementation of the DISC-all algorithm for mining frequent sequential
 * patterns. Reads an input database in SPMF format, mines all frequent
 * sequences above a relative minimum support threshold, and writes results to
 * an output file.
 * @author Chen Hui
 */
public class AlgoDiscALL implements DiscHost {

	/** Timestamp recorded when the algorithm starts. */
	private long startTimestamp;

	/** Timestamp recorded when the algorithm finishes. */
	private long endTimestamp;

	/** Total number of frequent patterns found. */
	private int patternCount;

	/** Number of sequences (customers) in the input database. */
	private int databaseSize;

	/** Absolute minimum support count derived from the relative threshold. */
	private int minSupportCount;

	/** Relative minimum support threshold provided by the user. */
	private double minSupportRelative;

	/** Peak memory usage in megabytes observed during the run. */
	private double maxMemory;

	/** Cached Runtime instance to avoid repeated lookups. */
	private final Runtime runtime = Runtime.getRuntime();

	/** Counter used to throttle memory-check sampling. */
	private int memoryCheckCounter = 0;

	/** Sampling mask: memory is checked only every 32 calls. */
	private static final int MEMORY_CHECK_MASK = 0x1F;

	/** Flattened customer sequences with items renamed to contiguous IDs. */
	private List<CustomerData> customers;

	/** Reverse mapping from internal item ID to original item value. */
	private int[] idToItem = new int[0];

	/** Number of distinct items after renumbering. */
	private int itemCount;

	/**
	 * True when the input database consists of sequences of single items (i.e.,
	 * every itemset in every sequence contains exactly one item). This is a common
	 * special case: some databases contain plain sequences of items rather than
	 * sequences of itemsets. When detected, a faster processing path is used
	 * because "same-transaction" co-occurrences can never happen (every itemset has
	 * only one item), so all the per-segment bookkeeping (segment boundaries,
	 * same-transaction stamping, containment checks) can be skipped in favor of a
	 * direct linear scan.
	 */
	private boolean singleItemMode = true;

	/** Writer used to output discovered patterns. */
	private BufferedWriter writer;

	/** Reused buffer for assembling output lines without per-pattern allocation. */
	private final StringBuilder outBuffer = new StringBuilder(256);

	/** Reused character buffer for writing output lines. */
	private char[] outCharBuffer = new char[512];

	/** Engine responsible for direct-comparison extension and counting. */
	private DiscEngine discEngine;

	/** Dense boolean array indicating which items are frequent at length 1. */
	private boolean[] frequentItemFlags;

	/**
	 * Stamp array tracking which items form a frequent same-transaction pair with
	 * O.
	 */
	private int[] sameStamp;

	/**
	 * Stamp array tracking which items form a frequent later-transaction pair with
	 * O.
	 */
	private int[] laterStamp;

	/**
	 * Current stamp value used to avoid clearing stamp arrays between partitions.
	 */
	private int stampId = 1;

	/**
	 * Per-item count of same-transaction pairs for the current first-level
	 * partition.
	 */
	private int[] pairCountSame;

	/**
	 * Per-item count of later-transaction pairs for the current first-level
	 * partition.
	 */
	private int[] pairCountLater;

	/**
	 * Stamp array to validate entries in pairCountSame without zeroing the array.
	 */
	private int[] pairCountStampSame;

	/**
	 * Stamp array to validate entries in pairCountLater without zeroing the array.
	 */
	private int[] pairCountStampLater;

	/**
	 * Tracks the last call ID that wrote a same-transaction count for each item.
	 */
	private int pairCountCallId = 0;

	/** Per-item last customer ID seen for same-transaction deduplication. */
	private int[] pairLastCidSame;

	/** Per-item last customer ID seen for later-transaction deduplication. */
	private int[] pairLastCidLater;

	/** Monotonically increasing customer counter used for pair deduplication. */
	private int pairCustomerCid = 0;

	/** Reused two-element buffer for emitting length-2 patterns. */
	private final int[] pairItemsBuf = new int[2];

	/**
	 * Tracks which items were touched (written for the first time) during
	 * same-transaction pair counting in the current partition call, so that
	 * the emission loop can iterate only over touched items instead of all
	 * itemCount entries, avoiding the O(itemCount^2) bottleneck.
	 */
	private int[] touchedItemsSame = new int[0];

	/**
	 * Tracks which items were touched (written for the first time) during
	 * later-transaction pair counting in the current partition call, so that
	 * the emission loop can iterate only over touched items instead of all
	 * itemCount entries, avoiding the O(itemCount^2) bottleneck.
	 */
	private int[] touchedItemsLater = new int[0];

	/** Number of valid entries currently recorded in {@link #touchedItemsSame}. */
	private int touchedCountSame = 0;

	/** Number of valid entries currently recorded in {@link #touchedItemsLater}. */
	private int touchedCountLater = 0;

	/**
	 * Whether the second-level key is ordered type-first rather than item-first.
	 */
	private boolean secondLevelTypeFirstOrder;

	/** Reused bucket array for the second-level partition heap. */
	private ArrayList<SecondLevelMember>[] secondLevelBuckets = null;

	/** Min-heap driving traversal order over second-level partition keys. */
	private IntMinHeap secondLevelHeap = new IntMinHeap(64);

	/**
	 * Reused list of CustomerData objects assembled for each second-level
	 * partition.
	 */
	private final ArrayList<CustomerData> spCustomersBuf = new ArrayList<CustomerData>();

	/**
	 * Shared scratch buffer for encoding eligible items during CustomerState
	 * construction.
	 */
	private long[] encodeScratch = new long[0];

	/** Shared txCum array for single-item patterns. */
	private static final int[] TX_CUM_1 = new int[] { 1 };

	/** Shared txCum array for same-transaction two-item patterns. */
	private static final int[] TX_CUM_SAME_2 = new int[] { 1, 1 };

	/** Shared txCum array for later-transaction two-item patterns. */
	private static final int[] TX_CUM_LATER_2 = new int[] { 1, 2 };

	/**
	 * Runs the DISC-all algorithm on the given input database and writes frequent
	 * sequential patterns to the output file.
	 *
	 * @param input              path to the input database file in SPMF format
	 * @param output             path to the output file where patterns are written
	 * @param minSupportRelative minimum support as a fraction of the database size,
	 *                           in [0,1]
	 * @throws IOException              if an I/O error occurs reading or writing
	 *                                  files
	 * @throws IllegalArgumentException if minSupportRelative is not in [0,1]
	 */
	public void runAlgorithm(String input, String output, double minSupportRelative) throws IOException {

		if (Double.isNaN(minSupportRelative) || minSupportRelative < 0.0 || minSupportRelative > 1.0) {
			throw new IllegalArgumentException("Minimum support must be between 0 and 1.");
		}

		startTimestamp = System.currentTimeMillis();
		patternCount = 0;
		maxMemory = 0;
		memoryCheckCounter = 0;
		this.minSupportRelative = minSupportRelative;

		// Read and renumber the database (also detects the single-item fast-path
		// eligibility)
		DatabaseInfo db = readDatabaseAsCustomerDataAndRenumber(input);
		this.customers = db.customers;
		this.databaseSize = db.databaseSize;
		this.itemCount = idToItem.length;

		// Compute absolute support count, ensuring it is at least 1
		minSupportCount = (int) Math.ceil(minSupportRelative * databaseSize);
		if (minSupportCount == 0) {
			minSupportCount = 1;
		}

		// Pass itemCount so DiscEngine can size its per-item presence-stamp array
		// used to speed up buildAprioriKMinimum's leading-item group filtering.
		discEngine = new DiscEngine(minSupportCount, this, singleItemMode, itemCount);

		initSecondLevelKeyOrder();
		ensureSecondLevelStructures();

		// Mine patterns and write results, releasing resources in finally block
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(output), 1 << 20)) {
			writer = bw;
			writer.write("@FILETYPE=\"Frequent sequential patterns\"");
			writer.newLine();
			writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			writer.newLine();
			
			checkMemory();
			mineDiscAll();
			checkMemory();
		} finally {
			writer = null;
			frequentItemFlags = null;
			sameStamp = null;
			laterStamp = null;
			pairCountSame = null;
			pairCountLater = null;
			pairCountStampSame = null;
			pairCountStampLater = null;
			pairLastCidSame = null;
			pairLastCidLater = null;
			touchedItemsSame = new int[0];
			touchedItemsLater = new int[0];
			touchedCountSame = 0;
			touchedCountLater = 0;
			secondLevelBuckets = null;
			secondLevelHeap.clear();
			spCustomersBuf.clear();
			encodeScratch = new long[0];
			discEngine = null;
			customers = null;
			idToItem = new int[0];
			itemCount = 0;
		}

		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Executes the main DISC-all mining loop over all first-level partitions. Finds
	 * frequent 1-sequences, builds first-level buckets, and for each partition
	 * processes second-level extensions.
	 *
	 * @throws IOException if an I/O error occurs while writing patterns
	 */
	private void mineDiscAll() throws IOException {

		// Find all frequent single items and mark them
		int[] frequentOneItems = findFrequentOneItemsByCounting(customers);
		if (frequentOneItems.length == 0) {
			return;
		}

		frequentItemFlags = new boolean[itemCount];
		for (int it : frequentOneItems) {
			frequentItemFlags[it] = true;
		}

		// Build first-level bucket array and heap
		@SuppressWarnings("unchecked")
		ArrayList<FirstLevelMember>[] firstBuckets = (ArrayList<FirstLevelMember>[]) new ArrayList[itemCount];
		IntMinHeap firstHeap = new IntMinHeap(Math.min(1024, itemCount));

		// Assign each customer to its initial first-level partition
		for (int ci = 0, cs = customers.size(); ci < cs; ci++) {
			CustomerData cd = customers.get(ci);
			CustomerState state = new CustomerState(cd, frequentItemFlags, growEncodeScratch(cd.N));

			long asg = computeFirstLevelAssignmentPacked(state);
			if (asg == NO_ASSIGNMENT) {
				continue;
			}

			int O = unpackItem(asg);
			int pos = unpackPos(asg);

			FirstLevelMember member = new FirstLevelMember(state, pos);
			state.firstMember = member;

			enqueueFirstLevel(firstBuckets, firstHeap, O, member);
		}

		// Process partitions in ascending key order
		while (!firstHeap.isEmpty()) {

			int O = firstHeap.poll();
			ArrayList<FirstLevelMember> fpMembers = firstBuckets[O];
			if (fpMembers == null || fpMembers.isEmpty()) {
				continue;
			}

			// Project each member's data starting from its minimum-point position
			for (int i = 0, sz = fpMembers.size(); i < sz; i++) {
				FirstLevelMember m = fpMembers.get(i);
				m.projected = projectFrom(m.state.data, m.minPointPos);
			}

			if (fpMembers.size() < minSupportCount) {
				reassignAfterFirstLevelDone(firstBuckets, firstHeap, O, fpMembers);
				fpMembers.clear();
				continue;
			}

			// Find frequent pairs and explore second-level extensions.
			// Use the faster single-item path when the database has no multi-item itemsets.
			FrequentTwoInfo frequentTwo = singleItemMode
					? findFrequentTwoSequencesInFirstLevelPartitionSingleItem(O, fpMembers)
					: findFrequentTwoSequencesInFirstLevelPartition(O, fpMembers);

			processSecondLevelUnderFirstLevel(O, fpMembers, frequentTwo);
			reassignAfterFirstLevelDone(firstBuckets, firstHeap, O, fpMembers);

			fpMembers.clear();
			checkMemory();
		}
	}

	/**
	 * Grows the shared scratch buffer to at least {@code minSize} elements if
	 * needed.
	 *
	 * @param minSize the minimum required length
	 * @return the scratch buffer, guaranteed to have length >= minSize
	 */
	private long[] growEncodeScratch(int minSize) {
		if (encodeScratch.length < minSize) {
			int newSize = Math.max(minSize, encodeScratch.length * 2);
			encodeScratch = new long[newSize];
		}
		return encodeScratch;
	}

	/**
	 * Holds precomputed state for a single customer during first-level
	 * partitioning, including the sorted list of eligible (frequent) items and
	 * their first positions.
	 */
	private static final class CustomerState {

		/** The underlying customer sequence data. */
		final CustomerData data;

		/** Sorted array of frequent items appearing in this customer's sequence. */
		final int[] eligibleItems;

		/** Position of the first occurrence of each eligible item. */
		final int[] eligiblePositions;

		/** Index into eligibleItems pointing to the next unprocessed item. */
		int scanCursor;

		/**
		 * The largest first-level key already used as a partition for this customer.
		 */
		int usedPrefixThreshold = Integer.MIN_VALUE;

		/** Reference to this customer's current first-level membership record. */
		FirstLevelMember firstMember;

		/**
		 * Constructs a CustomerState by scanning the customer's sequence for frequent
		 * items.
		 *
		 * @param data              the customer's sequence data
		 * @param frequentItemFlags boolean array indicating which items are frequent
		 * @param scratch           caller-provided scratch buffer of length >= data.N
		 */
		CustomerState(CustomerData data, boolean[] frequentItemFlags, long[] scratch) {
			this.data = data;

			final int n = data.N;
			final int[] srcItems = data.flatItem;
			final int base = data.offset;

			// Encode each frequent item and its position into a sortable long
			int count = 0;
			for (int p = 0; p < n; p++) {
				int item = srcItems[base + p];
				if (frequentItemFlags[item]) {
					scratch[count++] = (((long) item) << 32) | (p & 0xFFFFFFFFL);
				}
			}

			if (count == 0) {
				this.eligibleItems = new int[0];
				this.eligiblePositions = new int[0];
				return;
			}

			// Sort by item then by position
			Arrays.sort(scratch, 0, count);

			// Keep only the first occurrence of each distinct item
			int distinctCount = 0;
			int lastItem = Integer.MIN_VALUE;
			for (int i = 0; i < count; i++) {
				long e = scratch[i];
				int item = (int) (e >>> 32);
				if (item != lastItem) {
					scratch[distinctCount++] = e;
					lastItem = item;
				}
			}

			this.eligibleItems = new int[distinctCount];
			this.eligiblePositions = new int[distinctCount];
			for (int i = 0; i < distinctCount; i++) {
				long e = scratch[i];
				this.eligibleItems[i] = (int) (e >>> 32);
				this.eligiblePositions[i] = (int) e;
			}
		}
	}

	/**
	 * Represents a customer's membership in a first-level partition, holding the
	 * projected sequence and auxiliary data for pair counting.
	 */
	private static final class FirstLevelMember {

		/** The owning customer's state. */
		final CustomerState state;

		/** Position in the original sequence from which the projection starts. */
		int minPointPos;

		/** The projected customer sequence used during second-level processing. */
		CustomerData projected;

		/**
		 * Per-segment flags indicating whether segment contains the partition item O,
		 * computed once during pair counting and reused during reduction. Only used in
		 * the general (non single-item) processing path.
		 */
		boolean[] segHasO;

		/**
		 * Constructs a FirstLevelMember for the given state and minimum-point position.
		 *
		 * @param state       the customer state
		 * @param minPointPos position in the sequence at the minimum point
		 */
		FirstLevelMember(CustomerState state, int minPointPos) {
			this.state = state;
			this.minPointPos = minPointPos;
		}
	}

	/**
	 * Sentinel value returned when no first-level assignment exists for a customer.
	 */
	private static final long NO_ASSIGNMENT = Long.MIN_VALUE;

	/**
	 * Computes the next first-level partition assignment for a customer, packed as
	 * a long. The upper 32 bits hold the item ID and the lower 32 bits hold the
	 * position.
	 *
	 * @param state the customer state to advance
	 * @return packed (item, position) assignment, or {@link #NO_ASSIGNMENT} if none
	 *         remains
	 */
	private static long computeFirstLevelAssignmentPacked(CustomerState state) {

		int[] items = state.eligibleItems;
		int n = items.length;
		int cursor = state.scanCursor;

		// Advance past items already used as partition keys
		while (cursor < n && items[cursor] <= state.usedPrefixThreshold) {
			cursor++;
		}

		state.scanCursor = cursor;

		if (cursor >= n) {
			return NO_ASSIGNMENT;
		}

		return (((long) items[cursor]) << 32) | (state.eligiblePositions[cursor] & 0xFFFFFFFFL);
	}

	/**
	 * Extracts the item ID from a packed assignment value.
	 *
	 * @param packed the packed (item, position) long
	 * @return the item ID
	 */
	private static int unpackItem(long packed) {
		return (int) (packed >>> 32);
	}

	/**
	 * Extracts the position from a packed assignment value.
	 *
	 * @param packed the packed (item, position) long
	 * @return the position
	 */
	private static int unpackPos(long packed) {
		return (int) packed;
	}

	/**
	 * Adds a member to the first-level bucket for the given key, registering the
	 * key in the heap if the bucket was previously empty.
	 *
	 * @param buckets array of per-key member lists
	 * @param heap    min-heap of active keys
	 * @param key     the partition key (item ID)
	 * @param member  the member to enqueue
	 */
	private static void enqueueFirstLevel(ArrayList<FirstLevelMember>[] buckets, IntMinHeap heap, int key,
			FirstLevelMember member) {

		ArrayList<FirstLevelMember> bucket = buckets[key];
		if (bucket == null) {
			bucket = new ArrayList<FirstLevelMember>(4);
			buckets[key] = bucket;
			heap.add(key);
		} else if (bucket.isEmpty()) {
			heap.add(key);
		}
		bucket.add(member);
	}

	/**
	 * Reassigns each member of a completed first-level partition to its next
	 * partition key. Updates the used-prefix threshold so items already processed
	 * are skipped.
	 *
	 * @param firstBuckets array of per-key first-level member lists
	 * @param firstHeap    min-heap of active first-level keys
	 * @param processedO   the partition key that was just processed
	 * @param fpMembers    the members that belonged to that partition
	 */
	private void reassignAfterFirstLevelDone(ArrayList<FirstLevelMember>[] firstBuckets, IntMinHeap firstHeap,
			int processedO, ArrayList<FirstLevelMember> fpMembers) {

		for (int i = 0, sz = fpMembers.size(); i < sz; i++) {

			FirstLevelMember m = fpMembers.get(i);

			// Mark processedO as consumed so it won't be used again for this customer
			if (processedO > m.state.usedPrefixThreshold) {
				m.state.usedPrefixThreshold = processedO;
			}

			long next = computeFirstLevelAssignmentPacked(m.state);
			if (next == NO_ASSIGNMENT) {
				continue;
			}

			m.minPointPos = unpackPos(next);
			m.projected = null;

			enqueueFirstLevel(firstBuckets, firstHeap, unpackItem(next), m);
		}
	}

	/**
	 * Holds a customer's reduced projected sequence for use in a second-level
	 * partition.
	 */
	private static final class SecondLevelMember {

		/** The owning customer's state. */
		final CustomerState state;

		/** The reduced projected sequence passed to the DISC engine. */
		final CustomerData reducedProjected;

		/**
		 * Constructs a SecondLevelMember with the given state and reduced projection.
		 *
		 * @param state            the customer state
		 * @param reducedProjected the reduced projected sequence
		 */
		SecondLevelMember(CustomerState state, CustomerData reducedProjected) {
			this.state = state;
			this.reducedProjected = reducedProjected;
		}
	}

	/**
	 * Processes all second-level partitions under a given first-level partition key
	 * O. For each qualifying customer, reduces the projection and assigns it to a
	 * second-level bucket; then mines length-3 and longer patterns from each
	 * bucket.
	 *
	 * @param O           the first-level partition item
	 * @param fpMembers   members of the first-level partition
	 * @param frequentTwo frequent pair information computed for this partition
	 * @throws IOException if an I/O error occurs while writing patterns
	 */
	private void processSecondLevelUnderFirstLevel(int O, ArrayList<FirstLevelMember> fpMembers,
			FrequentTwoInfo frequentTwo) throws IOException {

		if (!frequentTwo.hasAny) {
			return;
		}

		secondLevelHeap.clear();

		final CandidateKey prefixO = new CandidateKey(new int[] { O }, TX_CUM_1);

		// Reduce each member's projection and assign to its second-level bucket
		for (int i = 0, sz = fpMembers.size(); i < sz; i++) {

			FirstLevelMember fm = fpMembers.get(i);

			// The single-item database special case never has same-transaction pairs,
			// so the reduction can skip all segment/same-transaction bookkeeping.
			CustomerData reduced = singleItemMode
					? reduceByNonFrequentTwoSingleItem(fm.projected, O, frequentItemFlags, frequentTwo.stampId,
							laterStamp)
					: reduceByNonFrequentTwo(fm.projected, O, frequentItemFlags, frequentTwo.stampId, sameStamp,
							laterStamp, fm.segHasO);

			if (reduced.N < 3) {
				continue;
			}

			Candidate twoMin = discEngine.minimumExtensionForPrefix(reduced, prefixO, null, true);
			if (twoMin == null) {
				continue;
			}

			int x = twoMin.items[1];
			int type = (twoMin.txCum[1] == 1) ? 0 : 1;
			int key = encodeSecondLevelKey(x, type);
			enqueueSecondLevel(key, new SecondLevelMember(fm.state, reduced));
		}

		final ArrayList<CustomerData> spCustomers = spCustomersBuf;

		// Process second-level buckets in ascending key order
		while (!secondLevelHeap.isEmpty()) {

			int key = secondLevelHeap.poll();
			ArrayList<SecondLevelMember> members = secondLevelBuckets[key];
			if (members == null || members.isEmpty()) {
				continue;
			}

			int x = decodeSecondLevelX(key);
			int type = decodeSecondLevelType(key);

			final CandidateKey spKey = new CandidateKey(new int[] { O, x },
					(type == 0) ? TX_CUM_SAME_2 : TX_CUM_LATER_2);

			List<CandidateKey> frequentThree = Collections.emptyList();

			if (members.size() >= minSupportCount) {
				// Collect reduced projections and find frequent length-3 extensions
				spCustomers.clear();
				spCustomers.ensureCapacity(members.size());
				for (int i = 0, sz = members.size(); i < sz; i++) {
					spCustomers.add(members.get(i).reducedProjected);
				}
				frequentThree = discEngine.findFrequentExtensionsByCounting(spCustomers, spKey);
			}

			// Mine length-4+ patterns if any length-3 extensions were found
			if (!frequentThree.isEmpty()) {
				discEngine.mineFromLength4WithBiLevel(spCustomers, frequentThree);
			}

			// Reassign each member to its next second-level bucket
			for (int i = 0, sz = members.size(); i < sz; i++) {

				SecondLevelMember m = members.get(i);

				Candidate nextTwo = discEngine.minimumExtensionForPrefix(m.reducedProjected, prefixO, spKey, false);
				if (nextTwo == null) {
					continue;
				}

				int nx = nextTwo.items[1];
				int ntype = (nextTwo.txCum[1] == 1) ? 0 : 1;
				int nkey = encodeSecondLevelKey(nx, ntype);
				enqueueSecondLevel(nkey, m);
			}

			members.clear();
			checkMemory();
		}
	}

	/**
	 * Allocates or reallocates the second-level bucket array to match the current
	 * item count.
	 */
	@SuppressWarnings("unchecked")
	private void ensureSecondLevelStructures() {
		int needed = Math.max(2, itemCount * 2);
		if (secondLevelBuckets == null || secondLevelBuckets.length != needed) {
			secondLevelBuckets = (ArrayList<SecondLevelMember>[]) new ArrayList[needed];
		}
	}

	/**
	 * Adds a member to the second-level bucket for the given key, registering the
	 * key in the heap if the bucket was previously empty.
	 *
	 * @param key    the second-level partition key
	 * @param member the member to enqueue
	 */
	private void enqueueSecondLevel(int key, SecondLevelMember member) {
		ArrayList<SecondLevelMember> bucket = secondLevelBuckets[key];
		if (bucket == null) {
			bucket = new ArrayList<SecondLevelMember>(4);
			secondLevelBuckets[key] = bucket;
			secondLevelHeap.add(key);
		} else if (bucket.isEmpty()) {
			secondLevelHeap.add(key);
		}
		bucket.add(member);
	}

	/**
	 * Encodes a second-level partition key from an item ID and relation type.
	 *
	 * @param x    the second item's internal ID
	 * @param type 0 for same-transaction, 1 for later-transaction
	 * @return the encoded integer key
	 */
	private int encodeSecondLevelKey(int x, int type) {
		return secondLevelTypeFirstOrder ? (type * itemCount + x) : ((x << 1) | type);
	}

	/**
	 * Decodes the item ID from a second-level partition key.
	 *
	 * @param key the encoded second-level key
	 * @return the item ID
	 */
	private int decodeSecondLevelX(int key) {
		return secondLevelTypeFirstOrder ? (key % itemCount) : (key >>> 1);
	}

	/**
	 * Decodes the relation type from a second-level partition key.
	 *
	 * @param key the encoded second-level key
	 * @return 0 for same-transaction, 1 for later-transaction
	 */
	private int decodeSecondLevelType(int key) {
		return secondLevelTypeFirstOrder ? (key / itemCount) : (key & 1);
	}

	/**
	 * Determines and caches the second-level key ordering by comparing a sample
	 * same-transaction pattern against a sample later-transaction pattern.
	 */
	private void initSecondLevelKeyOrder() {
		secondLevelTypeFirstOrder = false;
		if (itemCount < 2) {
			return;
		}
		CandidateKey A = new CandidateKey(new int[] { 0, 1 }, TX_CUM_SAME_2);
		CandidateKey B = new CandidateKey(new int[] { 0, 0 }, TX_CUM_LATER_2);
		secondLevelTypeFirstOrder = CandidateKey.COMPARATOR.compare(A, B) < 0;
	}

	/**
	 * Returns a projected view of a customer sequence starting at the given
	 * position.
	 *
	 * @param base     the original customer sequence
	 * @param startPos the position from which to begin the projection
	 * @return the projected CustomerData slice
	 */
	private static CustomerData projectFrom(CustomerData base, int startPos) {
		return base.slice(startPos);
	}

	/**
	 * Performs a binary search for a key within a sorted sub-range of an array.
	 *
	 * @param a             the array to search
	 * @param fromInclusive start index (inclusive)
	 * @param toExclusive   end index (exclusive)
	 * @param key           the value to search for
	 * @return {@code true} if the key is found in the range
	 */
	private static boolean containsSortedInRange(final int[] a, int fromInclusive, int toExclusive, int key) {
		int lo = fromInclusive;
		int hi = toExclusive - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			int v = a[mid];
			if (v < key)
				lo = mid + 1;
			else if (v > key)
				hi = mid - 1;
			else
				return true;
		}
		return false;
	}

	/**
	 * Returns a reduced copy of the projected sequence with items removed that
	 * cannot participate in any frequent pair with O. Items are kept only if they
	 * form a frequent same- or later-transaction pair with O as recorded in the
	 * stamp arrays.
	 *
	 * <p>Because same-transaction pairs are only stamped for items x &gt; O
	 * (canonical ascending itemset order), any item x &lt; O will never have
	 * {@code sameStamp[x] == partStampId} and will therefore be dropped from
	 * the same-transaction branch automatically, without any additional check.
	 *
	 * @param projected         the projected customer sequence to reduce
	 * @param O                 the first-level partition item
	 * @param frequentItemFlags dense flag array of frequent items
	 * @param partStampId       stamp value identifying the current partition
	 * @param sameStamp         per-item stamp for frequent same-transaction pairs
	 * @param laterStamp        per-item stamp for frequent later-transaction pairs
	 * @param segHasO           per-segment flags indicating whether each segment
	 *                          contains O
	 * @return the reduced CustomerData, or the original if no items were removed
	 */
	private CustomerData reduceByNonFrequentTwo(CustomerData projected, int O, boolean[] frequentItemFlags,
	        int partStampId, int[] sameStamp, int[] laterStamp, boolean[] segHasO) {

	    final int n = projected.N;
	    if (n == 0) {
	        return projected;
	    }

	    final int[] srcItems = projected.flatItem;
	    final int[] srcTx = projected.flatTx;
	    final int base = projected.offset;
	    final int minTx = srcTx[base];

	    int[] outItems = null;
	    int[] outTx = null;
	    int outLen = 0;
	    boolean reduced = false;

	    int segCount = projected.segmentCount();

	    for (int seg = 0; seg < segCount; seg++) {

	        int start = projected.segmentStart(seg);
	        int end = projected.segmentEndExclusive(seg);
	        int transaction = srcTx[base + start];

	        // Reuse the precomputed O-presence flag for this segment
	        boolean txHasO = segHasO[seg];
	        boolean cond2 = transaction > minTx;

	        for (int p = start; p < end; p++) {

	            int item = srcItems[base + p];
	            boolean keep = true;

	            // Determine whether this item should be kept based on pair frequency
	            // stamps. Note: sameStamp is only ever set for items x > O (canonical
	            // ascending itemset order enforced during pair counting), so items
	            // x < O will naturally fail the sameStamp check and be dropped from
	            // the same-transaction branch without any explicit item-vs-O comparison
	            // here. O itself is always kept unconditionally.
	            if (item != O) {
	                if (!frequentItemFlags[item]) {
	                    keep = false;
	                } else if (!txHasO) {
	                    keep = (laterStamp[item] == partStampId);
	                } else if (!cond2) {
	                    keep = (sameStamp[item] == partStampId);
	                } else {
	                    keep = (sameStamp[item] == partStampId) || (laterStamp[item] == partStampId);
	                }
	            }

	            if (!keep) {
	                // Lazily allocate output buffers on first removal
	                if (!reduced) {
	                    outItems = new int[n];
	                    outTx = new int[n];
	                    if (p > 0) {
	                        System.arraycopy(srcItems, base, outItems, 0, p);
	                        System.arraycopy(srcTx, base, outTx, 0, p);
	                    }
	                    outLen = p;
	                    reduced = true;
	                }
	                continue;
	            }

	            if (reduced) {
	                outItems[outLen] = item;
	                outTx[outLen] = srcTx[base + p];
	                outLen++;
	            }
	        }
	    }

	    if (!reduced) {
	        return projected;
	    }

	    return new CustomerData(Arrays.copyOf(outItems, outLen), Arrays.copyOf(outTx, outLen));
	}

	/**
	 * Fast-path variant of {@link #reduceByNonFrequentTwo} used when the input
	 * database consists of sequences of single items. Since every itemset has
	 * exactly one item, "same-transaction" pairs with O can never occur, so the
	 * only relevant frequency stamp is {@code laterStamp}, and there is no need to
	 * compute segment boundaries, per-segment O-presence flags, or
	 * transaction-ordering conditions: a plain linear scan over the flattened items
	 * is sufficient and equivalent to the general logic.
	 *
	 * @param projected         the projected customer sequence to reduce
	 * @param O                 the first-level partition item
	 * @param frequentItemFlags dense flag array of frequent items
	 * @param partStampId       stamp value identifying the current partition
	 * @param laterStamp        per-item stamp for frequent later-transaction pairs
	 * @return the reduced CustomerData, or the original if no items were removed
	 */
	private CustomerData reduceByNonFrequentTwoSingleItem(CustomerData projected, int O, boolean[] frequentItemFlags,
			int partStampId, int[] laterStamp) {

		final int n = projected.N;
		if (n == 0) {
			return projected;
		}

		final int[] srcItems = projected.flatItem;
		final int[] srcTx = projected.flatTx;
		final int base = projected.offset;

		int[] outItems = null;
		int[] outTx = null;
		int outLen = 0;
		boolean reduced = false;

		for (int p = 0; p < n; p++) {

			int item = srcItems[base + p];

			// With single-item transactions, an item other than O is never in the same
			// transaction as O, so only the later-transaction stamp is relevant.
			boolean keep = (item == O) || (frequentItemFlags[item] && laterStamp[item] == partStampId);

			if (!keep) {
				// Lazily allocate output buffers on first removal
				if (!reduced) {
					outItems = new int[n];
					outTx = new int[n];
					if (p > 0) {
						System.arraycopy(srcItems, base, outItems, 0, p);
						System.arraycopy(srcTx, base, outTx, 0, p);
					}
					outLen = p;
					reduced = true;
				}
				continue;
			}

			if (reduced) {
				outItems[outLen] = item;
				outTx[outLen] = srcTx[base + p];
				outLen++;
			}
		}

		if (!reduced) {
			return projected;
		}

		return new CustomerData(Arrays.copyOf(outItems, outLen), Arrays.copyOf(outTx, outLen));
	}

	/**
	 * Counts item occurrences across all customer sequences and returns the IDs of
	 * items meeting the minimum support threshold. Also writes each frequent
	 * single-item pattern to the output file.
	 *
	 * @param customers the list of customer sequences
	 * @return sorted array of frequent item IDs
	 * @throws IOException if an I/O error occurs while writing patterns
	 */
	private int[] findFrequentOneItemsByCounting(List<CustomerData> customers) throws IOException {

		final int[] counts = new int[itemCount];
		final int[] lastSeenCustomer = new int[itemCount];

		// Count each item once per customer using a per-item last-seen marker
		int cid = 1;
		for (int ci = 0, cs = customers.size(); ci < cs; ci++) {
			CustomerData customer = customers.get(ci);
			final int[] items = customer.flatItem;
			final int base = customer.offset;
			final int n = customer.N;

			for (int p = 0; p < n; p++) {
				int it = items[base + p];
				if (lastSeenCustomer[it] != cid) {
					lastSeenCustomer[it] = cid;
					counts[it]++;
				}
			}
			cid++;
		}

		// Count how many items meet the threshold
		int freqCount = 0;
		for (int it = 0; it < itemCount; it++) {
			if (counts[it] >= minSupportCount)
				freqCount++;
		}

		int[] frequent = new int[freqCount];
		int[] itemsBuf = new int[1];
		int w = 0;

		// Write each frequent single-item pattern and collect its ID
		for (int it = 0; it < itemCount; it++) {
			int support = counts[it];
			if (support >= minSupportCount) {
				itemsBuf[0] = it;
				writePattern(itemsBuf, TX_CUM_1, support);
				frequent[w++] = it;
			}
		}

		return frequent;
	}

	/**
	 * Carries the stamp ID and presence flag for the frequent pairs found in one
	 * first-level partition, used to filter subsequent projections.
	 */
	private static final class FrequentTwoInfo {

		/** The stamp value written into sameStamp/laterStamp for frequent pairs. */
		final int stampId;

		/** True if at least one frequent pair was found in this partition. */
		final boolean hasAny;

		/**
		 * Constructs a FrequentTwoInfo with the given stamp and presence flag.
		 *
		 * @param stampId the partition stamp value
		 * @param hasAny  whether any frequent pair was found
		 */
		FrequentTwoInfo(int stampId, boolean hasAny) {
			this.stampId = stampId;
			this.hasAny = hasAny;
		}
	}

	/**
	 * Allocates or resets the same/later stamp arrays when the item count changes.
	 */
	private void ensureTwoStampArrays() {
		if (sameStamp == null || sameStamp.length != itemCount) {
			sameStamp = new int[itemCount];
			laterStamp = new int[itemCount];
			stampId = 1;
		}
	}

	/**
	 * Allocates or resets the reusable pair-count and deduplication arrays when the
	 * item count changes. Also allocates the touched-item tracking arrays used to
	 * avoid scanning all itemCount entries during pair emission.
	 */
	private void ensurePairArrays() {
		if (pairCountSame == null || pairCountSame.length != itemCount) {
			pairCountSame = new int[itemCount];
			pairCountLater = new int[itemCount];
			pairCountStampSame = new int[itemCount];
			pairCountStampLater = new int[itemCount];
			pairLastCidSame = new int[itemCount];
			pairLastCidLater = new int[itemCount];
			touchedItemsSame = new int[itemCount];
			touchedItemsLater = new int[itemCount];
			pairCountCallId = 0;
			pairCustomerCid = 0;
		}
	}

	/**
	 * Counts frequent length-2 sequences of the form (O, x) in the given
	 * first-level partition, distinguishing same-transaction and later-transaction
	 * occurrences. Writes frequent pairs to the output file and stamps their item
	 * IDs for use in subsequent projection reduction.
	 *
	 * <p>Only items that were actually touched during counting are examined during
	 * the emission pass, avoiding the O(itemCount^2) bottleneck of scanning all
	 * items for every partition.
	 *
	 * <p>Same-transaction extensions are only counted for items x &gt; O. Since
	 * every frequent item eventually becomes its own first-level anchor, an
	 * unordered itemset {O, x} would otherwise be discovered twice — once as
	 * "O x" when O is the anchor and once as "x O" when x is the anchor. Both
	 * represent the same pattern (itemsets are unordered sets), so canonical
	 * ascending order within an itemset is enforced by accepting only x &gt; O
	 * in the same-transaction branch. The complementary pair (with x as anchor)
	 * is correctly discovered later when x itself becomes O.
	 *
	 * @param O         the first-level partition item
	 * @param fpMembers members of the first-level partition
	 * @return a FrequentTwoInfo describing which pairs were found
	 * @throws IOException if an I/O error occurs while writing patterns
	 */
	private FrequentTwoInfo findFrequentTwoSequencesInFirstLevelPartition(final int O,
	        ArrayList<FirstLevelMember> fpMembers) throws IOException {

	    ensureTwoStampArrays();
	    ensurePairArrays();

	    // Reset touched item counters for this partition call
	    touchedCountSame = 0;
	    touchedCountLater = 0;

	    // Advance and wrap the partition stamp to avoid clearing arrays
	    int curStamp = ++stampId;
	    if (curStamp == Integer.MAX_VALUE) {
	        Arrays.fill(sameStamp, 0);
	        Arrays.fill(laterStamp, 0);
	        curStamp = stampId = 1;
	    }

	    // Advance and wrap the call ID for pair-count validity tracking
	    int callId = ++pairCountCallId;
	    if (callId == Integer.MAX_VALUE) {
	        Arrays.fill(pairCountStampSame, 0);
	        Arrays.fill(pairCountStampLater, 0);
	        callId = pairCountCallId = 1;
	        // Stamps were zeroed so no touched items from a previous call can
	        // appear valid; reset counters to start fresh
	        touchedCountSame = 0;
	        touchedCountLater = 0;
	    }

	    final int[] countSame = pairCountSame;
	    final int[] countLater = pairCountLater;
	    final int[] countStampSame = pairCountStampSame;
	    final int[] countStampLater = pairCountStampLater;
	    final int[] lastCidSame = pairLastCidSame;
	    final int[] lastCidLater = pairLastCidLater;
	    final int[] tSame = touchedItemsSame;
	    final int[] tLater = touchedItemsLater;

	    for (int mi = 0, msz = fpMembers.size(); mi < msz; mi++) {

	        // Advance and wrap the customer ID counter
	        int cid = ++pairCustomerCid;
	        if (cid == Integer.MAX_VALUE) {
	            Arrays.fill(lastCidSame, 0);
	            Arrays.fill(lastCidLater, 0);
	            cid = pairCustomerCid = 1;
	        }

	        FirstLevelMember fm = fpMembers.get(mi);
	        CustomerData c = fm.projected;

	        boolean seenOInEarlierTransaction = false;

	        final int[] srcItems = c.flatItem;
	        final int base = c.offset;
	        int segCount = c.segmentCount();

	        // Ensure segHasO buffer is large enough for this customer's segments
	        boolean[] segHasO = fm.segHasO;
	        if (segHasO == null || segHasO.length < segCount) {
	            segHasO = new boolean[Math.max(segCount, 8)];
	            fm.segHasO = segHasO;
	        }

	        for (int seg = 0; seg < segCount; seg++) {

	            int start = c.segmentStart(seg);
	            int end = c.segmentEndExclusive(seg);

	            // Check whether this transaction contains O
	            boolean txHasO = containsSortedInRange(srcItems, base + start, base + end, O);
	            segHasO[seg] = txHasO;

	            // Count later-transaction co-occurrences after O has been seen
	            if (seenOInEarlierTransaction) {
	                for (int p = start; p < end; p++) {
	                    int x = srcItems[base + p];
	                    if (!frequentItemFlags[x])
	                        continue;
	                    if (lastCidLater[x] != cid) {
	                        lastCidLater[x] = cid;
	                        if (countStampLater[x] != callId) {
	                            countLater[x] = 0;
	                            countStampLater[x] = callId;
	                            // Record first touch for this item in this partition call
	                            // so the emission loop only visits touched items
	                            tLater[touchedCountLater++] = x;
	                        }
	                        countLater[x]++;
	                    }
	                }
	            }

	            // Count same-transaction co-occurrences in transactions containing O.
	            //
	            // FIX: only accept items x > O (strictly greater). Every frequent item
	            // eventually becomes its own first-level anchor O, so without this guard
	            // an unordered itemset {O, x} would be discovered twice: once as pattern
	            // "O x" (when O is the anchor) and once as pattern "x O" (when x is the
	            // anchor). Both strings represent the same itemset, so only the canonical
	            // ascending form is retained by requiring x > O. The case x < O is
	            // correctly discovered when x itself becomes the anchor later.
	            if (txHasO) {
	                for (int p = start; p < end; p++) {
	                    int x = srcItems[base + p];
	                    if (x <= O || !frequentItemFlags[x])
	                        continue;
	                    if (lastCidSame[x] != cid) {
	                        lastCidSame[x] = cid;
	                        if (countStampSame[x] != callId) {
	                            countSame[x] = 0;
	                            countStampSame[x] = callId;
	                            // Record first touch for this item in this partition call
	                            // so the emission loop only visits touched items
	                            tSame[touchedCountSame++] = x;
	                        }
	                        countSame[x]++;
	                    }
	                }
	                seenOInEarlierTransaction = true;
	            }
	        }
	    }

	    boolean hasAny = false;

	    final int[] itemsBuf = pairItemsBuf;
	    itemsBuf[0] = O;

	    // Emit frequent same-transaction pairs.
	    // Iterates only over items touched during counting rather than all itemCount
	    // entries, fixing the O(itemCount^2) bottleneck in the original code.
	    for (int i = 0; i < touchedCountSame; i++) {
	        int x = tSame[i];
	        // countStampSame[x] == callId is guaranteed by the touch-recording logic
	        // above; the guard is kept as a safety net for wrap-around edge cases
	        int suppSame = (countStampSame[x] == callId) ? countSame[x] : 0;
	        if (suppSame >= minSupportCount) {
	            itemsBuf[1] = x;
	            writePattern(itemsBuf, TX_CUM_SAME_2, suppSame);
	            sameStamp[x] = curStamp;
	            hasAny = true;
	        }
	    }

	    // Emit frequent later-transaction pairs.
	    // Same rationale: iterate only over touched items.
	    for (int i = 0; i < touchedCountLater; i++) {
	        int x = tLater[i];
	        int suppLater = (countStampLater[x] == callId) ? countLater[x] : 0;
	        if (suppLater >= minSupportCount) {
	            itemsBuf[1] = x;
	            writePattern(itemsBuf, TX_CUM_LATER_2, suppLater);
	            laterStamp[x] = curStamp;
	            hasAny = true;
	        }
	    }

	    return new FrequentTwoInfo(curStamp, hasAny);
	}

	/**
	 * Fast-path variant of {@link #findFrequentTwoSequencesInFirstLevelPartition}
	 * used when the database consists of sequences of single items. In this special
	 * case every itemset has exactly one item, so "same-transaction" pairs can
	 * never occur (a pair (O, x) with x != O in the same singleton itemset is
	 * impossible). This means:
	 * <ul>
	 * <li>No same-transaction stamping/counting is needed at all.</li>
	 * <li>Since each member's projection always starts at the first occurrence of O
	 * (by construction of the first-level assignment), position 0 of the projection
	 * is always O itself, so every later position is automatically a
	 * later-transaction co-occurrence - no segment scanning or containment checks
	 * are required.</li>
	 * </ul>
	 * This avoids all per-segment bookkeeping and replaces it with a single linear
	 * scan. Only items actually touched during counting are examined during
	 * emission, avoiding the O(itemCount^2) bottleneck.
	 *
	 * @param O         the first-level partition item
	 * @param fpMembers members of the first-level partition
	 * @return a FrequentTwoInfo describing which pairs were found
	 * @throws IOException if an I/O error occurs while writing patterns
	 */
	private FrequentTwoInfo findFrequentTwoSequencesInFirstLevelPartitionSingleItem(final int O,
			ArrayList<FirstLevelMember> fpMembers) throws IOException {

		ensureTwoStampArrays();
		ensurePairArrays();

		// Reset touched item counter for this partition call.
		// Only the later-transaction path is used in single-item mode.
		touchedCountLater = 0;

		// Advance and wrap the partition stamp to avoid clearing arrays
		int curStamp = ++stampId;
		if (curStamp == Integer.MAX_VALUE) {
			Arrays.fill(sameStamp, 0);
			Arrays.fill(laterStamp, 0);
			curStamp = stampId = 1;
		}

		// Advance and wrap the call ID for pair-count validity tracking
		int callId = ++pairCountCallId;
		if (callId == Integer.MAX_VALUE) {
			Arrays.fill(pairCountStampLater, 0);
			callId = pairCountCallId = 1;
			// Stamps were zeroed so touched items from a previous call are no longer
			// valid; reset the counter to start fresh
			touchedCountLater = 0;
		}

		final int[] countLater = pairCountLater;
		final int[] countStampLater = pairCountStampLater;
		final int[] lastCidLater = pairLastCidLater;
		final int[] tLater = touchedItemsLater;

		for (int mi = 0, msz = fpMembers.size(); mi < msz; mi++) {

			// Advance and wrap the customer ID counter
			int cid = ++pairCustomerCid;
			if (cid == Integer.MAX_VALUE) {
				Arrays.fill(lastCidLater, 0);
				cid = pairCustomerCid = 1;
			}

			FirstLevelMember fm = fpMembers.get(mi);
			CustomerData c = fm.projected;

			final int[] srcItems = c.flatItem;
			final int base = c.offset;
			final int n = c.N;

			// Position 0 is always O (the projection starts at O's first occurrence);
			// every subsequent position is, by construction, a later single-item
			// transaction, so a plain linear scan suffices.
			for (int p = 1; p < n; p++) {
				int x = srcItems[base + p];
				if (!frequentItemFlags[x])
					continue;
				if (lastCidLater[x] != cid) {
					lastCidLater[x] = cid;
					if (countStampLater[x] != callId) {
						countLater[x] = 0;
						countStampLater[x] = callId;
						// Record first touch for this item in this partition call
						// so the emission loop only visits touched items
						tLater[touchedCountLater++] = x;
					}
					countLater[x]++;
				}
			}
		}

		boolean hasAny = false;

		final int[] itemsBuf = pairItemsBuf;
		itemsBuf[0] = O;

		// Emit frequent later-transaction pairs.
		// Iterates only over items touched during counting rather than all itemCount
		// entries, fixing the O(itemCount^2) bottleneck in the original code.
		for (int i = 0; i < touchedCountLater; i++) {
			int x = tLater[i];
			// countStampLater[x] == callId is guaranteed by the touch-recording logic
			// above; the guard is kept as a safety net for wrap-around edge cases
			int suppLater = (countStampLater[x] == callId) ? countLater[x] : 0;
			if (suppLater >= minSupportCount) {
				itemsBuf[1] = x;
				writePattern(itemsBuf, TX_CUM_LATER_2, suppLater);
				laterStamp[x] = curStamp;
				hasAny = true;
			}
		}

		return new FrequentTwoInfo(curStamp, hasAny);
	}

	/**
	 * Holds information about the input database after reading and renumbering.
	 */
	private static final class DatabaseInfo {

		/** The list of customer sequences after renumbering. */
		final List<CustomerData> customers;

		/** Total number of sequences read from the input file. */
		final int databaseSize;

		/**
		 * Constructs a DatabaseInfo with the given customer list and sequence count.
		 *
		 * @param customers    the renumbered customer sequences
		 * @param databaseSize total number of sequences in the database
		 */
		DatabaseInfo(List<CustomerData> customers, int databaseSize) {
			this.customers = customers;
			this.databaseSize = databaseSize;
		}
	}

	/**
	 * A compact open-addressing hash map from int keys to int values, used for
	 * efficient item renumbering during database loading.
	 */
	private static final class Int2IntOpenHashMap {

		/** Sentinel value representing an empty key slot. */
		private static final int EMPTY = Integer.MIN_VALUE;

		/** Key storage array. */
		private int[] keys;

		/** Value storage array, parallel to keys. */
		private int[] values;

		/** Bitmask for fast modulo operations (capacity - 1). */
		private int mask;

		/** Whether the special EMPTY key has been inserted. */
		private boolean hasEmptyKey;

		/** Value associated with the EMPTY key, if present. */
		private int emptyKeyValue;

		/**
		 * Constructs a map with enough capacity for the expected number of entries.
		 *
		 * @param expectedSize anticipated number of entries
		 */
		Int2IntOpenHashMap(int expectedSize) {
			int cap = 1;
			int needed = Math.max(2, (expectedSize * 3) / 2);
			while (cap < needed)
				cap <<= 1;
			keys = new int[cap];
			values = new int[cap];
			Arrays.fill(keys, EMPTY);
			mask = cap - 1;
		}

		/**
		 * Associates the given value with the given key, inserting or updating as
		 * needed.
		 *
		 * @param k the key
		 * @param v the value
		 */
		void put(int k, int v) {
			if (k == EMPTY) {
				hasEmptyKey = true;
				emptyKeyValue = v;
				return;
			}
			int idx = mix(k) & mask;
			while (true) {
				int cur = keys[idx];
				if (cur == EMPTY) {
					keys[idx] = k;
					values[idx] = v;
					return;
				}
				if (cur == k) {
					values[idx] = v;
					return;
				}
				idx = (idx + 1) & mask;
			}
		}

		/**
		 * Returns the value associated with the given key, or {@link #EMPTY} if absent.
		 *
		 * @param k the key to look up
		 * @return the associated value, or EMPTY if not found
		 */
		int get(int k) {
			if (k == EMPTY)
				return hasEmptyKey ? emptyKeyValue : EMPTY;
			int idx = mix(k) & mask;
			while (true) {
				int cur = keys[idx];
				if (cur == EMPTY)
					return EMPTY;
				if (cur == k)
					return values[idx];
				idx = (idx + 1) & mask;
			}
		}

		/**
		 * Applies a bit-mixing hash function to reduce clustering.
		 *
		 * @param x the input integer
		 * @return the mixed hash value
		 */
		private static int mix(int x) {
			x ^= (x >>> 16);
			x *= 0x7feb352d;
			x ^= (x >>> 15);
			x *= 0x846ca68b;
			x ^= (x >>> 16);
			return x;
		}
	}

	/**
	 * Reads the input database file in SPMF format, collects all sequences as
	 * CustomerData objects, and renumbers items to contiguous IDs starting from 0.
	 * Populates {@link #idToItem} with the reverse mapping. Also detects whether
	 * the database consists solely of single-item sequences (every itemset has
	 * exactly one item), enabling the faster processing path in
	 * {@link #singleItemMode}.
	 *
	 * @param input path to the input file
	 * @return a DatabaseInfo containing the renumbered sequences and the total
	 *         sequence count
	 * @throws IOException if an I/O error occurs while reading the file
	 */
	private DatabaseInfo readDatabaseAsCustomerDataAndRenumber(String input) throws IOException {

		final ArrayList<CustomerData> out = new ArrayList<CustomerData>();
		final IntHashSet uniqueItems = new IntHashSet(1024);

		// Reset detection of the single-item fast-path eligibility for this run
		singleItemMode = true;

		int sequenceCount = 0;

		// Reusable flat buffers grown as needed during parsing
		int[] flatItemsWork = new int[128];
		int[] flatTxWork = new int[128];

		try (BufferedReader reader = new BufferedReader(new FileReader(input), 1 << 20)) {

			String line;
			while ((line = reader.readLine()) != null) {

				final int len = line.length();
				int i = 0;

				// Skip leading whitespace and comment lines
				while (i < len && line.charAt(i) <= ' ')
					i++;
				if (i >= len)
					continue;

				char c0 = line.charAt(i);
				if (c0 == '#' || c0 == '%' || c0 == '@')
					continue;

				int flatLen = 0;
				int tx = 1;
				int itemsetSize = 0;
				boolean stop = false;

				while (i < len && !stop) {

					// Skip whitespace between tokens
					while (i < len && line.charAt(i) <= ' ')
						i++;
					if (i >= len)
						break;

					boolean negative = false;
					if (line.charAt(i) == '-') {
						negative = true;
						i++;
					}

					// Parse the next integer token
					int value = 0;
					while (i < len) {
						char ch = line.charAt(i);
						if (ch < '0' || ch > '9')
							break;
						value = value * 10 + (ch - '0');
						i++;
					}
					if (negative)
						value = -value;

					if (value == -1) {
						// Itemset separator: advance transaction counter
						if (itemsetSize > 0) {
							tx++;
							itemsetSize = 0;
						}
					} else if (value == -2) {
						// Sequence terminator
						stop = true;
					} else {
						// Grow flat buffers if needed and append item
						if (flatLen == flatItemsWork.length) {
							int newCap = flatItemsWork.length * 2;
							flatItemsWork = Arrays.copyOf(flatItemsWork, newCap);
							flatTxWork = Arrays.copyOf(flatTxWork, newCap);
						}
						flatItemsWork[flatLen] = value;
						flatTxWork[flatLen] = tx;
						flatLen++;
						itemsetSize++;
						// As soon as any itemset has more than one item, the fast
						// single-item-sequence path can no longer be used.
						if (itemsetSize > 1) {
							singleItemMode = false;
						}
						uniqueItems.add(value);
					}
				}

				sequenceCount++;

				if (flatLen > 0) {
					out.add(new CustomerData(Arrays.copyOf(flatItemsWork, flatLen),
							Arrays.copyOf(flatTxWork, flatLen)));
				}
			}
		}

		// Build sorted original-item array and assign contiguous IDs
		final int m = uniqueItems.size();
		final int[] originals = new int[m];
		final int[] idx = new int[1];
		uniqueItems.forEachKey(new IntKeyProcedure() {
			@Override
			public void apply(int key) {
				originals[idx[0]++] = key;
			}
		});
		Arrays.sort(originals);

		this.idToItem = originals;

		// Build reverse map from original item to new contiguous ID
		final Int2IntOpenHashMap origToId = new Int2IntOpenHashMap(m);
		for (int newId = 0; newId < m; newId++) {
			origToId.put(originals[newId], newId);
		}

		// Remap all item values in-place to their contiguous IDs
		for (int ci = 0, cs = out.size(); ci < cs; ci++) {
			CustomerData cd = out.get(ci);
			final int[] items = cd.flatItem;
			final int base = cd.offset;
			final int n = cd.N;
			for (int p = 0; p < n; p++) {
				items[base + p] = origToId.get(items[base + p]);
			}
		}

		return new DatabaseInfo(out, sequenceCount);
	}

	/**
	 * Writes a discovered pattern to the output file and increments the pattern
	 * count. This method is called by the {@link DiscEngine} via the
	 * {@link DiscHost} interface.
	 *
	 * @param items   internal item IDs forming the pattern
	 * @param txCum   cumulative transaction indices defining itemset boundaries
	 * @param support the absolute support count of the pattern
	 * @throws IOException if an I/O error occurs while writing
	 */
	@Override
	public void writePattern(int[] items, int[] txCum, int support) throws IOException {
		writePatternToFile(items, txCum, support);
		patternCount++;
	}

	/**
	 * Formats and writes a single pattern line to the output file using the
	 * reusable output buffer. Items are translated back to their original values
	 * via {@link #idToItem}.
	 *
	 * @param items   internal item IDs forming the pattern
	 * @param txCum   cumulative transaction indices defining itemset boundaries
	 * @param support the absolute support count of the pattern
	 * @throws IOException if an I/O error occurs while writing
	 */
	private void writePatternToFile(int[] items, int[] txCum, int support) throws IOException {

		StringBuilder builder = outBuffer;
		builder.setLength(0);

		// Append each itemset separated by -1, translating IDs to original values
		int i = 0;
		while (i < items.length) {

			builder.append(idToItem[items[i]]);
			int j = i + 1;
			while (j < items.length && txCum[j] == txCum[i]) {
				builder.append(' ').append(idToItem[items[j]]);
				j++;
			}
			builder.append(" -1 ");
			i = j;
		}

		builder.append("#SUP: ").append(support);

		// Copy to char buffer and write in one call to avoid per-char overhead
		int outLen = builder.length();
		if (outCharBuffer.length < outLen) {
			outCharBuffer = new char[Math.max(outLen, outCharBuffer.length * 2)];
		}
		builder.getChars(0, outLen, outCharBuffer, 0);
		writer.write(outCharBuffer, 0, outLen);
		writer.newLine();
	}

	/**
	 * Samples the current JVM heap usage and updates {@link #maxMemory} if a new
	 * peak is observed. Calls are throttled so that the JVM is queried only once
	 * every 32 invocations to reduce overhead in tight mining loops.
	 */
	@Override
	public void checkMemory() {
		if ((memoryCheckCounter++ & MEMORY_CHECK_MASK) != 0) {
			return;
		}
		// Compute current heap usage in megabytes
		double currentMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576d;
		if (currentMemory > maxMemory)
			maxMemory = currentMemory;
	}

	/**
	 * Prints a summary of the last algorithm run to standard output, including
	 * database size, support thresholds, pattern count, runtime, and peak memory.
	 */
	public void printStats() {
		System.out.println("============= DISC-ALL 2.68 - STATS =============");
		System.out.println(" Database size  : " + databaseSize);
		System.out.println(" Minsup (rel.)  : " + minSupportRelative);
		System.out.println(" Minsup (abs.)  : " + minSupportCount);
		System.out.println(" Single-item db : " + singleItemMode);
		System.out.println(" Patterns found : " + patternCount);
		System.out.println(" Time           : " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory     : " + maxMemory + " MB");
		System.out.println("==========================================================");
	}

	/**
	 * Returns the total number of frequent patterns found in the last run.
	 *
	 * @return the pattern count
	 */
	public int getPatternCount() {
		return patternCount;
	}

	/**
	 * Returns the wall-clock runtime of the last algorithm run in milliseconds.
	 *
	 * @return elapsed time in milliseconds
	 */
	public long getRuntime() {
		return endTimestamp - startTimestamp;
	}

	/**
	 * Returns the peak memory usage observed during the last algorithm run.
	 *
	 * @return peak memory in megabytes
	 */
	public double getMaxMemory() {
		return maxMemory;
	}

	/**
	 * Returns whether the last processed database was detected as consisting solely
	 * of single-item sequences, in which case the faster processing path was used.
	 *
	 * @return {@code true} if the single-item fast path was used
	 */
	public boolean isSingleItemMode() {
		return singleItemMode;
	}

	/**
	 * A binary min-heap over primitive int keys, used to drive partition traversal
	 * in ascending key order without boxing overhead.
	 */
	private static final class IntMinHeap {

		/** Backing array storing heap elements. */
		private int[] heap;

		/** Number of elements currently in the heap. */
		private int size;

		/**
		 * Constructs an empty heap with the given initial capacity.
		 *
		 * @param expectedSize anticipated number of elements
		 */
		IntMinHeap(int expectedSize) {
			heap = new int[Math.max(4, expectedSize)];
		}

		/**
		 * Returns {@code true} if the heap contains no elements.
		 *
		 * @return whether the heap is empty
		 */
		boolean isEmpty() {
			return size == 0;
		}

		/** Removes all elements from the heap. */
		void clear() {
			size = 0;
		}

		/**
		 * Inserts a value into the heap, growing the backing array if necessary.
		 *
		 * @param v the value to insert
		 */
		void add(int v) {
			if (size == heap.length)
				heap = Arrays.copyOf(heap, heap.length * 2);
			// Sift up from the last position
			int i = size++;
			while (i > 0) {
				int p = (i - 1) >>> 1;
				if (heap[p] <= v)
					break;
				heap[i] = heap[p];
				i = p;
			}
			heap[i] = v;
		}

		/**
		 * Removes and returns the smallest value in the heap.
		 *
		 * @return the minimum value
		 */
		int poll() {
			int res = heap[0];
			int v = heap[--size];
			if (size == 0)
				return res;
			// Sift down from the root
			int i = 0;
			int half = size >>> 1;
			while (i < half) {
				int l = (i << 1) + 1;
				int r = l + 1;
				int c = (r < size && heap[r] < heap[l]) ? r : l;
				if (heap[c] >= v)
					break;
				heap[i] = heap[c];
				i = c;
			}
			heap[i] = v;
			return res;
		}
	}
}