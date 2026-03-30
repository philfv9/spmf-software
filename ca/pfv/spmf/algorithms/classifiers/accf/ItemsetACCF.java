/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
* @Copyright original version LAC 2019   @copyright of modifications SPMF 2021
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

import java.util.Arrays;

import ca.pfv.spmf.algorithms.ArraysAlgos;

/**
 * This class represents an itemset (a set of items) implemented as an array of
 * integers with a variable to store the support count of the itemset.
 * 
 * @see AlgoAACF
 */
public class ItemsetACCF {
	/**
	 * Set of items
	 */
	private Short[] itemset;

	/**
	 * Support for this itemset
	 */
	private long support = 0;

	/**
	 * Constructor
	 * 
	 * @param items   forming the itemset
	 * @param support for the whole itemset
	 */
	public ItemsetACCF(Short[] items, long support) {
		this.itemset = items;
		this.support = support;
	}

	/**
	 * Get the support
	 * 
	 * @return support for current itemset
	 */
	public long getSupport() {
		return support;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(itemset);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if ((o instanceof ItemsetACCF) == false) {
			return false;
		}
		return this.hashCode() == ((ItemsetACCF) o).hashCode();
	}

	/**
	 * Check if the set received as parameter is all contained in this itemset
	 * 
	 * @param smallSet to be checked
	 * @return true if itemset2 is all contained in this itemset
	 */
	boolean containsAll(ItemsetACCF smallSet) {
		// Optimized by Philippe
		return ArraysAlgos.containsOrEquals(this.itemset, smallSet.itemset);
	} 
	
	/**
	 * Check if an itemset contains another itemset. It assumes that itemsets are
	 * sorted according to the lexical order.
	 * 
	 * @param itemset1 the first itemset
	 * @param itemset2 the second itemset
	 * @return true if the first itemset contains the second itemset
	 */
	public static boolean containsOrEquals(Short itemset1[], Short itemset2[]) {
		// for each item in the first itemset
		loop1: for (int i = 0; i < itemset2.length; i++) {
			// for each item in the second itemset
			for (int j = 0; j < itemset1.length; j++) {
				// if the current item in itemset1 is equal to the one in itemset2
				// search for the next one in itemset1
				if (itemset1[j].shortValue() == itemset2[i].shortValue()) {
					continue loop1;
					// if the current item in itemset1 is larger
					// than the current item in itemset2, then
					// stop because of the lexical order.
				} else if (itemset1[j].shortValue() > itemset2[i].shortValue()) {
					return false;
				}
			}
			// means that an item was not found
			return false;
		}
		// if all items were found, return true.
		return true;
	}

}
