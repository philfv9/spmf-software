package ca.pfv.spmf.algorithms.frequentpatterns.aprioriTID_fast;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
 *
 *  This file is part of the SPMF DATA MINING SOFTWARE
 *  (http://www.philippe-fournier-viger.com/spmf).
 *  SPMF is free software: you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation, either version 3 of the License, or (at your option) any later
 *  version.
 *  SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 *  A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with
 *  SPMF. If not, see http://www.gnu.org/licenses/.
 */

/**
 * Manages tidsets for AprioriTID-based algorithms using either a dense (long[]
 * bitset) or sparse (int[] list) representation. <br/>
 * <br/>
 * Dense representation is used when support is high (above a threshold), while
 * sparse representation is used when support is low. <br/>
 * <br/>
 * Intersections are optimized depending on the representation: bitset–bitset,
 * bitset–list, and list–list. <br/>
 * <br/>
 * A small object pool is used for dense tidsets to reduce memory overhead.
 * Sparse tidsets are not pooled because their size varies. <br/>
 * <br/>
 * This class is not thread-safe. Each algorithm instance must use its own
 * TidsetManager.
 *
 * @author Philippe Fournier-Viger
 */
class TidsetManager {

	/** Number of long words per dense tidset */
	private int wordCount;

	/**
	 * Density threshold: tidsets with support below this value use sparse (int[])
	 * representation. Equal to wordCount so that sparse representation is used when
	 * int[] would be smaller than long[].
	 */
	private int sparseThreshold;

	/** Pool of reusable long[wordCount] arrays for dense tidsets */
	private final ArrayList<long[]> pool = new ArrayList<long[]>();

	/**
	 * Temporary buffer for list intersection results. Sized to sparseThreshold to
	 * hold the maximum possible sparse result.
	 */
	private int[] tempListBuffer;

	/**
	 * Result of the last intersection: dense representation. Non-null only if the
	 * last result was dense.
	 */
	private long[] lastResultBitset;

	/**
	 * Result of the last intersection: sparse representation. Non-null only if the
	 * last result was sparse.
	 */
	private int[] lastResultList;

	/**
	 * Constructor
	 */
	public TidsetManager() {
	}

	/**
	 * Initialize (or re-initialize) for a dataset with the given number of
	 * transactions. Clears any previously pooled arrays and sets the density
	 * threshold.
	 *
	 * @param transactionCount the total number of transactions
	 */
	public void init(int transactionCount) {
		this.wordCount = (transactionCount + 63) >>> 6;
		this.sparseThreshold = wordCount;
		this.tempListBuffer = new int[Math.max(sparseThreshold, 64)];
		pool.clear();
		lastResultBitset = null;
		lastResultList = null;
	}

	/**
	 * @return the number of long words per dense tidset
	 */
	public int getWordCount() {
		return wordCount;
	}

	/**
	 * @return the density threshold: support values below this use sparse
	 *         representation
	 */
	public int getSparseThreshold() {
		return sparseThreshold;
	}

	/**
	 * Determine whether an itemset with the given support should use dense (bitset)
	 * representation.
	 *
	 * @param support the support of the itemset
	 * @return true if bitset representation should be used
	 */
	public boolean shouldUseBitset(int support) {
		return support >= sparseThreshold;
	}

	/**
	 * Borrow a long[wordCount] array from the pool, or allocate a new one if the
	 * pool is empty. Contents are undefined.
	 *
	 * @return a long array of length wordCount
	 */
	public long[] borrowTidset() {
		int size = pool.size();
		if (size > 0) {
			return pool.remove(size - 1);
		}
		return new long[wordCount];
	}

	/**
	 * Return a long[] array to the pool for reuse.
	 *
	 * @param arr the array to return (must be of length wordCount)
	 */
	public void returnTidset(long[] arr) {
		pool.add(arr);
	}

	/**
	 * Recycle tidset arrays from a completed level into the pool. Only dense
	 * (long[]) arrays are recycled; sparse (int[]) arrays are left for garbage
	 * collection since they vary in length.
	 *
	 * @param level the list of itemsets whose tidsets may be recycled
	 */
	public void recycleTidsets(List<ItemsetWithTID> level) {
		for (int i = 0, sz = level.size(); i < sz; i++) {
			ItemsetWithTID item = level.get(i);
			if (item.tidWords != null) {
				pool.add(item.tidWords);
			}
		}
	}

	/**
	 * Clear all pooled arrays, releasing memory.
	 */
	public void clearPool() {
		pool.clear();
	}

	/**
	 * Convert a java.util.BitSet to a fixed-length long[] array suitable for dense
	 * tidset representation.
	 *
	 * @param bitset the BitSet to convert
	 * @return a long[] of length wordCount representing the same bits
	 */
	public long[] bitsetToLongArray(BitSet bitset) {
		long[] src = bitset.toLongArray();
		if (src.length == wordCount) {
			return src;
		}
		long[] result = new long[wordCount];
		System.arraycopy(src, 0, result, 0, Math.min(src.length, wordCount));
		return result;
	}

	/**
	 * Convert a java.util.BitSet to a sorted int[] array suitable for sparse tidset
	 * representation.
	 *
	 * @param bitset      the BitSet to convert
	 * @param cardinality the known cardinality (number of set bits)
	 * @return a sorted int[] containing all set bit positions
	 */
	public int[] bitsetToSortedIntArray(BitSet bitset, int cardinality) {
		int[] result = new int[cardinality];
		int idx = 0;
		for (int bit = bitset.nextSetBit(0); bit >= 0; bit = bitset.nextSetBit(bit + 1)) {
			result[idx++] = bit;
		}
		return result;
	}

	/**
	 * Intersect two itemsets' tidsets, automatically dispatching to the optimal
	 * method based on their representations. Stores the result internally; if the
	 * result meets minSupport, call {claimResultAsItemset(int[], int)} to retrieve
	 * it. If the result does not meet minSupport, any borrowed resources are
	 * automatically returned.
	 *
	 * @param itemsetA   first itemset
	 * @param itemsetB   second itemset
	 * @param minSupport absolute minimum support threshold
	 * @return cardinality of intersection, or -1 if below minSupport
	 */
	public int intersect(ItemsetWithTID itemsetA, ItemsetWithTID itemsetB, int minSupport) {
		lastResultBitset = null;
		lastResultList = null;

		boolean aDense = itemsetA.isDense();
		boolean bDense = itemsetB.isDense();

		if (aDense && bDense) {
			return intersectBB(itemsetA.tidWords, itemsetA.support, itemsetB.tidWords, itemsetB.support, minSupport);
		} else if (aDense) {
			// a is bitset, b is list
			return intersectBL(itemsetA.tidWords, itemsetB.tidList, minSupport);
		} else if (bDense) {
			// b is bitset, a is list
			return intersectBL(itemsetB.tidWords, itemsetA.tidList, minSupport);
		} else {
			// both lists
			return intersectLL(itemsetA.tidList, itemsetB.tidList, minSupport);
		}
	}

	/**
	 * Claim the result of the last successful intersection as an ItemsetWithTID.
	 * Must be called only after intersect() returned a value greate or equal to
	 * minSupport.
	 *
	 * @param items       the items array for the new itemset
	 * @param cardinality the cardinality returned by intersect()
	 * @return a new ItemsetWithTID with the appropriate representation
	 */
	public ItemsetWithTID claimResultAsItemset(int[] items, int cardinality) {
		if (lastResultBitset != null) {
			long[] bits = lastResultBitset;
			lastResultBitset = null;
			return new ItemsetWithTID(items, bits, cardinality);
		} else {
			int[] list = lastResultList;
			lastResultList = null;
			return new ItemsetWithTID(items, list);
		}
	}

	/**
	 * Intersect two dense tidsets with quarter-point and half-point early
	 * termination and 4-wide loop unrolling. If the result support is below
	 * sparseThreshold, converts to sparse representation.
	 *
	 * @param a          first dense tidset
	 * @param supA       support of a
	 * @param b          second dense tidset
	 * @param supB       support of b
	 * @param minSupport absolute minimum support threshold
	 * @return cardinality of intersection, or -1 if below minSupport
	 */
	private int intersectBB(long[] a, int supA, long[] b, int supB, int minSupport) {
		if (supA < minSupport || supB < minSupport) {
			return -1;
		}

		int wc = this.wordCount;
		long[] result = borrowTidset();

		// For very small word counts, skip early-termination
		// overhead
		if (wc <= 4) {
			int count = 0;
			for (int w = 0; w < wc; w++) {
				long word = a[w] & b[w];
				result[w] = word;
				count += Long.bitCount(word);
			}
			if (count < minSupport) {
				returnTidset(result);
				return -1;
			}
			storeResultBB(result, count);
			return count;
		}

		// Identify smaller parent for tight upper-bound checks
		boolean aIsSmaller = supA <= supB;
		long[] smaller = aIsSmaller ? a : b;
		int smallerSup = aIsSmaller ? supA : supB;

		int quarter = wc >>> 2;
		int half = wc >>> 1;
		int count = 0;

		// First quarter
		int smallerPartial = 0;
		for (int w = 0; w < quarter; w++) {
			long word = a[w] & b[w];
			result[w] = word;
			count += Long.bitCount(word);
			smallerPartial += Long.bitCount(smaller[w]);
		}

		// Quarter-point early-termination check
		if (count + (smallerSup - smallerPartial) < minSupport) {
			returnTidset(result);
			return -1;
		}

		// Second quarter (up to half)
		for (int w = quarter; w < half; w++) {
			long word = a[w] & b[w];
			result[w] = word;
			count += Long.bitCount(word);
			smallerPartial += Long.bitCount(smaller[w]);
		}

		// Half-point early-termination check
		if (count + (smallerSup - smallerPartial) < minSupport) {
			returnTidset(result);
			return -1;
		}

		// Second half: 4-wide unrolled loop
		int w = half;
		int limit = half + ((wc - half) & ~3);
		for (; w < limit; w += 4) {
			long w0 = a[w] & b[w];
			long w1 = a[w + 1] & b[w + 1];
			long w2 = a[w + 2] & b[w + 2];
			long w3 = a[w + 3] & b[w + 3];
			result[w] = w0;
			result[w + 1] = w1;
			result[w + 2] = w2;
			result[w + 3] = w3;
			count += Long.bitCount(w0) + Long.bitCount(w1) + Long.bitCount(w2) + Long.bitCount(w3);
		}
		// Remaining words
		for (; w < wc; w++) {
			long word = a[w] & b[w];
			result[w] = word;
			count += Long.bitCount(word);
		}

		if (count < minSupport) {
			returnTidset(result);
			return -1;
		}

		storeResultBB(result, count);
		return count;
	}

	/**
	 * Store bitset-bitset intersection result, converting to sparse if below
	 * threshold.
	 *
	 * @param result the intersection bitset
	 * @param count  the cardinality
	 */
	private void storeResultBB(long[] result, int count) {
		if (count < sparseThreshold) {
			// Convert to sparse: extract set bit positions
			int[] list = new int[count];
			int idx = 0;
			for (int w = 0; w < wordCount && idx < count; w++) {
				long word = result[w];
				while (word != 0) {
					int bit = Long.numberOfTrailingZeros(word);
					list[idx++] = (w << 6) + bit;
					word &= word - 1;
				}
			}
			returnTidset(result);
			lastResultList = list;
		} else {
			lastResultBitset = result;
		}
	}

	/**
	 * Intersect a dense tidset with a sparse tidset by probing each TID from the
	 * list in the bitset. Result is always sparse. O(listLen) complexity.
	 *
	 * @param bits       the dense tidset (long[] of length wordCount)
	 * @param list       the sparse tidset (sorted int[])
	 * @param minSupport absolute minimum support threshold
	 * @return cardinality of intersection, or -1 if below minSupport
	 */
	private int intersectBL(long[] bits, int[] list, int minSupport) {
		int listLen = list.length;
		if (listLen < minSupport) {
			return -1;
		}

		// Ensure temp buffer is large enough
		if (tempListBuffer.length < listLen) {
			tempListBuffer = new int[listLen];
		}

		int count = 0;
		int need = minSupport;
		int remaining = listLen;

		for (int i = 0; i < listLen; i++) {
			int tid = list[i];
			// Check if tid is set in the bitset
			if ((bits[tid >>> 6] & (1L << tid)) != 0) {
				tempListBuffer[count++] = tid;
			}
			remaining--;

			// Early termination: can't reach minSupport
			if (count + remaining < need) {
				return -1;
			}
		}

		if (count < minSupport) {
			return -1;
		}

		// Copy to correctly sized array
		int[] resultList = new int[count];
		System.arraycopy(tempListBuffer, 0, resultList, 0, count);
		lastResultList = resultList;
		return count;
	}

	/**
	 * Intersect two sparse tidsets using sorted merge. Result is always sparse.
	 * O(|A|+|B|) complexity.
	 *
	 * @param listA      first sorted int[] tidset
	 * @param listB      second sorted int[] tidset
	 * @param minSupport absolute minimum support threshold
	 * @return cardinality of intersection, or -1 if below minSupport
	 */
	private int intersectLL(int[] listA, int[] listB, int minSupport) {
		int sA = listA.length;
		int sB = listB.length;

		// O(1) pre-check
		if (sA < minSupport || sB < minSupport) {
			return -1;
		}

		int maxPossible = Math.min(sA, sB);
		if (maxPossible < minSupport) {
			return -1;
		}

		// Ensure temp buffer is large enough
		if (tempListBuffer.length < maxPossible) {
			tempListBuffer = new int[maxPossible];
		}

		int count = 0;
		int i = 0;
		int j = 0;

		while (i < sA && j < sB) {
			int va = listA[i];
			int vb = listB[j];

			if (va < vb) {
				i++;
				// Early termination: remaining in A too few
				if (count + (sA - i) < minSupport) {
					return -1;
				}
			} else if (va > vb) {
				j++;
				// Early termination: remaining in B too few
				if (count + (sB - j) < minSupport) {
					return -1;
				}
			} else {
				tempListBuffer[count++] = va;
				i++;
				j++;
			}
		}

		if (count < minSupport) {
			return -1;
		}

		// Copy to correctly sized array
		int[] resultList = new int[count];
		System.arraycopy(tempListBuffer, 0, resultList, 0, count);
		lastResultList = resultList;
		return count;
	}
}