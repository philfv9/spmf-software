
package ca.pfv.spmf.algorithms.frequentpatterns.krimp;
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
 * A class to represent an itemset and its support
 * @see AlgoKrimp
 * @author Philippe Fournier-Viger, 2024
 */
public class Itemset {
	/** the items in the itemset */
	public int[] items;

	/** the number of transactions that contain the itemset */
	public int support;

	public Itemset(int[] items, int support) {
		this.items = items;
		this.support = support;
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
}