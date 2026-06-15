package ca.pfv.spmf.algorithms.frequentpatterns.hmine_fast;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

import java.util.Arrays;

/**
 * Helper for the HMine algorithm that performs suffix compression (transaction
 * merging). Given a set of suffix pointers from a projected database, it
 * identifies and merges identical suffixes so that each unique suffix is stored
 * once with a combined weight.
 *
 * Two suffixes are considered identical if they contain the same sequence of
 * relevant items (items marked as frequent at the current recursion level).
 * Irrelevant items are skipped during both hashing and comparison, which avoids
 * copying data to remove them.
 *
 * Internally, suffixes are deduplicated using an epoch-based hash table, to
 * avoid zeroing the entire table on each reset. The epoch counter is
 * incremented and only entries whose stamp matches the current epoch are
 * considered valid to have o(1) reset of the hash table.
 *
 * @author Philippe Fournier-Viger
 * @see AlgoHMine_FAST
 */
class SuffixCompressor {

	// ---- Suffix data stored in parallel arrays ----

	/** Start positions of unique suffixes in the cell array */
	private int[] starts;

	/** End positions of unique suffixes in the cell array */
	private int[] ends;

	/** Filtered hash values of unique suffixes */
	private long[] hashes;

	/** Accumulated transaction weight for each unique suffix */
	private int[] weights;

	/** Number of unique suffixes currently stored */
	private int count;

	/** Current capacity of the suffix arrays */
	private int capacity;

	// ---- Epoch-based hash table fields ----

	/** Hash table buckets: bucket -> first index into suffix arrays */
	private int[] hashTable;

	/** Epoch stamp for each bucket (valid only when == currentEpoch) */
	private int[] hashEpochs;

	/** Collision chain: next suffix index in same bucket (-1 = end) */
	private int[] hashNext;

	/** Bitmask for fast modulo: bucket = hash & hashMask */
	private final int hashMask;

	/** Current epoch; incremented on each reset */
	private int currentEpoch;

	/** Reference to the immutable cell array (set once after loading) */
	private int[] cells;

	/**
	 * Constructor
	 *
	 * @param initialCapacity the estimated number of unique suffixes per round
	 */
	SuffixCompressor(int initialCapacity) {
		// ensure a reasonable minimum capacity
		this.capacity = Math.max(64, initialCapacity);
		this.hashes = new long[capacity];
		this.starts = new int[capacity];
		this.ends = new int[capacity];
		this.weights = new int[capacity];
		this.hashNext = new int[capacity];

		// hash table size must be a power of two for bitmask modulo
		int tableSize = Integer.highestOneBit(Math.max(16, capacity * 2 - 1)) << 1;
		this.hashMask = tableSize - 1;
		this.hashTable = new int[tableSize];
		this.hashEpochs = new int[tableSize];
		this.currentEpoch = 0;
		this.count = 0;
	}

	/**
	 * Set the reference to the immutable cell array. Must be called once after
	 * loading the transaction data and before any calls to AddOrMerge()
	 *
	 * @param cells the cell array containing all transaction items
	 */
	void setCells(int[] cells) {
		this.cells = cells;
	}

	/**
	 * Prepare for a new round of suffix collection. All previously stored suffixes
	 * become invalid. This operation is O(1) using this epoch-based hash table.
	 */
	void reset() {
		// advance epoch to invalidate all existing buckets
		currentEpoch++;
		if (currentEpoch == Integer.MAX_VALUE) {
			// overflow guard (practically unreachable)
			Arrays.fill(hashEpochs, 0);
			currentEpoch = 1;
		}
		count = 0;
	}

	/**
	 * Add a suffix to the collection.
	 *
	 * @param suffixStart start position of the suffix in cells[]
	 * @param suffixEnd   end position of the suffix in cells[]
	 * @param weight      transaction weight to add
	 * @param isRelevant  marks which items are considered during comparison
	 */
	void addOrMerge(int suffixStart, int suffixEnd, int weight, boolean[] isRelevant) {
		// compute hash over relevant items only
		long hash = computeFilteredHash(suffixStart, suffixEnd, isRelevant);

		// If an identical suffix (same sequence of relevant items) already exists, its
		// weight is increased. Otherwise, a new
		// entry is created.
		int existing = findMatch(hash, suffixStart, suffixEnd, isRelevant);
		if (existing >= 0) {
			// identical suffix found — merge by adding weight
			weights[existing] += weight;
		} else {
			// new unique suffix — insert into arrays and hash table
			insertNew(hash, suffixStart, suffixEnd, weight);
		}
	}

	/**
	 * Get the number of unique suffixes currently stored.
	 * 
	 * @return the number of unique suffixes stored
	 */
	int count() {
		return count;
	}

	/**
	 * Get the start position of a suffix in the cell array.
	 * 
	 * @param index the index of the suffix
	 * @return the start position of the i-th suffix in cells[]
	 */
	int start(int index) {
		return starts[index];
	}

	/**
	 * Get the end position of a suffix in the cell array.
	 * 
	 * @param index the index of the suffix
	 * @return the end position of the i-th suffix in cells[]
	 */
	int end(int index) {
		return ends[index];
	}

	/**
	 * Get the accumulated weight of a suffix.
	 * 
	 * @param index the index of the suffix
	 * @return the accumulated weight of the i-th suffix
	 */
	int weight(int index) {
		return weights[index];
	}

	/**
	 * Compute a hash (base 31, right-to-left) of a suffix, including
	 * only items where isRelevant[item] is true.
	 * 
	 * @param start      the start position of the suffix
	 * @param end        the end position of the suffix
	 * @param isRelevant marks which items are considered in the hash
	 * @return the computed hash value
	 */
	private long computeFilteredHash(int start, int end, boolean[] isRelevant) {
		long hashValue = 0;
		// scan right-to-left so that position matters in the hash
		for (int position = end; position >= start; position--) {
			int item = cells[position];
			if (isRelevant[item]) {
				hashValue = item + 31L * hashValue;
			}
		}
		return hashValue;
	}

	/**
	 * Compare two suffixes for filtered equality, skipping items not marked as
	 * relevant. Used for hash collision resolution.
	 * 
	 * @param firstStart  the start position of the first suffix
	 * @param firstEnd    the end position of the first suffix
	 * @param secondStart the start position of the second suffix
	 * @param secondEnd   the end position of the second suffix
	 * @param isRelevant  marks which items are considered in the comparison
	 * @return true if both suffixes have the same relevant items in the same order
	 */
	private boolean filteredEquals(int firstStart, int firstEnd,
			int secondStart, int secondEnd, boolean[] isRelevant) {
		int posFirst = firstStart;
		int posSecond = secondStart;
		while (true) {
			// advance past irrelevant items in both suffixes
			while (posFirst <= firstEnd && !isRelevant[cells[posFirst]])
				posFirst++;
			while (posSecond <= secondEnd && !isRelevant[cells[posSecond]])
				posSecond++;

			// check if both suffixes are exhausted
			if (posFirst > firstEnd && posSecond > secondEnd)
				return true;
			// check if only one is exhausted (different length)
			if (posFirst > firstEnd || posSecond > secondEnd)
				return false;
			// compare the current relevant items
			if (cells[posFirst] != cells[posSecond])
				return false;

			posFirst++;
			posSecond++;
		}
	}

	/**
	 * Search the hash table for a suffix matching the given range.
	 *
	 * @param hash       the precomputed hash of the suffix
	 * @param start      the start position of the suffix
	 * @param end        the end position of the suffix
	 * @param isRelevant marks which items are considered in the comparison
	 * @return index into the suffix arrays, or -1 if not found
	 */
	private int findMatch(long hash, int start, int end, boolean[] isRelevant) {
		// compute bucket from hash
		int bucket = (int) (hash & hashMask);

		// bucket is empty if its epoch doesn't match
		if (hashEpochs[bucket] != currentEpoch) {
			return -1;
		}

		// walk the collision chain looking for a match
		int candidate = hashTable[bucket];
		while (candidate != -1) {
			// check hash first (cheap), then full filtered comparison
			if (hashes[candidate] == hash
					&& filteredEquals(starts[candidate], ends[candidate], start, end, isRelevant)) {
				return candidate;
			}
			candidate = hashNext[candidate];
		}
		return -1;
	}

	/**
	 * Insert a new unique suffix into the arrays and hash table.
	 * 
	 * @param hash   the precomputed hash of the suffix
	 * @param start  the start position of the suffix
	 * @param end    the end position of the suffix
	 * @param weight the transaction weight for this suffix
	 */
	private void insertNew(long hash, int start, int end, int weight) {
		// grow suffix arrays if capacity is exceeded
		if (count >= capacity) {
			int newSize = capacity * 2;
			hashes = Arrays.copyOf(hashes, newSize);
			starts = Arrays.copyOf(starts, newSize);
			ends = Arrays.copyOf(ends, newSize);
			weights = Arrays.copyOf(weights, newSize);
			hashNext = Arrays.copyOf(hashNext, newSize);
			capacity = newSize;
		}

		// store the new suffix data
		int newIndex = count++;
		hashes[newIndex] = hash;
		starts[newIndex] = start;
		ends[newIndex] = end;
		weights[newIndex] = weight;

		// insert into the hash table bucket
		int bucket = (int) (hash & hashMask);
		if (hashEpochs[bucket] != currentEpoch) {
			// bucket was empty (stale epoch) — start a new chain
			hashEpochs[bucket] = currentEpoch;
			hashTable[bucket] = newIndex;
			hashNext[newIndex] = -1;
		} else {
			// bucket already active — prepend to existing chain
			hashNext[newIndex] = hashTable[bucket];
			hashTable[bucket] = newIndex;
		}
	}

	/**
	 * Release all internal arrays. Called once when the algorithm finishes to help
	 * garbage collection.
	 */
	void dispose() {
		hashes = null;
		starts = null;
		ends = null;
		weights = null;
		hashTable = null;
		hashEpochs = null;
		hashNext = null;
		cells = null;
	}
}