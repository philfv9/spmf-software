package ca.pfv.spmf.algorithms.classifiers.general;

/**
 * Class representing an item and its support.
 * Used by some Apriori algorithms like ACN and ACAC
 */
public class Item {
	/** an item */
	public short item;
	/** the item support */
	public long support;

	/**
	 * Constructor
	 * @param item the item
	 * @param support its support
	 */
	public Item(short item, long support) {
		this.item = item;
		this.support = support;
	}
}