/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* A revision was obtained from the LAC library under the GNU GPL license and re-adapted for SPMF.
* @Copyright SPMF, LAC 2019   
*
* SPMF is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* SPMF is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
* 
*/
package ca.pfv.spmf.algorithms.classifiers.accf;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Class used to represent a superset table used in CHARM to check if itemsets
 * are closed or not. It enables to speed-up testing.
 * 
 * Originally based on the Charm code of SPMF.
 * 
 * @see AlgoAACF
 */
public class SupersetTable {
	/**
	 * Field used to store the table itself
	 */
	private List<ItemsetACCF>[] table;

	/**
	 * Default constructor
	 */
	@SuppressWarnings("unchecked")
	public SupersetTable() {
		table = new ArrayList[1];
	}

	/**
	 * Check if itemset is superset or not
	 * 
	 * @param itemset to be checked if has a superset in the same table with same
	 *                support
	 * @param bitset  the list of tids of that itemset
	 * @return false if it has no superset, true otherwise
	 */
	public boolean isSuperset(ItemsetACCF itemset, BitSet bitset) {
		int hashcode = calculateHashCode(bitset);

		if (table[hashcode] == null) {
			return true;
		}

		for (ItemsetACCF currentItemset : table[hashcode]) {
			if (currentItemset.getSupport() == itemset.getSupport() && currentItemset.containsAll(itemset)) {
				// Above line, modified by Philippe to optimize
				return false;
			}
		}
		return true;
	}

	/**
	 * Add an itemset to the hash table
	 * 
	 * @param itemset to be added to the table
	 * @param bitset  bitset to calculate hashcode
	 */
	public void add(ItemsetACCF itemset, BitSet bitset) {
		int hashcode = calculateHashCode(bitset);

		if (table[hashcode] == null) {
			table[hashcode] = new ArrayList<ItemsetACCF>();
		}
		table[hashcode].add(itemset);
	}

	/**
	 * Calculate a hashcode to effectively check supersets
	 * 
	 * @param bitset to calculate hashcode
	 * @return the hashcode
	 */
	private int calculateHashCode(BitSet bitset) {
		int hashCode = 0;
		// For each bit in the bitset, sum it
		for (int bid = bitset.nextSetBit(0); bid >= 0; bid = bitset.nextSetBit(bid + 1)) {
			hashCode += bid;
		}

		// Negative hashcode are converted to positive
		if (hashCode < 0) {
			hashCode = -1 * hashCode;
		}

		// Module with the size of the table
		return hashCode % table.length;
	}
}
