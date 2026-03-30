package ca.pfv.spmf.input.utility_transaction_database;

// this class represent an item and its utility in a transaction
public class ItemUtility{
	
	public ItemUtility(int item, int utility) {
		this.item = item;
		this.utility = utility;
	}
	
	public int item;
	public  int utility;
	
	public String toString() {
		return "[" + item + "," + utility + "]";
	}
}