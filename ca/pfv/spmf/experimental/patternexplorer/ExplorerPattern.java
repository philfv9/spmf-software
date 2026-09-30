package ca.pfv.spmf.experimental.patternexplorer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

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
 * 
 * Do not remove copyright and license information
 */
/**
 * A generic pattern as used by the Interactive Pattern Explorer. A pattern is
 * an <b>unordered</b> set of items plus zero or more named attributes
 * (measures) whose values are kept as strings. Items are stored sorted so that
 * the order of items in the input file never affects the semantics.
 * 
 * @author Philippe Fournier-Viger
 */
public class ExplorerPattern implements Pattern, Comparable<ExplorerPattern> {

	/**
	 * Ordering used for items: numeric item names are compared numerically,
	 * otherwise lexicographically.
	 */
	public static final Comparator<String> ITEM_ORDER = (a, b) -> {
		if (isInteger(a) && isInteger(b)) {
			int cmp = Long.compare(Long.parseLong(a), Long.parseLong(b));
			if (cmp != 0) {
				return cmp;
			}
		}
		return a.compareTo(b);
	};

	/** Sorted, unique items */
	private final String[] items;

	/** Attributes (measures) name -> string value */
	private final Map<String, String> attributes;

	/** Cached string representation */
	private String cachedString;

	/**
	 * Constructor
	 * 
	 * @param itemCollection the items (any order, duplicates are removed)
	 * @param attributes     attributes/measures (may be null)
	 */
	public ExplorerPattern(Collection<String> itemCollection, Map<String, String> attributes) {
		Objects.requireNonNull(itemCollection, "items must not be null");
		TreeSet<String> set = new TreeSet<>(ITEM_ORDER);
		for (String s : itemCollection) {
			if (s != null && !s.isEmpty()) {
				set.add(s);
			}
		}
		this.items = set.toArray(new String[0]);
		this.attributes = attributes == null ? Collections.emptyMap()
				: Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
	}

	private static boolean isInteger(String s) {
		int len = s.length();
		if (len == 0 || len > 18) {
			return false;
		}
		int i = 0;
		if (s.charAt(0) == '-') {
			if (len == 1) {
				return false;
			}
			i = 1;
		}
		for (; i < len; i++) {
			char c = s.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	/** @return the items (sorted, unmodifiable) */
	public List<String> getItems() {
		return Collections.unmodifiableList(Arrays.asList(items));
	}

	/** @return internal item array (do not modify) */
	String[] itemArray() {
		return items;
	}

	/** @return number of items */
	public int size() {
		return items.length;
	}

	/** @return attributes (measures) as name -> value */
	public Map<String, String> getAttributes() {
		return attributes;
	}

	/** @return the value of an attribute or null */
	public String getAttribute(String name) {
		return attributes.get(name);
	}

	/** @return true if the pattern contains the item */
	public boolean containsItem(String item) {
		return Arrays.binarySearch(items, item, ITEM_ORDER) >= 0;
	}

	/**
	 * Check if this pattern is a superset (or equal) of another pattern
	 * 
	 * @param other another pattern
	 * @return true if all items of other are contained in this pattern
	 */
	public boolean containsAll(ExplorerPattern other) {
		String[] o = other.items;
		if (o.length > items.length) {
			return false;
		}
		int i = 0, j = 0;
		while (i < items.length && j < o.length) {
			int cmp = ITEM_ORDER.compare(items[i], o[j]);
			if (cmp == 0) {
				i++;
				j++;
			} else if (cmp < 0) {
				i++;
			} else {
				return false;
			}
		}
		return j == o.length;
	}

	/** @return items separated by spaces (SPMF style) */
	public String toSpaceSeparatedString() {
		return String.join(" ", items);
	}

	@Override
	public String toString() {
		if (cachedString == null) {
			cachedString = "{" + String.join(", ", items) + "}";
		}
		return cachedString;
	}

	@Override
	public int compareTo(ExplorerPattern o) {
		int n = Math.min(items.length, o.items.length);
		for (int i = 0; i < n; i++) {
			int cmp = ITEM_ORDER.compare(items[i], o.items[i]);
			if (cmp != 0) {
				return cmp;
			}
		}
		return Integer.compare(items.length, o.items.length);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ExplorerPattern)) {
			return false;
		}
		ExplorerPattern that = (ExplorerPattern) o;
		return Arrays.equals(items, that.items) && attributes.equals(that.attributes);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(items) + attributes.hashCode();
	}
}