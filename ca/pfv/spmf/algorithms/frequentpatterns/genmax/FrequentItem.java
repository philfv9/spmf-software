package ca.pfv.spmf.algorithms.frequentpatterns.genmax;
/* This file is copyright (c) 2025 Philippe Fournier-Viger
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
 * Represents a frequent item with its support in a transaction database for GENMAX 
 * </p>
 * @author Philippe Fournier-Viger
 * @see AlgoGENMAX
 */
class FrequentItem {
	/** The unique identifier of the item. */
	int item;

	/** The support count of the item (number of transactions containing it). */
	int support;

	/**
	 * Creates a new frequent item with the specified item ID and support count.
	 *
	 * @param item    the unique identifier of the item
	 * @param support the number of transactions containing the item
	 */
	FrequentItem(int item, int support) {
		this.item = item;
		this.support = support;
	}
}