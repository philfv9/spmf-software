package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * Simple descriptive statistics (count, min, max, mean, median, standard
 * deviation) of a list of values, used by the statistics panel and the
 * measure histogram of the Visual Pattern Viewer.
 *
 * @author Philippe Fournier-Viger
 */
public final class DescriptiveStats {

	public final int count;
	public final double min;
	public final double max;
	public final double mean;
	public final double median;
	public final double stdDev;

	private DescriptiveStats(int count, double min, double max, double mean, double median, double stdDev) {
		this.count = count;
		this.min = min;
		this.max = max;
		this.mean = mean;
		this.median = median;
		this.stdDev = stdDev;
	}

	/**
	 * @param values the values (null entries are ignored)
	 * @return the statistics, or null if there is no value
	 */
	public static DescriptiveStats of(List<Double> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}
		List<Double> sorted = new ArrayList<>(values.size());
		for (Double v : values) {
			if (v != null && !v.isNaN()) {
				sorted.add(v);
			}
		}
		if (sorted.isEmpty()) {
			return null;
		}
		Collections.sort(sorted);
		int n = sorted.size();
		double sum = 0;
		for (double v : sorted) {
			sum += v;
		}
		double mean = sum / n;
		double sq = 0;
		for (double v : sorted) {
			sq += (v - mean) * (v - mean);
		}
		double median = (n % 2 == 1) ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
		return new DescriptiveStats(n, sorted.get(0), sorted.get(n - 1), mean, median, Math.sqrt(sq / n));
	}
}