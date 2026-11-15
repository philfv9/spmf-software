package ca.pfv.spmf.algorithms.frequentpatterns.tkn;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/* This file is copyright (c) 2012-2015 Souleymane Zida, Philippe Fournier-Viger, Alan Souza
* Modified for the TKN algorithm by Mohamed Ashraf et al. (2021-2026)
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
*
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
* 
* Do not remove copyright and author information.
*/

/**
 * This is the parser class for the dataset used by the TKN algorithm. It reads
 * a file in the SPMF format for high utility itemset mining and creates the
 * transactions. It is adapted from the Dataset class of the SPMF implementation
 * of EFIM. The transaction utility of each line is not used: the positive
 * transaction utility is calculated from the utilities of the items. If an item
 * appears more than once in a transaction, its utilities are added.
 *
 * @see AlgoTKN
 * @author Souleymane Zida, Philippe Fournier-Viger, Alan Souza, modified by
 *         Mohamed Ashraf
 */
public class Dataset {

	/** the list of transactions in this dataset */
	List<Transaction> transactions;

	/** the largest item name */
	private int maxItem = 0;

	/**
	 * Constructor
	 *
	 * @param datasetPath             the path of the file containing the dataset
	 * @param maximumTransactionCount the number of transactions to be read from
	 *                                the input file
	 * @throws IOException exception if error reading the file
	 */
	public Dataset(String datasetPath, int maximumTransactionCount) throws IOException {

		// Initialize a list to store transactions in memory
		transactions = new ArrayList<Transaction>();

		// Create a buffered reader to read the input file
		BufferedReader br = new BufferedReader(new FileReader(datasetPath));
		try {
			String line;
			int i = 0;
			// iterate over the lines to build the transactions
			while ((line = br.readLine()) != null) {
				// if the line is a comment, is empty or is metadata
				if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%'
						|| line.charAt(0) == '@') {
					continue;
				}
				i++;
				// read the transaction
				transactions.add(createTransaction(line));
				// if the number of transactions to be read is reached, we stop
				if (i == maximumTransactionCount) {
					break;
				}
			}
		} finally {
			br.close();
		}
	}

	/**
	 * Create a transaction object from a line from the input file
	 *
	 * @param line a line from input file
	 * @return a transaction
	 */
	private Transaction createTransaction(String line) {
		// split the line into tokens according to the ":" separator
		String[] split = line.split(":");

		// Get the list of items
		String[] itemsString = split[0].split(" ");

		// Get the list of item utilities
		// (the transaction utility in split[1] is not used)
		String[] itemsUtilitiesString = split[2].split(" ");

		// Create arrays to store the items and their utilities
		int[] items = new int[itemsString.length];
		int[] utilities = new int[itemsString.length];

		// this variable will be false if the items are not in increasing order
		boolean isSorted = true;

		// for each item
		for (int i = 0; i < items.length; i++) {
			// store the item
			items[i] = Integer.parseInt(itemsString[i]);

			// items must be positive integers
			if (items[i] <= 0) {
				throw new IllegalArgumentException(
						"Items must be positive integers, but the item " + items[i] + " was found in the line: " + line);
			}

			// store its utility in that transaction
			utilities[i] = Integer.parseInt(itemsUtilitiesString[i]);

			// check if the items are still in increasing order
			if (i > 0 && items[i] <= items[i - 1]) {
				isSorted = false;
			}

			// if the item name is larger than the largest item read from the database
			// until now, we remember its name
			if (items[i] > maxItem) {
				maxItem = items[i];
			}
		}

		// if the items are not in increasing order, an item may appear more than
		// once. Thus, we sort the items and merge each duplicate item by adding its
		// utilities.
		if (!isSorted) {
			Transaction.insertionSort(items, utilities);
			int count = 0;
			for (int i = 0; i < items.length; i++) {
				if (count > 0 && items[i] == items[count - 1]) {
					// the same item as the previous one: add its utility
					utilities[count - 1] += utilities[i];
				} else {
					// a new item
					items[count] = items[i];
					utilities[count] = utilities[i];
					count++;
				}
			}
			if (count < items.length) {
				items = Arrays.copyOf(items, count);
				utilities = Arrays.copyOf(utilities, count);
			}
		}

		// calculate the positive transaction utility (the sum of the positive
		// utilities of the items)
		int positiveTransactionUtility = 0;
		for (int utility : utilities) {
			if (utility > 0) {
				positiveTransactionUtility += utility;
			}
		}

		// create the transaction object for this transaction and return it
		return new Transaction(items, utilities, positiveTransactionUtility);
	}

	/**
	 * Get the list of transactions in this database
	 *
	 * @return the list of transactions
	 */
	public List<Transaction> getTransactions() {
		return transactions;
	}

	/**
	 * Get the largest item in this database.
	 *
	 * @return the largest item
	 */
	public int getMaxItem() {
		return maxItem;
	}

	/**
	 * Get a string representation of this database
	 *
	 * @return a string
	 */
	public String toString() {
		// Create a stringbuilder for storing the string
		StringBuilder datasetContent = new StringBuilder();

		// We will append each transaction to this string builder
		for (Transaction transaction : transactions) {
			datasetContent.append(transaction.toString());
			datasetContent.append("\n");
		}
		// Return the string
		return datasetContent.toString();
	}
}
