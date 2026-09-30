package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Implementation of a Idlist for SPADE and SPAM. This IdList is based on a hash
 * map of entries <Integer, Bitset>, and it makes a correspondence between a
 * sid, denoted by the Integer, with the appearances of the pattern in that
 * sequence, denoted by the bitset. In that bitset we will have one bit set to 1
 * if in that itemset, an appearance of the pattern can be found. In order to
 * make the join operation, we will do it entry by entry, for those entries
 * shared by two sequences.
 *
 * @author agomariz
 */
public class IDListBitmap implements IDList {

	/**
	 * The map where we keep the appearances of a pattern in a sequence; integer is
	 * sequence id, bitset codes itemsets.
	 */
	Map<Integer, BitSet> sequence_ItemsetEntries;

	/**
	 * A bitset to keep just the sequences where a pattern appears; bitset
	 * representation of the keyset of sequence_ItemsetEntries.
	 */
	BitSet sequences;

	/**
	 * Standard constructor that creates an empty IdList.
	 */
	public IDListBitmap() {
		super();
		sequence_ItemsetEntries = new HashMap<Integer, BitSet>();
		sequences = new BitSet();
	}

	/**
	 * Adds the appearance of the pattern in the given itemset and sequence.
	 *
	 * @param sid The sequence identifier where the pattern appears
	 * @param tid The itemset timestamp where the pattern appears
	 */
	public void registerBit(int sid, int tid) {
		BitSet bitmap = sequence_ItemsetEntries.get(sid);
		if (bitmap == null) {
			bitmap = new BitSet();
			sequence_ItemsetEntries.put(sid, bitmap);
			sequences.set(sid);
		}
		bitmap.set(tid);
	}

	/**
	 * Adds the appearances of the pattern in the itemsets contained in tids for the
	 * given sequence.
	 *
	 * @param sid  The sequence identifier where the pattern appears
	 * @param tids The set of itemset timestamps where the pattern appears
	 */
	public void registerNBits(int sid, List<Integer> tids) {
		BitSet bitmap = sequence_ItemsetEntries.get(sid);
		if (bitmap == null) {
			bitmap = new BitSet();
			sequence_ItemsetEntries.put(sid, bitmap);
			sequences.set(sid);
		}
		int size = tids.size();
		for (int i = 0; i < size; i++) {
			bitmap.set(tids.get(i));
		}
	}

	/**
	 * Returns the number of sequences where the IdList is active.
	 *
	 * @return the number of sequences
	 */
	@Override
	public int getSupport() {
		return sequences.cardinality();
	}

	/**
	 * Returns the string representation of this kind of IdList.
	 *
	 * @return the string representation
	 */
	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (Map.Entry<Integer, BitSet> entry : sequence_ItemsetEntries.entrySet()) {
			int sid = entry.getKey();
			BitSet bitmap = entry.getValue();
			for (int bit = bitmap.nextSetBit(0); bit >= 0; bit = bitmap.nextSetBit(bit + 1)) {
				buffer.append("[sid=");
				buffer.append(sid);
				buffer.append(" tid=");
				buffer.append(bit);
				buffer.append("]");
			}
		}
		return buffer.toString();
	}

	/**
	 * Returns the intersection IdList resulting from joining the current object
	 * with the given IdList.
	 *
	 * @param idList     IdList with which we join the current IdList
	 * @param equals     Flag indicating if we want a intersection for equal
	 *                   relation, or after relation if false
	 * @param minSupport Minimum relative support
	 * @return the resulting idlist
	 */
	@Override
	public IDList join(IDList idList, boolean equals, int minSupport) {
		IDListBitmap idStandard = (IDListBitmap) idList;
		Map<Integer, BitSet> idListMap = idStandard.sequence_ItemsetEntries;

		// Reuse a pooled IDListBitmap instead of always allocating a fresh HashMap
		// and BitSet.
		IDListBitmap output = IDListBitmapPool.acquire();
		Map<Integer, BitSet> intersection = output.sequence_ItemsetEntries;
		BitSet newSequences = output.sequences;

		if (equals) {
			equalLoop(intersection, idListMap, newSequences);
		} else {
			laterLoop(intersection, idListMap, newSequences);
		}
		return output;
	}

	/**
	 * Does the join operation under equal relation, populating
	 * sequenceItemsetEntries with results. Iterates whichever of the two operand
	 * maps has fewer entries (the AND relation is symmetric, so it does not matter
	 * which side ends up being treated as "this"/"other" below).
	 *
	 * @param sequenceItemsetEntries Map where we put the new elements resulting
	 *                               from the join method
	 * @param idListMap              Map with which we are going to join the current
	 *                               IdList
	 * @param sequences              New bitset where we keep the sequences where
	 *                               the new IdList is active
	 */
	private void equalLoop(Map<Integer, BitSet> sequenceItemsetEntries, Map<Integer, BitSet> idListMap,
			BitSet sequences) {
		boolean iterateThis = this.sequence_ItemsetEntries.size() <= idListMap.size();
		Map<Integer, BitSet> mapToIterate = iterateThis ? this.sequence_ItemsetEntries : idListMap;
		Map<Integer, BitSet> mapToLookup = iterateThis ? idListMap : this.sequence_ItemsetEntries;

		for (Map.Entry<Integer, BitSet> entry : mapToIterate.entrySet()) {
			int sid = entry.getKey();
			BitSet a = entry.getValue();
			BitSet b = mapToLookup.get(sid);
			if (b != null) {
				BitSet equalResult = equalOperation(a, b);
				if (equalResult != null) {
					sequenceItemsetEntries.put(sid, equalResult);
					sequences.set(sid);
				}
			}
		}
	}

	/**
	 * Does the join operation under after relation, populating
	 * sequenceItemsetEntries with results. Iterates whichever of the two operand
	 * maps has fewer entries, same reasoning as equalLoop() above. Unlike the equal
	 * relation, the after relation is NOT symmetric: greaterThanOperation() must
	 * always receive thisBitmap (this object, the earlier pattern) as its first
	 * argument and otherBitmap (the join() parameter, the later pattern) as its
	 * second, regardless of which of the two maps ends up being the one iterated
	 * over.
	 *
	 * @param sequenceItemsetEntries Map where we put the new elements resulting
	 *                               from the join method
	 * @param idListMap              Map with which we are going to join the current
	 *                               IdList
	 * @param sequences              New bitset where we keep the sequences where
	 *                               the new IdList is active
	 */
	private void laterLoop(Map<Integer, BitSet> sequenceItemsetEntries, Map<Integer, BitSet> idListMap,
			BitSet sequences) {
		boolean iterateThis = this.sequence_ItemsetEntries.size() <= idListMap.size();
		Map<Integer, BitSet> mapToIterate = iterateThis ? this.sequence_ItemsetEntries : idListMap;

		for (Map.Entry<Integer, BitSet> entry : mapToIterate.entrySet()) {
			int sid = entry.getKey();
			BitSet thisIdList;
			BitSet otherIdList;
			if (iterateThis) {
				thisIdList = entry.getValue();
				otherIdList = idListMap.get(sid);
			} else {
				otherIdList = entry.getValue();
				thisIdList = this.sequence_ItemsetEntries.get(sid);
			}
			if (thisIdList != null && otherIdList != null) {
				BitSet greaterThanResult = greaterThanOperation(thisIdList, otherIdList);
				if (greaterThanResult != null) {
					sequenceItemsetEntries.put(sid, greaterThanResult);
					sequences.set(sid);
				}
			}
		}
	}

	/**
	 * Inserts in the pattern the set of sequence identifiers where the IdList
	 * appears.
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
	 * Clears this id-list's internal map and bitset so that the instance can be
	 * safely reused by IDListBitmapPool instead of being reallocated from scratch.
	 * Must only be called once this id-list is no longer referenced by any Pattern
	 * or EquivalenceClass.
	 */
	void reset() {
		sequence_ItemsetEntries.clear();
		sequences.clear();
	}

	/**
	 * Returns this id-list to the shared per-thread IDListBitmapPool for reuse by a
	 * subsequent join call.
	 */
	@Override
	public void recycle() {
		IDListBitmapPool.release(this);
	}

	/**
	 * Adds for a particular sequence all the appearances given by the list of
	 * itemsets. Takes a primitive int sid (instead of Integer) since this is called
	 * once per sequence while loading the database.
	 *
	 * @param sid      Sequence id where the itemsets will be inserted
	 * @param itemsets Set of itemsets to insert in a sequence
	 */
	public void addAppearancesInSequence(int sid, List<Integer> itemsets) {
		registerNBits(sid, itemsets);
	}

	/**
	 * Returns the map of entries mapping sequence ids to bitsets.
	 *
	 * @return the map of entries
	 */
	public Map<Integer, BitSet> getSecuenceItemsetEntries() {
		return sequence_ItemsetEntries;
	}

	/**
	 * Sets the map of entries mapping sequence ids to bitsets.
	 *
	 * @param sequenceItemsetEntries the map of entries to set
	 */
	public void setSequenceItemsetEntries(Map<Integer, BitSet> sequenceItemsetEntries) {
		this.sequence_ItemsetEntries = sequenceItemsetEntries;
	}

	/**
	 * Executes a join operation under the equal relation for two sets of
	 * appearances in the same sequence.
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
	 * appearances in the same sequence.
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
}