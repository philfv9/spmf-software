package ca.pfv.spmf.algorithms.frequentpatterns.relim;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents the database structure used by the RELIM algorithm.
 * It contains:
 * - An array of supports for each item
 * - A list of transaction lists, one for each item
 * 
 * @see AlgoRelim
 * @author Philippe Fournier-Viger
 */
public class DatabaseStructureRelim {
	
	/** The supports array - supports[i] contains the support of item i */
	public int[] supports;
	
	/** 
	 * The transactions array - transactions[i] contains the list of 
	 * transactions having item i as first item (with the first item removed)
	 * Uses array instead of List<List<>> for better cache locality
	 */
	private List<TransactionRelim>[] transactions;
	
	/**
	 * Constructor
	 * @param supports the supports array
	 * @param itemCount the number of items
	 */
	@SuppressWarnings("unchecked")
	public DatabaseStructureRelim(int[] supports, int itemCount) {
		this.supports = supports;
		this.transactions = new List[itemCount];
	}
		
	/**
	 * Get list for item - may return null if not initialized
	 * @param item the item index
	 * @return the transaction list for this item, or null
	 */
	public List<TransactionRelim> getList(int item) {
		return transactions[item];
	}
	
	/**
	 * Get or create list for item with default capacity
	 * @param item the item index
	 * @return the transaction list for this item
	 */
	public List<TransactionRelim> getOrCreateList(int item) {
		List<TransactionRelim> list = transactions[item];
		if (list == null) {
			list = new ArrayList<>();
			transactions[item] = list;
		}
		return list;
	}
	
	/**
	 * Get or create list for item with specified capacity
	 * @param item the item index
	 * @param capacity the initial capacity if creating new list
	 * @return the transaction list for this item
	 */
	public List<TransactionRelim> getOrCreateListWithCapacity(int item, int capacity) {
		List<TransactionRelim> list = transactions[item];
		if (list == null) {
			list = new ArrayList<>(capacity);
			transactions[item] = list;
		}
		return list;
	}
}