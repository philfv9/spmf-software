package ca.pfv.spmf.algorithms.frequentpatterns.fuimtf;


/**
 * This is an implementation of an Item as used by the mHUIMiner algorithm.
 * The code is under the GPLv3 license.
 * 
 * @see AlgoFUIMTF
 * @author Prashant Barhate, modified by Alex Peng
 * 
 */

public class Item {

	/** the id of the item */
	private int itemID = 0;
	
	/** a utility value */
	private int utility = 0;

	/**
	 * Constructor
	 * @param id the id of the item
	 * @param utility a utility value
	 */
	public Item(int id, int utility) {
		this.itemID = id;
		this.utility = utility;

	}

	/**
	 * method to get utility
	 */
	public int getUtility() {
		return utility;
	}

	/**
	 * method to get the id of the item
	 */
	public int getItemID() {
		return itemID;
	}
}
