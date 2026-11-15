package ca.pfv.spmf.algorithms.frequentpatterns.tkn;

/* This file is copyright (c) 2021-2026 Mohamed Ashraf et al.
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
*
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
* 
* Do not remove copyright and author information.
*/

/**
 * This class represents an itemset found by the TKN algorithm, with its
 * utility. Itemsets are compared by utility, so that the top-k itemsets can be
 * stored in a priority queue.
 *
 * @see AlgoTKN
 * @author Mohamed Ashraf
 */
public class ItemsetTKN implements Comparable<ItemsetTKN> {

	/** the items of the itemset */
	private final int[] items;

	/** the utility of the itemset */
	private final long utility;

	/**
	 * Constructor
	 *
	 * @param items   the items of the itemset
	 * @param utility the utility of the itemset
	 */
	public ItemsetTKN(int[] items, long utility) {
		this.items = items;
		this.utility = utility;
	}

	/**
	 * Get the items of this itemset
	 *
	 * @return the items
	 */
	public int[] getItems() {
		return items;
	}

	/**
	 * Get the utility of this itemset
	 *
	 * @return the utility
	 */
	public long getUtility() {
		return utility;
	}

	/**
	 * Compare this itemset with another itemset according to their utility
	 *
	 * @param other the other itemset
	 * @return a negative integer, zero, or a positive integer if the utility of
	 *         this itemset is lower than, equal to, or higher than the utility of
	 *         the other itemset
	 */
	public int compareTo(ItemsetTKN other) {
		return Long.compare(utility, other.utility);
	}

	/**
	 * Get a string representation of this itemset in the SPMF output format: the
	 * items separated by spaces, followed by "#UTIL:" and the utility
	 *
	 * @return a string
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (int item : items) {
			buffer.append(item);
			buffer.append(' ');
		}
		buffer.append("#UTIL: ");
		buffer.append(utility);
		return buffer.toString();
	}
}
