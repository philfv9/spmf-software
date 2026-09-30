package ca.pfv.spmf.experimental.patternexplorer;

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
 * Computes "maximal" and "closed" flags for the patterns of a subset,
 * <b>relative to that subset only</b>. A local inverted index is built on the
 * subset and each pattern is compared only with the patterns sharing its
 * rarest item, which avoids a quadratic comparison of all pairs.
 * 
 * @author Philippe Fournier-Viger
 */
final class SubsetRelations {

	private SubsetRelations() {
	}

	/**
	 * Compute the flags.
	 * 
	 * @param collection       the collection
	 * @param subset           pattern indices of the subset
	 * @param closedMeasureIdx numeric measure used for closedness, or -1
	 * @return {maximalFlags, closedFlags} (closedFlags all false if no measure)
	 */
	static boolean[][] computeFlags(PatternCollection collection, int[] subset, int closedMeasureIdx) {
		int n = subset.length;
		int itemCount = collection.getItemCount();

		// 1) Local inverted index: item id -> positions in the subset
		int[] counts = new int[itemCount];
		for (int i = 0; i < n; i++) {
			for (int id : collection.getPatternItemIds(subset[i])) {
				counts[id]++;
			}
		}
		int[][] index = new int[itemCount][];
		for (int id = 0; id < itemCount; id++) {
			index[id] = new int[counts[id]];
		}
		int[] fill = new int[itemCount];
		for (int i = 0; i < n; i++) {
			for (int id : collection.getPatternItemIds(subset[i])) {
				index[id][fill[id]++] = i;
			}
		}

		// 2) For each pattern, look for proper supersets among the patterns
		// sharing its rarest item
		boolean[] maximal = new boolean[n];
		boolean[] closed = new boolean[n];
		for (int i = 0; i < n; i++) {
			int p = subset[i];
			int[] ids = collection.getPatternItemIds(p);
			double value = closedMeasureIdx >= 0 ? collection.getNumericValue(closedMeasureIdx, p) : Double.NaN;
			boolean isMaximal = true;
			boolean isClosed = true;

			if (ids.length == 0) {
				// The empty pattern: every non-empty pattern is a superset
				for (int j = 0; j < n && (isMaximal || isClosed); j++) {
					int q = subset[j];
					if (j != i && collection.getPatternSize(q) > 0) {
						isMaximal = false;
						if (closedMeasureIdx >= 0 && collection.getNumericValue(closedMeasureIdx, q) == value) {
							isClosed = false;
						}
					}
				}
			} else {
				int rarest = ids[0];
				for (int id : ids) {
					if (counts[id] < counts[rarest]) {
						rarest = id;
					}
				}
				for (int j : index[rarest]) {
					if (j == i) {
						continue;
					}
					int[] other = collection.getPatternItemIds(subset[j]);
					if (other.length <= ids.length || !containsAll(other, ids)) {
						continue;
					}
					isMaximal = false;
					if (closedMeasureIdx < 0) {
						break;
					}
					if (collection.getNumericValue(closedMeasureIdx, subset[j]) == value) {
						isClosed = false;
						break;
					}
				}
			}
			maximal[i] = isMaximal;
			closed[i] = closedMeasureIdx >= 0 && isClosed;
		}
		return new boolean[][] { maximal, closed };
	}

	/** @return true if the sorted array superIds contains all of subIds */
	private static boolean containsAll(int[] superIds, int[] subIds) {
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