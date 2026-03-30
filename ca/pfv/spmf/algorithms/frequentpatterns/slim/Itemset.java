package ca.pfv.spmf.algorithms.frequentpatterns.slim;

import java.util.Arrays;
import java.util.List;
/* Copyright (c) 2008-2013 Philippe Fournier-Viger
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
 * A class to represent an itemset, using an array of integers
 */
public class Itemset implements Comparable{

	/** the items */
	public int[] items;

	/** the number of transactions that use this itemset for compression */
	List<Integer> transactionList;

	/**
	 * A constructor to create an itemset from an array of items and a support value
	 * 
	 * @param items           the array of items
	 * @param transactionList the support value
	 */
	public Itemset(int[] items, List<Integer> transactionList) {
		this.items = items;
		this.transactionList = transactionList;
	}

	/** A method to return the length of this itemset */
	public int length() {
		return this.items.length;
	}

	/**
	 * Get this itemset as a string
	 * 
	 * @return this itemset as a string
	 */
	public String toString() {
		StringBuilder r = new StringBuilder();
		for (Integer item : items) {
			r.append(item.toString());
			r.append(' ');
		}
		return r.toString();
	}

	/**
	 * Get the support count .
	 * @return the support count.
	 */
	public int getSupport() {
		return transactionList.size();
	}

	public int compareTo(Object o) {
		Itemset itemset = (Itemset) o;
		return Arrays.compare(items, itemset.items);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null) {
			return false;
		}
		return Arrays.equals(items, ((Itemset)obj).items);
	}

}
