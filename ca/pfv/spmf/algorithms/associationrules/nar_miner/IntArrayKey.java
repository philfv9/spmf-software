package ca.pfv.spmf.algorithms.associationrules.nar_miner;

import java.util.Arrays;
/* This file is copyright (c) Philippe Fournier-Viger
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
 * Immutable wrapper around a sorted {@code int[]} that provides correct
 * hashCode() and equals() implementations based on array content,
 * making it suitable as a HashMap key without String conversion overhead.
 * 
 * @author Philippe Fournier-Viger
 */
final class IntArrayKey {

    /** The underlying sorted item array. Never modified after construction. */
    final int[] items;

    /** Pre-computed hash code for fast repeated lookups. */
    private final int hash;

    /**
     * Construct a key from a sorted item array.
     * The array is stored by reference; the caller must not modify it.
     *
     * @param items sorted int array (not copied for performance)
     */
    IntArrayKey(int[] items) {
        this.items = items;
        this.hash  = Arrays.hashCode(items);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return hash;
    }

    /**
     * {@inheritDoc}
     * Two keys are equal iff their underlying arrays have identical contents.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof IntArrayKey)) return false;
        IntArrayKey other = (IntArrayKey) obj;
        if (this.hash != other.hash) return false;
        return Arrays.equals(this.items, other.items);
    }
}