package ca.pfv.spmf.algorithms.frequentpatterns.THUIsl;

/* This file is copyright (c) 2024 Srikumar Krishnamoorthy
 * 
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Represents an item in a high-utility itemset mining context.
 * Stores the transaction-weighted utility (TWU) and actual utility of the item.
 */
public class Item{
	/** Transaction-Weighted Utility (TWU) of the item. */
	long twu = 0L;
	
	/** Actual utility of the item. */
	float utility = 0;
	 
	 /**
     * Returns a string representation of the item.
     * Currently, it outputs only the utility value.
     *
     * @return the utility of the item as a string
     */
	public String toString(){
		return String.valueOf(utility);
	}
}