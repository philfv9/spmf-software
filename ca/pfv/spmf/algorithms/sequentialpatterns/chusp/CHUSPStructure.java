package ca.pfv.spmf.algorithms.sequentialpatterns.chusp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license information.
 */

/**
 * This class represents the CHUS data structure of a pattern as described in
 * the CHUSP algorithm.
 * <br/>
 * The CHUS structure of a pattern t is the set of q-sequences of the projected
 * database of t. Each q-sequence stores: its transformed itemsets (the
 * q-sequence itself), the utility and remaining-utility information of each
 * element, and a list of the match positions of the current pattern. The
 * support of the pattern is the number of q-sequences in the structure, and the
 * utility is the sum, over the q-sequences, of the maximum utility of a match.
 *
 * @see AlgoCHUSP
 * @author Philippe Fournier-Viger
 */
class CHUSPStructure {

	/** the utility of the whole q-sequence, u(s) */
	int sequenceUtility;
	/** the number of itemsets in the q-sequence */
	int itemsetCount;
	/** the q-sequence elements, in position order */
	CItem[] elements;
	/** itemsets[i] is the list of element positions belonging to itemset i, sorted by item */
	List<List<Integer>> itemsets;
	/** itemsetItemMap[i] maps an item to its element position inside itemset i */
	List<Map<Integer, Integer>> itemsetItemMap;
	/** item -> list of [itemsetIndex, elementPosition], ordered by itemset */
	Map<Integer, List<int[]>> itemOccurrences;
	/** the match positions of the current pattern, together with their utilities */
	List<int[]> matches;

	/**
	 * Package-private constructor for an empty CHUS structure that is then
	 * populated field-by-field. Used by AlgoCHUSP.copyCHUS to avoid re-running
	 * the full construction on a throwaway empty list (memory/time optimization).
	 */
	CHUSPStructure() {
	}

	/**
	 * Constructor. Builds the q-sequence from the raw itemsets. Each raw itemset
	 * is a list of pairs [item, utility]. Items inside an itemset are sorted by
	 * item so that the enumeration stays lexicographic.
	 * @param rawItemsets the itemsets of the q-sequence
	 */
	CHUSPStructure(List<List<int[]>> rawItemsets) {
		this.itemsetCount = rawItemsets.size();
		this.itemsets = new ArrayList<List<Integer>>(itemsetCount);
		this.itemsetItemMap = new ArrayList<Map<Integer, Integer>>(itemsetCount);
		List<CItem> elemList = new ArrayList<CItem>();

		int position = 0;
		for (List<int[]> raw : rawItemsets) {
			List<int[]> sorted = new ArrayList<int[]>(raw);
			Collections.sort(sorted, new Comparator<int[]>() {
				public int compare(int[] a, int[] b) {
					return Integer.compare(a[0], b[0]);
				}
			});
			List<Integer> idxs = new ArrayList<Integer>();
			Map<Integer, Integer> imap = new HashMap<Integer, Integer>();
			for (int[] pu : sorted) {
				CItem e = new CItem(pu[0], pu[1], itemsets.size(), position);
				elemList.add(e);
				idxs.add(Integer.valueOf(position));
				imap.put(Integer.valueOf(pu[0]), Integer.valueOf(position));
				position++;
			}
			itemsets.add(idxs);
			itemsetItemMap.add(imap);
		}
		this.elements = elemList.toArray(new CItem[elemList.size()]);

		// Compute the remaining utility of each element by scanning backwards.
		this.sequenceUtility = 0;
		for (int i = elements.length - 1; i >= 0; i--) {
			elements[i].remainingUtility = sequenceUtility;
			sequenceUtility += elements[i].utility;
		}

		// Build the per-item occurrence lists.
		this.itemOccurrences = new HashMap<Integer, List<int[]>>();
		for (int i = 0; i < elements.length; i++) {
			CItem e = elements[i];
			List<int[]> occ = itemOccurrences.get(Integer.valueOf(e.item));
			if (occ == null) {
				occ = new ArrayList<int[]>();
				itemOccurrences.put(Integer.valueOf(e.item), occ);
			}
			occ.add(new int[] { e.itemsetIndex, i });
		}
	}

	/**
	 * Find the element position of an item inside a given itemset.
	 * @param itemsetIndex the index of the itemset
	 * @param item the item to look for
	 * @return the element position, or -1 if the item is not in that itemset
	 */
	int findItemInItemset(int itemsetIndex, int item) {
		Map<Integer, Integer> imap = itemsetItemMap.get(itemsetIndex);
		Integer idx = imap.get(Integer.valueOf(item));
		return idx == null ? -1 : idx.intValue();
	}

	/** This class represents one element of a q-sequence in the CHUS structure. */
	static class CItem {
		/** the item name */
		int item;
		/** the utility of the item */
		int utility;
		/** the remaining utility of the item */
		int remainingUtility;
		/** the index of the itemset containing the item */
		int itemsetIndex;
		/** the position of the element in the q-sequence */
		int position;

		CItem(int item, int utility, int itemsetIndex, int position) {
			this.item = item;
			this.utility = utility;
			this.itemsetIndex = itemsetIndex;
			this.position = position;
		}
	}
}
