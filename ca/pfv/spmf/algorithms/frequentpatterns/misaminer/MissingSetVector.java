package ca.pfv.spmf.algorithms.frequentpatterns.misaminer;

import java.util.Arrays;

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
* Do not remove license and copyright information.
*/
/**
 * A fixed-size bit vector representing a "missing-set" assigned to an itemset
 * by the MISAMiner algorithm.
 * A bit is set to 1 if the corresponding transaction does not contain the
 * itemset.
 * The class is specifically optimized for this algorithm.
 *
 * @author Philippe Fournier-Viger
 */
final class MissingSetVector {

	/** Number of valid bits (transactions in D') represented by this vector */
	private final int nBits;

	/** Number of 64-bit machine words used to store nBits bits (B in Eq. 16) */
	private final int numWords;

	/** The underlying bit storage, one bit per transaction index */
	private final long[] words;

	/**
	 * Create a new missing-set vector of the given bit length, initialized to all
	 * zeros (i.e. representing an empty missing-set, as used for missing(&empty;).
	 *
	 * @param nBits the number of transactions represented (n' in the paper)
	 */
	MissingSetVector(int nBits) {
		this.nBits = nBits;
		this.numWords = (nBits + 63) >>> 6;
		this.words = new long[numWords];
	}

	/**
	 * Private constructor used internally to wrap an already-computed word array
	 * (used by {@link #copy()}).
	 *
	 * @param words the word array to wrap (not copied by this constructor)
	 * @param nBits the number of valid bits
	 */
	private MissingSetVector(long[] words, int nBits) {
		this.words = words;
		this.nBits = nBits;
		this.numWords = words.length;
	}

	/**
	 * Set all bits to 1 (used to initialize missing(x) as "all transactions miss
	 * x".
	 */
	void setAllOnes() {
		Arrays.fill(words, -1L);
		maskTrailingBits();
	}

	/**
	 * Mask out any stray 1-bits beyond {@code nBits} in the last word, so that
	 * {@link #cardinality()} and other operations are exact even when {@code nBits}
	 * is not a multiple of 64.
	 */
	private void maskTrailingBits() {
		int rem = nBits & 63;
		if (rem != 0 && numWords > 0) {
			long mask = (1L << rem) - 1L;
			words[numWords - 1] &= mask;
		}
	}

	/**
	 * Set the bit at the given transaction index to 1 (the transaction is missing
	 * from the itemset).
	 *
	 * @param bitIndex the transaction index
	 */
	void set(int bitIndex) {
		words[bitIndex >>> 6] |= (1L << (bitIndex & 63));
	}

	/**
	 * Clear the bit at the given transaction index (the transaction contains the
	 * item, so it is not part of its missing-set).
	 *
	 * @param bitIndex the transaction index
	 */
	void clear(int bitIndex) {
		words[bitIndex >>> 6] &= ~(1L << (bitIndex & 63));
	}

	/**
	 * Compute |missing(X)|, the number of set bits in this vector..
	 *
	 * @return the cardinality of this missing-set
	 */
	int cardinality() {
		int c = 0;
		for (int i = 0; i < numWords; i++) {
			c += Long.bitCount(words[i]);
		}
		return c;
	}

	/**
	 * Test whether this vector is a subset of another missing set vector, exiting
	 * immediately as soon as a violating word is found.
	 *
	 * @param other the other missing-set vector
	 * @return true if it is a subset. Otherwise, false.
	 */
	boolean isSubsetOf(MissingSetVector other) {
		for (int i = 0; i < numWords; i++) {
			if ((words[i] & ~other.words[i]) != 0L) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Test whether the cardinality of the union with antoher missing vector other
	 * cap (the frequency test of Eq. 9 / Eq. 13, |M'| &le; cap), without ever
	 * allocating or materializing the union. If the test succeeds, the exact union
	 * cardinality is returned (so callers that need it, do not have to recompute
	 * the union a second time). If the running popcount exceeds {@code cap} before
	 * all words have been scanned, the method returns immediately, which is the
	 * common case described in the paper.
	 *
	 * @param other the other missing-set vector
	 * @param cap   the maximum allowed cardinality (Eq. 1)
	 * @return the exact cardinality of the union if it is &le; cap, otherwise a
	 *         negative value
	 */
	int unionCardinalityCapped(MissingSetVector other, int cap) {
		int c = 0;
		for (int i = 0; i < numWords; i++) {
			c += Long.bitCount(words[i] | other.words[i]);
			if (c > cap) {
				return -1;
			}
		}
		return c;
	}

	/**
	 * Mutate this vector in place to become the union of itself and another missing
	 * set vector (used only on the accepted path, when a branch actually needs to
	 * be expanded and its new missing-set materialized for the recursive call).
	 *
	 * @param other the other missing-set vector to union into this one
	 */
	void orInPlace(MissingSetVector other) {
		for (int i = 0; i < numWords; i++) {
			words[i] |= other.words[i];
		}
	}

	/**
	 * Overwrite the contents of this vector with the bits of another missing set,
	 * without allocating a new word array. This allows creating a pool of reusable
	 * missing set vector instances.
	 *
	 * @param other the vector whose bits should be copied into this one (must have
	 *              the same length as this vector)
	 */
	void copyFrom(MissingSetVector other) {
		System.arraycopy(other.words, 0, this.words, 0, numWords);
	}

	/**
	 * Create an independent copy of this vector.
	 *
	 * @return a new MissingSetVector with the same bits set
	 */
	MissingSetVector copy() {
		return new MissingSetVector(words.clone(), nBits);
	}

	/**
	 * @return B, the number of 64-bit machine words used by this vector (Eq. 16
	 *         with w = 64)
	 */
	int sizeInWords() {
		return numWords;
	}
}