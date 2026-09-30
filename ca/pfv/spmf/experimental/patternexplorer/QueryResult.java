package ca.pfv.spmf.experimental.patternexplorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

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
 * The result of evaluating an {@link ExplorerQuery} on a
 * {@link PatternCollection}: the matching pattern indices plus aggregates
 * (candidate items, size distribution, measure statistics, histograms) that
 * are computed lazily and cached.
 * 
 * @author Philippe Fournier-Viger
 */
public final class QueryResult {

	private final PatternCollection collection;
	private final ExplorerQuery query;
	/** Sorted indices of matching patterns */
	private final int[] matches;

	private List<CandidateItemStats> candidates;
	private TreeMap<Integer, Integer> sizeDistribution;
	private MeasureStats[] measureStats;
	private int distinctItemCount = -1;

	QueryResult(PatternCollection collection, ExplorerQuery query, int[] matches) {
		this.collection = collection;
		this.query = query;
		this.matches = matches;
	}

	public PatternCollection getCollection() {
		return collection;
	}

	public ExplorerQuery getQuery() {
		return query;
	}

	/** @return number of matching patterns */
	public int size() {
		return matches.length;
	}

	public boolean isEmpty() {
		return matches.length == 0;
	}

	/** @return the sorted matching pattern indices (do not modify) */
	public int[] getMatchIndices() {
		return matches;
	}

	/** @return pattern index of the i-th match */
	public int getPatternIndex(int row) {
		return matches[row];
	}

	/** @return the i-th matching pattern */
	public ExplorerPattern getPattern(int row) {
		return collection.getPattern(matches[row]);
	}

	/** @return fraction of the whole collection that matches */
	public double getFractionOfCollection() {
		return collection.size() == 0 ? 0 : (double) matches.length / collection.size();
	}

	/** @return number of distinct items appearing in matching patterns */
	public int getDistinctItemCount() {
		getCandidateItems();
		return distinctItemCount;
	}

	/**
	 * @return statistics for each item that can be added to the query (items of
	 *         the matching patterns that are not already required), sorted by
	 *         decreasing number of patterns
	 */
	public synchronized List<CandidateItemStats> getCandidateItems() {
		if (candidates != null) {
			return candidates;
		}
		int numItems = collection.getItemCount();
		int m = collection.getNumericMeasureNames().size();
		int[] counts = new int[numItems];
		int[][] numericCounts = new int[m][numItems];
		double[][] sums = new double[m][numItems];
		double[][] mins = new double[m][numItems];
		double[][] maxs = new double[m][numItems];
		for (int k = 0; k < m; k++) {
			Arrays.fill(mins[k], Double.POSITIVE_INFINITY);
			Arrays.fill(maxs[k], Double.NEGATIVE_INFINITY);
		}
		boolean[] skip = new boolean[numItems];
		for (String item : query.getRequiredItems()) {
			int id = collection.getItemId(item);
			if (id >= 0) {
				skip[id] = true;
			}
		}
		int touched = 0;
		for (int p : matches) {
			for (int id : collection.getPatternItemIds(p)) {
				if (counts[id]++ == 0) {
					touched++;
				}
				if (skip[id]) {
					continue;
				}
				for (int k = 0; k < m; k++) {
					double v = collection.getNumericValue(k, p);
					if (!Double.isNaN(v)) {
						numericCounts[k][id]++;
						sums[k][id] += v;
						if (v < mins[k][id]) {
							mins[k][id] = v;
						}
						if (v > maxs[k][id]) {
							maxs[k][id] = v;
						}
					}
				}
			}
		}
		distinctItemCount = touched;
		List<CandidateItemStats> list = new ArrayList<>();
		for (int id = 0; id < numItems; id++) {
			if (counts[id] == 0 || skip[id]) {
				continue;
			}
			int[] nc = new int[m];
			double[] s = new double[m];
			double[] mn = new double[m];
			double[] mx = new double[m];
			for (int k = 0; k < m; k++) {
				nc[k] = numericCounts[k][id];
				s[k] = sums[k][id];
				mn[k] = nc[k] == 0 ? Double.NaN : mins[k][id];
				mx[k] = nc[k] == 0 ? Double.NaN : maxs[k][id];
			}
			list.add(new CandidateItemStats(collection.getItemName(id), counts[id],
					(double) counts[id] / matches.length, nc, s, mn, mx));
		}
		list.sort(Comparator.comparingInt(CandidateItemStats::getCount).reversed()
				.thenComparing(CandidateItemStats::getItem, ExplorerPattern.ITEM_ORDER));
		candidates = Collections.unmodifiableList(list);
		return candidates;
	}

	/** @return size -> number of matching patterns of that size */
	public synchronized TreeMap<Integer, Integer> getSizeDistribution() {
		if (sizeDistribution == null) {
			TreeMap<Integer, Integer> map = new TreeMap<>();
			for (int p : matches) {
				map.merge(collection.getPatternSize(p), 1, Integer::sum);
			}
			sizeDistribution = map;
		}
		return sizeDistribution;
	}

	/** @return statistics of a numeric measure over the matching patterns */
	public synchronized MeasureStats getMeasureStats(int measureIdx) {
		if (measureStats == null) {
			int m = collection.getNumericMeasureNames().size();
			measureStats = new MeasureStats[m];
			for (int k = 0; k < m; k++) {
				int count = 0;
				double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum = 0;
				for (int p : matches) {
					double v = collection.getNumericValue(k, p);
					if (!Double.isNaN(v)) {
						count++;
						sum += v;
						if (v < min) {
							min = v;
						}
						if (v > max) {
							max = v;
						}
					}
				}
				measureStats[k] = count == 0 ? MeasureStats.EMPTY : new MeasureStats(count, min, max, sum);
			}
		}
		return measureStats[measureIdx];
	}

	/**
	 * Compute a histogram of a numeric measure over the matching patterns.
	 * 
	 * @param measureIdx numeric measure index
	 * @param bins       number of bins
	 * @return counts per bin (between the min and max of the measure in the result)
	 */
	public int[] computeHistogram(int measureIdx, int bins) {
		int[] histogram = new int[bins];
		MeasureStats stats = getMeasureStats(measureIdx);
		if (!stats.hasValues()) {
			return histogram;
		}
		double min = stats.getMin();
		double range = stats.getMax() - min;
		for (int p : matches) {
			double v = collection.getNumericValue(measureIdx, p);
			if (Double.isNaN(v)) {
				continue;
			}
			int b = range <= 0 ? 0 : (int) ((v - min) / range * bins);
			if (b >= bins) {
				b = bins - 1;
			}
			if (b < 0) {
				b = 0;
			}
			histogram[b]++;
		}
		return histogram;
	}
}