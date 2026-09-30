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
 * Summary statistics (count, min, max, mean) of a numeric measure over a set
 * of patterns.
 * 
 * @author Philippe Fournier-Viger
 */
public final class MeasureStats {

	/** Statistics of an empty set */
	public static final MeasureStats EMPTY = new MeasureStats(0, Double.NaN, Double.NaN, 0);

	private final int count;
	private final double min;
	private final double max;
	private final double sum;

	public MeasureStats(int count, double min, double max, double sum) {
		this.count = count;
		this.min = min;
		this.max = max;
		this.sum = sum;
	}

	/** @return number of numeric values */
	public int getCount() {
		return count;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getSum() {
		return sum;
	}

	public double getMean() {
		return count == 0 ? Double.NaN : sum / count;
	}

	/** @return true if there is at least one value */
	public boolean hasValues() {
		return count > 0;
	}
}