package ca.pfv.spmf.input.cost_utility_transaction_database;
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

import java.util.List;

/**
 * This class represents a transaction (a set of items) from a transaction
 * database with cost and utility values, as used by the LCIM algorithm for low-cost high utility itemset mining.
 *
 * @see CostUtilityTransactionDatabase
 * @see ItemCost
 * 
 * @author Philippe Fournier-Viger
 */
public class TransactionCost{
	
	/** a transaction is an ordered list of items */
	protected final List<ItemCost> itemsCosts; 
	
	/** the transaction utility**/
	protected final int transactionUtility;
	
	/**
	 * Constructor
	 * @param itemsCosts list of items with costs values
	 * @param transactionUtility  the transaction utility
	 */
	public TransactionCost(List<ItemCost> itemsCosts, int transactionUtility){
		this.itemsCosts =  itemsCosts;
		this.transactionUtility = transactionUtility;
	}
	
	/**
	 * Get the list of items.
	 * @return a list of items with cost values
	 */
	public List<ItemCost> getItems(){
		return itemsCosts;
	}
	
	/**
	 * Get the item at a given position.
	 * @param index  the position 
	 * @return the item with its cost value
	 */
	public ItemCost get(int index){
		return itemsCosts.get(index);
	}
	
	/**
	 * Print the transaction to System.out
	 */
	public void print(){
		System.out.print(toString());
	}
	
	/**
	 * Return a string representation of this transaction.
	 */
	public String toString(){
		// create a string buffer
		StringBuilder r = new StringBuilder ();
		// append all items
		for(int i=0; i< itemsCosts.size(); i++){
			r.append(itemsCosts.get(i) + " ");
			if(i == itemsCosts.size() -1){
				r.append(":");
			}
		}
		// append the transaction utility
		r.append(transactionUtility + ": ");
		// append the item utility values
		for(int i=0; i< itemsCosts.size(); i++){
			r.append(itemsCosts.get(i) + " ");
		}
		// return the buffer as a string
		return r.toString();
	}

	/**
	 * Check if this transaction contains an item.
	 * @param item the given item
	 * @return true if yes, otherwise false.
	 */
	public boolean contains(Integer item) {
		// for each item in the transaction
		for(ItemCost itemI : itemsCosts){
			// if found, return true
			if(itemI.item == item){
				return true;
			}else if(itemI.item > item){
				// if the current item is larger, then the item will not be found
				// because of lexical order so return false
				return false;
			}
		}
		// if not found, return false
		return false;
	}
	
	/**
	 * Check if this transaction contains an item.
	 * @param item the given item
	 * @return true if yes, otherwise false.
	 */
	public boolean contains(int item) {
		// for each item in the transaction
		for(int i=0; i<itemsCosts.size(); i++){
			// if found, return true
			if(itemsCosts.get(i).item == item){
				return true;
			}else if(itemsCosts.get(i).item > item){
				// if the current item is larger, then the item will not be found
				// because of lexical order so return false
				return false;
			}
		}
		// if not found, return false
		return false;
	}


	/**
	 * Get the number of items in this transaction.
	 * @return the item count
	 */
	public int size(){
		return itemsCosts.size();
	}

	/**
	 * Get the item utilities for this transaction.
	 * @return a list containing the item utilities
	 */
	public List<ItemCost> getItemsCosts() {
		return itemsCosts;
	}

	/**
	 * Get the transaction utility of this transaction.
	 * @return  an integer
	 */
	public int getTransactionUtility() {
		return transactionUtility;
	}

}
