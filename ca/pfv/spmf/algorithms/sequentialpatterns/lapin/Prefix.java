package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of a sequential pattern. A sequential pattern is a list of
 * itemsets. An itemset is a list of integers.
 *
 * Copyright (c) 2008-2012 Philippe Fournier-Viger
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
public class Prefix {

	/**
	 * the internal representation of this sequential pattern (a list of list of
	 * integers)
	 */
	final List<List<Integer>> itemsets = new ArrayList<List<Integer>>();

	/**
	 * Constructor
	 */
	public Prefix() {
		 // No initialization needed beyond the declaration
	}
	
	/**
	 * Constructor to initialize a prefix with a single itemset containing one item.
	 *
	 * @param item the item to add as the first and only element in the itemset
	 */
	public Prefix(int item) {
		List<Integer> itemset = new ArrayList<>(1);
		itemset.add(item);
		this.itemsets.add(itemset);
	}
	
	/**
	 * Constructor to initialize a prefix with a single itemset containing two items.
	 *
	 * @param item1 the first item
	 * @param item2 the second item
	 */
	public Prefix(int item1, int item2) {
		List<Integer> itemset = new ArrayList<>(2);
		itemset.add(item1);
		itemset.add(item2);
		this.itemsets.add(itemset);
	}

	/**
	 * Get a copy of this sequential pattern
	 * 
	 * @return the copy
	 */
	public Prefix cloneSequence() {
		// Create a new sequential pattern object
		Prefix clone = new Prefix();
		
		// for each itemset
		for (List<Integer> itemset : itemsets) {
			// add the itemset to the sequential pattern
			clone.itemsets.add(new ArrayList<>(itemset));
		}
		// return the copied pattern
		return clone;
	}

	/**
	 * Print this sequential pattern to the console
	 */
	public void print() {
		System.out.print(toString());
	}

	/**
	 * Get a string representation of that sequential pattern
	 */
	public String toString() {
		// for each itemset in that pattern
		StringBuilder r = new StringBuilder();
		for (List<Integer> itemset : itemsets) {
			r.append('(');
			// for each item in the current itemset
			for (Integer item : itemset) {
				// append it
				String string = item.toString();
				r.append(string).append(' ');
			}
			r.append(')');
		}
		// return the string
		return r.append("    ").toString();
	}

	/**
	 * Get the number of itemset in this pattern
	 * 
	 * @return the number of itemsets (int).
	 */
	public int size() {
		return itemsets.size();
	}
	
    /**
     * Returns the internal list of itemsets.
     *
     * @return the list of itemsets
     */
    public List<List<Integer>> getItemsets() {
        return itemsets;
    }

}
