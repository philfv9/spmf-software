package ca.pfv.spmf.algorithms.frequentpatterns.dbvminer;

import java.util.Arrays;
/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
*/

/**
 * Dynamic Bit-Vector: compressed representation of transaction IDs.
 * 
 * Instead of storing a full bit vector for all transactions, DBV stores: - pos:
 * position of first non-zero long (leading zeros are trimmed) - bitVector: only
 * the non-zero portion (trailing zeros also trimmed) - support: precomputed
 * count of 1-bits (number of transactions)
 * 
 * OPTIMIZATION: Uses long (64-bit) instead of byte (8-bit) for: - 8x fewer
 * iterations in intersection/subset operations - Better CPU utilization (64-bit
 * native operations) - Hardware-accelerated bit counting (POPCNT instruction)
 * 
 * @author Philippe Fournier-Viger
 * @see AlgoDBVMiner
 */
public class DBV {
	/** Long position offset (leading zeros skipped) */
	protected final int pos;

	/** Compressed bit vector using 64-bit longs for efficiency */
	protected final long[] bitVector;

	/** Number of transactions (count of 1-bits) */
	protected final int support;

	/**
	 * Constructor
	 * 
	 * @param pos       position of first bit set to 1
	 * @param bitVector the bitvector
	 * @param support   the support
	 */
	public DBV(int pos, long[] bitVector, int support) {
		this.pos = pos;
		this.bitVector = bitVector;
		this.support = support;
	}

	/**
	 * Get the length of the compressed bit vector in longs.
	 * 
	 * @return the length
	 */
	public int getLength() {
		return bitVector.length;
	}

	/**
	 * Get a specific long from the bit vector.
	 * 
	 * @param index Index of the long to retrieve
	 * @return The long value at the specified index
	 */
	public long getLong(int index) {
		return bitVector[index];
	}

	/**
	 * Check if this DBV represents an empty set.
	 * 
	 * @return true if it is an empty set
	 */
	public boolean isEmpty() {
		return support == 0;
	}

	/**
	 * Check if this DBV is a subset of another: DBV1 ⊆ DBV2.
	 * 
	 * This means every transaction in this DBV is also in the other. Used to
	 * determine subsumption: if DBV(X) ⊆ DBV(Y), then Y appears in all transactions
	 * containing X, so Y should be in X's closure.
	 * 
	 * @return true if a subset, and otherwise, false
	 */
	public boolean isSubsetOf(DBV other) {
		if (this.isEmpty())
			return true;
		if (other.isEmpty())
			return false;

		// Quick rejection: can't be subset if we have more transactions
		if (this.support > other.support)
			return false;

		// Check range overlap using long positions
		int thisEnd = this.pos + this.bitVector.length;
		int otherEnd = other.pos + other.bitVector.length;
		if (this.pos < other.pos || thisEnd > otherEnd) {
			return false;
		}

		// Check each long: (this & other) must equal this
		// Processing 64 bits at a time for efficiency
		int offset = this.pos - other.pos;
		for (int i = 0; i < this.bitVector.length; i++) {
			long thisLong = this.bitVector[i];
			long otherLong = other.bitVector[offset + i];
			if ((thisLong & otherLong) != thisLong) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Generate a unique key for this DBV for use in hash-based lookups. Uses long
	 * values for the key representation.
	 * 
	 * @return string representation
	 */
	public String toKey() {
		if (bitVector.length == 0) {
			return "0:empty";
		}
		StringBuilder sb = new StringBuilder();
		sb.append(pos).append(':').append(support).append(':');
		for (int i = 0; i < bitVector.length; i++) {
			sb.append(bitVector[i]);
			if (i < bitVector.length - 1)
				sb.append(',');
		}
		return sb.toString();
	}

	/**
	 * Check if two DBVs are identical (same transaction set). Used to detect when
	 * two itemsets have the same closure.
	 * 
	 * Uses Arrays.equals for efficient long array comparison.
	 * 
	 * @return true if equal. Otherwise, false
	 */
	public boolean equalDBV(DBV other) {
		if (this.support != other.support)
			return false;
		if (this.pos != other.pos)
			return false;
		if (this.bitVector.length != other.bitVector.length)
			return false;
		return Arrays.equals(this.bitVector, other.bitVector);
	}
}