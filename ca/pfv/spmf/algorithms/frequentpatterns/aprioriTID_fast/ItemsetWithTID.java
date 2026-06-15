package ca.pfv.spmf.algorithms.frequentpatterns.aprioriTID_fast;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
 *
 *  This file is part of the SPMF DATA MINING SOFTWARE
 *  (http://www.philippe-fournier-viger.com/spmf).
 *  SPMF is free software: you can redistribute it and/or modify it under the
 *  terms of the GNU General Public License as published by the Free Software
 *  Foundation, either version 3 of the License, or (at your option) any later
 *  version.
 *  SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 *  A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with
 *  SPMF. If not, see http://www.gnu.org/licenses/.
 */

/**
 * Internal representation of an itemset with its tidset stored in either dense
 * (long[] bitset) or sparse (int[] list) form. Only one of them is non-null.
 * <br/>
 * <br/>
 * Dense representation is used when support is greater than a sparseThreshold
 * (the number of long words needed for the full bitset). Sparse representation
 * is used otherwise. <br/>
 * <br/>
 * This class is shared across all optimized versions of AprioriTID-based
 * algorithms.
 *
 * @author Philippe Fournier-Viger
 */
public final class ItemsetWithTID {

	/** The items in this itemset, sorted in ascending order */
	public final int[] items;

	/**
	 * Dense tidset: raw long array of uniform length (wordCount). Non-null only
	 * when using dense (bitset) representation.
	 */
	public final long[] tidWords;

	/**
	 * Sparse tidset: sorted array of transaction IDs. Non-null only when using
	 * sparse (list) representation.
	 */
	public final int[] tidList;

	/** The support (number of transactions containing this itemset) */
	public final int support;

	/**
	 * Constructor for dense (bitset) representation.
	 *
	 * @param items    items in the itemset (sorted ascending)
	 * @param tidWords tidset as a long array of uniform length
	 * @param support  the support of the itemset
	 */
	public ItemsetWithTID(int[] items, long[] tidWords, int support) {
		this.items = items;
		this.tidWords = tidWords;
		this.tidList = null;
		this.support = support;
	}

	/**
	 * Constructor for sparse (sorted int list) representation. Support is derived
	 * from the list length.
	 *
	 * @param items   items in the itemset (sorted ascending)
	 * @param tidList sorted array of transaction IDs
	 */
	public ItemsetWithTID(int[] items, int[] tidList) {
		this.items = items;
		this.tidWords = null;
		this.tidList = tidList;
		this.support = tidList.length;
	}

	/**
	 * @return true if this itemset uses dense (bitset) representation
	 */
	public boolean isDense() {
		return tidWords != null;
	}
}