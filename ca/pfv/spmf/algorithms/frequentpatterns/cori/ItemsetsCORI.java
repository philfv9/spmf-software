package ca.pfv.spmf.algorithms.frequentpatterns.cori;

/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class represents a set of itemsets with support counts, organized by
 * size. Itemsets are stored in levels where level k contains itemsets of size
 * k.
 * 
 * @author Philippe Fournier-Viger, 2015
 * @see AlgoCORI
 */
public class ItemsetsCORI {
	
	/** List of levels where position i contains itemsets of size i */
	private final List<List<ItemsetCORI>> levels = new ArrayList<List<ItemsetCORI>>();
	
	/** Total number of itemsets */
	private int itemsetsCount = 0;
	
	/** Name for this set of itemsets */
	private String name;

	/**
	 * Constructor
	 * 
	 * @param name the name of these itemsets
	 */
	public ItemsetsCORI(String name) {
		this.name = name;
		levels.add(new ArrayList<ItemsetCORI>()); // Create empty level 0
	}

	/**
	 * Print all itemsets to System.out
	 * 
	 * @param nbObject number of objects in the database
	 */
	public void printItemsets(int nbObject) {
		System.out.println(" ------- " + name + " -------");
		int patternCount = 0;
		int levelCount = 0;
		for (List<ItemsetCORI> level : levels) {
			System.out.println("  L" + levelCount + " ");
			for (ItemsetCORI itemset : level) {
				Arrays.sort(itemset.getItems());
				System.out.print("  pattern " + patternCount + ":  ");
				itemset.print();
				System.out.print("support :  " + itemset.getAbsoluteSupport());
				System.out.print(" bond :  " + itemset.getBond());
				patternCount++;
				System.out.println("");
			}
			levelCount++;
		}
		System.out.println(" --------------------------------");
	}

	/**
	 * Add an itemset at level k
	 * 
	 * @param itemset the itemset to add
	 * @param k       the level (size of the itemset)
	 */
	public void addItemset(ItemsetCORI itemset, int k) {
		while (levels.size() <= k) {
			levels.add(new ArrayList<ItemsetCORI>());
		}
		levels.get(k).add(itemset);
		itemsetsCount++;
	}

	/**
	 * Get all levels
	 * 
	 * @return the levels as a list of lists
	 */
	public List<List<ItemsetCORI>> getLevels() {
		return levels;
	}

	/**
	 * Get the total number of itemsets
	 * 
	 * @return the itemset count
	 */
	public int getItemsetsCount() {
		return itemsetsCount;
	}

	/**
	 * Set the name of this set of itemsets
	 * 
	 * @param newName the new name
	 */
	public void setName(String newName) {
		name = newName;
	}

	/**
	 * Decrease the itemset count by 1
	 */
	public void decreaseItemsetCount() {
		itemsetsCount--;
	}
}