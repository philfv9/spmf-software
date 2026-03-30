package ca.pfv.spmf.algorithms.frequentpatterns.sppgrowth;

import java.util.List;

public class Itemset implements Comparable<Itemset>{

	// the array of items
	public int[] itemset;
	
	// the support of this itemset
	public int support = 0;
	
	// the maxla of this itemset
	public int maxla = 0;
	
	public int[] getItems() {
		return itemset;
	}
	
	public Itemset() {
		
	}
	
	public Itemset(int item) {
		itemset = new int[] {item};
	}
	
	public Itemset(int [] items) {
		this.itemset = items;
		
	}
	
	public Itemset(List<Integer> itemset, int support, int maxla) {
		
		this.itemset = new int[itemset.size()];
		
		int i = 0;
		for(Integer item: itemset) {
			
			this.itemset[i++] = item.intValue();
		}
		
		this.support = support;
		this.maxla = maxla;
	}
	
	public Itemset(int[] itemset, int support, int maxla) {
		this.itemset = itemset;
		this.support = support;
		this.maxla = maxla;
	}
	
	public int compareTo(Itemset o) {
		
		if (o == this) {
			 return 0;
		}
		
		int compare = this.getAbsoluteSupport() - o.getAbsoluteSupport();
		
		if (compare != 0) {
			
			return compare;
		}
		
		int itemset1sizeA = this.itemset == null ? 0 : this.itemset.length;
		int itemset1sizeB = o.itemset == null ? 0: o.itemset.length;
		int compare2 = itemset1sizeA - itemset1sizeB;
		if (compare2 != 0) {
			return compare2;
		}
		
		int compare3 = this.maxla - o.maxla;
		if (compare3 !=0) {
			return compare3;
		}
		
		return this.hashCode() - o.hashCode();
	}
	
	
	public int getAbsoluteSupport() {
		return support;
	}
	
	public void increaseTransactionCount() {
		this.support++;
	}
}
