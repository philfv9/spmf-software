package ca.pfv.spmf.algorithms.frequentpatterns.emsfui_d;

/**
 * Implementation of a utility list element as used by the EMSFUI_D algorithm.
 * Obtained from Github liuxuan615 under the GPL v3 license
 * as it contains derived code from SPMF, which is under the GPL.
 */
class Element {
	// The three variables as described in the paper:
	/** transaction id */
	final int tid ;   
	/** itemset utility */
	final int iutils; 
	/** remaining utility */
	final int rutils; 
	
	/**
	 * Constructor.
	 * @param tid  the transaction id
	 * @param iutils  the itemset utility
	 * @param rutils  the remaining utility
	 */
	public Element(int tid, int iutils, int rutils){
		this.tid = tid;
		this.iutils = iutils;
		this.rutils = rutils;
	}
}

