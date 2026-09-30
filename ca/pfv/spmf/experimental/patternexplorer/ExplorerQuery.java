package ca.pfv.spmf.experimental.patternexplorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * An immutable filter state of the Interactive Pattern Explorer. A query is a
 * conjunction of restrictions: required items, excluded items, an optional
 * "items must be among" set, a size range, ranges on numeric measures and a
 * text filter. Each "with..." method returns a new query.
 * 
 * @author Philippe Fournier-Viger
 */
public final class ExplorerQuery {

	/** Value meaning "no limit" for size bounds */
	public static final int NO_LIMIT = -1;

	/** The query without any restriction */
	public static final ExplorerQuery UNRESTRICTED = new ExplorerQuery(Collections.emptySet(),
			Collections.emptySet(), null, NO_LIMIT, NO_LIMIT, Collections.emptyMap(), null);

	private final Set<String> requiredItems;
	private final Set<String> excludedItems;
	/** null = no restriction */
	private final Set<String> withinItems;
	private final int minSize;
	private final int maxSize;
	/** measure -> {min, max} (NaN = unbounded) */
	private final Map<String, double[]> measureRanges;
	private final String textFilter;

	private ExplorerQuery(Set<String> required, Set<String> excluded, Set<String> within, int minSize,
			int maxSize, Map<String, double[]> ranges, String textFilter) {
		this.requiredItems = Collections.unmodifiableSet(new LinkedHashSet<>(required));
		this.excludedItems = Collections.unmodifiableSet(new LinkedHashSet<>(excluded));
		if (within == null) {
			this.withinItems = null;
		} else {
			TreeSet<String> ts = new TreeSet<>(ExplorerPattern.ITEM_ORDER);
			ts.addAll(within);
			this.withinItems = Collections.unmodifiableSet(ts);
		}
		this.minSize = minSize;
		this.maxSize = maxSize;
		LinkedHashMap<String, double[]> copy = new LinkedHashMap<>();
		for (Map.Entry<String, double[]> e : ranges.entrySet()) {
			copy.put(e.getKey(), e.getValue().clone());
		}
		this.measureRanges = Collections.unmodifiableMap(copy);
		this.textFilter = (textFilter == null || textFilter.isBlank()) ? null : textFilter.trim();
	}

	// ------------------------------------------------------------------
	// Derivation methods
	// ------------------------------------------------------------------

	public ExplorerQuery requireItem(String item) {
		LinkedHashSet<String> req = new LinkedHashSet<>(requiredItems);
		req.add(item);
		LinkedHashSet<String> exc = new LinkedHashSet<>(excludedItems);
		exc.remove(item);
		return new ExplorerQuery(req, exc, withinItems, minSize, maxSize, measureRanges, textFilter);
	}

	public ExplorerQuery requireItems(Collection<String> items) {
		ExplorerQuery q = this;
		for (String item : items) {
			q = q.requireItem(item);
		}
		return q;
	}

	public ExplorerQuery excludeItem(String item) {
		LinkedHashSet<String> exc = new LinkedHashSet<>(excludedItems);
		exc.add(item);
		LinkedHashSet<String> req = new LinkedHashSet<>(requiredItems);
		req.remove(item);
		return new ExplorerQuery(req, exc, withinItems, minSize, maxSize, measureRanges, textFilter);
	}

	public ExplorerQuery removeRequiredItem(String item) {
		LinkedHashSet<String> req = new LinkedHashSet<>(requiredItems);
		req.remove(item);
		return new ExplorerQuery(req, excludedItems, withinItems, minSize, maxSize, measureRanges, textFilter);
	}

	public ExplorerQuery removeExcludedItem(String item) {
		LinkedHashSet<String> exc = new LinkedHashSet<>(excludedItems);
		exc.remove(item);
		return new ExplorerQuery(requiredItems, exc, withinItems, minSize, maxSize, measureRanges, textFilter);
	}

	/** Restrict to patterns whose items are all among the given items */
	public ExplorerQuery restrictWithin(Collection<String> items) {
		return new ExplorerQuery(requiredItems, excludedItems, new LinkedHashSet<>(items), minSize, maxSize,
				measureRanges, textFilter);
	}

	public ExplorerQuery clearWithin() {
		return new ExplorerQuery(requiredItems, excludedItems, null, minSize, maxSize, measureRanges, textFilter);
	}

	public ExplorerQuery withSizeRange(int min, int max) {
		return new ExplorerQuery(requiredItems, excludedItems, withinItems, min, max, measureRanges, textFilter);
	}

	public ExplorerQuery clearSizeRange() {
		return withSizeRange(NO_LIMIT, NO_LIMIT);
	}

	public ExplorerQuery withMeasureRange(String measure, double min, double max) {
		LinkedHashMap<String, double[]> ranges = new LinkedHashMap<>(measureRanges);
		ranges.put(measure, new double[] { min, max });
		return new ExplorerQuery(requiredItems, excludedItems, withinItems, minSize, maxSize, ranges, textFilter);
	}

	public ExplorerQuery clearMeasureRange(String measure) {
		LinkedHashMap<String, double[]> ranges = new LinkedHashMap<>(measureRanges);
		ranges.remove(measure);
		return new ExplorerQuery(requiredItems, excludedItems, withinItems, minSize, maxSize, ranges, textFilter);
	}

	public ExplorerQuery withTextFilter(String text) {
		return new ExplorerQuery(requiredItems, excludedItems, withinItems, minSize, maxSize, measureRanges, text);
	}

	public ExplorerQuery clearTextFilter() {
		return withTextFilter(null);
	}

	// ------------------------------------------------------------------
	// Getters
	// ------------------------------------------------------------------

	public Set<String> getRequiredItems() {
		return requiredItems;
	}

	public Set<String> getExcludedItems() {
		return excludedItems;
	}

	/** @return the "within" item set or null if none */
	public Set<String> getWithinItems() {
		return withinItems;
	}

	public int getMinSize() {
		return minSize;
	}

	public int getMaxSize() {
		return maxSize;
	}

	/** @return measure -> {min,max} (NaN = unbounded) */
	public Map<String, double[]> getMeasureRanges() {
		return measureRanges;
	}

	/** @return the text filter or null */
	public String getTextFilter() {
		return textFilter;
	}

	/** @return true if this query has no restriction at all */
	public boolean isUnrestricted() {
		return requiredItems.isEmpty() && excludedItems.isEmpty() && withinItems == null && minSize == NO_LIMIT
				&& maxSize == NO_LIMIT && measureRanges.isEmpty() && textFilter == null;
	}

	// ------------------------------------------------------------------
	// Restrictions (for chips) and labels
	// ------------------------------------------------------------------

	/**
	 * A single restriction of a query, with a label and the query obtained by
	 * removing this restriction.
	 */
	public static final class Restriction {
		public enum Kind {
			REQUIRED, EXCLUDED, WITHIN, SIZE, MEASURE, TEXT
		}

		private final Kind kind;
		private final String label;
		private final String tooltip;
		private final ExplorerQuery queryWithout;

		Restriction(Kind kind, String label, String tooltip, ExplorerQuery queryWithout) {
			this.kind = kind;
			this.label = label;
			this.tooltip = tooltip;
			this.queryWithout = queryWithout;
		}

		public Kind getKind() {
			return kind;
		}

		public String getLabel() {
			return label;
		}

		public String getTooltip() {
			return tooltip;
		}

		public ExplorerQuery getQueryWithout() {
			return queryWithout;
		}
	}

	/** @return the list of restrictions composing this query */
	public List<Restriction> getRestrictions() {
		List<Restriction> list = new ArrayList<>();
		for (String it : requiredItems) {
			list.add(new Restriction(Restriction.Kind.REQUIRED, "+ " + it, "Patterns must contain \"" + it + "\"",
					removeRequiredItem(it)));
		}
		for (String it : excludedItems) {
			list.add(new Restriction(Restriction.Kind.EXCLUDED, "\u2212 " + it,
					"Patterns must not contain \"" + it + "\"", removeExcludedItem(it)));
		}
		if (withinItems != null) {
			list.add(new Restriction(Restriction.Kind.WITHIN, "\u2286 " + setLabel(withinItems),
					"Patterns may only contain items among " + setLabel(withinItems) + " (subsets)",
					clearWithin()));
		}
		if (minSize != NO_LIMIT || maxSize != NO_LIMIT) {
			list.add(new Restriction(Restriction.Kind.SIZE, sizeLabel(minSize, maxSize),
					"Restriction on the number of items", clearSizeRange()));
		}
		for (Map.Entry<String, double[]> e : measureRanges.entrySet()) {
			list.add(new Restriction(Restriction.Kind.MEASURE,
					measureLabel(e.getKey(), e.getValue()[0], e.getValue()[1]),
					"Restriction on measure " + e.getKey(), clearMeasureRange(e.getKey())));
		}
		if (textFilter != null) {
			list.add(new Restriction(Restriction.Kind.TEXT, "text \u201C" + textFilter + "\u201D",
					"Pattern text must contain all words of the search", clearTextFilter()));
		}
		return list;
	}

	/** @return a multi-line textual description of the query */
	public String describe() {
		if (isUnrestricted()) {
			return "All patterns (no restriction)";
		}
		StringBuilder sb = new StringBuilder();
		for (Restriction r : getRestrictions()) {
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(r.getLabel());
		}
		return sb.toString();
	}

	/** Label for a size range */
	public static String sizeLabel(int min, int max) {
		if (min != NO_LIMIT && max != NO_LIMIT) {
			return min == max ? "size = " + min : "size " + min + "\u2013" + max;
		} else if (min != NO_LIMIT) {
			return "size \u2265 " + min;
		} else if (max != NO_LIMIT) {
			return "size \u2264 " + max;
		}
		return "any size";
	}

	/** Label for a measure range */
	public static String measureLabel(String measure, double min, double max) {
		boolean hasMin = !Double.isNaN(min), hasMax = !Double.isNaN(max);
		if (hasMin && hasMax) {
			if (min == max) {
				return measure + " = " + ExplorerFormat.number(min);
			}
			return measure + " \u2208 [" + ExplorerFormat.number(min) + ", " + ExplorerFormat.number(max) + "]";
		} else if (hasMin) {
			return measure + " \u2265 " + ExplorerFormat.number(min);
		} else if (hasMax) {
			return measure + " \u2264 " + ExplorerFormat.number(max);
		}
		return "any " + measure;
	}

	/** Label for an item set (abbreviated if long) */
	public static String setLabel(Collection<String> items) {
		StringBuilder sb = new StringBuilder("{");
		int i = 0;
		for (String s : items) {
			if (i > 0) {
				sb.append(", ");
			}
			if (i == 6 && items.size() > 7) {
				sb.append("\u2026 (").append(items.size()).append(" items)");
				break;
			}
			sb.append(s);
			i++;
		}
		return sb.append('}').toString();
	}

	// ------------------------------------------------------------------
	// equals / hashCode
	// ------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ExplorerQuery)) {
			return false;
		}
		ExplorerQuery q = (ExplorerQuery) o;
		if (minSize != q.minSize || maxSize != q.maxSize) {
			return false;
		}
		if (!requiredItems.equals(q.requiredItems) || !excludedItems.equals(q.excludedItems)) {
			return false;
		}
		if (!Objects.equals(withinItems, q.withinItems) || !Objects.equals(textFilter, q.textFilter)) {
			return false;
		}
		if (!measureRanges.keySet().equals(q.measureRanges.keySet())) {
			return false;
		}
		for (Map.Entry<String, double[]> e : measureRanges.entrySet()) {
			if (!Arrays.equals(e.getValue(), q.measureRanges.get(e.getKey()))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		return Objects.hash(requiredItems, excludedItems, withinItems, minSize, maxSize, measureRanges.keySet(),
				textFilter);
	}

	@Override
	public String toString() {
		return describe().replace('\n', ';');
	}
}