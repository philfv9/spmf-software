package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;

import java.util.Arrays;
import java.util.BitSet;

/***
 * This is an implementation of an Item-is-exist table used by the LAPIN-SPAM
 * algorithm.
 *
 * The LAPIN-SPAM algorithm was originally described in this paper:
 *
 * Zhenlu Yang and Masrau Kitsuregawa. LAPIN-SPAM: An improved algorithm for
 * mining sequential pattern In Proc. of Int'l Special Workshop on Databases For
 * Next Generation Researchers (SWOD'05) in conjunction with ICDE'05, pp. 8-11,
 * Tokyo, Japan, Apr. 2005.
 *
 * Copyright (c) 2008-2013 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information.
 */
public class Table {

	/** Position of each vector in this table, always inserted in descending order
	 * since the table is built by scanning each sequence backward **/
	int[] positions;

	/** Bitset of each vector, parallel to {@link #positions} **/
	BitSet[] bitsets;

	/** Number of valid entries currently stored in this table **/
	int size;

	/**
	 * Constructor
	 */
	public Table() {
		positions = new int[4];
		bitsets = new BitSet[4];
		size = 0;
	}

	/**
	 * Adds a new position vector to this table (must be added in descending order of position).
	 *
	 * @param position the position (an itemset index, or -1 for the sentinel row)
	 * @param bitset   the bitset of items appearing at or after this position
	 */
	void add(int position, BitSet bitset) {
		if (size == positions.length) {
			positions = Arrays.copyOf(positions, positions.length * 2);
			bitsets = Arrays.copyOf(bitsets, bitsets.length * 2);
		}
		positions[size] = position;
		bitsets[size] = bitset;
		size++;
	}

	/**
	 * Finds the bitset of the first vector whose position is strictly less than the given
	 * target, using binary search since positions are sorted in descending order. This
	 * replaces a linear scan and is what the I-step support estimation relies on.
	 *
	 * @param targetPosition the position to search below
	 * @return the matching bitset, or null if none is found
	 */
	BitSet findBitsetBefore(int targetPosition) {
		int lo = 0, hi = size;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (positions[mid] < targetPosition) {
				hi = mid;
			} else {
				lo = mid + 1;
			}
		}
		return (lo < size) ? bitsets[lo] : null;
	}

	/**
	 * Get a string representation of this table.
	 *
	 * @return a string
	 */
	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < size; i++) {
			buffer.append(" Position: ").append(positions[i]).append("  Bitset: ").append(bitsets[i])
					.append(System.lineSeparator());
		}
		return buffer.toString();
	}
}