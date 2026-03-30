package ca.pfv.spmf.algorithms.frequentpatterns.HMiner_CLosed;

import java.util.Arrays;
import java.util.HashSet;

/**
 * This class is an itemset as used by the HMinerClosed algorithm
 * <br/>
 * The code was obtained from Github repository of user "limuhangk" under the GPL license (since
 * the code is derived from GPL code from SPMF).
 * @see AlgoHMiner_Closed
 * */
public class Itemset {
	public int[] itemset;
	long utility;
	int support;

	public Itemset(int[] itemset, long utility, int support) {
		this.itemset = itemset;
		this.utility = utility;
		this.support = support;
	}
	/**
	 * Get the items as array
	 * @return the items
	 */
	public int[] getItems() {
		return itemset;
	}

	/**
	 * Get the utility of this itemset
	 */
	public double getUtility(){
		return utility;
	}
	/**
	 * Get the size of this itemset
	 */
	public int size() {
		return itemset.length;
	}

	public Integer get(int position) {
		return itemset[position];
	}

	public boolean contains(Itemset itemset2){
		HashSet<Integer> hashset = new HashSet<>();
		for (int i = 0; i < this.itemset.length; i++) {
			if (!hashset.contains(this.itemset[i]))
				hashset.add(this.itemset[i]);
		}
		for (int j = 0; j < itemset2.itemset.length; j++)
			if (!hashset.contains(itemset2.itemset[j]))
				return false;
		return true;
	}
//	@Override
/*	public String toString() {
		return Arrays.toString(itemset) + " support:  " + support + " utility : " + utility;
	}*/
public String toString() {

	// use a string buffer for more efficiency
	StringBuffer r = new StringBuffer();
	// for each item, append it to the stringbuffer
	for (int i = 0; i < size(); i++) {
		r.append(get(i));
		r.append(' ');
	}
	r.append("#SUP: ");
	r.append(this.support);
	r.append(" #UTIL: ");
	r.append(this.utility);
	return r.toString(); // return the string
}
}
