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
 * Aggregated information about a candidate item that can be added to the
 * current query: how many matching patterns contain it, and statistics of
 * each numeric measure over those patterns.
 * 
 * @author Philippe Fournier-Viger
 */
public final class CandidateItemStats {

	private final String item;
	private final int count;
	private final double fraction;
	/** Per numeric measure (in the order of PatternCollection.getNumericMeasureNames()) */
	private final int[] numericCounts;
	private final double[] sums;
	private final double[] mins;
	private final double[] maxs;

	CandidateItemStats(String item, int count, double fraction, int[] numericCounts, double[] sums,
			double[] mins, double[] maxs) {
		this.item = item;
		this.count = count;
		this.fraction = fraction;
		this.numericCounts = numericCounts;
		this.sums = sums;
		this.mins = mins;
		this.maxs = maxs;
	}

	public String getItem() {
		return item;
	}

	/** @return number of matching patterns containing the item */
	public int getCount() {
		return count;
	}

	/** @return fraction of the matching patterns containing the item */
	public double getFraction() {
		return fraction;
	}

	public double getMean(int measureIdx) {
		return numericCounts[measureIdx] == 0 ? Double.NaN : sums[measureIdx] / numericCounts[measureIdx];
	}

	public double getMin(int measureIdx) {
		return mins[measureIdx];
	}

	public double getMax(int measureIdx) {
		return maxs[measureIdx];
	}

	public double getSum(int measureIdx) {
		return numericCounts[measureIdx] == 0 ? Double.NaN : sums[measureIdx];
	}
}