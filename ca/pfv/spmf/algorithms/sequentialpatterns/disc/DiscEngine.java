package ca.pfv.spmf.algorithms.sequentialpatterns.disc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Core mining engine for the DISC sequential pattern algorithm. Drives
 * apriori-style candidate generation and frequent-sequence discovery across
 * multiple length levels, delegating output to a {@link DiscHost}.
 * 
 * @author Chen Hui
 */
final class DiscEngine {

	/**
	 * Minimum number of customers that must support a sequence for it to be
	 * frequent.
	 */
	private final int minSupportCount;

	/** Callback used to write discovered patterns and check memory. */
	private final DiscHost host;

	/**
	 * True when the source database consists of sequences of single items (every
	 * itemset has exactly one item). In that case a "same transaction" extension
	 * can never occur, so a much simpler and faster scanning path can be used that
	 * avoids computing transaction-segment boundaries altogether.
	 */
	private final boolean singleItemMode;

	/**
	 * Stamp array recording, per item, the id of the last customer for which the
	 * item was observed to be present (see {@link #markItemPresence}). Reusing a
	 * monotonically increasing stamp instead of clearing the array between calls
	 * keeps presence marking at O(customer length) rather than O(itemCount) per
	 * customer, following the same pattern used elsewhere in this package (e.g.
	 * {@code sameStamp}/{@code laterStamp} in {@code AlgoDiscALL}).
	 */
	private int[] presenceStamp;

	/**
	 * Monotonically increasing counter identifying the customer currently marked in
	 * {@link #presenceStamp}.
	 */
	private int presenceCustomerId = 0;

	/**
	 * Reusable workspace for prefix-match computations to avoid repeated
	 * allocations.
	 */
	private final PrefixMatchWorkspace pmw = new PrefixMatchWorkspace();

	/**
	 * Constructs a DiscEngine with the given minimum support and output host.
	 *
	 * @param minSupportCount minimum number of supporting customers required
	 * @param host            callback for writing patterns and checking memory
	 * @param singleItemMode  {@code true} if the source database consists solely of
	 *                        single-item sequences, enabling a faster scanning path
	 * @param itemCount       number of distinct (renumbered) items in the database,
	 *                        used to size internal per-item presence-tracking
	 *                        arrays
	 */
	DiscEngine(int minSupportCount, DiscHost host, boolean singleItemMode, int itemCount) {
		this.minSupportCount = minSupportCount;
		this.host = host;
		this.singleItemMode = singleItemMode;
		// Guard against a zero-length array in the (unlikely) case itemCount == 0;
		// isItemPresent() also bounds-checks defensively.
		this.presenceStamp = new int[Math.max(itemCount, 1)];
	}

	/**
	 * Scans {@code customer}'s items and stamps each distinct item as "present" for
	 * the current customer id, enabling subsequent O(1) lookups via
	 * {@link #isItemPresent(int)}. This costs O(customer length), the same order as
	 * a single extension-search attempt, but lets many such attempts be skipped
	 * entirely when their leading item is absent from the customer.
	 *
	 * @param customer the customer sequence to scan
	 */
	private void markItemPresence(CustomerData customer) {

		int cid = ++presenceCustomerId;
		if (cid == Integer.MAX_VALUE) {
			Arrays.fill(presenceStamp, 0);
			cid = presenceCustomerId = 1;
		}

		final int[] items = customer.flatItem;
		final int base = customer.offset;
		final int n = customer.N;

		for (int p = 0; p < n; p++) {
			int item = items[base + p];
			if (item >= 0 && item < presenceStamp.length) {
				presenceStamp[item] = cid;
			}
		}
	}

	/**
	 * Returns whether {@code item} was observed present in the customer most
	 * recently scanned via {@link #markItemPresence}.
	 *
	 * @param item the item to test
	 * @return {@code true} if the item occurs in the most recently marked customer
	 */
	private boolean isItemPresent(int item) {
		return item >= 0 && item < presenceStamp.length && presenceStamp[item] == presenceCustomerId;
	}

	/**
	 * Groups a sorted list of frequent prefixes into contiguous ranges sharing the
	 * same leading item ({@code items[0]}). Because {@code frequentPrefixes} is
	 * always sorted by {@link CandidateKey#COMPARATOR}, which compares sequences
	 * position by position, all entries sharing the same leading item are
	 * guaranteed to be contiguous, so this grouping can be computed with a single
	 * linear pass and preserves the original ascending traversal order exactly.
	 *
	 * <p>
	 * This index is built once per mining level (not once per customer), and lets
	 * {@link #buildAprioriKMinimum} test an entire group of candidate prefixes with
	 * a single O(1) presence check instead of attempting an expensive extension
	 * search for each one individually.
	 */
	private static final class PrefixGroupIndex {

		/**
		 * Distinct leading items, in ascending order matching the original list order.
		 */
		final int[] leadingItems;

		/**
		 * Inclusive start index (into the original list) of each leading-item group.
		 */
		final int[] groupStart;

		/** Exclusive end index (into the original list) of each leading-item group. */
		final int[] groupEnd;

		/**
		 * Constructs a PrefixGroupIndex from parallel group-boundary arrays.
		 *
		 * @param leadingItems distinct leading items in list order
		 * @param groupStart   inclusive start index of each group
		 * @param groupEnd     exclusive end index of each group
		 */
		PrefixGroupIndex(int[] leadingItems, int[] groupStart, int[] groupEnd) {
			this.leadingItems = leadingItems;
			this.groupStart = groupStart;
			this.groupEnd = groupEnd;
		}
	}

	/**
	 * Builds a {@link PrefixGroupIndex} over {@code frequentPrefixes}, grouping
	 * contiguous runs of entries that share the same leading item.
	 *
	 * @param frequentPrefixes sorted list of frequent prefixes for the current
	 *                         level
	 * @return the computed group index
	 */
	private static PrefixGroupIndex buildPrefixGroupIndex(List<CandidateKey> frequentPrefixes) {

		final int n = frequentPrefixes.size();

		if (n == 0) {
			return new PrefixGroupIndex(new int[0], new int[0], new int[0]);
		}

		// First pass: count distinct leading items. Entries sharing a leading item
		// are contiguous because the list is sorted position-by-position.
		int distinctCount = 1;
		int prevLeading = frequentPrefixes.get(0).items[0];
		for (int i = 1; i < n; i++) {
			int leading = frequentPrefixes.get(i).items[0];
			if (leading != prevLeading) {
				distinctCount++;
				prevLeading = leading;
			}
		}

		int[] leadingItems = new int[distinctCount];
		int[] groupStart = new int[distinctCount];
		int[] groupEnd = new int[distinctCount];

		int g = 0;
		leadingItems[0] = frequentPrefixes.get(0).items[0];
		groupStart[0] = 0;
		prevLeading = leadingItems[0];

		// Second pass: record group boundaries.
		for (int i = 1; i < n; i++) {
			int leading = frequentPrefixes.get(i).items[0];
			if (leading != prevLeading) {
				groupEnd[g] = i;
				g++;
				leadingItems[g] = leading;
				groupStart[g] = i;
				prevLeading = leading;
			}
		}
		groupEnd[g] = n;

		return new PrefixGroupIndex(leadingItems, groupStart, groupEnd);
	}

	/**
	 * Iteratively mines frequent sequences of length &ge; 4 using bi-level DISC
	 * rounds, starting from the provided frequent prefixes of length {@code k-1}.
	 *
	 * @param customers        all customer sequences to mine
	 * @param frequentPrefixes frequent sequences of length {@code k-1} to seed the
	 *                         first round
	 * @throws IOException if writing a pattern fails
	 */
	void mineFromLength4WithBiLevel(List<CustomerData> customers, List<CandidateKey> frequentPrefixes)
			throws IOException {

		int k = 4;

		// Reuse the active buffer across iterations to avoid repeated allocations
		final ArrayList<Candidate> active = new ArrayList<Candidate>(customers.size());

		while (true) {

			// Build a leading-item group index over the current frequent prefixes
			// once per level (rather than once per customer). This lets
			// buildAprioriKMinimum skip whole groups of candidate prefixes whose
			// leading item does not occur in a given customer's sequence, without
			// attempting an O(prefixLength * N) extension search for each one -
			// the fix for the O(prefixCount) linear scan bottleneck.
			PrefixGroupIndex groupIndex = buildPrefixGroupIndex(frequentPrefixes);

			// Build the initial candidate for each customer
			active.clear();
			for (CustomerData customer : customers) {
				Candidate candidate = buildAprioriKMinimum(customer, frequentPrefixes, groupIndex, k);
				if (candidate != null) {
					active.add(candidate);
				}
			}

			if (active.size() < minSupportCount) {
				break;
			}

			DiscRoundResult result = runDiscRoundForLength(active, frequentPrefixes);

			if (result.frequentThisLevel.isEmpty() || result.frequentNextLevel.isEmpty()) {
				break;
			}

			// Advance to the next level
			frequentPrefixes = result.frequentNextLevel;
			k += 2;

			host.checkMemory();
		}
	}

	/**
	 * Runs one DISC round over the given active candidates, discovers all frequent
	 * sequences at the current length, and collects candidate keys for the next
	 * length.
	 *
	 * @param initialActive    candidates entering this round
	 * @param frequentPrefixes frequent prefixes used to regenerate candidates after
	 *                         pruning
	 * @return the frequent sequences found and the candidates for the next round
	 * @throws IOException if writing a pattern fails
	 */
	DiscRoundResult runDiscRoundForLength(List<Candidate> initialActive, List<CandidateKey> frequentPrefixes)
			throws IOException {

		// Load all candidates into the sorted database
		ActiveSortedDatabase sortedDatabase = new ActiveSortedDatabase();
		for (Candidate candidate : initialActive) {
			sortedDatabase.add(candidate);
		}

		List<CandidateKey> frequentSequences = new ArrayList<CandidateKey>();
		List<CandidateKey> frequentNextLevel = new ArrayList<CandidateKey>();

		// Reuse this buffer when converting a candidate block to a customer list
		final ArrayList<CustomerData> blockCustomers = new ArrayList<CustomerData>();

		while (sortedDatabase.size() >= minSupportCount) {

			CandidateKey d1 = sortedDatabase.firstKey();
			CandidateKey dg = sortedDatabase.keyAtRank(minSupportCount);

			if (d1 == null || dg == null) {
				break;
			}

			int comparison = CandidateKey.compare(d1, dg);

			if (comparison == 0) {
				// The minimum key has enough support: output it and process its block
				List<Candidate> block = sortedDatabase.removeKey(d1);
				int support = block.size();

				host.writePattern(d1.items, d1.txCum, support);
				frequentSequences.add(d1);

				// Count extensions over all customers in this block
				blockCustomers.clear();
				blockCustomers.ensureCapacity(block.size());
				for (Candidate candidate : block) {
					blockCustomers.add(candidate.customer);
				}
				frequentNextLevel.addAll(findFrequentExtensionsByCounting(blockCustomers, d1));

				// Advance each candidate past the current key
				for (Candidate candidate : block) {
					Candidate next = buildAprioriConditionalKMinimum(candidate, dg, false, frequentPrefixes);
					if (next != null) {
						sortedDatabase.add(next);
					}
				}

			} else {
				// The minimum key cannot reach support; skip all candidates below the threshold
				List<Candidate> toRecompute = sortedDatabase.removeAllLessThan(dg);

				for (Candidate candidate : toRecompute) {
					Candidate next = buildAprioriConditionalKMinimum(candidate, dg, true, frequentPrefixes);
					if (next != null) {
						sortedDatabase.add(next);
					}
				}
			}

			host.checkMemory();
		}

		frequentNextLevel.sort(CandidateKey.COMPARATOR);

		return new DiscRoundResult(frequentSequences, frequentNextLevel);
	}

	// --- Extension key encoding helpers ---

	/**
	 * Encodes an (item, same-transaction) extension as a single long key.
	 *
	 * @param item            the extension item
	 * @param sameTransaction true if the extension is in the same transaction as
	 *                        the prefix end
	 * @return the encoded long key
	 */
	private static long encodeExtKey(int item, boolean sameTransaction) {
		return (((long) item) << 1) | (sameTransaction ? 1L : 0L);
	}

	/**
	 * Decodes the item from an extension key produced by {@link #encodeExtKey}.
	 *
	 * @param key the encoded key
	 * @return the item value
	 */
	private static int decodeExtItem(long key) {
		return (int) (key >>> 1);
	}

	/**
	 * Decodes whether an extension key produced by {@link #encodeExtKey} represents
	 * a same-transaction extension.
	 *
	 * @param key the encoded key
	 * @return true if the extension is in the same transaction
	 */
	private static boolean decodeExtSame(long key) {
		return (key & 1L) == 1L;
	}

	/**
	 * Packs an (item, transaction-number) pair into a single long that preserves
	 * lexicographic ordering (item first, then transaction number), so that the
	 * "minimum extension" search can be done with a single comparison instead of
	 * nested int comparisons.
	 *
	 * @param itemVal the item value
	 * @param txVal   the transaction number
	 * @return the packed long key
	 */
	private static long packKey(int itemVal, int txVal) {
		return (((long) itemVal) << 32) | (txVal & 0xffffffffL);
	}

	/**
	 * Counts all frequent one-step extensions of {@code prefix} over the given
	 * customers and writes each frequent extension as a pattern. Dispatches to the
	 * faster single-item-sequence path when applicable.
	 *
	 * @param customers list of customer sequences to count extensions over
	 * @param prefix    the current frequent prefix to extend
	 * @return sorted list of CandidateKeys for each frequent extension found
	 * @throws IOException if writing a pattern fails
	 */
	/**
	 * Counts all frequent one-step extensions of {@code prefix} over the given
	 * customers and writes each frequent extension as a pattern. Dispatches to the
	 * faster single-item-sequence path when applicable.
	 *
	 * <p>
	 * Same-transaction extensions are only recorded for items strictly greater than
	 * the last item in {@code prefix}. This enforces canonical ascending order
	 * within itemsets and prevents the same unordered itemset from being emitted
	 * multiple times with different item orderings.
	 *
	 * @param customers list of customer sequences to count extensions over
	 * @param prefix    the current frequent prefix to extend
	 * @return sorted list of CandidateKeys for each frequent extension found
	 * @throws IOException if writing a pattern fails
	 */
	List<CandidateKey> findFrequentExtensionsByCounting(List<CustomerData> customers, CandidateKey prefix)
			throws IOException {

		if (singleItemMode) {
			return findFrequentExtensionsByCountingSingleItem(customers, prefix);
		}

		int prefixLength = prefix.items.length;

		// FIX: record the last item of the prefix so the same-transaction branch
		// can enforce canonical ascending itemset order (only accept items strictly
		// greater than lastPrefixItem as same-transaction extensions).
		final int lastPrefixItem = prefix.items[prefixLength - 1];

		final LongIntHashMap counts = new LongIntHashMap(16);
		final LongHashSet supportedThisCustomer = new LongHashSet(16);

		// Procedure that increments the global count for each extension seen for a
		// customer
		final LongKeyProcedure incrementer = new LongKeyProcedure() {
			@Override
			public void apply(long key) {
				counts.increment(key);
			}
		};

		for (CustomerData customer : customers) {

			boolean[] prefixEndPositions = findAllPrefixMatchEnds(customer, prefix);
			if (!pmw.matched) {
				continue;
			}

			supportedThisCustomer.clear();

			boolean hasPrefixEndInEarlierTransaction = false;
			int segCount = customer.segmentCount();

			for (int seg = 0; seg < segCount; seg++) {

				int start = customer.segmentStart(seg);
				int end = customer.segmentEndExclusive(seg);

				boolean hasPrefixEndBeforePositionInSameTransaction = false;
				boolean hasPrefixEndInThisTransaction = false;

				for (int p = start; p < end; p++) {

					int item = customer.itemAt(p);

					// FIX: record same-transaction extension only when the item is
					// strictly greater than the prefix's last item, preserving
					// canonical ascending order within the itemset and preventing
					// duplicate patterns with reversed item order.
					if (hasPrefixEndBeforePositionInSameTransaction && item > lastPrefixItem) {
						supportedThisCustomer.add(encodeExtKey(item, true));
					}

					// Record cross-transaction extension if prefix ended in an earlier transaction.
					// No ordering constraint applies here: cross-transaction order is meaningful
					// and "O then x" vs "x then O" in different transactions are distinct patterns.
					if (hasPrefixEndInEarlierTransaction) {
						supportedThisCustomer.add(encodeExtKey(item, false));
					}

					if (prefixEndPositions[p]) {
						hasPrefixEndBeforePositionInSameTransaction = true;
						hasPrefixEndInThisTransaction = true;
					}
				}

				if (hasPrefixEndInThisTransaction) {
					hasPrefixEndInEarlierTransaction = true;
				}
			}

			// Add each extension seen for this customer to the global counts
			supportedThisCustomer.forEachKey(incrementer);
		}

		List<CandidateKey> frequentExtensions = new ArrayList<CandidateKey>();
		final int baseTransactionNumber = prefix.txCum[prefixLength - 1];

		// Emit each extension that meets minimum support as a pattern
		counts.forEachEntry(new LongIntProcedure() {
			@Override
			public void apply(long key, int support) {

				if (support < minSupportCount) {
					return;
				}

				int item = decodeExtItem(key);
				boolean same = decodeExtSame(key);

				// Build the extended items and txCum arrays
				int[] items = new int[prefixLength + 1];
				int[] txCum = new int[prefixLength + 1];

				System.arraycopy(prefix.items, 0, items, 0, prefixLength);
				System.arraycopy(prefix.txCum, 0, txCum, 0, prefixLength);

				items[prefixLength] = item;
				txCum[prefixLength] = same ? baseTransactionNumber : baseTransactionNumber + 1;

				try {
					host.writePattern(items, txCum, support);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				frequentExtensions.add(new CandidateKey(items, txCum));
			}
		});

		frequentExtensions.sort(CandidateKey.COMPARATOR);

		return frequentExtensions;
	}

	/**
	 * Fast-path variant of {@link #findFrequentExtensionsByCounting} used when the
	 * source database consists of sequences of single items. Same-transaction
	 * extensions are structurally impossible in that case (an itemset never has
	 * more than one item), so only cross-transaction extensions are ever recorded,
	 * and the scan can walk positions directly instead of enumerating transaction
	 * segments.
	 *
	 * @param customers list of customer sequences to count extensions over
	 * @param prefix    the current frequent prefix to extend
	 * @return sorted list of CandidateKeys for each frequent extension found
	 * @throws IOException if writing a pattern fails
	 */
	private List<CandidateKey> findFrequentExtensionsByCountingSingleItem(List<CustomerData> customers,
			CandidateKey prefix) throws IOException {

		final int prefixLength = prefix.items.length;

		final LongIntHashMap counts = new LongIntHashMap(16);
		final LongHashSet supportedThisCustomer = new LongHashSet(16);

		final LongKeyProcedure incrementer = new LongKeyProcedure() {
			@Override
			public void apply(long key) {
				counts.increment(key);
			}
		};

		for (CustomerData customer : customers) {

			boolean[] prefixEndPositions = findAllPrefixMatchEndsSingleItem(customer, prefix);
			if (!pmw.matched) {
				continue;
			}

			supportedThisCustomer.clear();

			boolean seenPrefixEndBefore = false;
			final int n = customer.N;

			// Every position is its own transaction, so "cross-transaction" reduces to
			// "any earlier position had a prefix match end".
			for (int p = 0; p < n; p++) {

				if (seenPrefixEndBefore) {
					int item = customer.itemAt(p);
					supportedThisCustomer.add(encodeExtKey(item, false));
				}

				if (prefixEndPositions[p]) {
					seenPrefixEndBefore = true;
				}
			}

			supportedThisCustomer.forEachKey(incrementer);
		}

		List<CandidateKey> frequentExtensions = new ArrayList<CandidateKey>();
		final int baseTransactionNumber = prefix.txCum[prefixLength - 1];

		counts.forEachEntry(new LongIntProcedure() {
			@Override
			public void apply(long key, int support) {

				if (support < minSupportCount) {
					return;
				}

				int item = decodeExtItem(key);
				// In single-item mode the "same transaction" bit is always false.

				int[] items = new int[prefixLength + 1];
				int[] txCum = new int[prefixLength + 1];

				System.arraycopy(prefix.items, 0, items, 0, prefixLength);
				System.arraycopy(prefix.txCum, 0, txCum, 0, prefixLength);

				items[prefixLength] = item;
				txCum[prefixLength] = baseTransactionNumber + 1;

				try {
					host.writePattern(items, txCum, support);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				frequentExtensions.add(new CandidateKey(items, txCum));
			}
		});

		frequentExtensions.sort(CandidateKey.COMPARATOR);

		return frequentExtensions;
	}

	// --- Apriori-KMS / CKMS style candidate generation ---

	/**
	 * Finds the lexicographically smallest length-{@code k} candidate for
	 * {@code customer} using any of the given frequent prefixes of length
	 * {@code k-1}.
	 *
	 * <p>
	 * Rather than scanning every prefix in {@code frequentPrefixes} in order (which
	 * costs O(prefixCount) expensive extension-search attempts per customer), this
	 * uses the precomputed {@code groupIndex} to test, in O(1), whether a whole
	 * contiguous group of prefixes sharing the same leading item can possibly match
	 * this customer at all (i.e. whether that leading item occurs anywhere in the
	 * customer's sequence). Groups whose leading item is absent are skipped
	 * entirely without ever calling {@link #minimumExtensionForPrefix}. Because
	 * groups are visited in the same ascending order as the original sorted list,
	 * and prefixes within a matching group are tried in their original order, this
	 * returns exactly the same result as the naive full scan.
	 *
	 * @param customer         the customer sequence to search
	 * @param frequentPrefixes sorted list of frequent prefixes of length
	 *                         {@code k-1}
	 * @param groupIndex       precomputed leading-item grouping of
	 *                         {@code frequentPrefixes}
	 * @param k                target sequence length
	 * @return the smallest valid candidate, or {@code null} if none exists
	 */
	private Candidate buildAprioriKMinimum(CustomerData customer, List<CandidateKey> frequentPrefixes,
			PrefixGroupIndex groupIndex, int k) {

		if (frequentPrefixes == null || frequentPrefixes.isEmpty()) {
			return null;
		}

		if (customer.N < k) {
			return null;
		}

		// Mark which items occur in this customer once (O(customer length)), so
		// each group below can be tested for feasibility in O(1).
		markItemPresence(customer);

		final int[] leadingItems = groupIndex.leadingItems;
		final int[] groupStart = groupIndex.groupStart;
		final int[] groupEnd = groupIndex.groupEnd;
		final int groupCount = leadingItems.length;

		for (int g = 0; g < groupCount; g++) {

			int leadingItem = leadingItems[g];

			if (!isItemPresent(leadingItem)) {
				// No prefix in this group can possibly match: its leading item does
				// not even occur in the customer's sequence. Skip the entire group
				// without attempting any O(prefixLength * N) extension search.
				continue;
			}

			int start = groupStart[g];
			int end = groupEnd[g];

			for (int idx = start; idx < end; idx++) {

				CandidateKey prefix = frequentPrefixes.get(idx);

				// Skip prefixes that are not exactly length k-1
				if (prefix.items.length != k - 1) {
					continue;
				}

				Candidate candidate = minimumExtensionForPrefix(customer, prefix, null, true, idx);
				if (candidate != null) {
					return candidate;
				}
			}
		}

		return null;
	}

	/**
	 * Returns the first index in {@code list} (starting from {@code fromIndex})
	 * whose element is &ge; {@code key} according to {@link CandidateKey#compare}.
	 *
	 * @param list      sorted list to search
	 * @param key       the key to locate
	 * @param fromIndex starting search position
	 * @return the insertion point for {@code key}
	 */
	private static int lowerBound(List<CandidateKey> list, CandidateKey key, int fromIndex) {
		int lo = Math.max(0, fromIndex);
		int hi = list.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (CandidateKey.compare(list.get(mid), key) < 0) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	/**
	 * Finds the smallest candidate for {@code previous.customer} that is greater
	 * than (or equal to, if {@code inclusive}) {@code threshold}, resuming the
	 * prefix search from where the previous candidate left off.
	 *
	 * @param previous         the candidate from the previous iteration
	 * @param threshold        the lower bound key the new candidate must meet
	 * @param inclusive        if {@code true}, a candidate equal to
	 *                         {@code threshold} is accepted
	 * @param frequentPrefixes sorted list of frequent prefixes to search over
	 * @return the next valid candidate, or {@code null} if none exists
	 */
	private Candidate buildAprioriConditionalKMinimum(Candidate previous, CandidateKey threshold, boolean inclusive,
			List<CandidateKey> frequentPrefixes) {

		if (frequentPrefixes == null || frequentPrefixes.isEmpty()) {
			return null;
		}

		CandidateKey thresholdPrefix = CandidateKey.prefixOf(threshold);

		int idx = previous.aprioriPointer;
		if (idx < 0) {
			idx = 0;
		}

		// Use binary search to skip prefixes that are already below the threshold
		// prefix
		idx = lowerBound(frequentPrefixes, thresholdPrefix, idx);

		final int prefixCount = frequentPrefixes.size();

		for (; idx < prefixCount; idx++) {

			CandidateKey prefix = frequentPrefixes.get(idx);
			int prefixComparison = CandidateKey.compare(prefix, thresholdPrefix);

			Candidate candidate;

			if (prefixComparison == 0) {
				// Same prefix as the threshold: apply the threshold constraint
				candidate = minimumExtensionForPrefix(previous.customer, prefix, threshold, inclusive, idx);
			} else {
				// Prefix is beyond the threshold prefix: any extension is valid
				candidate = minimumExtensionForPrefix(previous.customer, prefix, null, true, idx);
			}

			if (candidate != null) {
				return candidate;
			}
		}

		return null;
	}

	/**
	 * Finds the lexicographically smallest extension of {@code prefix} in
	 * {@code customer} without an apriori pointer.
	 *
	 * @param customer  the customer sequence to search
	 * @param prefix    the prefix to extend
	 * @param threshold optional lower-bound constraint on the extension key; may be
	 *                  {@code null}
	 * @param inclusive if {@code true}, an extension equal to {@code threshold} is
	 *                  accepted
	 * @return the smallest valid candidate, or {@code null} if none exists
	 */
	Candidate minimumExtensionForPrefix(CustomerData customer, CandidateKey prefix, CandidateKey threshold,
			boolean inclusive) {
		return minimumExtensionForPrefix(customer, prefix, threshold, inclusive, -1);
	}

	/**
	 * Finds the lexicographically smallest extension of {@code prefix} in
	 * {@code customer} that satisfies the optional threshold constraint. Dispatches
	 * to the faster single-item-sequence path when applicable.
	 *
	 * <p>
	 * Same-transaction extensions are only considered for items strictly greater
	 * than the last item in {@code prefix}. This enforces canonical ascending order
	 * within itemsets and prevents duplicate patterns from being generated with
	 * reversed item orderings.
	 *
	 * @param customer       the customer sequence to search
	 * @param prefix         the prefix to extend
	 * @param threshold      optional lower-bound constraint; {@code null} means no
	 *                       constraint
	 * @param inclusive      if {@code true}, an extension equal to
	 *                       {@code threshold} is accepted
	 * @param aprioriPointer index into the frequent-prefix list for later
	 *                       resumption
	 * @return the smallest valid candidate, or {@code null} if none exists
	 */
	Candidate minimumExtensionForPrefix(CustomerData customer, CandidateKey prefix, CandidateKey threshold,
			boolean inclusive, int aprioriPointer) {

		if (singleItemMode) {
			return minimumExtensionForPrefixSingleItem(customer, prefix, threshold, inclusive, aprioriPointer);
		}

		final int prefixLength = prefix.items.length;

		if (prefixLength == 0 || customer.N <= prefixLength) {
			return null;
		}

		if (threshold != null && threshold.items.length != prefixLength + 1) {
			return null;
		}

		boolean[] prefixEndPositions = findAllPrefixMatchEnds(customer, prefix);
		if (!pmw.matched) {
			return null;
		}

		final int baseTransactionNumber = prefix.txCum[prefixLength - 1];
		final int txSame = baseTransactionNumber;
		final int txNext = baseTransactionNumber + 1;

		// FIX: record the last item of the prefix so the same-transaction branch
		// can enforce canonical ascending itemset order (only accept items strictly
		// greater than lastPrefixItem as same-transaction extensions).
		final int lastPrefixItem = prefix.items[prefixLength - 1];

		// Encode the threshold as a comparable long to avoid nested comparisons
		final boolean useThreshold = (threshold != null);
		final long thresholdKey;
		if (useThreshold) {
			int last = threshold.items.length - 1;
			thresholdKey = packKey(threshold.items[last], threshold.txCum[last]);
		} else {
			thresholdKey = 0L;
		}

		boolean found = false;
		long bestKey = 0L;

		boolean hasPrefixEndInEarlierTransaction = false;
		int segCount = customer.segmentCount();

		for (int seg = 0; seg < segCount; seg++) {

			int start = customer.segmentStart(seg);
			int end = customer.segmentEndExclusive(seg);

			boolean hasPrefixEndBeforePositionInSameTransaction = false;
			boolean hasPrefixEndInThisTransaction = false;

			for (int p = start; p < end; p++) {

				int item = customer.itemAt(p);

				// FIX: check same-transaction extension candidate only when the item
				// is strictly greater than the prefix's last item, preserving
				// canonical ascending order within the itemset and preventing
				// duplicate patterns with reversed item order.
				if (hasPrefixEndBeforePositionInSameTransaction && item > lastPrefixItem) {
					long key = packKey(item, txSame);
					boolean ok = !useThreshold || key > thresholdKey || (inclusive && key == thresholdKey);
					if (ok && (!found || key < bestKey)) {
						found = true;
						bestKey = key;
					}
				}

				// Check cross-transaction extension candidate. No ordering constraint
				// applies: cross-transaction order is meaningful and "O then x" vs
				// "x then O" in different transactions are genuinely distinct patterns.
				if (hasPrefixEndInEarlierTransaction) {
					long key = packKey(item, txNext);
					boolean ok = !useThreshold || key > thresholdKey || (inclusive && key == thresholdKey);
					if (ok && (!found || key < bestKey)) {
						found = true;
						bestKey = key;
					}
				}

				if (prefixEndPositions[p]) {
					hasPrefixEndBeforePositionInSameTransaction = true;
					hasPrefixEndInThisTransaction = true;
				}
			}

			if (hasPrefixEndInThisTransaction) {
				hasPrefixEndInEarlierTransaction = true;
			}
		}

		if (!found) {
			return null;
		}

		// Decode the best extension and build the result arrays
		int bestItem = (int) (bestKey >>> 32);
		int bestTxCum = (int) bestKey;

		int[] items = new int[prefixLength + 1];
		int[] txCum = new int[prefixLength + 1];

		System.arraycopy(prefix.items, 0, items, 0, prefixLength);
		System.arraycopy(prefix.txCum, 0, txCum, 0, prefixLength);

		items[prefixLength] = bestItem;
		txCum[prefixLength] = bestTxCum;

		return new Candidate(customer, items, txCum, aprioriPointer);
	}

	/**
	 * Fast-path variant of {@link #minimumExtensionForPrefix} used when the source
	 * database consists of sequences of single items. Same-transaction extensions
	 * are structurally impossible in that case, so the extension's transaction
	 * number is always {@code baseTransactionNumber + 1}, and the scan reduces to
	 * finding the smallest item occurring anywhere after the first position at
	 * which the prefix match ends, iterated directly over positions rather than
	 * over transaction segments.
	 *
	 * @param customer       the customer sequence to search
	 * @param prefix         the prefix to extend
	 * @param threshold      optional lower-bound constraint; {@code null} means no
	 *                       constraint
	 * @param inclusive      if {@code true}, an extension equal to
	 *                       {@code threshold} is accepted
	 * @param aprioriPointer index into the frequent-prefix list for later
	 *                       resumption
	 * @return the smallest valid candidate, or {@code null} if none exists
	 */
	private Candidate minimumExtensionForPrefixSingleItem(CustomerData customer, CandidateKey prefix,
			CandidateKey threshold, boolean inclusive, int aprioriPointer) {

		final int prefixLength = prefix.items.length;

		if (prefixLength == 0 || customer.N <= prefixLength) {
			return null;
		}

		if (threshold != null && threshold.items.length != prefixLength + 1) {
			return null;
		}

		boolean[] prefixEndPositions = findAllPrefixMatchEndsSingleItem(customer, prefix);
		if (!pmw.matched) {
			return null;
		}

		final int baseTransactionNumber = prefix.txCum[prefixLength - 1];
		final int txNext = baseTransactionNumber + 1;

		final boolean useThreshold = (threshold != null);
		final long thresholdKey;
		if (useThreshold) {
			int last = threshold.items.length - 1;
			thresholdKey = packKey(threshold.items[last], threshold.txCum[last]);
		} else {
			thresholdKey = 0L;
		}

		boolean found = false;
		long bestKey = 0L;

		final int n = customer.N;
		boolean seenPrefixEndBefore = false;

		for (int p = 0; p < n; p++) {

			if (seenPrefixEndBefore) {
				int item = customer.itemAt(p);
				long key = packKey(item, txNext);
				boolean ok = !useThreshold || key > thresholdKey || (inclusive && key == thresholdKey);
				if (ok && (!found || key < bestKey)) {
					found = true;
					bestKey = key;
				}
			}

			if (prefixEndPositions[p]) {
				seenPrefixEndBefore = true;
			}
		}

		if (!found) {
			return null;
		}

		int bestItem = (int) (bestKey >>> 32);
		int bestTxCum = (int) bestKey;

		int[] items = new int[prefixLength + 1];
		int[] txCum = new int[prefixLength + 1];

		System.arraycopy(prefix.items, 0, items, 0, prefixLength);
		System.arraycopy(prefix.txCum, 0, txCum, 0, prefixLength);

		items[prefixLength] = bestItem;
		txCum[prefixLength] = bestTxCum;

		return new Candidate(customer, items, txCum, aprioriPointer);
	}

	/**
	 * Computes a boolean array indicating every position in {@code customer} where
	 * a complete match of {@code prefix} ends, using a dynamic-programming pass
	 * over the prefix items.
	 *
	 * @param customer the customer sequence to search
	 * @param prefix   the prefix whose end positions are needed
	 * @return boolean array of length {@code customer.N}; {@code true} at positions
	 *         where the prefix ends
	 */
	private boolean[] findAllPrefixMatchEnds(CustomerData customer, CandidateKey prefix) {

		int prefixLength = prefix.items.length;
		int n = customer.N;

		pmw.ensureCapacity(n);

		boolean[] previous = pmw.previous;
		boolean[] current = pmw.current;

		// Mark positions matching the first prefix item
		boolean anyTrue = false;
		final int firstWanted = prefix.items[0];
		for (int i = 0; i < n; i++) {
			boolean match = (customer.itemAt(i) == firstWanted);
			previous[i] = match;
			anyTrue |= match;
		}

		if (!anyTrue) {
			pmw.matched = false;
			return previous;
		}

		int segCount = customer.segmentCount();

		// Extend matches item by item through the prefix
		for (int i = 1; i < prefixLength; i++) {

			int wantedItem = prefix.items[i];
			boolean sameTransaction = prefix.txCum[i] == prefix.txCum[i - 1];

			Arrays.fill(current, 0, n, false);
			anyTrue = false;

			if (sameTransaction) {

				// Extend within the same transaction segment
				for (int seg = 0; seg < segCount; seg++) {

					int start = customer.segmentStart(seg);
					int end = customer.segmentEndExclusive(seg);

					boolean hasPreviousBeforePosition = false;

					for (int p = start; p < end; p++) {

						if (hasPreviousBeforePosition && customer.itemAt(p) == wantedItem) {
							current[p] = true;
							anyTrue = true;
						}

						if (previous[p]) {
							hasPreviousBeforePosition = true;
						}
					}
				}

			} else {

				// Extend across transaction boundaries
				boolean hasPreviousInEarlierTransaction = false;

				for (int seg = 0; seg < segCount; seg++) {

					int start = customer.segmentStart(seg);
					int end = customer.segmentEndExclusive(seg);

					boolean previousInThisTransaction = false;

					if (hasPreviousInEarlierTransaction) {
						for (int p = start; p < end; p++) {
							if (customer.itemAt(p) == wantedItem) {
								current[p] = true;
								anyTrue = true;
							}
							if (previous[p]) {
								previousInThisTransaction = true;
							}
						}
					} else {
						// Just track whether there is a previous-match in this transaction
						for (int p = start; p < end; p++) {
							if (previous[p]) {
								previousInThisTransaction = true;
							}
						}
					}

					if (previousInThisTransaction) {
						hasPreviousInEarlierTransaction = true;
					}
				}
			}

			// Swap buffers for the next prefix item
			boolean[] tmp = previous;
			previous = current;
			current = tmp;

			pmw.previous = previous;
			pmw.current = current;

			if (!anyTrue) {
				pmw.matched = false;
				return previous;
			}
		}

		pmw.matched = true;
		return previous;
	}

	/**
	 * Fast-path variant of {@link #findAllPrefixMatchEnds} used when the source
	 * database consists of sequences of single items. Since every transaction
	 * segment has exactly one position, the "same transaction" extension branch is
	 * unreachable, and "extend across transaction boundaries" degenerates into a
	 * plain running-OR scan over positions: {@code current[p]} is set whenever some
	 * earlier position matched and {@code customer.itemAt(p)} equals the wanted
	 * item. This avoids computing/using transaction-segment boundaries at all.
	 *
	 * @param customer the customer sequence to search
	 * @param prefix   the prefix whose end positions are needed
	 * @return boolean array of length {@code customer.N}; {@code true} at positions
	 *         where the prefix ends
	 */
	private boolean[] findAllPrefixMatchEndsSingleItem(CustomerData customer, CandidateKey prefix) {

		int prefixLength = prefix.items.length;
		int n = customer.N;

		pmw.ensureCapacity(n);

		boolean[] previous = pmw.previous;
		boolean[] current = pmw.current;

		// Mark positions matching the first prefix item
		boolean anyTrue = false;
		final int firstWanted = prefix.items[0];
		for (int i = 0; i < n; i++) {
			boolean match = (customer.itemAt(i) == firstWanted);
			previous[i] = match;
			anyTrue |= match;
		}

		if (!anyTrue) {
			pmw.matched = false;
			return previous;
		}

		// Extend matches item by item through the prefix; every step is a
		// cross-transaction extension since single-item transactions can never
		// support a same-transaction match.
		for (int i = 1; i < prefixLength; i++) {

			int wantedItem = prefix.items[i];

			Arrays.fill(current, 0, n, false);
			anyTrue = false;

			boolean seenPreviousBefore = false;
			for (int p = 0; p < n; p++) {

				if (seenPreviousBefore && customer.itemAt(p) == wantedItem) {
					current[p] = true;
					anyTrue = true;
				}

				if (previous[p]) {
					seenPreviousBefore = true;
				}
			}

			// Swap buffers for the next prefix item
			boolean[] tmp = previous;
			previous = current;
			current = tmp;

			pmw.previous = previous;
			pmw.current = current;

			if (!anyTrue) {
				pmw.matched = false;
				return previous;
			}
		}

		pmw.matched = true;
		return previous;
	}

	/**
	 * Reusable workspace that holds the two boolean arrays used in
	 * {@link #findAllPrefixMatchEnds} to avoid per-call allocations.
	 */
	private static final class PrefixMatchWorkspace {

		/** Array representing match-end positions for the previous prefix item. */
		boolean[] previous = new boolean[0];

		/** Array representing match-end positions for the current prefix item. */
		boolean[] current = new boolean[0];

		/**
		 * Set to {@code true} if the last call to findAllPrefixMatchEnds found at least
		 * one match.
		 */
		boolean matched = false;

		/**
		 * Ensures both boolean arrays are at least {@code n} elements long,
		 * reallocating if necessary.
		 *
		 * @param n required minimum capacity
		 */
		void ensureCapacity(int n) {
			if (previous.length < n) {
				previous = new boolean[n];
				current = new boolean[n];
			}
		}
	}

	/**
	 * An order-statistic treap (randomised BST) that stores candidates keyed by
	 * their {@link CandidateKey}, supports O(log n) rank queries, and groups
	 * candidates with identical keys into buckets.
	 */
	private static final class ActiveSortedDatabase {

		/**
		 * Lightweight xorshift PRNG used only to generate treap priorities. Avoids the
		 * CAS-based atomic seed updates that {@link java.util.Random} performs on every
		 * call (unnecessary overhead here since this structure is used
		 * single-threaded).
		 */
		private static final class XorShiftRandom {

			/** Current PRNG state. */
			private long seed;

			/**
			 * Creates a new XorShiftRandom with the given seed (non-zero is enforced).
			 *
			 * @param seed initial seed value
			 */
			XorShiftRandom(long seed) {
				this.seed = (seed == 0) ? 0x9E3779B97F4A7C15L : seed;
			}

			/**
			 * Returns the next pseudo-random int from the xorshift sequence.
			 *
			 * @return next pseudo-random int
			 */
			int nextInt() {
				long x = seed;
				x ^= (x << 13);
				x ^= (x >>> 7);
				x ^= (x << 17);
				seed = x;
				return (int) x;
			}
		}

		/**
		 * A single treap node holding a key, a bucket of candidates, and subtree
		 * metadata.
		 */
		private static final class Node {

			/** The key stored in this node. */
			final CandidateKey key;

			/** All candidates whose key equals this node's key. */
			final ArrayList<Candidate> bucket;

			/** Heap priority used to maintain treap balance. */
			final int priority;

			/**
			 * Total number of candidates in this subtree (including this node's bucket).
			 */
			int subtreeCount;

			/** Left child. */
			Node left;

			/** Right child. */
			Node right;

			/**
			 * Creates a new Node with the given key, initial candidate, and priority.
			 *
			 * @param key      the key for this node
			 * @param first    the first candidate in the bucket
			 * @param priority random heap priority
			 */
			Node(CandidateKey key, Candidate first, int priority) {
				this.key = key;
				// Use small initial capacity since most buckets are small
				this.bucket = new ArrayList<Candidate>(2);
				this.bucket.add(first);
				this.priority = priority;
				this.subtreeCount = 1;
			}

			/**
			 * Recomputes {@code subtreeCount} from children and this node's bucket size.
			 */
			void recompute() {
				subtreeCount = bucket.size() + (left == null ? 0 : left.subtreeCount)
						+ (right == null ? 0 : right.subtreeCount);
			}
		}

		/**
		 * Mutable holder used to return a removed bucket from recursive tree
		 * operations.
		 */
		private static final class BucketHolder {
			/** The bucket extracted during a remove operation. */
			ArrayList<Candidate> bucket;
		}

		/** Mutable holder used to return a node from split operations. */
		private static final class NodeHolder {
			/** The node returned from a split operation. */
			Node value;
		}

		/** PRNG for generating node priorities. */
		private final XorShiftRandom random = new XorShiftRandom(0x5EED1234L);

		/** Reusable holder to avoid per-call allocation in removeKey. */
		private final BucketHolder removeHolder = new BucketHolder();

		/** Reusable holder to avoid per-call allocation in splitLessThan. */
		private final NodeHolder splitGreaterEqHolder = new NodeHolder();

		/** Root of the treap, or {@code null} if empty. */
		private Node root;

		/** Total number of candidates currently stored across all nodes. */
		private int totalCount = 0;

		/**
		 * Returns the total number of candidates stored.
		 *
		 * @return total candidate count
		 */
		int size() {
			return totalCount;
		}

		/**
		 * Inserts {@code candidate} into the database, keyed by its items and txCum.
		 *
		 * @param candidate the candidate to insert
		 */
		void add(Candidate candidate) {
			CandidateKey key = new CandidateKey(candidate.items, candidate.txCum);
			root = insert(root, key, candidate);
			totalCount++;
		}

		/**
		 * Recursively inserts a candidate into the treap rooted at {@code node},
		 * performing rotations to maintain the heap invariant.
		 *
		 * @param node      current treap node (may be {@code null})
		 * @param key       key of the candidate being inserted
		 * @param candidate the candidate to insert
		 * @return the new root of this subtree
		 */
		private Node insert(Node node, CandidateKey key, Candidate candidate) {

			if (node == null) {
				return new Node(key, candidate, random.nextInt());
			}

			int comparison = CandidateKey.compare(key, node.key);

			if (comparison == 0) {
				// Existing key: add to the bucket
				node.bucket.add(candidate);
				node.recompute();
				return node;
			}

			if (comparison < 0) {
				node.left = insert(node.left, key, candidate);
				// Rotate right if heap property violated
				if (node.left.priority > node.priority) {
					node = rotateRight(node);
				}
			} else {
				node.right = insert(node.right, key, candidate);
				// Rotate left if heap property violated
				if (node.right.priority > node.priority) {
					node = rotateLeft(node);
				}
			}

			node.recompute();
			return node;
		}

		/**
		 * Performs a right rotation around {@code node}.
		 *
		 * @param node the node to rotate around
		 * @return the new subtree root
		 */
		private static Node rotateRight(Node node) {
			Node newRoot = node.left;
			node.left = newRoot.right;
			newRoot.right = node;
			node.recompute();
			newRoot.recompute();
			return newRoot;
		}

		/**
		 * Performs a left rotation around {@code node}.
		 *
		 * @param node the node to rotate around
		 * @return the new subtree root
		 */
		private static Node rotateLeft(Node node) {
			Node newRoot = node.right;
			node.right = newRoot.left;
			newRoot.left = node;
			node.recompute();
			newRoot.recompute();
			return newRoot;
		}

		/**
		 * Returns the smallest key in the database, or {@code null} if empty.
		 *
		 * @return the minimum CandidateKey, or {@code null}
		 */
		CandidateKey firstKey() {
			Node node = root;
			if (node == null) {
				return null;
			}
			// Walk to the leftmost node
			while (node.left != null) {
				node = node.left;
			}
			return node.key;
		}

		/**
		 * Returns the key at the given one-based rank in sorted order, or {@code null}
		 * if {@code rank} exceeds the total count.
		 *
		 * @param rank one-based rank of the desired key
		 * @return the CandidateKey at that rank, or {@code null}
		 */
		CandidateKey keyAtRank(int rank) {

			Node node = root;
			int remaining = rank;

			while (node != null) {

				int leftCount = node.left == null ? 0 : node.left.subtreeCount;

				if (remaining <= leftCount) {
					node = node.left;
					continue;
				}

				remaining -= leftCount;

				int bucketSize = node.bucket.size();
				if (remaining <= bucketSize) {
					return node.key;
				}

				// Rank is in the right subtree
				remaining -= bucketSize;
				node = node.right;
			}

			return null;
		}

		/**
		 * Removes all candidates associated with {@code key} and returns them.
		 *
		 * @param key the key whose bucket should be removed
		 * @return the bucket of candidates that were stored under {@code key}
		 */
		List<Candidate> removeKey(CandidateKey key) {

			removeHolder.bucket = null;
			root = removeKey(root, key, removeHolder);

			if (removeHolder.bucket == null) {
				return java.util.Collections.emptyList();
			}

			ArrayList<Candidate> bucket = removeHolder.bucket;
			totalCount -= bucket.size();

			return bucket;
		}

		/**
		 * Recursively removes the node with the given key from the subtree rooted at
		 * {@code node}, storing the removed bucket in {@code holder}.
		 *
		 * @param node   current subtree root
		 * @param key    the key to remove
		 * @param holder receives the removed bucket
		 * @return the new subtree root after removal
		 */
		private Node removeKey(Node node, CandidateKey key, BucketHolder holder) {

			if (node == null) {
				return null;
			}

			int comparison = CandidateKey.compare(key, node.key);

			if (comparison < 0) {
				node.left = removeKey(node.left, key, holder);
				node.recompute();
				return node;
			}
			if (comparison > 0) {
				node.right = removeKey(node.right, key, holder);
				node.recompute();
				return node;
			}

			// Found the node: capture its bucket and merge children
			holder.bucket = node.bucket;
			return merge(node.left, node.right);
		}

		/**
		 * Merges two treap subtrees into one, preserving the heap invariant.
		 *
		 * @param left  the left subtree (all keys less than those in {@code right})
		 * @param right the right subtree
		 * @return the root of the merged treap
		 */
		private static Node merge(Node left, Node right) {

			if (left == null)
				return right;
			if (right == null)
				return left;

			if (left.priority > right.priority) {
				left.right = merge(left.right, right);
				left.recompute();
				return left;
			}

			right.left = merge(left, right.left);
			right.recompute();
			return right;
		}

		/**
		 * Removes all candidates whose key is strictly less than {@code threshold} and
		 * returns them as a flat list.
		 *
		 * @param threshold the exclusive lower bound; candidates below this key are
		 *                  removed
		 * @return all candidates that were removed
		 */
		List<Candidate> removeAllLessThan(CandidateKey threshold) {

			// Split the treap into "less" and "greater-or-equal" parts
			splitGreaterEqHolder.value = null;
			Node less = splitLessThan(root, threshold, splitGreaterEqHolder);
			root = splitGreaterEqHolder.value;

			final int removedCount = (less == null) ? 0 : less.subtreeCount;
			final ArrayList<Candidate> removed = new ArrayList<Candidate>(removedCount);

			collectAll(less, removed);

			totalCount -= removedCount;
			return removed;
		}

		/**
		 * Splits the subtree rooted at {@code node} into two treaps: one with keys
		 * strictly less than {@code threshold} (returned) and one with keys &ge;
		 * {@code threshold} (stored in {@code greaterEqHolder.value}).
		 *
		 * @param node            current subtree root
		 * @param threshold       the split key
		 * @param greaterEqHolder receives the &ge; subtree
		 * @return the root of the &lt; subtree
		 */
		private Node splitLessThan(Node node, CandidateKey threshold, NodeHolder greaterEqHolder) {

			if (node == null) {
				greaterEqHolder.value = null;
				return null;
			}

			if (CandidateKey.compare(node.key, threshold) < 0) {
				// Node belongs to the "less" part; split its right subtree
				Node lessRight = splitLessThan(node.right, threshold, greaterEqHolder);
				node.right = lessRight;
				node.recompute();
				return node;
			}

			// Node belongs to the "greater-or-equal" part; split its left subtree
			Node less = splitLessThan(node.left, threshold, greaterEqHolder);
			node.left = greaterEqHolder.value;
			node.recompute();
			greaterEqHolder.value = node;
			return less;
		}

		/**
		 * Performs an in-order traversal of the subtree rooted at {@code node},
		 * appending all candidates to {@code out}.
		 *
		 * @param node current subtree root (may be {@code null})
		 * @param out  list to append candidates to
		 */
		private void collectAll(Node node, List<Candidate> out) {

			if (node == null) {
				return;
			}

			collectAll(node.left, out);
			out.addAll(node.bucket);
			collectAll(node.right, out);
		}
	}
}