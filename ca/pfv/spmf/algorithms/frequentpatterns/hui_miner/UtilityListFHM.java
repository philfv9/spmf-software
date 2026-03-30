package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.datastructures.collections.list.ArrayListObject;
import ca.pfv.spmf.datastructures.collections.list.ListObject;

/**
 * This class represents a UtilityList as used by the HUI-Miner algorithm.
 *
 * @see AlgoHUIMiner
 * @see Element
 * @author Philippe Fournier-Viger
 */
public class UtilityListFHM {
	 public Integer item;  // the item
	 public long sumIutils = 0;  // the sum of item utilities
	 public long sumRutils = 0;  // the sum of remaining utilities
	 public ListObject<Element> elements = new ArrayListObject<Element>();  // the elements
	 
	/**
	 * Constructor.
	 * @param item the item that is used for this utility list
	 */
	public UtilityListFHM(Integer item){
		this.item = item;
	}
	
	/**
	 * Method to add an element to this utility list and update the sums at the same time.
	 */
	public void addElement(Element element){
		sumIutils += element.iutils;
		sumRutils += element.rutils;
		elements.add(element);
	}
	
	/**
	 * Get the support of the itemset represented by this utility-list
	 * @return the support as a number of trnsactions
	 */
	public int getSupport() {
		return elements.size();
	}
	
	/** Get the sum of iutil values
	 * 
	 * @return the sum
	 */
	public long getUtils(){
		return this.sumIutils;
	}
}
