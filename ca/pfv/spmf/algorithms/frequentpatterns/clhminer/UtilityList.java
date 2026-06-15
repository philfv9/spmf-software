package ca.pfv.spmf.algorithms.frequentpatterns.clhminer;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a utility list as used by CLH-Miner
 * @see AlgoCLHMiner
 * 
 * @author Bay Vo et al.
 */
public class UtilityList {

	/** the item */
	Integer item;

	/** the sum of item utilities */
	int sumIutils = 0;

	/** the sum of remaining utilities */
	int sumRutils = 0;

	/** the elements */
	List<Element> elements = new ArrayList<Element>();

	/** the child utility lists */
	List<UtilityList> childs = new ArrayList<UtilityList>();

	/** the global weighted utility */
	int GWU = 0;

	/**
	 * Constructor.
	 * @param item the item that is used for this utility list
	 */
	public UtilityList(Integer item) {
		this.item = item;
	}

	/**
	 * Method to add an element to this utility list and update the sums at the same time.
	 * @param element the element to be added
	 */
	public void addElement(Element element) {
		sumIutils += element.iutils;
		sumRutils += element.rutils;
		GWU += element.TU;
		elements.add(element);
	}

	/**
	 * Get the support of the itemset represented by this utility-list
	 * @return the support as a number of transactions
	 */
	public int getSupport() {
		return elements.size();
	}

	/**
	 * Get the elements of this utility list
	 * @return the elements
	 */
	public List<Element> getElement() {
		return elements;
	}

	/**
	 * Get the child utility lists
	 * @return the child utility lists
	 */
	public List<UtilityList> getChild() {
		return childs;
	}

	/**
	 * Add a child utility list
	 * @param uLs the child utility list
	 */
	public void addChild(UtilityList uLs) {
		childs.add(uLs);
	}
}