package ca.pfv.spmf.algorithms.frequentpatterns.tko;


public class ItemsetTKO implements Comparable<ItemsetTKO>{

	int[] itemset; 
	int item;
	long utility; // absolute support
	
	public int[] getItemset() {
		return itemset;
	}

	public int getItem() {
		return item;
	}

		
	public ItemsetTKO(int[] itemset, int item, long utility){
		this.itemset = itemset;
		this.item = item;
		this.utility = utility;
	}

	public int compareTo(ItemsetTKO o) {
		if(o == this){
			return 0;
		}
		long compare =  this.utility - o.utility;
		if(compare > 0){
			return 1;
		}
		if(compare < 0){
			return -1;
		}
		return 0;
	}

	public String toString() {
		StringBuffer temp = new StringBuffer();
		for(int item : itemset){
			temp.append(item + ",");
		}
		temp.append(item);
		return temp.toString();
	}
}
