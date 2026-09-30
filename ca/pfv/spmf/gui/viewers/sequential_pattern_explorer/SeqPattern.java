package ca.pfv.spmf.gui.viewers.sequential_pattern_explorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license.
 */

/**
 * A sequential pattern read from an SPMF output file: an ordered list of
 * itemsets plus an arbitrary set of named measures (e.g. SUP, CONF, SID...).
 *
 * @author Philippe Fournier-Viger 2025
 */
public class SeqPattern {

	/** The itemsets of the pattern, in order. */
	private final List<List<String>> itemsets;

	/** Measures as (name, raw text value), in order of appearance. */
	private final Map<String, String> measures;

	/** The original line from the file. */
	private final String rawLine;

	/** Total number of items (sum of itemset sizes). */
	private final int itemCount;

	/** Cached human readable form. */
	private String displayString;

	public SeqPattern(List<List<String>> itemsets, Map<String, String> measures, String rawLine) {
		this.itemsets = Collections.unmodifiableList(itemsets);
		this.measures = Collections.unmodifiableMap(measures);
		this.rawLine = rawLine;
		int count = 0;
		for (List<String> is : itemsets) count += is.size();
		this.itemCount = count;
	}

	public List<List<String>> getItemsets() { return itemsets; }

	public Map<String, String> getMeasures() { return measures; }

	public String getMeasure(String name) { return measures.get(name); }

	public String getRawLine() { return rawLine; }

	public int getItemsetCount() { return itemsets.size(); }

	public int getItemCount() { return itemCount; }

	/** @return true if the first itemset contains the item */
	public boolean startsWith(String item) {
		return !itemsets.isEmpty() && itemsets.get(0).contains(item);
	}

	/** @return true if the last itemset contains the item */
	public boolean endsWith(String item) {
		return !itemsets.isEmpty() && itemsets.get(itemsets.size() - 1).contains(item);
	}

	/** @return true if any itemset contains the item */
	public boolean contains(String item) {
		for (List<String> is : itemsets) if (is.contains(item)) return true;
		return false;
	}

	/** @return the distinct items of this pattern */
	public Set<String> getDistinctItems() {
		Set<String> set = new LinkedHashSet<>();
		for (List<String> is : itemsets) set.addAll(is);
		return set;
	}

	/** @return items of the first itemset (empty list if none) */
	public List<String> getFirstItemset() {
		return itemsets.isEmpty() ? new ArrayList<>() : itemsets.get(0);
	}

	/** @return items of the last itemset (empty list if none) */
	public List<String> getLastItemset() {
		return itemsets.isEmpty() ? new ArrayList<>() : itemsets.get(itemsets.size() - 1);
	}

	/** @return e.g. "{1, 2} → {3} → {4}" */
	public String toDisplayString() {
		if (displayString == null) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < itemsets.size(); i++) {
				if (i > 0) sb.append(" \u2192 ");
				sb.append('{');
				List<String> is = itemsets.get(i);
				for (int j = 0; j < is.size(); j++) {
					if (j > 0) sb.append(", ");
					sb.append(is.get(j));
				}
				sb.append('}');
			}
			displayString = sb.toString();
		}
		return displayString;
	}

	@Override
	public String toString() { return toDisplayString(); }
}