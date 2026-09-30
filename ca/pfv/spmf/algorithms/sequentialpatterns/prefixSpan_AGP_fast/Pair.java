package ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast;

import java.util.BitSet;
/*
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * This class is used by PrefixSpan and it is based on SPMF original PrefixSpan
 * Algorithm. It represents, based on Hirate & Yamana a pair of an Item and its
 * occurrences in the projected database. It is used for calculating the support
 * of item in a database.
 *
 * Copyright Antonio Gomariz Peñalver 2013
 *
 * @author agomariz
 */

public class Pair implements Comparable<Pair> {

	/**
	 * Flag indicating if the item appears in the same itemset that one from the
	 * projection was done.
	 */
	private final boolean postfix;
	/**
	 * Item of the pair.
	 */
	private final int item;
	/**
	 * List of the sequences that contains this one.
	 */
	private BitSet sequencesID = new BitSet();

	/**
	 * Standard constructor
	 * 
	 * @param postfix flag indicating if it is an item appearing in the same itemset
	 *                as that one from the projection was done
	 * @param item    item appearing after the item of the projection
	 */
	public Pair(boolean postfix, int item) {
		this.postfix = postfix;
		this.item = item;
	}

	/**
	 * Check if this pair is equal to another
	 * 
	 * @param arg the other pair
	 * @return true if equal, otherwise false.
	 */
	@Override
	public boolean equals(Object arg) {
		Pair p = (Pair) arg;
		if ((p.item == this.item) && (p.postfix == this.postfix)) {
			return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 59 * hash + (this.postfix ? 1 : 0);
		hash = 59 * hash + this.item;
		return hash;
	}

	public boolean isPostfix() {
		return postfix;
	}

	public int getItem() {
		return item;
	}

	/**
	 * It returns the number of sequences where the pair appears.
	 * 
	 * @return the number of sequences
	 */
	public int getSupport() {
		return sequencesID.cardinality();
	}

	/**
	 * It returns the list of the sequences where the pair appears
	 * 
	 * @return the list of sequences as a bitset.
	 */
	public BitSet getSequencesID() {
		return sequencesID;
	}

	public void setSequencesID(BitSet sequencesID) {
		this.sequencesID = sequencesID;
	}

	@Override
	public int compareTo(Pair arg) {
		int comparisonItems = -Integer.compare(item, arg.item);
		if (comparisonItems != 0) {
			return comparisonItems;
		}
		if (this.postfix == arg.postfix) {
			return 0;
		}
		return this.postfix ? -1 : 1;
	}

	@Override
	public String toString() {
		String post = "";
		if (postfix) {
			post = "*";
		}
		String relation = postfix ? "" : " ->";
		return (post + relation + " " + item + "[" + sequencesID.size() + "]");
	}
}