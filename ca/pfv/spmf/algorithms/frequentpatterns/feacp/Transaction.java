package ca.pfv.spmf.algorithms.frequentpatterns.feacp;

import java.util.HashMap;
import java.util.Map;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
*/

/**
 * This class represents a transaction
 * 
 * @author Philippe Fournier-Viger
 */
public class Transaction {

	/** a buffer to store items of an itemset */
	public static int[] tempItems = new int[2000];
	/** a buffer to store utilities of an itemset */
	public static double[] tempUtilities = new double[2000];

	/** an offset pointer, used by projected transactions */
	int offset;

	/** an array of items representing the transaction */
	int[] items;
	/** an array of utilities associated to items of the transaction */
	double[] utilities;

	/** the transaction utility of the transaction or projected transaction */
	double transactionUtility;

	/** the old transaction utility */
	double OldTU;

	/**
	 * the profit of a given prefix in this transaction (initially 0 if a
	 * transaction is not projected)
	 */
	double prefixUtility;
	public Map<Integer, Double> parentsInTransaction = new HashMap<Integer, Double>();

	/**
	 * Constructor of a transaction
	 * 
	 * @param items              the items in the transaction
	 * @param utilities          the utilities of item in this transaction
	 * @param transactionUtility the transaction utility
	 */
	public Transaction(int[] items, double[] utilities, double transactionUtility) {
		this.items = items;
		this.utilities = utilities;
		this.transactionUtility = transactionUtility;
		this.prefixUtility = 0;
		this.offset = 0;
		OldTU = transactionUtility;
	}

	/**
	 * Constructor for a projected transaction
	 * 
	 * @param transaction the transaction that will be projected (it may be an
	 *                    original transaction or a previously projected transaction
	 * @param offsetE     an offset over the original transaction for projecting the
	 *                    transaction
	 */
	public Transaction(Transaction transaction, int itemE, int[] newNamesToOldNames, TaxonomyTree taxonomy) {
		// copy items and utilities from the original transaction
		this.OldTU = transaction.OldTU;
//		List<Integer> listItems = new ArrayList<Integer>();
//		List<Double> listUtitlities = new ArrayList<Double>();
		double newTU = transaction.getUtility();
		double utilityE = 0;
		int newSize = 0;
		for (int i = 0; i < transaction.getItems().length; i++) {
			int item = transaction.getItems()[i];
			if (item < itemE) {
				newTU -= transaction.getUtilities()[i];
			} else {
				if (item == itemE) {
					newTU -= transaction.getUtilities()[i];
					utilityE = transaction.getUtilities()[i];
				} else {
					tempItems[newSize] = transaction.getItems()[i];
					tempUtilities[newSize] = transaction.getUtilities()[i];
					newSize++;
				}
			}
		}
		this.items = new int[newSize];
		System.arraycopy(tempItems, 0, this.items, 0, newSize);
		this.utilities = new double[newSize];
		System.arraycopy(tempUtilities, 0, this.utilities, 0, newSize);
		this.prefixUtility = transaction.prefixUtility + utilityE;
		this.transactionUtility = newTU;
		this.parentsInTransaction = new HashMap<Integer, Double>();
		long initialTime = System.currentTimeMillis();
		for (Map.Entry<Integer, Double> entry : transaction.parentsInTransaction.entrySet()) {
			if (itemE < entry.getKey()) {
				if (!CheckParent(newNamesToOldNames[entry.getKey()], newNamesToOldNames[itemE], taxonomy)) {
					parentsInTransaction.put(entry.getKey(), entry.getValue());
				}
			}
		}
		long endTime = System.currentTimeMillis();
		AlgoFEACP.timeProject += (endTime - initialTime);

	}

	public Transaction(Transaction transaction, int ParentItem, TaxonomyTree taxonomy, int[] newNamesToOldNames) {

		this.OldTU = transaction.OldTU;
		double newTU = transaction.getUtility();
		int newSize = 0;
		for (int i = 0; i < transaction.getItems().length; i++) {
			int item = transaction.getItems()[i];
			if (CheckParent(newNamesToOldNames[item], newNamesToOldNames[ParentItem], taxonomy) == false) {
				if (ParentItem > item) {
					newTU -= transaction.getUtilities()[i];
				} else {
					tempItems[newSize] = transaction.getItems()[i];
					tempUtilities[newSize] = transaction.getUtilities()[i];
					newSize++;
				}

			}

		}

		this.items = new int[newSize];
		System.arraycopy(tempItems, 0, this.items, 0, newSize);
		this.utilities = new double[newSize];
		System.arraycopy(tempUtilities, 0, this.utilities, 0, newSize);
		double utilityofParent = transaction.parentsInTransaction.get(ParentItem);
		this.prefixUtility = transaction.prefixUtility + utilityofParent;
		newTU -= utilityofParent;
		this.transactionUtility = newTU;
		this.parentsInTransaction = new HashMap<Integer, Double>();
		long start = System.currentTimeMillis();

		for (Map.Entry<Integer, Double> entry : transaction.parentsInTransaction.entrySet()) {
			Integer item = entry.getKey();
			if (ParentItem < item) {
				if (!CheckParent(newNamesToOldNames[item], newNamesToOldNames[ParentItem], taxonomy)) {
					parentsInTransaction.put(item, entry.getValue());
				}
			}

		}
		long end = System.currentTimeMillis();
		AlgoFEACP.timeProject += (end - start);

	}

	/**
	 * Get a string representation of this transaction
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < items.length; i++) {
			buffer.append(items[i]);
			buffer.append("[");
			buffer.append(utilities[i]);
			buffer.append("] ");
		}
		buffer.append(" Remaining Utility:" + transactionUtility);
		buffer.append(" Prefix Utility:" + prefixUtility);
		return buffer.toString();
	}

	public String toStringShort() {
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < items.length; i++) {
			buffer.append(items[i]);
			buffer.append("[");
			buffer.append(utilities[i]);
			buffer.append("] ");
		}
		return buffer.toString();
	}

	public String toStringCompact() {
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < items.length; i++) {
			buffer.append(items[i]);
			buffer.append(" ");
		}
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
	public double[] getUtilities() {
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
	 * time rename items from old names to new names
	 * 
	 * @param oldNamesToNewNames An array indicating for each old name, the
	 *                           corresponding new name.
	 */
	public void removeUnpromisingItems(int[] oldNamesToNewNames, TaxonomyTree taxonomy) {
		// In this method, we used buffers for temporary storing items and their
		// utilities
		// (tempItems and tempUtilities)
		// This is for memory optimization.
		// for each item
		int i = 0;
		int newTU = 0;
		for (int j = 0; j < items.length; j++) {
			int item = items[j];
			double utility = utilities[j];
			TaxonomyNode nodeParent = taxonomy.getMapItemToTaxonomyNode().get(item).getParent();
			while (nodeParent.getData() != -1) {
				int newNameParent = oldNamesToNewNames[nodeParent.getData()];
				if (newNameParent != 0) {
					Double UtitilityOfParent = parentsInTransaction.get(newNameParent);
					if (UtitilityOfParent == null) {
						parentsInTransaction.put(newNameParent, utility);
					} else {
						parentsInTransaction.put(newNameParent, UtitilityOfParent + utility);
					}
				}
				nodeParent = nodeParent.getParent();
			}
			// Convert from old name to new name
			int newName = oldNamesToNewNames[item];

			// if the item is promising (it has a new name)
			if (newName != 0) {
				// copy the item and its utility
				tempItems[i] = newName;
				tempUtilities[i] = utilities[j];
				i++;
				newTU += utilities[j];
			} else {
				// else subtract the utility of the item

				// transactionUtility -= utilities[j];
			}
		}
		this.transactionUtility = OldTU;
		// copy the buffer of items back into the original array
		this.items = new int[i];
		System.arraycopy(tempItems, 0, this.items, 0, i);

		// copy the buffer of utilities back into the original array
		this.utilities = new double[i];
		System.arraycopy(tempUtilities, 0, this.utilities, 0, i);

		// Sort by increasing TWU values
		insertionSort(this.items, this.utilities);
	}

	/**
	 * Implementation of Insertion sort for integers. This has an average
	 * performance of O(n log n)
	 * 
	 * @param items array of integers
	 */
	public static void insertionSort(int[] items, double[] utitilies) {
		for (int j = 1; j < items.length; j++) {
			int itemJ = items[j];
			double utilityJ = utitilies[j];
			int i = j - 1;
			for (; i >= 0 && (items[i] > itemJ); i--) {
				items[i + 1] = items[i];
				utitilies[i + 1] = utitilies[i];
			}
			items[i + 1] = itemJ;
			utitilies[i + 1] = utilityJ;
		}
	}

	public double getUtility() {
		return transactionUtility;
	}

	private boolean CheckParent(int item1, int item2, TaxonomyTree taxonomy) {
		TaxonomyNode nodeItem1 = taxonomy.getMapItemToTaxonomyNode().get(item1);
		TaxonomyNode nodeItem2 = taxonomy.getMapItemToTaxonomyNode().get(item2);
		int levelOfItem1 = nodeItem1.getLevel();
		int levelOfItem2 = nodeItem2.getLevel();
		if (levelOfItem1 == levelOfItem2) {
			return false;
		} else {
			if (levelOfItem1 > levelOfItem2) {
				TaxonomyNode parentItem1 = nodeItem1.getParent();
				while (parentItem1.getData() != -1) {
					if (parentItem1.getData() == nodeItem2.getData()) {
						return true;
					}
					parentItem1 = parentItem1.getParent();
				}
				return false;
			} else {
				TaxonomyNode parentItem2 = nodeItem2.getParent();
				while (parentItem2.getData() != -1) {
					if (parentItem2.getData() == nodeItem1.getData()) {
						return true;
					}
					parentItem2 = parentItem2.getParent();
				}
				return false;
			}
		}
	}
}
