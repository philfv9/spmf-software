package ca.pfv.spmf.algorithms.frequentpatterns.etauim;

/**
 * Implementation of an itemset as used by the ETAUIM algorithm.
 * Obtained from Github liuxuan615 under the GPL v3 license
 * as it contains derived code from SPMF, which is under the GPL.
 */
public class Itemset implements Comparable<Itemset>{

	public int[] itemset; 
	public double utility; // absolute support
	
	public int[] getItemset() {
		return itemset;
	}


	
	public int compareTo(Itemset o) {
		if(o == this){
			return 0;
		}
		Double au2 = o.utility/(double)o.itemset.length;
		Double au1 = this.utility/(double)this.itemset.length;
		int compare = au1.compareTo(au2);
		if(compare > 0){
			return 1;
		}
		if(compare < 0){
			return -1;
		}
		return 0;
	}
		
	public Itemset(int[] itemset, double utility){
		this.itemset = itemset;
		this.utility = utility;
	}


	public String toString() {
		StringBuffer temp = new StringBuffer();
		for(int item : itemset){
			temp.append(item + ",");
		}
		return temp.toString();
	}
}
