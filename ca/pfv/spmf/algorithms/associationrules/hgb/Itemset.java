/* This file is copyright (c) Jayakrushna Sahoo
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

package ca.pfv.spmf.algorithms.associationrules.hgb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is an itemset as used by the algorithms of Sahoo et al. (2015), including FHIM and HUCI-Miner
 * @see AlgoFHIM_and_HUCI
 *  @see AlgoHGB
 *  @see AlgoHGBAll
 * @author Jayakrushna Sahoo
 */
public class Itemset {
	/** a list of items */
	private List<Integer> items = new ArrayList<>(); // ordered
	/** the utilities of items */
	private List<Integer> itemsUtilities = new ArrayList<>();
	/** the transaction utility */
	public int acutility = 0;
	/** the support */
	public int support = 0;

	/** 
	 * The constructor
	 */
	public Itemset() {
	}

	/**
	 * Constructor
	 * @param items the items
	 * @param itemsUtilities the utilities
	 * @param transactionUtility the transaction utility
	 */
	public Itemset(List<Integer> items, List<Integer> itemsUtilities, int transactionUtility) {
		this.items = items;
		this.itemsUtilities = itemsUtilities;
		this.acutility = transactionUtility;
	}

	/** check if this itemset contains an item
	 * 
	 * @param item the item
	 * @return true if it is contained
	 */
	public boolean contains(Integer item) {
		return items.contains(item);
	}

	/**
	 * Sort the items in the itemset
	 */
	public void sort() {
		Collections.sort(items);
	}

	/** get the position of an item in this itemset 
	 * 
	 * @param item the item
	 * @return the position or -1 if the item is not found
	 */
	public int contains1(Integer item) {
		if (this.contains(item))
			return items.indexOf(item);
		else
			return -1;
	}

	/** 
	 * Increase the utility
	 * @param au the amount to add to the utility
	 */
	public void incrementUtility(int au) {
		acutility = acutility + au;
	}

	/**
	 * Check if this itemset contains the same items as another itemset
	 * @param itemset2 the other itemset
	 * @return true if they are the same
	 */
	public boolean isEqualTo(Itemset itemset2) {
		if (items.size() != itemset2.items.size()) {
			return false;
		}
		return items.containsAll(itemset2.items);
	}

	/**
	 * Get the utilities of items in this itemset
	 * @return the utilities
	 */
	public List<Integer> getItemsUtilities() {
		return itemsUtilities;
	}

	/**  Set the utilities of items in this itemset
	 * 
	 * @param itemsU the utilities
	 */
	public void setItemsUtilities(List<Integer> itemsU) {
		this.itemsUtilities = itemsU;
	}

	/**
	 * Add an item to this itemset
	 * @param value the item
	 */
	public void addItem(Integer value) {
		items.add(value);
	}

	/**
	 * Add a utility value to this itemset
	 * @param value the utility value
	 */
	public void addutility(Integer value) {
		itemsUtilities.add(value);
	}

	/** Get all items of this itemset
	 * 
	 * @return the list of items
	 */
	public List<Integer> getItems() {
		return items;
	}

	/** Get the i-th item of this itemset
	 * 
	 * @param index the position i
	 * @return the item
	 */
	public Integer get(int index) {
		return items.get(index);
	}

	/**
	 * Print this itemset to the console
	 */
	public void print() {
		System.out.print(toString());
	}

	@Override
	public String toString() {
		StringBuilder r = new StringBuilder();
		for (Integer attribute : items) {
			r.append(attribute.toString());
			r.append(' ');
		}
		r.append(":");
		r.append(acutility);
		r.append(":");
		r.append(support);
		r.append(":");
		r.append(" [ ");
		itemsUtilities.stream().map((k) -> {
			r.append(k);
			return k;
		}).forEach((_item) -> {
			r.append(' ');
		});
		r.append("] ");
		return r.toString();
	}

	/**Get the number of items in this itemset
	 * 
	 * @return the number of items
	 */
	public int size() {
		return items.size();
	}

	/**
	 * Set the items for this itemset
	 * @param lis the list of items.
	 */
	public void setItemset(List<Integer> lis) {
		this.items = lis;
	}


	/**
	 * Check if this itemset is contained in another itemset
	 * @param itemset2 the other itemset
	 * @return true if it is contained. Otherwise, false
	 */
	public boolean includedIn(Itemset itemset2) {
		return itemset2.getItems().containsAll(items);
	}

	/**
	 * Close this itemset but remove some items
	 * @param itemsetToNotKeep the items to be remove
	 * @return the resulting itemset
	 */
	public Itemset cloneItemSetMinusAnItemset(Itemset itemsetToNotKeep) {
		Itemset itemset = new Itemset();
		for (int l = 0; l < items.size(); l++) {
			if (itemsetToNotKeep.contains(items.get(l)) == false) {
				itemset.addItem(items.get(l));

			}
		}
		itemset.sort();
		return itemset;
	}

	/**
	 * Close this itemset
	 * @return the clone
	 */
	public Itemset clone() {
		Itemset temp = new Itemset();
		for (Integer item : items) {
			temp.addItem(item);
		}
		return temp;
	}

	/**
	 * Do the union with another itemset
	 * @param itemset the other itemset
	 * @return the resulting itemset
	 */
	public Itemset union(Itemset itemset) {
		Itemset union = new Itemset();
		union.getItems().addAll(items);
		for (int l = 0; l < itemset.getItems().size(); l++) {
			if (items.contains(itemset.getItems().get(l)) == false) {
				union.addItem(itemset.getItems().get(l));

			}
		}
		union.sort();
		return union;
	}

	/**
	 * Do the union with another itemset while also considering the utility
	 * @param itemset the other itemset
	 * @return the resulting itemset
	 */
	public Itemset unionU(Itemset itemset) {
		Itemset union = new Itemset();
		union.getItems().addAll(items);
		union.getItemsUtilities().addAll(itemsUtilities);
		for (int l = 0; l < itemset.getItems().size(); l++) {
			if (items.contains(itemset.getItems().get(l)) == false) {
				union.addItem(itemset.getItems().get(l));
				union.addutility(itemset.getItemsUtilities().get(l));

			}
		}
		union.bubbleSort();
		return union;
	}

	/**
	 * Do a bubble sort of items in this itemset
	 */
	private void bubbleSort() {

		for (int i = 0; i < items.size(); i++) {
			for (int j = items.size() - 1; j >= i + 1; j--) {
				if (items.get(j) < items.get(j - 1)) {
					int temp = items.get(j);
					items.set(j, items.get(j - 1));
					items.set(j - 1, temp);
					int tempUtilities = itemsUtilities.get(j);
					itemsUtilities.set(j, itemsUtilities.get(j - 1));
					itemsUtilities.set(j - 1, tempUtilities);
				}
			}
		}
	}



}