package ca.pfv.spmf.input.utility_transaction_database;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithms.frequentpatterns.two_phase.AlgoTwoPhase;

/**
 * This class represents a transaction database with utility values, as used by
 * the Two-Phase algorithm for high utility itemset mining.
 *
 * @see AlgoTwoPhase
 * @see TransactionTP
 * @author Philippe Fournier-Viger
 */
public class UtilityTransactionDatabaseTP {

	/** this is the set of items in the database **/
	private final Set<Integer> allItems = new HashSet<Integer>();

	/** this is the list of transactions in the database **/
	private final List<TransactionTP> transactions = new ArrayList<TransactionTP>();

	/** The total utility of the database */
	private long totalUtility;
	
	/** largest item id */
	private int maxItemID;


	/**
	 * A map, where an entry in the map is key = String (attribute value), value =
	 * Integer (item id)
	 */
	Map<Integer, String> mapItemIDtoStringValue = null;

	/**
	 * Load a transaction database from a file.
	 * 
	 * @param path the path of the file
	 * @throws IOException exception if error while reading the file.
	 */
	public void loadFile(String path) throws IOException {
		maxItemID = 0;
		
		String thisLine;
		BufferedReader myInput = null;
		try {
			FileInputStream fin = new FileInputStream(new File(path));
			myInput = new BufferedReader(new InputStreamReader(fin));
			// for each transaction (line) in the input file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.startsWith("@ITEM")) {
					// remove "@ITEM="
					thisLine = thisLine.substring(6);
					// get the position of the first = in the remaining string
					int index = thisLine.indexOf("=");
					int itemID = Integer.parseInt(thisLine.substring(0, index));
					String stringValue = thisLine.substring(index + 1);
//					System.out.println(itemID);
//					System.out.println(stringValue);
					if (mapItemIDtoStringValue == null) {
						mapItemIDtoStringValue = new HashMap<Integer, String>();
					}
					mapItemIDtoStringValue.put(itemID, stringValue);
				} else if (thisLine.isEmpty() == false && thisLine.charAt(0) != '#' && thisLine.charAt(0) != '%'
						&& thisLine.charAt(0) != '@') {
					// split the line according to spaces and then
					// call "addTransaction" to process this line.

					// process the transaction
					processTransaction(thisLine.split(":"));
				}

			}
		} catch (Exception e) {
			// catch exceptions
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				// close the file
				myInput.close();
			}
		}
	}

	/**
	 * Process a line (transaction) from the input file
	 * 
	 * @param line a line
	 */
	private void processTransaction(String[] line) {
		// get the transaction utility
		int transactionUtility = Integer.parseInt(line[1]);

		String[] items = line[0].split(" ");
		String[] utilities = line[2].split(" ");

		// Create a list for storing items
		List<ItemUtility> itemUtilityObjects = new ArrayList<ItemUtility>();
		// for each item
		for (int i = 0; i < items.length; i++) {
			int item = Integer.parseInt(items[i]);
			itemUtilityObjects.add(new ItemUtility(item, Integer.parseInt(utilities[i])));
			allItems.add(item);
			
			if(item >maxItemID) {
				maxItemID = item;
			}
		}

		totalUtility += transactionUtility;

		// add the transaction to the list of transactions
		transactions.add(new TransactionTP(itemUtilityObjects, transactionUtility));
	}

	/**
	 * Print this database to System.out.
	 */
	public void printDatabase() {
		System.out.println("===================  Database ===================");
		int count = 0;
		// for each transaction
		for (TransactionTP itemset : transactions) {
			// print the transaction
			System.out.print("0" + count + ":  ");
			itemset.print();
			System.out.println("");
			count++;
		}
	}

	/**
	 * Get the number of transactions.
	 * 
	 * @return a int
	 */
	public int size() {
		return transactions.size();
	}

	/**
	 * Get the list of transactions.
	 * 
	 * @return the list of Transactions.
	 */
	public List<TransactionTP> getTransactions() {
		return transactions;
	}

	/**
	 * Get the set of items in this database.
	 * 
	 * @return a Set of Integers
	 */
	public Set<Integer> getAllItems() {
		return allItems;
	}

	/**
	 * Get the name corresponding to an item id, if one is known. Otherwise returns
	 * null.
	 * 
	 * @param item the item
	 * @return a string or null
	 */
	public String getNameForItem(Integer item) {
		if (mapItemIDtoStringValue == null) {
			return null;
		}
		String name = mapItemIDtoStringValue.get(item);
		if (name == null) {
			return null;
		}
		return name;
	}

	/**
	 * Return a map to convert item names to strings (if applicable)
	 * 
	 * @return a map or null;
	 */
	public Map<Integer, String> getMapItemToStringValues() {
		return mapItemIDtoStringValue;
	}

	/**
	 * Get the total utility of this database
	 * 
	 * @return the total
	 */
	public long getTotalUtility() {
		return totalUtility;
	}
	

	/** Get the maximum Item ID
	 * 
	 * @return the id
	 */
	public int getMaxItemID() {
		return maxItemID;
	}

}
