package ca.pfv.spmf.algorithms.frequentpatterns.carpenter;

/**
 * A transaction-id list for an item, as used by the Carpenter algorithm
 * @author Philippe Fournier-Viger
 */
final class TIDList {
	/** an item */
	final int item;
	/** the item support */
	int support;
	/** the list of transaction IDs */
	final int[] tids;


	/** Constructor */
	TIDList(int item, int support, int[] tids) {
		this.item = item;
		this.support = support;
		this.tids = tids;
	}
	
	@Override
	public String toString() {
	    return "item=" + item + ", sup=" + support + ", tids=" + java.util.Arrays.toString(tids);
	}
}