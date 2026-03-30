package ca.pfv.spmf.algorithms.frequentpatterns.emsfui_b;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a utility list as used by the EMSFUI_B algorithm.
 * Obtained from Github liuxuan615 under the GPL v3 license
 * as it contains derived code from SPMF, which is under the GPL.
 */
class UtilityList {
	int[] itemset;  // the item
	int sumIutils = 0;  // the sum of item utilities
	int sumRutils = 0;  // the sum of remaining utilities
	int prefixIndex = 0; //the index of the prefix of an itemset
	List<Element> elements = new ArrayList<Element>();  // the elements
	
	
	/**
	 * Constructor.
	 * @param item the item that is used for this utility list
	 */
	public UtilityList(int[] itemset){
		this.itemset = itemset;
	}
	
	/**
	 * Method to add an element to this utility list and update the sums at the same time.
	 */
	public void addElement(Element element){
		sumIutils += element.iutils;
		sumRutils += element.rutils;
		elements.add(element);
	}
}
