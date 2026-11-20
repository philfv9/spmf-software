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
*
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*
* Do not remove copyright and license information
*/
package ca.pfv.spmf.algorithms.sequentialpatterns.huspull;

import java.util.Arrays;

/**
 * Utility-list (UL-list) of ONE input sequence, as used by the HUSP-ULL
 * algorithm (Gan et al., "Fast Utility Mining on Complex Sequences", 2019).
 * <br/><br/>
 * The paper stores one object per element (item, utility, remaining utility,
 * next position) plus a header table. Here the same information is stored in
 * flat primitive arrays, which is much more compact and cache friendly:
 * <ul>
 * <li>Elements are numbered 0..n-1 in sequence order. Inside an itemset, items
 * are sorted by increasing (dense) item id.</li>
 * <li>{@code item[p]}, {@code util[p]}: item and utility of element p.</li>
 * <li>{@code rem[p]}: remaining utility of element p, i.e. the sum of the
 * utilities of ALL elements located after p in the sequence, including the
 * items that follow p inside the same itemset (they can still be added by an
 * I-concatenation). For the first "a" of S1 in the paper's example this is
 * 84.</li>
 * <li>{@code setOf[p]}: index of the itemset containing element p.</li>
 * <li>{@code setStart[s]}: position of the first element of itemset s;
 * {@code setStart[numSets]} equals n.</li>
 * <li>{@code occItem / occStart / occPos}: a CSR-style index that replaces the
 * paper's header table and "next position" pointers. For the distinct item
 * {@code occItem[k]}, its positions in the sequence, in increasing order, are
 * {@code occPos[occStart[k] .. occStart[k+1]-1]}.</li>
 * </ul>
 * Items with a low SWU are removed before the lists are built, so the
 * remaining utilities already exclude them.
 */
public class ULList {

	/** item (dense id) of each element */
	final int[] item;
	/** utility of each element */
	final int[] util;
	/** remaining utility after each element (long to avoid overflow) */
	final long[] rem;
	/** index of the itemset containing each element */
	final int[] setOf;
	/** first element of each itemset (plus a final sentinel equal to n) */
	final int[] setStart;

	/** distinct items of this sequence, in increasing order */
	final int[] occItem;
	/** start offset in occPos for each distinct item (plus a sentinel) */
	final int[] occStart;
	/** positions of the elements, grouped by item, increasing inside a group */
	final int[] occPos;

	/**
	 * Builds the utility-list of one sequence from its items, utilities and
	 * itemset boundaries.
	 *
	 * @param items         items of the elements, itemset by itemset, each itemset
	 *                      sorted by increasing item id with no duplicate
	 * @param utilities     utility of each element
	 * @param itemsetStarts start position of each itemset, followed by items.length
	 */
	ULList(int[] items, int[] utilities, int[] itemsetStarts) {
		int elementCount = items.length;
		this.item = items;
		this.util = utilities;
		this.setStart = itemsetStarts;

		// itemset index of each element
		int numSets = itemsetStarts.length - 1;
		setOf = new int[elementCount];
		for (int itemsetIndex = 0; itemsetIndex < numSets; itemsetIndex++) {
			for (int position = itemsetStarts[itemsetIndex]; position < itemsetStarts[itemsetIndex + 1]; position++) {
				setOf[position] = itemsetIndex;
			}
		}

		// remaining utility: suffix sums, excluding the element itself
		rem = new long[elementCount];
		long accumulatedUtility = 0;
		for (int position = elementCount - 1; position >= 0; position--) {
			rem[position] = accumulatedUtility;
			accumulatedUtility += utilities[position];
		}

		// item index: sort (item, position) pairs packed in a long
		long[] packedKeys = new long[elementCount];
		for (int position = 0; position < elementCount; position++) {
			packedKeys[position] = ((long) items[position] << 32) | position;
		}
		Arrays.sort(packedKeys);

		int distinctCount = 0;
		for (int index = 0; index < elementCount; index++) {
			if (index == 0 || (int) (packedKeys[index] >>> 32) != (int) (packedKeys[index - 1] >>> 32)) {
				distinctCount++;
			}
		}
		occItem = new int[distinctCount];
		occStart = new int[distinctCount + 1];
		occPos = new int[elementCount];
		int distinctIndex = -1;
		for (int index = 0; index < elementCount; index++) {
			int currentItem = (int) (packedKeys[index] >>> 32);
			if (distinctIndex < 0 || occItem[distinctIndex] != currentItem) {
				distinctIndex++;
				occItem[distinctIndex] = currentItem;
				occStart[distinctIndex] = index;
			}
			occPos[index] = (int) packedKeys[index]; // low 32 bits = position
		}
		occStart[distinctCount] = elementCount;
	}

	/**
	 * Returns the number of elements (items) in this sequence.
	 *
	 * @return the number of elements
	 */
	int length() {
		return item.length;
	}

	/**
	 * Finds the index of an item in the item index of this sequence using binary
	 * search.
	 *
	 * @param targetItem an item (dense id)
	 * @return index k such that occItem[k] == targetItem, or -1 if targetItem does
	 *         not occur
	 */
	int occIndex(int targetItem) {
		int index = Arrays.binarySearch(occItem, targetItem);
		return index >= 0 ? index : -1;
	}

	/**
	 * Finds an item inside a range of positions belonging to ONE itemset (hence
	 * sorted by item id) using binary search.
	 *
	 * @param targetItem the item to search
	 * @param from       first position of the range (inclusive)
	 * @param to         last position of the range (exclusive)
	 * @return the position of targetItem, or -1 if targetItem is not in the range
	 */
	int findInRange(int targetItem, int from, int to) {
		int low = from;
		int high = to - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			int value = item[middle];
			if (value < targetItem) {
				low = middle + 1;
			} else if (value > targetItem) {
				high = middle - 1;
			} else {
				return middle;
			}
		}
		return -1;
	}
}