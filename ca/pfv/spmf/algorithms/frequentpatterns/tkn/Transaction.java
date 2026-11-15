package ca.pfv.spmf.algorithms.frequentpatterns.tkn;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * This class represents a transaction or a projected transaction, as used by the
 * TKN algorithm. It is adapted from the Transaction class of the SPMF
 * implementation of EFIM. In TKN, the transaction utility only considers the
 * positive utilities, since items may have negative utilities.
 *
 * @see AlgoTKN
 * @author Philippe Fournier-Viger, modified by Mohamed Ashraf
 */
public class Transaction {

	/** an offset pointer, used by projected transactions */
	int offset;

	/** an array of items representing the transaction */
	int[] items;

	/** an array of utilities associated to items of the transaction */
	int[] utilities;

	/**
	 * the positive transaction utility (the sum of the positive utilities of the
	 * items in the transaction). For a projected transaction, it is the remaining
	 * positive utility, used to calculate the positive local utility.
	 */
	int transactionUtility;

	/**
	 * the utility of a given prefix in this transaction (initially 0 if a
	 * transaction is not projected)
	 */
	int prefixUtility;

	/**
	 * Constructor of a transaction
	 *
	 * @param items              the items in the transaction
	 * @param utilities          the utilities of items in this transaction
	 * @param transactionUtility the positive transaction utility
	 */
	public Transaction(int[] items, int[] utilities, int transactionUtility) {
		this.items = items;
		this.utilities = utilities;
		this.transactionUtility = transactionUtility;
		this.offset = 0;
		this.prefixUtility = 0;
	}

	/**
	 * Constructor for a projected transaction
	 *
	 * @param transaction the transaction that will be projected (it may be an
	 *                    original transaction or a previously projected
	 *                    transaction)
	 * @param offsetE     an offset over the original transaction for projecting
	 *                    the transaction
	 */
	public Transaction(Transaction transaction, int offsetE) {
		// copy items and utilities from the original transaction
		this.items = transaction.getItems();
		this.utilities = transaction.getUtilities();

		// copy the utility of element e
		int utilityE = this.utilities[offsetE];

		// add the utility of item e to the utility of the whole prefix used to
		// project the transaction
		this.prefixUtility = transaction.prefixUtility + utilityE;

		// we will now calculate the remaining positive utility.
		if (utilityE < 0) {
			// if e is a negative item, all the items after e are also negative
			// (negative items are after positive items in the total order),
			// so the remaining positive utility is 0
			this.transactionUtility = 0;
		} else {
			// otherwise, it is the positive transaction utility minus the utility of
			// the element that was removed
			this.transactionUtility = transaction.transactionUtility - utilityE;
			// and we also need to subtract the positive utilities of all items before e
			// but after the previous offset
			for (int i = transaction.offset; i < offsetE; i++) {
				if (transaction.utilities[i] > 0) {
					this.transactionUtility -= transaction.utilities[i];
				}
			}
		}
		// remember the offset for this projected transaction
		this.offset = offsetE + 1;
	}

	/**
	 * Get a string representation of this transaction
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (int i = offset; i < items.length; i++) {
			buffer.append(items[i]);
			buffer.append("[");
			buffer.append(utilities[i]);
			buffer.append("] ");
		}
		buffer.append(" Remaining Utility:" + transactionUtility);
		buffer.append(" Prefix Utility:" + prefixUtility);
		return buffer.toString();
	}

	/**
	 * Get the array of items in this transaction
	 *
	 * @return array of items
	 */
	public int[] getItems() {
		return items;
	}

	/**
	 * Get the array of utilities in this transaction
	 *
	 * @return array of utilities
	 */
	public int[] getUtilities() {
		return utilities;
	}

	/**
	 * get the last position in this transaction
	 *
	 * @return the last position (the number of items -1 )
	 */
	public int getLastPosition() {
		return items.length - 1;
	}

	/**
	 * This method removes unpromising items from the transaction and at the same
	 * time renames items from old names to new names. Then, the items are sorted
	 * according to their new names (the total order of TKN), and the positive
	 * transaction utility is updated.
	 *
	 * @param oldNamesToNewNames An array indicating for each old name, the
	 *                           corresponding new name (or 0 if the item is not
	 *                           promising).
	 */
	public void removeUnpromisingItems(int[] oldNamesToNewNames) {
		// count the promising items
		int count = 0;
		for (int item : items) {
			if (oldNamesToNewNames[item] != 0) {
				count++;
			}
		}

		// create arrays to store the promising items and their utilities
		int[] newItems = new int[count];
		int[] newUtilities = new int[count];

		// for each item
		int i = 0;
		transactionUtility = 0;
		for (int j = 0; j < items.length; j++) {
			// Convert from old name to new name
			int newName = oldNamesToNewNames[items[j]];

			// if the item is promising (it has a new name)
			if (newName != 0) {
				// copy the item and its utility
				newItems[i] = newName;
				newUtilities[i] = utilities[j];
				// add its utility to the positive transaction utility if it is positive
				if (utilities[j] > 0) {
					transactionUtility += utilities[j];
				}
				i++;
			}
		}
		this.items = newItems;
		this.utilities = newUtilities;

		// Sort the items according to the total order
		insertionSort(this.items, this.utilities);
	}

	/**
	 * Implementation of insertion sort for sorting the items of a transaction by
	 * increasing order (the utilities are moved with their items).
	 *
	 * @param items     array of items
	 * @param utilities array of utilities of the items
	 */
	public static void insertionSort(int[] items, int[] utilities) {
		for (int j = 1; j < items.length; j++) {
			int itemJ = items[j];
			int utilityJ = utilities[j];
			int i = j - 1;
			for (; i >= 0 && (items[i] > itemJ); i--) {
				items[i + 1] = items[i];
				utilities[i + 1] = utilities[i];
			}
			items[i + 1] = itemJ;
			utilities[i + 1] = utilityJ;
		}
	}
}
