package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.Pattern;
/*
 * Copyright Antonio Gomariz Peñalver 2013
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */
/**
 * Implementation of a Idlist for SPADE and SPAM. This IdList
 * is based on a big bitmap and it codes all the sequences by means of a bitset
 * and a list of bitsets. In the first only set we keep the sequence identifiers
 * of all the sequences where the pattern appears. For each sequence we have an
 * itemset. Therefore, if we have a bitset with 512 bits, we also have a list of
 * 512 bitsets, where each bitset codes an itemset.
 *
 * @author agomariz
 */
public class IDListFatBitmap implements IDList {

	/**
	 * Initial capacity hint (in bits) for the "sequences" BitSet and (in
	 * elements) for the "itemsetsOfSequences" ArrayList.
	 */
	private static final int INITIAL_CAPACITY_HINT = 512;

	/**
	 * Bitset that codes all the sequences where the pattern associated with this
	 * bitmap appears.
	 */
	private BitSet sequences;

	/**
	 * List that codes all the itemsets of this bitmap. We have as many itemsets
	 * as sequences exist; entry i holds the itemset bitset for sequence i, or
	 * null if sequence i is not present in this id-list.
	 */
	private List<BitSet> itemsetsOfSequences;

	/**
	 * Support corresponding to the pattern associated to this IdList. 
	 */
	private int support;

	/**
	 * Standard constructor for a bitmap.
	 */
	public IDListFatBitmap() {
		super();
		sequences = new BitSet(INITIAL_CAPACITY_HINT);
		itemsetsOfSequences = new ArrayList<BitSet>(INITIAL_CAPACITY_HINT);
	}

	/**
	 * It adds an appearance of the pattern in the sequence, denoted by sid,
	 * and the itemset, denoted by tid.
	 *
	 * @param sid The sequence identifier where the pattern appears
	 * @param tid The itemset where the pattern appears
	 */
	public void registerBit(int sid, int tid) {
		// insertInSequence() increments support and pads itemsetsOfSequences
		// only the first time this sid is seen, keeping both O(1) amortized.
		insertInSequence(sid);
		BitSet itemsetsFromSequence = itemsetsOfSequences.get(sid);
		if (itemsetsFromSequence == null) {
			itemsetsFromSequence = new BitSet();
			itemsetsOfSequences.set(sid, itemsetsFromSequence);
		}
		itemsetsFromSequence.set(tid);
	}

	/**
	 * It adds all the appearances of the pattern in the sequence, denoted by
	 * sid, and all the itemset appearances, denoted by tids.
	 *
	 * @param sid  The sequence identifier where the pattern appears
	 * @param tids The itemsets where the pattern appears
	 */
	public void registerNBits(int sid, List<Integer> tids) {
		insertInSequence(sid);
		BitSet itemsetsFromSequence = itemsetsOfSequences.get(sid);
		if (itemsetsFromSequence == null) {
			itemsetsFromSequence = new BitSet();
			itemsetsOfSequences.set(sid, itemsetsFromSequence);
		}
		int size = tids.size();
		for (int i = 0; i < size; i++) {
			itemsetsFromSequence.set(tids.get(i));
		}
	}

	/**
	 * It gets the number of sequences the IdList is active, so the pattern
	 * does appear.
	 *
	 * @return the number of sequences
	 */
	@Override
	public int getSupport() {
		return support;
	}

	/**
	 * Get the string representation of this IdList.
	 *
	 * @return the string
	 */
	@Override
	public String toString() {
		return "";
	}

	/**
	 * It returns the intersection IdList that results from the current object
	 * and the IdList given as an argument.
	 *
	 * @param idList     IdList with which we join the current IdList.
	 * @param equals     Flag indicating if we want an intersection for equal
	 *                   relation, or, if false, an after relation.
	 * @param minSupport Minimum absolute support.
	 * @return the intersection
	 */
	@Override
	public IDList join(IDList idList, boolean equals, int minSupport) {
		// Reuse a pooled IDListFatBitmap instead of always allocating a fresh
		// BitSet and ArrayList.
		IDListFatBitmap result = IDListFatBitmapPool.acquire();
		IDListFatBitmap idStandard = (IDListFatBitmap) idList;
		BitSet sequencesIdList = idStandard.sequences;
		List<BitSet> itemsetsIdList = idStandard.itemsetsOfSequences;
		if (equals) {
			equalLoop(result, sequencesIdList, itemsetsIdList, minSupport);
		} else {
			laterLoop(result, sequencesIdList, itemsetsIdList, minSupport);
		}
		return result;
	}

	/**
	 * Setter method to insert in the pattern given as parameter the set of
	 * sequence identifiers where the IdList appears.
	 *
	 * @param pattern Pattern where we insert the sid list
	 */
	@Override
	public void setAppearingSequences(Pattern pattern) {
		pattern.setAppearingIn(sequences);
	}

	/** {@inheritDoc} */
	@Override
	public void clear() {
	}

	/**
	 * Clears this id-list's internal bitset, itemset list and support so that
	 * the instance can be safely reused by IDListFatBitmapPool instead of
	 * being reallocated from scratch. Must only be called once this id-list is
	 * no longer referenced by any Pattern or EquivalenceClass.
	 */
	void reset() {
		sequences.clear();
		itemsetsOfSequences.clear();
		support = 0;
	}

	/**
	 * Returns this id-list to the shared per-thread IDListFatBitmapPool for
	 * reuse by a subsequent join call.
	 */
	@Override
	public void recycle() {
		IDListFatBitmapPool.release(this);
	}

	/**
	 * It adds, for a particular sequence, all the appearances given by the
	 * list of itemsets.
	 *
	 * @param sequence Sequence id where the itemsets will be inserted
	 * @param itemsets Set of itemsets to insert in a sequence
	 */
	public void addAppearancesInSequence(int sequence, List<Integer> itemsets) {
		registerNBits(sequence, itemsets);
	}

	/**
	 * Executes a join operation under the equal relation for two sets of
	 * appearances that correspond to the same sequence in two different
	 * patterns.
	 *
	 * @param thisBitmap  Set of appearances of the current IdList
	 * @param otherBitmap Set of appearances of the given IdList
	 * @return The resulting bitmap, or null if empty
	 */
	private BitSet equalOperation(BitSet thisBitmap, BitSet otherBitmap) {
		if (thisBitmap == null || !thisBitmap.intersects(otherBitmap)) {
			return null;
		}
		BitSet result = (BitSet) thisBitmap.clone();
		result.and(otherBitmap);
		return result;
	}

	/**
	 * Executes a join operation under the after relation for two sets of
	 * appearances that correspond to the same sequence in two different
	 * patterns.
	 *
	 * @param thisBitmap  Set of appearances of the current IdList
	 * @param otherBitmap Set of appearances of the given IdList
	 * @return The resulting bitmap, or null if empty
	 */
	private BitSet greaterThanOperation(BitSet thisBitmap, BitSet otherBitmap) {
		if (thisBitmap == null) {
			return null;
		}
		int index = thisBitmap.nextSetBit(0);
		if (index < 0 || index >= otherBitmap.length() - 1) {
			return null;
		}
		int newIndex = index + 1;
		BitSet result = (BitSet) otherBitmap.clone();
		result.clear(0, newIndex);
		return result.nextSetBit(newIndex) >= 0 ? result : null;
	}

	/**
	 * Method to do the join operation under equal relation.
	 *
	 * @param newIdList           IdList where we put the new elements resulting
	 *                            from the join
	 * @param sequencesFromIdList Sequence bitset of the IdList we are joining
	 *                            against
	 * @param itemsetsFromIdList  Itemset bitsets of the IdList we are joining
	 *                            against
	 * @param minSupport          Minimum absolute support
	 */
	private void equalLoop(IDListFatBitmap newIdList, BitSet sequencesFromIdList,
			List<BitSet> itemsetsFromIdList, int minSupport) {
		List<BitSet> itemsetIntersection = newIdList.getItemsets();
		// Reuse the pooled (already-cleared) sequences bitset of newIdList
		// directly.
		BitSet sequencesIntersection = newIdList.sequences;
		sequencesIntersection.or(sequences);
		sequencesIntersection.and(sequencesFromIdList);
		setSize(itemsetIntersection, sequencesIntersection.length());
		newIdList.setSupport(sequencesIntersection.cardinality());
		if (newIdList.getSupport() >= minSupport) {
			for (int i = sequencesIntersection.nextSetBit(0); i >= 0;
					i = sequencesIntersection.nextSetBit(i + 1)) {
				BitSet otherItemset = itemsetsFromIdList.get(i);
				BitSet thisItemset = itemsetsOfSequences.get(i);
				if (thisItemset != null) {
					BitSet equalResult = equalOperation(thisItemset, otherItemset);
					if (equalResult != null) {
						itemsetIntersection.set(i, equalResult);
					} else {
						sequencesIntersection.clear(i);
						newIdList.decreaseSupport();
					}
				}
			}
		}
	}

	/**
	 * Method to do the join operation under an after relation.
	 *
	 * @param newIdList           IdList where we put the new elements resulting
	 *                            from the join
	 * @param sequencesFromIdList Sequence bitset of the IdList we are joining
	 *                            against
	 * @param itemsetsFromIdList  Itemset bitsets of the IdList we are joining
	 *                            against
	 * @param minSupport          Minimum absolute support
	 */
	private void laterLoop(IDListFatBitmap newIdList, BitSet sequencesFromIdList,
			List<BitSet> itemsetsFromIdList, int minSupport) {
		List<BitSet> itemsetIntersection = newIdList.getItemsets();
		BitSet sequenceIntersection = newIdList.sequences;
		sequenceIntersection.or(sequences);
		sequenceIntersection.and(sequencesFromIdList);
		setSize(itemsetIntersection, sequenceIntersection.length());
		newIdList.setSupport(sequenceIntersection.cardinality());
		if (newIdList.getSupport() >= minSupport) {
			for (int i = sequenceIntersection.nextSetBit(0); i >= 0;
					i = sequenceIntersection.nextSetBit(i + 1)) {
				BitSet otherItemset = itemsetsFromIdList.get(i);
				BitSet thisItemset = itemsetsOfSequences.get(i);
				if (thisItemset != null) {
					BitSet greaterThanResult = greaterThanOperation(thisItemset, otherItemset);
					if (greaterThanResult != null) {
						itemsetIntersection.set(i, greaterThanResult);
					} else {
						sequenceIntersection.clear(i);
						newIdList.decreaseSupport();
					}
				}
			}
		}
	}

	/**
	 * Returns the list of itemset bitsets of the current IdList.
	 *
	 * @return the itemsets list
	 */
	private List<BitSet> getItemsets() {
		return this.itemsetsOfSequences;
	}

	private void setSupport(int support) {
		this.support = support;
	}

	/**
	 * It decreases the support associated with the IdList.
	 */
	private void decreaseSupport() {
		this.support--;
	}

	/**
	 * Inserts the sequence identifier given as parameter into the set of
	 * sequences, padding itemsetsOfSequences with nulls up to sid if needed.
	 *
	 * @param sid the sequence identifier to insert
	 */
	private void insertInSequence(int sid) {
		if (!sequences.get(sid)) {
			sequences.set(sid);
			support++;
			int currentSize = itemsetsOfSequences.size();
			int needed = sid + 1;
			if (currentSize < needed) {
				itemsetsOfSequences.addAll(
						java.util.Collections.<BitSet>nCopies(needed - currentSize, null));
			}
		}
	}

	/**
	 * Adjusts the list of itemset bitsets to the given length.
	 *
	 * @param list   list of itemset bitsets to adjust
	 * @param length the target size
	 */
	private void setSize(List<BitSet> list, int length) {
		int currentSize = list.size();
		if (currentSize > length) {
			list.subList(length, currentSize).clear();
		} else if (currentSize < length) {
			list.addAll(java.util.Collections.<BitSet>nCopies(length - currentSize, null));
		}
	}
}