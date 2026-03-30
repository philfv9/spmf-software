package ca.pfv.spmf.algorithms.frequentpatterns.etauim;

/**
 * Implementation a utility list element as used by the ETAUIM algorithm.
 * Obtained from Github liuxuan615 under the GPL v3 license
 * as it contains derived code from SPMF, which is under the GPL.
 */
class Element {
	// The five variables as described in the paper:
	/** transaction id */
	final int tid ;   
	/** itemset utility */
	final double iutils; 
	/** remaining utility */
	final int remu;
	/**remaining items number*/
	final int rn;
	final double rmu;
	
	
	/**
	 * Constructor.
	 * @param tid  the transaction id
	 * @param iutils  the itemset utility
	 * @param rutils  the remaining utility
	 */
	public Element(int tid, double iutils, int remu, int rn,double rmu){
		this.tid = tid;
		this.iutils = iutils;
		this.remu = remu;
		this.rn = rn;	
		this.rmu=rmu;
	}
}

