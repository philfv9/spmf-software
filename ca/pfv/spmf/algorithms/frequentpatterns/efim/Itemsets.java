package ca.pfv.spmf.algorithms.frequentpatterns.efim;

import java.util.Arrays;

import ca.pfv.spmf.datastructures.collections.list.ArrayListObject;

/**
 * This class represents a set of itemsets, where an itemset is an array of integers 
 * with an associated support count. Itemsets are ordered by size. For
 * example, level 1 means itemsets of size 1 (that contains 1 item).
* 
 * @author Philippe Fournier-Viger
 */
public class Itemsets{
	/** We store the itemsets in a list named "levels".
	 Position i in "levels" contains the list of itemsets of size i */
	private final ArrayListObject<ArrayListObject<Itemset>> levels = new ArrayListObject<ArrayListObject<Itemset>>(); 
	/** the total number of itemsets **/
	private int itemsetsCount = 0;
	/** a name that we give to these itemsets (e.g. "frequent itemsets") */
	private String name;

	/**
	 * Constructor
	 * @param name the name of these itemsets
	 */
	public Itemsets(String name) {
		this.name = name;
		levels.add(new ArrayListObject<Itemset>()); // We create an empty level 0 by
												// default.
	}

	/* (non-Javadoc)
	 * @see ca.pfv.spmf.patterns.itemset_array_integers_with_count.AbstractItemsets#printItemsets(int)
	 */
	public void printItemsets() {
		System.out.println(" ------- " + name + " -------");
		int patternCount = 0;
		int levelCount = 0;
		// for each level (a level is a set of itemsets having the same number of items)
		for(int i =0; i <levels.size(); i++) {
			ArrayListObject<Itemset> level = levels.get(i);
//		for (List<Itemset> level : levels) {
			// print how many items are contained in this level
			System.out.println("  L" + levelCount + " ");
			// for each itemset0S
			for(int j =0; j <level.size(); j++) {
				Itemset itemset = level.get(j);
//			for (Itemset itemset : level) {
				Arrays.sort(itemset.getItems());
				// print the itemset
				System.out.print("  pattern " + patternCount + ":  " +itemset.toString());
//				itemset.print();
				// print the support of this itemset
				System.out.print("Utility :  "
						+ itemset.getUtility());
				patternCount++;
				System.out.println(" ");
			}
			levelCount++;
		}
		System.out.println(" --------------------------------");
	}

	/* (non-Javadoc)
	 * @see ca.pfv.spmf.patterns.itemset_array_integers_with_count.AbstractItemsets#addItemset(ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset, int)
	 */
	public void addItemset(Itemset itemset, int k) {
		while (levels.size() <= k) {
			levels.add(new ArrayListObject<Itemset>());
		}
		levels.get(k).add(itemset);
		itemsetsCount++;
	}

	/* (non-Javadoc)
	 * @see ca.pfv.spmf.patterns.itemset_array_integers_with_count.AbstractItemsets#getLevels()
	 */
	public ArrayListObject<ArrayListObject<Itemset>> getLevels() {
		return levels;
	}

	/* (non-Javadoc)
	 * @see ca.pfv.spmf.patterns.itemset_array_integers_with_count.AbstractItemsets#getItemsetsCount()
	 */
	public int getItemsetsCount() {
		return itemsetsCount;
	}

	/* (non-Javadoc)
	 * @see ca.pfv.spmf.patterns.itemset_array_integers_with_count.AbstractItemsets#setName(java.lang.String)
	 */
	public void setName(String newName) {
		name = newName;
	}
	
	/* (non-Javadoc)
	 * @see ca.pfv.spmf.patterns.itemset_array_integers_with_count.AbstractItemsets#decreaseItemsetCount()
	 */
	public void decreaseItemsetCount() {
		itemsetsCount--;
	}
}
