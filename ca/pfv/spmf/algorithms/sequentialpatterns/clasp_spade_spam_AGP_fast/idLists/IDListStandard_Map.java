package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists;

import java.util.ArrayList;
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
 * map of entries Integer to List of Integer, and it makes a correspondence
 * between a sid, denoted by the Integer, with the appearances of the pattern in
 * that sequence, denoted by the list of Integer. In that list we will have one
 * itemset timestamp where an appearance of the pattern can be found, and is
 * increasingly sorted in the itemset timestamps.
 *
 * In order to make the join operation, we will do it entry by entry, for those
 * entries shared by two sequences.
 * 
 * @author agomariz
 */
public class IDListStandard_Map implements IDList {

	/**
	 * The map where we keep the appearances of a pattern in a sequence; integer is
	 * sequence id, list holds itemset timestamps.
	 */
	Map<Integer, List<Integer>> itemsetSequenceEntries;

	/**
	 * A bitset to keep just the sequences where a pattern appears; bitset
	 * representation of the keyset of itemsetSequenceEntries.
	 */
	java.util.BitSet sequences;

	/**
	 * Standard constructor that creates an empty IdList.
	 */
	public IDListStandard_Map() {
		this.itemsetSequenceEntries = new HashMap<Integer, List<Integer>>();
		this.sequences = new java.util.BitSet();
	}

	/**
	 * Creates an IdList from a map of sequence id to list of itemset timestamps.
	 * "sequences" is populated from the given map's keys so that getSupport() is
	 * correct even if a pre-populated map is passed in, instead of being left
	 * all-zero regardless of the map's actual contents.
	 *
	 * @param itemsetSequenceEntries the map to use
	 */
	public IDListStandard_Map(Map<Integer, List<Integer>> itemsetSequenceEntries) {
		this.itemsetSequenceEntries = itemsetSequenceEntries;
		this.sequences = new java.util.BitSet();
		for (Integer sid : itemsetSequenceEntries.keySet()) {
			this.sequences.set(sid);
		}
	}

	/**
	 * Returns the intersection IdList resulting from joining the current object
	 * with the given IdList. Iterates whichever of the two operand maps has fewer
	 * entries, looking the sid up in the larger one, instead of unconditionally
	 * iterating the parameter's map.
	 *
	 * @param idList     IdList with which we join the current IdList
	 * @param equals     Flag indicating if we want a intersection for equal
	 *                   relation, or after relation if false
	 * @param minSupport Minimum relative support
	 * @return the intersection
	 */
	@Override
	public IDList join(IDList idList, boolean equals, int minSupport) {
		IDListStandard_Map idStandard = (IDListStandard_Map) idList;
		Map<Integer, List<Integer>> idListMap = idStandard.itemsetSequenceEntries;

		// Reuse a pooled IDListStandard_Map instead of always allocating a fresh
		// HashMap and BitSet.
		IDListStandard_Map output = IDListStandardMapPool.acquire();
		Map<Integer, List<Integer>> intersection = output.itemsetSequenceEntries;
		java.util.BitSet newSequences = output.sequences;

		boolean iterateThis = this.itemsetSequenceEntries.size() <= idListMap.size();
		Map<Integer, List<Integer>> mapToIterate = iterateThis ? this.itemsetSequenceEntries : idListMap;
		Map<Integer, List<Integer>> mapToLookup = iterateThis ? idListMap : this.itemsetSequenceEntries;

		for (Map.Entry<Integer, List<Integer>> entry : mapToIterate.entrySet()) {
			int sid = entry.getKey();
			List<Integer> counterpart = mapToLookup.get(sid);
			if (counterpart == null) {
				continue;
			}

			// myList must always be this object's list (the "earlier" pattern)
			// and otherList the join() parameter's list (the "later" pattern),
			// regardless of which of the two maps we ended up iterating over.
			List<Integer> myList = iterateThis ? entry.getValue() : counterpart;
			List<Integer> otherList = iterateThis ? counterpart : entry.getValue();

			List<Integer> transactionAppearances = equals ? equalOperation(myList, otherList)
					: laterOperation(myList, otherList);

			if (transactionAppearances != null) {
				intersection.put(sid, transactionAppearances);
				newSequences.set(sid);
			}
		}
		return output;
	}

	/**
	 * Returns the map that codes the appearances of the pattern in this IdList.
	 *
	 * @return the map of sequence id to list of itemset timestamps
	 */
	public Map<Integer, List<Integer>> getSequenceItemsetEntries() {
		return itemsetSequenceEntries;
	}

	/**
	 * Executes a join operation under the after relation for two sets of
	 * appearances in the same sequence.
	 *
	 * @param myList                           Itemset timestamps of the current
	 *                                         (earlier) IdList for this sequence
	 * @param transactionAppearancesInSequence Itemset timestamps of the parameter
	 *                                         (later) IdList for this sequence
	 * @return The new entry for the new IdList, or null if no result
	 */
	private List<Integer> laterOperation(List<Integer> myList, List<Integer> transactionAppearancesInSequence) {
		if (myList == null || myList.isEmpty()) {
			return null;
		}
		List<Integer> result = new ArrayList<Integer>();

		int firstTid = myList.get(0);
		int index = -1;
		int transSize = transactionAppearancesInSequence.size();
		for (int i = 0; i < transSize && index < 0; i++) {
			if (firstTid < transactionAppearancesInSequence.get(i)) {
				index = i;
			}
		}
		if (index >= 0) {
			for (int i = index; i < transSize; i++) {
				result.add(transactionAppearancesInSequence.get(i));
			}
		}

		if (result.isEmpty()) {
			return null;
		}
		return result;
	}

	/**
	 * Executes a join operation under the equal relation for two sets of
	 * appearances in the same sequence. Takes both operand lists directly, for the
	 * same reason as laterOperation() above. The AND relation is symmetric, so
	 * which list is "myList" vs "transactionAppearancesInSequence" does not affect
	 * correctness here.
	 *
	 * @param myList                           Itemset timestamps of the current
	 *                                         IdList for this sequence
	 * @param transactionAppearancesInSequence Itemset timestamps of the parameter
	 *                                         IdList for this sequence
	 * @return The new entry for the new IdList, or null if no result
	 */
	private List<Integer> equalOperation(List<Integer> myList, List<Integer> transactionAppearancesInSequence) {
		if (myList == null || myList.isEmpty()) {
			return null;
		}
		List<Integer> result = new ArrayList<Integer>();
		int beginningIndex = 0;

		List<Integer> listToExplore, listToSearch;
		if (myList.size() <= transactionAppearancesInSequence.size()) {
			listToExplore = myList;
			listToSearch = transactionAppearancesInSequence;
		} else {
			listToExplore = transactionAppearancesInSequence;
			listToSearch = myList;
		}
		int exploreSize = listToExplore.size();
		int searchSize = listToSearch.size();
		for (int ei = 0; ei < exploreSize; ei++) {
			int eid = listToExplore.get(ei);
			for (int i = beginningIndex; i < searchSize; i++) {
				int val = listToSearch.get(i);
				int comparison = Integer.compare(val, eid);
				if (comparison >= 0) {
					if (comparison == 0) {
						result.add(eid);
						beginningIndex = i + 1;
					}
					break;
				}
			}
		}

		if (result.isEmpty()) {
			return null;
		}
		return result;
	}

	/** {@inheritDoc} */
	@Override
	public int getSupport() {
		return sequences.cardinality();
	}

	/**
	 * Adds an appearance for the given sequence and timestamp in the current
	 * IdList.
	 *
	 * @param sequence  Sequence identifier where the appearance occurs
	 * @param timestamp Itemset timestamp where the appearance occurs
	 */
	public void addAppearance(int sequence, int timestamp) {
		List<Integer> list = itemsetSequenceEntries.get(sequence);
		if (list == null) {
			list = new ArrayList<Integer>();
			itemsetSequenceEntries.put(sequence, list);
			sequences.set(sequence);
		}
		if (list.isEmpty() || list.get(list.size() - 1) != timestamp) {
			list.add(timestamp);
		}
	}

	/**
	 * Adds the appearances for the given sequence and the timestamp list in the
	 * current IdList.
	 *
	 * @param sid      sequence identifier where the appearances occur
	 * @param itemsets Itemset timestamps where the appearances occur
	 */
	public void addAppearancesInSequence(int sid, List<Integer> itemsets) {
		List<Integer> list = itemsetSequenceEntries.get(sid);
		if (list == null) {
			itemsetSequenceEntries.put(sid, itemsets);
		}
		sequences.set(sid);
	}

	/**
	 * Returns the string representation of this IdList.
	 *
	 * @return the string representation
	 */
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		for (Map.Entry<Integer, List<Integer>> entry : itemsetSequenceEntries.entrySet()) {
			result.append("\t").append(entry.getKey()).append(" {");
			List<Integer> eids = entry.getValue();
			int size = eids.size();
			for (int i = 0; i < size; i++) {
				result.append(eids.get(i)).append(",");
			}
			result.deleteCharAt(result.length() - 1);
			result.append("}\n");
		}
		return result.toString();
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

	/**
	 * Clears the attributes of this IdList.
	 */
	@Override
	public void clear() {
		itemsetSequenceEntries.clear();
		sequences.clear();
	}

	/**
	 * Clears this id-list's internal map and bitset so that the instance can be
	 * safely reused by IDListStandardMapPool instead of being reallocated from
	 * scratch. Must only be called once this id-list is no longer referenced by any
	 * Pattern or EquivalenceClass.
	 */
	void reset() {
		itemsetSequenceEntries.clear();
		sequences.clear();
	}

	/**
	 * Returns this id-list to the shared per-thread IDListStandardMapPool for reuse
	 * by a subsequent join call.
	 */
	@Override
	public void recycle() {
		IDListStandardMapPool.release(this);
	}
}