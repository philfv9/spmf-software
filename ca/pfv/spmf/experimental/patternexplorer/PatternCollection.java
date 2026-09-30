package ca.pfv.spmf.experimental.patternexplorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * An indexed, immutable collection of {@link ExplorerPattern}s. It keeps an
 * inverted index (item -> sorted list of pattern indices) and one numeric
 * column per numeric measure, so that queries can be evaluated quickly on
 * hundreds of thousands of patterns. Relationships between patterns (subsets,
 * supersets, closedness, maximality) are computed only on demand.
 * 
 * @author Philippe Fournier-Viger
 */
public class PatternCollection {

	/** The patterns */
	private final List<ExplorerPattern> patterns;
	/** All distinct items, sorted with ExplorerPattern.ITEM_ORDER */
	private final String[] itemNames;
	/** item name -> item id */
	private final Map<String, Integer> itemIds;
	/** pattern index -> sorted item ids */
	private final int[][] patternItemIds;
	/** item id -> sorted pattern indices containing the item */
	private final int[][] postings;
	/** All measure (attribute) names in order of first appearance */
	private final List<String> measureNames;
	/** Names of the measures whose values are all numeric */
	private final List<String> numericMeasureNames;
	/** numeric measure name -> index in numericValues */
	private final Map<String, Integer> numericMeasureIds;
	/** [numeric measure index][pattern index] (NaN if missing) */
	private final double[][] numericValues;
	/** Global min / max per numeric measure */
	private final double[] globalMin;
	private final double[] globalMax;
	/** Largest pattern size */
	private final int maxPatternSize;
	/** Description of the source (file name) */
	private final String sourceDescription;
	/** Lower-case item text of each pattern, built lazily for text search */
	private String[] searchText;

	/**
	 * Constructor
	 * 
	 * @param patternList       the patterns
	 * @param sourceDescription a description of the source (e.g. file name)
	 */
	public PatternCollection(List<ExplorerPattern> patternList, String sourceDescription) {
		this.patterns = Collections.unmodifiableList(new ArrayList<>(patternList));
		this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
		int n = patterns.size();

		// 1) Distinct items, measure names and max size
		TreeSet<String> itemSet = new TreeSet<>(ExplorerPattern.ITEM_ORDER);
		LinkedHashSet<String> measureSet = new LinkedHashSet<>();
		int maxSize = 0;
		for (ExplorerPattern p : patterns) {
			Collections.addAll(itemSet, p.itemArray());
			measureSet.addAll(p.getAttributes().keySet());
			if (p.size() > maxSize) {
				maxSize = p.size();
			}
		}
		this.maxPatternSize = maxSize;
		this.itemNames = itemSet.toArray(new String[0]);
		this.itemIds = new HashMap<>(Math.max(16, itemNames.length * 2));
		for (int i = 0; i < itemNames.length; i++) {
			itemIds.put(itemNames[i], i);
		}

		// 2) Item ids of each pattern (ascending because items are sorted with the
		// same order as the ids) and inverted index
		this.patternItemIds = new int[n][];
		int[] postingSizes = new int[itemNames.length];
		for (int p = 0; p < n; p++) {
			String[] arr = patterns.get(p).itemArray();
			int[] ids = new int[arr.length];
			for (int k = 0; k < arr.length; k++) {
				ids[k] = itemIds.get(arr[k]);
				postingSizes[ids[k]]++;
			}
			patternItemIds[p] = ids;
		}
		this.postings = new int[itemNames.length][];
		for (int i = 0; i < itemNames.length; i++) {
			postings[i] = new int[postingSizes[i]];
		}
		int[] fill = new int[itemNames.length];
		for (int p = 0; p < n; p++) {
			for (int id : patternItemIds[p]) {
				postings[id][fill[id]++] = p;
			}
		}

		// 3) Measures: detect numeric ones and store them as columns
		this.measureNames = Collections.unmodifiableList(new ArrayList<>(measureSet));
		List<String> numeric = new ArrayList<>();
		List<double[]> columns = new ArrayList<>();
		for (String measure : measureNames) {
			double[] column = new double[n];
			boolean anyNumeric = false;
			boolean allNumeric = true;
			for (int p = 0; p < n; p++) {
				String s = patterns.get(p).getAttribute(measure);
				if (s == null) {
					column[p] = Double.NaN;
					continue;
				}
				try {
					column[p] = Double.parseDouble(s.trim());
					anyNumeric = true;
				} catch (NumberFormatException e) {
					allNumeric = false;
					break;
				}
			}
			if (anyNumeric && allNumeric) {
				numeric.add(measure);
				columns.add(column);
			}
		}
		this.numericMeasureNames = Collections.unmodifiableList(numeric);
		this.numericMeasureIds = new HashMap<>();
		for (int i = 0; i < numeric.size(); i++) {
			numericMeasureIds.put(numeric.get(i), i);
		}
		this.numericValues = columns.toArray(new double[0][]);
		this.globalMin = new double[numeric.size()];
		this.globalMax = new double[numeric.size()];
		for (int k = 0; k < numeric.size(); k++) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (double v : numericValues[k]) {
				if (!Double.isNaN(v)) {
					if (v < min) {
						min = v;
					}
					if (v > max) {
						max = v;
					}
				}
			}
			globalMin[k] = Double.isInfinite(min) ? Double.NaN : min;
			globalMax[k] = Double.isInfinite(max) ? Double.NaN : max;
		}
	}

	// ------------------------------------------------------------------
	// Accessors
	// ------------------------------------------------------------------

	/** @return number of patterns */
	public int size() {
		return patterns.size();
	}

	/** @return all patterns (unmodifiable) */
	public List<ExplorerPattern> getPatterns() {
		return patterns;
	}

	/** @return the pattern at the given index */
	public ExplorerPattern getPattern(int index) {
		return patterns.get(index);
	}

	/** @return number of distinct items */
	public int getItemCount() {
		return itemNames.length;
	}

	/** @return name of the item with the given id */
	public String getItemName(int id) {
		return itemNames[id];
	}

	/** @return id of an item or -1 if unknown */
	public int getItemId(String item) {
		Integer id = itemIds.get(item);
		return id == null ? -1 : id;
	}

	/** @return number of patterns containing an item id */
	public int getItemFrequency(int id) {
		return postings[id].length;
	}

	/** @return sorted item ids of a pattern (do not modify) */
	public int[] getPatternItemIds(int patternIndex) {
		return patternItemIds[patternIndex];
	}

	/** @return size of a pattern */
	public int getPatternSize(int patternIndex) {
		return patternItemIds[patternIndex].length;
	}

	/** @return largest pattern size */
	public int getMaxPatternSize() {
		return maxPatternSize;
	}

	/** @return all measure names (numeric or not) */
	public List<String> getMeasureNames() {
		return measureNames;
	}

	/** @return names of numeric measures */
	public List<String> getNumericMeasureNames() {
		return numericMeasureNames;
	}

	/** @return index of a numeric measure or -1 */
	public int getNumericMeasureIndex(String measure) {
		Integer idx = numericMeasureIds.get(measure);
		return idx == null ? -1 : idx;
	}

	/** @return true if the measure is numeric */
	public boolean isNumericMeasure(String measure) {
		return numericMeasureIds.containsKey(measure);
	}

	/** @return numeric value of a measure for a pattern (NaN if none) */
	public double getNumericValue(int measureIdx, int patternIdx) {
		return numericValues[measureIdx][patternIdx];
	}

	/** @return global minimum of a numeric measure */
	public double getGlobalMin(int measureIdx) {
		return globalMin[measureIdx];
	}

	/** @return global maximum of a numeric measure */
	public double getGlobalMax(int measureIdx) {
		return globalMax[measureIdx];
	}

	/** @return a description of the source */
	public String getSourceDescription() {
		return sourceDescription;
	}

	// ------------------------------------------------------------------
	// Query evaluation
	// ------------------------------------------------------------------

	/**
	 * Evaluate a query.
	 * 
	 * @param query the query
	 * @return the result (matching pattern indices + lazy aggregates)
	 */
	public QueryResult evaluate(ExplorerQuery query) {
		int n = patterns.size();
		BitSet bits = new BitSet(n);

		// Required items: intersection of posting lists (smallest first)
		Set<String> required = query.getRequiredItems();
		if (required.isEmpty()) {
			bits.set(0, n);
		} else {
			List<int[]> lists = new ArrayList<>();
			for (String item : required) {
				Integer id = itemIds.get(item);
				if (id == null) {
					return new QueryResult(this, query, new int[0]);
				}
				lists.add(postings[id]);
			}
			lists.sort(Comparator.comparingInt(a -> a.length));
			int[] current = lists.get(0);
			for (int i = 1; i < lists.size() && current.length > 0; i++) {
				current = intersectSorted(current, lists.get(i));
			}
			for (int idx : current) {
				bits.set(idx);
			}
		}

		// Excluded items
		for (String item : query.getExcludedItems()) {
			Integer id = itemIds.get(item);
			if (id != null) {
				for (int idx : postings[id]) {
					bits.clear(idx);
				}
			}
		}

		// Items must be among a given set (subsets)
		Set<String> within = query.getWithinItems();
		if (within != null) {
			boolean[] allowed = new boolean[itemNames.length];
			for (String item : within) {
				Integer id = itemIds.get(item);
				if (id != null) {
					allowed[id] = true;
				}
			}
			for (int p = bits.nextSetBit(0); p >= 0; p = bits.nextSetBit(p + 1)) {
				for (int id : patternItemIds[p]) {
					if (!allowed[id]) {
						bits.clear(p);
						break;
					}
				}
			}
		}

		// Size
		int minSize = query.getMinSize();
		int maxSize = query.getMaxSize();
		if (minSize != ExplorerQuery.NO_LIMIT || maxSize != ExplorerQuery.NO_LIMIT) {
			for (int p = bits.nextSetBit(0); p >= 0; p = bits.nextSetBit(p + 1)) {
				int sz = patternItemIds[p].length;
				if ((minSize != ExplorerQuery.NO_LIMIT && sz < minSize)
						|| (maxSize != ExplorerQuery.NO_LIMIT && sz > maxSize)) {
					bits.clear(p);
				}
			}
		}

		// Measure ranges
		for (Map.Entry<String, double[]> e : query.getMeasureRanges().entrySet()) {
			int m = getNumericMeasureIndex(e.getKey());
			if (m < 0) {
				bits.clear();
				break;
			}
			double lo = e.getValue()[0];
			double hi = e.getValue()[1];
			double[] column = numericValues[m];
			for (int p = bits.nextSetBit(0); p >= 0; p = bits.nextSetBit(p + 1)) {
				double v = column[p];
				if (Double.isNaN(v) || (!Double.isNaN(lo) && v < lo) || (!Double.isNaN(hi) && v > hi)) {
					bits.clear(p);
				}
			}
		}

		// Text
		String text = query.getTextFilter();
		if (text != null) {
			String[] tokens = text.toLowerCase().split("\\s+");
			for (int p = bits.nextSetBit(0); p >= 0; p = bits.nextSetBit(p + 1)) {
				if (!matchesText(p, tokens)) {
					bits.clear(p);
				}
			}
		}

		int[] matches = new int[bits.cardinality()];
		int k = 0;
		for (int p = bits.nextSetBit(0); p >= 0; p = bits.nextSetBit(p + 1)) {
			matches[k++] = p;
		}
		return new QueryResult(this, query, matches);
	}

	/** @return true if the item text of the pattern contains all tokens */
	private boolean matchesText(int p, String[] tokens) {
		if (searchText == null) {
			searchText = new String[patterns.size()];
		}
		String s = searchText[p];
		if (s == null) {
			s = patterns.get(p).toSpaceSeparatedString().toLowerCase();
			searchText[p] = s;
		}
		for (String token : tokens) {
			if (!s.contains(token)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Relationships (computed on demand)
	// ------------------------------------------------------------------

	/** @return indices of all patterns containing all items of the pattern */
	private int[] supersetCandidates(int patternIdx) {
		int[] ids = patternItemIds[patternIdx];
		if (ids.length == 0) {
			int[] all = new int[patterns.size()];
			for (int i = 0; i < all.length; i++) {
				all[i] = i;
			}
			return all;
		}
		int[][] lists = new int[ids.length][];
		for (int k = 0; k < ids.length; k++) {
			lists[k] = postings[ids[k]];
		}
		Arrays.sort(lists, Comparator.comparingInt(a -> a.length));
		int[] current = lists[0];
		for (int k = 1; k < lists.length && current.length > 0; k++) {
			current = intersectSorted(current, lists[k]);
		}
		return current;
	}

	/**
	 * Find the proper supersets of a pattern.
	 * 
	 * @param patternIdx the pattern
	 * @param within     restrict the search to these pattern indices (null = all)
	 * @return sorted indices of proper supersets
	 */
	public int[] findSupersets(int patternIdx, BitSet within) {
		int size = patternItemIds[patternIdx].length;
		List<Integer> result = new ArrayList<>();
		for (int c : supersetCandidates(patternIdx)) {
			if (c != patternIdx && patternItemIds[c].length > size && (within == null || within.get(c))) {
				result.add(c);
			}
		}
		return toArray(result);
	}

	/**
	 * Find the proper subsets of a pattern (linear scan, on demand only).
	 * 
	 * @param patternIdx the pattern
	 * @param within     restrict the search to these pattern indices (null = all)
	 * @return sorted indices of proper subsets
	 */
	public int[] findSubsets(int patternIdx, BitSet within) {
		int[] ids = patternItemIds[patternIdx];
		List<Integer> result = new ArrayList<>();
		int n = patterns.size();
		for (int c = 0; c < n; c++) {
			if (c == patternIdx || (within != null && !within.get(c))) {
				continue;
			}
			int[] other = patternItemIds[c];
			if (other.length < ids.length && containsAllIds(ids, other)) {
				result.add(c);
			}
		}
		return toArray(result);
	}

	/**
	 * @return true if the pattern has no proper superset (within the given set)
	 */
	public boolean isMaximal(int patternIdx, BitSet within) {
		int size = patternItemIds[patternIdx].length;
		for (int c : supersetCandidates(patternIdx)) {
			if (c != patternIdx && patternItemIds[c].length > size && (within == null || within.get(c))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @return true if the pattern has no proper superset (within the given set)
	 *         having the same value for the given numeric measure
	 */
	public boolean isClosed(int patternIdx, int measureIdx, BitSet within) {
		int size = patternItemIds[patternIdx].length;
		double value = numericValues[measureIdx][patternIdx];
		for (int c : supersetCandidates(patternIdx)) {
			if (c != patternIdx && patternItemIds[c].length > size && (within == null || within.get(c))
					&& numericValues[measureIdx][c] == value) {
				return false;
			}
		}
		return true;
	}

	/** @return maximality of each pattern of a subset, relative to the subset */
	public boolean[] computeMaximalFlags(int[] subset) {
		BitSet within = toBitSet(subset, patterns.size());
		boolean[] flags = new boolean[subset.length];
		for (int i = 0; i < subset.length; i++) {
			flags[i] = isMaximal(subset[i], within);
		}
		return flags;
	}

	/** @return closedness of each pattern of a subset, relative to the subset */
	public boolean[] computeClosedFlags(int[] subset, int measureIdx) {
		BitSet within = toBitSet(subset, patterns.size());
		boolean[] flags = new boolean[subset.length];
		for (int i = 0; i < subset.length; i++) {
			flags[i] = isClosed(subset[i], measureIdx, within);
		}
		return flags;
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/** @return a BitSet with the given indices set */
	public static BitSet toBitSet(int[] indices, int size) {
		BitSet bits = new BitSet(size);
		for (int i : indices) {
			bits.set(i);
		}
		return bits;
	}

	private static int[] toArray(List<Integer> list) {
		int[] arr = new int[list.size()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = list.get(i);
		}
		return arr;
	}

	/** Intersection of two sorted int arrays */
	private static int[] intersectSorted(int[] a, int[] b) {
		int[] tmp = new int[Math.min(a.length, b.length)];
		int i = 0, j = 0, k = 0;
		while (i < a.length && j < b.length) {
			if (a[i] == b[j]) {
				tmp[k++] = a[i];
				i++;
				j++;
			} else if (a[i] < b[j]) {
				i++;
			} else {
				j++;
			}
		}
		return Arrays.copyOf(tmp, k);
	}

	/** @return true if the sorted array superIds contains all of subIds */
	private static boolean containsAllIds(int[] superIds, int[] subIds) {
		int i = 0, j = 0;
		while (i < superIds.length && j < subIds.length) {
			if (superIds[i] == subIds[j]) {
				i++;
				j++;
			} else if (superIds[i] < subIds[j]) {
				i++;
			} else {
				return false;
			}
		}
		return j == subIds.length;
	}
}