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
 * Represents an item along with its associated utility value.
 * Typically used in high-utility itemset mining to track
 * the utility of individual items.
 */
 
public class ItemUtility{
	
	/** The identifier or index of the item. */
	final int item;
	
	/** The utility value associated with this item. */
	final float utility;
	
	public String itemRef;
	
	/**
     * Constructs an ItemUtility instance with the given item ID and utility.
     *
     * @param item the item identifier
     * @param utility the utility value of the item
     */
	public ItemUtility(int item, float utility){
		this.item = item;
		this.utility = utility;
	}
}