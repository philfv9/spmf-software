package ca.pfv.spmf.algorithms.frequentpatterns.hmine_fast;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * This class represents a row of the header table.
 * A row represents an item and contains (1) its support, (2) a head pointer
 * to the linked list of pointers in the projected database.
 * 
 * The linked list is stored in global arrays (AlgoHMine.pointerPool and
 * AlgoHMine.nextPointerIndex) rather than using ArrayList for efficiency.
 *
 * @see AlgoHMine_FAST
 * @author Philippe Fournier-Viger, 2015
 */
class Row {
	/** the item **/
	int item;
	/** its support **/
	int support;
	/** Head pointer index for the global pointer pool (-1 means empty list).
	 */
	int headPointer;

	/**
	 * Default constructor for object pooling.
	 */
	public Row() {
		this.item = -1;
		this.support = 0;
		this.headPointer = -1;
	}

	/**
	 * Reset the row for reuse in object pool.
	 */
	public void reset() {
		this.item = -1;
		this.support = 0;
		this.headPointer = -1;
	}

	/**
	 * Get a string representation of this row.
	 * @return a string representation
	 */
	public String toString() {
		return "Row[item=" + item + ", support=" + support + "]";
	}
}