package ca.pfv.spmf.gui.visual_pattern_viewer.itemsets;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import ca.pfv.spmf.gui.visual_pattern_viewer.Pattern;

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
 */
/**
 * Represents a single itemsets, and its evaluation measures and corresponding values.
 * @author Philippe Fournier-Viger
 */
public class Itemset implements Pattern {

	/**
	 * List of items
	 */
	private final List<String> items;

	/**
	 * Map of measure names to their string values (e.g., support, confidence).
	 */
	private final Map<String, String> measures;
	
	/**
	 * Cached string representation
	 */
	private final String cachedToString;

	/**
	 * Constructs a new Rule instance.
	 *
	 * @param items list of items 
	 * @param measures   mapping of measure names to measure values
	 * @throws NullPointerException if any argument is null
	 */
	public Itemset(List<String> items,  Map<String, String> measures) {
		this.items = items;
		this.measures = measures;
		this.cachedToString = items.toString(); // compute once
	}

	/**
	 * @return the list of items
	 */
	public List<String> getItems() {
		return items;
	}


	/**
	 * @return map of measure names to their values
	 */
	public Map<String, String> getMeasures() {
		return measures;
	}

	/**
	 * @return a human-readable representation 
	 */
	@Override
	public String toString() {
		return cachedToString;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Itemset))
			return false;
		Itemset that = (Itemset) o;
		return getItems().equals(that.getItems())
				&& getMeasures().equals(that.getMeasures());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getItems(), getMeasures());
	}
}