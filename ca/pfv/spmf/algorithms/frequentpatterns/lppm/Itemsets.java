package ca.pfv.spmf.algorithms.frequentpatterns.lppm;

import java.util.ArrayList;
import java.util.List;

/*
 * Copyright (c) 2019 Peng Yang, Philippe Fournier-Viger et al.

 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This class represents a set of itemsets as used by the LPPM algorithms.
 * 
 * @author Peng yang
 * @see AlgoLPPMBreadth1
 * @see AlgoLPPMBreadth2
 * @see AlgoLPPMDepth1
 * @see AlgoLPPMDepth2
 */
public class Itemsets {
	/**
	 * Get the itemsets sorted by level as a list of list of itemsets. The list at
	 * position i contains the itemsets of size i.
	 */
	private final List<List<Itemset>> levels = new ArrayList<List<Itemset>>();

	/** The total number of itemsets */
	private int itemsetsCount = 0;

	/** The name of this set of itemsets */
	private final String name;

	/**
	 * Constructor
	 * 
	 * @param name the name of these itemsets
	 */
	public Itemsets(String name) {
		this.name = name;
		// We create an empty level 0 by default.
		levels.add(new ArrayList<Itemset>());
	}

	/**
	 * Add an itemset to this structure
	 * 
	 * @param itemset the itemset
	 * @param k       the number of items contained in the itemset
	 */
	public void addItemset(Itemset itemset, int k) {
		while (levels.size() <= k) {
			levels.add(new ArrayList<Itemset>());
		}
		levels.get(k).add(itemset);
		itemsetsCount++;
	}

	/**
	 * Get all itemsets.
	 * 
	 * @return A list of list of itemsets. Position i in this list is the list of
	 *         itemsets of size i.
	 */
	public List<List<Itemset>> getLevels() {
		return levels;
	}

	/**
	 * Get the total number of itemsets
	 * 
	 * @return the number of itemsets.
	 */
	public int getItemsetsCount() {
		return itemsetsCount;
	}

	/**
	 * Print the itemsets to the console.
	 */
	public void printItemsets() {
		System.out.println(" ------- " + name + " -------");
		int patternCount = 0;
		int levelCount = 0;
		// for each level (a level is a set of itemsets having the same number of items)
		for (List<Itemset> level : levels) {
			// print how many items are contained in this level
			System.out.println("  L" + levelCount + " ");
			// for each itemset
			for (Itemset itemset : level) {
				// print the itemset
				System.out.print("  pattern " + patternCount + ":  ");
				System.out.print(itemset.toString());
				patternCount++;
				System.out.println("");
			}
			levelCount++;
		}
		System.out.println(" --------------------------------");
		System.out.println(" counts of patterns : " + patternCount);
	}
}
