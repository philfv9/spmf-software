package ca.pfv.spmf.algorithms.frequentpatterns.genmax;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger

This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages maximal itemsets with their support values and supports efficient
 * superset/subset checks. Maintains itemsets in canonical order for fast
 * comparisons. This class stores both the itemsets and their corresponding
 * support values, serving as the single source of truth for maximal frequent
 * itemsets.
 * @see AlgoGENMAX
 * 
 * @author Philippe Fournier-Viger
 */
class MaxItemsetStorage {

	/**
	 * List indexed by itemset length containing lists of itemsets with support.
	 * Index 0 is unused (no itemsets of length 0). Index i contains itemsets of
	 * length i.
	 */
	private final List<List<ItemsetWithSupport>> byLength;

	/** Cached length of the largest maximal itemset. */
	private int maxLength;

	/** Initializes an empty manager. */
	MaxItemsetStorage() {
		byLength = new ArrayList<>();
		// Add null for index 0 (no itemsets of length 0)
		byLength.add(null);
		maxLength = 0;
	}

	/**
	 * Ensures the list has capacity for the given length.
	 * 
	 * @param length the required length
	 */
	private void ensureCapacity(int length) {
		while (byLength.size() <= length) {
			byLength.add(null);
		}
	}

	/**
	 * Gets the list of itemsets for the given length, or null if none exist.
	 * 
	 * @param length the itemset length
	 * @return list of itemsets or null
	 */
	private List<ItemsetWithSupport> getItemsOfLength(int length) {
		if (length >= byLength.size()) {
			return null;
		}
		return byLength.get(length);
	}

	/**
	 * Checks if a candidate has a superset that is an existing maximal itemset.
	 * 
	 * @param candidate the candidate itemset
	 * @return true if a superset exists
	 */
	boolean hasSuperset(int[] candidate) {
		int candidateLen = candidate.length;
		for (int length = candidateLen; length <= maxLength; length++) {
			List<ItemsetWithSupport> itemsOfLength = getItemsOfLength(length);
			if (itemsOfLength == null)
				continue;

			for (ItemsetWithSupport entry : itemsOfLength) {
				if (isSubsetOf(candidate, entry.itemset)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Adds a candidate as a maximal itemset with its support if no superset exists.
	 * Also removes any existing subsets.
	 * 
	 * @param candidate the candidate itemset
	 * @param support   the support value of the candidate
	 * @return true if added successfully
	 */
	boolean addIfMaximal(int[] candidate, int support) {
		if (hasSuperset(candidate))
			return false;

		removeSubsetsOf(candidate);

		int length = candidate.length;
		ensureCapacity(length);

		List<ItemsetWithSupport> itemsOfLength = byLength.get(length);
		if (itemsOfLength == null) {
			itemsOfLength = new ArrayList<>();
			byLength.set(length, itemsOfLength);
		}
		itemsOfLength.add(new ItemsetWithSupport(candidate.clone(), support));

		if (length > maxLength) {
			maxLength = length;
		}
		return true;
	}

	/**
	 * Adds an itemset directly with its support without checking for supersets.
	 * Used when we know the itemset is maximal in its context. Still removes
	 * subsets to maintain maximality.
	 * 
	 * @param itemset the itemset to add
	 * @param support the support value of the itemset
	 */
	void addAsMaximal(int[] itemset, int support) {
		if (hasSuperset(itemset))
			return;

		removeSubsetsOf(itemset);

		int length = itemset.length;
		ensureCapacity(length);

		List<ItemsetWithSupport> itemsOfLength = byLength.get(length);
		if (itemsOfLength == null) {
			itemsOfLength = new ArrayList<>();
			byLength.set(length, itemsOfLength);
		}
		itemsOfLength.add(new ItemsetWithSupport(itemset.clone(), support));

		if (length > maxLength) {
			maxLength = length;
		}
	}

	/**
	 * Removes all existing maximal itemsets that are subsets of the given itemset.
	 * 
	 * @param itemset the reference itemset
	 */
	private void removeSubsetsOf(int[] itemset) {
		int itemsetLen = itemset.length;
		for (int length = 1; length < itemsetLen; length++) {
			List<ItemsetWithSupport> itemsets = getItemsOfLength(length);

			if (itemsets != null) {
				Iterator<ItemsetWithSupport> it = itemsets.iterator();
				while (it.hasNext()) {
					ItemsetWithSupport existing = it.next();
					if (isSubsetOf(existing.itemset, itemset)) {
						it.remove();
					}
				}
			}
		}
	}

	/**
	 * Returns true if subset ⊆ superset (both must be in canonical order).
	 * 
	 * @param subset   the potential subset
	 * @param superset the potential superset
	 * @return true if subset ⊆ superset
	 */
	private static boolean isSubsetOf(int[] subset, int[] superset) {
		int subLen = subset.length;
		int superLen = superset.length;

		if (subLen > superLen)
			return false;
		if (subLen == 0)
			return true;

		int i = 0, j = 0;
		while (i < subLen && j < superLen) {
			if (subset[i] == superset[j]) {
				i++;
			} else if (subset[i] < superset[j]) {
				// Since both are sorted, if subset[i] < superset[j],
				// subset[i] cannot be found in superset
				return false;
			}
			j++;
		}
		return i == subLen;
	}

	/**
	 * Returns the number of maximal itemsets stored.
	 * 
	 * @return count of itemsets
	 */
	int size() {
		int count = 0;
		for (int i = 1; i < byLength.size(); i++) {
			List<ItemsetWithSupport> list = byLength.get(i);
			if (list != null) {
				count += list.size();
			}
		}
		return count;
	}

	/**
	 * Checks if storage is empty.
	 * 
	 * @return true if no itemsets stored
	 */
	boolean isEmpty() {
		return maxLength == 0;
	}

	/**
	 * Clears all stored itemsets.
	 */
	void clear() {
		byLength.clear();
		byLength.add(null); // Re-add null for index 0
		maxLength = 0;
	}

	/**
	 * Returns all stored itemsets as a list.
	 * 
	 * @return list of itemsets
	 */
	List<int[]> getItemsets() {
		List<int[]> result = new ArrayList<>(size());
		for (int i = 1; i < byLength.size(); i++) {
			List<ItemsetWithSupport> list = byLength.get(i);
			if (list != null) {
				for (ItemsetWithSupport entry : list) {
					result.add(entry.itemset);
				}
			}
		}
		return result;
	}

	/**
	 * Returns all stored support values as a list, in the same order as
	 * getItemsets().
	 * 
	 * @return list of support values
	 */
	List<Integer> getSupports() {
		List<Integer> result = new ArrayList<>(size());
		for (int i = 1; i < byLength.size(); i++) {
			List<ItemsetWithSupport> list = byLength.get(i);
			if (list != null) {
				for (ItemsetWithSupport entry : list) {
					result.add(entry.support);
				}
			}
		}
		return result;
	}
}