package ca.pfv.spmf.algorithms.frequentpatterns.sam;
/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger

This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/
/**
 * Transaction element used by the SAM algorithm.
 * <p>
 * Represents a transaction with a shared item array, a current offset
 * (logical start position), and a weight (occurrence count).
 * </p>
 *
 * @see AlgoSAM
 * @author Philippe Fournier-Viger
 */
final class TransactionSAM {

    /** Array of items (shared between copies). */
    private final int[] items;

    /** Current logical start position in the items array. */
    private int offset;

    /** Occurrence count (transaction weight). */
    private int weight;

    /**
     * Constructs a transaction with a given offset and weight.
     *
     * @param items  item array
     * @param offset starting offset
     * @param weight occurrence count
     */
    TransactionSAM(int[] items, int offset, int weight) {
        this.items = items;
        this.offset = offset;
        this.weight = weight;
    }

    /**
     * Creates a shallow copy sharing the same items array.
     *
     * @return copied transaction
     */
    TransactionSAM copy() {
        return new TransactionSAM(items, offset, weight);
    }

    /**
     * Returns the first remaining item.
     *
     * @return first item, or -1 if empty
     */
    int firstItem() {
        return isEmpty() ? -1 : items[offset];
    }

    /**
     * Removes the first remaining item by advancing the offset.
     */
    void removeFirst() {
        offset++;
    }

    /**
     * Checks whether no items remain.
     *
     * @return true if empty
     */
    boolean isEmpty() {
        return offset >= items.length;
    }

    /**
     * Increases the transaction weight.
     *
     * @param additional amount to add
     */
    void addWeight(int additional) {
        weight += additional;
    }

    /**
     * Returns the transaction weight.
     *
     * @return weight
     */
    int getWeight() {
        return weight;
    }

    /**
     * Gets the item at a logical index relative to the current offset.
     *
     * @param index logical index (0 = first remaining item)
     * @return item value, or -1 if out of bounds
     */
    int getItem(int index) {
        int pos = offset + index;
        return pos < items.length ? items[pos] : -1;
    }

    /**
     * Returns the number of remaining items.
     *
     * @return remaining item count
     */
    int size() {
        return items.length - offset;
    }
}
