package ca.pfv.spmf.algorithms.sequentialpatterns.vepreco;

import java.util.Arrays;
/*
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
 * Copyright (c) Chen Hui
 *
 * Do not remove copyright and license information.
 */
/**
 * DictMap is the core vertical data structure used by VEPRECO. For each
 * sequence id (sid), it stores a sorted IntList of transaction ids (tids) where
 * the pattern appears. It also maintains: - the pattern itself (compact int[]
 * encoding) - absolute support count - the length of the pattern (number of
 * items) - s-candidates and i-candidates (for pruning)
 *
 * Pattern encoding: patItems[] holds all items in order; patBounds[] holds
 * itemset boundary indices such that itemset i spans
 * patItems[patBounds[i]..patBounds[i+1]).
 * @author Chen Hui
 */
public class DictMap {

	/** Empty int array */
	private static final int[] EMPTY_ARRAY = new int[0];

	/**
	 * Flat array of all items in the pattern, in order across itemsets. Itemset i
	 * spans patItems[patBounds[i] .. patBounds[i+1]).
	 */
	public int[] patItems;

	/**
	 * Boundary indices into patItems. Length = (number of itemsets) + 1.
	 * patBounds[0] = 0, patBounds[numItemsets] = patItems.length.
	 */
	public int[] patBounds;

	/**
	 * Locations: maps sequence id -> sorted IntList of transaction ids where
	 * pattern appears.
	 */
	public IntIntArrayMap locations;

	/** Absolute support (number of sequences containing this pattern) */
	public int support;

	/** Length of the pattern (total number of items) */
	public int length;

	/** s-candidates stored as a sorted array of item ids. */
	public int[] sCandidates;

	/** i-candidates stored as a sorted array of item ids. */
	public int[] iCandidates;

	/** Temporary builder for s-candidates */
	private IntList sCandidatesBuilder;

	/** Temporary builder for i-candidates */
	private IntList iCandidatesBuilder;

	/**
	 * Constructor for a 1-item pattern (used during database scan).
	 *
	 * @param items  flat item array (single element)
	 * @param bounds boundary array ([0, 1])
	 * @param length pattern length (1)
	 */
	public DictMap(int[] items, int[] bounds, int length) {
		this.patItems = items;
		this.patBounds = bounds;
		this.length = length;
		this.locations = new IntIntArrayMap(8);
		this.support = 0;
		this.sCandidates = EMPTY_ARRAY;
		this.iCandidates = EMPTY_ARRAY;
	}

	/**
	 * Copy-extension constructor: build the pattern for a child node derived from
	 * parent P by adding fItem as either s-extension or i-extension.
	 *
	 * @param P      parent DictMap
	 * @param fItem  the new item being added
	 * @param isIExt true for i-extension (append to last itemset), false for
	 *               s-extension
	 */
	public DictMap(DictMap P, int fItem, boolean isIExt) {
		this.length = P.length + 1;
		this.locations = new IntIntArrayMap(Math.max(P.support, 4));
		this.support = 0;
		this.sCandidates = EMPTY_ARRAY;
		this.iCandidates = EMPTY_ARRAY;

		int pNumItemsets = P.patBounds.length - 1;
		if (isIExt) {
			// i-extension: add fItem to last itemset (keep sorted)
			int lastStart = P.patBounds[pNumItemsets - 1];
			int lastEnd = P.patBounds[pNumItemsets];
			int newLen = P.patItems.length + 1;
			patItems = new int[newLen];
			System.arraycopy(P.patItems, 0, patItems, 0, lastStart);
			// Insert fItem into last itemset maintaining sorted order
			int wi = lastStart;
			boolean inserted = false;
			for (int ri = lastStart; ri < lastEnd; ri++) {
				if (!inserted && fItem < P.patItems[ri]) {
					patItems[wi++] = fItem;
					inserted = true;
				}
				patItems[wi++] = P.patItems[ri];
			}
			if (!inserted)
				patItems[wi] = fItem;
			// Same number of itemsets; only the last boundary changes
			patBounds = Arrays.copyOf(P.patBounds, P.patBounds.length);
			patBounds[pNumItemsets] = newLen;
		} else {
			// s-extension: append a new single-item itemset
			int newLen = P.patItems.length + 1;
			patItems = Arrays.copyOf(P.patItems, newLen);
			patItems[newLen - 1] = fItem;
			patBounds = Arrays.copyOf(P.patBounds, P.patBounds.length + 1);
			patBounds[P.patBounds.length] = newLen;
		}
	}

	/**
	 * Add a (sid, tid) pair to this DictMap's locations. If sid is new, increments
	 * support.
	 *
	 * @param sid the sequence id
	 * @param tid the transaction id
	 */
	public void addLocation(int sid, int tid) {
		IntList tids = locations.getOrCreate(sid);
		if (tids.size == 0)
			support++;
		tids.add(tid);
	}

	/**
	 * Add an s-candidate.
	 *
	 * @param item the item
	 */
	public void addSCandidate(int item) {
		if (sCandidatesBuilder == null)
			sCandidatesBuilder = new IntList(8);
		sCandidatesBuilder.add(item);
	}

	/**
	 * Add an i-candidate.
	 *
	 * @param item the item
	 */
	public void addICandidate(int item) {
		if (iCandidatesBuilder == null)
			iCandidatesBuilder = new IntList(8);
		iCandidatesBuilder.add(item);
	}

	/**
	 * Freeze candidate builders into arrays.
	 */
	public void freezeCandidates() {
		sCandidates = toIntArray(sCandidatesBuilder);
		iCandidates = toIntArray(iCandidatesBuilder);
		sCandidatesBuilder = null;
		iCandidatesBuilder = null;
	}

	/**
	 * Convert an IntList to an int array.
	 *
	 * @param list the list
	 * @return the array
	 */
	private int[] toIntArray(IntList list) {
		if (list == null || list.size == 0)
			return EMPTY_ARRAY;
		return Arrays.copyOf(list.data, list.size);
	}

	/**
	 * Append the SPMF-format pattern string to sb.
	 *
	 * @param sb target StringBuilder
	 */
	public void appendPatternTo(StringBuilder sb) {
		int numItemsets = patBounds.length - 1;
		for (int is = 0; is < numItemsets; is++) {
			for (int i = patBounds[is]; i < patBounds[is + 1]; i++) {
				sb.append(patItems[i]).append(' ');
			}
			sb.append("-1 ");
		}
		if (sb.length() > 0)
			sb.setLength(sb.length() - 1);
	}

	/**
	 * Gets the last item in the pattern (last item of the last itemset).
	 *
	 * @return last item
	 */
	public int getLastItem() {
		if (patItems == null || patItems.length == 0)
			return -1;
		return patItems[patItems.length - 1];
	}

	/**
	 * Gets the second-to-last item in the pattern.
	 *
	 * @return second-to-last item, or -1 if pattern length < 2
	 */
	public int getSecondToLastItem() {
		if (length < 2)
			return -1;
		int numItemsets = patBounds.length - 1;
		int lastStart = patBounds[numItemsets - 1];
		int lastEnd = patBounds[numItemsets];
		int lastIsSize = lastEnd - lastStart;
		if (lastIsSize >= 2) {
			return patItems[lastEnd - 2];
		} else if (numItemsets >= 2) {
			int prevEnd = patBounds[numItemsets - 1];
			return patItems[prevEnd - 1];
		}
		return -1;
	}

	/**
	 * Returns true if the last itemset has more than one item (i.e., the last
	 * extension was an i-extension).
	 *
	 * @return true if last itemset has size > 1
	 */
	public boolean isLastExtensionI() {
		if (patBounds == null || patBounds.length < 2)
			return false;
		int numItemsets = patBounds.length - 1;
		return (patBounds[numItemsets] - patBounds[numItemsets - 1]) > 1;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		appendPatternTo(sb);
		return "DictMap{pattern=" + sb + ", support=" + support + "}";
	}
}