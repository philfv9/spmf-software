package ca.pfv.spmf.gui.viewers.product_tdb_viewer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
//Import the Scanner class
import java.util.Scanner;
import java.util.Set;

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
 * A class to represent a transaction database with profit values
 * 
 * @author Philippe Fournier-Viger 2024
 */
class ProductTransactionDatabase {
	/** A list to store the transactions **/
	private List<ProductTransaction> transactions;
	/** A set to store the distinct items **/
	private Set<Integer> distinctItems;
	/** A list to store the distinct items **/
	private ArrayList<Integer> distinctItemsList;

	/** largest item id */
	private int maxItemID;

	/**
	 * The constructor that creates an empty database
	 */
	public ProductTransactionDatabase() {
		transactions = new ArrayList<ProductTransaction>();
		distinctItems = new HashSet<Integer>();
	}

	// A method to load the data from a file
	public void loadFile(String filepath) throws Exception {
		// Create a scanner object to read the file
		Scanner scanner = new Scanner(new File(filepath));
		// Loop through the lines of the file
		while (scanner.hasNextLine()) {
			// Read the next line
			String line = scanner.nextLine();
			if (line.isEmpty() == false && line.charAt(0) != '#' && line.charAt(0) != '%' && line.charAt(0) != '@') {

				// Split the line by space
				String[] parts = line.split(" ");
				// Parse the profit value
				int profit = Integer.parseInt(parts[0]);
				// Create a list to store the items
				List<Integer> items = new ArrayList<Integer>();
				// Loop through the remaining parts of the line
				for (int i = 1; i < parts.length; i++) {
					// Parse the item value
					int item = Integer.parseInt(parts[i]);
					// Add the item to the list
					items.add(item);
					// Add the item to the set
					this.distinctItems.add(item);

					if (item > maxItemID) {
						maxItemID = item;
					}
				}
				// Create a new transaction object with the profit and the items
				ProductTransaction transaction = new ProductTransaction(profit, items);
				// Add the transaction to the list
				transactions.add(transaction);
			}
		}
		// Close the scanner
		scanner.close();

		distinctItemsList = new ArrayList(distinctItems);
	}

	/** A method to return the maximum item ID
	 * 
	 * @return the maximum id
	 */
	public int getMaxItemID() {
		return maxItemID;
	}

	/**
	 *  A getter method for the transactions
	 * @return the list of transactions
	 */
	public List<ProductTransaction> getTransactions() {
		return transactions;
	}

	/**
	 *  A getter method for the items
	 * @return the list of distinct items
	 */
	public List<Integer> getItems() {
		return distinctItemsList;
	}

	/**
	 *  A method to return the size of the database
	 * @return the number of transactions
	 */
	public int size() {
		return transactions.size();
	}
}