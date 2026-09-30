package ca.pfv.spmf.algorithms.sequentialpatterns.disc;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
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
 * Holds the flattened item/transaction data for a single customer sequence,
 * with lazy-initialized transaction segment boundaries.
 * @author Chen Hui
 */
final class CustomerData {

    /** Shared empty array used to avoid repeated allocations for empty instances. */
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    /** Flat array of item values for this customer. */
    final int[] flatItem;

    /** Flat array of transaction identifiers parallel to {@code flatItem}. */
    final int[] flatTx;

    /** Starting offset into the backing arrays for this view. */
    final int offset;

    /** Number of elements visible in this view. */
    final int N;

    /** Start positions (relative to offset) of each transaction segment. */
    private int[] segStart;

    /** Exclusive end positions (relative to offset) of each transaction segment. */
    private int[] segEndExclusive;

    /**
     * Creates a CustomerData over the full extent of the provided arrays.
     *
     * @param flatItem array of item values
     * @param flatTx   array of transaction identifiers, parallel to {@code flatItem}
     * @throws IllegalArgumentException if the arrays have different lengths
     */
    CustomerData(int[] flatItem, int[] flatTx) {
        if (flatItem.length != flatTx.length) {
            throw new IllegalArgumentException("flatItem and flatTx must have the same length.");
        }
        if (flatItem.length == 0) {
            this.flatItem = EMPTY_INT_ARRAY;
            this.flatTx = EMPTY_INT_ARRAY;
            this.offset = 0;
            this.N = 0;
            return;
        }
        this.flatItem = flatItem;
        this.flatTx = flatTx;
        this.offset = 0;
        this.N = flatItem.length;
    }

    /**
     * Creates a sub-view into existing backing arrays without copying data.
     *
     * @param flatItem backing item array
     * @param flatTx   backing transaction array
     * @param offset   start index in the backing arrays
     * @param length   number of elements in this view
     */
    private CustomerData(int[] flatItem, int[] flatTx, int offset, int length) {
        this.flatItem = flatItem;
        this.flatTx = flatTx;
        this.offset = offset;
        this.N = length;
    }

    /**
     * Returns the item at position {@code i} within this view.
     *
     * @param i logical index within this view
     * @return item value at position {@code i}
     */
    int itemAt(int i) {
        return flatItem[offset + i];
    }

    /**
     * Returns a sub-view of this customer starting at {@code startPos}.
     * If {@code startPos} is zero or negative, returns {@code this}.
     *
     * @param startPos first logical position to include in the new view
     * @return a CustomerData view beginning at {@code startPos}
     */
    CustomerData slice(int startPos) {
        if (startPos <= 0) {
            return this;
        }
        if (startPos >= N) {
            // Return an empty view still pointing to the same backing arrays
            return new CustomerData(flatItem, flatTx, offset + N, 0);
        }
        return new CustomerData(flatItem, flatTx, offset + startPos, N - startPos);
    }

    /**
     * Lazily computes the transaction segment boundaries in a single pass
     * and caches them for subsequent calls.
     */
    private void ensureSegments() {

        if (segStart != null) {
            return;
        }

        if (N == 0) {
            segStart = EMPTY_INT_ARRAY;
            segEndExclusive = EMPTY_INT_ARRAY;
            return;
        }

        final int[] tx = flatTx;
        final int base = offset;

        // Start with a small capacity and grow as needed
        int cap = Math.min(16, N);
        int[] starts = new int[cap];
        int[] ends = new int[cap];

        int seg = 0;
        starts[0] = 0;

        int prev = tx[base];
        for (int i = 1; i < N; i++) {
            int cur = tx[base + i];
            if (cur != prev) {
                // Close the current segment and open a new one
                ends[seg] = i;
                seg++;

                if (seg == cap) {
                    cap = Math.min(N, cap << 1);
                    starts = Arrays.copyOf(starts, cap);
                    ends = Arrays.copyOf(ends, cap);
                }

                starts[seg] = i;
                prev = cur;
            }
        }

        // Close the final segment
        ends[seg] = N;
        int segCount = seg + 1;

        segStart = (segCount == starts.length) ? starts : Arrays.copyOf(starts, segCount);
        segEndExclusive = (segCount == ends.length) ? ends : Arrays.copyOf(ends, segCount);
    }

    /**
     * Returns the number of distinct transaction segments in this view.
     *
     * @return number of segments
     */
    int segmentCount() {
        ensureSegments();
        return segStart.length;
    }

    /**
     * Returns the start position (relative to this view) of the segment at {@code segIndex}.
     *
     * @param segIndex zero-based segment index
     * @return inclusive start position of the segment
     */
    int segmentStart(int segIndex) {
        return segStart[segIndex];
    }

    /**
     * Returns the exclusive end position (relative to this view) of the segment at {@code segIndex}.
     *
     * @param segIndex zero-based segment index
     * @return exclusive end position of the segment
     */
    int segmentEndExclusive(int segIndex) {
        return segEndExclusive[segIndex];
    }
}

/**
 * Represents a candidate sequence attached to a specific customer, used during
 * the DISC mining process to track the current best extension for that customer.
 */
final class Candidate {

    /** The customer sequence this candidate is derived from. */
    final CustomerData customer;

    /** Item values forming the candidate sequence. */
    final int[] items;

    /** Cumulative transaction numbers for each item in the candidate sequence. */
    final int[] txCum;

    /** Index into the frequent-prefix list used to resume apriori search. */
    final int aprioriPointer;

    /**
     * Constructs a Candidate with the given customer data, item sequence, transaction
     * numbers, and apriori pointer.
     *
     * @param customer       the customer this candidate belongs to
     * @param items          item values of the candidate sequence
     * @param txCum          cumulative transaction numbers parallel to {@code items}
     * @param aprioriPointer index into the frequent-prefix list for resuming search
     */
    Candidate(CustomerData customer, int[] items, int[] txCum, int aprioriPointer) {
        this.customer = customer;
        this.items = items;
        this.txCum = txCum;
        this.aprioriPointer = aprioriPointer;
    }
}

/**
 * An immutable key identifying a candidate sequence by its item array and
 * cumulative transaction array, with caching support for prefix computation.
 */
final class CandidateKey {

    /** Shared empty array used for zero-length keys. */
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    /** Item values forming this key's sequence. */
    final int[] items;

    /** Cumulative transaction numbers for each item, parallel to {@code items}. */
    final int[] txCum;

    /** Cached prefix key (all items except the last) to avoid repeated allocations. */
    private CandidateKey cachedPrefix;

    /**
     * Constructs a CandidateKey with the given item and transaction arrays.
     *
     * @param items  item values of the sequence
     * @param txCum  cumulative transaction numbers parallel to {@code items}
     */
    CandidateKey(int[] items, int[] txCum) {
        this.items = items;
        this.txCum = txCum;
    }

    /**
     * Compares two sequences lexicographically by (item, txCum) pairs, then by length.
     *
     * @param itemsA item array of the first sequence
     * @param txA    transaction array of the first sequence
     * @param itemsB item array of the second sequence
     * @param txB    transaction array of the second sequence
     * @return negative, zero, or positive as A is less than, equal to, or greater than B
     */
    static int compareArrays(int[] itemsA, int[] txA, int[] itemsB, int[] txB) {

        final int lenA = itemsA.length;
        final int lenB = itemsB.length;
        final int limit = Math.min(lenA, lenB);

        for (int i = 0; i < limit; i++) {
            // Compare items at position i
            int a = itemsA[i];
            int b = itemsB[i];
            if (a < b) return -1;
            if (a > b) return 1;

            // Items equal; compare transaction numbers at position i
            int ta = txA[i];
            int tb = txB[i];
            if (ta < tb) return -1;
            if (ta > tb) return 1;
        }

        return Integer.compare(lenA, lenB);
    }

    /**
     * Compares two CandidateKey instances lexicographically.
     *
     * @param first  the first key
     * @param second the second key
     * @return negative, zero, or positive as {@code first} is less than, equal to, or greater than {@code second}
     */
    static int compare(CandidateKey first, CandidateKey second) {
        return compareArrays(first.items, first.txCum, second.items, second.txCum);
    }

    /** Comparator that delegates to {@link #compare(CandidateKey, CandidateKey)}. */
    static final Comparator<CandidateKey> COMPARATOR = CandidateKey::compare;

    /**
     * Returns a CandidateKey representing all but the last element of {@code sequence},
     * caching the result on the sequence for reuse.
     *
     * @param sequence the key whose prefix is needed
     * @return the prefix key (empty if {@code sequence} has length &le; 1)
     */
    static CandidateKey prefixOf(CandidateKey sequence) {
        CandidateKey cached = sequence.cachedPrefix;
        if (cached != null) {
            return cached;
        }

        int prefixLength = sequence.items.length - 1;
        if (prefixLength <= 0) {
            // Sequence has one element; prefix is empty
            CandidateKey computed = new CandidateKey(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            sequence.cachedPrefix = computed;
            return computed;
        }

        // Copy all but the last element
        CandidateKey computed = new CandidateKey(
                Arrays.copyOf(sequence.items, prefixLength),
                Arrays.copyOf(sequence.txCum, prefixLength));

        sequence.cachedPrefix = computed;
        return computed;
    }
}

/**
 * Holds the output of one DISC mining round: the frequent sequences found at
 * the current length and the candidate keys to be explored at the next length.
 */
final class DiscRoundResult {

    /** Frequent sequences discovered during this round. */
    final List<CandidateKey> frequentThisLevel;

    /** Candidate keys that should seed the next mining round. */
    final List<CandidateKey> frequentNextLevel;

    /**
     * Constructs a DiscRoundResult with the given frequent sequences for this
     * level and the next level.
     *
     * @param frequentThisLevel frequent sequences found at the current length
     * @param frequentNextLevel candidate keys for the next length
     */
    DiscRoundResult(List<CandidateKey> frequentThisLevel, List<CandidateKey> frequentNextLevel) {
        this.frequentThisLevel = frequentThisLevel;
        this.frequentNextLevel = frequentNextLevel;
    }
}

/**
 * Callback interface that the DISC engine uses to output discovered patterns
 * and to poll memory usage.
 */
interface DiscHost {

    /**
     * Writes a discovered frequent pattern to the output.
     *
     * @param items   item values of the pattern
     * @param txCum   cumulative transaction numbers parallel to {@code items}
     * @param support number of customers supporting the pattern
     * @throws IOException if writing fails
     */
    void writePattern(int[] items, int[] txCum, int support) throws IOException;

    /** Checks current memory usage and triggers cleanup if necessary. */
    void checkMemory();
}

/**
 * Callback interface for iterating over long keys in a collection.
 */
interface LongKeyProcedure {

    /**
     * Processes a single long key.
     *
     * @param key the key to process
     */
    void apply(long key);
}

/**
 * Callback interface for iterating over (long key, int value) map entries.
 */
interface LongIntProcedure {

    /**
     * Processes a single map entry.
     *
     * @param key   the entry's key
     * @param value the entry's value
     */
    void apply(long key, int value);
}

/**
 * Callback interface for iterating over int keys in a collection.
 */
interface IntKeyProcedure {

    /**
     * Processes a single int key.
     *
     * @param key the key to process
     */
    void apply(int key);
}

/**
 * Utility class providing fast integer and long hash-mixing functions
 * and a power-of-two rounding helper.
 */
final class HashUtil {

    /** Prevents instantiation of this utility class. */
    private HashUtil() {
    }

    /**
     * Applies a bijective 32-bit finalisation mix to reduce hash collisions.
     *
     * @param x the value to mix
     * @return the mixed value
     */
    static int mix32(int x) {
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }

    /**
     * Folds a 64-bit value into a 32-bit hash using a strong finalisation mix.
     *
     * @param z the 64-bit value to mix
     * @return a 32-bit hash derived from {@code z}
     */
    static int mix64to32(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return (int) z;
    }

    /**
     * Returns the smallest power of two that is &ge; {@code x}, capped at 2^30.
     *
     * @param x the input value
     * @return the next power of two &ge; {@code x}
     */
    static int nextPowerOfTwo(int x) {
        if (x <= 1) return 1;
        final int MAX_POW2 = 1 << 30;
        if (x >= MAX_POW2) return MAX_POW2;
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }
}

/**
 * An open-addressing hash set for primitive {@code int} keys using linear probing.
 * Zero is treated as a special sentinel for empty slots.
 */
final class IntHashSet {

    /** Maximum load factor before the table is resized. */
    private static final float LOAD = 0.7f;

    /** Maximum table capacity (must be a power of two). */
    private static final int MAX_CAP = 1 << 30;

    /** Backing array; slot value of 0 means empty. */
    private int[] keys;

    /** True if the key {@code 0} has been added (since 0 doubles as the empty sentinel). */
    private boolean hasZero;

    /** Number of distinct keys currently stored. */
    private int size;

    /** Bit mask used in place of modulo for power-of-two table sizes. */
    private int mask;

    /** Size threshold at which the table is rehashed. */
    private int resizeAt;

    /**
     * Creates an IntHashSet with enough initial capacity for {@code expected} elements.
     *
     * @param expected anticipated number of elements
     */
    IntHashSet(int expected) {
        int cap = HashUtil.nextPowerOfTwo(Math.max(16, (int) (expected / LOAD) + 1));
        keys = new int[cap];
        mask = cap - 1;
        resizeAt = (int) (cap * LOAD);
    }

    /** Creates an IntHashSet with a default initial capacity. */
    IntHashSet() {
        this(16);
    }

    /**
     * Returns the number of distinct keys in this set.
     *
     * @return current size
     */
    int size() {
        return size;
    }

    /** Removes all keys from this set without releasing the backing array. */
    void clear() {
        if (size == 0) {
            return;
        }
        Arrays.fill(keys, 0);
        hasZero = false;
        size = 0;
    }

    /**
     * Returns {@code true} if this set contains {@code key}.
     *
     * @param key the key to look up
     * @return {@code true} if present
     */
    boolean contains(int key) {
        if (key == 0) return hasZero;

        final int[] ks = keys;
        final int m = mask;

        // Probe until an empty slot or a match is found
        int idx = HashUtil.mix32(key) & m;
        while (true) {
            int k = ks[idx];
            if (k == 0) return false;
            if (k == key) return true;
            idx = (idx + 1) & m;
        }
    }

    /**
     * Adds {@code key} to this set if it is not already present.
     *
     * @param key the key to add
     */
    void add(int key) {
        if (key == 0) {
            if (!hasZero) {
                hasZero = true;
                size++;
            }
            return;
        }

        if (size >= resizeAt && keys.length < MAX_CAP) {
            rehash(keys.length << 1);
        }

        final int[] ks = keys;
        final int m = mask;

        // Insert at the first empty slot
        int idx = HashUtil.mix32(key) & m;
        while (true) {
            int k = ks[idx];
            if (k == 0) {
                ks[idx] = key;
                size++;
                return;
            }
            if (k == key) return;
            idx = (idx + 1) & m;
        }
    }

    /**
     * Invokes {@code proc} once for every key in this set, in unspecified order.
     *
     * @param proc the procedure to call for each key
     */
    void forEachKey(IntKeyProcedure proc) {
        if (hasZero) {
            proc.apply(0);
        }
        final int[] ks = keys;
        final int len = ks.length;
        for (int i = 0; i < len; i++) {
            int k = ks[i];
            if (k != 0) {
                proc.apply(k);
            }
        }
    }

    /**
     * Rebuilds the backing table at {@code newCap} and reinserts all existing keys.
     *
     * @param newCap new table capacity (must be a power of two)
     */
    private void rehash(int newCap) {
        int[] oldKeys = keys;

        // Allocate new table and update metadata
        keys = new int[newCap];
        mask = newCap - 1;
        resizeAt = (int) (newCap * LOAD);

        final int[] nk = keys;
        final int m = mask;
        final int oldLen = oldKeys.length;

        // Reinsert each non-empty key into the new table
        for (int i = 0; i < oldLen; i++) {
            int k = oldKeys[i];
            if (k == 0) continue;
            int idx = HashUtil.mix32(k) & m;
            while (nk[idx] != 0) idx = (idx + 1) & m;
            nk[idx] = k;
        }
    }
}

/**
 * An open-addressing hash set for primitive {@code long} keys using linear probing.
 * Zero is treated as a special sentinel for empty slots.
 */
final class LongHashSet {

    /** Maximum load factor before the table is resized. */
    private static final float LOAD = 0.7f;

    /** Maximum table capacity (must be a power of two). */
    private static final int MAX_CAP = 1 << 30;

    /** Backing array; slot value of 0 means empty. */
    private long[] keys;

    /** True if the key {@code 0L} has been added. */
    private boolean hasZero;

    /** Number of distinct keys currently stored. */
    private int size;

    /** Bit mask used in place of modulo for power-of-two table sizes. */
    private int mask;

    /** Size threshold at which the table is rehashed. */
    private int resizeAt;

    /**
     * Creates a LongHashSet with enough initial capacity for {@code expected} elements.
     *
     * @param expected anticipated number of elements
     */
    LongHashSet(int expected) {
        int cap = HashUtil.nextPowerOfTwo(Math.max(16, (int) (expected / LOAD) + 1));
        keys = new long[cap];
        mask = cap - 1;
        resizeAt = (int) (cap * LOAD);
    }

    /** Creates a LongHashSet with a default initial capacity. */
    LongHashSet() {
        this(16);
    }

    /** Removes all keys from this set without releasing the backing array. */
    void clear() {
        if (size == 0) {
            return;
        }
        Arrays.fill(keys, 0L);
        hasZero = false;
        size = 0;
    }

    /**
     * Adds {@code key} to this set if it is not already present.
     *
     * @param key the key to add
     */
    void add(long key) {
        if (key == 0L) {
            if (!hasZero) {
                hasZero = true;
                size++;
            }
            return;
        }

        if (size >= resizeAt && keys.length < MAX_CAP) {
            rehash(keys.length << 1);
        }

        final long[] ks = keys;
        final int m = mask;

        // Insert at the first empty slot
        int idx = HashUtil.mix64to32(key) & m;
        while (true) {
            long k = ks[idx];
            if (k == 0L) {
                ks[idx] = key;
                size++;
                return;
            }
            if (k == key) return;
            idx = (idx + 1) & m;
        }
    }

    /**
     * Invokes {@code proc} once for every key in this set, in unspecified order.
     *
     * @param proc the procedure to call for each key
     */
    void forEachKey(LongKeyProcedure proc) {
        if (hasZero) proc.apply(0L);
        final long[] ks = keys;
        final int len = ks.length;
        for (int i = 0; i < len; i++) {
            long k = ks[i];
            if (k != 0L) {
                proc.apply(k);
            }
        }
    }

    /**
     * Rebuilds the backing table at {@code newCap} and reinserts all existing keys.
     *
     * @param newCap new table capacity (must be a power of two)
     */
    private void rehash(int newCap) {
        long[] oldKeys = keys;

        // Allocate new table and update metadata
        keys = new long[newCap];
        mask = newCap - 1;
        resizeAt = (int) (newCap * LOAD);

        final long[] nk = keys;
        final int m = mask;
        final int oldLen = oldKeys.length;

        // Reinsert each non-empty key into the new table
        for (int i = 0; i < oldLen; i++) {
            long k = oldKeys[i];
            if (k == 0L) continue;
            int idx = HashUtil.mix64to32(k) & m;
            while (nk[idx] != 0L) idx = (idx + 1) & m;
            nk[idx] = k;
        }
    }
}

/**
 * An open-addressing hash map from primitive {@code long} keys to {@code int} values,
 * using linear probing. Zero is treated as a special sentinel for empty slots.
 */
final class LongIntHashMap {

    /** Maximum load factor before the table is resized. */
    private static final float LOAD = 0.7f;

    /** Maximum table capacity (must be a power of two). */
    private static final int MAX_CAP = 1 << 30;

    /** Backing array of keys; slot value of 0 means empty. */
    private long[] keys;

    /** Backing array of values, parallel to {@code keys}. */
    private int[] values;

    /** True if key {@code 0L} is present in the map. */
    private boolean hasZero;

    /** Value associated with key {@code 0L}. */
    private int zeroValue;

    /** Number of distinct keys currently stored. */
    private int size;

    /** Bit mask used in place of modulo for power-of-two table sizes. */
    private int mask;

    /** Size threshold at which the table is rehashed. */
    private int resizeAt;

    /**
     * Creates a LongIntHashMap with enough initial capacity for {@code expected} entries.
     *
     * @param expected anticipated number of entries
     */
    LongIntHashMap(int expected) {
        int cap = HashUtil.nextPowerOfTwo(Math.max(16, (int) (expected / LOAD) + 1));
        keys = new long[cap];
        values = new int[cap];
        mask = cap - 1;
        resizeAt = (int) (cap * LOAD);
    }

    /**
     * Increments the value associated with {@code key} by one, inserting it with
     * value 1 if not already present.
     *
     * @param key the key to increment
     */
    void increment(long key) {
        addTo(key, 1);
    }

    /**
     * Adds {@code delta} to the value associated with {@code key}, inserting it
     * with value {@code delta} if not already present.
     *
     * @param key   the key to update
     * @param delta the amount to add
     */
    void addTo(long key, int delta) {
        if (key == 0L) {
            if (!hasZero) {
                hasZero = true;
                size++;
            }
            zeroValue += delta;
            return;
        }

        if (size >= resizeAt && keys.length < MAX_CAP) {
            rehash(keys.length << 1);
        }

        final long[] ks = keys;
        final int[] vs = values;
        final int m = mask;

        // Find existing entry or first empty slot
        int idx = HashUtil.mix64to32(key) & m;
        while (true) {
            long k = ks[idx];
            if (k == 0L) {
                ks[idx] = key;
                vs[idx] = delta;
                size++;
                return;
            }
            if (k == key) {
                vs[idx] += delta;
                return;
            }
            idx = (idx + 1) & m;
        }
    }

    /**
     * Invokes {@code proc} once for every (key, value) entry in this map, in unspecified order.
     *
     * @param proc the procedure to call for each entry
     */
    void forEachEntry(LongIntProcedure proc) {
        if (hasZero) proc.apply(0L, zeroValue);
        final long[] ks = keys;
        final int[] vs = values;
        final int len = ks.length;
        for (int i = 0; i < len; i++) {
            long k = ks[i];
            if (k != 0L) {
                proc.apply(k, vs[i]);
            }
        }
    }

    /**
     * Rebuilds the backing table at {@code newCap} and reinserts all existing entries.
     *
     * @param newCap new table capacity (must be a power of two)
     */
    private void rehash(int newCap) {
        long[] oldKeys = keys;
        int[] oldValues = values;

        // Allocate new tables and update metadata
        keys = new long[newCap];
        values = new int[newCap];
        mask = newCap - 1;
        resizeAt = (int) (newCap * LOAD);

        final long[] nk = keys;
        final int[] nv = values;
        final int m = mask;
        final int oldLen = oldKeys.length;

        // Reinsert each non-empty entry into the new table
        for (int i = 0; i < oldLen; i++) {
            long k = oldKeys[i];
            if (k == 0L) continue;
            int idx = HashUtil.mix64to32(k) & m;
            while (nk[idx] != 0L) idx = (idx + 1) & m;
            nk[idx] = k;
            nv[idx] = oldValues[i];
        }
    }
}