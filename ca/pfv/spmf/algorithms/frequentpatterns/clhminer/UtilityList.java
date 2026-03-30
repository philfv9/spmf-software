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
	Integer item;  // the item
	int sumIutils = 0;  // the sum of item utilities
	int sumRutils = 0;  // the sum of remaining utilities
	 List<Element> elements = new ArrayList<Element>();  // the elements
	 List<UtilityList> childs = new ArrayList<UtilityList>();
	 int GWU = 0;
	/**
	 * Constructor.
	 * @param item the item that is used for this utility list
	 */
	public UtilityList(Integer item){
		this.item = item;
	}
	
	/**
	 * Method to add an element to this utility list and update the sums at the same time.
	 */
	public void addElement(Element element){
		sumIutils += element.iutils;
		sumRutils += element.rutils;
		elements.add(element);
		GWU+=element.TU;
	}
	
	/**
	 * Get the support of the itemset represented by this utility-list
	 * @return the support as a number of trnsactions
	 */
	public int getSupport() {
		return elements.size();
	}
	
	public List<Element> getElement(){
		return elements;
	}
	public List<UtilityList> getChild(){
		return childs;
	}
	public void AddChild(UtilityList uLs) {	
		this.childs.add(uLs);
	}
}
