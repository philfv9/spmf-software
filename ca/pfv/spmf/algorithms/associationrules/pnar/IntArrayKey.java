package ca.pfv.spmf.algorithms.associationrules.pnar;

import java.util.Arrays;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
*/
/**
 * An immutable wrapper around a sorted int[] that computes hashCode once
 * and implements equals by array content.
 * @author Philippe Fournier-Viger
 * @see AlgoPNAR
 */
final class IntArrayKey {

    /** The underlying itemset (must be sorted). */
    final int[] items;

    /** Precomputed hash code. */
    private final int hash;

    /**
     * Constructs an IntArrayKey from an existing sorted array.
     * The array must not be modified after this call.
     *
     * @param items a sorted int array; ownership is transferred to this key
     */
    IntArrayKey(int[] items) {
        this.items = items;
        this.hash  = Arrays.hashCode(items);
    }

    /**
     * Constructs an IntArrayKey from the first {@code len} elements of a
     * scratch array. A defensive copy is made so the caller may reuse the
     * scratch buffer.
     *
     * @param scratch the source array
     * @param len     number of elements to copy
     */
    IntArrayKey(int[] scratch, int len) {
        this.items = Arrays.copyOf(scratch, len);
        this.hash  = Arrays.hashCode(this.items);
    }

    @Override
    public int hashCode() { return hash; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntArrayKey)) return false;
        IntArrayKey other = (IntArrayKey) o;
        if (hash != other.hash) return false;
        return Arrays.equals(items, other.items);
    }
}