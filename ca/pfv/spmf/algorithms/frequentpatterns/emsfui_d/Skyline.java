package ca.pfv.spmf.algorithms.frequentpatterns.emsfui_d;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a skyline as used by the EMSFUI_D algorithm.
 * Obtained from Github liuxuan615 under the GPL v3 license
 * as it contains derived code from SPMF, which is under the GPL.
 */
public class Skyline {
	String itemSet ;  //the itemset	
	int frequent; 	//the frequency of itemset
	int utility; //the utility of itemset
}

class SkylineList{
	//skylinelist store different itemsets that have same frequency and same utility.
	List<Skyline> skylinelist= new ArrayList<Skyline>();

	public Skyline get(int index) {
		return skylinelist.get(index);
	}

	public void add(Skyline e) {
		skylinelist.add(e);
	}
	
	public void remove(int index) {
		skylinelist.remove(index);
	}
	
	public int size(){
		return skylinelist.size();
	}
}

