package ca.pfv.spmf.gui.viewers.product_tdb_viewer;

import java.util.List;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 * A class to represents a transaction with a profit value
 * 
 * @author Philippe Fournier-Viger 2024
 */
class ProductTransaction {
	/** The profit of the transaction **/
	private int profit;
	/** The items in the transaction **/
	private List<Integer> items;

	/**
	 * The constructor that takes the profit and the items as parameters
	 * 
	 * @param profit the profit
	 * @param items  the items
	 */
	public ProductTransaction(int profit, List<Integer> items) {
		this.profit = profit;
		this.items = items;
	}

	/**
	 * A getter method for the profit
	 * 
	 * @return the profit
	 */
	public int getProfit() {
		return profit;
	}

	/**
	 * A getter method for the items
	 * 
	 * @return the list of items
	 */
	public List<Integer> getItems() {
		return items;
	}

	/**
	 * A method to check if the transaction contains a given item
	 * 
	 * @param item the item
	 * @return true if it appears
	 */
	public boolean contains(int item) {
		return items.contains(item);
	}

	/**
	 * A method to return the size of the transaction
	 * 
	 * @return the size
	 */
	public int size() {
		return items.size();
	}

	/**
	 * A method to override the toString() method
	 * 
	 * @return a String
	 */
	public String toString() {
		return "Profit: " + profit + " | Items: " + items;
	}
}
