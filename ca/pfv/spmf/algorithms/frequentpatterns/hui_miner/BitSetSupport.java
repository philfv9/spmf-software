package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

import java.util.BitSet;

/**
 * Class to store a bitset and its cardinality
 * (an itemset's tidset and its support).
 * Storing the cardinality is useful because the cardinality() method
 * of a bitset in Java is very expensive, so it should not be called
 * more than once.
 */ 
public class BitSetSupport{
	/** the bitset */
	public BitSet bitset = new BitSet();
	/** the support */
	public int support;
	
	/**
	 * Get the bitset
	 * @return the biset
	 */
	public BitSet getBitset() {
		return bitset;
	}
}